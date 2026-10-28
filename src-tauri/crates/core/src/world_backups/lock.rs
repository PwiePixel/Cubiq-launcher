//! Ist eine Welt gerade in einem Spiel offen?
//!
//! Minecraft legt beim Öffnen einer Welt `session.lock` an bzw. schreibt sie neu – alle
//! Versionen, bevor sie irgendetwas anderes in der Welt anfassen. Neuere Versionen sperren die
//! Datei zusätzlich über das Betriebssystem, solange die Welt offen ist (Java `FileChannel.lock`:
//! unter Windows `LockFileEx`, unter Linux/macOS `fcntl`).
//!
//! Daraus zwei Prüfungen:
//! - [`state`]: Momentaufnahme der Datei. Ändert sie sich, hat jemand die Welt geöffnet – auch ein
//!   altes Minecraft ohne Sperre. Ein Backup vergleicht vor und nach dem Packen damit.
//! - [`is_locked`]: Hält gerade ein Prozess die Sperre?

use std::path::Path;
use std::time::SystemTime;

pub const SESSION_LOCK: &str = "session.lock";

/// Zustand von `session.lock` – ändert er sich, wurde die Welt geöffnet.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LockState {
    pub exists: bool,
    pub len: u64,
    pub modified: Option<SystemTime>,
}

pub fn state(world: &Path) -> LockState {
    match std::fs::metadata(world.join(SESSION_LOCK)) {
        Ok(meta) => LockState { exists: true, len: meta.len(), modified: meta.modified().ok() },
        Err(_) => LockState { exists: false, len: 0, modified: None },
    }
}

/// Anfang von `session.lock` bei Minecraft ab 1.16 (`DirectoryLock` schreibt einen Schneemann, ohne die
/// Datei zu kürzen). Ältere Versionen schreiben dort die Uhrzeit als 8-Byte-Zahl (beginnt mit `00`).
const MODERN_MARK: &[u8] = "\u{2603}".as_bytes();

/// Stammt `session.lock` von einer Version, die die Datei sperrt, solange die Welt offen ist? Dann
/// sagt die Sperre alles – ohne sie ist die Welt zu, auch wenn die Datei eben erst geschrieben wurde.
pub fn is_modern(world: &Path) -> bool {
    use std::io::Read;
    let Ok(file) = std::fs::File::open(world.join(SESSION_LOCK)) else { return false };
    let mut head = Vec::with_capacity(MODERN_MARK.len());
    file.take(MODERN_MARK.len() as u64).read_to_end(&mut head).is_ok() && head == MODERN_MARK
}

/// Hält ein laufendes Spiel die Welt gerade gesperrt?
pub fn is_locked(world: &Path) -> bool {
    let Ok(file) = std::fs::File::open(world.join(SESSION_LOCK)) else { return false };
    locked(&file)
}

#[cfg(windows)]
fn locked(file: &std::fs::File) -> bool {
    // LockFileEx gegen die Sperre von Java: schlägt fehl, solange das Spiel sie hält.
    match file.try_lock_shared() {
        Ok(()) => {
            let _ = file.unlock();
            false
        }
        Err(std::fs::TryLockError::WouldBlock) => true,
        Err(_) => false,
    }
}

#[cfg(unix)]
fn locked(file: &std::fs::File) -> bool {
    // Java sperrt hier per fcntl (nicht flock) – mit F_GETLK nachsehen, ohne selbst zu sperren.
    use std::os::fd::AsRawFd;
    // SAFETY: `flock` ist ein reines C-Struct; komplett mit Nullen ist es gültig.
    let mut probe: libc::flock = unsafe { std::mem::zeroed() };
    probe.l_type = libc::F_WRLCK as _;
    probe.l_whence = libc::SEEK_SET as _;
    // SAFETY: gültiger Dateideskriptor (lebt so lange wie `file`) und ein initialisiertes Struct.
    let rc = unsafe { libc::fcntl(file.as_raw_fd(), libc::F_GETLK, &mut probe) };
    rc == 0 && i32::from(probe.l_type) != libc::F_UNLCK
}

#[cfg(not(any(unix, windows)))]
fn locked(_file: &std::fs::File) -> bool {
    false
}
