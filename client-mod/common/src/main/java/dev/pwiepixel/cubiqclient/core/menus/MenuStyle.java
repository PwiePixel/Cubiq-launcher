package dev.pwiepixel.cubiqclient.core.menus;

import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Welche Vanilla-Menüs im Redstone-Stil gezeichnet werden (Modul „Menü-Stil“). Jede Loader-Variante ordnet
 * den offenen Minecraft-Bildschirm einer {@link Kind} zu und fragt hier nach; ausgeschaltete Menüs bleiben
 * „klassisch“. Die Menüs selbst werden nie ersetzt – nur Hintergrund, Knopfflächen und Listen neu gezeichnet,
 * damit alle Knöpfe (auch die anderer Mods) weiter funktionieren.
 */
public final class MenuStyle {
	/** Arten von Vanilla-Menüs mit eigenem Schalter. */
	public enum Kind {
		PAUSE, MULTIPLAYER, LOADING, OPTIONS, WORLDS,
		/** Verbindung getrennt, gekickt, Anmeldung fehlgeschlagen. */
		ERROR
	}

	private static volatile CubiqModules modules;

	private MenuStyle() {
	}

	/** Einmal beim Start (je Loader). */
	public static void install(CubiqModules m) {
		modules = m;
	}

	/** Im Redstone-Stil zeichnen? {@code null} = kein bekanntes Menü. */
	public static boolean enabled(Kind kind) {
		CubiqModules m = modules;
		if (kind == null || m == null || !m.menuStyle.isEnabled()) return false;
		switch (kind) {
			case PAUSE:
				return m.menuPause.get();
			case MULTIPLAYER:
				return m.menuMultiplayer.get();
			case LOADING:
				return m.menuLoading.get();
			case OPTIONS:
				return m.menuOptions.get();
			case WORLDS:
				return m.menuWorlds.get();
			case ERROR:
				return m.menuErrors.get();
			default:
				return false;
		}
	}

	/**
	 * Art eines Dialogs ohne eigenes Menü (Bestätigen wie „Server löschen?“, Frage nach dem Server-Ressourcenpaket,
	 * Link öffnen …): die des Menüs, aus dem er geöffnet wurde ({@code opener}, null = keins bekannt). Aus einem
	 * Ladebildschirm heraus (Paket-Frage beim Verbinden) gilt er als Mehrspieler-Dialog; ohne bekanntes Menü in der
	 * Welt bzw. auf einem Server als Pause- bzw. Mehrspieler-Dialog, sonst bleibt er klassisch (null).
	 */
	public static Kind dialogKind(Kind opener, boolean inWorld, boolean onServer) {
		if (opener == Kind.LOADING) return Kind.MULTIPLAYER;
		if (opener != null) return opener;
		if (onServer) return Kind.MULTIPLAYER;
		return inWorld ? Kind.PAUSE : null;
	}

	/** Zusätzliche Cubiq-Knöpfe (Garderobe, Konten, Clips, Freunde, Server-Info) im Pausenmenü? */
	public static boolean pauseButtons() {
		CubiqModules m = modules;
		return m != null && m.menuStyle.isEnabled() && m.menuPauseButtons.get();
	}

	/**
	 * Bewegung in Szenen, Einführung und unbestimmten Ladeanzeigen? Eigene Einstellung „Animierter Hintergrund“ des
	 * Startbildschirms, dazu „Animationen“ des Launchers (siehe {@link #animated(boolean)}).
	 */
	public static boolean animated() {
		CubiqModules m = modules;
		return animated(m == null || m.titleAnimated.get());
	}

	/**
	 * Bewegung, wenn die eigene Einstellung {@code own} sie erlaubt – außer der Launcher steht auf „Reduziert“
	 * ({@link Theme#launcherReducedMotion()}). Der Launcher ändert die eigene Einstellung nie, er beruhigt nur zusätzlich.
	 */
	public static boolean animated(boolean own) {
		return own && !Theme.launcherReducedMotion();
	}
}
