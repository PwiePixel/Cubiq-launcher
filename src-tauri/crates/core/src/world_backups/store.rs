//! Ablage der Welt-Backups im Datenordner:
//!
//! ```text
//! <daten>/world-backups/
//!   config.json                         eigene Einstellung je Instanz (an/aus)
//!   <instanz-id>/<weltordner>/          bzw. `_shared/…` für den gemeinsamen Welten-Ordner
//!     2026-10-27_14-05-09-auto.zip      Ortszeit im Namen – sortiert sich von selbst
//!     2026-10-27_15-30-00-manual.zip
//!     2026-10-27_16-00-00-safety.zip
//!     .<name>.part                      wird gerade geschrieben
//! ```
//!
//! Ältere Sicherungen aus dem Spielordner (`<spiel>/backups/<welt>_<datum>.zip`, früherer Knopf
//! „Sichern“) erscheinen als „legacy“ in der Liste und lassen sich ebenso wiederherstellen.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use chrono::{DateTime, Local, NaiveDateTime, TimeZone, Utc};
use serde::{Deserialize, Serialize};

use super::archive::Kind;
use crate::paths::Paths;
use crate::{Error, Result, fsutil};

pub const ROOT_DIR: &str = "world-backups";
/// Ordner für Backups des gemeinsamen Welten-Ordners (Instanz-IDs haben nie einen Unterstrich).
pub const SHARED_SCOPE: &str = "_shared";
const CONFIG: &str = "config.json";
const STAMP: &str = "%Y-%m-%d_%H-%M-%S";
/// Ordner der früheren Sicherungen im Spielordner.
pub const LEGACY_DIR: &str = "backups";

pub fn root(paths: &Paths) -> PathBuf {
    paths.root().join(ROOT_DIR)
}

pub fn scope_dir(paths: &Paths, scope: &str) -> PathBuf {
    root(paths).join(scope)
}

pub fn world_dir(paths: &Paths, scope: &str, folder: &str) -> PathBuf {
    scope_dir(paths, scope).join(folder)
}

/// Ein Backup auf der Platte.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BackupFile {
    /// Kennung fürs Frontend: Dateiname, bei älteren Sicherungen `legacy:<Dateiname>`.
    pub id: String,
    pub path: PathBuf,
    pub created: DateTime<Utc>,
    pub kind: Kind,
    pub size: u64,
}

/// Dateiname für ein neues Backup; `n` > 1 bei mehreren in derselben Sekunde.
pub fn file_name(created: DateTime<Local>, kind: Kind, n: u32) -> String {
    let stamp = created.format(STAMP);
    if n > 1 { format!("{stamp}-{n}-{}.zip", kind.code()) } else { format!("{stamp}-{}.zip", kind.code()) }
}

/// Freier Name in `dir` (auch keine laufende `.part`-Datei gleichen Namens).
pub fn free_file_name(dir: &Path, created: DateTime<Local>, kind: Kind) -> String {
    (1..10_000)
        .map(|n| file_name(created, kind, n))
        .find(|name| !dir.join(name).exists() && !dir.join(part_name(name)).exists())
        .unwrap_or_else(|| format!("{}-{}.zip", uuid::Uuid::new_v4().simple(), kind.code()))
}

/// Temp-Name während des Schreibens.
pub fn part_name(name: &str) -> String {
    format!(".{name}.part")
}

fn local_stamp(text: &str) -> Option<DateTime<Utc>> {
    let naive = NaiveDateTime::parse_from_str(text, STAMP).ok()?;
    Local.from_local_datetime(&naive).earliest().map(|t| t.with_timezone(&Utc))
}

/// Liest Zeit und Art aus einem Dateinamen dieser Ablage.
pub fn parse_name(name: &str) -> Option<(DateTime<Utc>, Kind)> {
    let base = name.strip_suffix(".zip")?;
    let (rest, kind) = base.rsplit_once('-')?;
    let kind = match kind {
        "auto" => Kind::Auto,
        "manual" => Kind::Manual,
        "safety" => Kind::Safety,
        _ => return None,
    };
    if rest.len() < 19 || !rest.is_char_boundary(19) {
        return None;
    }
    let (stamp, counter) = rest.split_at(19);
    if !(counter.is_empty() || counter.strip_prefix('-').is_some_and(|n| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()))) {
        return None;
    }
    Some((local_stamp(stamp)?, kind))
}

/// Backups einer Welt, neueste zuerst.
pub fn list(dir: &Path) -> Vec<BackupFile> {
    let Ok(read) = std::fs::read_dir(dir) else { return Vec::new() };
    let mut out: Vec<BackupFile> = read
        .flatten()
        .filter_map(|entry| {
            let name = entry.file_name().to_str()?.to_owned();
            let (created, kind) = parse_name(&name)?;
            let meta = entry.metadata().ok().filter(std::fs::Metadata::is_file)?;
            Some(BackupFile { id: name, path: entry.path(), created, kind, size: meta.len() })
        })
        .collect();
    out.sort_by(|a, b| b.created.cmp(&a.created).then_with(|| b.id.cmp(&a.id)));
    out
}

/// Ältere Sicherungen einer Welt aus `<spiel>/backups` (`<Ordner>_<Datum>.zip`, evtl. „ (2)“).
pub fn legacy(game_dir: &Path, folder: &str) -> Vec<BackupFile> {
    let dir = game_dir.join(LEGACY_DIR);
    let Ok(read) = std::fs::read_dir(&dir) else { return Vec::new() };
    let mut out: Vec<BackupFile> = read
        .flatten()
        .filter_map(|entry| {
            let name = entry.file_name().to_str()?.to_owned();
            let created = parse_legacy(&name, folder)?;
            let meta = entry.metadata().ok().filter(std::fs::Metadata::is_file)?;
            Some(BackupFile { id: format!("legacy:{name}"), path: entry.path(), created, kind: Kind::Legacy, size: meta.len() })
        })
        .collect();
    out.sort_by_key(|b| std::cmp::Reverse(b.created));
    out
}

fn parse_legacy(name: &str, folder: &str) -> Option<DateTime<Utc>> {
    let base = name.strip_suffix(".zip")?;
    // „ (2)“ von einem freien Namen am Ende abschneiden.
    let base = match base.rsplit_once(" (") {
        Some((head, tail)) if tail.strip_suffix(')').is_some_and(|n| n.bytes().all(|b| b.is_ascii_digit())) => head,
        _ => base,
    };
    let stamp = base.strip_prefix(folder)?.strip_prefix('_')?;
    if stamp.len() != 19 {
        return None;
    }
    local_stamp(stamp)
}

/// Ältere automatische Backups über `keep` hinaus löschen (manuelle und Sicherheits-Backups bleiben).
pub fn rotate(dir: &Path, keep: usize) -> Vec<PathBuf> {
    let mut removed = Vec::new();
    for old in list(dir).into_iter().filter(|b| b.kind == Kind::Auto).skip(keep.max(1)) {
        match std::fs::remove_file(&old.path) {
            Ok(()) => removed.push(old.path),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => tracing::warn!("Altes Backup {} konnte nicht gelöscht werden: {e}", old.path.display()),
        }
    }
    removed
}

/// Alle Welt-Ordner mit Backups: `(Scope, Weltordner, Pfad)`.
pub fn all_worlds(paths: &Paths) -> Vec<(String, String, PathBuf)> {
    let mut out = Vec::new();
    let Ok(scopes) = std::fs::read_dir(root(paths)) else { return out };
    for scope in scopes.flatten() {
        let Some(scope_name) = scope.file_name().to_str().map(str::to_owned) else { continue };
        if !scope.file_type().is_ok_and(|t| t.is_dir()) {
            continue;
        }
        let Ok(worlds) = std::fs::read_dir(scope.path()) else { continue };
        for world in worlds.flatten() {
            if world.file_type().is_ok_and(|t| t.is_dir())
                && let Some(folder) = world.file_name().to_str()
            {
                out.push((scope_name.clone(), folder.to_owned(), world.path()));
            }
        }
    }
    out
}

/// Gesamtgröße begrenzen: älteste automatische Backups zuerst – aber nie das neueste einer Welt.
/// Liefert die freigegebenen Bytes.
pub fn enforce_cap(paths: &Paths, max_bytes: u64) -> u64 {
    if max_bytes == 0 {
        return 0;
    }
    let mut candidates = Vec::new();
    let mut total = 0u64;
    for (_, _, dir) in all_worlds(paths) {
        let backups = list(&dir);
        total += backups.iter().map(|b| b.size).sum::<u64>();
        // Das neueste Backup jeder Welt bleibt immer.
        candidates.extend(backups.into_iter().skip(1).filter(|b| b.kind == Kind::Auto));
    }
    candidates.sort_by_key(|b| b.created);
    let mut freed = 0u64;
    for old in candidates {
        if total <= max_bytes {
            break;
        }
        if std::fs::remove_file(&old.path).is_ok() {
            total = total.saturating_sub(old.size);
            freed += old.size;
        }
    }
    freed
}

/// Reste abgebrochener Backups (`.<name>.part`) löschen – nur unter der Sperre des Scopes, wenn
/// sicher niemand mehr in diesen Ordner schreibt.
pub fn remove_stale_parts(dir: &Path) {
    let Ok(read) = std::fs::read_dir(dir) else { return };
    for entry in read.flatten() {
        if entry.file_name().to_str().is_some_and(|n| n.starts_with('.') && n.ends_with(".part")) {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

/// Leere Ordner einer Welt bzw. eines Scopes aufräumen.
pub fn remove_if_empty(dir: &Path) {
    if std::fs::read_dir(dir).is_ok_and(|mut d| d.next().is_none()) {
        let _ = std::fs::remove_dir(dir);
    }
}

// --- Eigene Einstellung je Instanz -------------------------------------------------------------------

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Config {
    /// Instanz → automatische Backups an/aus (fehlt = globale Einstellung).
    #[serde(default)]
    pub instances: BTreeMap<String, bool>,
}

pub async fn load_config(paths: &Paths) -> Config {
    fsutil::read_json(&root(paths).join(CONFIG)).await.ok().flatten().unwrap_or_default()
}

pub async fn save_config(paths: &Paths, config: &Config) -> Result<()> {
    fsutil::write_json(&root(paths).join(CONFIG), config).await
}

/// Instanz-ID als Ordnername prüfen (Scope).
pub fn check_scope(scope: &str) -> Result<()> {
    if scope == SHARED_SCOPE {
        return Ok(());
    }
    crate::instance::validate_id(scope)
}

/// Kennung eines Backups prüfen und auflösen.
pub fn resolve(paths: &Paths, scope: &str, game_dir: &Path, folder: &str, id: &str) -> Result<BackupFile> {
    let missing = || Error::validation(crate::msg!("worldBackups.notFound", "Dieses Backup gibt es nicht mehr."));
    let (dir, name, legacy) = match id.strip_prefix("legacy:") {
        Some(name) => (game_dir.join(LEGACY_DIR), name, true),
        None => (world_dir(paths, scope, folder), id, false),
    };
    crate::instance_files::validate_name(name).map_err(|_| missing())?;
    let found = if legacy { legacy_list_one(&dir, folder, name) } else { list(&dir).into_iter().find(|b| b.id == name) };
    found.ok_or_else(missing)
}

fn legacy_list_one(dir: &Path, folder: &str, name: &str) -> Option<BackupFile> {
    let created = parse_legacy(name, folder)?;
    let path = dir.join(name);
    let meta = std::fs::metadata(&path).ok().filter(std::fs::Metadata::is_file)?;
    Some(BackupFile { id: format!("legacy:{name}"), path, created, kind: Kind::Legacy, size: meta.len() })
}
