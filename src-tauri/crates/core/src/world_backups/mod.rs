//! Automatische Welt-Backups (Tab „Welten“, Einstellungen → Welt-Backups).
//!
//! **Wann:** Bei jedem Klick auf „Spielen“ hält der Launcher sofort den Zustand jeder Welt fest
//! (`session.lock`, siehe [`lock`]) und packt dann im Hintergrund die Welten, die sich seit ihrem
//! letzten Backup geändert haben (Fingerabdruck, siehe [`archive`]) – zuletzt gespielte zuerst.
//! Nach dem Spielen (letzter Prozess der Instanz beendet) passiert dasselbe noch einmal: Dann
//! schreibt garantiert niemand in die Welten, und beim nächsten Start ist meist nichts mehr zu tun.
//!
//! **Warum der Start nie wartet:** Minecraft fasst eine Welt erst an, wenn man sie im Spiel öffnet –
//! nach dem Laden des Spiels und der Weltauswahl. Bis dahin ist das Backup kleiner und mittlerer
//! Welten längst fertig. Öffnet man eine Welt doch, bevor ihr Backup fertig ist, merkt der Launcher
//! das an `session.lock` (das schreibt jede Version beim Öffnen, neuere sperren es zusätzlich): Das
//! halbe Archiv wird verworfen – es entsteht nie ein Backup aus einem halb gespeicherten Stand –,
//! das vorige Backup bleibt, und der Spieler bekommt einen Hinweis. Nach dem Spielen holt der
//! Launcher es nach.
//!
//! **Was bleibt:** je Welt die letzten N automatischen Backups (Einstellung, ab Werk 5), optional
//! eine Obergrenze für alle zusammen (älteste zuerst, das neueste jeder Welt bleibt immer).
//! „Jetzt sichern“ und die Sicherung vor dem Ersetzen beim Wiederherstellen werden nie
//! automatisch gelöscht. Ein Fehler beim Backup hält keinen Start auf – er landet im Log und als
//! Hinweis beim Spieler.

mod archive;
mod lock;
mod store;

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::time::{Duration, Instant, SystemTime};

use chrono::{DateTime, Local, Utc};
use serde::{Deserialize, Serialize};

pub use archive::{Kind, Meta};

use crate::error::UserError;
use crate::instance::{Instance, InstanceStore};
use crate::paths::Paths;
use crate::process::{RunningGame, RunningProbe};
use crate::settings::{Settings, WorldBackupSettings};
use crate::shared_folders::{LinkState, SharedFolder, link_state};
use crate::task::TaskControl;
use crate::{Error, Launcher, Result, platform};

/// Ereignisse der automatischen Backups (Tauri: `world-backup`).
pub type BackupSink = Arc<dyn Fn(BackupEvent) + Send + Sync>;

/// Anlass einer automatischen Sicherung.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub enum JobReason {
    /// Beim Spielstart.
    Launch,
    /// Nach dem Spielen.
    Exit,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FailedWorld {
    pub name: String,
    pub error: UserError,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "type", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum BackupEvent {
    /// Es gibt geänderte Welten – die Sicherung beginnt.
    Started { job: u64, instance_id: String, reason: JobReason, worlds: Vec<String>, total_bytes: u64 },
    Progress { job: u64, instance_id: String, world: String, index: usize, count: usize, percent: u8 },
    /// `skipped`: im Spiel geöffnet, bevor ihr Backup fertig war (das vorige bleibt).
    Finished {
        job: u64,
        instance_id: String,
        reason: JobReason,
        saved: Vec<String>,
        skipped: Vec<String>,
        failed: Vec<FailedWorld>,
        cancelled: bool,
    },
}

/// Laufende automatische Sicherung (für die Oberfläche nach einem Neuladen).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct JobView {
    pub job: u64,
    pub instance_id: String,
    pub reason: JobReason,
    pub world: Option<String>,
    pub index: usize,
    pub count: usize,
    pub percent: u8,
}

struct Job {
    view: Arc<Mutex<JobView>>,
    control: TaskControl,
}

/// Wo die Welten einer Instanz liegen und unter welchem Namen ihre Backups.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Scope {
    pub key: String,
    pub saves: PathBuf,
    /// Gemeinsamer Welten-Ordner mehrerer Instanzen.
    pub shared: bool,
}

pub fn scope_of(paths: &Paths, instance: &Instance) -> Scope {
    let saves = paths.instance_game_dir(&instance.id).join("saves");
    let pool = paths.shared_folder(SharedFolder::Saves);
    if instance.overrides.shared_folders.contains(&SharedFolder::Saves) && link_state(&saves, &pool) == LinkState::Linked {
        Scope { key: store::SHARED_SCOPE.to_owned(), saves: pool, shared: true }
    } else {
        Scope { key: instance.id.clone(), saves, shared: false }
    }
}

/// Eine Welt in `saves/` mit ihrem Zustand zu Beginn.
#[derive(Debug, Clone)]
struct World {
    folder: String,
    dir: PathBuf,
    baseline: lock::LockState,
}

/// Welten in `saves/`, zuletzt gespielte zuerst.
fn worlds_in(saves: &Path) -> Vec<World> {
    let Ok(read) = std::fs::read_dir(saves) else { return Vec::new() };
    let mut worlds: Vec<(Option<SystemTime>, World)> = read
        .flatten()
        .filter_map(|entry| {
            let folder = entry.file_name().to_str()?.to_owned();
            if !entry.file_type().ok()?.is_dir() || folder.starts_with(".cubiq-") || crate::instance_files::validate_name(&folder).is_err() {
                return None;
            }
            let dir = entry.path();
            let played = std::fs::metadata(dir.join("level.dat")).ok().filter(std::fs::Metadata::is_file)?.modified().ok();
            Some((played, World { baseline: lock::state(&dir), folder, dir }))
        })
        .collect();
    worlds.sort_by_key(|w| std::cmp::Reverse(w.0));
    worlds.into_iter().map(|(_, w)| w).collect()
}

/// Ist die Welt gerade offen? Sperre des Spiels – oder, für alte Versionen ohne Sperre,
/// `session.lock` jünger als der Start eines laufenden Spiels, das diesen Welten-Ordner nutzt.
fn busy(world: &Path, running_since: Option<SystemTime>) -> bool {
    if lock::is_locked(world) {
        return true;
    }
    // Ab 1.16 hält das Spiel die Sperre, solange die Welt offen ist: ohne Sperre ist sie zu.
    if running_since.is_none() || lock::is_modern(world) {
        return false;
    }
    matches!((running_since, lock::state(world).modified), (Some(since), Some(modified)) if modified >= since)
}

fn opened() -> Error {
    Error::validation(crate::msg!("worldBackups.worldOpened", "Die Welt wurde im Spiel geöffnet, bevor ihr Backup fertig war."))
}

fn world_open() -> Error {
    Error::validation(crate::msg!("worldBackups.worldOpen", "Diese Welt ist gerade im Spiel geöffnet – schließe sie zuerst."))
}

fn stop_first() -> Error {
    Error::launch(crate::msg!("launcher.instanceRunningStopFirst", "Die Instanz läuft gerade – bitte erst beenden."))
}

/// Name und Version aus der `level.dat` (für die Beschreibung im Archiv).
fn level_summary(world: &Path) -> crate::worlds::LevelSummary {
    std::fs::read(world.join("level.dat")).ok().and_then(|bytes| crate::worlds::parse_level_dat(&bytes).ok()).unwrap_or_default()
}

/// Packt eine Welt als Backup der Art `kind`. Bricht ab (und verwirft das halbe Archiv), sobald
/// `cancelled` wahr wird oder jemand die Welt öffnet.
#[allow(clippy::too_many_arguments)]
fn create_sync(
    paths: &Paths,
    scope: &str,
    saves: &Path,
    folder: &str,
    kind: Kind,
    baseline: lock::LockState,
    entries: Option<Vec<archive::Entry>>,
    cancelled: &dyn Fn() -> bool,
    progress: &mut dyn FnMut(u64, u64),
) -> Result<(store::BackupFile, Meta)> {
    let world = saves.join(folder);
    let entries = match entries {
        Some(entries) => entries,
        None => archive::scan(&world)?,
    };
    let total = archive::total_bytes(&entries).max(1);
    let summary = level_summary(&world);
    let dir = store::world_dir(paths, scope, folder);
    std::fs::create_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;
    // Läuft immer unter der Sperre des Scopes: Übrige `.part`-Dateien stammen von einem Absturz.
    store::remove_stale_parts(&dir);
    let now = Local::now();
    let name = store::free_file_name(&dir, now, kind);
    let part = dir.join(store::part_name(&name));
    // Sperre höchstens alle 250 ms prüfen – sonst kostet sie bei Zehntausenden Dateien Zeit.
    let last = std::cell::Cell::new(None::<Instant>);
    let guard = |force: bool| -> Result<()> {
        if cancelled() {
            return Err(Error::Cancelled);
        }
        if force || last.get().is_none_or(|t| t.elapsed() >= Duration::from_millis(250)) {
            last.set(Some(Instant::now()));
            if lock::state(&world) != baseline || lock::is_locked(&world) {
                return Err(opened());
            }
        }
        Ok(())
    };
    let meta = Meta {
        cubiq: 1,
        kind,
        world: folder.to_owned(),
        name: summary.name.unwrap_or_else(|| folder.to_owned()),
        fp: String::new(),
        files: 0,
        bytes: 0,
        created: now.timestamp(),
        version: summary.version,
    };
    let result = archive::write(folder, &entries, &part, meta, &mut |done| progress(done, total), &|| guard(false))
        .and_then(|meta| guard(true).map(|()| meta));
    let meta = match result {
        Ok(meta) => meta,
        Err(e) => {
            let _ = std::fs::remove_file(&part);
            store::remove_if_empty(&dir);
            return Err(e);
        }
    };
    let path = dir.join(&name);
    if let Err(e) = std::fs::rename(&part, &path) {
        let _ = std::fs::remove_file(&part);
        return Err(Error::io(&path, e));
    }
    let size = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
    Ok((store::BackupFile { id: name, path, created: now.with_timezone(&Utc), kind, size }, meta))
}

/// Geplante Welt eines automatischen Backups.
struct Planned {
    world: World,
    name: String,
    entries: Vec<archive::Entry>,
}

/// Welche Welten haben sich seit ihrem letzten Backup geändert? (Ein Backup mit demselben
/// Fingerabdruck gibt es schon → nichts zu tun.) Offene Welten bleiben außen vor.
fn plan(paths: &Paths, scope: &str, worlds: Vec<World>, running_since: Option<SystemTime>) -> Vec<Planned> {
    let mut out = Vec::new();
    for world in worlds {
        if busy(&world.dir, running_since) || lock::state(&world.dir) != world.baseline {
            continue;
        }
        let entries = match archive::scan(&world.dir) {
            Ok(entries) => entries,
            Err(e) => {
                tracing::warn!("Welt '{}' konnte nicht gelesen werden: {e}", world.folder);
                continue;
            }
        };
        let fingerprint = archive::fingerprint(&entries);
        let dir = store::world_dir(paths, scope, &world.folder);
        if store::list(&dir).iter().any(|b| archive::read_meta(&b.path).is_some_and(|m| m.fp == fingerprint)) {
            continue;
        }
        let name = level_summary(&world.dir).name.unwrap_or_else(|| world.folder.clone());
        out.push(Planned { world, name, entries });
    }
    out
}

/// Ein Backup, wie es die Oberfläche sieht.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupInfo {
    /// Kennung für Wiederherstellen/Löschen.
    pub id: String,
    pub kind: Kind,
    pub created: DateTime<Utc>,
    /// Größe des Archivs.
    pub size: u64,
    /// Weltname zum Zeitpunkt des Backups.
    pub name: Option<String>,
    pub version: Option<String>,
    pub files: Option<u64>,
}

fn info(file: &store::BackupFile, meta: Option<Meta>) -> BackupInfo {
    BackupInfo {
        id: file.id.clone(),
        kind: file.kind,
        created: file.created,
        size: file.size,
        files: meta.as_ref().map(|m| m.files),
        version: meta.as_ref().and_then(|m| m.version.clone()),
        name: meta.map(|m| m.name).filter(|n| !n.is_empty()),
    }
}

/// Backup-Stand einer Welt (Tab „Welten“).
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorldBackupSummary {
    pub folder: String,
    /// Name aus dem neuesten Backup (für gelöschte Welten).
    pub name: Option<String>,
    /// Gibt es die Welt noch?
    pub exists: bool,
    pub count: usize,
    pub latest: Option<DateTime<Utc>>,
    pub bytes: u64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupOverview {
    /// Gilt für diese Instanz.
    pub enabled: bool,
    pub global: bool,
    /// Eigene Einstellung der Instanz (`None` = wie global).
    pub instance: Option<bool>,
    pub keep: u8,
    /// Gemeinsamer Welten-Ordner (Backups gelten für alle Instanzen, die ihn teilen).
    pub shared: bool,
    pub worlds: Vec<WorldBackupSummary>,
    pub job: Option<JobView>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum RestoreMode {
    /// Als neue Welt daneben.
    Copy,
    /// Statt der jetzigen Welt (die vorher als Sicherheits-Backup gesichert wird).
    Replace,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreOutcome {
    /// Ordner der wiederhergestellten Welt.
    pub folder: String,
    /// Sicherheits-Backup des vorigen Stands (beim Ersetzen).
    pub safety_backup: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupUsage {
    pub bytes: u64,
    pub count: usize,
    pub worlds: usize,
    /// Backups gelöschter Instanzen.
    pub orphaned_bytes: u64,
}

/// Dienst für Welt-Backups; ein Exemplar je Launcher.
pub struct WorldBackups {
    paths: Paths,
    sink: RwLock<Option<BackupSink>>,
    /// Laufende automatische Sicherung je Scope.
    jobs: Mutex<HashMap<String, Job>>,
    next: AtomicU64,
    /// Nie gleichzeitig in denselben Welten-Ordner (Packen, Wiederherstellen, Löschen).
    locks: Mutex<HashMap<String, Arc<tokio::sync::Mutex<()>>>>,
}

fn lock_map<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
}

impl WorldBackups {
    pub fn new(paths: Paths) -> Self {
        Self { paths, sink: RwLock::default(), jobs: Mutex::default(), next: AtomicU64::new(1), locks: Mutex::default() }
    }

    pub fn set_sink(&self, sink: BackupSink) {
        *self.sink.write().unwrap_or_else(std::sync::PoisonError::into_inner) = Some(sink);
    }

    fn sink(&self) -> Option<BackupSink> {
        self.sink.read().unwrap_or_else(std::sync::PoisonError::into_inner).clone()
    }

    fn scope_lock(&self, scope: &str) -> Arc<tokio::sync::Mutex<()>> {
        lock_map(&self.locks).entry(scope.to_owned()).or_default().clone()
    }

    /// Automatische Backups für diese Instanz an? (Eigene Einstellung vor der globalen.)
    async fn enabled_for(&self, instance_id: &str, settings: &WorldBackupSettings) -> bool {
        store::load_config(&self.paths).await.instances.get(instance_id).copied().unwrap_or(settings.enabled)
    }

    /// Instanzen, die diesen Welten-Ordner benutzen.
    async fn users(&self, scope: &Scope) -> HashSet<String> {
        if !scope.shared {
            return [scope.key.clone()].into();
        }
        let instances = InstanceStore::new(self.paths.clone()).list().await.unwrap_or_default();
        instances.into_iter().filter(|i| scope_of(&self.paths, i).shared).map(|i| i.id).collect()
    }

    /// Frühester Start eines laufenden Spiels, das diesen Welten-Ordner benutzt.
    async fn running_since(&self, scope: &Scope, running: &[RunningGame]) -> Option<SystemTime> {
        let users = self.users(scope).await;
        running.iter().filter(|g| users.contains(&g.instance_id)).map(|g| SystemTime::from(g.started_at)).min()
    }

    /// Laufende automatische Sicherungen.
    pub fn jobs(&self) -> Vec<JobView> {
        lock_map(&self.jobs).values().map(|j| lock_map(&j.view).clone()).collect()
    }

    /// Automatische Sicherung abbrechen (der Start läuft weiter).
    pub fn cancel(&self, job: u64) -> bool {
        let jobs = lock_map(&self.jobs);
        match jobs.values().find(|j| lock_map(&j.view).job == job) {
            Some(j) => {
                j.control.cancel();
                true
            }
            None => false,
        }
    }

    /// Spielstart: Zustand der Welten jetzt festhalten (das Spiel läuft noch nicht), gepackt wird im
    /// Hintergrund. Kehrt sofort zurück.
    pub async fn before_launch(self: &Arc<Self>, instance: &Instance, settings: &WorldBackupSettings, running: &[RunningGame]) {
        if platform::MOBILE || !self.enabled_for(&instance.id, settings).await {
            return;
        }
        let scope = scope_of(&self.paths, instance);
        let since = self.running_since(&scope, running).await;
        let saves = scope.saves.clone();
        let worlds = tokio::task::spawn_blocking(move || worlds_in(&saves)).await.unwrap_or_default();
        self.spawn_job(&instance.id, scope, worlds, JobReason::Launch, *settings, since);
    }

    /// Nach dem Spielen (letzter Prozess der Instanz zu): geänderte Welten sichern.
    pub async fn after_exit(self: &Arc<Self>, instance_id: &str, still_running: RunningProbe) {
        if platform::MOBILE {
            return;
        }
        let Ok(instance) = InstanceStore::new(self.paths.clone()).get(instance_id).await else { return };
        let settings = Settings::load(&self.paths.settings_file()).await.unwrap_or_default().world_backups;
        if !self.enabled_for(&instance.id, &settings).await {
            return;
        }
        let scope = scope_of(&self.paths, &instance);
        // Läuft noch ein Spiel mit diesen Welten, wartet das Backup auf dessen Ende bzw. den nächsten Start.
        if self.users(&scope).await.iter().any(|id| still_running(id)) {
            return;
        }
        let saves = scope.saves.clone();
        let worlds = tokio::task::spawn_blocking(move || worlds_in(&saves)).await.unwrap_or_default();
        self.spawn_job(&instance.id, scope, worlds, JobReason::Exit, settings, None);
    }

    fn spawn_job(self: &Arc<Self>, instance_id: &str, scope: Scope, worlds: Vec<World>, reason: JobReason, settings: WorldBackupSettings, since: Option<SystemTime>) {
        if worlds.is_empty() {
            return;
        }
        let Ok(handle) = tokio::runtime::Handle::try_current() else { return };
        let (view, control) = {
            let mut jobs = lock_map(&self.jobs);
            // Läuft schon eine Sicherung dieses Ordners, deckt sie die Welten ab.
            if jobs.contains_key(&scope.key) {
                return;
            }
            let view = Arc::new(Mutex::new(JobView {
                job: self.next.fetch_add(1, Ordering::Relaxed),
                instance_id: instance_id.to_owned(),
                reason,
                world: None,
                index: 0,
                count: 0,
                percent: 0,
            }));
            let control = TaskControl::new();
            jobs.insert(scope.key.clone(), Job { view: view.clone(), control: control.clone() });
            (view, control)
        };
        let this = Arc::clone(self);
        handle.spawn(async move {
            this.run_job(&scope, worlds, reason, settings, since, &view, &control).await;
            lock_map(&this.jobs).remove(&scope.key);
        });
    }

    #[allow(clippy::too_many_arguments)]
    async fn run_job(
        &self,
        scope: &Scope,
        worlds: Vec<World>,
        reason: JobReason,
        settings: WorldBackupSettings,
        since: Option<SystemTime>,
        view: &Arc<Mutex<JobView>>,
        control: &TaskControl,
    ) {
        let (job, instance_id) = {
            let v = lock_map(view);
            (v.job, v.instance_id.clone())
        };
        let (paths, key) = (self.paths.clone(), scope.key.clone());
        let planned = tokio::task::spawn_blocking(move || plan(&paths, &key, worlds, since)).await.unwrap_or_default();
        if planned.is_empty() {
            return;
        }
        let sink = self.sink();
        let emit = |event: BackupEvent| {
            if let Some(sink) = &sink {
                sink(event);
            }
        };
        let count = planned.len();
        emit(BackupEvent::Started {
            job,
            instance_id: instance_id.clone(),
            reason,
            worlds: planned.iter().map(|p| p.name.clone()).collect(),
            total_bytes: planned.iter().map(|p| archive::total_bytes(&p.entries)).sum(),
        });
        let (mut saved, mut skipped, mut failed) = (Vec::new(), Vec::new(), Vec::new());
        let mut cancelled = false;
        for (index, item) in planned.into_iter().enumerate() {
            if control.is_cancelled() {
                cancelled = true;
                break;
            }
            {
                let mut v = lock_map(view);
                v.world = Some(item.name.clone());
                v.index = index;
                v.count = count;
                v.percent = 0;
            }
            let scope_lock = self.scope_lock(&scope.key);
            let _guard = scope_lock.lock().await;
            let (paths, key, saves) = (self.paths.clone(), scope.key.clone(), scope.saves.clone());
            let (sink, view2, control2, name) = (sink.clone(), view.clone(), control.clone(), item.name.clone());
            let instance = instance_id.clone();
            let folder = item.world.folder.clone();
            let result = tokio::task::spawn_blocking(move || {
                let mut last = 0u8;
                let mut progress = |done: u64, total: u64| {
                    let percent = ((done * 100) / total.max(1)).min(99) as u8;
                    if percent != last {
                        last = percent;
                        lock_map(&view2).percent = percent;
                        if let Some(sink) = &sink {
                            sink(BackupEvent::Progress { job, instance_id: instance.clone(), world: name.clone(), index, count, percent });
                        }
                    }
                };
                let cancelled = || control2.is_cancelled();
                let out = create_sync(&paths, &key, &saves, &folder, Kind::Auto, item.world.baseline, Some(item.entries), &cancelled, &mut progress);
                if out.is_ok() {
                    store::rotate(&store::world_dir(&paths, &key, &folder), usize::from(settings.keep));
                }
                out
            })
            .await
            .map_err(|e| Error::Internal(e.to_string()))
            .and_then(|r| r);
            match result {
                Ok(_) => saved.push(item.name),
                Err(Error::Cancelled) => {
                    cancelled = true;
                    break;
                }
                Err(e) if e.message_code() == "worldBackups.worldOpened" => {
                    tracing::info!("Backup von '{}' verworfen: Welt wurde geöffnet", item.world.folder);
                    skipped.push(item.name);
                }
                Err(e) => {
                    tracing::warn!("Backup von '{}' fehlgeschlagen: {e}", item.world.folder);
                    failed.push(FailedWorld { name: item.name, error: e.to_user() });
                }
            }
        }
        if settings.max_total_gb > 0 {
            let paths = self.paths.clone();
            let max = u64::from(settings.max_total_gb) << 30;
            let freed = tokio::task::spawn_blocking(move || store::enforce_cap(&paths, max)).await.unwrap_or(0);
            if freed > 0 {
                tracing::info!("Welt-Backups: {freed} Bytes alter Backups gelöscht (Obergrenze)");
            }
        }
        emit(BackupEvent::Finished { job, instance_id, reason, saved, skipped, failed, cancelled });
    }

    // --- Für die Oberfläche --------------------------------------------------------------------

    /// Backups aller Welten der Instanz – auch gelöschter Welten, deren Backups noch da sind.
    pub async fn overview(&self, instance: &Instance, settings: &WorldBackupSettings) -> Result<BackupOverview> {
        let scope = scope_of(&self.paths, instance);
        let config = store::load_config(&self.paths).await;
        let own = config.instances.get(&instance.id).copied();
        let job = lock_map(&self.jobs).get(&scope.key).map(|j| lock_map(&j.view).clone());
        let (paths, key, saves) = (self.paths.clone(), scope.key.clone(), scope.saves.clone());
        let worlds = tokio::task::spawn_blocking(move || {
            let mut out = Vec::new();
            let Ok(read) = std::fs::read_dir(store::scope_dir(&paths, &key)) else { return out };
            for entry in read.flatten() {
                let Some(folder) = entry.file_name().to_str().map(str::to_owned) else { continue };
                let backups = store::list(&entry.path());
                if backups.is_empty() {
                    continue;
                }
                let exists = saves.join(&folder).join("level.dat").is_file();
                out.push(WorldBackupSummary {
                    name: backups.first().and_then(|b| archive::read_meta(&b.path)).map(|m| m.name).filter(|n| !n.is_empty()),
                    exists,
                    count: backups.len(),
                    latest: backups.first().map(|b| b.created),
                    bytes: backups.iter().map(|b| b.size).sum(),
                    folder,
                });
            }
            out.sort_by_key(|w| std::cmp::Reverse(w.latest));
            out
        })
        .await
        .map_err(|e| Error::Internal(e.to_string()))?;
        Ok(BackupOverview {
            enabled: own.unwrap_or(settings.enabled),
            global: settings.enabled,
            instance: own,
            keep: settings.keep,
            shared: scope.shared,
            worlds,
            job,
        })
    }

    /// Alle Backups einer Welt, neueste zuerst (mit älteren Sicherungen aus dem Spielordner).
    pub async fn list(&self, instance: &Instance, folder: &str) -> Result<Vec<BackupInfo>> {
        crate::instance_files::validate_name(folder)?;
        let scope = scope_of(&self.paths, instance);
        let dir = store::world_dir(&self.paths, &scope.key, folder);
        let game_dir = self.paths.instance_game_dir(&instance.id);
        let folder = folder.to_owned();
        tokio::task::spawn_blocking(move || {
            let mut out: Vec<BackupInfo> = store::list(&dir).iter().map(|b| info(b, archive::read_meta(&b.path))).collect();
            out.extend(store::legacy(&game_dir, &folder).iter().map(|b| info(b, None)));
            out.sort_by_key(|b| std::cmp::Reverse(b.created));
            out
        })
        .await
        .map_err(|e| Error::Internal(e.to_string()))
    }

    /// „Jetzt sichern“ – auch während das Spiel läuft, solange die Welt nicht offen ist.
    pub async fn backup_now(&self, instance: &Instance, folder: &str, running: &[RunningGame], on_progress: impl Fn(u8) + Send + 'static) -> Result<BackupInfo> {
        crate::worlds::world_dir(&self.paths, &instance.id, folder)?;
        let scope = scope_of(&self.paths, instance);
        let world = scope.saves.join(folder);
        let since = self.running_since(&scope, running).await;
        if busy(&world, since) {
            return Err(world_open());
        }
        let baseline = lock::state(&world);
        let scope_lock = self.scope_lock(&scope.key);
        let _guard = scope_lock.lock().await;
        let control = crate::task::current();
        let (paths, folder) = (self.paths.clone(), folder.to_owned());
        tokio::task::spawn_blocking(move || {
            let cancelled = || control.as_ref().is_some_and(TaskControl::is_cancelled);
            let mut last = 0u8;
            let mut progress = |done: u64, total: u64| {
                let percent = ((done * 100) / total.max(1)).min(99) as u8;
                if percent != last {
                    last = percent;
                    on_progress(percent);
                }
            };
            let (file, meta) = create_sync(&paths, &scope.key, &scope.saves, &folder, Kind::Manual, baseline, None, &cancelled, &mut progress)?;
            on_progress(100);
            Ok(info(&file, Some(meta)))
        })
        .await
        .map_err(|e| Error::Internal(e.to_string()))?
    }

    /// Backup wiederherstellen – als Kopie daneben oder statt der jetzigen Welt (die wird vorher
    /// als Sicherheits-Backup gesichert). Nur, solange kein Spiel diese Welten benutzt.
    #[allow(clippy::too_many_arguments)]
    pub async fn restore(
        &self,
        instance: &Instance,
        folder: &str,
        backup: &str,
        mode: RestoreMode,
        copy_name: Option<&str>,
        running: &[RunningGame],
        on_progress: impl Fn(u8) + Send + 'static,
    ) -> Result<RestoreOutcome> {
        crate::instance_files::validate_name(folder)?;
        let scope = scope_of(&self.paths, instance);
        let users = self.users(&scope).await;
        if running.iter().any(|g| users.contains(&g.instance_id)) {
            return Err(stop_first());
        }
        let game_dir = self.paths.instance_game_dir(&instance.id);
        let file = store::resolve(&self.paths, &scope.key, &game_dir, folder, backup)?;
        let copy_name = match mode {
            RestoreMode::Copy => {
                let name = copy_name.map(str::trim).filter(|n| !n.is_empty()).map(str::to_owned).unwrap_or_else(|| format!("{folder} (restored)"));
                let name: String = name.chars().filter(|c| !matches!(c, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|') && !c.is_control()).take(80).collect();
                let name = name.trim().trim_end_matches('.').to_owned();
                crate::instance_files::validate_name(&name)?;
                Some(name)
            }
            RestoreMode::Replace => None,
        };
        // Eine laufende automatische Sicherung dieses Ordners hört auf – sie hielte die gleich
        // ersetzte Welt sonst für „geöffnet“. Beim nächsten Start bzw. Spielende kommt sie wieder.
        if let Some(job) = lock_map(&self.jobs).get(&scope.key) {
            job.control.cancel();
        }
        let scope_lock = self.scope_lock(&scope.key);
        let _guard = scope_lock.lock().await;
        let control = crate::task::current();
        let (paths, folder) = (self.paths.clone(), folder.to_owned());
        tokio::task::spawn_blocking(move || {
            let cancelled = || control.as_ref().is_some_and(TaskControl::is_cancelled);
            restore_sync(&paths, &scope, &folder, &file, copy_name.as_deref(), &cancelled, &on_progress)
        })
        .await
        .map_err(|e| Error::Internal(e.to_string()))?
    }

    /// Backup löschen (auch ältere Sicherungen aus dem Spielordner).
    pub async fn delete(&self, instance: &Instance, folder: &str, backup: &str) -> Result<()> {
        crate::instance_files::validate_name(folder)?;
        let scope = scope_of(&self.paths, instance);
        let game_dir = self.paths.instance_game_dir(&instance.id);
        let file = store::resolve(&self.paths, &scope.key, &game_dir, folder, backup)?;
        let scope_lock = self.scope_lock(&scope.key);
        let _guard = scope_lock.lock().await;
        std::fs::remove_file(&file.path).map_err(|e| Error::io(&file.path, e))?;
        if file.kind != Kind::Legacy {
            let dir = store::world_dir(&self.paths, &scope.key, folder);
            store::remove_if_empty(&dir);
            store::remove_if_empty(&store::scope_dir(&self.paths, &scope.key));
        }
        Ok(())
    }

    /// Ordner mit den Backups einer Welt (bzw. der Instanz); wird bei Bedarf angelegt.
    pub fn folder(&self, instance: &Instance, folder: Option<&str>) -> Result<PathBuf> {
        let scope = scope_of(&self.paths, instance);
        let dir = match folder {
            Some(folder) => {
                crate::instance_files::validate_name(folder)?;
                store::world_dir(&self.paths, &scope.key, folder)
            }
            None => store::scope_dir(&self.paths, &scope.key),
        };
        std::fs::create_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;
        Ok(dir)
    }

    /// Ordner aller Backups.
    pub fn root_folder(&self) -> Result<PathBuf> {
        let dir = store::root(&self.paths);
        std::fs::create_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;
        Ok(dir)
    }

    /// Eigene Einstellung der Instanz (`None` = wie global).
    pub async fn set_instance(&self, instance_id: &str, enabled: Option<bool>) -> Result<()> {
        crate::instance::validate_id(instance_id)?;
        let mut config = store::load_config(&self.paths).await;
        match enabled {
            Some(on) => config.instances.insert(instance_id.to_owned(), on),
            None => config.instances.remove(instance_id),
        };
        store::save_config(&self.paths, &config).await
    }

    /// Platz aller Backups; `known` = IDs der vorhandenen Instanzen (für verwaiste Backups).
    pub async fn usage(&self, known: HashSet<String>) -> BackupUsage {
        let paths = self.paths.clone();
        tokio::task::spawn_blocking(move || {
            let mut usage = BackupUsage::default();
            for (scope, _, dir) in store::all_worlds(&paths) {
                let backups = store::list(&dir);
                let bytes: u64 = backups.iter().map(|b| b.size).sum();
                usage.bytes += bytes;
                usage.count += backups.len();
                usage.worlds += usize::from(!backups.is_empty());
                if scope != store::SHARED_SCOPE && !known.contains(&scope) {
                    usage.orphaned_bytes += bytes;
                }
            }
            usage
        })
        .await
        .unwrap_or_default()
    }

    /// Backups gelöschter Instanzen entfernen. Liefert die freigegebenen Bytes.
    pub async fn prune_orphans(&self, known: HashSet<String>) -> Result<u64> {
        let usage = self.usage(known.clone()).await;
        let paths = self.paths.clone();
        tokio::task::spawn_blocking(move || {
            let Ok(read) = std::fs::read_dir(store::root(&paths)) else { return Ok(0) };
            for entry in read.flatten() {
                let Some(scope) = entry.file_name().to_str().map(str::to_owned) else { continue };
                let orphan = entry.file_type().is_ok_and(|t| t.is_dir())
                    && scope != store::SHARED_SCOPE
                    && store::check_scope(&scope).is_ok()
                    && !known.contains(&scope);
                if orphan {
                    std::fs::remove_dir_all(entry.path()).map_err(|e| Error::io(entry.path(), e))?;
                }
            }
            Ok(usage.orphaned_bytes)
        })
        .await
        .map_err(|e| Error::Internal(e.to_string()))?
    }
}

/// Neuer Name der Welt (`LevelName` in der `level.dat`) – best effort, eine unlesbare Datei bleibt.
fn rename_level(world: &Path, name: &str) {
    use std::io::{Read, Write};
    let path = world.join("level.dat");
    let Ok(bytes) = std::fs::read(&path) else { return };
    let mut raw = Vec::new();
    if flate2::read::GzDecoder::new(bytes.as_slice()).take(64 << 20).read_to_end(&mut raw).is_err() {
        return;
    }
    let Ok(mut root) = crate::nbt::read_root(&raw) else { return };
    let Some((_, crate::nbt::Tag::Compound(data))) = root.iter_mut().find(|(k, _)| k == b"Data") else { return };
    crate::nbt::set(data, "LevelName", crate::nbt::Tag::string(name));
    let mut gz = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::default());
    if gz.write_all(&crate::nbt::write_root(&root)).is_err() {
        return;
    }
    let Ok(out) = gz.finish() else { return };
    if let Err(e) = crate::fsutil::write_atomic_sync(&path, &out) {
        tracing::warn!("Weltname konnte nicht gesetzt werden: {e}");
    }
}

/// Reste eines abgebrochenen Wiederherstellens (`.cubiq-restore-…`, `.cubiq-old-…`) aufräumen – das
/// Spiel würde sie sonst als weitere Welten anzeigen. Den Stand vor dem Ersetzen hält das
/// Sicherheits-Backup.
fn remove_stale_restores(saves: &Path) {
    let Ok(read) = std::fs::read_dir(saves) else { return };
    for entry in read.flatten() {
        let stale = entry.file_name().to_str().is_some_and(|n| n.starts_with(".cubiq-restore-") || n.starts_with(".cubiq-old-"));
        if stale && entry.file_type().is_ok_and(|t| t.is_dir()) {
            let _ = std::fs::remove_dir_all(entry.path());
        }
    }
}

/// Entfernt einen Ordner; klappt das nicht (gesperrte Datei), landet er im Papierkorb.
fn remove_tree(dir: &Path) {
    if std::fs::remove_dir_all(dir).is_ok() {
        return;
    }
    if let Err(e) = crate::platform::move_to_trash(dir) {
        tracing::warn!("Alter Weltordner {} konnte nicht entfernt werden: {e}", dir.display());
    }
}

fn restore_sync(
    paths: &Paths,
    scope: &Scope,
    folder: &str,
    file: &store::BackupFile,
    copy_name: Option<&str>,
    cancelled: &dyn Fn() -> bool,
    on_progress: &dyn Fn(u8),
) -> Result<RestoreOutcome> {
    let saves = &scope.saves;
    std::fs::create_dir_all(saves).map_err(|e| Error::io(saves, e))?;
    remove_stale_restores(saves);
    let world = saves.join(folder);
    let replacing = copy_name.is_none() && world.join("level.dat").is_file();
    // Ersetzen: erst den jetzigen Stand sichern (0–40 %), dann entpacken (40–100 %).
    let mut safety = None;
    if replacing {
        if lock::is_locked(&world) {
            return Err(world_open());
        }
        let baseline = lock::state(&world);
        let mut progress = |done: u64, total: u64| on_progress(((done * 40) / total.max(1)).min(40) as u8);
        let (backup, _) = create_sync(paths, &scope.key, saves, folder, Kind::Safety, baseline, None, cancelled, &mut progress)?;
        safety = Some(backup.id);
    }
    let base = if replacing { 40 } else { 0 };
    let tmp = saves.join(format!(".cubiq-restore-{}", uuid::Uuid::new_v4().simple()));
    let check = || if cancelled() { Err(Error::Cancelled) } else { Ok(()) };
    let mut progress = |done: u64, total: u64| on_progress((base + (done * (100 - base)) / total.max(1)).min(99) as u8);
    if let Err(e) = archive::extract(&file.path, &tmp, &mut progress, &check) {
        let _ = std::fs::remove_dir_all(&tmp);
        return Err(e);
    }
    let target = match copy_name {
        Some(name) => {
            let free = crate::instance_files::free_name(saves, name);
            let target = saves.join(&free);
            if let Err(e) = std::fs::rename(&tmp, &target) {
                let _ = std::fs::remove_dir_all(&tmp);
                return Err(Error::io(&target, e));
            }
            rename_level(&target, &free);
            free
        }
        None if replacing => {
            let old = saves.join(format!(".cubiq-old-{}", uuid::Uuid::new_v4().simple()));
            if let Err(e) = std::fs::rename(&world, &old) {
                let _ = std::fs::remove_dir_all(&tmp);
                return Err(Error::io(&world, e));
            }
            if let Err(e) = std::fs::rename(&tmp, &world) {
                // Zurück wie vorher – die Welt bleibt, wie sie war.
                let _ = std::fs::rename(&old, &world);
                let _ = std::fs::remove_dir_all(&tmp);
                return Err(Error::io(&world, e));
            }
            remove_tree(&old);
            folder.to_owned()
        }
        None => {
            // Gelöschte Welt: unter ihrem alten Ordnernamen zurück (falls frei).
            let free = crate::instance_files::free_name(saves, folder);
            let target = saves.join(&free);
            if let Err(e) = std::fs::rename(&tmp, &target) {
                let _ = std::fs::remove_dir_all(&tmp);
                return Err(Error::io(&target, e));
            }
            free
        }
    };
    on_progress(100);
    Ok(RestoreOutcome { folder: target, safety_backup: safety })
}

// --- Launcher -------------------------------------------------------------------------------------

impl Launcher {
    /// Dienst der Welt-Backups (z. B. für den Ereignis-Kanal der App).
    pub fn world_backups(&self) -> &Arc<WorldBackups> {
        &self.world_backups
    }

    /// Vor dem Start: Zustand festhalten, im Hintergrund sichern (Fehler nie ein Startfehler).
    pub(crate) async fn world_backups_before_launch(&self, instance: &Instance) {
        let settings = self.settings().await.world_backups;
        self.world_backups.before_launch(instance, &settings, &self.games.running()).await;
    }

    pub async fn world_backup_overview(&self, id: &str) -> Result<BackupOverview> {
        let instance = self.instances.get(id).await?;
        self.world_backups.overview(&instance, &self.settings().await.world_backups).await
    }

    pub async fn world_backup_list(&self, id: &str, folder: &str) -> Result<Vec<BackupInfo>> {
        let instance = self.instances.get(id).await?;
        self.world_backups.list(&instance, folder).await
    }

    pub async fn world_backup_now(&self, id: &str, folder: &str, on_progress: impl Fn(u8) + Send + 'static) -> Result<BackupInfo> {
        let instance = self.instances.get(id).await?;
        self.world_backups.backup_now(&instance, folder, &self.games.running(), on_progress).await
    }

    pub async fn world_backup_restore(
        &self,
        id: &str,
        folder: &str,
        backup: &str,
        mode: RestoreMode,
        copy_name: Option<&str>,
        on_progress: impl Fn(u8) + Send + 'static,
    ) -> Result<RestoreOutcome> {
        let instance = self.instances.get(id).await?;
        if self.is_preparing(&instance.id) {
            return Err(stop_first());
        }
        self.world_backups.restore(&instance, folder, backup, mode, copy_name, &self.games.running(), on_progress).await
    }

    pub async fn world_backup_delete(&self, id: &str, folder: &str, backup: &str) -> Result<()> {
        let instance = self.instances.get(id).await?;
        self.world_backups.delete(&instance, folder, backup).await
    }

    pub async fn world_backup_folder(&self, id: &str, folder: Option<&str>) -> Result<PathBuf> {
        let instance = self.instances.get(id).await?;
        self.world_backups.folder(&instance, folder)
    }

    /// Eigene Einstellung der Instanz (`None` = globale) – liefert den neuen Stand.
    pub async fn set_world_backup_auto(&self, id: &str, enabled: Option<bool>) -> Result<BackupOverview> {
        let instance = self.instances.get(id).await?;
        self.world_backups.set_instance(&instance.id, enabled).await?;
        self.world_backups.overview(&instance, &self.settings().await.world_backups).await
    }

    /// IDs aller bekannten Instanzen – auch an eigenem Ort, dessen Laufwerk gerade fehlt (deren
    /// Backups sind nicht „verwaist“).
    async fn known_instances(&self) -> Result<HashSet<String>> {
        let mut known: HashSet<String> = self.instances.list().await?.into_iter().map(|i| i.id).collect();
        known.extend(self.paths.custom_locations().into_iter().map(|(id, _)| id));
        Ok(known)
    }

    pub async fn world_backup_usage(&self) -> Result<BackupUsage> {
        let known = self.known_instances().await?;
        Ok(self.world_backups.usage(known).await)
    }

    /// Backups gelöschter Instanzen entfernen.
    pub async fn prune_world_backups(&self) -> Result<u64> {
        let known = self.known_instances().await?;
        self.world_backups.prune_orphans(known).await
    }
}

#[cfg(test)]
mod tests;
