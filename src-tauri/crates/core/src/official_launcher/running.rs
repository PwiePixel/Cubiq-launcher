//! Läuft der offizielle Launcher gerade? Erkannt wird nur am Prozessnamen – Kommandozeilen oder
//! Umgebung fremder Programme liest Cubiq nie (dort können Tokens stehen).

/// Programmnamen unter Windows (klassischer Launcher und Store-Ausgabe).
const WINDOWS_NAMES: &[&str] = &["MinecraftLauncher.exe", "Minecraft.exe"];
/// Unter Linux kürzt der Kernel den Namen in `/proc/<pid>/comm` auf 15 Zeichen.
const LINUX_NAMES: &[&str] = &["minecraft-launcher", "minecraft-launc"];

/// Ist das der Prozessname des offiziellen Launchers?
pub fn is_launcher_name(name: &str, windows: bool) -> bool {
    let name = name.trim();
    if windows {
        WINDOWS_NAMES.iter().any(|n| n.eq_ignore_ascii_case(name))
    } else {
        LINUX_NAMES.contains(&name)
    }
}

/// Prozessnamen aller laufenden Programme dieses Benutzers (nur Namen).
#[cfg(windows)]
fn process_names() -> Vec<String> {
    use windows::Win32::Foundation::CloseHandle;
    use windows::Win32::System::Diagnostics::ToolHelp::{
        CreateToolhelp32Snapshot, PROCESSENTRY32W, Process32FirstW, Process32NextW, TH32CS_SNAPPROCESS,
    };

    let mut names = Vec::new();
    // SAFETY: Schnappschuss der Prozessliste; das Handle wird unten geschlossen.
    let Ok(snapshot) = (unsafe { CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0) }) else { return names };
    let mut entry = PROCESSENTRY32W { dwSize: std::mem::size_of::<PROCESSENTRY32W>() as u32, ..Default::default() };
    // SAFETY: `entry` ist korrekt initialisiert (dwSize gesetzt) und lebt während der Aufrufe.
    let mut ok = unsafe { Process32FirstW(snapshot, &raw mut entry) }.is_ok();
    while ok {
        // Nur der Dateiname der Programmdatei (szExeFile) – keine Kommandozeile.
        let len = entry.szExeFile.iter().position(|&c| c == 0).unwrap_or(entry.szExeFile.len());
        names.push(String::from_utf16_lossy(&entry.szExeFile[..len]));
        // SAFETY: wie oben.
        ok = unsafe { Process32NextW(snapshot, &raw mut entry) }.is_ok();
    }
    // SAFETY: Handle aus CreateToolhelp32Snapshot, nur hier geschlossen.
    unsafe {
        let _ = CloseHandle(snapshot);
    }
    names
}

/// Linux: `/proc/<pid>/comm` – der kurze Programmname, nichts sonst.
#[cfg(not(windows))]
fn process_names() -> Vec<String> {
    let Ok(entries) = std::fs::read_dir("/proc") else { return Vec::new() };
    entries
        .flatten()
        .filter(|e| e.file_name().to_str().is_some_and(|n| !n.is_empty() && n.bytes().all(|b| b.is_ascii_digit())))
        .filter_map(|e| std::fs::read_to_string(e.path().join("comm")).ok())
        .collect()
}

/// Läuft irgendeine Ausgabe des offiziellen Launchers?
pub fn is_running() -> bool {
    if crate::platform::MOBILE {
        return false;
    }
    process_names().iter().any(|n| is_launcher_name(n, cfg!(windows)))
}
