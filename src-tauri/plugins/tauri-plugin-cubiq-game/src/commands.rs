use tauri::{AppHandle, Runtime, command};

use crate::{GameLaunchSpec, Result, RuntimeInfo, SessionId, CubiqGameExt};

#[command]
pub(crate) async fn prepare_runtime<R: Runtime>(app: AppHandle<R>, java_major: u8) -> Result<RuntimeInfo> {
    app.cubiq_game().prepare_runtime(java_major).await
}

#[command]
pub(crate) async fn launch<R: Runtime>(app: AppHandle<R>, spec: GameLaunchSpec) -> Result<SessionId> {
    app.cubiq_game().launch(spec)
}

#[command]
pub(crate) fn runtimes<R: Runtime>(app: AppHandle<R>) -> Vec<RuntimeInfo> {
    app.cubiq_game().runtimes()
}
