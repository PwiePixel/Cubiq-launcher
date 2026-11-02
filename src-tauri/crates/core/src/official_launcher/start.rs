//! Den offiziellen Launcher öffnen – wie ein Klick im Startmenü. Er läuft danach unabhängig von Cubiq.

use std::process::Stdio;
use std::time::Duration;

use super::detect::StartCommand;

/// So lange wird auf einen sofortigen Fehlstart geachtet (fehlende Bibliothek, kaputtes Paket …).
const EARLY_EXIT: Duration = Duration::from_millis(1500);

/// Startet den Launcher. `Err`, wenn er sich nicht starten ließ oder sofort mit einem Fehler endete –
/// dann öffnet ihn der Spieler selbst (die Oberfläche erklärt, wie).
pub fn spawn(cmd: &StartCommand) -> std::io::Result<()> {
    let mut command = std::process::Command::new(&cmd.program);
    command.args(&cmd.args).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    // AppImage: Pfade aus dem eingehängten Abbild gehören nicht in fremde Programme.
    crate::platform::env::clean_std(&mut command);
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        // Eigene Prozessgruppe: Schließen von Cubiq (oder Strg+C im Terminal) beendet ihn nicht mit.
        command.process_group(0);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // CREATE_NO_WINDOW: kein Konsolenfenster für den Explorer-Aufruf.
        command.creation_flags(0x0800_0000);
    }
    let mut child = command.spawn()?;
    if cmd.check_exit {
        let steps = EARLY_EXIT.as_millis() / 100;
        for _ in 0..steps {
            std::thread::sleep(Duration::from_millis(100));
            if let Some(status) = child.try_wait()? {
                // Schnell und ohne Fehler zu Ende: Ein schon laufender Launcher hat übernommen.
                return if status.success() { Ok(()) } else { Err(std::io::Error::other(format!("Exit {status}"))) };
            }
        }
    }
    // Nicht auf das Ende warten, den Prozess aber später einsammeln (keine Zombies unter Linux).
    std::thread::spawn(move || {
        let _ = child.wait();
    });
    Ok(())
}
