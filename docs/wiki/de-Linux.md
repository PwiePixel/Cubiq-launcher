# Linux

> Kopie der Wiki-Seite „de-Linux“ – ins GitHub-Wiki einfügen (`de-Linux.md`).

Der Cubiq Launcher läuft auf jedem x86_64-Linux mit glibc 2.35 oder neuer (Ubuntu 22.04+, Debian 12+, Fedora 36+,
Arch, openSUSE Tumbleweed, …), unter Wayland und X11. Java wird automatisch geladen.

## Installieren

| Distribution | Befehl |
|---|---|
| Arch Linux / Manjaro / EndeavourOS | `yay -S cubiq-launcher-bin` oder `paru -S cubiq-launcher-bin` |
| Debian / Ubuntu / Mint / Pop!_OS | `.deb` herunterladen, dann `sudo apt install ./Cubiq-Launcher_<version>_amd64.deb` |
| Fedora | `.rpm` herunterladen, dann `sudo dnf install ./Cubiq-Launcher-<version>-1.x86_64.rpm` |
| openSUSE | `sudo zypper install ./Cubiq-Launcher-<version>-1.x86_64.rpm` |
| Jede Distribution | `.AppImage` herunterladen, `chmod +x` und starten |
| Flatpak | `flatpak install flathub dev.pwiepixel.cubiqlauncher` (sobald verfügbar) |

Nur das AppImage aktualisiert sich selbst. Alle anderen Pakete aktualisierst du über die Paketverwaltung – der
Launcher zeigt „Update verfügbar“ und verlinkt das Release.

## Wo liegt was?

| Was | Pfad |
|---|---|
| Launcher-Daten (Instanzen, Java, Libraries) | `~/.local/share/Cubiq-Launcher` |
| Flatpak | `~/.var/app/dev.pwiepixel.cubiqlauncher/data/Cubiq-Launcher` |
| Launcher-Log | `~/.local/share/dev.pwiepixel.cubiqlauncher/logs` |
| Anmeldeschlüssel | Schlüsselbund (Secret Service), sonst `~/.local/share/Cubiq-Launcher/.token-key` (Rechte 0600) |

## Probleme lösen

- **Leeres oder weißes Fenster:** mit `WEBKIT_DISABLE_DMABUF_RENDERER=1 cubiq-launcher` starten (beim NVIDIA-Treiber
  automatisch gesetzt). Bei sehr alten GPUs zusätzlich `WEBKIT_DISABLE_COMPOSITING_MODE=1`.
- **AppImage startet nicht („FUSE“):** `libfuse2` (Debian/Ubuntu) bzw. `fuse2` (Arch) installieren oder mit
  `--appimage-extract-and-run` starten.
- **Minecraft 1.12.2 oder älter stürzt beim Start ab:** `xrandr` installieren (`xorg-xrandr` bei Arch,
  `x11-xserver-utils` bei Debian/Ubuntu, `xrandr` bei Fedora).
- **„Nur Dateirechte“ unter Einstellungen → Speicher:** Es wurde kein Schlüsselbund gefunden. GNOME Keyring oder
  KWallet (oder KeePassXC mit Secret Service) einrichten, entsperren und den Launcher neu starten – der Schlüssel zieht
  von selbst um.
- **Falsche Grafikkarte am Laptop:** Einstellungen → Standardwerte → „Leistungsstarke Grafikkarte“ setzt
  `DRI_PRIME=1` (AMD/Intel) bzw. die NVIDIA-PRIME-Variablen. Eigene Werte unter „Umgebungsvariablen“ haben Vorrang.
- **Start-Wrapper:** Einstellungen → Start-Hooks → Wrapper, z. B. `gamemoderun` oder `prime-run`.
- **Import aus Prism Launcher, MultiMC, Modrinth App oder `~/.minecraft`:** wird automatisch gefunden, auch
  Flatpak-Installationen. In der Flatpak-Version sind nur diese Ordner lesbar; alles andere über „Ordner wählen“.

## Unter Linux nicht verfügbar

- Clip-Aufnahme und die Windows-Firewall-Freigabe.
- ARM64-Builds (Mojang hat keine Java-Runtime für ARM-Linux, und Minecraft ≤ 1.12.2 hat keine ARM-Natives).
