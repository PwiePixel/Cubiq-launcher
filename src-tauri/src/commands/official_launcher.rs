//! „Mit dem Minecraft Launcher spielen“ (siehe `cubiq_core::official_launcher`).

use cubiq_core::official_launcher::{HandoffOptions, HandoffOutcome, OfficialStatus, PlayWith, Prefs};
use cubiq_core::prepare::StageProgress;
use tauri::ipc::Channel;
use tauri::{AppHandle, State};

use crate::LauncherState;
use crate::commands::tasks::tracked;
use crate::error::CommandResult;

/// Gefundene Ausgaben, Wahl „Spiele starten mit“, eingetragene Cubiq-Installationen.
#[tauri::command]
pub async fn official_launcher_status(launcher: State<'_, LauncherState>) -> CommandResult<OfficialStatus> {
    Ok(launcher.official_launcher_status().await)
}

/// Wahl bzw. „Erklärung gesehen“ speichern (`null` = unverändert).
#[tauri::command]
pub async fn official_launcher_set_prefs(
    launcher: State<'_, LauncherState>,
    play_with: Option<PlayWith>,
    explained: Option<bool>,
) -> CommandResult<Prefs> {
    Ok(launcher.set_official_prefs(play_with, explained).await?)
}

/// Instanz vorbereiten, im offiziellen Launcher eintragen und ihn öffnen. Fortschritt wie beim Start.
#[tauri::command]
pub async fn play_with_official_launcher(
    app: AppHandle,
    launcher: State<'_, LauncherState>,
    id: String,
    ignore_running: Option<bool>,
    on_progress: Channel<StageProgress>,
    task_id: Option<String>,
) -> CommandResult<HandoffOutcome> {
    let report = move |progress| {
        let _ = on_progress.send(progress);
    };
    let options = HandoffOptions { ignore_running: ignore_running.unwrap_or(false) };
    let work = launcher.play_with_official_launcher(&id, options, &report);
    Ok(tracked(&app, task_id, work).await?)
}

/// Nur öffnen (z. B. damit er beim ersten Mal seine Dateien anlegt). `false`: ließ sich nicht starten.
#[tauri::command]
pub async fn open_official_launcher(launcher: State<'_, LauncherState>) -> CommandResult<bool> {
    Ok(launcher.open_official_launcher().await?)
}

/// Alle Cubiq-Installationen aus dem offiziellen Launcher entfernen; liefert, wie viele.
#[tauri::command]
pub async fn remove_official_profiles(launcher: State<'_, LauncherState>) -> CommandResult<usize> {
    Ok(launcher.remove_official_profiles().await?)
}
