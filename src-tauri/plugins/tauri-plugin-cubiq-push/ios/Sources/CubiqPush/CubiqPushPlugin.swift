// Push-Benachrichtigungen auf iOS ohne APNs (Sideload): Das System weckt die App ab und zu im Hintergrund
// (BGAppRefreshTask), der Rust-Kern holt wartende Hinweise beim Cubiq-Server ab (§33.4) und diese Datei zeigt sie
// als lokale Benachrichtigung. Die ganze Logik (Konto, Token, Abholen, doppelte Hinweise) steckt in Rust.

import BackgroundTasks
import Foundation
import Tauri
import UIKit
import UserNotifications
import WebKit

// Aus libcubiq_launcher_lib (src-tauri/src/push.rs): JSON-Liste der neuen Hinweise, freigeben mit cubiq_push_free.
@_silgen_name("cubiq_push_poll_json")
func cubiq_push_poll_json() -> UnsafeMutablePointer<CChar>?
@_silgen_name("cubiq_push_free")
func cubiq_push_free(_ ptr: UnsafeMutablePointer<CChar>?)

struct PushNotification: Codable {
  let id: String
  let category: String
  let title: String
  let body: String
  let target: String
  let collapse: String?
}

class PollArgs: Decodable {
  let enabled: Bool
}

class AppIconArgs: Decodable {
  let icon: String
}

class NotifyArgs: Decodable {
  let notifications: [PushNotification]
}

/// Tipp auf eine eigene Benachrichtigung → `cubiq-launcher://notify<route>` (die App öffnet die passende Seite, wie
/// unter Android). Alles andere geht unverändert an den bisherigen Delegate (Notification-Plugin).
class TapForwarder: NSObject, UNUserNotificationCenterDelegate {
  static let targetKey = "cubiqPushTarget"
  weak var previous: UNUserNotificationCenterDelegate?

  func userNotificationCenter(
    _ center: UNUserNotificationCenter, willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    if let previous = previous,
      previous.responds(to: #selector(UNUserNotificationCenterDelegate.userNotificationCenter(_:willPresent:withCompletionHandler:)))
    {
      previous.userNotificationCenter?(center, willPresent: notification, withCompletionHandler: completionHandler)
      return
    }
    completionHandler([.banner, .sound])
  }

  func userNotificationCenter(
    _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
    withCompletionHandler completionHandler: @escaping () -> Void
  ) {
    let info = response.notification.request.content.userInfo
    if let target = info[TapForwarder.targetKey] as? String, target.hasPrefix("/"), target.count <= 300,
      let url = URL(string: "cubiq-launcher://notify" + target)
    {
      // Die Route prüft die App beim Öffnen des Links noch einmal (deeplink.rs).
      DispatchQueue.main.async { UIApplication.shared.open(url) }
      completionHandler()
      return
    }
    if let previous = previous,
      previous.responds(to: #selector(UNUserNotificationCenterDelegate.userNotificationCenter(_:didReceive:withCompletionHandler:)))
    {
      previous.userNotificationCenter?(center, didReceive: response, withCompletionHandler: completionHandler)
      return
    }
    completionHandler()
  }
}

class CubiqPushPlugin: Plugin {
  /// Muss in Info.plist unter BGTaskSchedulerPermittedIdentifiers stehen.
  static let taskId = "dev.pwiepixel.cubiqlauncher.push-poll"
  private static var registered = false
  private static var polling = false
  /// Der Delegate des Benachrichtigungszentrums ist `weak` – hier festhalten.
  private static var forwarder: TapForwarder?

  override func load(webview: WKWebView) {
    CubiqPushPlugin.registerTask()
    CubiqPushPlugin.installTapForwarder()
  }

  static func installTapForwarder() {
    if forwarder != nil { return }
    let center = UNUserNotificationCenter.current()
    let f = TapForwarder()
    f.previous = center.delegate
    forwarder = f
    center.delegate = f
  }

  static func registerTask() {
    if registered { return }
    registered = true
    BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
      guard let refresh = task as? BGAppRefreshTask else {
        task.setTaskCompleted(success: false)
        return
      }
      CubiqPushPlugin.handle(refresh)
    }
  }

  /// Nächsten Abruf frühestens in 15 Minuten (iOS entscheidet, wann wirklich).
  static func schedule() {
    let request = BGAppRefreshTaskRequest(identifier: taskId)
    request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
    do {
      try BGTaskScheduler.shared.submit(request)
    } catch {
      NSLog("CubiqPush: Hintergrundabruf nicht planbar: \(error)")
    }
  }

  static func handle(_ task: BGAppRefreshTask) {
    if polling { schedule() }
    let queue = DispatchQueue.global(qos: .utility)
    var finished = false
    task.expirationHandler = {
      finished = true
    }
    queue.async {
      let json = poll()
      if !finished {
        show(json)
      }
      task.setTaskCompleted(success: !finished)
    }
  }

  static func poll() -> [PushNotification] {
    guard let ptr = cubiq_push_poll_json() else { return [] }
    defer { cubiq_push_free(ptr) }
    let data = Data(String(cString: ptr).utf8)
    return (try? JSONDecoder().decode([PushNotification].self, from: data)) ?? []
  }

  static func show(_ list: [PushNotification]) {
    let center = UNUserNotificationCenter.current()
    for n in list.prefix(50) {
      let content = UNMutableNotificationContent()
      content.title = String(n.title.prefix(80))
      content.body = String(n.body.prefix(200))
      content.sound = .default
      content.threadIdentifier = n.category
      content.userInfo = [TapForwarder.targetKey: n.target]
      let request = UNNotificationRequest(identifier: n.collapse ?? n.id, content: content, trigger: nil)
      center.add(request, withCompletionHandler: nil)
    }
  }

  @objc public func state(_ invoke: Invoke) {
    invoke.resolve(["distributors": [], "temporary": false, "deviceName": UIDevice.current.name])
  }

  @objc public func setPoll(_ invoke: Invoke) {
    do {
      let args = try invoke.parseArgs(PollArgs.self)
      CubiqPushPlugin.polling = args.enabled
      if args.enabled {
        CubiqPushPlugin.schedule()
      } else {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: CubiqPushPlugin.taskId)
      }
      invoke.resolve()
    } catch {
      invoke.reject("invalid_args", code: "invalid_args")
    }
  }

  /// Event-Symbol auf dem Home-Bildschirm (alternatives App-Symbol aus dem Asset-Katalog, siehe
  /// scripts/ios-project.sh). iOS zeigt beim Wechsel selbst einen Hinweis. Schon richtig: nichts tun.
  @objc public func setAppIcon(_ invoke: Invoke) {
    let args: AppIconArgs
    do {
      args = try invoke.parseArgs(AppIconArgs.self)
    } catch {
      invoke.reject("invalid_args", code: "invalid_args")
      return
    }
    let name: String?
    switch args.icon {
    case "default": name = nil
    case "halloween": name = "AppIcon-Halloween"
    default:
      invoke.reject("invalid_icon", code: "invalid_icon")
      return
    }
    DispatchQueue.main.async {
      let app = UIApplication.shared
      guard app.supportsAlternateIcons else {
        invoke.reject("unsupported", code: "unsupported")
        return
      }
      if app.alternateIconName == name {
        invoke.resolve(["changed": false])
        return
      }
      app.setAlternateIconName(name) { error in
        if let error = error {
          NSLog("CubiqPush: App-Symbol nicht gewechselt: \(error)")
          invoke.reject("icon_failed", code: "icon_failed")
        } else {
          invoke.resolve(["changed": true])
        }
      }
    }
  }

  @objc public func notify(_ invoke: Invoke) {
    do {
      let args = try invoke.parseArgs(NotifyArgs.self)
      CubiqPushPlugin.show(args.notifications)
      invoke.resolve()
    } catch {
      invoke.reject("invalid_args", code: "invalid_args")
    }
  }

  /// UnifiedPush gibt es auf iOS nicht.
  @objc public func register(_ invoke: Invoke) {
    invoke.reject("unsupported", code: "unsupported")
  }

  @objc public func unregister(_ invoke: Invoke) {
    invoke.resolve()
  }
}

@_cdecl("init_plugin_cubiq_push")
func initPlugin() -> Plugin {
  return CubiqPushPlugin()
}
