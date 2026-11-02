//! Wo der offizielle Minecraft Launcher liegt und wie er startet – ohne `cfg`: Alle Pfade kommen
//! aus [`Env`], damit sich jede Plattform mit Temp-Ordnern testen lässt.
//!
//! - Windows klassisch: `%ProgramFiles(x86)%\Minecraft Launcher\MinecraftLauncher.exe`,
//!   Profile in `%APPDATA%\.minecraft\launcher_profiles.json`.
//! - Windows Store/Xbox-App: Paket `Microsoft.4297127D64EC6`, Start über die App-Verknüpfung,
//!   Profile in `%APPDATA%\.minecraft\launcher_profiles_microsoft_store.json`.
//! - Linux: `minecraft-launcher` (Paket oder Archiv von Mojang), `~/.minecraft`.
//! - Linux Flatpak `com.mojang.Minecraft` (inoffiziell): `~/.var/app/com.mojang.Minecraft/.minecraft`.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

/// App-ID des inoffiziellen Flatpaks des offiziellen Launchers (Flathub).
pub const FLATPAK_ID: &str = "com.mojang.Minecraft";
/// Paketfamilie des Store-Launchers und seine App-ID (`shell:AppsFolder\<Familie>!<App>`).
pub const STORE_FAMILY: &str = "Microsoft.4297127D64EC6_8wekyb3d8bbwe";
const STORE_PACKAGE: &str = "Microsoft.4297127D64EC6";
const STORE_PUBLISHER: &str = "8wekyb3d8bbwe";
const STORE_APP: &str = "Minecraft";
pub const LEGACY_PROFILES: &str = "launcher_profiles.json";
pub const STORE_PROFILES: &str = "launcher_profiles_microsoft_store.json";

/// Ausgabe des offiziellen Launchers.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Variant {
    /// Windows: klassischer Launcher (`MinecraftLauncher.exe`).
    WindowsLegacy,
    /// Windows: Microsoft Store bzw. Xbox-App.
    WindowsStore,
    /// Linux: Paket oder Archiv von Mojang.
    LinuxNative,
    /// Linux: Flatpak `com.mojang.Minecraft` (eigene Sandbox).
    LinuxFlatpak,
}

/// Wie der Launcher gestartet wird.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StartCommand {
    pub program: PathBuf,
    pub args: Vec<String>,
    /// Endet der Prozess sofort mit einem Fehler, hat der Start nicht geklappt. Gilt nicht für den
    /// Explorer: Der meldet oft einen Fehler, obwohl die App startet.
    pub check_exit: bool,
}

/// Eine gefundene Installation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Installation {
    pub variant: Variant,
    /// Spielordner des offiziellen Launchers (`.minecraft`).
    pub data_dir: PathBuf,
    /// Seine Installations-Datei (`launcher_profiles*.json`).
    pub profiles_file: PathBuf,
    /// `None` = Programm nicht gefunden (nur seine Daten).
    pub start: Option<StartCommand>,
}

impl Installation {
    pub fn profiles_exist(&self) -> bool {
        self.profiles_file.is_file()
    }

    fn profiles_modified(&self) -> std::time::SystemTime {
        std::fs::metadata(&self.profiles_file).and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH)
    }
}

/// Alles, was die Erkennung von außen braucht.
#[derive(Debug, Clone, Default)]
pub struct Env {
    pub windows: bool,
    /// `%APPDATA%`
    pub appdata: Option<PathBuf>,
    /// `%ProgramFiles(x86)%`, `%ProgramFiles%`
    pub program_files: Vec<PathBuf>,
    /// `%SystemRoot%` (für `explorer.exe`)
    pub system_root: Option<PathBuf>,
    /// Ist das Store-Paket für diesen Benutzer installiert?
    pub store_installed: bool,
    pub home: Option<PathBuf>,
    /// `$XDG_DATA_HOME` bzw. `~/.local/share` (Linux).
    pub data_home: Option<PathBuf>,
    /// Wo Programme gesucht werden (`PATH` und die üblichen Orte, siehe [`Env::current`]).
    pub path_dirs: Vec<PathBuf>,
    /// Flatpak-Installationen (`~/.local/share/flatpak`, `/var/lib/flatpak`).
    pub flatpak_roots: Vec<PathBuf>,
    /// Läuft Cubiq selbst in einer Flatpak-Sandbox? Dann lassen sich Programme des Systems nicht starten.
    pub in_flatpak: bool,
}

impl Env {
    /// Die Umgebung dieses Rechners.
    pub fn current() -> Self {
        let var = |key: &str| std::env::var_os(key).map(PathBuf::from).filter(|p| p.is_absolute());
        let home = if cfg!(windows) { var("USERPROFILE") } else { var("HOME") };
        let data_home = var("XDG_DATA_HOME").or_else(|| home.as_ref().map(|h| h.join(".local").join("share")));
        Self {
            windows: cfg!(windows),
            appdata: var("APPDATA"),
            program_files: [var("ProgramFiles(x86)"), var("ProgramFiles")].into_iter().flatten().collect(),
            system_root: var("SystemRoot").or_else(|| var("windir")),
            store_installed: cfg!(windows) && crate::platform::appx_package_installed(is_store_package),
            path_dirs: search_dirs(std::env::var_os("PATH").as_deref(), home.as_deref()),
            flatpak_roots: if cfg!(windows) {
                Vec::new()
            } else {
                data_home.iter().map(|d| d.join("flatpak")).chain([PathBuf::from("/var/lib/flatpak")]).collect()
            },
            data_home,
            in_flatpak: !cfg!(windows) && (std::env::var_os("FLATPAK_ID").is_some() || Path::new("/.flatpak-info").exists()),
            home,
        }
    }
}

/// Wo Programme gesucht werden: `PATH`, dazu die üblichen Orte des Linux-Pakets und des Archivs
/// von Mojang (ein Programm aus dem Startmenü hat nicht immer den ganzen `PATH`).
fn search_dirs(path: Option<&std::ffi::OsStr>, home: Option<&Path>) -> Vec<PathBuf> {
    let mut dirs: Vec<PathBuf> = path.map(|p| std::env::split_paths(p).filter(|d| d.is_absolute()).collect()).unwrap_or_default();
    if !cfg!(windows) {
        let extra = ["/usr/bin", "/usr/local/bin", "/opt/minecraft-launcher"].map(PathBuf::from);
        for dir in extra.into_iter().chain(home.map(|h| h.join(".local").join("bin"))) {
            if !dirs.contains(&dir) {
                dirs.push(dir);
            }
        }
    }
    dirs
}

/// Paket-Vollname (`Name_Version_Arch__Herausgeber`) des Store-Launchers?
pub fn is_store_package(full_name: &str) -> bool {
    full_name.len() <= 200
        && full_name.split('_').next().is_some_and(|name| name.eq_ignore_ascii_case(STORE_PACKAGE))
        && full_name.rsplit('_').next().is_some_and(|publisher| publisher.eq_ignore_ascii_case(STORE_PUBLISHER))
}

/// Ausführbare Datei `name` in `dirs`.
fn find_program(dirs: &[PathBuf], name: &str) -> Option<PathBuf> {
    dirs.iter().map(|d| d.join(name)).find(|p| is_executable(p))
}

fn is_executable(path: &Path) -> bool {
    let Ok(meta) = std::fs::metadata(path) else { return false };
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        meta.is_file() && meta.permissions().mode() & 0o111 != 0
    }
    #[cfg(not(unix))]
    {
        meta.is_file()
    }
}

/// Startbefehl je Ausgabe (`None`: hier nicht startbar).
pub fn start_command(variant: Variant, env: &Env) -> Option<StartCommand> {
    match variant {
        Variant::WindowsLegacy => env
            .program_files
            .iter()
            .map(|dir| dir.join("Minecraft Launcher").join("MinecraftLauncher.exe"))
            .find(|p| p.is_file())
            .map(|program| StartCommand { program, args: Vec::new(), check_exit: true }),
        Variant::WindowsStore => env.store_installed.then(|| StartCommand {
            program: env.system_root.as_ref().map_or_else(|| PathBuf::from("explorer.exe"), |r| r.join("explorer.exe")),
            args: vec![store_target()],
            check_exit: false,
        }),
        // In der eigenen Flatpak-Sandbox gibt es die Programme des Systems nicht.
        _ if env.in_flatpak => None,
        Variant::LinuxNative => find_program(&env.path_dirs, "minecraft-launcher")
            .map(|program| StartCommand { program, args: Vec::new(), check_exit: true }),
        Variant::LinuxFlatpak => find_program(&env.path_dirs, "flatpak")
            .map(|program| StartCommand { program, args: vec!["run".into(), FLATPAK_ID.into()], check_exit: true }),
    }
}

/// App-Verknüpfung des Store-Launchers für den Explorer.
pub fn store_target() -> String {
    format!("shell:AppsFolder\\{STORE_FAMILY}!{STORE_APP}")
}

/// Flatpak-Daten des offiziellen Launchers (`~/.var/app/com.mojang.Minecraft/.minecraft`).
pub fn flatpak_data_dir(home: &Path) -> PathBuf {
    home.join(".var").join("app").join(FLATPAK_ID).join(".minecraft")
}

fn flatpak_installed(env: &Env) -> bool {
    env.flatpak_roots.iter().any(|root| root.join("app").join(FLATPAK_ID).is_dir())
}

/// Alle Installationen, die es hier gibt (Programm gefunden oder seine Installations-Datei vorhanden).
pub fn detect(env: &Env) -> Vec<Installation> {
    let mut found = Vec::new();
    let mut add = |variant: Variant, data_dir: PathBuf, file: &str, present: bool| {
        let installation = Installation {
            variant,
            profiles_file: data_dir.join(file),
            data_dir,
            start: start_command(variant, env),
        };
        if present || installation.start.is_some() || installation.profiles_exist() {
            found.push(installation);
        }
    };
    if env.windows {
        if let Some(appdata) = &env.appdata {
            let data = appdata.join(".minecraft");
            add(Variant::WindowsLegacy, data.clone(), LEGACY_PROFILES, false);
            add(Variant::WindowsStore, data, STORE_PROFILES, env.store_installed);
        }
    } else if let Some(home) = &env.home {
        add(Variant::LinuxNative, home.join(".minecraft"), LEGACY_PROFILES, false);
        add(Variant::LinuxFlatpak, flatpak_data_dir(home), LEGACY_PROFILES, flatpak_installed(env));
    }
    found
}

/// Welche Installation Cubiq öffnet: zuerst eine, die startet und schon eingerichtet ist (die zuletzt
/// benutzte – ihre Datei ist die jüngste), dann eine eingerichtete ohne bekannten Start, zuletzt
/// eine, die noch nie geöffnet wurde. Gleichstand: Store vor klassisch (der klassische ist veraltet).
pub fn choose(found: &[Installation]) -> Option<&Installation> {
    let rank = |i: &Installation| match (i.start.is_some(), i.profiles_exist()) {
        (true, true) => 3,
        (false, true) => 2,
        (true, false) => 1,
        (false, false) => 0,
    };
    found.iter().max_by(|a, b| {
        rank(a)
            .cmp(&rank(b))
            .then_with(|| a.profiles_modified().cmp(&b.profiles_modified()))
            .then_with(|| (a.variant == Variant::WindowsStore).cmp(&(b.variant == Variant::WindowsStore)))
    })
}

/// In welche Dateien der Eintrag kommt: Unter Windows teilen sich beide Ausgaben `.minecraft` –
/// dort in jede vorhandene, damit die Installation in beiden erscheint. Sonst nur in die gewählte.
pub fn write_targets<'a>(found: &'a [Installation], chosen: &'a Installation) -> Vec<&'a Installation> {
    let mut targets: Vec<&Installation> = found
        .iter()
        .filter(|i| i.data_dir == chosen.data_dir && i.profiles_exist() && matches!(i.variant, Variant::WindowsLegacy | Variant::WindowsStore))
        .collect();
    if !targets.iter().any(|i| i.variant == chosen.variant) {
        targets.insert(0, chosen);
    }
    targets
}
