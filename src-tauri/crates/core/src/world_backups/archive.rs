//! ZIP-Archive der Welt-Backups.
//!
//! - **Fingerabdruck:** Pfad, Größe und Änderungszeit (ganze Sekunden) jeder Datei, als SHA-256.
//!   Gleicher Abdruck = nichts geändert, kein neues Backup.
//! - **Packen:** Datei für Datei gestreamt (nie eine ganze Welt im Speicher), unter `<Ordner>/…`.
//!   Region-Dateien schnell komprimiert, schon gepackte Formate (`.dat`, `.png` …) nur gespeichert.
//!   Jede Datei trägt ihre Änderungszeit (DOS-Zeit und sekundengenauer „Extended Timestamp“),
//!   das Archiv eine Beschreibung als Kommentar (Art, Welt, Name, Abdruck) – so ist jedes Backup
//!   für sich lesbar, ohne Verzeichnis, das kaputtgehen könnte.
//! - **Entpacken:** nur in einen frischen, leeren Ordner; jeder Pfad wird geprüft (kein `..`, keine
//!   absoluten Pfade oder Laufwerke, keine unter Windows ungültigen Namen, keine Links) – ein
//!   präpariertes Archiv kann nichts außerhalb des Ziels schreiben (Zip-Slip). Dateizeiten kommen
//!   wieder auf die Dateien, damit der Fingerabdruck nach dem Wiederherstellen passt.

use std::fs::File;
use std::io::{BufReader, BufWriter, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use chrono::{Datelike, Local, TimeZone, Timelike};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::lock::SESSION_LOCK;
use crate::{Error, Result};

/// Mehr Dateien hat keine echte Welt.
const MAX_FILES: usize = 2_000_000;
const MAX_DEPTH: usize = 64;
const BUFFER: usize = 256 * 1024;
/// Kennung des „Extended Timestamp“ (Unix-Sekunden je Datei).
const EXTENDED_TIMESTAMP: u16 = 0x5455;
/// Beschreibung im Archiv-Kommentar – klein halten.
const MAX_COMMENT: usize = 8 * 1024;
/// So viel darf ein Archiv beim Entpacken höchstens ergeben (Schutz vor „Zip-Bomben“).
const MAX_UNPACKED: u64 = 256 << 30;

/// Art des Backups.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    /// Vor dem Start bzw. nach dem Spielen – wird rotiert.
    Auto,
    /// „Jetzt sichern“ – bleibt, bis man es löscht.
    Manual,
    /// Stand vor dem Ersetzen durch ein Backup – bleibt ebenfalls.
    Safety,
    /// Ältere Sicherung aus `<Spielordner>/backups` (vor den automatischen Backups).
    Legacy,
}

impl Kind {
    pub fn code(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::Manual => "manual",
            Self::Safety => "safety",
            Self::Legacy => "legacy",
        }
    }
}

/// Beschreibung im Archiv-Kommentar.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Meta {
    /// Format (1).
    pub cubiq: u32,
    pub kind: Kind,
    /// Ordnername der Welt in `saves/`.
    pub world: String,
    /// Name aus `level.dat`.
    #[serde(default)]
    pub name: String,
    /// Fingerabdruck der gesicherten Dateien.
    #[serde(default)]
    pub fp: String,
    #[serde(default)]
    pub files: u64,
    /// Summe der Dateigrößen (entpackt).
    #[serde(default)]
    pub bytes: u64,
    /// Unix-Sekunden.
    #[serde(default)]
    pub created: i64,
    /// Spielversion, mit der die Welt zuletzt gespeichert wurde.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
}

/// Datei oder Ordner einer Welt.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Entry {
    pub path: PathBuf,
    /// Relativer Pfad mit `/`, ohne den Weltordner.
    pub rel: String,
    pub dir: bool,
    pub size: u64,
    /// Änderungszeit in Unix-Sekunden.
    pub mtime: i64,
}

fn unix_secs(time: SystemTime) -> i64 {
    match time.duration_since(UNIX_EPOCH) {
        Ok(d) => i64::try_from(d.as_secs()).unwrap_or(i64::MAX),
        Err(e) => -i64::try_from(e.duration().as_secs()).unwrap_or(i64::MAX),
    }
}

fn too_large() -> Error {
    Error::validation(crate::msg!("worldBackups.tooManyFiles", "Diese Welt hat zu viele Dateien für ein Backup."))
}

/// Alle Dateien und Ordner der Welt (ohne `session.lock` und Links), nach Pfad sortiert.
pub fn scan(world: &Path) -> Result<Vec<Entry>> {
    let mut out = Vec::new();
    let mut stack = vec![(world.to_path_buf(), String::new(), 0usize)];
    while let Some((dir, prefix, depth)) = stack.pop() {
        if depth > MAX_DEPTH {
            return Err(too_large());
        }
        let read = std::fs::read_dir(&dir).map_err(|e| Error::io(&dir, e))?;
        for entry in read {
            let entry = entry.map_err(|e| Error::io(&dir, e))?;
            let Ok(kind) = entry.file_type() else { continue };
            let Some(name) = entry.file_name().to_str().map(str::to_owned) else {
                tracing::warn!("Datei mit unlesbarem Namen in {} übersprungen", dir.display());
                continue;
            };
            if kind.is_symlink() || (depth == 0 && name == SESSION_LOCK) {
                continue;
            }
            let rel = if prefix.is_empty() { name } else { format!("{prefix}/{name}") };
            let meta = entry.metadata().map_err(|e| Error::io(entry.path(), e))?;
            let mtime = meta.modified().map(unix_secs).unwrap_or(0);
            if kind.is_dir() {
                out.push(Entry { path: entry.path(), rel: rel.clone(), dir: true, size: 0, mtime });
                stack.push((entry.path(), rel, depth + 1));
            } else if kind.is_file() {
                out.push(Entry { path: entry.path(), rel, dir: false, size: meta.len(), mtime });
            }
            if out.len() > MAX_FILES {
                return Err(too_large());
            }
        }
    }
    out.sort_by(|a, b| a.rel.cmp(&b.rel));
    Ok(out)
}

/// Fingerabdruck über Pfad, Größe und Änderungszeit aller Dateien.
pub fn fingerprint(entries: &[Entry]) -> String {
    let mut hash = Sha256::new();
    for e in entries.iter().filter(|e| !e.dir) {
        hash.update(format!("{}\t{}\t{}\n", e.rel, e.size, e.mtime).as_bytes());
    }
    hash.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// Summe der Dateigrößen.
pub fn total_bytes(entries: &[Entry]) -> u64 {
    entries.iter().map(|e| e.size).sum()
}

/// Region- und Chunk-Dateien: schon gepackte Chunks in 4-KB-Sektoren – schnell packen lohnt (Lücken).
fn is_region(name: &str) -> bool {
    [".mca", ".mcr", ".mcc", ".mcf"].iter().any(|ext| name.ends_with(ext))
}

/// Schon komprimierte Formate: nur speichern (spart Zeit, kaum Platz).
fn is_compressed(name: &str) -> bool {
    [".dat", ".dat_old", ".nbt", ".png", ".jpg", ".jpeg", ".gz", ".zip", ".jar", ".ogg"].iter().any(|ext| name.ends_with(ext))
}

/// DOS-Zeit (Ortszeit, 2-Sekunden-Raster) für die Kopfdaten.
fn dos_time(secs: i64) -> zip::DateTime {
    let local = Local.timestamp_opt(secs, 0).single();
    local
        .and_then(|t| {
            zip::DateTime::from_date_and_time(
                u16::try_from(t.year()).ok()?,
                t.month() as u8,
                t.day() as u8,
                t.hour() as u8,
                t.minute() as u8,
                t.second() as u8,
            )
            .ok()
        })
        .unwrap_or_default()
}

fn options_for(entry: &Entry) -> Result<zip::write::FullFileOptions<'static>> {
    let name = entry.rel.to_ascii_lowercase();
    let (method, level) = if entry.dir || is_compressed(&name) {
        (zip::CompressionMethod::Stored, None)
    } else if is_region(&name) {
        (zip::CompressionMethod::Deflated, Some(1))
    } else {
        (zip::CompressionMethod::Deflated, None)
    };
    let mut options = zip::write::FullFileOptions::default()
        .compression_method(method)
        .compression_level(level)
        .last_modified_time(dos_time(entry.mtime))
        .large_file(entry.size >= 0xFFFF_0000);
    // Sekundengenaue Zeit (Unix, UTC) – davon lebt der Fingerabdruck nach dem Wiederherstellen.
    let mut stamp = vec![1u8];
    stamp.extend_from_slice(&u32::try_from(entry.mtime.max(0)).unwrap_or(u32::MAX).to_le_bytes());
    options.add_extra_data(EXTENDED_TIMESTAMP, stamp, false).map_err(|e| Error::Internal(e.to_string()))?;
    Ok(options)
}

fn zip_err(dest: &Path, e: zip::result::ZipError) -> Error {
    match e {
        zip::result::ZipError::Io(io) => Error::io(dest, io),
        other => Error::Internal(other.to_string()),
    }
}

/// Packt `entries` der Welt unter `<folder>/…` nach `dest`. `check` läuft vor jeder Datei (Abbruch,
/// Welt inzwischen geöffnet); `progress` bekommt die gepackten Bytes. Liefert die Beschreibung mit
/// dem Fingerabdruck der wirklich gepackten Dateien.
pub fn write(
    folder: &str,
    entries: &[Entry],
    dest: &Path,
    mut meta: Meta,
    progress: &mut dyn FnMut(u64),
    check: &dyn Fn() -> Result<()>,
) -> Result<Meta> {
    let file = File::create(dest).map_err(|e| Error::io(dest, e))?;
    let mut zip = zip::ZipWriter::new(BufWriter::with_capacity(BUFFER, file));
    zip.add_directory(format!("{folder}/"), zip::write::SimpleFileOptions::default()).map_err(|e| zip_err(dest, e))?;
    let mut packed: Vec<Entry> = Vec::with_capacity(entries.len());
    let mut buf = vec![0u8; BUFFER];
    let mut done = 0u64;
    for entry in entries {
        check()?;
        let name = format!("{folder}/{}", entry.rel);
        if entry.dir {
            zip.add_directory(format!("{name}/"), options_for(entry)?).map_err(|e| zip_err(dest, e))?;
            packed.push(entry.clone());
            continue;
        }
        let mut input = match File::open(&entry.path) {
            Ok(f) => f,
            // Inzwischen gelöscht: gehört nicht mehr zur Welt.
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => continue,
            Err(e) => return Err(Error::io(&entry.path, e)),
        };
        // Größe und Zeit, wie sie jetzt wirklich gepackt werden.
        let now = input.metadata().map_err(|e| Error::io(&entry.path, e))?;
        let current = Entry { size: now.len(), mtime: now.modified().map(unix_secs).unwrap_or(entry.mtime), ..entry.clone() };
        zip.start_file(name, options_for(&current)?).map_err(|e| zip_err(dest, e))?;
        loop {
            let read = input.read(&mut buf).map_err(|e| Error::io(&entry.path, e))?;
            if read == 0 {
                break;
            }
            zip.write_all(&buf[..read]).map_err(|e| Error::io(dest, e))?;
            done += read as u64;
            progress(done);
        }
        packed.push(current);
    }
    check()?;
    meta.fp = fingerprint(&packed);
    meta.files = packed.iter().filter(|e| !e.dir).count() as u64;
    meta.bytes = total_bytes(&packed);
    let comment = serde_json::to_string(&meta).map_err(|e| Error::json("backup comment", e))?;
    if comment.len() > MAX_COMMENT {
        return Err(Error::Internal("Backup-Beschreibung zu lang".into()));
    }
    zip.set_comment(comment).map_err(|e| zip_err(dest, e))?;
    let writer = zip.finish().map_err(|e| zip_err(dest, e))?;
    let file = writer.into_inner().map_err(|e| Error::io(dest, e.into_error()))?;
    file.sync_all().map_err(|e| Error::io(dest, e))?;
    Ok(meta)
}

/// Beschreibung aus dem Archiv-Kommentar – liest nur das Ende der Datei, nicht das Verzeichnis.
pub fn read_meta(path: &Path) -> Option<Meta> {
    let mut file = File::open(path).ok()?;
    let len = file.metadata().ok()?.len();
    // Ende des Archivs: 22 Bytes plus höchstens 64 KB Kommentar.
    let tail = len.min(22 + 65_535);
    file.seek(SeekFrom::Start(len - tail)).ok()?;
    let mut buf = Vec::with_capacity(tail as usize);
    file.take(tail).read_to_end(&mut buf).ok()?;
    let mut at = buf.len().checked_sub(22)?;
    loop {
        if buf[at..].starts_with(&[0x50, 0x4b, 0x05, 0x06]) {
            let comment_len = usize::from(u16::from_le_bytes([buf[at + 20], buf[at + 21]]));
            if at + 22 + comment_len == buf.len() {
                let comment = &buf[at + 22..];
                return serde_json::from_slice::<Meta>(comment).ok().filter(|m| m.cubiq >= 1);
            }
        }
        at = at.checked_sub(1)?;
    }
}

/// Geprüfte Teile des Pfads eines Archiv-Eintrags (`/` und `\` trennen). Fehler bei allem, was
/// aus dem Ziel herausführen oder unter Windows etwas anderes bedeuten könnte.
pub fn safe_parts(name: &str) -> Result<Vec<String>> {
    let broken = || Error::validation(crate::msg!("worldBackups.unsafeArchive", "Das Backup enthält ungültige Pfade und wird nicht entpackt."));
    if name.contains('\0') {
        return Err(broken());
    }
    let normalized = name.replace('\\', "/");
    if normalized.starts_with('/') {
        return Err(broken());
    }
    let parts: Vec<String> = normalized.split('/').filter(|p| !p.is_empty()).map(str::to_owned).collect();
    for part in &parts {
        // validate_name: kein „..“/„.“, keine Laufwerks-Doppelpunkte, keine Windows-Gerätenamen.
        if crate::instance_files::validate_name(part).is_err() {
            return Err(broken());
        }
    }
    Ok(parts)
}

/// Was beim Entpacken herauskam.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Extracted {
    /// Weltordner im Archiv.
    pub top: String,
    pub files: u64,
    pub bytes: u64,
}

/// Entpackt das Backup nach `target` (darf noch nicht existieren): der Inhalt des Weltordners im
/// Archiv landet direkt darin. Links werden nie angelegt, Dateizeiten wiederhergestellt.
pub fn extract(zip_path: &Path, target: &Path, progress: &mut dyn FnMut(u64, u64), check: &dyn Fn() -> Result<()>) -> Result<Extracted> {
    let broken = || Error::validation(crate::msg!("worldBackups.broken", "Dieses Backup ist beschädigt."));
    let file = File::open(zip_path).map_err(|e| Error::io(zip_path, e))?;
    let mut archive = zip::ZipArchive::new(BufReader::with_capacity(BUFFER, file)).map_err(|_| broken())?;
    if archive.len() > MAX_FILES + 1 {
        return Err(too_large());
    }
    // Erst alle Namen prüfen – ein einziger böser Pfad, und es wird gar nichts entpackt.
    let mut top: Option<String> = None;
    let mut total = 0u64;
    let mut plan = Vec::with_capacity(archive.len());
    for i in 0..archive.len() {
        let entry = archive.by_index_raw(i).map_err(|_| broken())?;
        let parts = safe_parts(entry.name())?;
        let Some((first, rest)) = parts.split_first() else { continue };
        match &top {
            None => top = Some(first.clone()),
            Some(t) if t != first => return Err(broken()),
            Some(_) => {}
        }
        // Eine Datei direkt oben (ohne Weltordner) gehört zu keinem Backup von uns.
        if rest.is_empty() && !entry.is_dir() {
            return Err(broken());
        }
        // Links (Unix-Modus) nie anlegen.
        let link = entry.unix_mode().is_some_and(|mode| mode & 0o170_000 == 0o120_000);
        if rest.is_empty() || link {
            continue;
        }
        total = total.saturating_add(entry.size());
        plan.push((i, rest.to_vec(), entry.is_dir()));
    }
    let top = top.ok_or_else(broken)?;
    if !plan.iter().any(|(_, _, dir)| !dir) {
        return Err(broken());
    }
    if total > MAX_UNPACKED {
        return Err(too_large());
    }

    std::fs::create_dir(target).map_err(|e| Error::io(target, e))?;
    let mut done = 0u64;
    let mut files = 0u64;
    let mut buf = vec![0u8; BUFFER];
    for (index, parts, is_dir) in plan {
        check()?;
        let path = parts.iter().fold(target.to_path_buf(), |p, part| p.join(part));
        // Doppelt gesichert: Der Pfad liegt im Ziel (Teile sind geprüft, Ordner legen nur wir an).
        if !path.starts_with(target) {
            return Err(broken());
        }
        if is_dir {
            std::fs::create_dir_all(&path).map_err(|e| Error::io(&path, e))?;
            continue;
        }
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| Error::io(parent, e))?;
        }
        let mut entry = archive.by_index(index).map_err(|_| broken())?;
        let declared = entry.size();
        let stamp = entry
            .extra_data_fields()
            .find_map(|f| match f {
                zip::ExtraField::ExtendedTimestamp(ts) => ts.mod_time(),
                _ => None,
            })
            .map(|secs| UNIX_EPOCH + Duration::from_secs(u64::from(secs)))
            .or_else(|| entry.last_modified().and_then(dos_to_system));
        let mut out = File::create_new(&path).map_err(|e| Error::io(&path, e))?;
        let mut written = 0u64;
        let mut limited = (&mut entry).take(declared.saturating_add(1));
        loop {
            // Lesefehler (CRC, kaputte Daten) = kaputtes Backup.
            let read = limited.read(&mut buf).map_err(|_| broken())?;
            if read == 0 {
                break;
            }
            out.write_all(&buf[..read]).map_err(|e| Error::io(&path, e))?;
            written += read as u64;
            done += read as u64;
            progress(done, total);
        }
        if written != declared {
            return Err(broken());
        }
        if let Some(time) = stamp {
            let _ = out.set_modified(time);
        }
        files += 1;
    }
    Ok(Extracted { top, files, bytes: done })
}

fn dos_to_system(t: zip::DateTime) -> Option<SystemTime> {
    let local = Local
        .with_ymd_and_hms(i32::from(t.year()), u32::from(t.month()), u32::from(t.day()), u32::from(t.hour()), u32::from(t.minute()), u32::from(t.second()))
        .earliest()?;
    let secs = u64::try_from(local.timestamp()).ok()?;
    Some(UNIX_EPOCH + Duration::from_secs(secs))
}
