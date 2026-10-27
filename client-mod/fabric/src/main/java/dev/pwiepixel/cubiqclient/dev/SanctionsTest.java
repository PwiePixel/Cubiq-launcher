package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.online.CubiqOnline;
import dev.pwiepixel.cubiqclient.core.social.Social;
import dev.pwiepixel.cubiqclient.core.ui.social.SocialUi;
import dev.pwiepixel.cubiqclient.screen.MenuScreens;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqUiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Selbsttest Strafen ({@code -PcubiqAutotestOnly=sanctions}, mit API-Attrappe über {@code -PcubiqApi}): Sozial-Bildschirm
 * mit Banner und stummgeschalteter Unterhaltung, „Meine Strafen“ und der Einspruch-Dialog. Beendet das Spiel danach.
 * Screenshots: cubiqclient-&lt;mc&gt;-sanctions-*.png.
 */
public final class SanctionsTest {
	private int phase;
	private int wait;
	private int waited;

	public static void install() {
		SanctionsTest test = new SanctionsTest();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}

	private static void log(String text) {
		CubiqClient.LOGGER.info("[Autotest] Strafen: {}", text);
	}

	private static void shot(Minecraft mc, String name) {
		AutoTest.shot(mc, "cubiqclient-sanctions-" + name);
		log("Bild " + name);
	}

	private static SocialUi social(Screen s) {
		return s instanceof CubiqUiScreen && ((CubiqUiScreen) s).ui() instanceof SocialUi ? (SocialUi) ((CubiqUiScreen) s).ui() : null;
	}

	private void tick(Minecraft mc) {
		if (wait > 0) {
			wait--;
			return;
		}
		int current = phase;
		try {
			step(mc);
		} catch (RuntimeException | LinkageError e) {
			CubiqClient.LOGGER.error("[Autotest] Strafen: Fehler in Phase {} – weiter", current, e);
			if (phase == current) phase++;
			wait = 5;
		}
		if (phase > 100) {
			phase = -1000;
			mc.stop();
		}
	}

	private void step(Minecraft mc) {
		Screen screen = Mc.screen();
		SocialUi ui = social(screen);
		CubiqOnline online = CubiqOnline.current();
		Social s = online == null ? null : online.social();
		switch (phase) {
			case 0:
				if (Mc.overlay() != null || screen == null) return;
				mc.options.pauseOnLostFocus = false;
				Mc.setScreen(MenuScreens.social(new CubiqTitleScreen()));
				phase++;
				wait = 10;
				return;
			case 1:
				// Liste und Strafen geladen (Anmeldung an der Attrappe), höchstens ~15 s.
				boolean ready = ui != null && ui.ready() && s != null && s.sanctions().loaded();
				if (!ready && waited++ < 300) {
					wait = 1;
					return;
				}
				log("bereit=" + ready + " aktiv=" + (s == null ? -1 : s.sanctions().activeCount(System.currentTimeMillis())));
				if (ui != null && !ui.openConversationNamed("Bob")) log("keine Unterhaltung");
				phase++;
				wait = 40;
				return;
			case 2:
				shot(mc, "chat");
				if (ui != null) ui.testSanctions();
				phase++;
				wait = 20;
				return;
			case 3:
				shot(mc, "list");
				if (ui != null && !ui.testAppeal("Ich habe Bob nicht beleidigt – das war ein Insider-Witz unter Freunden.\n"
						+ "Er hat es selbst bestätigt, siehe Chat.")) {
					log("kein Einspruch möglich");
				}
				phase++;
				wait = 20;
				return;
			case 4:
				shot(mc, "appeal");
				phase = 101;
				wait = 5;
				return;
			default:
				phase = 101;
		}
	}
}
