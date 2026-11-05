//! Anbindung des Cubiq-Link an den Launcher: Clips, Welt-Hosting. Konten gehen nie über den Link.

use std::sync::Weak;

use futures::future::BoxFuture;

use super::{ClipsHandler, HandlerResult, HostingHandler, LinkModSource, LinkPreview};
use crate::{Error, Launcher};

/// Fester Fehlercode für die Mod – nie die interne Meldung.
fn code(e: &Error) -> &'static str {
    match e {
        Error::Cancelled => "cancelled",
        Error::Http(_) => "offline",
        Error::Auth(msg) if msg.code == "auth.loginInProgress" => "busy",
        Error::Auth(_) | Error::AuthNotApproved => "auth_failed",
        _ => "error",
    }
}

/// `clips.enable` → [`Launcher::enable_clips_from_game`].
pub(crate) fn clips_enabler(launcher: Weak<Launcher>) -> super::ClipsEnabler {
    std::sync::Arc::new(move |instance_id: String| {
        let launcher = launcher.clone();
        Box::pin(async move {
            let launcher = launcher.upgrade().ok_or("error")?;
            launcher.enable_clips_from_game(&instance_id).await
        })
    })
}

/// `clips.preview` / `clips.open` → Clip-Bibliothek des Launchers.
pub(crate) struct ClipsBridge {
    pub(crate) launcher: Weak<Launcher>,
}

impl ClipsHandler for ClipsBridge {
    fn preview(&self, instance_id: String, clip: String) -> BoxFuture<'static, HandlerResult<LinkPreview>> {
        let launcher = self.launcher.clone();
        Box::pin(async move { launcher.upgrade().ok_or("error")?.clip_preview_for_game(&instance_id, &clip).await })
    }

    fn open(&self, instance_id: String, clip: String) -> BoxFuture<'static, HandlerResult<()>> {
        let launcher = self.launcher.clone();
        Box::pin(async move { launcher.upgrade().ok_or("error")?.open_clip_from_game(&instance_id, &clip).await })
    }
}

/// `hosting.mods` / `hosting.open` → Welt-Hosting mit Mods.
pub(crate) struct HostingBridge {
    pub(crate) launcher: Weak<Launcher>,
}

impl HostingHandler for HostingBridge {
    fn identify_mods(&self, instance_id: String) -> BoxFuture<'static, HandlerResult<Vec<LinkModSource>>> {
        let launcher = self.launcher.clone();
        Box::pin(async move {
            let launcher = launcher.upgrade().ok_or("error")?;
            if !launcher.games().is_running(&instance_id) {
                return Err("not_allowed");
            }
            launcher.identify_instance_mods(&instance_id).await.map_err(|e| code(&e))
        })
    }

    fn open_world(&self, instance_id: String, room_id: String) -> BoxFuture<'static, HandlerResult<()>> {
        let launcher = self.launcher.clone();
        Box::pin(async move { launcher.upgrade().ok_or("error")?.open_world_from_game(&instance_id, &room_id) })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fehlercodes_verraten_nichts() {
        assert_eq!(code(&Error::Cancelled), "cancelled");
        assert_eq!(code(&Error::auth(crate::msg!("auth.loginInProgress", "x"))), "busy");
        assert_eq!(code(&Error::auth(crate::msg!("auth.sessionExpired", "x"))), "auth_failed");
        assert_eq!(code(&Error::Internal("geheim".into())), "error");
    }
}
