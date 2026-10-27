package dev.pwiepixel.cubiqclient.core.comfort;

import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.panorama.Panorama;
import dev.pwiepixel.cubiqclient.core.panorama.PanoramaPanel;
import dev.pwiepixel.cubiqclient.core.profile.ServerPattern;
import dev.pwiepixel.cubiqclient.core.profile.ServerProfilesPanel;
import dev.pwiepixel.cubiqclient.core.tooltip.Tooltips;
import dev.pwiepixel.cubiqclient.core.ui.menu.ModulePanel;

import java.nio.file.Path;

/**
 * Gemeinsamer Einstieg des Komfort-Pakets 2 für die Bäume (Tooltips, Server-Profile, Panorama): einmal
 * {@link #install}, dann je Client-Tick {@link #tickProfiles}. Alles fängt Fehler selbst ab – ein Problem hier darf
 * das Spiel nie stören.
 */
public final class Comfort {
	private Comfort() {
	}

	/**
	 * @param panoramaSupported gibt es die Panorama-Aufnahme in dieser Version/diesem Baum?
	 */
	public static void install(CubiqModules modules, Path gameDir, final boolean panoramaSupported) {
		Tooltips.get().init(modules.comfort);
		Panorama.get().setGameDir(gameDir);
		ModulePanel.Registry.set(modules.comfort.panorama, new PanoramaPanel(modules.comfort, new PanoramaPanel.Supported() {
			@Override
			public boolean supported() {
				return panoramaSupported;
			}
		}));
		ModulePanel.Registry.set(modules.comfort.serverProfiles, new ServerProfilesPanel(modules.serverProfiles));
	}

	/**
	 * Kontext eines Ticks für die Server-Profile.
	 *
	 * @param inWorld      Welt + Spieler vorhanden
	 * @param singleplayer integrierter Server (Einzelspieler, auch für LAN geöffnet)
	 * @param address      Serveradresse (null = unbekannt)
	 */
	public static String context(boolean inWorld, boolean singleplayer, String address) {
		if (!inWorld) return null;
		if (singleplayer) return ServerPattern.SINGLEPLAYER;
		return address == null || address.trim().isEmpty() ? ServerPattern.UNKNOWN_SERVER : address.trim();
	}

	/**
	 * Server-Profile weiterschalten.
	 *
	 * @return Meldung für die Aktionsleiste ("" = nichts anzeigen) oder null = nichts geändert; bei nicht-null speichern
	 */
	public static String tickProfiles(CubiqModules modules, String context) {
		try {
			String message = modules.serverProfiles.update(context);
			if (message != null && !message.isEmpty() && !modules.comfort.profilesNotify.get()) return "";
			return message;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
