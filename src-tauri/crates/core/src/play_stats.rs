//! Spielzeit-Statistik (Seite „Statistik“ und Übersicht im Verlauf einer Instanz).
//!
//! Jede Spielsitzung landet kompakt in `<daten>/play-stats.json`: Instanz, Minecraft-Version,
//! Loader, Start und Ende, die Server (Schnellbeitritt oder „Connecting to …“ im Log) und ob das
//! Spiel abgestürzt ist. Tage, Wochen, Serien und Bestenlisten rechnet die Oberfläche daraus
//! (`app/utils/playStats.ts`) – sie kennt Zeitzone und Wochenbeginn der eingestellten Sprache.
//!
//! Robust gegen Abstürze:
//! - Eine Sitzung steht schon beim Spielstart als „offen“ in der Datei. Endet das Spiel, während
//!   der Launcher nicht läuft (Launcher-Absturz, PC aus), schließt der nächste Start sie mit dem
//!   letzten Schreibzeitpunkt ihrer Log-Dateien – so nah am echten Ende wie möglich – und bucht
//!   die Zeit auch auf die Instanz (das ging bisher verloren).
//! - Geschrieben wird atomar (Temp-Datei, fsync, Umbenennen); die vorige Fassung bleibt als
//!   `.bak` und springt ein, wenn die Hauptdatei fehlt oder kaputt ist. Eine kaputte Datei wird
//!   nie überschrieben, sondern daneben aufgehoben.
//! - Höchstens [`MAX_SESSIONS`] Sitzungen: Die ältesten gehen mit ihrer Zeit in den Übertrag
//!   ihrer Instanz – Summen und Bestenlisten bleiben stimmig.
//!
//! Bisherige Spielzeit zählt mit: Beim ersten Start kommen die Sitzungen aus dem Verlauf der
//! Instanzen (`history.json`, mit Datum und der damaligen Version). Was eine Instanz darüber
//! hinaus an Spielzeit kennt (ältere Einträge, Zeit vor dem Verlauf), steht als Übertrag ohne
//! Datum in den Summen. Instanzen, die die Statistik erst später sieht, übernimmt sie beim ersten
//! Kontakt genauso.

use std::collections::{BTreeMap, BTreeSet, HashSet};
use std::io::Write;
use std::path::{Path, PathBuf};

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;

use crate::history::{HistoryEntry, HistoryKind};
use crate::instance::{Instance, InstanceStore, LoaderKind, validate_id};
use crate::paths::Paths;
use crate::process::{GameEvent, RunningGame};
use crate::{Error, Launcher, Result};

/// Datei im Datenordner.
pub const FILE: &str = "play-stats.json";
/// Format der Datei (für spätere Umbauten).
const FORMAT: u32 = 1;
/// Höchstens so viele Sitzungen (gut 2 MB); ältere gehen in den Übertrag.
pub const MAX_SESSIONS: usize = 20_000;
/// Mehr offene Sitzungen gibt es nur durch Reste – die ältesten fallen weg.
const MAX_OPEN: usize = 32;
const MAX_SERVERS: usize = 8;
const MAX_NAME: usize = 64;
/// Ein geschätztes Ende liegt höchstens so weit nach dem Start (Uhr verstellt, uralte Reste).
const MAX_ESTIMATED_SECS: i64 = 7 * 24 * 3600;
/// Ende und Sitzung gehören zusammen, wenn ihre Starts so nah beieinander liegen. Beide kommen
/// aus derselben Prozess-Startzeit; `play_seconds` ist auf ganze Sekunden abgerundet.
const MATCH_SECS: i64 = 2;

/// Lesen, Ändern und Schreiben nacheinander – Einträge kommen aus vielen Tasks.
static LOCK: Mutex<()> = Mutex::const_new(());

// --- Datei ------------------------------------------------------------------------------------

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
struct StatsFile {
    #[serde(default)]
    v: u32,
    /// Seit wann aufgezeichnet wird (Unix-Sekunden).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    since: Option<i64>,
    /// Bisherige Spielzeit aller Instanzen übernommen (einmalig beim ersten Start).
    #[serde(default)]
    migrated: bool,
    /// Instanzen, deren bisherige Spielzeit übernommen ist.
    #[serde(default)]
    known: BTreeSet<String>,
    /// Abgeschlossene Sitzungen, nach Start sortiert.
    #[serde(default)]
    sessions: Vec<Stored>,
    /// Laufende Sitzungen.
    #[serde(default)]
    open: Vec<Open>,
    /// Spielzeit ohne Datum je Instanz (vor der Statistik bzw. aus gekappten Sitzungen).
    #[serde(default)]
    carried: BTreeMap<String, u64>,
    /// Letzter bekannter Name je Instanz – auch gelöschter.
    #[serde(default)]
    names: BTreeMap<String, String>,
}

/// Abgeschlossene Sitzung. Kurze Schlüssel: Die Datei wächst mit jeder Sitzung.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
struct Stored {
    #[serde(rename = "i")]
    instance: String,
    #[serde(rename = "v", default, skip_serializing_if = "String::is_empty")]
    version: String,
    #[serde(rename = "l", default, skip_serializing_if = "String::is_empty")]
    loader: String,
    /// Unix-Sekunden.
    #[serde(rename = "s")]
    start: i64,
    #[serde(rename = "e")]
    end: i64,
    #[serde(rename = "sv", default, skip_serializing_if = "Vec::is_empty")]
    servers: Vec<String>,
    #[serde(rename = "c", default, skip_serializing_if = "is_false")]
    crashed: bool,
    /// Ende geschätzt (der Launcher lief beim Spielende nicht).
    #[serde(rename = "x", default, skip_serializing_if = "is_false")]
    estimated: bool,
    /// Aus dem Verlauf der Instanz übernommen.
    #[serde(rename = "h", default, skip_serializing_if = "is_false")]
    imported: bool,
    /// Prozess-Schlüssel (`<id>` bzw. `<id>~2`), solange die Sitzung frisch ist – nur zum Abgleich.
    #[serde(rename = "k", default, skip_serializing_if = "String::is_empty")]
    key: String,
}

impl Stored {
    fn seconds(&self) -> u64 {
        u64::try_from(self.end.saturating_sub(self.start)).unwrap_or(0)
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
struct Open {
    #[serde(rename = "k")]
    key: String,
    #[serde(rename = "i")]
    instance: String,
    #[serde(rename = "v", default)]
    version: String,
    #[serde(rename = "l", default)]
    loader: String,
    #[serde(rename = "s")]
    start: i64,
    #[serde(rename = "sv", default, skip_serializing_if = "Vec::is_empty")]
    servers: Vec<String>,
}

fn is_false(value: &bool) -> bool {
    !*value
}

fn file_path(paths: &Paths) -> PathBuf {
    paths.root().join(FILE)
}

fn backup_path(path: &Path) -> PathBuf {
    path.with_extension("json.bak")
}

/// `Ok(None)` = keine Datei; `Err` = da, aber nicht lesbar.
fn read_file(path: &Path) -> std::result::Result<Option<StatsFile>, String> {
    match std::fs::read(path) {
        Ok(bytes) => serde_json::from_slice(&bytes).map(Some).map_err(|e| e.to_string()),
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e.to_string()),
    }
}

/// Lädt die Statistik. Kaputt → die vorige Fassung (`.bak`); eine kaputte Hauptdatei wird
/// daneben aufgehoben statt beim nächsten Speichern überschrieben.
fn load_sync(path: &Path) -> StatsFile {
    match read_file(path) {
        Ok(Some(file)) => return file,
        Ok(None) => {}
        Err(e) => {
            tracing::warn!("{FILE} ist beschädigt ({e}) – nehme die vorige Fassung");
            let keep = path.with_extension(format!("broken-{}.json", Utc::now().timestamp()));
            if let Err(e) = std::fs::rename(path, &keep) {
                tracing::warn!("Beschädigte Statistik konnte nicht beiseitegelegt werden: {e}");
            }
        }
    }
    match read_file(&backup_path(path)) {
        Ok(Some(file)) => file,
        _ => StatsFile::default(),
    }
}

/// Schreibt atomar: Temp-Datei mit fsync, die bisherige Fassung wird zur `.bak`, dann
/// umbenennen. Fehlt die Hauptdatei nach einem Absturz genau dazwischen, liest
/// [`load_sync`] die `.bak`.
fn save_sync(path: &Path, file: &StatsFile) -> Result<()> {
    let bytes = serde_json::to_vec(file).map_err(|e| Error::json(FILE, e))?;
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| Error::io(parent, e))?;
    }
    let tmp = path.with_extension(format!("tmp-{}", uuid::Uuid::new_v4().simple()));
    let written = (|| -> std::io::Result<()> {
        let mut out = std::fs::File::create(&tmp)?;
        out.write_all(&bytes)?;
        out.sync_all()
    })();
    if let Err(e) = written {
        let _ = std::fs::remove_file(&tmp);
        return Err(Error::io(&tmp, e));
    }
    if path.exists()
        && let Err(e) = std::fs::rename(path, backup_path(path))
    {
        tracing::debug!("Vorige Statistik nicht als .bak gesichert: {e}");
    }
    if let Err(e) = std::fs::rename(&tmp, path) {
        let _ = std::fs::remove_file(&tmp);
        return Err(Error::io(path, e));
    }
    // Linux/macOS: auch die Umbenennung selbst auf die Platte bringen.
    #[cfg(unix)]
    if let Some(parent) = path.parent()
        && let Ok(dir) = std::fs::File::open(parent)
    {
        let _ = dir.sync_all();
    }
    Ok(())
}

/// Liest, ändert und schreibt die Datei (nur, wenn sich etwas geändert hat).
async fn update<R: Send + 'static>(paths: &Paths, change: impl FnOnce(&mut StatsFile) -> R + Send + 'static) -> Result<R> {
    let _guard = LOCK.lock().await;
    let path = file_path(paths);
    tokio::task::spawn_blocking(move || {
        let mut file = load_sync(&path);
        let before = file.clone();
        let out = change(&mut file);
        if file != before {
            file.v = FORMAT;
            save_sync(&path, &file)?;
        }
        Ok(out)
    })
    .await
    .map_err(|e| Error::Internal(e.to_string()))?
}

/// Nur lesen.
async fn read(paths: &Paths) -> Result<StatsFile> {
    let _guard = LOCK.lock().await;
    let path = file_path(paths);
    tokio::task::spawn_blocking(move || load_sync(&path)).await.map_err(|e| Error::Internal(e.to_string()))
}

// --- Bausteine (ohne Ein-/Ausgabe, damit testbar) -------------------------------------------------

/// Was zu einer Sitzung gehört, bevor sie endet.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SessionMeta {
    pub instance_id: String,
    pub name: String,
    pub game_version: String,
    /// Wie vom Spieler gewählt (Vanilla bleibt Vanilla, auch mit Cubiq-Optimierung).
    pub loader: LoaderKind,
}

impl SessionMeta {
    pub fn of(instance: &Instance) -> Self {
        Self {
            instance_id: instance.id.clone(),
            name: instance.name.clone(),
            game_version: instance.game_version.clone(),
            loader: instance.loader.kind,
        }
    }
}

fn clean(text: &str, max: usize) -> String {
    text.trim().chars().filter(|c| !c.is_control()).take(max).collect()
}

/// Neue Sitzung an ihrer Stelle (nach Start sortiert) einfügen.
fn insert_sorted(file: &mut StatsFile, session: Stored) {
    let at = file.sessions.partition_point(|s| s.start <= session.start);
    file.sessions.insert(at, session);
}

/// Höchstens [`MAX_SESSIONS`]: die ältesten mit ihrer Zeit in den Übertrag.
fn cap(file: &mut StatsFile) {
    let over = file.sessions.len().saturating_sub(MAX_SESSIONS);
    for old in file.sessions.drain(..over) {
        *file.carried.entry(old.instance.clone()).or_default() += old.seconds();
    }
    // Schlüssel braucht nur, wer noch zu einem Ende passen könnte (die jüngsten Sitzungen).
    let keep_from = file.sessions.len().saturating_sub(64);
    for s in &mut file.sessions[..keep_from] {
        s.key.clear();
    }
}

fn set_name(file: &mut StatsFile, instance: &str, name: &str) {
    let name = clean(name, MAX_NAME);
    if !name.is_empty() {
        file.names.insert(instance.to_owned(), name);
    }
}

fn tracked_seconds(file: &StatsFile, instance: &str) -> u64 {
    file.sessions.iter().filter(|s| s.instance == instance).map(Stored::seconds).sum()
}

/// Bisherige Spielzeit einer Instanz übernehmen, die die Statistik noch nicht kennt: was sie mehr
/// hat als die aufgezeichneten Sitzungen, wird Übertrag ohne Datum.
fn account(file: &mut StatsFile, instance: &Instance) {
    if file.known.contains(&instance.id) {
        return;
    }
    let extra = instance.total_play_seconds.saturating_sub(tracked_seconds(file, &instance.id));
    if extra > 0 {
        *file.carried.entry(instance.id.clone()).or_default() += extra;
    }
    file.known.insert(instance.id.clone());
    set_name(file, &instance.id, &instance.name);
}

/// Version und Loader aus einem Verlaufstext wie `1.21.1 Fabric 0.16.10` (siehe `describe_version`).
fn parse_described(text: &str) -> Option<(String, String)> {
    let parts: Vec<&str> = text.split_whitespace().collect();
    let at = parts.iter().rposition(|p| matches!(*p, "Vanilla" | "Fabric" | "Quilt" | "Forge" | "NeoForge"))?;
    if at == 0 {
        return None;
    }
    Some((parts[..at].join(" "), parts[at].to_ascii_lowercase()))
}

/// Einmalig: Sitzungen aus dem Verlauf jeder Instanz (mit der damaligen Version) und der Rest der
/// bisherigen Spielzeit als Übertrag.
fn migrate(file: &mut StatsFile, instances: &[(Instance, Vec<HistoryEntry>)], now: i64) {
    if file.migrated {
        return;
    }
    for (instance, history) in instances {
        if file.known.contains(&instance.id) {
            continue;
        }
        // Verlauf: neueste zuerst. Rückwärts gelesen gilt vor einem Versionswechsel dessen `from`.
        let mut version = instance.game_version.clone();
        let mut loader = crate::loader_name(instance.loader.kind).to_owned();
        for entry in history {
            match entry.kind {
                HistoryKind::Stopped | HistoryKind::Crashed => {
                    let seconds = entry.seconds.unwrap_or(0).min(MAX_ESTIMATED_SECS as u64);
                    if seconds == 0 {
                        continue;
                    }
                    let end = entry.at.timestamp();
                    insert_sorted(
                        file,
                        Stored {
                            instance: instance.id.clone(),
                            version: version.clone(),
                            loader: loader.clone(),
                            start: end - seconds as i64,
                            end,
                            servers: Vec::new(),
                            crashed: entry.kind == HistoryKind::Crashed,
                            estimated: false,
                            imported: true,
                            key: String::new(),
                        },
                    );
                }
                HistoryKind::VersionSwitched => {
                    if let Some((v, l)) = entry.from.as_deref().and_then(parse_described) {
                        version = v;
                        loader = l;
                    }
                }
                _ => {}
            }
        }
        account(file, instance);
    }
    file.migrated = true;
    file.since.get_or_insert(now);
    cap(file);
}

/// Spiel gestartet. Hat das Ende die Sitzung schon gebucht (Spiel sofort wieder zu), kommt nur
/// noch der Server dazu.
fn start(file: &mut StatsFile, key: &str, meta: &SessionMeta, start: i64, server: Option<&str>, instance: Option<&Instance>) {
    if let Some(instance) = instance {
        account(file, instance);
    }
    set_name(file, &meta.instance_id, &meta.name);
    let servers: Vec<String> = server.and_then(normalize_server).into_iter().collect();
    if let Some(done) = file
        .sessions
        .iter_mut()
        .rev()
        .take(64)
        .find(|s| s.instance == meta.instance_id && s.key == key && (s.start - start).abs() <= MATCH_SECS)
    {
        for server in servers {
            add_server(&mut done.servers, server);
        }
        return;
    }
    file.open.retain(|o| o.key != key || o.instance != meta.instance_id || (o.start - start).abs() > MATCH_SECS);
    file.open.push(Open {
        key: key.to_owned(),
        instance: meta.instance_id.clone(),
        version: meta.game_version.clone(),
        loader: crate::loader_name(meta.loader).to_owned(),
        start,
        servers,
    });
    let over = file.open.len().saturating_sub(MAX_OPEN);
    file.open.drain(..over);
    file.since.get_or_insert(start);
}

/// Spiel beendet: offene Sitzung schließen. Ohne offene Sitzung (Spiel vom Handy, Start mit einer
/// älteren Version) entsteht sie aus `fallback`. War sie schon geschätzt geschlossen (Abgleich beim
/// Start), gelten jetzt die genauen Werte.
fn exit(file: &mut StatsFile, instance: &str, key: &str, play_seconds: u64, crashed: bool, now: i64, fallback: Option<&SessionMeta>) {
    let start = now - i64::try_from(play_seconds).unwrap_or(i64::MAX / 2);
    if let Some(at) = file.open.iter().position(|o| o.instance == instance && o.key == key && (o.start - start).abs() <= MATCH_SECS) {
        let open = file.open.remove(at);
        insert_sorted(
            file,
            Stored {
                instance: open.instance,
                version: open.version,
                loader: open.loader,
                start,
                end: now,
                servers: open.servers,
                crashed,
                estimated: false,
                imported: false,
                key: key.to_owned(),
            },
        );
        return;
    }
    if let Some(done) = file
        .sessions
        .iter_mut()
        .rev()
        .take(64)
        .find(|s| s.instance == instance && s.key == key && (s.start - start).abs() <= MATCH_SECS)
    {
        // Doppelt gemeldet oder beim Start geschätzt geschlossen: genaue Werte übernehmen.
        if done.estimated {
            done.end = now;
            done.crashed = crashed;
            done.estimated = false;
        }
        return;
    }
    if play_seconds == 0 {
        return;
    }
    let (version, loader) = match fallback {
        Some(meta) => {
            set_name(file, instance, &meta.name);
            (meta.game_version.clone(), crate::loader_name(meta.loader).to_owned())
        }
        None => (String::new(), String::new()),
    };
    insert_sorted(
        file,
        Stored {
            instance: instance.to_owned(),
            version,
            loader,
            start,
            end: now,
            servers: Vec::new(),
            crashed,
            estimated: false,
            imported: false,
            key: key.to_owned(),
        },
    );
    file.since.get_or_insert(start);
    cap(file);
}

/// Server einer laufenden Sitzung merken.
fn join_server(file: &mut StatsFile, instance: &str, key: &str, server: &str) {
    let Some(server) = normalize_server(server) else { return };
    if let Some(open) = file.open.iter_mut().rev().find(|o| o.instance == instance && o.key == key) {
        add_server(&mut open.servers, server);
    }
}

fn add_server(list: &mut Vec<String>, server: String) {
    if !list.contains(&server) && list.len() < MAX_SERVERS {
        list.push(server);
    }
}

/// Offene Sitzungen, deren Spiel nicht mehr läuft (Launcher-Absturz, PC aus): mit dem geschätzten
/// Ende schließen. Liefert die gebuchte Zeit je Instanz (fehlt dort bisher).
fn close_stale(file: &mut StatsFile, running: &HashSet<String>, now: i64, estimate: impl Fn(&str, &str) -> Option<i64>) -> Vec<(String, u64)> {
    let mut booked = Vec::new();
    let (stale, alive): (Vec<Open>, Vec<Open>) = std::mem::take(&mut file.open).into_iter().partition(|o| !running.contains(&o.key));
    file.open = alive;
    for open in stale {
        // Nie über den Beginn einer späteren Sitzung desselben Prozess-Schlüssels hinaus.
        let next = file.sessions.iter().filter(|s| s.instance == open.instance && s.start > open.start).map(|s| s.start).min();
        let Some(end) = estimate(&open.instance, &open.key)
            .map(|end| end.min(now).min(open.start + MAX_ESTIMATED_SECS).min(next.unwrap_or(i64::MAX)))
            .filter(|end| *end > open.start)
        else {
            continue;
        };
        let session = Stored {
            instance: open.instance,
            version: open.version,
            loader: open.loader,
            start: open.start,
            end,
            servers: open.servers,
            crashed: false,
            estimated: true,
            imported: false,
            key: open.key,
        };
        booked.push((session.instance.clone(), session.seconds()));
        insert_sorted(file, session);
    }
    cap(file);
    booked
}

/// Server-Adresse vereinheitlichen: klein, ohne Standard-Port 25565.
pub fn normalize_server(raw: &str) -> Option<String> {
    let text = raw.trim().trim_end_matches('.').to_ascii_lowercase();
    let host = text.strip_suffix(":25565").unwrap_or(&text);
    let ok = !host.is_empty()
        && host.len() <= 255
        && host.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | ':' | '[' | ']'));
    ok.then(|| host.to_owned())
}

/// Server aus der Log-Zeile beim Verbinden (`Connecting to mc.example.net, 25565`), wie sie
/// Minecraft seit Jahren schreibt – auch beim Beitritt über das Mehrspieler-Menü im Spiel.
/// Chat-Zeilen beginnen mit `[CHAT]` und zählen deshalb nie.
pub fn parse_connect(message: &str) -> Option<String> {
    let rest = message.trim().strip_prefix("Connecting to ")?;
    let (host, port) = rest.rsplit_once(", ")?;
    let port: u16 = port.trim().parse().ok()?;
    let host = normalize_server(host)?;
    Some(if port == 25565 { host } else { format!("{host}:{port}") })
}

/// Wann die Log-Dateien eines Spielprozesses zuletzt geschrieben wurden – das Spiel schreibt
/// beim Beenden zuletzt hinein. Weitere Prozesse (`<id>~2`) haben eigene Dateien.
fn log_end(paths: &Paths, instance: &str, key: &str) -> Option<i64> {
    validate_id(instance).ok()?;
    let mut dir = paths.instance_dir(instance).join("launcher-logs");
    let main = key == instance;
    if !main {
        let n = key.rsplit_once('~').map(|(_, n)| n).filter(|n| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit()))?;
        dir = dir.join(format!("extra-{n}"));
    }
    let mut files = vec![dir.join("launcher-stdout.log"), dir.join("launcher-stderr.log")];
    if main {
        files.push(paths.instance_game_dir(instance).join("logs").join("latest.log"));
    }
    files
        .iter()
        .filter_map(|f| std::fs::metadata(f).ok()?.modified().ok())
        .map(|t| DateTime::<Utc>::from(t).timestamp())
        .max()
}

// --- Für die Oberfläche ---------------------------------------------------------------------------

/// Eine Sitzung, wie sie die Oberfläche bekommt (Zeiten in Unix-Sekunden).
#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlaySession {
    pub instance_id: String,
    pub game_version: String,
    /// `vanilla`, `fabric`, …; leer = unbekannt.
    pub loader: String,
    pub start: i64,
    pub end: i64,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub servers: Vec<String>,
    #[serde(skip_serializing_if = "is_false")]
    pub crashed: bool,
    #[serde(skip_serializing_if = "is_false")]
    pub estimated: bool,
    #[serde(skip_serializing_if = "is_false")]
    pub imported: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CarriedTime {
    pub instance_id: String,
    pub seconds: u64,
}

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayStats {
    /// Abgeschlossene Sitzungen, älteste zuerst.
    pub sessions: Vec<PlaySession>,
    /// Gerade laufende Sitzungen (Ende = jetzt).
    pub running: Vec<PlaySession>,
    /// Spielzeit ohne Datum je Instanz.
    pub carried: Vec<CarriedTime>,
    /// Letzter bekannter Name je Instanz (auch gelöschter).
    pub names: BTreeMap<String, String>,
    /// Seit wann aufgezeichnet wird.
    pub tracking_since: Option<i64>,
    pub now: i64,
}

fn view(file: &StatsFile, running: &HashSet<String>, now: i64) -> PlayStats {
    let session = |s: &Stored| PlaySession {
        instance_id: s.instance.clone(),
        game_version: s.version.clone(),
        loader: s.loader.clone(),
        start: s.start,
        end: s.end.max(s.start),
        servers: s.servers.clone(),
        crashed: s.crashed,
        estimated: s.estimated,
        imported: s.imported,
    };
    PlayStats {
        sessions: file.sessions.iter().map(session).collect(),
        running: file
            .open
            .iter()
            .filter(|o| running.contains(&o.key))
            .map(|o| PlaySession {
                instance_id: o.instance.clone(),
                game_version: o.version.clone(),
                loader: o.loader.clone(),
                start: o.start,
                end: now.max(o.start),
                servers: o.servers.clone(),
                crashed: false,
                estimated: false,
                imported: false,
            })
            .collect(),
        carried: file
            .carried
            .iter()
            .filter(|(_, s)| **s > 0)
            .map(|(id, s)| CarriedTime { instance_id: id.clone(), seconds: *s })
            .collect(),
        names: file.names.clone(),
        tracking_since: file.since,
        now,
    }
}

// --- Öffentliche Schritte ----------------------------------------------------------------------------

async fn with_history(paths: &Paths, instances: Vec<Instance>) -> Vec<(Instance, Vec<HistoryEntry>)> {
    let mut out = Vec::with_capacity(instances.len());
    for instance in instances {
        let history = crate::history::list(paths, &instance.id).await.unwrap_or_default();
        out.push((instance, history));
    }
    out
}

/// Launcher-Start (nach dem Wiederfinden laufender Spiele): bisherige Spielzeit einmalig
/// übernehmen, liegengebliebene Sitzungen schließen und deren Zeit auf die Instanz buchen.
pub async fn startup(paths: &Paths, running: &[RunningGame]) -> Result<()> {
    let store = InstanceStore::new(paths.clone());
    let pending = !read(paths).await?.migrated;
    let instances = if pending { with_history(paths, store.list().await.unwrap_or_default()).await } else { Vec::new() };
    let running: HashSet<String> = running.iter().map(|g| g.key.clone()).collect();
    let now = Utc::now().timestamp();
    let estimate_paths = paths.clone();
    let booked = update(paths, move |file| {
        migrate(file, &instances, now);
        close_stale(file, &running, now, |instance, key| log_end(&estimate_paths, instance, key))
    })
    .await?;
    for (instance, seconds) in booked {
        tracing::info!("Spielzeit einer liegengebliebenen Sitzung von '{instance}' nachgetragen: {seconds} s");
        if seconds > 0
            && let Err(e) = store.add_play_time(&instance, seconds).await
        {
            tracing::warn!("Spielzeit für '{instance}' konnte nicht nachgetragen werden: {e}");
        }
    }
    Ok(())
}

/// Spielstart: Sitzung öffnen. `started_at` ist die Startzeit des Prozesses (wie beim Ende).
pub async fn session_started(paths: &Paths, key: &str, instance: &Instance, meta: SessionMeta, started_at: i64, server: Option<String>) {
    let (key, instance) = (key.to_owned(), instance.clone());
    let result = update(paths, move |file| start(file, &key, &meta, started_at, server.as_deref(), Some(&instance))).await;
    if let Err(e) = result {
        tracing::warn!("Spielsitzung konnte nicht in der Statistik vermerkt werden: {e}");
    }
}

/// Spielende (aus dem `exited`-Ereignis).
pub async fn session_exited(paths: &Paths, instance_id: &str, key: &str, play_seconds: u64, crashed: bool) {
    let fallback = InstanceStore::new(paths.clone()).get(instance_id).await.ok().map(|i| SessionMeta::of(&i));
    let (instance, key) = (instance_id.to_owned(), key.to_owned());
    let now = Utc::now().timestamp();
    let result = update(paths, move |file| exit(file, &instance, &key, play_seconds, crashed, now, fallback.as_ref())).await;
    if let Err(e) = result {
        tracing::warn!("Spielsitzung konnte nicht in die Statistik geschrieben werden: {e}");
    }
}

/// Spiel-Ereignisse mitlesen: Ende → Sitzung schließen, „Connecting to …“ → Server merken.
/// Läuft im Hintergrund; ohne Tokio-Laufzeit (fremder Thread) passiert nichts.
pub fn observe(paths: &Paths, event: &GameEvent) {
    let Ok(handle) = tokio::runtime::Handle::try_current() else { return };
    match event {
        GameEvent::Exited { instance_id, key, crashed, play_seconds, .. } => {
            let (paths, instance, key, crashed, seconds) = (paths.clone(), instance_id.clone(), key.clone(), *crashed, *play_seconds);
            handle.spawn(async move { session_exited(&paths, &instance, &key, seconds, crashed).await });
        }
        GameEvent::Logs { instance_id, key, lines } => {
            for server in lines.iter().filter_map(|l| parse_connect(&l.message)) {
                let (paths, instance, key) = (paths.clone(), instance_id.clone(), key.clone());
                handle.spawn(async move {
                    if let Err(e) = update(&paths, move |file| join_server(file, &instance, &key, &server)).await {
                        tracing::debug!("Server konnte nicht vermerkt werden: {e}");
                    }
                });
            }
        }
        _ => {}
    }
}

/// Daten für die Seite „Statistik“. Instanzen, die die Statistik noch nicht kennt, werden dabei
/// übernommen (nicht, solange sie laufen – deren Zeit kommt gleich mit dem Ende).
pub async fn load(paths: &Paths, instances: &[Instance], running: &[RunningGame]) -> Result<PlayStats> {
    let busy: HashSet<String> = running.iter().map(|g| g.instance_id.clone()).collect();
    let keys: HashSet<String> = running.iter().map(|g| g.key.clone()).collect();
    let pending = !read(paths).await?.migrated;
    let migration = if pending { with_history(paths, instances.to_vec()).await } else { Vec::new() };
    let instances: Vec<Instance> = instances.iter().filter(|i| !busy.contains(&i.id)).cloned().collect();
    let now = Utc::now().timestamp();
    update(paths, move |file| {
        migrate(file, &migration, now);
        for instance in &instances {
            account(file, instance);
            set_name(file, &instance.id, &instance.name);
        }
        view(file, &keys, now)
    })
    .await
}

/// Statistik zurücksetzen: alle Sitzungen und Überträge weg, laufende Spiele zählen weiter. Die
/// bisherige Spielzeit der Instanzen wird danach nicht noch einmal übernommen.
pub async fn reset(paths: &Paths, instances: &[Instance]) -> Result<()> {
    let ids: Vec<String> = instances.iter().map(|i| i.id.clone()).collect();
    let now = Utc::now().timestamp();
    update(paths, move |file| {
        file.sessions.clear();
        file.carried.clear();
        file.known.extend(ids);
        file.migrated = true;
        file.since = Some(now);
    })
    .await
}

impl Launcher {
    /// Daten für die Seite „Statistik“.
    pub async fn play_stats(&self) -> Result<PlayStats> {
        let instances = self.instances.list().await?;
        load(&self.paths, &instances, &self.games.running()).await
    }

    /// Statistik zurücksetzen (Spielzeit der Instanzen bleibt).
    pub async fn reset_play_stats(&self) -> Result<()> {
        let instances = self.instances.list().await?;
        reset(&self.paths, &instances).await
    }

    /// Beim Start: Übernahme und Abgleich (Fehler nur im Log). Läuft noch in `init` mit – meist ein
    /// paar Millisekunden, einmalig etwas länger für die Übernahme. Danach schreibt beim Start nichts
    /// mehr im Hintergrund in den Datenordner (z. B. während er gleich verschoben wird).
    pub(crate) async fn play_stats_startup(&self) {
        // Was gerade läuft, bleibt offen; sein Ende schließt die Sitzung genau.
        if let Err(e) = startup(&self.paths, &self.games.running()).await {
            tracing::warn!("Spielzeit-Statistik konnte beim Start nicht abgeglichen werden: {e}");
        }
    }

    /// Nach dem Start eines Spielprozesses: Sitzung öffnen (im Hintergrund – der Start wartet nie).
    pub(crate) fn play_stats_started(&self, instance: &Instance, meta: SessionMeta, pid: u32, server: Option<String>) {
        let Some(game) = self.games.running().into_iter().find(|g| g.pid == pid && g.instance_id == meta.instance_id) else {
            // Schon wieder zu: das Ende bucht die Sitzung selbst.
            return;
        };
        let Ok(handle) = tokio::runtime::Handle::try_current() else { return };
        let (paths, instance) = (self.paths.clone(), instance.clone());
        handle.spawn(async move {
            session_started(&paths, &game.key, &instance, meta, game.started_at.timestamp(), server).await;
        });
    }
}

#[cfg(test)]
mod tests;
