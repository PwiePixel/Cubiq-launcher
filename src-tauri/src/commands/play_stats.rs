//! Spielzeit-Statistik (Seite „Statistik“, Übersicht im Verlauf einer Instanz).

use cubiq_core::play_stats::PlayStats;
use tauri::State;

use crate::LauncherState;
use crate::error::CommandResult;

/// Alle Sitzungen, laufende Spiele und die Spielzeit ohne Datum – rechnen tut die Oberfläche.
#[tauri::command]
pub async fn play_stats(launcher: State<'_, LauncherState>) -> CommandResult<PlayStats> {
    Ok(launcher.play_stats().await?)
}

/// Statistik zurücksetzen (die Spielzeit der Instanzen bleibt).
#[tauri::command]
pub async fn reset_play_stats(launcher: State<'_, LauncherState>) -> CommandResult<()> {
    Ok(launcher.reset_play_stats().await?)
}
