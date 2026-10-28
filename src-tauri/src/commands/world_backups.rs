//! Welt-Backups (Tab „Welten“, Einstellungen → Welt-Backups): Übersicht, Liste, Wiederherstellen,
//! Löschen, Ordner, eigene Einstellung je Instanz. Die automatischen Backups selbst startet der
//! Kern beim Spielstart und nach dem Spielen; ihr Stand kommt als Event `world-backup`.

use cubiq_core::world_backups::{BackupInfo, BackupOverview, BackupUsage, JobView, RestoreMode, RestoreOutcome};
use tauri::ipc::Channel;
use tauri::{AppHandle, State};

use super::tasks::tracked;
use crate::LauncherState;
use crate::error::CommandResult;

#[tauri::command]
pub async fn world_backup_overview(launcher: State<'_, LauncherState>, id: String) -> CommandResult<BackupOverview> {
    Ok(launcher.world_backup_overview(&id).await?)
}

#[tauri::command]
pub async fn world_backup_list(launcher: State<'_, LauncherState>, id: String, folder: String) -> CommandResult<Vec<BackupInfo>> {
    Ok(launcher.world_backup_list(&id, &folder).await?)
}

/// Als Kopie (`copyName` = Name der neuen Welt) oder statt der jetzigen Welt wiederherstellen.
#[allow(clippy::too_many_arguments)]
#[tauri::command]
pub async fn world_backup_restore(
    app: AppHandle,
    launcher: State<'_, LauncherState>,
    id: String,
    folder: String,
    backup: String,
    mode: RestoreMode,
    copy_name: Option<String>,
    on_progress: Channel<u8>,
    task_id: Option<String>,
) -> CommandResult<RestoreOutcome> {
    let work = launcher.world_backup_restore(&id, &folder, &backup, mode, copy_name.as_deref(), move |p| {
        let _ = on_progress.send(p);
    });
    Ok(tracked(&app, task_id, work).await?)
}

#[tauri::command]
pub async fn world_backup_delete(launcher: State<'_, LauncherState>, id: String, folder: String, backup: String) -> CommandResult<()> {
    Ok(launcher.world_backup_delete(&id, &folder, &backup).await?)
}

/// Ordner mit den Backups einer Welt (ohne `folder`: der Instanz) im Datei-Explorer.
#[tauri::command]
pub async fn open_world_backups(app: AppHandle, launcher: State<'_, LauncherState>, id: String, folder: Option<String>) -> CommandResult<()> {
    let dir = launcher.world_backup_folder(&id, folder.as_deref()).await?;
    crate::open::path(&app, dir.display().to_string())?;
    Ok(())
}

/// Ordner aller Welt-Backups im Datei-Explorer.
#[tauri::command]
pub async fn open_world_backup_root(app: AppHandle, launcher: State<'_, LauncherState>) -> CommandResult<()> {
    let dir = launcher.world_backups().root_folder()?;
    crate::open::path(&app, dir.display().to_string())?;
    Ok(())
}

/// Eigene Einstellung der Instanz: `true`/`false` oder `null` = wie in den Einstellungen.
#[tauri::command]
pub async fn set_world_backup_auto(launcher: State<'_, LauncherState>, id: String, enabled: Option<bool>) -> CommandResult<BackupOverview> {
    Ok(launcher.set_world_backup_auto(&id, enabled).await?)
}

#[tauri::command]
pub async fn world_backup_usage(launcher: State<'_, LauncherState>) -> CommandResult<BackupUsage> {
    Ok(launcher.world_backup_usage().await?)
}

/// Backups gelöschter Instanzen entfernen; liefert die freigegebenen Bytes.
#[tauri::command]
pub async fn prune_world_backups(launcher: State<'_, LauncherState>) -> CommandResult<u64> {
    Ok(launcher.prune_world_backups().await?)
}

#[tauri::command]
pub fn world_backup_jobs(launcher: State<'_, LauncherState>) -> Vec<JobView> {
    launcher.world_backups().jobs()
}

/// Laufende automatische Sicherung abbrechen (das Spiel läuft weiter).
#[tauri::command]
pub fn cancel_world_backup_job(launcher: State<'_, LauncherState>, job: u64) -> bool {
    launcher.world_backups().cancel(job)
}
