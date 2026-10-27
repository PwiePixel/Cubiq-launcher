//! Cubiq Client: unser eigener In-Game-Mod (HUD, Zoom, Fullbright, Menü).
//! Der Launcher bringt die Jars samt `builds.json` mit und legt beim Start
//! automatisch den passenden Build in jede Instanz – inklusive benötigter
//! Abhängigkeiten wie Fabric API. Neuere Versionen kommen über den eigenen
//! Update-Kanal ([`crate::client_mod_update`]) auch ohne Launcher-Update.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::client_mod_update::{self, ClientModUpdater};
use crate::content::{self, ContentKind};
use crate::download;
use crate::history::{self, HistoryEntry, HistoryKind};
use crate::instance::{Instance, LoaderKind};
use crate::paths::Paths;
use crate::settings::{Accent, Motion, Theme, UiSettings};
use crate::{Error, Result, fsutil, modrinth};

/// So heißt die Datei im Mods-Ordner – fester Name, damit Updates sie ersetzen.
const INSTALLED_NAME: &str = "cubiqclient.jar";
const FABRIC_API_PROJECT: &str = "P7dR8mSH";
const MANIFEST: &str = "builds.json";
/// Zuletzt in die Instanz gelegte Cubiq-Client-Version (für den Verlauf).
const VERSION_MARKER: &str = "cubiqclient-version";
/// Farben des Launchers für das In-Game-Menü (relativ zum Config-Ordner der Instanz).
const THEME_FILE: &str = "cubiqclient/launcher-theme.json";
const CUBIQ_API_FILE: &str = "cubiqclient/cubiq-api.json";

/// Ein Build aus dem Manifest (erzeugt von `scripts/publish-client-mod.mjs`
/// aus den `collectLauncherJars`-Ausgaben).
#[derive(Debug, Clone, Deserialize)]
pub struct Build {
    /// `fabric`, `forge`, `neoforge`
    pub loader: String,
    /// Alle exakten Spielversionen, die dieser Jar unterstützt.
    pub minecraft: Vec<String>,
    pub file: String,
    #[serde(default)]
    pub requires: Vec<String>,
    /// Eingebaute Optimierungs-Mods (Jar-in-Jar, nur Fabric) – das FPS-Boost-Preset
    /// lässt sie weg, und abschalten lassen sie sich nur beim Start (siehe [`bundled_jvm_args`]).
    #[serde(default)]
    pub bundled: Vec<BundledMod>,
    /// Pflicht im Update-Kanal, in der mitgelieferten Datei zur Kontrolle.
    #[serde(default)]
    pub sha256: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    /// Stammt aus dem Update-Kanal (nicht Teil der Datei).
    #[serde(skip)]
    pub from_channel: bool,
}

/// Eine im Cubiq Client eingebettete Optimierungs-Mod (`id` = Mod-ID aus fabric.mod.json).
#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct BundledMod {
    pub id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub version: String,
}

/// `builds.json` bzw. `client-mod.json`: `{ "version": "0.3.0", "builds": [...] }`.
/// Die alte Form (nur die Liste, ohne Version) wird weiter gelesen.
#[derive(Debug, Clone, Default)]
pub struct Manifest {
    /// Version des Mods (`mod_version` aus client-mod/gradle.properties); leer = unbekannt.
    pub version: String,
    pub builds: Vec<Build>,
}

/// Liest ein Manifest in neuer oder alter Form; Builds mit unsicheren
/// Dateinamen fallen heraus. `None`, wenn es gar nicht lesbar ist.
pub fn parse_manifest(bytes: &[u8]) -> Option<Manifest> {
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum Raw {
        Current { version: String, builds: Vec<Build> },
        Legacy(Vec<Build>),
    }
    let (version, mut builds) = match serde_json::from_slice::<Raw>(bytes).ok()? {
        Raw::Current { version, builds } => (version, builds),
        Raw::Legacy(builds) => (String::new(), builds),
    };
    // Dateinamen stammen aus unserer eigenen Datei – trotzdem nur einfache Namen zulassen.
    builds.retain(|b| validate_build_file(&b.file).is_ok());
    Some(Manifest { version, builds })
}

/// Eingeschalteter Cubiq Client im Mods-Ordner der Instanz (fehlt er, lädt das Spiel ihn nicht).
pub fn installed_jar(paths: &Paths, instance_id: &str) -> PathBuf {
    content::content_dir(paths, instance_id, ContentKind::Mod).join(INSTALLED_NAME)
}

/// Ist das die Datei, die der Launcher selbst für den Cubiq Client verwaltet?
pub fn is_client_mod_file(kind: ContentKind, file_name: &str) -> bool {
    kind == ContentKind::Mod && file_name == INSTALLED_NAME
}

/// Nur einfache Jar-Namen ohne Pfadanteile.
pub fn validate_build_file(file: &str) -> Result<()> {
    content::validate_file_name(ContentKind::Mod, file)
}

/// Das mitgelieferte Manifest aus dem Ressourcen-Ordner; kaputt oder fehlend = leer.
pub fn load_manifest(dir: &Path) -> Manifest {
    std::fs::read(dir.join(MANIFEST)).ok().and_then(|b| parse_manifest(&b)).unwrap_or_default()
}

/// Was der Launcher gerade vom Cubiq Client kennt: die mitgelieferten Builds und –
/// falls neuer – die aus dem Update-Kanal.
#[derive(Debug, Clone, Default)]
pub struct Catalog {
    bundled_dir: Option<PathBuf>,
    bundled: Manifest,
    channel: Option<Manifest>,
    /// Kanal-Builds zuerst, danach die mitgelieferten (Rückfall für Versionen,
    /// die der Kanal nicht oder nicht mehr hat).
    merged: Vec<Build>,
}

impl Catalog {
    pub async fn load(bundled_dir: Option<&Path>, updater: Option<&ClientModUpdater>) -> Self {
        let bundled = bundled_dir.map(load_manifest).unwrap_or_default();
        let channel = match updater {
            Some(u) => u.stored().await.filter(|c| client_mod_update::is_newer(&c.version, &bundled.version)),
            None => None,
        };
        let merged = channel
            .iter()
            .flat_map(|c| c.builds.iter().cloned().map(|b| Build { from_channel: true, ..b }))
            .chain(bundled.builds.iter().cloned())
            .collect();
        Self { bundled_dir: bundled_dir.map(Path::to_owned), bundled, channel, merged }
    }

    /// Alle bekannten Builds, neueste Quelle zuerst.
    pub fn builds(&self) -> &[Build] {
        &self.merged
    }

    pub fn bundled_version(&self) -> Option<&str> {
        Some(self.bundled.version.as_str()).filter(|v| !v.is_empty())
    }

    /// Kanal-Version, sofern neuer als die mitgelieferte.
    pub fn channel_version(&self) -> Option<&str> {
        self.channel.as_ref().map(|c| c.version.as_str())
    }

    pub fn status(&self) -> ClientModStatus {
        match (self.bundled_version(), self.channel_version()) {
            // Ohne mitgelieferten Client (Android/iOS) ist der Kanal der aktuelle Stand, kein Update.
            (None, Some(channel)) => ClientModStatus { bundled: Some(channel.to_owned()), update: None },
            (bundled, update) => ClientModStatus { bundled: bundled.map(str::to_owned), update: update.map(str::to_owned) },
        }
    }

    /// Jar-Datei für die Instanz: bevorzugt aus dem Kanal (lädt bei Bedarf),
    /// bei jedem Fehler aus dem Launcher-Paket.
    /// `installed`: die Kopie in der Instanz – ist sie schon genau dieser Kanal-Build,
    /// bleibt sie auch dann, wenn der Cache fehlt und gerade nichts ladbar ist.
    async fn resolve(
        &self,
        updater: Option<&ClientModUpdater>,
        kind: LoaderKind,
        game_version: &str,
        installed: &Path,
    ) -> Option<Resolved> {
        if let (Some(channel), Some(updater)) = (&self.channel, updater)
            && let Some(build) = build_for(&self.merged, kind, game_version).filter(|b| b.from_channel)
        {
            let resolved = |path| Resolved { path, version: channel.version.clone(), build: build.clone() };
            match updater.jar(channel, build).await {
                Ok(path) => return Some(resolved(path)),
                Err(e) => {
                    if let (Some(sha256), Some(size)) = (build.sha256.as_deref(), build.size)
                        && client_mod_update::file_matches(installed, sha256, size).await
                    {
                        return Some(resolved(installed.to_owned()));
                    }
                    tracing::warn!("Cubiq Client {} nicht ladbar, nehme den mitgelieferten: {e}", channel.version);
                }
            }
        }
        let dir = self.bundled_dir.as_deref()?;
        let build = build_for(&self.bundled.builds, kind, game_version)?;
        let path = dir.join(&build.file);
        if !path.is_file() {
            tracing::warn!("Cubiq Client fehlt im Launcher-Paket: {}", path.display());
            return None;
        }
        Some(Resolved { path, version: self.bundled.version.clone(), build: build.clone() })
    }
}

/// Für die Einstellungen: mitgelieferte Version und – falls neuer – die aus dem Kanal.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientModStatus {
    pub bundled: Option<String>,
    /// Neuere, geprüfte Version aus dem Update-Kanal (wird beim nächsten Start verwendet).
    pub update: Option<String>,
}

struct Resolved {
    path: PathBuf,
    version: String,
    build: Build,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Availability {
    Available,
    Unsupported,
    Disabled,
}

/// Builds aus `builds.json` im Ressourcen-Ordner; kaputt oder fehlend = leer.
pub fn load_builds(dir: &Path) -> Vec<Build> {
    load_manifest(dir).builds
}

fn loader_matches(build: &str, kind: LoaderKind) -> bool {
    match build {
        // Quilt lädt Fabric-Mods.
        "fabric" => matches!(kind, LoaderKind::Fabric | LoaderKind::Quilt),
        "forge" => kind == LoaderKind::Forge,
        "neoforge" => kind == LoaderKind::NeoForge,
        _ => false,
    }
}

pub fn build_for<'a>(builds: &'a [Build], kind: LoaderKind, game_version: &str) -> Option<&'a Build> {
    builds.iter().find(|b| loader_matches(&b.loader, kind) && b.minecraft.iter().any(|v| v == game_version))
}

/// Für welchen Loader es einen Cubiq-Client-Build für diese Version gibt
/// (bevorzugt Fabric) – für die Cubiq-Optimierung von Vanilla-Instanzen.
pub fn boost_loader(builds: &[Build], game_version: &str) -> Option<LoaderKind> {
    [LoaderKind::Fabric, LoaderKind::Forge, LoaderKind::NeoForge]
        .into_iter()
        .find(|&k| build_for(builds, k, game_version).is_some())
}

pub fn availability(builds: &[Build], instance: &Instance) -> Availability {
    if instance.overrides.cubiq_client == Some(false) {
        Availability::Disabled
    } else if build_for(builds, instance.loader.kind, &instance.game_version).is_some() {
        Availability::Available
    } else {
        Availability::Unsupported
    }
}

/// Sorgt vor dem Start dafür, dass der Cubiq Client (und seine Abhängigkeiten)
/// in der Instanz liegt – bzw. entfernt ihn, wenn er abgeschaltet wurde oder
/// für die Version keinen Build mehr hat. Quelle ist die neueste geprüfte
/// Version: Update-Kanal (falls neuer und ladbar), sonst das Launcher-Paket.
/// `system_reduced_motion`: „Bewegung reduzieren“ des Systems, wie die Oberfläche es meldet
/// (löst „Wie System“ der Animationen für den Mod auf, siehe [`write_theme`]).
#[allow(clippy::too_many_arguments)]
pub async fn sync(
    http: &reqwest::Client,
    paths: &Paths,
    bundled_dir: Option<&Path>,
    updater: Option<&ClientModUpdater>,
    instance: &Instance,
    ui: &UiSettings,
    system_reduced_motion: bool,
    cubiq_api: bool,
    events: &[String],
    look: &[String],
) -> Result<()> {
    let catalog = Catalog::load(bundled_dir, updater).await;
    // Weder Launcher-Paket noch Kanal bekannt: nichts anfassen.
    if bundled_dir.is_none() && catalog.channel.is_none() {
        return Ok(());
    }
    let mods = content::content_dir(paths, &instance.id, ContentKind::Mod);
    let target = mods.join(INSTALLED_NAME);
    let disabled_copy = mods.join(format!("{INSTALLED_NAME}.disabled"));

    let build = build_for(catalog.builds(), instance.loader.kind, &instance.game_version);
    if build.is_none() || instance.overrides.cubiq_client == Some(false) {
        // Abgeschaltet oder nicht passend (z. B. nach Versionswechsel): alte Kopie weg.
        for file in [&target, &disabled_copy] {
            if file.is_file() {
                tokio::fs::remove_file(file).await.map_err(|e| Error::io(file, e))?;
            }
        }
        for file in [
            theme_path(paths, &instance.id),
            cubiq_api_path(paths, &instance.id),
            connect_hints_path(paths, &instance.id),
            paths.instance_dir(&instance.id).join(VERSION_MARKER),
        ] {
            if file.is_file() {
                let _ = tokio::fs::remove_file(&file).await;
            }
        }
        return Ok(());
    }

    // Es gibt einen Build, aber keine Datei (Kanal-Jar nicht ladbar und nichts
    // mitgeliefert): Instanz so lassen, wie sie ist.
    let Some(Resolved { path: source, version, build }) =
        catalog.resolve(updater, instance.loader.kind, &instance.game_version, &target).await
    else {
        return Ok(());
    };
    // In der Mod-Liste deaktiviert (cubiqclient.jar.disabled): Das bleibt so – nur die
    // deaktivierte Kopie wird aktuell gehalten, damit ein späteres Einschalten passt.
    if disabled_copy.is_file() && !target.exists() {
        if !same_file(&source, &disabled_copy).await {
            tokio::fs::copy(&source, &disabled_copy).await.map_err(|e| Error::io(&disabled_copy, e))?;
        }
        return Ok(());
    }
    // Nur kopieren, wenn sich etwas geändert hat (neue Version aus Kanal oder Launcher-Update).
    let marker = paths.instance_dir(&instance.id).join(VERSION_MARKER);
    if !same_file(&source, &target).await {
        let replaced = target.is_file();
        tokio::fs::create_dir_all(&mods).await.map_err(|e| Error::io(&mods, e))?;
        tokio::fs::copy(&source, &target).await.map_err(|e| Error::io(&target, e))?;
        let _ = tokio::fs::remove_file(&disabled_copy).await;
        let label = if version.is_empty() { build.file.clone() } else { format!("{} {version}", build.file) };
        tracing::info!("Cubiq Client ({label}) in '{}' installiert", instance.id);
        let previous = tokio::fs::read_to_string(&marker).await.ok().map(|v| v.trim().to_owned());
        if replaced && !version.is_empty() && previous.as_deref() != Some(version.as_str()) {
            // Im Verlauf der Instanz sichtbar: "Cubiq Client aktualisiert 0.2.0 → 0.3.0".
            let mut entry = HistoryEntry::new(HistoryKind::ModUpdated).subject("Cubiq Client").to(&version);
            if let Some(previous) = previous.filter(|p| client_mod_update::is_valid_version(p)) {
                if client_mod_update::is_newer(&previous, &version) {
                    entry = entry.detail("downgrade");
                }
                entry = entry.from(previous);
            }
            history::record(paths, &instance.id, entry).await;
        }
    }
    if !version.is_empty() && tokio::fs::read_to_string(&marker).await.ok().as_deref() != Some(version.as_str()) {
        let _ = fsutil::write_atomic(&marker, version.as_bytes()).await;
    }

    // Der Mod übernimmt Thema, Akzentfarbe und Animationen des Launchers.
    if let Err(e) = write_theme(paths, &instance.id, ui, system_reduced_motion, events, look).await {
        tracing::warn!("Farben für den Cubiq Client konnten nicht geschrieben werden: {e}");
    }
    // Und ob er die Cubiq API benutzen darf (Einwilligung im Launcher).
    if let Err(e) = write_cubiq_api_config(paths, &instance.id, cubiq_api).await {
        tracing::warn!("cubiq-api.json für den Cubiq Client konnte nicht geschrieben werden: {e}");
    }

    if build.requires.iter().any(|r| r == "fabric-api") {
        ensure_fabric_api(http, paths, instance).await;
    }
    Ok(())
}

/// Version des Cubiq Client, die der Launcher zuletzt in die Instanz kopiert hat
/// (`None` = unbekannt, z. B. nie installiert).
pub async fn installed_version(paths: &Paths, instance_id: &str) -> Option<String> {
    let marker = paths.instance_dir(instance_id).join(VERSION_MARKER);
    let version = tokio::fs::read_to_string(&marker).await.ok()?.trim().to_owned();
    client_mod_update::is_valid_version(&version).then_some(version)
}

/// Kennt die installierte Mod nur das alte Clip-Token in `clips.json`
/// (Cubiq Client ≤ 0.5.0, Protokoll v1)?
pub async fn installed_is_legacy(paths: &Paths, instance_id: &str) -> bool {
    installed_version(paths, instance_id)
        .await
        .is_some_and(|v| !client_mod_update::is_newer(&v, crate::link::LEGACY_MOD_MAX))
}

/// Erste Cubiq-Client-Version, die Sozial-Hinweise (Chat, Anfragen, Einladungen …)
/// selbst im Spiel zeigt.
pub const SOCIAL_MOD_MIN: &str = "0.8.0";

/// Zeigt der Cubiq Client dieser Version Sozial-Hinweise selbst im Spiel?
/// Unbekannte Version (`None`, z. B. von Hand eingelegt) zählt als ja – ein
/// Spiel, das sich über Cubiq Link v2 meldet, ist mindestens 0.6.
pub fn shows_social_in_game(version: Option<&str>) -> bool {
    version.is_none_or(|v| !client_mod_update::is_newer(SOCIAL_MOD_MIN, v))
}

/// Modul „Eingebaute Optimierungen“ des Cubiq Clients (`config/cubiqclient.json`); fehlt es, ist es an.
pub async fn builtin_optimizations_enabled(game_dir: &Path) -> bool {
    let file = game_dir.join("config").join("cubiqclient.json");
    let Ok(bytes) = tokio::fs::read(&file).await else { return true };
    serde_json::from_slice::<serde_json::Value>(&bytes)
        .ok()
        .and_then(|v| v.pointer("/modules/builtinOptimizations/enabled").and_then(serde_json::Value::as_bool))
        .unwrap_or(true)
}

/// Mod-IDs, die der Cubiq Client in dieser Instanz eingebaut mitbringt – leer, wenn er
/// dort aus ist, es keinen Build gibt oder der Spieler „Eingebaute Optimierungen“
/// ausgeschaltet hat. Das FPS-Boost-Preset lässt diese Mods weg.
pub async fn builtin_mod_ids(paths: &Paths, builds: &[Build], instance: &Instance) -> Vec<String> {
    builtin_mods(paths, builds, instance).await.into_iter().map(|b| b.id).collect()
}

/// Wie [`builtin_mod_ids`], aber mit Namen und Version (für die Verträglichkeitsprüfung
/// des Presets: eingebaute Mods zählen dort als vorhanden).
pub async fn builtin_mods(paths: &Paths, builds: &[Build], instance: &Instance) -> Vec<BundledMod> {
    if instance.overrides.cubiq_client == Some(false) {
        return Vec::new();
    }
    let Some(build) = build_for(builds, instance.loader.kind, &instance.game_version) else { return Vec::new() };
    if build.bundled.is_empty() || !builtin_optimizations_enabled(&paths.instance_game_dir(&instance.id)).await {
        return Vec::new();
    }
    build.bundled.clone()
}

/// Mod-IDs (fabric.mod.json) der eingeschalteten Mods im Mods-Ordner – ohne den Cubiq Client selbst.
async fn own_mod_ids(mods: &Path) -> Vec<String> {
    let dir = mods.to_owned();
    tokio::task::spawn_blocking(move || {
        let mut ids = Vec::new();
        let Ok(entries) = std::fs::read_dir(&dir) else { return ids };
        for entry in entries.flatten().take(2000) {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_lowercase();
            if !name.ends_with(".jar") || name == INSTALLED_NAME {
                continue;
            }
            let Some(id) = std::fs::File::open(&path).ok().and_then(|f| zip::ZipArchive::new(f).ok()).and_then(|mut z| {
                let entry = z.by_name("fabric.mod.json").ok()?;
                let json: serde_json::Value = serde_json::from_reader(std::io::Read::take(entry, 256 * 1024)).ok()?;
                json.get("id")?.as_str().map(str::to_owned)
            }) else {
                continue;
            };
            ids.push(id);
        }
        ids
    })
    .await
    .unwrap_or_default()
}

/// JVM-Argumente für die eingebauten Optimierungen: Hat der Spieler das Modul im
/// Cubiq-Menü ausgeschaltet, lässt Fabric die eingebetteten Mods weg
/// (`-Dfabric.debug.disableModIds`) – außer denen, die er selbst in den Mods-Ordner
/// gelegt hat (die IDs sind gleich, Fabric würde sonst auch seine abschalten).
pub async fn bundled_jvm_args(paths: &Paths, build: &Build, instance: &Instance) -> Vec<String> {
    if build.bundled.is_empty() || !loader_matches("fabric", instance.loader.kind) {
        return Vec::new();
    }
    if builtin_optimizations_enabled(&paths.instance_game_dir(&instance.id)).await {
        return Vec::new();
    }
    let own = own_mod_ids(&content::content_dir(paths, &instance.id, ContentKind::Mod)).await;
    disable_arg(&build.bundled, &own).into_iter().collect()
}

fn disable_arg(bundled: &[BundledMod], own: &[String]) -> Option<String> {
    let ids: Vec<&str> = bundled
        .iter()
        .map(|b| b.id.as_str())
        .filter(|id| !own.iter().any(|o| o == id))
        // Mod-IDs sind [a-z0-9_-]; alles andere wäre ein kaputter Eintrag.
        .filter(|id| !id.is_empty() && id.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'_' || b == b'-'))
        .collect();
    (!ids.is_empty()).then(|| format!("-Dfabric.debug.disableModIds={}", ids.join(",")))
}

/// Grafik-Modus des Cubiq Clients: „Schön“ (nur Leistungs-Schalter ohne Optik-Verlust)
/// oder „Max FPS“. Der Mod wendet ihn beim nächsten Start an (nur Werte, die der
/// Spieler nicht selbst geändert hat) – Datei `config/cubiqclient/fps-mode.json`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum FpsMode {
    Pretty,
    Max,
}

const FPS_MODE_FILE: &str = "cubiqclient/fps-mode.json";

fn fps_mode_path(paths: &Paths, instance_id: &str) -> std::path::PathBuf {
    paths.instance_game_dir(instance_id).join("config").join(FPS_MODE_FILE)
}

/// Gewählter Grafik-Modus; `None` = noch nie gewählt (weder im Spiel noch im Launcher).
pub async fn fps_mode(paths: &Paths, instance_id: &str) -> Option<FpsMode> {
    let bytes = tokio::fs::read(fps_mode_path(paths, instance_id)).await.ok()?;
    let json: serde_json::Value = serde_json::from_slice(&bytes).ok()?;
    if !json.get("chosen").and_then(serde_json::Value::as_bool).unwrap_or(false) {
        return None;
    }
    match json.get("mode").and_then(serde_json::Value::as_str) {
        Some("max") => Some(FpsMode::Max),
        _ => Some(FpsMode::Pretty),
    }
}

/// Grafik-Modus setzen. Was der Mod sonst in der Datei führt (was er selbst gesetzt
/// hat), bleibt erhalten – nur `mode`/`chosen` gehören dem Launcher.
pub async fn set_fps_mode(paths: &Paths, instance_id: &str, mode: FpsMode) -> Result<()> {
    let file = fps_mode_path(paths, instance_id);
    let mut json = tokio::fs::read(&file)
        .await
        .ok()
        .and_then(|b| serde_json::from_slice::<serde_json::Value>(&b).ok())
        .filter(serde_json::Value::is_object)
        .unwrap_or_else(|| serde_json::json!({ "version": 1 }));
    let object = json.as_object_mut().expect("Objekt");
    object.insert("mode".into(), serde_json::to_value(mode).map_err(|e| Error::json("fps-mode.json", e))?);
    object.insert("chosen".into(), serde_json::Value::Bool(true));
    let text = serde_json::to_string_pretty(&json).map_err(|e| Error::json("fps-mode.json", e))?;
    fsutil::write_atomic(&file, text.as_bytes()).await
}

/// Datei mit den Farben des Launchers in der Instanz.
fn theme_path(paths: &Paths, instance_id: &str) -> std::path::PathBuf {
    paths.instance_game_dir(instance_id).join("config").join(THEME_FILE)
}

fn cubiq_api_path(paths: &Paths, instance_id: &str) -> std::path::PathBuf {
    paths.instance_game_dir(instance_id).join("config").join(CUBIQ_API_FILE)
}

fn connect_hints_path(paths: &Paths, instance_id: &str) -> std::path::PathBuf {
    paths.instance_game_dir(instance_id).join("config").join(crate::connect_hints::FILE)
}

/// `config/cubiqclient/connect-hints.json` schreiben (`Some`) bzw. löschen (`None`).
pub async fn write_connect_hints(paths: &Paths, instance_id: &str, json: Option<&str>) -> Result<()> {
    let file = connect_hints_path(paths, instance_id);
    match json {
        Some(json) => fsutil::write_atomic(&file, json.as_bytes()).await,
        None => match tokio::fs::remove_file(&file).await {
            Err(e) if e.kind() != std::io::ErrorKind::NotFound => Err(Error::io(&file, e)),
            _ => Ok(()),
        },
    }
}

/// `config/cubiqclient/cubiq-api.json`: Darf der Mod die Cubiq API benutzen?
/// `false`, wenn der Nutzer im Launcher nicht zugestimmt hat. Kein Token – der
/// Mod meldet sich über die Spielsitzung selbst an.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
struct CubiqApiConfig {
    version: u32,
    enabled: bool,
}

pub async fn write_cubiq_api_config(paths: &Paths, instance_id: &str, enabled: bool) -> Result<()> {
    let file = cubiq_api_path(paths, instance_id);
    let dir = file.parent().expect("Elternordner");
    tokio::fs::create_dir_all(dir).await.map_err(|e| Error::io(dir, e))?;
    let json = serde_json::to_string_pretty(&CubiqApiConfig { version: 1, enabled })
        .map_err(|e| Error::json("cubiq-api.json", e))?;
    fsutil::write_atomic(&file, json.as_bytes()).await
}

/// Farben, die der Mod liest: Thema, Akzent-Name und der dazugehörige Farbwert.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LauncherTheme {
    version: u32,
    theme: &'static str,
    accent: &'static str,
    /// "#RRGGBB" – passend zum Akzent-Namen, damit der Mod nichts raten muss.
    accent_color: &'static str,
    /// Sprache des Launchers ("en", "de", "pt-BR", …) – der Mod darf sie übernehmen.
    language: &'static str,
    /// Design des Launchers (derzeit immer "cubiq") – der Mod kann sein Aussehen anpassen.
    design: &'static str,
    /// Animationen, wie der Launcher sie gerade zeigt: "full" oder "reduced" („Wie System“ ist schon
    /// aufgelöst). Für den Mod ein zusätzliches Signal – seine eigene Einstellung bleibt, wie sie ist.
    motion: &'static str,
    /// Läuft ein Event für diesen Spieler (`"halloween"`), weiß der Mod es sofort; sonst fehlt das Feld.
    #[serde(skip_serializing_if = "Option::is_none")]
    event: Option<&'static str>,
    /// Zeigt der Launcher den Look (Design & Effekte) des Events? `false`, wenn das Team den
    /// Spieler gesperrt hat oder `ui.eventLook` aus ist. Ohne Event: der eigene Schalter.
    /// `event` bleibt davon unberührt (Features wie die Kürbis-Suche).
    event_look: bool,
    /// Halloween „Flackern & Rauschen“ (Filmkorn + Blitz-Aufflackern) – Vorgabe für den Client,
    /// solange der Spieler es dort nicht selbst umgestellt hat.
    halloween_flicker: bool,
}

/// Events, die der Mod als Theme kennt.
const THEME_EVENTS: [&str; 1] = ["halloween"];

/// Schreibt `config/cubiqclient/launcher-theme.json` in die Instanz (nur bekannte, feste Werte).
/// `system_reduced_motion`: „Bewegung reduzieren“ des Systems, wie die Oberfläche es meldet.
pub async fn write_theme(
    paths: &Paths,
    instance_id: &str,
    ui: &UiSettings,
    system_reduced_motion: bool,
    events: &[String],
    look: &[String],
) -> Result<()> {
    let file = theme_path(paths, instance_id);
    let dir = file.parent().expect("Elternordner");
    tokio::fs::create_dir_all(dir).await.map_err(|e| Error::io(dir, e))?;
    let event = THEME_EVENTS.into_iter().find(|e| events.iter().any(|x| x == e));
    let theme = LauncherTheme {
        version: 1,
        theme: theme_name(ui.theme),
        accent: accent_name(ui.accent),
        accent_color: accent_color(ui.accent),
        language: ui.language.code(),
        design: ui.design.code(),
        motion: motion_name(ui.motion, system_reduced_motion),
        event,
        event_look: ui.event_look && event.is_none_or(|e| look.iter().any(|x| x == e)),
        halloween_flicker: ui.halloween_flicker,
    };
    let json = serde_json::to_string_pretty(&theme).map_err(|e| Error::json("launcher-theme.json", e))?;
    tokio::fs::write(&file, json).await.map_err(|e| Error::io(&file, e))
}

/// Name des Themas für den Mod. "System" löst der Launcher auf – im Spiel gilt dann Dunkel.
fn theme_name(theme: Theme) -> &'static str {
    match theme {
        Theme::Dark | Theme::System => "dark",
        Theme::Oled => "oled",
        Theme::Light => "light",
    }
}

/// Animationen für den Mod. Den Systemwert sieht nur die Oberfläche – „Wie System“ folgt deshalb dem,
/// was sie zuletzt gemeldet hat (ohne Meldung: nicht reduziert, also "full" wie ab Werk).
fn motion_name(motion: Motion, system_reduced: bool) -> &'static str {
    if motion.is_reduced(system_reduced) { "reduced" } else { "full" }
}

fn accent_name(accent: Accent) -> &'static str {
    match accent {
        Accent::Cubiq => "cubiq",
        Accent::Amber => "amber",
        Accent::Emerald => "emerald",
        Accent::Lapis => "lapis",
        Accent::Amethyst => "amethyst",
        Accent::Ice => "ice",
        Accent::Orange => "orange",
    }
}

/// Dieselben Farbwerte wie in der Oberfläche (app/assets/css/main.css, `--color-primary-500`).
fn accent_color(accent: Accent) -> &'static str {
    match accent {
        Accent::Cubiq => "#71A330",
        Accent::Amber => "#E0900C",
        Accent::Emerald => "#17A34A",
        Accent::Lapis => "#3563E9",
        Accent::Amethyst => "#9B4DDF",
        Accent::Ice => "#2B97D6",
        Accent::Orange => "#E8702A",
    }
}

async fn same_file(a: &Path, b: &Path) -> bool {
    let (Ok(ma), Ok(mb)) = (tokio::fs::metadata(a).await, tokio::fs::metadata(b).await) else { return false };
    if ma.len() != mb.len() {
        return false;
    }
    match (download::sha1_of_file(a).await, download::sha1_of_file(b).await) {
        (Ok(x), Ok(y)) => x == y,
        _ => false,
    }
}

/// Fabric API nachinstallieren, falls sie fehlt. Offline oder bei Fehlern
/// nur warnen – das Spiel meldet eine fehlende Abhängigkeit dann selbst.
async fn ensure_fabric_api(http: &reqwest::Client, paths: &Paths, instance: &Instance) {
    // Eine deaktivierte Fabric API zählt nicht – das Spiel lädt sie nicht.
    let installed = content::enabled_project_ids(paths, &instance.id).await.unwrap_or_default();
    if installed.iter().any(|id| id == FABRIC_API_PROJECT) || has_fabric_api_file(paths, instance).await {
        return;
    }
    match modrinth::install(http, paths, instance, FABRIC_API_PROJECT, ContentKind::Mod, None).await {
        Ok(_) => tracing::info!("Fabric API für den Cubiq Client in '{}' installiert", instance.id),
        Err(e) => tracing::warn!("Fabric API konnte nicht installiert werden: {e}"),
    }
}

/// Auch von Hand hinzugefügte Fabric API zählt (z. B. aus einem Import).
async fn has_fabric_api_file(paths: &Paths, instance: &Instance) -> bool {
    content::list(paths, &instance.id, ContentKind::Mod)
        .await
        .map(|items| {
            items.iter().any(|i| {
                i.enabled
                    && (i.title.as_deref() == Some("Fabric API") || i.file_name.to_ascii_lowercase().starts_with("fabric-api"))
            })
        })
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use chrono::Utc;

    use super::*;
    use crate::instance::{InstanceOverrides, Loader};

    fn instance(version: &str, kind: LoaderKind, enabled: Option<bool>) -> Instance {
        Instance {
            id: "test".into(),
            name: "Test".into(),
            game_version: version.into(),
            loader: Loader { kind, version: None },
            created_at: Utc::now(),
            last_played: None,
            total_play_seconds: 0,
            icon: None,
            group: None,
            overrides: InstanceOverrides { cubiq_client: enabled, ..Default::default() },
        }
    }

    #[test]
    fn bundled_mods_come_from_the_manifest() {
        let m = parse_manifest(
            br#"{"version":"0.6.0","builds":[{"loader":"fabric","minecraft":["1.21.1"],"file":"cubiqclient-fabric-1.21.1.jar",
                "requires":["fabric-api"],"bundled":[{"id":"lithium","name":"Lithium","version":"0.15.4+mc1.21.1"},{"id":"ferritecore"}]}]}"#,
        )
        .unwrap();
        assert_eq!(m.builds[0].bundled.len(), 2);
        assert_eq!(m.builds[0].bundled[0].name, "Lithium");
        // Alte Manifeste ohne Feld: nichts eingebaut.
        assert!(parse_manifest(MANIFEST_JSON.as_bytes()).unwrap().builds[0].bundled.is_empty());
    }

    #[tokio::test]
    async fn fps_mode_keeps_what_the_mod_wrote() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path());
        assert_eq!(fps_mode(&paths, "a").await, None, "noch nie gewählt");
        set_fps_mode(&paths, "a", FpsMode::Max).await.unwrap();
        assert_eq!(fps_mode(&paths, "a").await, Some(FpsMode::Max));
        // Der Mod führt dort, was er selbst gesetzt hat – das bleibt beim Umschalten erhalten.
        let file = fps_mode_path(&paths, "a");
        std::fs::write(&file, r#"{"version":1,"mode":"max","chosen":true,"appliedGame":"max","owned":{"clouds":[2,0]}}"#).unwrap();
        set_fps_mode(&paths, "a", FpsMode::Pretty).await.unwrap();
        let json: serde_json::Value = serde_json::from_slice(&std::fs::read(&file).unwrap()).unwrap();
        assert_eq!(json["mode"], "pretty");
        assert_eq!(json["owned"]["clouds"][1], 0);
        assert_eq!(json["appliedGame"], "max");
        // Vom Mod geschrieben, aber noch nicht gewählt (Standard „Schön“): gilt als nicht gewählt.
        std::fs::write(&file, r#"{"version":1,"mode":"pretty","chosen":false}"#).unwrap();
        assert_eq!(fps_mode(&paths, "a").await, None);
        std::fs::write(&file, b"kaputt").unwrap();
        assert_eq!(fps_mode(&paths, "a").await, None);
        set_fps_mode(&paths, "a", FpsMode::Max).await.unwrap();
        assert_eq!(fps_mode(&paths, "a").await, Some(FpsMode::Max));
    }

    #[test]
    fn disable_argument_spares_the_players_own_copies() {
        let bundled: Vec<BundledMod> = ["lithium", "ferritecore", "Bad Id", "modernfix"]
            .iter()
            .map(|id| BundledMod { id: (*id).into(), name: String::new(), version: String::new() })
            .collect();
        assert_eq!(
            disable_arg(&bundled, &["ferritecore".into()]).as_deref(),
            Some("-Dfabric.debug.disableModIds=lithium,modernfix")
        );
        assert_eq!(disable_arg(&bundled[..2], &["lithium".into(), "ferritecore".into()]), None);
    }

    #[tokio::test]
    async fn builtin_optimizations_follow_the_module_switch() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path());
        let inst = instance("1.21.1", LoaderKind::Fabric, None);
        let game = paths.instance_game_dir(&inst.id);
        let build = Build {
            loader: "fabric".into(),
            minecraft: vec!["1.21.1".into()],
            file: "cubiqclient-fabric-1.21.1.jar".into(),
            requires: Vec::new(),
            bundled: vec![
                BundledMod { id: "lithium".into(), name: String::new(), version: String::new() },
                BundledMod { id: "ferritecore".into(), name: String::new(), version: String::new() },
            ],
            sha256: None,
            size: None,
            from_channel: false,
        };
        let builds = [build.clone()];
        // Keine Config: Modul an → Preset lässt beide weg, nichts wird abgeschaltet.
        assert!(builtin_optimizations_enabled(&game).await);
        assert_eq!(builtin_mod_ids(&paths, &builds, &inst).await, ["lithium", "ferritecore"]);
        assert_eq!(builtin_mods(&paths, &builds, &inst).await, build.bundled);
        assert!(bundled_jvm_args(&paths, &build, &inst).await.is_empty());

        // Im Cubiq-Menü ausgeschaltet; der Spieler hat FerriteCore selbst im Mods-Ordner.
        std::fs::create_dir_all(game.join("config")).unwrap();
        std::fs::write(
            game.join("config/cubiqclient.json"),
            r#"{"configVersion":2,"modules":{"builtinOptimizations":{"enabled":false}}}"#,
        )
        .unwrap();
        let mods = content::content_dir(&paths, &inst.id, ContentKind::Mod);
        std::fs::create_dir_all(&mods).unwrap();
        {
            use std::io::Write;
            let mut zip = zip::ZipWriter::new(std::fs::File::create(mods.join("ferritecore-7.jar")).unwrap());
            zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
            zip.write_all(br#"{"schemaVersion":1,"id":"ferritecore","version":"7.1.0"}"#).unwrap();
            zip.finish().unwrap();
        }
        std::fs::write(mods.join("kaputt.jar"), b"kein zip").unwrap();
        assert!(!builtin_optimizations_enabled(&game).await);
        assert!(builtin_mod_ids(&paths, &builds, &inst).await.is_empty(), "Preset darf sie wieder installieren");
        assert!(builtin_mods(&paths, &builds, &inst).await.is_empty());
        assert_eq!(bundled_jvm_args(&paths, &build, &inst).await, ["-Dfabric.debug.disableModIds=lithium"]);
        // Forge-Instanz: nie (dort gibt es keine eingebauten Mods).
        assert!(bundled_jvm_args(&paths, &build, &instance("1.21.1", LoaderKind::Forge, None)).await.is_empty());
        // Cubiq Client in der Instanz aus: keine eingebauten Mods.
        assert!(builtin_mod_ids(&paths, &builds, &instance("1.21.1", LoaderKind::Fabric, Some(false))).await.is_empty());
    }

    const MANIFEST_JSON: &str = r#"[
        {"loader":"fabric","minecraft":["1.21","1.21.1"],"file":"cubiqclient-fabric-1.21.jar","requires":["fabric-api"]},
        {"loader":"forge","minecraft":["1.8.9"],"file":"cubiqclient-forge-1.8.9.jar","requires":[]},
        {"loader":"fabric","minecraft":["1.20.1"],"file":"../boese.jar"}
    ]"#;

    fn bundled(dir: &Path) -> Vec<Build> {
        std::fs::write(dir.join(MANIFEST), MANIFEST_JSON).unwrap();
        load_builds(dir)
    }

    #[test]
    fn manifest_matching() {
        let dir = tempfile::tempdir().unwrap();
        let builds = bundled(dir.path());
        assert_eq!(builds.len(), 2, "unsichere Dateinamen werden verworfen");
        assert_eq!(availability(&builds, &instance("1.21", LoaderKind::Fabric, None)), Availability::Available);
        assert_eq!(availability(&builds, &instance("1.21.1", LoaderKind::Quilt, None)), Availability::Available);
        assert_eq!(availability(&builds, &instance("1.8.9", LoaderKind::Forge, None)), Availability::Available);
        assert_eq!(availability(&builds, &instance("1.8.9", LoaderKind::Fabric, None)), Availability::Unsupported);
        assert_eq!(availability(&builds, &instance("1.21.1", LoaderKind::Fabric, Some(false))), Availability::Disabled);
        assert_eq!(boost_loader(&builds, "1.21.1"), Some(LoaderKind::Fabric));
        assert_eq!(boost_loader(&builds, "1.8.9"), Some(LoaderKind::Forge));
        assert_eq!(boost_loader(&builds, "1.5.2"), None);
        assert!(load_builds(&dir.path().join("fehlt")).is_empty());
    }

    /// In der Mod-Liste deaktiviert = bleibt deaktiviert, wird aber aktuell gehalten.
    #[tokio::test]
    async fn disabled_in_mod_list_stays_disabled() {
        let bundle = tempfile::tempdir().unwrap();
        bundled(bundle.path());
        std::fs::write(bundle.path().join("cubiqclient-forge-1.8.9.jar"), b"neu").unwrap();
        let root = tempfile::tempdir().unwrap();
        let paths = Paths::new(root.path());
        let inst = instance("1.8.9", LoaderKind::Forge, None);
        let http = reqwest::Client::new();
        let ui = UiSettings::default();
        let mods = content::content_dir(&paths, &inst.id, ContentKind::Mod);

        sync(&http, &paths, Some(bundle.path()), None, &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(mods.join(INSTALLED_NAME)).unwrap(), b"neu");

        // Nutzer schaltet ihn in der Mod-Liste aus, danach kommt ein Launcher-Update.
        std::fs::rename(mods.join(INSTALLED_NAME), mods.join("cubiqclient.jar.disabled")).unwrap();
        std::fs::write(bundle.path().join("cubiqclient-forge-1.8.9.jar"), b"neuer").unwrap();
        sync(&http, &paths, Some(bundle.path()), None, &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!mods.join(INSTALLED_NAME).exists(), "darf nicht wieder eingeschaltet werden");
        assert_eq!(std::fs::read(mods.join("cubiqclient.jar.disabled")).unwrap(), b"neuer");
    }

    /// Die mitgelieferte `builds.json`: jede Datei existiert, jede (Loader, Version)-Kombination
    /// ist eindeutig und kein Eintrag wurde wegen eines unsicheren Namens verworfen.
    #[test]
    fn bundled_manifest_is_consistent() {
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod");
        let raw: serde_json::Value = serde_json::from_slice(&std::fs::read(dir.join(MANIFEST)).unwrap()).unwrap();
        let manifest = load_manifest(&dir);
        let builds = manifest.builds;
        assert_eq!(builds.len(), raw["builds"].as_array().unwrap().len(), "unsichere Dateinamen in builds.json");
        assert!(client_mod_update::is_valid_version(&manifest.version), "Version: {}", manifest.version);
        let mut seen = std::collections::HashSet::new();
        for b in &builds {
            assert!(matches!(b.loader.as_str(), "fabric" | "forge" | "neoforge"), "{}", b.loader);
            assert!(dir.join(&b.file).is_file(), "{} fehlt", b.file);
            let data = std::fs::read(dir.join(&b.file)).unwrap();
            assert_eq!(b.size, Some(data.len() as u64), "{}: Größe", b.file);
            assert_eq!(b.sha256.as_deref(), Some(client_mod_update::tests::sha256_hex(&data).as_str()), "{}: SHA-256", b.file);
            assert!(!b.minecraft.is_empty(), "{}", b.file);
            for v in &b.minecraft {
                assert!(seen.insert((b.loader.clone(), v.clone())), "doppelt: {} {v}", b.loader);
            }
        }
    }

    /// Die mitgelieferte builds.json deckt jede Fabric-Version von 1.14.4 bis 26.3 ab,
    /// und jede genannte Jar liegt tatsächlich im Ressourcen-Ordner.
    #[test]
    fn bundled_manifest_covers_all_fabric_versions() {
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod");
        let builds = load_builds(&dir);
        // Das Repository bringt keine fertigen Jars mit; erst `collectLauncherJars` (client-mod/) legt sie hier ab.
        if builds.is_empty() {
            eprintln!("keine mitgelieferten Cubiq-Client-Jars in resources/client-mod – Abdeckung nicht prüfbar");
            return;
        }
        let versions = [
            "1.14.4", "1.15.2", "1.16.2", "1.16.3", "1.16.4", "1.16.5", "1.17", "1.17.1", "1.18", "1.18.1", "1.18.2",
            "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.1", "1.20.6", "1.21", "1.21.1", "1.21.11", "26.1",
            "26.3",
        ];
        for v in versions {
            let build = build_for(&builds, LoaderKind::Fabric, v).unwrap_or_else(|| panic!("kein Fabric-Build für {v}"));
            assert!(dir.join(&build.file).is_file(), "{} fehlt", build.file);
            assert_eq!(boost_loader(&builds, v), Some(LoaderKind::Fabric));
        }
    }

    /// Fabric-Jars laufen im echten Spiel mit Intermediary-Namen (`net/minecraft/class_…`).
    /// Bleibt ein Mojang-Name stehen (etwa ein Mixin-Ziel ohne vollständige Signatur, das
    /// Loom nicht umschreibt), findet Mixin nichts und das Spiel stürzt beim Start ab –
    /// im Entwicklungsmodus fällt das nicht auf, deshalb prüft es dieser Test.
    #[test]
    fn bundled_fabric_jars_contain_no_mojang_names() {
        if load_builds(&Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod")).is_empty() {
            eprintln!("keine mitgelieferten Cubiq-Client-Jars in resources/client-mod – nichts zu prüfen");
            return;
        }
        use std::io::Read;
        const MOJANG: &[&str] = &[
            "net/minecraft/client/renderer/",
            "net/minecraft/client/gui/",
            "net/minecraft/client/player/",
            "net/minecraft/client/multiplayer/",
            "net/minecraft/world/",
            "net/minecraft/network/",
            "net/minecraft/server/",
            "net/minecraft/core/",
            "net/minecraft/util/",
            "net/minecraft/resources/",
            "net/minecraft/sounds/",
        ];
        // Klassen, die auch in Intermediary ihren Namen behalten.
        const KEEPS_NAME: &[&str] = &["net/minecraft/server/MinecraftServer", "net/minecraft/server/Main"];
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod");
        let mut problems = Vec::new();
        // Ab 26.1 liefert Mojang das Spiel unverschleiert aus – dort sind Mojang-Namen richtig.
        let obfuscated = |b: &&Build| b.minecraft.iter().all(|v| v.starts_with("1."));
        for build in load_builds(&dir).iter().filter(|b| b.loader == "fabric").filter(obfuscated) {
            let file = std::fs::File::open(dir.join(&build.file)).unwrap();
            let mut zip = zip::ZipArchive::new(file).unwrap();
            for i in 0..zip.len() {
                let mut entry = zip.by_index(i).unwrap();
                if !entry.name().ends_with(".class") {
                    continue;
                }
                let name = entry.name().to_owned();
                let mut bytes = Vec::new();
                entry.read_to_end(&mut bytes).unwrap();
                for needle in MOJANG {
                    for (at, _) in bytes.windows(needle.len()).enumerate().filter(|(_, w)| *w == needle.as_bytes()) {
                        let class: String = bytes[at..]
                            .iter()
                            .take_while(|b| b.is_ascii_alphanumeric() || matches!(b, b'/' | b'_' | b'$'))
                            .map(|&b| b as char)
                            .collect();
                        // Innere Klassen behaltener Klassen heißen in Intermediary z. B. `MinecraftServer$class_7460`.
                        let kept_inner = KEEPS_NAME.iter().any(|k| {
                            class
                                .strip_prefix(k)
                                .and_then(|rest| rest.strip_prefix("$class_"))
                                .is_some_and(|n| !n.is_empty() && n.bytes().all(|c| c.is_ascii_digit()))
                        });
                        if !KEEPS_NAME.contains(&class.as_str()) && !kept_inner {
                            problems.push(format!("{}: {name} enthält {class}", build.file));
                        }
                    }
                }
            }
        }
        assert!(problems.is_empty(), "nicht umgeschriebene Mojang-Namen:\n{}", problems.join("\n"));
    }

    /// Das Jar-in-Jar der eingebauten Mods ist in den mitgelieferten Fabric-Jars vollständig: jede in
    /// einem `fabric.mod.json` genannte eingebettete Jar existiert (rekursiv) und ist selbst eine Mod,
    /// und jede eingebaute Mod aus builds.json steckt wirklich darin. ImmediatelyFast bis 1.16 nutzt
    /// z. B. Reflect (`net.lenni0451`) und bringt es eingebettet mit – fehlt es, stürzt das Spiel ab,
    /// sobald eine Karte geladen wird (`NoClassDefFoundError: net/lenni0451/reflect/Objects`).
    #[test]
    fn bundled_fabric_jars_keep_nested_jars() {
        if load_builds(&Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod")).is_empty() {
            eprintln!("keine mitgelieferten Cubiq-Client-Jars in resources/client-mod – nichts zu prüfen");
            return;
        }
        use std::io::Read;
        const REFLECT: &[u8] = b"net/lenni0451/reflect/";
        /// Eingebettete Mods: Mod-IDs sammeln; `uses_reflect`, wenn eine ihrer Klassen Reflect aufruft.
        fn walk(label: &str, bytes: Vec<u8>, ids: &mut Vec<String>, uses_reflect: &mut bool, problems: &mut Vec<String>) {
            let nested_mod = label.contains(" → ");
            let mut zip = match zip::ZipArchive::new(std::io::Cursor::new(bytes)) {
                Ok(zip) => zip,
                Err(e) => return problems.push(format!("{label}: kein Zip ({e})")),
            };
            let meta: serde_json::Value = match zip.by_name("fabric.mod.json") {
                Ok(mut file) => {
                    let mut text = String::new();
                    file.read_to_string(&mut text).unwrap();
                    match serde_json::from_str(&text) {
                        Ok(meta) => meta,
                        Err(e) => return problems.push(format!("{label}: fabric.mod.json unlesbar ({e})")),
                    }
                }
                Err(_) => return problems.push(format!("{label}: ohne fabric.mod.json")),
            };
            ids.extend(meta["id"].as_str().map(str::to_owned));
            let own_reflect = zip.file_names().any(|n| n.as_bytes().starts_with(REFLECT));
            if nested_mod && !own_reflect && !*uses_reflect {
                for i in 0..zip.len() {
                    let mut entry = zip.by_index(i).unwrap();
                    if !entry.name().ends_with(".class") {
                        continue;
                    }
                    let mut class = Vec::new();
                    entry.read_to_end(&mut class).unwrap();
                    if class.windows(REFLECT.len()).any(|w| w == REFLECT) {
                        *uses_reflect = true;
                        break;
                    }
                }
            }
            for nested in meta["jars"].as_array().into_iter().flatten() {
                let Some(path) = nested["file"].as_str() else { continue };
                let mut data = Vec::new();
                match zip.by_name(path) {
                    Ok(mut file) => file.read_to_end(&mut data).map(drop).unwrap(),
                    Err(_) => {
                        problems.push(format!("{label}: eingebettete {path} fehlt"));
                        continue;
                    }
                }
                walk(&format!("{label} → {path}"), data, ids, uses_reflect, problems);
            }
        }
        let dir = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../resources/client-mod");
        let mut problems = Vec::new();
        let mut checked_reflect = false;
        for build in load_builds(&dir).iter().filter(|b| b.loader == "fabric") {
            let (mut ids, mut uses_reflect) = (Vec::new(), false);
            walk(&build.file, std::fs::read(dir.join(&build.file)).unwrap(), &mut ids, &mut uses_reflect, &mut problems);
            for m in &build.bundled {
                if !ids.contains(&m.id) {
                    problems.push(format!("{}: eingebaute Mod {} fehlt", build.file, m.id));
                }
            }
            if uses_reflect {
                checked_reflect = true;
                if !ids.iter().any(|id| id == "net_lenni0451_reflect") {
                    problems.push(format!("{}: eingebaute Mod nutzt Reflect (net.lenni0451), es fehlt", build.file));
                }
            }
        }
        // Stichprobe, dass die Suche greift: ImmediatelyFast für 1.21.11 nutzt Reflect.
        assert!(checked_reflect, "keine Fabric-Jar mit Reflect-Nutzer gefunden");
        assert!(problems.is_empty(), "unvollständiges Jar-in-Jar:\n{}", problems.join("\n"));
    }

    #[tokio::test]
    async fn writes_launcher_colours_for_the_mod() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path().join("root"));
        let mut ui = UiSettings { accent: Accent::Emerald, theme: Theme::Oled, ..UiSettings::default() };
        write_theme(&paths, "test", &ui, false, &[], &[]).await.unwrap();

        let file = paths.instance_game_dir("test").join("config/cubiqclient/launcher-theme.json");
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!(json["version"], 1);
        assert_eq!(json["theme"], "oled");
        assert_eq!(json["accent"], "emerald");
        assert_eq!(json["accentColor"], "#17A34A");
        assert_eq!(json["language"], "en");
        assert!(json.get("event").is_none(), "ohne Event fehlt das Feld");
        assert_eq!(json["design"], "cubiq");
        assert_eq!(json["halloweenFlicker"], true, "Flackern & Rauschen ab Werk an");
        assert_eq!(json["eventLook"], true, "Event-Design ab Werk an");
        assert_eq!(json["motion"], "full", "Animationen ab Werk an");

        // "System" gibt es im Spiel nicht – dort gilt das dunkle Thema.
        ui.theme = Theme::System;
        ui.accent = Accent::Cubiq;
        ui.language = crate::settings::Language::PtBr;
        ui.halloween_flicker = false;
        let events: Vec<String> = vec!["halloween".into(), "other".into()];
        write_theme(&paths, "test", &ui, false, &events, &events).await.unwrap();
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!(json["theme"], "dark");
        assert_eq!(json["accentColor"], "#71A330", "der gespeicherte Akzent bleibt");
        assert_eq!(json["language"], "pt-BR");
        assert_eq!(json["event"], "halloween");
        assert_eq!(json["eventLook"], true);
        assert_eq!(json["design"], "cubiq");
        assert_eq!(json["halloweenFlicker"], false);

        // Team-Sperre: Event (Features) bleibt, nur der Look ist aus.
        write_theme(&paths, "test", &ui, false, &events, &["other".into()]).await.unwrap();
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!((json["event"].as_str(), json["eventLook"].as_bool()), (Some("halloween"), Some(false)));
        // Eigener Schalter aus: ebenso – auch ohne laufendes Event.
        ui.event_look = false;
        write_theme(&paths, "test", &ui, false, &events, &events).await.unwrap();
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!((json["event"].as_str(), json["eventLook"].as_bool()), (Some("halloween"), Some(false)));
        write_theme(&paths, "test", &ui, false, &[], &[]).await.unwrap();
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!((json.get("event"), json["eventLook"].as_bool()), (None, Some(false)));
        ui.event_look = true;

        ui.accent = Accent::Ice;
        write_theme(&paths, "test", &ui, false, &[], &[]).await.unwrap();
        let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
        assert_eq!((json["accent"].as_str(), json["accentColor"].as_str()), (Some("ice"), Some("#2B97D6")));

        // Animationen: "System" gibt es im Spiel nicht – es gilt, was die Oberfläche als
        // „Bewegung reduzieren“ des Systems meldet. „Immer“ bleibt auch dann "full".
        for (motion, system_reduced, expected) in [
            (Motion::Full, false, "full"),
            (Motion::Full, true, "full"),
            (Motion::System, false, "full"),
            (Motion::System, true, "reduced"),
            (Motion::Reduced, false, "reduced"),
            (Motion::Reduced, true, "reduced"),
        ] {
            ui.motion = motion;
            write_theme(&paths, "test", &ui, system_reduced, &[], &[]).await.unwrap();
            let json: serde_json::Value = serde_json::from_str(&std::fs::read_to_string(&file).unwrap()).unwrap();
            assert_eq!(json["motion"], expected, "{motion:?}, System reduziert: {system_reduced}");
        }
    }

    #[test]
    fn social_hints_in_game_from_0_8_0() {
        for (version, expected) in
            [("0.7.1", false), ("0.6.0", false), ("0.8.0-beta.1", false), ("0.8.0", true), ("0.8.3", true), ("1.0.0", true)]
        {
            assert_eq!(shows_social_in_game(Some(version)), expected, "{version}");
        }
        assert!(shows_social_in_game(None), "unbekannte Version, aber über Link v2 verbunden");
    }

    #[test]
    fn every_accent_has_a_colour() {
        for accent in [Accent::Cubiq, Accent::Amber, Accent::Emerald, Accent::Lapis, Accent::Amethyst, Accent::Ice, Accent::Orange] {
            let hex = accent_color(accent);
            assert!(hex.len() == 7 && hex.starts_with('#'), "{accent:?} → {hex}");
            assert!(hex[1..].chars().all(|c| c.is_ascii_hexdigit()), "{accent:?} → {hex}");
            assert!(!accent_name(accent).is_empty());
        }
    }

    #[tokio::test]
    async fn installs_updates_and_removes_jar() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path().join("root"));
        paths.ensure().await.unwrap();
        let res = dir.path().join("bundled");
        tokio::fs::create_dir_all(&res).await.unwrap();
        bundled(&res);
        tokio::fs::write(res.join("cubiqclient-fabric-1.21.jar"), b"v1").await.unwrap();

        // Offline-Client: die Fabric-API-Nachinstallation schlägt fehl, darf aber nichts blockieren.
        let http = reqwest::Client::builder().proxy(reqwest::Proxy::all("http://127.0.0.1:9").unwrap()).build().unwrap();
        let mods = content::content_dir(&paths, "test", ContentKind::Mod);
        let ui = UiSettings::default();

        let on = instance("1.21.1", LoaderKind::Fabric, None);
        sync(&http, &paths, Some(&res), None, &on, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(tokio::fs::read(mods.join(INSTALLED_NAME)).await.unwrap(), b"v1");
        assert!(theme_path(&paths, "test").is_file(), "Farben des Launchers liegen in der Instanz");
        let cubiq: serde_json::Value =
            serde_json::from_slice(&tokio::fs::read(cubiq_api_path(&paths, "test")).await.unwrap()).unwrap();
        assert_eq!(cubiq, serde_json::json!({ "version": 1, "enabled": true }), "Einwilligung für den Mod, kein Token");

        tokio::fs::write(res.join("cubiqclient-fabric-1.21.jar"), b"v2").await.unwrap();
        sync(&http, &paths, Some(&res), None, &on, &ui, false, false, &[], &[]).await.unwrap();
        let cubiq: serde_json::Value =
            serde_json::from_slice(&tokio::fs::read(cubiq_api_path(&paths, "test")).await.unwrap()).unwrap();
        assert_eq!(cubiq["enabled"], false, "ohne Einwilligung darf der Mod die API nicht nutzen");
        assert_eq!(tokio::fs::read(mods.join(INSTALLED_NAME)).await.unwrap(), b"v2");

        // „Wie System“ bei reduzierter Systembewegung (meldet die Oberfläche) kommt im Spiel als "reduced" an.
        let calm = UiSettings { motion: Motion::System, ..UiSettings::default() };
        sync(&http, &paths, Some(&res), None, &on, &calm, true, true, &[], &[]).await.unwrap();
        let theme: serde_json::Value = serde_json::from_slice(&tokio::fs::read(theme_path(&paths, "test")).await.unwrap()).unwrap();
        assert_eq!(theme["motion"], "reduced");

        // Versionswechsel auf eine Version ohne Build: alte Kopie verschwindet.
        let other = instance("1.20.4", LoaderKind::Fabric, None);
        sync(&http, &paths, Some(&res), None, &other, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!mods.join(INSTALLED_NAME).exists());

        sync(&http, &paths, Some(&res), None, &on, &ui, false, true, &[], &[]).await.unwrap();
        let off = instance("1.21.1", LoaderKind::Fabric, Some(false));
        sync(&http, &paths, Some(&res), None, &off, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!mods.join(INSTALLED_NAME).exists());
        assert!(!theme_path(&paths, "test").exists(), "abgeschaltet: auch die Farbdatei ist weg");
        assert!(!cubiq_api_path(&paths, "test").exists());
    }

    // --- Update-Kanal ---------------------------------------------------------

    use crate::client_mod_update::tests::{TestKey, TestServer, publish};

    /// Mitgeliefertes Manifest im neuen Format mit einem Fabric-Build (1.21/1.21.1).
    fn bundle_version(dir: &Path, version: &str, jar: &[u8]) {
        let manifest = serde_json::json!({
            "version": version,
            "builds": [{"loader":"fabric","minecraft":["1.21","1.21.1"],"file":"cubiqclient-fabric-1.21.jar","requires":["fabric-api"]}]
        });
        std::fs::create_dir_all(dir).unwrap();
        std::fs::write(dir.join(MANIFEST), manifest.to_string()).unwrap();
        std::fs::write(dir.join("cubiqclient-fabric-1.21.jar"), jar).unwrap();
    }

    fn offline_http() -> reqwest::Client {
        reqwest::Client::builder().proxy(reqwest::Proxy::all("http://127.0.0.1:9").unwrap()).build().unwrap()
    }

    #[test]
    fn reads_new_manifest_format() {
        let dir = tempfile::tempdir().unwrap();
        bundle_version(dir.path(), "0.2.0", b"x");
        let manifest = load_manifest(dir.path());
        assert_eq!(manifest.version, "0.2.0");
        assert_eq!(manifest.builds.len(), 1);
        // Alte Liste ohne Version: Version unbekannt, Builds wie gehabt.
        let legacy = parse_manifest(MANIFEST_JSON.as_bytes()).unwrap();
        assert_eq!((legacy.version.as_str(), legacy.builds.len()), ("", 2));
        assert!(parse_manifest(b"{kaputt").is_none());
    }

    #[tokio::test]
    async fn newer_channel_client_wins_until_the_launcher_brings_a_newer_one() {
        let key = TestKey::generate();
        let (server, url) = TestServer::start().await;
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path().join("root"));
        paths.ensure().await.unwrap();
        let res = dir.path().join("bundled");
        bundle_version(&res, "0.2.0", b"v2");
        let updater = ClientModUpdater::for_tests(paths.client_mod_cache_dir(), &url, &key.public);
        let (http, ui) = (offline_http(), UiSettings::default());
        let inst = instance("1.21.1", LoaderKind::Fabric, None);
        let jar = content::content_dir(&paths, "test", ContentKind::Mod).join(INSTALLED_NAME);
        // Der Verlauf schreibt nur für angelegte Instanzen.
        std::fs::create_dir_all(paths.instance_dir("test")).unwrap();
        std::fs::write(paths.instance_file("test"), "{}").unwrap();

        // Noch nichts im Kanal: mitgelieferte Version.
        assert_eq!(updater.check_now(Some("0.2.0")).await, client_mod_update::CheckOutcome::Failed);
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"v2");

        // Neuere Version im Kanal: wird geladen, geprüft und installiert.
        publish(&server, &key, "0.3.0", b"v3");
        updater.check_now(Some("0.2.0")).await;
        let catalog = Catalog::load(Some(&res), Some(&updater)).await;
        assert_eq!(catalog.status(), ClientModStatus { bundled: Some("0.2.0".into()), update: Some("0.3.0".into()) });
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"v3");
        let entry = history::list(&paths, "test").await.unwrap().into_iter().next().unwrap();
        assert_eq!(entry.kind, HistoryKind::ModUpdated);
        assert_eq!((entry.from.as_deref(), entry.to.as_deref()), (Some("0.2.0"), Some("0.3.0")));

        // Zweiter Start: nichts Neues zu laden, nichts Neues im Verlauf.
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(server.hits("cubiqclient-fabric-1.21.jar"), 1);
        assert_eq!(history::list(&paths, "test").await.unwrap().len(), 1);

        // Launcher-Update bringt 0.4.0 mit: das ist neuer als der Kanal.
        bundle_version(&res, "0.4.0", b"v4");
        let catalog = Catalog::load(Some(&res), Some(&updater)).await;
        assert_eq!(catalog.status(), ClientModStatus { bundled: Some("0.4.0".into()), update: None });
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"v4");
        updater.cleanup(Some("0.4.0")).await;
        assert!(!paths.client_mod_cache_dir().join("0.3.0").exists());
    }

    #[tokio::test]
    async fn channel_problems_fall_back_to_the_bundled_client() {
        let key = TestKey::generate();
        let (server, url) = TestServer::start().await;
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path().join("root"));
        paths.ensure().await.unwrap();
        let res = dir.path().join("bundled");
        bundle_version(&res, "0.2.0", b"v2");
        let updater = ClientModUpdater::for_tests(paths.client_mod_cache_dir(), &url, &key.public);
        let (http, ui) = (offline_http(), UiSettings::default());
        let inst = instance("1.21.1", LoaderKind::Fabric, None);
        let mods = content::content_dir(&paths, "test", ContentKind::Mod);

        // Signiertes Manifest, aber der Jar auf dem Server passt nicht zur Prüfsumme.
        publish(&server, &key, "0.3.0", b"v3");
        updater.check_now(Some("0.2.0")).await;
        server.put("cubiqclient-fabric-1.21.jar", b"v3-manipuliert".to_vec());
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(mods.join(INSTALLED_NAME)).unwrap(), b"v2", "Rückfall auf die mitgelieferte Version");

        // In der Mod-Liste deaktiviert: bleibt aus, die deaktivierte Kopie bekommt das Update.
        std::fs::rename(mods.join(INSTALLED_NAME), mods.join("cubiqclient.jar.disabled")).unwrap();
        server.put("cubiqclient-fabric-1.21.jar", b"v3".to_vec());
        sync(&http, &paths, Some(&res), Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!mods.join(INSTALLED_NAME).exists(), "darf nicht wieder eingeschaltet werden");
        assert_eq!(std::fs::read(mods.join("cubiqclient.jar.disabled")).unwrap(), b"v3");
        std::fs::rename(mods.join("cubiqclient.jar.disabled"), mods.join(INSTALLED_NAME)).unwrap();

        // Offline: geprüftes Manifest + Jar aus dem Cache reichen.
        let offline = ClientModUpdater::for_tests(paths.client_mod_cache_dir(), "http://127.0.0.1:9/", &key.public);
        assert_eq!(offline.check_now(Some("0.2.0")).await, client_mod_update::CheckOutcome::Failed);
        sync(&http, &paths, Some(&res), Some(&offline), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(mods.join(INSTALLED_NAME)).unwrap(), b"v3");

        // Offline und Cache weg: die installierte Kopie ist genau dieser Build und bleibt.
        std::fs::remove_dir_all(paths.client_mod_cache_dir().join("0.3.0")).unwrap();
        sync(&http, &paths, Some(&res), Some(&offline), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(mods.join(INSTALLED_NAME)).unwrap(), b"v3");

        // Offline, kein Cache und keine passende Kopie: mitgelieferte Version.
        std::fs::remove_file(mods.join(INSTALLED_NAME)).unwrap();
        sync(&http, &paths, Some(&res), Some(&offline), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(mods.join(INSTALLED_NAME)).unwrap(), b"v2");

        // Keine Quelle hat einen Build für die Version: Kopie wird entfernt.
        let other = instance("1.8.9", LoaderKind::Forge, None);
        sync(&http, &paths, Some(&res), Some(&offline), &other, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!mods.join(INSTALLED_NAME).exists(), "kein Build für 1.8.9: entfernt");
    }

    /// Android/iOS: kein Launcher-Paket, nur der Kanal – geladen wird genau der eine Jar,
    /// den die Instanz braucht (nie der ganze Katalog), danach aus dem Cache.
    #[tokio::test]
    async fn mobile_loads_only_the_jar_of_the_instance() {
        use crate::client_mod_update::tests::sha256_hex;
        use crate::client_mod_update::{MANIFEST_FILE, SIGNATURE_FILE};
        let key = TestKey::generate();
        let (server, url) = TestServer::start().await;
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path().join("root"));
        paths.ensure().await.unwrap();
        let cache = paths.client_mod_cache_dir();
        let updater = ClientModUpdater::for_tests(cache.clone(), &url, &key.public);
        let (http, ui) = (offline_http(), UiSettings::default());
        const FABRIC: &str = "cubiqclient-fabric-1.21.1.jar";
        const FORGE: &str = "cubiqclient-forge-1.8.9.jar";
        const NEO: &str = "cubiqclient-neoforge-1.21.1.jar";
        let publish_all = |version: &str, tag: &str| {
            let builds: Vec<serde_json::Value> = [("fabric", "1.21.1", FABRIC), ("forge", "1.8.9", FORGE), ("neoforge", "1.21.1", NEO)]
                .iter()
                .map(|(loader, mc, file)| {
                    let data = format!("{file}-{tag}").into_bytes();
                    let build = serde_json::json!({"loader": loader, "minecraft": [mc], "file": file, "sha256": sha256_hex(&data), "size": data.len()});
                    server.put(file, data);
                    build
                })
                .collect();
            let manifest = serde_json::json!({ "version": version, "builds": builds }).to_string().into_bytes();
            server.put(SIGNATURE_FILE, key.sign(&manifest, MANIFEST_FILE));
            server.put(MANIFEST_FILE, manifest);
        };
        let inst = instance("1.21.1", LoaderKind::Fabric, None);
        let jar = installed_jar(&paths, &inst.id);

        // Kanal noch nie erreicht: nichts bekannt, nichts angefasst.
        assert_eq!(updater.check_now(None).await, client_mod_update::CheckOutcome::Failed);
        sync(&http, &paths, None, Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!jar.exists());

        publish_all("0.3.0", "a");
        assert_eq!(updater.check_now(None).await, client_mod_update::CheckOutcome::Updated("0.3.0".into()));
        let catalog = Catalog::load(None, Some(&updater)).await;
        assert_eq!(catalog.status(), ClientModStatus { bundled: Some("0.3.0".into()), update: None }, "Kanal = aktueller Stand");
        assert!(server.hits(FABRIC) + server.hits(FORGE) + server.hits(NEO) == 0, "Prüfen lädt keine Jars");

        sync(&http, &paths, None, Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"cubiqclient-fabric-1.21.1.jar-a");
        assert_eq!((server.hits(FABRIC), server.hits(FORGE), server.hits(NEO)), (1, 0, 0), "nur der Jar der Instanz");
        let cached: Vec<_> = std::fs::read_dir(cache.join("0.3.0")).unwrap().map(|e| e.unwrap().file_name()).collect();
        assert_eq!(cached, [FABRIC], "nur dieser Jar im Cache");
        assert_eq!(installed_version(&paths, &inst.id).await.as_deref(), Some("0.3.0"));

        // Nächster Start und eine zweite Instanz derselben Version: aus dem Cache.
        sync(&http, &paths, None, Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        let second = Instance { id: "zwei".into(), ..inst.clone() };
        sync(&http, &paths, None, Some(&updater), &second, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(installed_jar(&paths, "zwei")).unwrap(), b"cubiqclient-fabric-1.21.1.jar-a");
        assert_eq!(server.hits(FABRIC), 1);

        // In der Instanz abgeschaltet: nichts geladen, nichts installiert.
        let off = Instance { id: "aus".into(), ..instance("1.8.9", LoaderKind::Forge, Some(false)) };
        sync(&http, &paths, None, Some(&updater), &off, &ui, false, true, &[], &[]).await.unwrap();
        assert!(!installed_jar(&paths, "aus").exists());
        assert_eq!(server.hits(FORGE), 0);

        // Neuer Build im Kanal: ersetzt Cache und Kopie in der Instanz.
        publish_all("0.4.0", "b");
        assert_eq!(updater.check_now(None).await, client_mod_update::CheckOutcome::Updated("0.4.0".into()));
        assert!(!cache.join("0.3.0").exists(), "alter Cache weg");
        sync(&http, &paths, None, Some(&updater), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"cubiqclient-fabric-1.21.1.jar-b");
        assert_eq!((server.hits(FABRIC), server.hits(FORGE), server.hits(NEO)), (2, 0, 0));

        // Offline mit Cache: bleibt beim geprüften Jar.
        let offline = ClientModUpdater::for_tests(cache.clone(), "http://127.0.0.1:9/", &key.public);
        assert_eq!(offline.check_now(None).await, client_mod_update::CheckOutcome::Failed);
        std::fs::remove_file(&jar).unwrap();
        sync(&http, &paths, None, Some(&offline), &inst, &ui, false, true, &[], &[]).await.unwrap();
        assert_eq!(std::fs::read(&jar).unwrap(), b"cubiqclient-fabric-1.21.1.jar-b");
    }

    #[tokio::test]
    async fn alte_mod_wird_am_versionsstempel_erkannt() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path());
        assert!(!installed_is_legacy(&paths, "a").await, "unbekannt = neues Protokoll");
        std::fs::create_dir_all(paths.instance_dir("a")).unwrap();
        for (version, legacy) in [("0.3.0", true), ("0.5.0", true), ("0.5.1", false), ("0.6.0", false), ("kaputt", false)] {
            std::fs::write(paths.instance_dir("a").join(VERSION_MARKER), version).unwrap();
            assert_eq!(installed_is_legacy(&paths, "a").await, legacy, "{version}");
        }
    }
}
