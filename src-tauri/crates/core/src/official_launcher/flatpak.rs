//! Flatpak `com.mojang.Minecraft`: Die Sandbox sieht nur ihren eigenen Ordner. Damit das Spiel im
//! Cubiq-Instanzordner läuft, muss der Spieler ihn einmal freigeben – Cubiq ändert keine
//! Systemeinstellungen selbst, es zeigt nur den passenden Befehl. Ob die Freigabe schon da ist, steht
//! in den Override-Dateien von Flatpak (keine Anmeldedaten, nur Berechtigungen).

use std::path::{Path, PathBuf};

use super::detect::FLATPAK_ID;

/// Der Befehl, der der Sandbox Lese- und Schreibzugriff auf `paths` gibt.
pub fn override_command(paths: &[PathBuf]) -> String {
    let mut command = String::from("flatpak override --user");
    for path in paths {
        command.push_str(" --filesystem=");
        command.push_str(&shell_quote(&path.to_string_lossy()));
    }
    command.push(' ');
    command.push_str(FLATPAK_ID);
    command
}

/// Für die Shell sicher: einfache Pfade unverändert, sonst in einfachen Anführungszeichen.
fn shell_quote(text: &str) -> String {
    if !text.is_empty() && text.bytes().all(|b| b.is_ascii_alphanumeric() || b"/._-+~@%,".contains(&b)) {
        text.to_owned()
    } else {
        format!("'{}'", text.replace('\'', "'\\''"))
    }
}

/// Wo Flatpak Berechtigungen ablegt: Overrides des Benutzers und des Systems sowie die Metadaten der
/// App selbst (je Flatpak-Installation).
pub fn permission_files(flatpak_roots: &[PathBuf]) -> Vec<PathBuf> {
    flatpak_roots
        .iter()
        .flat_map(|root| {
            [root.join("overrides").join(FLATPAK_ID), root.join("app").join(FLATPAK_ID).join("current").join("active").join("metadata")]
        })
        .collect()
}

/// `filesystems=` aus der Gruppe `[Context]` einer Flatpak-Schlüsseldatei.
pub fn filesystems(keyfile: &str) -> Vec<String> {
    let mut in_context = false;
    let mut out = Vec::new();
    for line in keyfile.lines().map(str::trim) {
        if line.starts_with('[') {
            in_context = line == "[Context]";
        } else if in_context && let Some(list) = line.strip_prefix("filesystems=") {
            out.extend(list.split(';').map(str::trim).filter(|e| !e.is_empty()).map(str::to_owned));
        }
    }
    out
}

/// Ordner, den ein Eintrag freigibt – nur mit Schreibrecht (`:ro` reicht für das Spiel nicht).
/// `None`: Eintrag, den wir nicht auflösen (dann zählt er nicht als Freigabe).
fn granted_dir(entry: &str, home: &Path, data_home: &Path) -> Option<PathBuf> {
    let (path, mode) = entry.rsplit_once(':').filter(|(_, m)| matches!(*m, "ro" | "rw" | "create")).unwrap_or((entry, "rw"));
    if mode == "ro" {
        return None;
    }
    match path {
        "host" => Some(PathBuf::from("/")),
        "home" | "~" => Some(home.to_owned()),
        "xdg-data" => Some(data_home.to_owned()),
        _ => {
            if let Some(rest) = path.strip_prefix("~/") {
                Some(home.join(rest))
            } else if let Some(rest) = path.strip_prefix("xdg-data/") {
                Some(data_home.join(rest))
            } else if path.starts_with('/') {
                Some(PathBuf::from(path))
            } else {
                None
            }
        }
    }
}

/// Darf die Sandbox laut `entries` in `path` schreiben? Ein verneinter Eintrag (`!…`) nimmt die
/// Freigabe wieder weg.
pub fn is_granted(path: &Path, entries: &[String], home: &Path, data_home: &Path) -> bool {
    let covers = |entry: &str| granted_dir(entry, home, data_home).is_some_and(|dir| path.starts_with(dir));
    let denied = entries.iter().filter_map(|e| e.strip_prefix('!')).any(|e| {
        let plain = e.rsplit_once(':').map_or(e, |(p, _)| p);
        covers(plain)
    });
    !denied && entries.iter().filter(|e| !e.starts_with('!')).any(|e| covers(e))
}

/// Welche der Ordner die Sandbox noch nicht beschreiben darf.
pub fn missing(paths: &[PathBuf], flatpak_roots: &[PathBuf], home: &Path, data_home: &Path) -> Vec<PathBuf> {
    let entries: Vec<String> = permission_files(flatpak_roots)
        .iter()
        .filter_map(|f| std::fs::read_to_string(f).ok())
        .flat_map(|text| filesystems(&text))
        .collect();
    paths.iter().filter(|p| !is_granted(p, &entries, home, data_home)).cloned().collect()
}
