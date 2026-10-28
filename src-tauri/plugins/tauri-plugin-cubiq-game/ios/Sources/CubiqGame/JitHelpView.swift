// Cubiq Launcher – Hilfe-Bildschirm, solange JIT fehlt. Prüft jede Sekunde und meldet sich,
// sobald JIT aktiv ist; ohne JIT startet das Spiel nie.
// SPDX-License-Identifier: GPL-3.0-only

import UIKit

final class JitHelpView: UIView {
  var onReady: (() -> Void)?
  var onCancel: (() -> Void)?

  private let txm: Bool
  private let isEnabled: () -> Bool
  private var timer: Timer?
  private let status = UILabel()

  init(txm: Bool, isEnabled: @escaping () -> Bool) {
    self.txm = txm
    self.isEnabled = isEnabled
    super.init(frame: .zero)
    // Grün getönte Flächen und Grün wie im Launcher (base-900, primary-500/300, base-200, Schrift dunkel auf Grün).
    backgroundColor = UIColor(red: 0.067, green: 0.086, blue: 0.075, alpha: 1)
    build()
    if txm {
      JitHelpView.exportScript()
    }
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) wird nicht benutzt")
  }

  private func build() {
    let accent = UIColor(red: 0.443, green: 0.639, blue: 0.188, alpha: 1)
    let accentText = UIColor(red: 0.663, green: 0.831, blue: 0.420, alpha: 1)
    let onAccent = UIColor(red: 0.043, green: 0.078, blue: 0.020, alpha: 1)
    let soft = UIColor(red: 0.804, green: 0.855, blue: 0.820, alpha: 1)

    let title = UILabel()
    title.text = Strings.jitTitle
    title.font = .boldSystemFont(ofSize: 22)
    title.textColor = .white
    title.numberOfLines = 0

    let body = UILabel()
    body.text = Strings.jitIntro + "\n\n" + (txm ? Strings.jitStepsTxm : Strings.jitStepsGeneric)
    body.font = .systemFont(ofSize: 15)
    body.textColor = soft
    body.numberOfLines = 0

    status.text = Strings.jitWaiting
    status.font = .systemFont(ofSize: 14, weight: .semibold)
    status.textColor = accentText

    let check = UIButton(type: .system)
    check.setTitle(Strings.checkAgain, for: .normal)
    check.setTitleColor(onAccent, for: .normal)
    check.backgroundColor = accent
    check.layer.cornerRadius = 12
    check.contentEdgeInsets = UIEdgeInsets(top: 10, left: 18, bottom: 10, right: 18)
    check.addTarget(self, action: #selector(checkNow), for: .touchUpInside)

    let cancel = UIButton(type: .system)
    cancel.setTitle(Strings.cancel, for: .normal)
    cancel.setTitleColor(soft, for: .normal)
    cancel.contentEdgeInsets = UIEdgeInsets(top: 10, left: 18, bottom: 10, right: 18)
    cancel.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)

    let buttons = UIStackView(arrangedSubviews: [check, cancel, UIView()])
    buttons.axis = .horizontal
    buttons.spacing = 12

    let stack = UIStackView(arrangedSubviews: [title, body, status, buttons])
    stack.axis = .vertical
    stack.spacing = 16
    stack.translatesAutoresizingMaskIntoConstraints = false

    let scroll = UIScrollView()
    scroll.translatesAutoresizingMaskIntoConstraints = false
    scroll.addSubview(stack)
    addSubview(scroll)

    let guide = safeAreaLayoutGuide
    NSLayoutConstraint.activate([
      scroll.topAnchor.constraint(equalTo: guide.topAnchor),
      scroll.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
      scroll.leadingAnchor.constraint(equalTo: guide.leadingAnchor),
      scroll.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 24),
      stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
      stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 32),
      stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -32),
    ])
  }

  func startPolling() {
    timer?.invalidate()
    timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
      self?.checkNow()
    }
  }

  func stopPolling() {
    timer?.invalidate()
    timer = nil
  }

  @objc func checkNow() {
    guard isEnabled() else { return }
    stopPolling()
    onReady?()
  }

  @objc private func cancelTapped() {
    stopPolling()
    onCancel?()
  }

  /// Legt das StikDebug-Skript in Documents, damit es über die Dateien-App wählbar ist.
  static func exportScript() {
    guard let source = Bundle.main.path(forResource: "UniversalJIT26", ofType: "js") else { return }
    let dest = (DeviceProbe.documentsPath() as NSString).appendingPathComponent("UniversalJIT26.js")
    if !FileManager.default.fileExists(atPath: dest) {
      try? FileManager.default.copyItem(atPath: source, toPath: dest)
    }
  }
}
