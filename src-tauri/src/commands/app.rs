use serde::Serialize;
use tauri::{AppHandle, State};
use cubiq_core::data_location::{DataLocation, RootSource};

use crate::LauncherState;
use crate::error::CommandResult;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppInfo {
    version: &'static str,
    data_dir: String,
    /// z. B. „Windows 11 (24H2, Build 26100)“ oder „Arch Linux (Kernel 6.10.2)“.
    os: String,
    /// Was es auf diesem System gibt (Plattform, Firewall, Papierkorb, Clips, Update-Weg).
    capabilities: cubiq_core::platform::Capabilities,
    /// Schutz der gespeicherten Anmeldedaten: `dpapi`, `keyring`, `file` oder `none`.
    token_protection: &'static str,
    /// Woher der Datenordner kommt: `default`, `custom`, `portable` oder `env`.
    data_source: RootSource,
    /// Lässt sich der Datenordner verschieben (nicht portabel, nicht per Umgebungsvariable)?
    data_movable: bool,
    /// Gewählter eigener Datenordner, der beim Start fehlte (dann gilt der Standard).
    missing_data_dir: Option<String>,
}

#[tauri::command]
pub fn app_info(launcher: State<'_, LauncherState>, location: State<'_, DataLocation>) -> AppInfo {
    AppInfo {
        version: cubiq_core::LAUNCHER_VERSION,
        data_dir: launcher.paths().root().display().to_string(),
        os: cubiq_core::system::os_description(),
        capabilities: cubiq_core::platform::capabilities(),
        token_protection: cubiq_core::auth::crypto::protection(),
        data_source: location.source,
        data_movable: location.movable(),
        missing_data_dir: location.missing_custom.as_ref().map(|p| p.display().to_string()),
    }
}

/// Cubiq Client: mitgelieferte Version und ein bereits geladenes Update.
#[tauri::command]
pub async fn client_mod_status(launcher: State<'_, LauncherState>) -> CommandResult<cubiq_core::client_mod::ClientModStatus> {
    cubiq_core::platform::desktop_only()?;
    Ok(launcher.client_mod_status().await)
}

#[tauri::command]
pub fn open_data_dir(app: AppHandle, launcher: State<'_, LauncherState>) -> CommandResult<()> {
    let path = launcher.paths().root().display().to_string();
    crate::open::path(&app, path)?;
    Ok(())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FirewallStatus {
    total: usize,
    missing: usize,
}

#[tauri::command]
pub async fn firewall_status(launcher: State<'_, LauncherState>) -> CommandResult<FirewallStatus> {
    cubiq_core::platform::desktop_only()?;
    Ok(FirewallStatus {
        total: cubiq_core::firewall::runtime_programs(launcher.paths()).len(),
        missing: cubiq_core::firewall::missing(launcher.paths()).await.len(),
    })
}

/// Trägt die Firewall-Freigabe für alle Java-Versionen des Launchers ein
/// (eine Windows-Admin-Abfrage).
#[tauri::command]
pub async fn firewall_allow_all(launcher: State<'_, LauncherState>) -> CommandResult<usize> {
    cubiq_core::platform::desktop_only()?;
    let programs = cubiq_core::firewall::runtime_programs(launcher.paths());
    Ok(cubiq_core::firewall::allow(launcher.paths(), programs).await?)
}
