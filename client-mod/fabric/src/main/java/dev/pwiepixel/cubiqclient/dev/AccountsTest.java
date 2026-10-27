package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.account.AccountManager;
import dev.pwiepixel.cubiqclient.core.account.GameAccount;
import dev.pwiepixel.cubiqclient.screen.AccountsScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import net.minecraft.client.Minecraft;

/**
 * Selbsttest Kontowechsel ({@code -PcubiqAutotestOnly=accounts}): öffnet den Kontobildschirm auf dem Titelbildschirm,
 * wechselt – mit der Launcher-Attrappe ({@code CUBIQ_CLIENT_LINK}) – zum zweiten Konto und prüft, dass Minecrafts
 * Benutzer getauscht ist. Screenshots: accounts, accounts-switched, menu-accounts. Beendet das Spiel danach.
 */
public final class AccountsTest {
	private int phase;
	private int wait;
	private int waited;

	public static void install() {
		AccountsTest test = new AccountsTest();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(test::tick);
	}

	/** Gesichter geladen (oder endgültig ohne)? */
	private static boolean facesSettled(AccountManager m) {
		dev.pwiepixel.cubiqclient.core.account.FaceCache faces = dev.pwiepixel.cubiqclient.core.account.FaceCache.shared(m.userAgent());
		for (GameAccount a : m.state().accounts) {
			if (!faces.settled(a.uuid)) return false;
		}
		return true;
	}

	private static void log(String what) {
		AccountManager m = AccountManager.get();
		AccountManager.State s = m == null ? null : m.state();
		StringBuilder names = new StringBuilder();
		if (s != null) {
			for (GameAccount a : s.accounts) names.append(a.name).append('/').append(a.source).append(' ');
		}
		CubiqClient.LOGGER.info("[Autotest] Konten {}: modus={} aufgabe={} aktuell={} meldung={} liste=[{}] mc-user={}", what,
				s == null ? null : s.mode, s == null ? null : s.task, s == null ? null : s.current,
				s == null ? null : s.message, names.toString().trim(), Minecraft.getInstance().getUser().getName());
	}

	private void tick(Minecraft mc) {
		if (wait > 0) {
			wait--;
			return;
		}
		AccountManager m = AccountManager.get();
		switch (phase) {
			case 0:
				if (Mc.overlay() != null || Mc.screen() == null) return;
				mc.options.pauseOnLostFocus = false;
				if (!(Mc.screen() instanceof CubiqTitleScreen)) Mc.setScreen(new CubiqTitleScreen());
				phase++;
				wait = 20;
				return;
			case 1:
				Mc.setScreen(AccountsScreen.create(Mc.screen()));
				log("geöffnet");
				phase++;
				wait = 40;
				return;
			case 2:
				// Auf die Launcher-Liste warten (Attrappe) – ohne Launcher gibt es nur das Startkonto.
				if ((m.state().mode != AccountManager.Mode.LOCAL && m.state().accounts.size() < 2 || !facesSettled(m)) && waited++ < 30) {
					wait = 10;
					return;
				}
				log("Liste");
				AutoTest.shot(mc, "cubiqclient-accounts");
				phase++;
				return;
			case 3: {
				GameAccount target = null;
				for (GameAccount a : m.state().accounts) {
					if (!a.uuid.equals(m.state().current)) target = a;
				}
				if (target == null || m.state().mode != AccountManager.Mode.LAUNCHER) {
					CubiqClient.LOGGER.info("[Autotest] Konten: kein zweites Launcher-Konto – Wechsel übersprungen");
					phase = 5;
					return;
				}
				CubiqClient.LOGGER.info("[Autotest] Konten: wechsle zu {}", target.name);
				m.switchTo(target.uuid);
				waited = 0;
				phase++;
				wait = 10;
				return;
			}
			case 4:
				if (m.state().task != AccountManager.Task.NONE && waited++ < 60) {
					wait = 5;
					return;
				}
				log("gewechselt");
				AutoTest.shot(mc, "cubiqclient-accounts-switched");
				phase++;
				return;
			case 5:
				Mc.setScreen(new CubiqMenuScreen(new CubiqTitleScreen()));
				phase++;
				wait = 30;
				return;
			case 6:
				AutoTest.shot(mc, "cubiqclient-menu-accounts");
				CubiqClient.LOGGER.info("[Autotest] Konten fertig");
				phase++;
				wait = 20;
				return;
			case 7:
				phase++;
				mc.stop();
				return;
			default:
		}
	}
}
