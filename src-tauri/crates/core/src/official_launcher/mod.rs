//! „Mit dem Minecraft Launcher spielen“: Cubiq bereitet die Instanz vor wie vor jedem eigenen Start
//! (Mods, Cubiq Client, Abhängigkeiten, gemeinsame Ordner, Optionen, Serverliste), trägt sie im
//! offiziellen Minecraft Launcher als Installation „Cubiq · <Name>“ ein und öffnet ihn. Anmeldung,
//! Besitzprüfung und Spielstart erledigt Mojangs eigene App – Cubiq sieht dabei kein Token.
//!
//! Feste Regeln (Tests in `tests.rs`):
//! - Keine fremde Client-ID, kein Spielen ohne Konto: Gespielt wird nur nach der Anmeldung im
//!   offiziellen Launcher.
//! - In `launcher_profiles*.json` ändert Cubiq nur seine eigenen Einträge `cubiq-<Instanz-ID>`;
//!   alles andere bleibt ungelesen und Byte für Byte erhalten (siehe [`profiles`]).
//! - Anmeldedateien des offiziellen Launchers (`launcher_msa_credentials*`, `launcher_accounts*`)
//!   und Kommandozeilen fremder Programme öffnet Cubiq nie; ob er läuft, steht nur am Prozessnamen.
//!
//! Minimalversion: Vanilla, Fabric und Quilt. Forge/NeoForge lehnt Cubiq mit einer klaren Meldung
//! ab; Vanilla mit Forge-Optimierung geht als echtes Vanilla hinüber. Spielzeit, Backups nach dem
//! Spielen und die Absturzhilfe gibt es auf diesem Weg (noch) nicht – Cubiq sieht das Spiel nicht.

pub mod detect;
pub mod flatpak;
pub mod prefs;
pub mod profiles;
pub mod running;
pub mod start;
pub mod versions;
#[cfg(test)]
mod tests;

use std::path::{Path, PathBuf};
use std::sync::Arc;

use serde::{Deserialize, Serialize};

pub use detect::{Installation, Variant};
pub use prefs::{PlayWith, Prefs};

use crate::instance::{Instance, LoaderKind};
use crate::meta::version::VersionInfo;
use crate::prepare::{ProgressFn, Stage, StageProgress};
use crate::{Error, GameDirReady, Launcher, ModsReady, Result, StartPurpose, client_mod, cubiq_api, fsutil, launch, meta, platform};

/// Stand für die Oberfläche (ohne Pfade – die bleiben im Kern).
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OfficialStatus {
    /// Gibt es diesen Weg auf diesem System (Windows- und Linux-Desktop)?
    pub supported: bool,
    /// Gefundene Ausgaben des offiziellen Launchers.
    pub installed: Vec<Variant>,
    /// Die Ausgabe, die Cubiq öffnet.
    pub preferred: Option<Variant>,
    /// Läuft er gerade (nur am Prozessnamen erkannt)?
    pub running: bool,
    pub play_with: PlayWith,
    /// Die Erklärung vor der ersten Übergabe wurde bestätigt.
    pub explained: bool,
    /// Eingetragene Cubiq-Installationen (für „Entfernen“).
    pub entries: usize,
}

/// Optionen der Übergabe.
#[derive(Debug, Clone, Copy, Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct HandoffOptions {
    /// Der offizielle Launcher läuft, der Spieler will trotzdem fortfahren.
    pub ignore_running: bool,
}

/// Ergebnis der Übergabe. Alles außer `Started`/`OpenManually` passiert, bevor Cubiq etwas ändert.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase", tag = "status")]
pub enum HandoffOutcome {
    /// Eingetragen und geöffnet: dort anmelden, die Installation wählen, „Spielen“.
    #[serde(rename_all = "camelCase")]
    Started { variant: Variant, installation: String },
    /// Eingetragen, aber nicht geöffnet – der Spieler öffnet den Launcher selbst.
    #[serde(rename_all = "camelCase")]
    OpenManually { variant: Variant, installation: String },
    /// Kein offizieller Launcher gefunden.
    NotInstalled,
    /// Installiert, aber noch nie geöffnet (seine Installations-Datei fehlt noch).
    #[serde(rename_all = "camelCase")]
    NeverOpened { variant: Variant, can_open: bool },
    /// Er läuft gerade und könnte den Eintrag überschreiben – erst schließen.
    #[serde(rename_all = "camelCase")]
    Running { variant: Variant },
    /// Flatpak: Die Sandbox darf den Instanzordner noch nicht benutzen – dieser Befehl gibt ihn frei.
    #[serde(rename_all = "camelCase")]
    NeedsAccess { command: String },
}

/// Größe des Symbols im offiziellen Launcher.
const ICON_SIZE: u32 = 128;
/// Cubiq-Symbol für Instanzen ohne eigenes Bild.
const DEFAULT_ICON: &[u8] = include_bytes!("../../../../icons/128x128.png");

/// Name der Installation im offiziellen Launcher.
pub fn installation_name(instance_name: &str) -> String {
    format!("Cubiq · {}", instance_name.trim())
}

fn forge_unsupported() -> Error {
    Error::launch(crate::msg!(
        "officialLauncher.forgeUnsupported",
        "Forge- und NeoForge-Instanzen lassen sich noch nicht mit dem offiziellen Minecraft Launcher spielen."
    ))
}

fn unreadable() -> Error {
    Error::launch(crate::msg!(
        "officialLauncher.unreadable",
        "Die Installationen des Minecraft Launchers ließen sich nicht lesen. Öffne den Minecraft Launcher einmal, schließe ihn und versuche es noch einmal."
    ))
}

fn write_failed() -> Error {
    Error::launch(crate::msg!(
        "officialLauncher.writeFailed",
        "Die Installation ließ sich nicht im Minecraft Launcher eintragen."
    ))
}

fn profiles_error(e: profiles::ProfilesError) -> Error {
    // Ohne Inhalt der Datei – nur die Art des Fehlers.
    tracing::warn!("Installationen des offiziellen Launchers: {e}");
    match e {
        profiles::ProfilesError::Io(_) => write_failed(),
        profiles::ProfilesError::Missing | profiles::ProfilesError::Unreadable(_) => unreadable(),
    }
}

async fn blocking<T: Send + 'static>(f: impl FnOnce() -> T + Send + 'static) -> Result<T> {
    tokio::task::spawn_blocking(f).await.map_err(|e| Error::Internal(e.to_string()))
}

/// Die Umgebung lesen (unter Windows mit Registry-Zugriff – nicht auf der Runtime).
async fn current_env() -> Result<detect::Env> {
    blocking(detect::Env::current).await
}

/// Alle Installations-Dateien, in denen Cubiq-Einträge stehen können.
fn profile_files(found: &[Installation]) -> Vec<PathBuf> {
    let mut files: Vec<PathBuf> = Vec::new();
    for installation in found {
        if !files.contains(&installation.profiles_file) {
            files.push(installation.profiles_file.clone());
        }
    }
    files
}

/// Gleicher Ordner? (Windows: ohne Groß-/Kleinschreibung.)
fn same_dir(a: &str, b: &str) -> bool {
    let (a, b) = (a.trim_end_matches(['/', '\\']), b.trim_end_matches(['/', '\\']));
    if cfg!(windows) { a.eq_ignore_ascii_case(b) } else { a == b }
}

/// Bild der Instanz als PNG-Daten-URL (höchstens 128 px, kleine Pixelbilder scharf vergrößert);
/// ohne eigenes Bild das Cubiq-Symbol.
fn icon_data_url(custom: Option<&Path>) -> String {
    use base64::Engine;
    let png = custom.and_then(scaled_png).unwrap_or_else(|| DEFAULT_ICON.to_vec());
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(png))
}

fn scaled_png(path: &Path) -> Option<Vec<u8>> {
    let bytes = std::fs::read(path).ok().filter(|b| b.len() as u64 <= crate::icon::MAX_ICON_BYTES)?;
    let image = image::load_from_memory(&bytes).ok()?;
    let largest = image.width().max(image.height());
    let image = if largest > ICON_SIZE {
        image.resize(ICON_SIZE, ICON_SIZE, image::imageops::FilterType::Triangle)
    } else if largest <= ICON_SIZE / 2 {
        image.resize(ICON_SIZE, ICON_SIZE, image::imageops::FilterType::Nearest)
    } else {
        image
    };
    let mut png = Vec::new();
    image.write_to(&mut std::io::Cursor::new(&mut png), image::ImageFormat::Png).ok()?;
    Some(png)
}

impl Launcher {
    /// Stand für die Oberfläche: gefundene Ausgaben, Wahl, Erklärung gesehen, eingetragene Installationen.
    pub async fn official_launcher_status(&self) -> OfficialStatus {
        let prefs = prefs::load(self.paths.root()).await;
        let supported = !platform::MOBILE && cfg!(any(windows, target_os = "linux"));
        if !supported {
            return OfficialStatus {
                supported,
                installed: Vec::new(),
                preferred: None,
                running: false,
                play_with: prefs.play_with,
                explained: prefs.explained,
                entries: 0,
            };
        }
        let scan = blocking(|| {
            let found = detect::detect(&detect::Env::current());
            let entries: usize =
                profile_files(&found).iter().map(|f| profiles::own_keys(f).map(|k| k.len()).unwrap_or(0)).sum();
            let mut installed: Vec<Variant> = Vec::new();
            for installation in &found {
                if !installed.contains(&installation.variant) {
                    installed.push(installation.variant);
                }
            }
            let preferred = detect::choose(&found).map(|i| i.variant);
            (installed, preferred, running::is_running(), entries)
        })
        .await;
        let (installed, preferred, running, entries) = scan.unwrap_or_default();
        OfficialStatus { supported, installed, preferred, running, play_with: prefs.play_with, explained: prefs.explained, entries }
    }

    /// Wahl „Spiele starten mit“ bzw. „Erklärung gesehen“ speichern (`None` = unverändert).
    pub async fn set_official_prefs(&self, play_with: Option<PlayWith>, explained: Option<bool>) -> Result<Prefs> {
        let mut prefs = prefs::load(self.paths.root()).await;
        if let Some(play_with) = play_with {
            prefs.play_with = play_with;
        }
        if let Some(explained) = explained {
            prefs.explained = explained;
        }
        prefs::save(self.paths.root(), &prefs).await?;
        Ok(prefs)
    }

    /// Nur öffnen (z. B. damit er seine Dateien beim ersten Mal anlegt). `false`: nicht startbar.
    pub async fn open_official_launcher(&self) -> Result<bool> {
        platform::desktop_only()?;
        let env = current_env().await?;
        let found = detect::detect(&env);
        let Some(cmd) = detect::choose(&found).and_then(|i| i.start.clone()) else { return Ok(false) };
        Ok(blocking(move || start::spawn(&cmd)).await?.is_ok())
    }

    /// Entfernt alle Cubiq-Installationen aus dem offiziellen Launcher; liefert, wie viele.
    pub async fn remove_official_profiles(&self) -> Result<usize> {
        platform::desktop_only()?;
        let env = current_env().await?;
        let files = profile_files(&detect::detect(&env));
        blocking(move || {
            let mut removed = 0;
            for file in files {
                removed += profiles::remove(&file, |key, _| key.starts_with(profiles::KEY_PREFIX)).map_err(profiles_error)?;
            }
            Ok(removed)
        })
        .await?
    }

    /// Instanz gelöscht: ihren Eintrag gleich mit entfernen – nur, wenn er noch auf ihren Spielordner
    /// zeigt (eine zweite Cubiq-Installation könnte dieselbe Instanz-ID haben). Fehler landen im Log.
    pub(crate) async fn forget_official_profile(&self, instance_id: &str, game_dir: &Path) {
        if platform::MOBILE {
            return;
        }
        let Ok(env) = current_env().await else { return };
        let files = profile_files(&detect::detect(&env));
        let key = profiles::key_for(instance_id);
        let expected = game_dir.to_string_lossy().into_owned();
        let result = blocking(move || {
            for file in files {
                let removed = profiles::remove(&file, |k, value| {
                    k == key
                        && value.get("gameDir").and_then(serde_json::Value::as_str).is_none_or(|dir| same_dir(dir, &expected))
                });
                match removed {
                    Ok(0) => {}
                    Ok(n) => tracing::info!("{n} Eintrag/Einträge im offiziellen Launcher entfernt"),
                    Err(e) => tracing::warn!("Eintrag im offiziellen Launcher nicht entfernt: {e}"),
                }
            }
        })
        .await;
        if let Err(e) = result {
            tracing::warn!("Eintrag im offiziellen Launcher nicht entfernt: {e}");
        }
    }

    /// Bereitet die Instanz vor, trägt sie im offiziellen Minecraft Launcher ein und öffnet ihn.
    pub async fn play_with_official_launcher(
        self: &Arc<Self>,
        instance_id: &str,
        options: HandoffOptions,
        on_progress: &ProgressFn,
    ) -> Result<HandoffOutcome> {
        platform::desktop_only()?;
        let instance = self.instances.get(instance_id).await?;
        // Forge/NeoForge brauchen Dateien, die nur Cubiqs eigener Installer erzeugt – kommt später.
        if matches!(instance.loader.kind, LoaderKind::Forge | LoaderKind::NeoForge) {
            return Err(forge_unsupported());
        }
        // Erst prüfen, dann ändern: Ist er da, schon einmal geöffnet, gerade offen, darf er hinein?
        let env = current_env().await?;
        let found = detect::detect(&env);
        let Some(chosen) = detect::choose(&found).cloned() else { return Ok(HandoffOutcome::NotInstalled) };
        if !chosen.profiles_exist() {
            return Ok(HandoffOutcome::NeverOpened { variant: chosen.variant, can_open: chosen.start.is_some() });
        }
        if !options.ignore_running && blocking(running::is_running).await? {
            return Ok(HandoffOutcome::Running { variant: chosen.variant });
        }
        if chosen.variant == Variant::LinuxFlatpak
            && let Some(command) = self.flatpak_access_command(&instance, &env)
        {
            return Ok(HandoffOutcome::NeedsAccess { command });
        }
        if self.games.is_running(&instance.id) {
            return Err(Error::launch(crate::msg!("launcher.alreadyRunning", "Diese Instanz läuft bereits.")));
        }
        if self.is_preparing(crate::relocate::DATA_MOVE_GUARD) {
            return Err(crate::relocate::data_moving());
        }
        {
            let mut preparing = self.preparing.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
            if !preparing.insert(instance.id.clone()) {
                return Err(Error::launch(crate::msg!("launcher.alreadyStarting", "Diese Instanz wird bereits gestartet.")));
            }
        }
        let result = self.hand_off(&instance, &found, &chosen, on_progress).await;
        self.preparing.lock().unwrap_or_else(std::sync::PoisonError::into_inner).remove(&instance.id);
        result
    }

    /// Flatpak: Befehl, der die noch fehlenden Ordner freigibt (`None` = alles freigegeben).
    fn flatpak_access_command(&self, instance: &Instance, env: &detect::Env) -> Option<String> {
        let home = env.home.as_deref()?;
        let data_home = env.data_home.clone().unwrap_or_else(|| home.join(".local").join("share"));
        let mut needed = vec![self.paths.instance_game_dir(&instance.id)];
        // Gemeinsame Ordner sind Links nach `<Daten>/shared-folders/` – auch die braucht die Sandbox.
        if !instance.overrides.shared_folders.is_empty() {
            needed.push(self.paths.shared_folders_dir());
        }
        let missing = flatpak::missing(&needed, &env.flatpak_roots, home, &data_home);
        (!missing.is_empty()).then(|| flatpak::override_command(&missing))
    }

    async fn hand_off(
        self: &Arc<Self>,
        instance: &Instance,
        found: &[Installation],
        chosen: &Installation,
        on_progress: &ProgressFn,
    ) -> Result<HandoffOutcome> {
        let settings = self.settings().await;
        let purpose = StartPurpose { official: true, ..StartPurpose::default() };
        let ModsReady { effective, catalog, cubiq_enabled } =
            self.prepare_mods(instance, &settings, purpose, on_progress).await?;

        on_progress(StageProgress::begin(Stage::Version));
        let vanilla = self.official_vanilla(&effective.game_version).await?;
        let version_id = self.official_version_id(&effective, &chosen.data_dir).await?;

        // Letzte Gelegenheit zum Abbrechen – ab hier wird eingetragen.
        crate::task::checkpoint().await?;
        on_progress(StageProgress::begin(Stage::Starting));
        // Spielordner wie vor jedem Start. Das Vanilla-Jar (für die Datenversion der Standard-Optionen)
        // liegt bei Cubiq, sonst vielleicht schon beim offiziellen Launcher.
        let own_jar = self.paths.version_jar(&effective.game_version);
        let jar = if own_jar.is_file() {
            own_jar
        } else {
            chosen.data_dir.join("versions").join(&effective.game_version).join(format!("{}.jar", effective.game_version))
        };
        let GameDirReady { game_dir, .. } = self.prepare_game_dir(&effective, &settings, None, false, &jar).await?;
        let game_dir = game_dir.to_str().map(str::to_owned).ok_or_else(write_failed)?;

        // Cubiq Client: dieselben JVM-Argumente wie beim eigenen Start, „Schnell verbinden“ ohne Ziel.
        let client_build = client_mod::build_for(catalog.builds(), effective.loader.kind, &effective.game_version)
            .filter(|_| effective.overrides.cubiq_client != Some(false));
        let mut extra = Vec::new();
        if let Some(build) = client_build {
            extra = client_mod::bundled_jvm_args(&self.paths, build, &effective).await;
            if cubiq_enabled {
                extra.extend(cubiq_api::client_mod_jvm_arg());
            }
        }
        self.write_connect_hints(&effective.id, client_build.is_some(), None, None).await;
        let java_major = vanilla.and_then(|v| v.java_version).map_or(8, |j| j.major_version);
        let java_args = launch::profile_jvm_args(&effective, &settings, java_major, platform::total_memory_mb(), extra).join(" ");

        let now = chrono::Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Millis, true);
        let resolution = effective.overrides.resolution.unwrap_or(settings.resolution);
        let custom_icon = self.instance_icon_path(instance);
        let icon = blocking(move || icon_data_url(custom_icon.as_deref())).await?;
        let installation = installation_name(&instance.name);
        let entry = profiles::Entry {
            name: installation.clone(),
            kind: "custom",
            created: now.clone(),
            last_used: now,
            icon,
            last_version_id: version_id,
            game_dir,
            java_args,
            resolution: Some(profiles::Resolution { width: resolution.width, height: resolution.height }),
        };
        let key = profiles::key_for(&instance.id);
        let files: Vec<PathBuf> = detect::write_targets(found, chosen).iter().map(|i| i.profiles_file.clone()).collect();
        blocking(move || {
            for file in &files {
                profiles::upsert(file, &key, &entry).map_err(profiles_error)?;
            }
            Ok::<_, Error>(())
        })
        .await??;
        tracing::info!("'{}' im offiziellen Launcher ({:?}) eingetragen", instance.id, chosen.variant);

        let started = match chosen.start.clone() {
            Some(cmd) => match blocking(move || start::spawn(&cmd)).await? {
                Ok(()) => true,
                Err(e) => {
                    tracing::warn!("Offizieller Launcher ließ sich nicht starten: {e}");
                    false
                }
            },
            None => false,
        };
        let variant = chosen.variant;
        Ok(if started {
            HandoffOutcome::Started { variant, installation }
        } else {
            HandoffOutcome::OpenManually { variant, installation }
        })
    }

    /// Vanilla-Version (Java-Hauptversion für die JVM-Argumente). Offline ohne Cache: `None`.
    async fn official_vanilla(&self, game_version: &str) -> Result<Option<VersionInfo>> {
        match meta::manifest::fetch(&self.http, &self.paths, false).await {
            Ok(manifest) => {
                let entry = manifest.find(game_version).ok_or_else(|| Error::UnknownGameVersion(game_version.to_owned()))?;
                match meta::version::fetch_vanilla(&self.http, &self.paths, entry).await {
                    Ok(info) => Ok(Some(info)),
                    Err(Error::Cancelled) => Err(Error::Cancelled),
                    Err(e) => {
                        tracing::warn!("Vanilla-Version {game_version} nicht geladen: {e}");
                        Ok(fsutil::read_json(&self.paths.version_json(game_version)).await.ok().flatten())
                    }
                }
            }
            Err(Error::Cancelled) => Err(Error::Cancelled),
            Err(_) => Ok(fsutil::read_json(&self.paths.version_json(game_version)).await.ok().flatten()),
        }
    }

    /// `lastVersionId` der Installation: Vanilla direkt, Fabric/Quilt über ihr Loader-Profil.
    async fn official_version_id(&self, effective: &Instance, data_dir: &Path) -> Result<String> {
        let prefix = match effective.loader.kind {
            LoaderKind::Vanilla => return Ok(effective.game_version.clone()),
            LoaderKind::Fabric => "fabric-loader-",
            LoaderKind::Quilt => "quilt-loader-",
            LoaderKind::Forge | LoaderKind::NeoForge => return Err(forge_unsupported()),
        };
        let unavailable = || {
            Error::launch(crate::msg!(
                "officialLauncher.versionUnavailable",
                "Das Loader-Profil für den Minecraft Launcher ließ sich nicht laden – bitte prüfe die Internetverbindung."
            ))
        };
        match crate::loaders::fetch_profile_raw(&self.http, &effective.game_version, &effective.loader).await {
            Ok(raw) => {
                let (dir, game_version) = (data_dir.to_owned(), effective.game_version.clone());
                blocking(move || versions::place(&dir, &raw, &game_version)).await?.map_err(|e| {
                    tracing::warn!("Loader-Profil nicht abgelegt: {e}");
                    unavailable()
                })
            }
            Err(Error::Cancelled) => Err(Error::Cancelled),
            Err(e) => {
                // Offline: ein schon abgelegtes Profil derselben Version tut es auch.
                tracing::warn!("Loader-Profil nicht geladen: {e}");
                versions::existing(data_dir, prefix, effective.loader.version.as_deref(), &effective.game_version)
                    .ok_or_else(unavailable)
            }
        }
    }
}
