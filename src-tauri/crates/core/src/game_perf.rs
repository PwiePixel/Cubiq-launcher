//! Live-Leistung eines laufenden Spiels für die Anzeige im Launcher (etwa einmal je Sekunde abgefragt, nur solange
//! sie zu sehen ist): CPU-Zeit und Arbeitsspeicher des Spielprozesses vom System (`platform::process_usage`), dazu
//! Bildrate und Java-Speicher, wenn der Cubiq Client sie über den Cubiq-Link meldet ([`crate::link::LinkPerf`]).
//!
//! Der Kern bleibt zustandslos: Er liefert die gesamte CPU-Zeit und einen monotonen Zeitstempel, die Oberfläche
//! rechnet aus zwei Messungen die Auslastung (`app/utils/perfLive.ts`).

use std::sync::OnceLock;
use std::time::Instant;

use serde::Serialize;

/// CPU-Zeit und Arbeitsspeicher eines Prozesses (samt Kindern, wo das System sie nennt).
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct ProcessUsage {
    /// Gesamte CPU-Zeit seit dem Start (Kernel + Benutzer) in ms.
    pub cpu_ms: u64,
    /// Belegter Arbeitsspeicher (Working Set bzw. RSS) in Bytes.
    pub rss_bytes: u64,
}

/// Eine Messung für die Oberfläche.
#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GamePerf {
    /// Zeitpunkt der Messung (ms, monoton – nur Abstände zählen).
    pub at_ms: u64,
    /// Gesamte CPU-Zeit des Spielprozesses (ms); `None`, wenn das System sie nicht nennt.
    pub cpu_ms: Option<u64>,
    /// Arbeitsspeicher des Spielprozesses (Bytes).
    pub ram_bytes: Option<u64>,
    /// Logische Prozessoren (Auslastung „vom ganzen PC“).
    pub cores: u32,
    /// Arbeitsspeicher des PCs: eingebaut und gerade frei (MB).
    pub system_total_mb: Option<u32>,
    pub system_free_mb: Option<u32>,
    /// Ist der Cubiq Client gerade verbunden?
    pub client: bool,
    /// Vom Cubiq Client (nur frische Meldungen).
    pub fps: Option<u32>,
    /// Die Bildrate wird gerade absichtlich gebremst (Fenster im Hintergrund, „Dynamische FPS“).
    pub fps_limited: bool,
    pub heap_used_mb: Option<u32>,
    pub heap_max_mb: Option<u32>,
}

/// Monotone Zeit seit dem ersten Aufruf in ms.
pub fn monotonic_ms() -> u64 {
    static START: OnceLock<Instant> = OnceLock::new();
    u64::try_from(START.get_or_init(Instant::now).elapsed().as_millis()).unwrap_or(u64::MAX)
}

/// Logische Prozessoren dieses PCs (mindestens 1).
pub fn cores() -> u32 {
    std::thread::available_parallelism().map_or(1, |n| u32::try_from(n.get()).unwrap_or(u32::MAX))
}

/// `/proc/<pid>/stat` → `utime + stime` in Ticks (Felder 14 und 15). Der Programmname steht in Klammern und darf
/// selbst Leerzeichen und Klammern enthalten – gezählt wird ab der letzten `)`.
#[cfg_attr(not(unix), allow(dead_code))]
pub(crate) fn parse_stat_cpu_ticks(stat: &str) -> Option<u64> {
    let rest = &stat[stat.rfind(')')? + 1..];
    let fields: Vec<&str> = rest.split_whitespace().collect();
    // Feld 3 (Zustand) = Index 0 → Feld 14 = Index 11, Feld 15 = Index 12.
    let utime: u64 = fields.get(11)?.parse().ok()?;
    let stime: u64 = fields.get(12)?.parse().ok()?;
    utime.checked_add(stime)
}

/// `/proc/<pid>/statm` → Seiten im Arbeitsspeicher (zweites Feld).
#[cfg_attr(not(unix), allow(dead_code))]
pub(crate) fn parse_statm_resident(statm: &str) -> Option<u64> {
    statm.split_whitespace().nth(1)?.parse().ok()
}

/// `/proc/<pid>/task/<tid>/children` → PIDs der Kinder („123 456 “).
#[cfg_attr(not(unix), allow(dead_code))]
pub(crate) fn parse_children(text: &str) -> Vec<u32> {
    text.split_whitespace().filter_map(|p| p.parse().ok()).filter(|p| *p > 0).collect()
}

impl crate::Launcher {
    /// Live-Werte des ersten Prozesses der Instanz; `None`, wenn er nicht (mehr) läuft oder kein eigener Prozess
    /// ist (Spiel-Engine am Handy).
    pub async fn game_perf(&self, instance_id: &str) -> Option<GamePerf> {
        let game = self.games.running().into_iter().find(|g| g.instance_id == instance_id && g.key == instance_id)?;
        if game.pid == 0 {
            return None;
        }
        let pid = game.pid;
        // Dateien unter /proc bzw. Prozess-Abfragen: kurz, aber nicht auf dem Async-Thread.
        let usage = tokio::task::spawn_blocking(move || crate::platform::process_usage(pid)).await.ok().flatten();
        let link = self.link.perf(instance_id);
        Some(GamePerf {
            at_ms: monotonic_ms(),
            cpu_ms: usage.map(|u| u.cpu_ms),
            ram_bytes: usage.map(|u| u.rss_bytes),
            cores: cores(),
            system_total_mb: crate::platform::total_memory_mb(),
            system_free_mb: crate::platform::available_memory_mb(),
            client: self.link.linked().iter().any(|id| id == instance_id),
            fps: link.and_then(|l| l.fps),
            fps_limited: link.is_some_and(|l| l.limited),
            heap_used_mb: link.and_then(|l| l.heap_used_mb),
            heap_max_mb: link.and_then(|l| l.heap_max_mb),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn liest_cpu_zeit_und_speicher_aus_proc() {
        // Programmname mit Leerzeichen und Klammern; utime = 1500, stime = 230.
        let stat = "4242 (java (Main) x) S 1 4242 4242 0 -1 4194560 120 0 0 0 1500 230 0 0 20 0 41 0 9123 0 0";
        assert_eq!(parse_stat_cpu_ticks(stat), Some(1730));
        assert_eq!(parse_stat_cpu_ticks("4242 (java) S 1 2"), None);
        assert_eq!(parse_stat_cpu_ticks("kaputt"), None);
        assert_eq!(parse_statm_resident("1048576 524288 2048 1 0 600000 0\n"), Some(524_288));
        assert_eq!(parse_statm_resident("1048576"), None);
        assert_eq!(parse_children("123 456 \n"), vec![123, 456]);
        assert_eq!(parse_children(""), Vec::<u32>::new());
        assert_eq!(parse_children("0 x 7"), vec![7]);
    }

    #[test]
    fn misst_den_eigenen_prozess() {
        assert!(cores() >= 1);
        let a = monotonic_ms();
        // Etwas rechnen, damit CPU-Zeit vergeht.
        let mut x = 0u64;
        for i in 0..20_000_000u64 {
            x = x.wrapping_mul(31).wrapping_add(i);
        }
        assert!(x != 1);
        assert!(monotonic_ms() >= a);
        if cfg!(any(windows, target_os = "linux")) {
            let usage = crate::platform::process_usage(std::process::id()).expect("eigener Prozess");
            assert!(usage.rss_bytes > 1024 * 1024, "{usage:?}");
            assert!(usage.cpu_ms > 0, "{usage:?}");
            // Ein PID, den es nicht gibt.
            assert!(crate::platform::process_usage(u32::MAX - 7).is_none());
        }
    }

    #[test]
    fn messung_als_json_fuer_die_oberflaeche() {
        let perf = GamePerf {
            at_ms: 5,
            cpu_ms: Some(1200),
            ram_bytes: Some(3 << 30),
            cores: 8,
            system_total_mb: Some(16_000),
            system_free_mb: Some(6000),
            client: true,
            fps: Some(144),
            fps_limited: false,
            heap_used_mb: Some(2100),
            heap_max_mb: Some(4096),
        };
        let json = serde_json::to_value(&perf).unwrap();
        for key in ["atMs", "cpuMs", "ramBytes", "cores", "systemTotalMb", "systemFreeMb", "client", "fps", "fpsLimited", "heapUsedMb", "heapMaxMb"] {
            assert!(json.get(key).is_some(), "{key}");
        }
    }

    #[tokio::test]
    async fn nur_fuer_laufende_spiele() {
        let dir = tempfile::tempdir().unwrap();
        let launcher = crate::Launcher::init(dir.path(), std::sync::Arc::new(|_| {})).await.unwrap();
        assert!(launcher.game_perf("survival").await.is_none());
    }
}
