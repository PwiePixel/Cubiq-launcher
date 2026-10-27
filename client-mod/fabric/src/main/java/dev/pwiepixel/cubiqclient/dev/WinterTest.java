package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.menus.MenuSkin;
import dev.pwiepixel.cubiqclient.core.ui.winter.WinterScene;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

/**
 * Selbsttest Design „Polarlicht“ ({@code -PcubiqAutotestOnly=winter}): Startbildschirm in der Nacht (zweimal, das
 * Polarlicht wiegt sich weiter), am hellen Morgen und in OLED, Cubiq-Menü (Seite „Menü-Stil“ mit der Design-Wahl),
 * Serverliste, Ladebildschirm der Welt und Pausenmenü. Danach gilt wieder das Aussehen aus der Launcher-Datei; das Spiel
 * endet.
 * Screenshots: cubiqclient-&lt;mc&gt;-winter-*.png.
 */
public final class WinterTest {
	private int phase;
	private int wait;
	private int waited;

	public static void install() {
		WinterTest test = new WinterTest();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}

	private static void log(String text) {
		CubiqClient.LOGGER.info("[Autotest] Winter: {}", text);
	}

	private void shot(Minecraft mc, String name) {
		AutoTest.shot(mc, "cubiqclient-winter-" + name);
		log("Bild " + name + " (Szene Ø " + Math.round(WinterScene.shared().averageMicros()) + " µs, Menü Ø "
				+ Math.round(MenuSkin.averageMicros()) + " µs)");
	}

	private boolean waitFor(boolean ready, int maxTries) {
		if (!ready && waited++ < maxTries) {
			wait = 5;
			return false;
		}
		waited = 0;
		return true;
	}

	private static void winter(String theme) {
		Theme.set(Theme.of(theme, "ice", null, Theme.WINTER));
	}

	private void tick(Minecraft mc) {
		if (wait > 0) {
			wait--;
			return;
		}
		try {
			step(mc);
		} catch (RuntimeException e) {
			CubiqClient.LOGGER.error("[Autotest] Winter: Fehler in Phase {}", phase, e);
			mc.stop();
			phase = 999;
		}
	}

	private void step(Minecraft mc) {
		Screen screen = Mc.screen();
		CubiqModules modules = CubiqClient.get().modules();
		switch (phase) {
			case 0:
				if (Mc.overlay() != null || screen == null) return;
				mc.options.pauseOnLostFocus = false;
				winter("dark");
				Mc.setScreen(new CubiqTitleScreen());
				phase++;
				wait = 40;
				return;
			case 1:
				shot(mc, "title");
				WinterScene.shared().setSeconds(40);
				phase++;
				wait = 10;
				return;
			case 2:
				// Dieselbe Szene ein paar Sekunden später: das Polarlicht hat sich weitergewiegt.
				shot(mc, "title-aurora");
				winter("light");
				phase++;
				wait = 20;
				return;
			case 3:
				shot(mc, "title-day");
				winter("oled");
				phase++;
				wait = 10;
				return;
			case 4:
				shot(mc, "title-oled");
				winter("dark");
				Mc.setScreen(new CubiqMenuScreen(new CubiqTitleScreen()).select(modules.menuStyle));
				phase++;
				wait = 25;
				return;
			case 5:
				shot(mc, "cubiqmenu");
				Mc.setScreen(new JoinMultiplayerScreen(new CubiqTitleScreen()));
				phase++;
				wait = 40;
				return;
			case 6:
				shot(mc, "multiplayer");
				AutoTest.startWorld(mc);
				phase++;
				return;
			case 7:
				// Ladebildschirm abpassen (während die Welt entsteht bzw. lädt).
				if (screen instanceof LevelLoadingScreen) {
					wait = 2;
					phase++;
					return;
				}
				if (mc.level != null && waited++ > 20) {
					log("Ladebildschirm verpasst");
					waited = 0;
					phase += 2;
				}
				return;
			case 8:
				if (screen instanceof LevelLoadingScreen) shot(mc, "loading");
				phase++;
				return;
			case 9:
				if (!waitFor(mc.level != null && mc.player != null && Mc.screen() == null, 600)) return;
				wait = 40;
				phase++;
				return;
			case 10:
				Mc.setScreen(new PauseScreen(true));
				phase++;
				wait = 15;
				return;
			case 11:
				shot(mc, "pause");
				Mc.setScreen(new CubiqMenuScreen(screen).select(modules.titleScreen));
				phase++;
				wait = 25;
				return;
			case 12:
				shot(mc, "cubiqmenu-ingame");
				// Wieder das Aussehen aus der Launcher-Datei (bzw. Standard).
				Theme.set(Theme.of("dark", "cubiq", null));
				Theme.loadFrom(mc.gameDirectory.toPath().resolve("config"));
				log("fertig");
				phase++;
				mc.stop();
				return;
			default:
		}
	}
}
