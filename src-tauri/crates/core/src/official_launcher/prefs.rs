//! Wahl „Spiele starten mit“ und ob die Erklärung schon gezeigt wurde – eigene kleine Datei
//! (`official-launcher.json` im Datenordner), unabhängig von `settings.json`.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};

use crate::{Result, fsutil};

const FILE: &str = "official-launcher.json";

/// Womit der Spielen-Knopf startet.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PlayWith {
    /// Mit angemeldetem Konto über Cubiq, sonst über den offiziellen Minecraft Launcher.
    #[default]
    Auto,
    /// Immer Cubiq selbst (braucht ein angemeldetes Konto).
    Cubiq,
    /// Immer über den offiziellen Minecraft Launcher.
    Official,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Prefs {
    pub play_with: PlayWith,
    /// Die kurze Erklärung vor der ersten Übergabe wurde bestätigt.
    pub explained: bool,
}

pub fn path(root: &Path) -> PathBuf {
    root.join(FILE)
}

/// Fehlt die Datei oder ist sie unlesbar: Standard.
pub async fn load(root: &Path) -> Prefs {
    fsutil::read_json::<Prefs>(&path(root)).await.ok().flatten().unwrap_or_default()
}

pub async fn save(root: &Path, prefs: &Prefs) -> Result<()> {
    fsutil::write_json(&path(root), prefs).await
}
