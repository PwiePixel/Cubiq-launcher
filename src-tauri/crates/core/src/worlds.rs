//! Welten einer Instanz (Tab „Welten“): Liste mit Namen aus `level.dat`,
//! Spielmodus, Version und Größe, dazu Löschen in den Papierkorb. Backups und
//! Wiederherstellen: [`crate::world_backups`].

use std::io::Read;
use std::path::{Path, PathBuf};

use chrono::{DateTime, Utc};
use serde::Serialize;

use crate::instance::validate_id;
use crate::instance_files::validate_name;
use crate::nbt::{self, Tag};
use crate::paths::Paths;
use crate::{Error, Result};

const MAX_WORLDS: usize = 500;
/// `level.dat` ist gzip-komprimiert und normalerweise wenige KB groß.
const MAX_LEVEL_DAT_BYTES: u64 = 16 << 20;
const MAX_LEVEL_DAT_UNPACKED: u64 = 64 << 20;
/// Größenberechnung bricht bei so vielen Dateien ab (riesige Welten).
const MAX_SIZE_ENTRIES: usize = 400_000;

#[derive(Debug, Clone, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct WorldInfo {
    /// Ordnername in `saves/` – die ID für alle Aktionen.
    pub folder: String,
    /// Name aus `level.dat` (sonst der Ordnername).
    pub name: String,
    pub icon_path: Option<PathBuf>,
    pub last_played: Option<DateTime<Utc>>,
    /// `survival` | `creative` | `adventure` | `spectator`
    pub game_mode: Option<&'static str>,
    pub hardcore: bool,
    pub cheats: bool,
    /// Spielversion, mit der die Welt zuletzt gespeichert wurde (ab 1.9).
    pub version: Option<String>,
    /// Bytes; `None`, wenn die Welt zu groß zum Zählen ist.
    pub size: Option<u64>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct LevelSummary {
    pub name: Option<String>,
    pub last_played_ms: Option<i64>,
    pub game_mode: Option<&'static str>,
    pub hardcore: bool,
    pub cheats: bool,
    pub version: Option<String>,
}

fn clean(text: &str, max: usize) -> String {
    // §-Formatcodes (Weltnamen dürfen sie enthalten) und Steuerzeichen entfernen.
    let mut out = String::new();
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else if !c.is_control() {
            out.push(c);
        }
    }
    out.trim().chars().take(max).collect()
}

/// Liest die wichtigsten Felder aus einer (gzip-komprimierten) `level.dat`.
pub fn parse_level_dat(bytes: &[u8]) -> Result<LevelSummary> {
    let raw = if bytes.starts_with(&[0x1f, 0x8b]) {
        let mut out = Vec::new();
        flate2::read::GzDecoder::new(bytes)
            .take(MAX_LEVEL_DAT_UNPACKED)
            .read_to_end(&mut out)
            .map_err(|_| Error::validation(crate::msg!("worlds.levelDatBroken", "Die level.dat der Welt ist beschädigt.")))?;
        out
    } else {
        bytes.to_vec()
    };
    let root = nbt::read_root(&raw)?;
    let Some(Tag::Compound(data)) = nbt::get(&root, "Data") else {
        return Err(Error::validation(crate::msg!("worlds.levelDatBroken", "Die level.dat der Welt ist beschädigt.")));
    };
    let int = |key: &str| match nbt::get(data, key) {
        Some(Tag::Int(v)) => Some(i64::from(*v)),
        Some(Tag::Byte(v)) => Some(i64::from(*v)),
        Some(Tag::Short(v)) => Some(i64::from(*v)),
        Some(Tag::Long(v)) => Some(*v),
        _ => None,
    };
    let game_mode = int("GameType").and_then(|mode| match mode {
        0 => Some("survival"),
        1 => Some("creative"),
        2 => Some("adventure"),
        3 => Some("spectator"),
        _ => None,
    });
    let version = match nbt::get(data, "Version") {
        Some(Tag::Compound(v)) => nbt::get(v, "Name").and_then(Tag::as_str_lossy).map(|s| clean(&s, 40)),
        _ => None,
    }
    .filter(|s| !s.is_empty());
    Ok(LevelSummary {
        name: nbt::get(data, "LevelName").and_then(Tag::as_str_lossy).map(|s| clean(&s, 100)).filter(|s| !s.is_empty()),
        last_played_ms: int("LastPlayed").filter(|&ms| ms > 0),
        game_mode,
        hardcore: int("hardcore").is_some_and(|v| v != 0),
        cheats: int("allowCommands").is_some_and(|v| v != 0),
        version,
    })
}

fn saves_dir(paths: &Paths, instance_id: &str) -> PathBuf {
    paths.instance_game_dir(instance_id).join("saves")
}

/// Geprüfter Ordner einer Welt (kein Symlink, liegt direkt in `saves/`).
pub fn world_dir(paths: &Paths, instance_id: &str, folder: &str) -> Result<PathBuf> {
    validate_id(instance_id)?;
    validate_name(folder)?;
    let dir = saves_dir(paths, instance_id).join(folder);
    match std::fs::symlink_metadata(&dir) {
        Ok(meta) if meta.is_dir() && !meta.file_type().is_symlink() => Ok(dir),
        _ => Err(Error::validation(crate::msg!("worlds.notFound", "Diese Welt gibt es nicht mehr."))),
    }
}

/// Summe aller Dateigrößen (ohne Symlinks); `None` bei zu vielen Dateien.
fn dir_size(dir: &Path) -> Option<u64> {
    let mut total = 0u64;
    let mut seen = 0usize;
    let mut stack = vec![dir.to_path_buf()];
    while let Some(current) = stack.pop() {
        let Ok(read) = std::fs::read_dir(&current) else { continue };
        for entry in read.flatten() {
            seen += 1;
            if seen > MAX_SIZE_ENTRIES {
                return None;
            }
            let Ok(kind) = entry.file_type() else { continue };
            if kind.is_dir() {
                stack.push(entry.path());
            } else if kind.is_file() {
                total += entry.metadata().map(|m| m.len()).unwrap_or(0);
            }
        }
    }
    Some(total)
}

fn read_world(dir: &Path, folder: String) -> Option<WorldInfo> {
    let level = dir.join("level.dat");
    let meta = std::fs::metadata(&level).ok()?;
    let summary = if meta.len() <= MAX_LEVEL_DAT_BYTES {
        std::fs::read(&level).ok().and_then(|bytes| parse_level_dat(&bytes).ok()).unwrap_or_default()
    } else {
        LevelSummary::default()
    };
    let icon = dir.join("icon.png");
    let last_played = summary
        .last_played_ms
        .and_then(DateTime::<Utc>::from_timestamp_millis)
        .or_else(|| meta.modified().ok().map(DateTime::<Utc>::from));
    Some(WorldInfo {
        name: summary.name.unwrap_or_else(|| folder.clone()),
        folder,
        icon_path: icon.is_file().then_some(icon),
        last_played,
        game_mode: summary.game_mode,
        hardcore: summary.hardcore,
        cheats: summary.cheats,
        version: summary.version,
        size: dir_size(dir),
    })
}

/// Alle Welten, zuletzt gespielte zuerst.
pub async fn list(paths: &Paths, instance_id: &str) -> Result<Vec<WorldInfo>> {
    validate_id(instance_id)?;
    let dir = saves_dir(paths, instance_id);
    tokio::task::spawn_blocking(move || {
        let read = match std::fs::read_dir(&dir) {
            Ok(read) => read,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
            Err(e) => return Err(Error::io(&dir, e)),
        };
        let mut worlds = Vec::new();
        for entry in read.flatten() {
            let Ok(kind) = entry.file_type() else { continue };
            let Some(folder) = entry.file_name().to_str().map(str::to_owned) else { continue };
            // `.cubiq-…`: Zwischenordner beim Wiederherstellen eines Backups.
            if !kind.is_dir() || validate_name(&folder).is_err() || folder.starts_with(".cubiq-") {
                continue;
            }
            // Nur echte Welten – in `saves` liegt gelegentlich auch anderes.
            if let Some(world) = read_world(&entry.path(), folder) {
                worlds.push(world);
            }
            if worlds.len() >= MAX_WORLDS {
                break;
            }
        }
        worlds.sort_by_key(|w| std::cmp::Reverse(w.last_played));
        Ok(worlds)
    })
    .await
    .map_err(|e| Error::Internal(e.to_string()))?
}

/// Welt in den Papierkorb legen.
pub async fn trash(paths: &Paths, instance_id: &str, folder: &str) -> Result<()> {
    let dir = world_dir(paths, instance_id, folder)?;
    tokio::task::spawn_blocking(move || crate::platform::move_to_trash(&dir))
        .await
        .map_err(|e| Error::Internal(e.to_string()))?
}

#[cfg(test)]
mod tests {
    use std::io::Write;

    use super::*;

    /// Baut eine kleine level.dat wie Minecraft (gzip, Root → Data).
    fn level_dat(name: &str, mode: i32, hardcore: bool, version: Option<&str>) -> Vec<u8> {
        let mut data = vec![
            (b"LevelName".to_vec(), Tag::string(name)),
            (b"GameType".to_vec(), Tag::Int(mode)),
            (b"hardcore".to_vec(), Tag::Byte(i8::from(hardcore))),
            (b"allowCommands".to_vec(), Tag::Byte(1)),
            (b"LastPlayed".to_vec(), Tag::Long(1_758_000_000_000)),
        ];
        if let Some(v) = version {
            data.push((b"Version".to_vec(), Tag::Compound(vec![(b"Name".to_vec(), Tag::string(v)), (b"Id".to_vec(), Tag::Int(3955))])));
        }
        let raw = nbt::write_root(&[(b"Data".to_vec(), Tag::Compound(data))]);
        let mut gz = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
        gz.write_all(&raw).unwrap();
        gz.finish().unwrap()
    }

    #[test]
    fn parses_level_dat() {
        let summary = parse_level_dat(&level_dat("§6Meine §lWelt", 1, false, Some("1.21.1"))).unwrap();
        assert_eq!(summary.name.as_deref(), Some("Meine Welt"));
        assert_eq!(summary.game_mode, Some("creative"));
        assert!(!summary.hardcore);
        assert!(summary.cheats);
        assert_eq!(summary.version.as_deref(), Some("1.21.1"));
        assert_eq!(summary.last_played_ms, Some(1_758_000_000_000));

        let old = parse_level_dat(&level_dat("Alt", 0, true, None)).unwrap();
        assert_eq!((old.game_mode, old.hardcore, old.version), (Some("survival"), true, None));

        assert!(parse_level_dat(b"\x1f\x8bkaputt").is_err());
        assert!(parse_level_dat(&nbt::write_root(&[(b"Ohne".to_vec(), Tag::Int(1))])).is_err());
    }

    #[tokio::test]
    async fn lists_and_validates() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path());
        let saves = paths.instance_game_dir("test").join("saves");
        std::fs::create_dir_all(saves.join("welt1/region")).unwrap();
        std::fs::create_dir_all(saves.join("kein-level")).unwrap();
        std::fs::write(saves.join("welt1/level.dat"), level_dat("Burg", 0, false, Some("1.20.4"))).unwrap();
        std::fs::write(saves.join("welt1/region/r.0.0.mca"), vec![7u8; 5000]).unwrap();
        std::fs::write(saves.join("welt1/session.lock"), b"x").unwrap();
        std::fs::write(saves.join("welt1/icon.png"), b"png").unwrap();
        std::fs::create_dir_all(saves.join("kaputt")).unwrap();
        std::fs::write(saves.join("kaputt/level.dat"), b"murks").unwrap();
        // Zwischenordner beim Wiederherstellen eines Backups erscheint nie als Welt.
        std::fs::create_dir_all(saves.join(".cubiq-restore-1")).unwrap();
        std::fs::write(saves.join(".cubiq-restore-1/level.dat"), level_dat("Halb", 0, false, None)).unwrap();

        let worlds = list(&paths, "test").await.unwrap();
        assert_eq!(worlds.len(), 2);
        let burg = worlds.iter().find(|w| w.folder == "welt1").unwrap();
        assert_eq!(burg.name, "Burg");
        assert_eq!(burg.game_mode, Some("survival"));
        assert!(burg.icon_path.is_some());
        assert!(burg.size.unwrap() >= 5000);
        // Kaputte level.dat: trotzdem gelistet, Name = Ordner.
        let broken = worlds.iter().find(|w| w.folder == "kaputt").unwrap();
        assert_eq!((broken.name.as_str(), broken.game_mode), ("kaputt", None));

        for bad in ["..", "../welt1", "welt1/region", "", "fehlt"] {
            assert!(world_dir(&paths, "test", bad).is_err(), "{bad:?}");
        }
        assert!(world_dir(&paths, "../x", "welt1").is_err());
        assert!(list(&paths, "ohne-saves").await.unwrap().is_empty());
    }
}
