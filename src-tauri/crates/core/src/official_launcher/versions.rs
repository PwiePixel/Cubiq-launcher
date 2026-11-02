//! Loader-Profile (Fabric, Quilt) für den offiziellen Launcher: genau das JSON der Meta-API,
//! abgelegt wie bei den Installern von Fabric und Quilt unter `<.minecraft>/versions/<id>/<id>.json`.
//! Es erbt von der Vanilla-Version (`inheritsFrom`); Bibliotheken und Spiel lädt der offizielle
//! Launcher selbst.

use std::path::{Path, PathBuf};

use crate::meta::is_safe_id;

/// Größer ist kein Loader-Profil.
pub const MAX_PROFILE_BYTES: usize = 2 * 1024 * 1024;

#[derive(Debug, PartialEq, Eq)]
pub enum VersionError {
    /// Kein JSON-Objekt, unsichere ID oder `inheritsFrom` passt nicht zur Spielversion.
    Invalid(&'static str),
    Io(std::io::ErrorKind),
}

impl std::fmt::Display for VersionError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Invalid(why) => write!(f, "ungültiges Loader-Profil ({why})"),
            Self::Io(kind) => write!(f, "{kind}"),
        }
    }
}

/// `<.minecraft>/versions/<id>/<id>.json` – `id` muss vorher geprüft sein ([`is_safe_id`]).
pub fn json_path(data_dir: &Path, id: &str) -> PathBuf {
    data_dir.join("versions").join(id).join(format!("{id}.json"))
}

/// Prüft ein Loader-Profil und liefert seine ID.
pub fn profile_id(raw: &[u8], game_version: &str) -> Result<String, VersionError> {
    if raw.len() > MAX_PROFILE_BYTES {
        return Err(VersionError::Invalid("size"));
    }
    let value: serde_json::Value = serde_json::from_slice(raw).map_err(|_| VersionError::Invalid("json"))?;
    let id = value.get("id").and_then(serde_json::Value::as_str).ok_or(VersionError::Invalid("id"))?;
    // Wird zum Ordner- und Dateinamen: nur sichere IDs, keine Leerzeichen.
    if !is_safe_id(id) || id.contains(' ') {
        return Err(VersionError::Invalid("id"));
    }
    if value.get("inheritsFrom").and_then(serde_json::Value::as_str) != Some(game_version) {
        return Err(VersionError::Invalid("inheritsFrom"));
    }
    Ok(id.to_owned())
}

/// Legt das Profil ab und liefert seine ID. Liegt dort schon ein gültiges Profil mit derselben ID
/// (z. B. vom Fabric-Installer), bleibt es unverändert.
pub fn place(data_dir: &Path, raw: &[u8], game_version: &str) -> Result<String, VersionError> {
    let id = profile_id(raw, game_version)?;
    let target = json_path(data_dir, &id);
    if std::fs::read(&target).ok().is_some_and(|existing| profile_id(&existing, game_version).as_deref() == Ok(id.as_str())) {
        return Ok(id);
    }
    let dir = target.parent().ok_or(VersionError::Invalid("path"))?;
    std::fs::create_dir_all(dir).map_err(|e| VersionError::Io(e.kind()))?;
    crate::fsutil::write_atomic_sync(&target, raw).map_err(|_| VersionError::Io(std::io::ErrorKind::Other))?;
    Ok(id)
}

/// Offline: ein schon abgelegtes Profil für diese Spielversion (`prefix` = `fabric-loader-` bzw.
/// `quilt-loader-`, `loader_version` = gewählte Version oder `None` für die neueste vorhandene).
pub fn existing(data_dir: &Path, prefix: &str, loader_version: Option<&str>, game_version: &str) -> Option<String> {
    if let Some(version) = loader_version {
        let id = format!("{prefix}{version}-{game_version}");
        let raw = std::fs::read(json_path(data_dir, &id)).ok()?;
        return (profile_id(&raw, game_version).ok()? == id).then_some(id);
    }
    let suffix = format!("-{game_version}");
    let mut best: Option<(std::time::SystemTime, String)> = None;
    for entry in std::fs::read_dir(data_dir.join("versions")).ok()?.flatten() {
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with(prefix) || !name.ends_with(&suffix) || !is_safe_id(&name) {
            continue;
        }
        let path = json_path(data_dir, &name);
        let Ok(raw) = std::fs::read(&path) else { continue };
        if profile_id(&raw, game_version).ok().as_deref() != Some(name.as_str()) {
            continue;
        }
        let modified = std::fs::metadata(&path).and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH);
        if best.as_ref().is_none_or(|(time, _)| modified > *time) {
            best = Some((modified, name));
        }
    }
    best.map(|(_, id)| id)
}
