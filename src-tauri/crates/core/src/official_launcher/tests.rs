use std::path::{Path, PathBuf};

use super::detect::{self, Env, Installation, StartCommand, Variant};
use super::profiles::{self, Entry, Resolution};
use super::{flatpak, running, versions};

/// So sieht eine Datei des offiziellen Launchers aus – mit alten Kontodaten, die Cubiq nie
/// anfassen darf. Werte wie `1.000`, `-0` und `\u00e9` würden sich beim Neu-Schreiben ändern:
/// Bleiben sie Byte für Byte, hat Cubiq sie nicht dekodiert.
const FIXTURE: &str = r#"{
  "authenticationDatabase" : {
    "0123abcd" : {
      "accessToken" : "SECRET-ACCESS-TOKEN-\u0041BC",
      "displayName" : "Steve",
      "userProperties" : [ ],
      "username" : "steve@example.com"
    }
  },
  "clientToken" : "c0ffee00-SECRET-CLIENT-TOKEN",
  "launcherVersion" : { "format" : 21, "name" : "2.2.1234", "ratio" : 1.000, "zero" : -0, "esc" : "\u00e9\/x" },
  "profiles" : {
    "a1b2c3" : {
      "created" : "2021-01-01T00:00:00.000Z",
      "icon" : "Grass",
      "lastUsed" : "2026-10-01T12:00:00.000Z",
      "lastVersionId" : "latest-release",
      "name" : "",
      "type" : "latest-release"
    }
  },
  "settings" : { "tricky" : "}{\"profiles\" : {}", "profiles" : "nicht dieses" },
  "version" : 3
}
"#;

fn entry(name: &str, game_dir: &str) -> Entry {
    Entry {
        name: name.into(),
        kind: "custom",
        created: "2026-10-29T10:00:00.000Z".into(),
        last_used: "2026-10-29T10:00:00.000Z".into(),
        icon: "data:image/png;base64,AAAA".into(),
        last_version_id: "fabric-loader-0.16.10-1.21.1".into(),
        game_dir: game_dir.into(),
        java_args: "-Xmx4096M -Dcubiqclient.api.url=https://launcher.cubiq.studio".into(),
        resolution: Some(Resolution { width: 854, height: 480 }),
    }
}

/// Bereich des Werts von `profiles` im Text (für die Byte-Vergleiche davor und danach).
fn profiles_span(text: &str) -> (usize, usize) {
    let start = text.find("\"profiles\" : {").unwrap() + "\"profiles\" : ".len();
    let end = text[start..].find("\n  },\n  \"settings\"").unwrap() + start + "\n  }".len();
    (start, end)
}

fn json(text: &str) -> serde_json::Value {
    serde_json::from_str(text).unwrap()
}

// --- Profile: nur der eigene Eintrag ändert sich ------------------------------------------------

#[test]
fn upsert_changes_only_our_entry_byte_for_byte() {
    let out = profiles::upsert_text(FIXTURE, "cubiq-meine-welt", &entry("Cubiq · Meine Welt", "/data/instances/meine-welt/minecraft")).unwrap();
    let (start, end) = profiles_span(FIXTURE);
    // Alles vor und nach dem Wert von `profiles` ist unverändert – auch die Kontodaten.
    assert!(out.starts_with(&FIXTURE[..start]), "{out}");
    let tail = &FIXTURE[end..];
    assert!(out.ends_with(tail), "{out}");
    // Der vorhandene Eintrag steht Zeichen für Zeichen wie vorher darin.
    let original_profile = &FIXTURE[FIXTURE.find("\"a1b2c3\"").unwrap()..FIXTURE.find("\"type\" : \"latest-release\"\n    }").unwrap()];
    assert!(out.contains(original_profile));
    for raw in ["SECRET-ACCESS-TOKEN-\\u0041BC", "c0ffee00-SECRET-CLIENT-TOKEN", "\"ratio\" : 1.000", "\"zero\" : -0", "\\u00e9\\/x", "}{\\\"profiles\\\" : {}"] {
        assert!(out.contains(raw), "{raw} fehlt");
    }

    // Inhaltlich: genau `profiles.cubiq-meine-welt` ist dazugekommen, sonst nichts.
    let mut after = json(&out);
    let ours = after["profiles"].as_object_mut().unwrap().remove("cubiq-meine-welt").unwrap();
    assert_eq!(after, json(FIXTURE));
    assert_eq!(ours["name"], "Cubiq · Meine Welt");
    assert_eq!(ours["type"], "custom");
    assert_eq!(ours["gameDir"], "/data/instances/meine-welt/minecraft");
    assert_eq!(ours["lastVersionId"], "fabric-loader-0.16.10-1.21.1");
    assert_eq!(ours["javaArgs"], "-Xmx4096M -Dcubiqclient.api.url=https://launcher.cubiq.studio");
    assert_eq!(ours["resolution"], serde_json::json!({ "width": 854, "height": 480 }));
    assert!(ours.get("javaDir").is_none(), "keine eigene Java – der offizielle Launcher nimmt seine");
}

#[test]
fn upsert_replaces_our_entry_and_keeps_created() {
    let first = profiles::upsert_text(FIXTURE, "cubiq-x", &entry("Cubiq · X", "/a")).unwrap();
    let mut newer = entry("Cubiq · X neu", "/b");
    newer.created = "2030-01-01T00:00:00.000Z".into();
    newer.last_used = "2030-01-01T00:00:00.000Z".into();
    let second = profiles::upsert_text(&first, "cubiq-x", &newer).unwrap();
    let value = json(&second);
    let ours = &value["profiles"]["cubiq-x"];
    assert_eq!(ours["name"], "Cubiq · X neu");
    assert_eq!(ours["gameDir"], "/b");
    assert_eq!(ours["created"], "2026-10-29T10:00:00.000Z", "erstes Anlegen bleibt");
    assert_eq!(ours["lastUsed"], "2030-01-01T00:00:00.000Z", "zuletzt benutzt → oben in der Liste");
    assert_eq!(value["profiles"].as_object().unwrap().len(), 2);
    // Gleicher Eintrag noch einmal: Text bleibt gleich.
    assert_eq!(profiles::upsert_text(&second, "cubiq-x", &newer).unwrap(), second);
}

#[test]
fn upsert_then_remove_restores_the_original_text() {
    let with = profiles::upsert_text(FIXTURE, "cubiq-a", &entry("Cubiq · A", "/a")).unwrap();
    let with = profiles::upsert_text(&with, "cubiq-b", &entry("Cubiq · B", "/b")).unwrap();
    assert_eq!(profiles::own_keys_text(&with).unwrap(), ["cubiq-a", "cubiq-b"]);
    let (without, removed) = profiles::remove_text(&with, |key, _| key.starts_with(profiles::KEY_PREFIX)).unwrap();
    assert_eq!(removed, 2);
    assert_eq!(without, FIXTURE, "Byte für Byte wie vorher");
    // Ohne Treffer: unverändert, nichts entfernt.
    assert_eq!(profiles::remove_text(FIXTURE, |_, _| true).unwrap(), (FIXTURE.to_owned(), 0));
}

#[test]
fn remove_only_touches_own_keys_with_matching_game_dir() {
    let text = profiles::upsert_text(FIXTURE, "cubiq-welt", &entry("Cubiq · Welt", "/fremd/minecraft")).unwrap();
    // Eintrag einer anderen Cubiq-Installation (anderer Spielordner) bleibt.
    let (same, removed) =
        profiles::remove_text(&text, |key, value| key == "cubiq-welt" && value["gameDir"] == "/meins/minecraft").unwrap();
    assert_eq!((same.as_str(), removed), (text.as_str(), 0));
    // Fremde Einträge (ohne Präfix) werden nicht einmal angesehen.
    let (same, removed) = profiles::remove_text(&text, |key, _| key == "a1b2c3").unwrap();
    assert_eq!((same.as_str(), removed), (text.as_str(), 0));
    let (gone, removed) = profiles::remove_text(&text, |_, value| value["gameDir"] == "/fremd/minecraft").unwrap();
    assert_eq!(removed, 1);
    assert_eq!(gone, FIXTURE);
}

#[test]
fn missing_null_and_empty_profiles_are_handled() {
    for (input, keep) in [
        ("{\"clientToken\":\"T\",\"version\":3}", vec!["clientToken", "version"]),
        ("{}", vec![]),
        ("{\"profiles\":null,\"clientToken\":\"T\"}", vec!["clientToken"]),
        ("{\"profiles\":{},\"clientToken\":\"T\"}", vec!["clientToken"]),
        ("{\"profiles\":{ }}", vec![]),
    ] {
        let out = profiles::upsert_text(input, "cubiq-x", &entry("Cubiq · X", "/x")).unwrap();
        let value = json(&out);
        assert_eq!(value["profiles"]["cubiq-x"]["name"], "Cubiq · X", "{input}");
        assert_eq!(value["profiles"].as_object().unwrap().len(), 1, "{input}");
        for key in keep {
            assert_eq!(value[key], json(input)[key], "{input}: {key}");
        }
        assert_eq!(value.as_object().unwrap().len(), json(input).as_object().unwrap().len() + usize::from(json(input).get("profiles").is_none()));
    }
}

#[test]
fn duplicate_entries_and_bad_files_are_refused_or_cleaned() {
    // Unser Schlüssel doppelt (von Hand kopiert): danach genau einmal.
    let doubled = "{\"profiles\":{\"cubiq-x\":{\"name\":\"alt\"},\"z\":{},\"cubiq-x\":{\"name\":\"alt2\"}}}";
    let out = profiles::upsert_text(doubled, "cubiq-x", &entry("Cubiq · X", "/x")).unwrap();
    assert_eq!(out.matches("\"cubiq-x\"").count(), 1, "{out}");
    assert!(out.contains("\"z\":{}"));
    // Kein JSON, kein Objekt, `profiles` doppelt oder kein Objekt: nichts anfassen.
    for bad in ["", "nicht json", "[1,2]", "{\"profiles\":{}", "{\"profiles\":{},\"profiles\":{}}", "{\"profiles\":[]}", "{\"profiles\":\"x\"}"] {
        assert!(profiles::upsert_text(bad, "cubiq-x", &entry("X", "/x")).is_err(), "{bad:?}");
    }
    // Schlüssel mit Escapes zählen wie geschrieben (`\u0070rofiles` = `profiles`).
    let escaped = "{\"\\u0070rofiles\":{\"a\":1}}";
    let out = profiles::upsert_text(escaped, "cubiq-x", &entry("X", "/x")).unwrap();
    assert_eq!(json(&out)["profiles"]["a"], 1);
    assert!(out.starts_with("{\"\\u0070rofiles\":{\"a\":1,"));
}

// --- Datei: atomar, einmalige Sicherung, Rechte bleiben -------------------------------------------

fn files_in(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir).unwrap().map(|e| e.unwrap().file_name().to_string_lossy().into_owned()).collect();
    names.sort();
    names
}

#[test]
fn file_writes_are_atomic_with_one_backup() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join(detect::LEGACY_PROFILES);
    // BOM bleibt erhalten.
    let original = format!("\u{feff}{FIXTURE}");
    std::fs::write(&file, &original).unwrap();
    // Anmeldedateien daneben: bleiben unberührt.
    let accounts = dir.path().join(["launcher", "_accounts.json"].concat());
    let credentials = dir.path().join(["launcher", "_msa_credentials.bin"].concat());
    std::fs::write(&accounts, "geheim-1").unwrap();
    std::fs::write(&credentials, "geheim-2").unwrap();
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&file, std::fs::Permissions::from_mode(0o600)).unwrap();
    }

    profiles::upsert(&file, "cubiq-a", &entry("Cubiq · A", "/a")).unwrap();
    let backup = profiles::backup_path(&file);
    assert_eq!(backup.file_name().unwrap(), "launcher_profiles.json.cubiq-bak");
    assert_eq!(std::fs::read_to_string(&backup).unwrap(), original, "Sicherung = Original");
    let written = std::fs::read_to_string(&file).unwrap();
    assert!(written.starts_with('\u{feff}'));
    assert!(written.contains("\"cubiq-a\""));
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        assert_eq!(std::fs::metadata(&file).unwrap().permissions().mode() & 0o777, 0o600, "Rechte des Originals");
    }

    // Zweite Änderung: Die Sicherung bleibt die vom ersten Mal.
    profiles::upsert(&file, "cubiq-b", &entry("Cubiq · B", "/b")).unwrap();
    assert_eq!(std::fs::read_to_string(&backup).unwrap(), original);
    assert_eq!(profiles::own_keys(&file).unwrap(), ["cubiq-a", "cubiq-b"]);
    assert_eq!(profiles::remove(&file, |key, _| key == "cubiq-a").unwrap(), 1);
    assert_eq!(profiles::remove(&file, |_, _| false).unwrap(), 0);
    assert_eq!(profiles::own_keys(&file).unwrap(), ["cubiq-b"]);
    // Keine Temp-Dateien liegen geblieben; Anmeldedateien unverändert.
    assert_eq!(
        files_in(dir.path()),
        ["launcher_accounts.json", "launcher_msa_credentials.bin", "launcher_profiles.json", "launcher_profiles.json.cubiq-bak"]
    );
    assert_eq!(std::fs::read_to_string(&accounts).unwrap(), "geheim-1");
    assert_eq!(std::fs::read_to_string(&credentials).unwrap(), "geheim-2");
}

#[test]
fn broken_or_missing_files_stay_untouched() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join(detect::LEGACY_PROFILES);
    assert!(matches!(profiles::upsert(&file, "cubiq-a", &entry("A", "/a")), Err(profiles::ProfilesError::Missing)));
    assert_eq!(profiles::remove(&file, |_, _| true).unwrap(), 0);
    assert!(!file.exists(), "fehlende Datei wird nicht angelegt");
    std::fs::write(&file, "{ kaputt").unwrap();
    assert!(matches!(profiles::upsert(&file, "cubiq-a", &entry("A", "/a")), Err(profiles::ProfilesError::Unreadable(_))));
    assert_eq!(std::fs::read_to_string(&file).unwrap(), "{ kaputt");
    assert_eq!(files_in(dir.path()), ["launcher_profiles.json"], "keine Sicherung, keine Temp-Datei");
}

// --- Erkennung je Plattform -----------------------------------------------------------------------

fn touch(path: &Path, text: &str) {
    std::fs::create_dir_all(path.parent().unwrap()).unwrap();
    std::fs::write(path, text).unwrap();
}

#[cfg(unix)]
fn make_executable(path: &Path) {
    use std::os::unix::fs::PermissionsExt;
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755)).unwrap();
}

#[cfg(not(unix))]
fn make_executable(_: &Path) {}

fn set_modified(path: &Path, seconds_ago: u64) {
    let time = std::time::SystemTime::now() - std::time::Duration::from_secs(seconds_ago);
    std::fs::File::options().write(true).open(path).unwrap().set_modified(time).unwrap();
}

#[test]
fn detects_windows_legacy_and_store() {
    let root = tempfile::tempdir().unwrap();
    let appdata = root.path().join("AppData/Roaming");
    let program_files = root.path().join("Program Files (x86)");
    let mut env = Env { windows: true, appdata: Some(appdata.clone()), program_files: vec![program_files.clone()], ..Env::default() };
    assert!(detect::detect(&env).is_empty(), "nichts installiert");
    assert!(detect::choose(&[]).is_none());

    // Klassisch: Programm und Datei.
    let exe = program_files.join("Minecraft Launcher/MinecraftLauncher.exe");
    touch(&exe, "");
    let legacy_file = appdata.join(".minecraft/launcher_profiles.json");
    touch(&legacy_file, "{}");
    set_modified(&legacy_file, 3600);
    let found = detect::detect(&env);
    assert_eq!(found.len(), 1);
    assert_eq!(found[0].variant, Variant::WindowsLegacy);
    assert_eq!(found[0].profiles_file, legacy_file);
    assert_eq!(found[0].start, Some(StartCommand { program: exe.clone(), args: vec![], check_exit: true }));

    // Store dazu (neuere Datei): wird geöffnet, eingetragen wird in beide Dateien.
    env.store_installed = true;
    env.system_root = Some(root.path().join("Windows"));
    let store_file = appdata.join(".minecraft/launcher_profiles_microsoft_store.json");
    touch(&store_file, "{}");
    let found = detect::detect(&env);
    assert_eq!(found.iter().map(|i| i.variant).collect::<Vec<_>>(), [Variant::WindowsLegacy, Variant::WindowsStore]);
    let chosen = detect::choose(&found).unwrap();
    assert_eq!(chosen.variant, Variant::WindowsStore);
    assert_eq!(
        chosen.start,
        Some(StartCommand {
            program: root.path().join("Windows").join("explorer.exe"),
            args: vec!["shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft".into()],
            check_exit: false,
        })
    );
    let targets: Vec<&PathBuf> = detect::write_targets(&found, chosen).iter().map(|i| &i.profiles_file).collect();
    assert_eq!(targets, [&legacy_file, &store_file]);

    // Klassisch zuletzt benutzt → klassisch öffnen.
    set_modified(&store_file, 7200);
    set_modified(&legacy_file, 10);
    assert_eq!(detect::choose(&detect::detect(&env)).unwrap().variant, Variant::WindowsLegacy);

    // Store installiert, aber noch nie geöffnet (keine Datei): zählt nach den eingerichteten.
    std::fs::remove_file(&store_file).unwrap();
    std::fs::remove_file(&exe).unwrap();
    let found = detect::detect(&env);
    let store = found.iter().find(|i| i.variant == Variant::WindowsStore).unwrap();
    assert!(!store.profiles_exist());
    // Klassische Datei ohne Programm (eingerichtet, Start unbekannt) schlägt „nie geöffnet“.
    assert_eq!(detect::choose(&found).unwrap().variant, Variant::WindowsLegacy);
}

#[test]
fn detects_linux_native_and_flatpak() {
    let root = tempfile::tempdir().unwrap();
    let home = root.path().join("home/steve");
    let bin = root.path().join("bin");
    let flatpak_root = root.path().join("flatpak");
    let env = Env {
        home: Some(home.clone()),
        data_home: Some(home.join(".local/share")),
        path_dirs: vec![bin.clone()],
        flatpak_roots: vec![flatpak_root.clone()],
        ..Env::default()
    };
    assert!(detect::detect(&env).is_empty());

    // Nativ: Programm im PATH, Daten in ~/.minecraft.
    let launcher = bin.join("minecraft-launcher");
    touch(&launcher, "#!/bin/sh\n");
    make_executable(&launcher);
    let found = detect::detect(&env);
    assert_eq!(found.len(), 1);
    assert_eq!(found[0].variant, Variant::LinuxNative);
    assert_eq!(found[0].data_dir, home.join(".minecraft"));
    assert_eq!(found[0].profiles_file, home.join(".minecraft/launcher_profiles.json"));
    assert_eq!(found[0].start, Some(StartCommand { program: launcher.clone(), args: vec![], check_exit: true }));
    assert!(!found[0].profiles_exist(), "noch nie geöffnet");

    // Flatpak: App installiert, eigene Daten unter ~/.var/app; `flatpak` im PATH startet ihn.
    std::fs::create_dir_all(flatpak_root.join("app/com.mojang.Minecraft")).unwrap();
    let flatpak_bin = bin.join("flatpak");
    touch(&flatpak_bin, "#!/bin/sh\n");
    make_executable(&flatpak_bin);
    let flatpak_file = home.join(".var/app/com.mojang.Minecraft/.minecraft/launcher_profiles.json");
    touch(&flatpak_file, "{}");
    let found = detect::detect(&env);
    assert_eq!(found.len(), 2);
    let chosen = detect::choose(&found).unwrap();
    assert_eq!(chosen.variant, Variant::LinuxFlatpak, "eingerichtet schlägt nie geöffnet");
    assert_eq!(chosen.data_dir, home.join(".var/app/com.mojang.Minecraft/.minecraft"));
    assert_eq!(
        chosen.start,
        Some(StartCommand { program: flatpak_bin.clone(), args: vec!["run".into(), "com.mojang.Minecraft".into()], check_exit: true })
    );
    // Unter Linux nur in die gewählte Datei.
    assert_eq!(detect::write_targets(&found, chosen).len(), 1);

    // Cubiq selbst als Flatpak: Programme des Systems lassen sich nicht starten.
    let sandboxed = Env { in_flatpak: true, ..env.clone() };
    assert!(detect::detect(&sandboxed).iter().all(|i| i.start.is_none()));
    // Ohne Ausführungsrecht ist es kein Programm.
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&launcher, std::fs::Permissions::from_mode(0o644)).unwrap();
        assert!(detect::start_command(Variant::LinuxNative, &env).is_none());
    }
}

#[test]
fn start_commands_per_platform() {
    let root = tempfile::tempdir().unwrap();
    // Store ohne Paket: nicht startbar; mit Paket über die App-Verknüpfung.
    let mut env = Env { windows: true, ..Env::default() };
    assert_eq!(detect::start_command(Variant::WindowsStore, &env), None);
    env.store_installed = true;
    let store = detect::start_command(Variant::WindowsStore, &env).unwrap();
    assert_eq!(store.program, PathBuf::from("explorer.exe"));
    assert_eq!(store.args, ["shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft"]);
    assert!(!store.check_exit, "der Explorer meldet oft einen Fehler, obwohl die App startet");
    // Klassisch: erst Program Files (x86), dann Program Files.
    let x64 = root.path().join("pf");
    env.program_files = vec![root.path().join("pf86"), x64.clone()];
    assert_eq!(detect::start_command(Variant::WindowsLegacy, &env), None);
    touch(&x64.join("Minecraft Launcher/MinecraftLauncher.exe"), "");
    assert_eq!(detect::start_command(Variant::WindowsLegacy, &env).unwrap().program, x64.join("Minecraft Launcher/MinecraftLauncher.exe"));
    // Paketname des Store-Launchers.
    assert!(detect::is_store_package("Microsoft.4297127D64EC6_1.1.28.0_x64__8wekyb3d8bbwe"));
    assert!(!detect::is_store_package("Microsoft.MinecraftUWP_1.21.0.0_x64__8wekyb3d8bbwe"));
    assert!(!detect::is_store_package("Microsoft.4297127D64EC6_1.0_x64__evil"));
}

#[test]
fn launcher_is_recognised_by_process_name_only() {
    assert!(running::is_launcher_name("MinecraftLauncher.exe", true));
    assert!(running::is_launcher_name("minecraftlauncher.EXE", true));
    assert!(running::is_launcher_name("Minecraft.exe", true));
    assert!(!running::is_launcher_name("javaw.exe", true));
    assert!(!running::is_launcher_name("Minecraft.Windows.exe", true), "Bedrock");
    assert!(running::is_launcher_name("minecraft-launc\n", false), "auf 15 Zeichen gekürzt");
    assert!(running::is_launcher_name("minecraft-launcher", false));
    assert!(!running::is_launcher_name("java", false));
    // Die Abfrage selbst geht immer (ob er hier läuft, hängt vom Rechner ab).
    let _ = running::is_running();
}

/// Die Regeln stehen auch im Quelltext: keine Anmeldedateien, keine Kommandozeilen oder Umgebungen
/// fremder Prozesse, keine fremden Schlüssel – außer in Kommentaren, die genau das verbieten.
#[test]
fn source_never_touches_credentials_or_command_lines() {
    let forbidden = [
        ["launcher", "_accounts"].concat(),
        ["msa", "_credentials"].concat(),
        ["cmd", "line"].concat(),
        ["/environ"].concat(),
        ["authentication", "Database"].concat(),
        ["client", "Token"].concat(),
        ["access", "Token"].concat(),
    ];
    let sources = [
        ("mod.rs", include_str!("mod.rs")),
        ("detect.rs", include_str!("detect.rs")),
        ("profiles.rs", include_str!("profiles.rs")),
        ("running.rs", include_str!("running.rs")),
        ("start.rs", include_str!("start.rs")),
        ("versions.rs", include_str!("versions.rs")),
        ("flatpak.rs", include_str!("flatpak.rs")),
        ("prefs.rs", include_str!("prefs.rs")),
    ];
    for (name, source) in sources {
        for (number, line) in source.lines().enumerate() {
            let code = line.trim_start();
            if code.starts_with("//") {
                continue;
            }
            for needle in &forbidden {
                assert!(!code.contains(needle.as_str()), "{name}:{}: {needle}", number + 1);
            }
        }
    }
}

// --- Loader-Profil im Versionsordner ----------------------------------------------------------------

const FABRIC_PROFILE: &str = r#"{"id":"fabric-loader-0.16.10-1.21.1","inheritsFrom":"1.21.1","releaseTime":"2026-01-01T00:00:00+0000","time":"2026-01-01T00:00:00+0000","type":"release","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient","libraries":[{"name":"net.fabricmc:fabric-loader:0.16.10","url":"https://maven.fabricmc.net/"}]}"#;

#[test]
fn loader_profile_lands_in_the_versions_folder() {
    let dir = tempfile::tempdir().unwrap();
    let id = versions::place(dir.path(), FABRIC_PROFILE.as_bytes(), "1.21.1").unwrap();
    assert_eq!(id, "fabric-loader-0.16.10-1.21.1");
    let file = dir.path().join("versions/fabric-loader-0.16.10-1.21.1/fabric-loader-0.16.10-1.21.1.json");
    assert_eq!(versions::json_path(dir.path(), &id), file);
    // Genau das JSON der Meta (mit `time`/`releaseTime` – die braucht der offizielle Launcher).
    assert_eq!(std::fs::read_to_string(&file).unwrap(), FABRIC_PROFILE);
    // Vorhandenes gültiges Profil (z. B. vom Fabric-Installer) bleibt unverändert.
    let installer_copy = FABRIC_PROFILE.replace("\"type\":\"release\"", "\"type\":\"release\" ");
    std::fs::write(&file, &installer_copy).unwrap();
    assert_eq!(versions::place(dir.path(), FABRIC_PROFILE.as_bytes(), "1.21.1").unwrap(), id);
    assert_eq!(std::fs::read_to_string(&file).unwrap(), installer_copy);
    // Kaputtes Profil an der Stelle wird ersetzt.
    std::fs::write(&file, "{").unwrap();
    versions::place(dir.path(), FABRIC_PROFILE.as_bytes(), "1.21.1").unwrap();
    assert_eq!(std::fs::read_to_string(&file).unwrap(), FABRIC_PROFILE);
    // Offline: schon abgelegte Profile finden.
    assert_eq!(versions::existing(dir.path(), "fabric-loader-", Some("0.16.10"), "1.21.1").as_deref(), Some(id.as_str()));
    assert_eq!(versions::existing(dir.path(), "fabric-loader-", None, "1.21.1").as_deref(), Some(id.as_str()));
    assert_eq!(versions::existing(dir.path(), "fabric-loader-", Some("0.15.0"), "1.21.1"), None);
    assert_eq!(versions::existing(dir.path(), "quilt-loader-", None, "1.21.1"), None);
    assert_eq!(versions::existing(dir.path(), "fabric-loader-", None, "1.20.1"), None);
}

#[test]
fn unsafe_loader_profiles_are_refused() {
    let dir = tempfile::tempdir().unwrap();
    for bad in [
        r#"{"id":"../../evil","inheritsFrom":"1.21.1"}"#,
        r#"{"id":"a b","inheritsFrom":"1.21.1"}"#,
        r#"{"id":"fabric-loader-1-1.21.1","inheritsFrom":"1.20.1"}"#,
        r#"{"id":"fabric-loader-1-1.21.1"}"#,
        r#"["id"]"#,
        "kein json",
    ] {
        assert!(versions::place(dir.path(), bad.as_bytes(), "1.21.1").is_err(), "{bad}");
    }
    assert!(!dir.path().join("versions").exists() || files_in(&dir.path().join("versions")).is_empty());
    assert_eq!(dir.path().read_dir().unwrap().count(), 0);
}

// --- Flatpak-Freigabe ------------------------------------------------------------------------------

#[test]
fn flatpak_access_and_command() {
    let home = Path::new("/home/steve");
    let data = Path::new("/home/steve/.local/share");
    let game = PathBuf::from("/home/steve/.local/share/dev.pwiepixel.cubiqlauncher/instances/welt/minecraft");
    let keyfile = "[Context]\nfilesystems=xdg-download:ro;~/Spiele;\n\n[Session Bus Policy]\nfilesystems=home;\n";
    let entries = flatpak::filesystems(keyfile);
    assert_eq!(entries, ["xdg-download:ro", "~/Spiele"], "nur [Context]");
    assert!(!flatpak::is_granted(&game, &entries, home, data));
    for granted in ["home", "host", "xdg-data", "xdg-data/dev.pwiepixel.cubiqlauncher", "~/.local/share", "/home/steve/.local/share/dev.pwiepixel.cubiqlauncher/instances/welt/minecraft:rw"] {
        assert!(flatpak::is_granted(&game, &[granted.to_owned()], home, data), "{granted}");
    }
    for denied in ["home:ro", "/home/steve/.local/share/dev.pwiepixel.cubiqlauncher/instances/andere", "xdg-documents"] {
        assert!(!flatpak::is_granted(&game, &[denied.to_owned()], home, data), "{denied}");
    }
    assert!(!flatpak::is_granted(&game, &["home".to_owned(), "!~/.local/share".to_owned()], home, data), "verneint");

    assert_eq!(
        flatpak::override_command(&[game.clone()]),
        "flatpak override --user --filesystem=/home/steve/.local/share/dev.pwiepixel.cubiqlauncher/instances/welt/minecraft com.mojang.Minecraft"
    );
    assert_eq!(
        flatpak::override_command(&[PathBuf::from("/mnt/Meine Spiele/it's"), PathBuf::from("/data/shared-folders")]),
        "flatpak override --user --filesystem='/mnt/Meine Spiele/it'\\''s' --filesystem=/data/shared-folders com.mojang.Minecraft"
    );

    // Override-Datei des Benutzers wird gelesen.
    let root = tempfile::tempdir().unwrap();
    let flatpak_root = root.path().join("flatpak");
    assert_eq!(flatpak::missing(&[game.clone()], &[flatpak_root.clone()], home, data), [game.clone()]);
    touch(&flatpak_root.join("overrides/com.mojang.Minecraft"), &format!("[Context]\nfilesystems={};\n", game.display()));
    assert!(flatpak::missing(&[game.clone()], &[flatpak_root], home, data).is_empty());
}

// --- Ganze Übergabe: Forge/NeoForge werden abgelehnt, bevor etwas passiert -------------------------

#[tokio::test]
async fn forge_and_neoforge_are_refused_before_anything_happens() {
    use crate::instance::{Loader, LoaderKind, NewInstance};
    let dir = tempfile::tempdir().unwrap();
    let launcher = std::sync::Arc::new(crate::Launcher::init(dir.path(), std::sync::Arc::new(|_| {})).await.unwrap());
    for kind in [LoaderKind::Forge, LoaderKind::NeoForge] {
        let instance = launcher
            .instances()
            .create(NewInstance { name: format!("{kind:?}"), game_version: "1.21.1".into(), loader: Loader { kind, version: Some("52.0.1".into()) } })
            .await
            .unwrap();
        let mods = launcher.paths().instance_game_dir(&instance.id);
        let error = launcher.play_with_official_launcher(&instance.id, super::HandoffOptions::default(), &|_| {}).await.unwrap_err();
        match error {
            crate::Error::Launch(msg) => assert_eq!(msg.code, "officialLauncher.forgeUnsupported"),
            other => panic!("{other:?}"),
        }
        assert!(!launcher.is_preparing(&instance.id));
        assert!(!mods.join("mods").exists(), "nichts vorbereitet");
    }
    assert_eq!(super::installation_name("  Meine Welt "), "Cubiq · Meine Welt");
}

#[tokio::test]
async fn prefs_are_stored_in_their_own_file() {
    let dir = tempfile::tempdir().unwrap();
    let launcher = crate::Launcher::init(dir.path(), std::sync::Arc::new(|_| {})).await.unwrap();
    let status = launcher.official_launcher_status().await;
    assert_eq!((status.play_with, status.explained), (super::PlayWith::Auto, false));
    let saved = launcher.set_official_prefs(Some(super::PlayWith::Official), Some(true)).await.unwrap();
    assert_eq!(saved, super::Prefs { play_with: super::PlayWith::Official, explained: true });
    let saved = launcher.set_official_prefs(None, Some(false)).await.unwrap();
    assert_eq!(saved.play_with, super::PlayWith::Official, "None = unverändert");
    let status = launcher.official_launcher_status().await;
    assert_eq!((status.play_with, status.explained), (super::PlayWith::Official, false));
    assert!(dir.path().join("official-launcher.json").is_file());
    assert_eq!(serde_json::to_value(super::PlayWith::Official).unwrap(), "official");
}

#[test]
fn outcome_is_serialized_for_the_ui() {
    let started = super::HandoffOutcome::Started { variant: Variant::WindowsStore, installation: "Cubiq · X".into() };
    assert_eq!(
        serde_json::to_value(&started).unwrap(),
        serde_json::json!({ "status": "started", "variant": "windowsStore", "installation": "Cubiq · X" })
    );
    let never = super::HandoffOutcome::NeverOpened { variant: Variant::LinuxNative, can_open: true };
    assert_eq!(serde_json::to_value(&never).unwrap(), serde_json::json!({ "status": "neverOpened", "variant": "linuxNative", "canOpen": true }));
    assert_eq!(serde_json::to_value(super::HandoffOutcome::NotInstalled).unwrap(), serde_json::json!({ "status": "notInstalled" }));
}

#[test]
fn installation_struct_is_comparable() {
    // Nur damit `Installation` als öffentliches Ergebnis brauchbar bleibt (Clone/Eq).
    let a = Installation {
        variant: Variant::LinuxNative,
        data_dir: PathBuf::from("/h/.minecraft"),
        profiles_file: PathBuf::from("/h/.minecraft/launcher_profiles.json"),
        start: None,
    };
    assert_eq!(a.clone(), a);
}
