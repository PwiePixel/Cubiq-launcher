use tauri::State;
use cubiq_core::settings::Settings;

use crate::LauncherState;
use crate::error::CommandResult;

#[tauri::command]
pub async fn get_settings(launcher: State<'_, LauncherState>) -> CommandResult<Settings> {
    Ok(launcher.settings().await)
}

#[tauri::command]
pub async fn update_settings(
    launcher: State<'_, LauncherState>,
    settings: Settings,
) -> CommandResult<Settings> {
    Ok(launcher.update_settings(settings).await?)
}

/// „Bewegung reduzieren“ des Systems, wie die Oberfläche es sieht – damit löst der Kern
/// „Wie System“ der Animationen für den Cubiq Client auf.
#[tauri::command]
pub fn set_system_reduced_motion(launcher: State<'_, LauncherState>, reduced: bool) {
    launcher.set_system_reduced_motion(reduced);
}
