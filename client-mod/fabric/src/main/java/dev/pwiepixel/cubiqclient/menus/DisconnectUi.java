package dev.pwiepixel.cubiqclient.menus;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.net.StatusPing;
import dev.pwiepixel.cubiqclient.qol.QolHooks;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Fehlerbildschirm (Verbindung getrennt, gekickt, Anmeldung fehlgeschlagen): zwei Knopfreihen unter „Zurück“ –
 * „Erneut verbinden“, darunter „Fehler kopieren“ und „Server-Status“. Der Bildschirm selbst bleibt der von Minecraft
 * (Stil übernimmt {@link VanillaMenus} als Menü-Art „Fehler“).
 */
public final class DisconnectUi {
	private DisconnectUi() {
	}

	private static volatile ServerData target;
	private static Screen screen;
	private static AbstractWidget reconnect;
	private static AbstractWidget copy;
	private static AbstractWidget status;

	/** Adresse der letzten Verbindung dieser Sitzung oder null (Schnell verbinden: vorab auflösen). */
	public static String lastAddress() {
		ServerData d = target;
		return d == null ? null : d.ip;
	}

	/** Beim Start jeder Verbindung (Mixin am Verbindungsbildschirm). */
	public static void target(ServerData data) {
		if (data != null) target = data;
	}

	public static void afterInit(Screen s, WidgetHost host) {
		if (!(s instanceof DisconnectedScreen)) return;
		screen = s;
		try {
			AbstractWidget back = null;
			for (GuiEventListener child : s.children()) {
				if (child instanceof Button && ((Button) child).visible) {
					back = (Button) child;
					break;
				}
			}
			int gap = 4;
			int full = Math.min(s.width - 20, 310);
			int left = s.width / 2 - full / 2;
			int top = back != null ? VanillaMenus.y(back) + 20 + 8 : s.height / 2 + 40;
			// Unten steht ggf. der Auto-Reconnect-Knopf (height - 28) – nicht überdecken.
			if (top + 44 > s.height - 32) top = Math.max(4, s.height - 32 - 44);
			int half = (full - gap) / 2;
			reconnect = add(host, left, top, full, I18n.tr("disconnect.reconnect"), new Runnable() {
				@Override
				public void run() {
					reconnect();
				}
			});
			copy = add(host, left, top + 24, half, I18n.tr("disconnect.copy"), new Runnable() {
				@Override
				public void run() {
					copyError();
				}
			});
			status = add(host, left + half + gap, top + 24, full - half - gap, I18n.tr("disconnect.status"), new Runnable() {
				@Override
				public void run() {
					checkStatus();
				}
			});
		} catch (RuntimeException | LinkageError e) {
			// Ohne Zusatzknöpfe weiter.
		}
	}

	private static AbstractWidget add(WidgetHost host, int x, int y, int w, String label, Runnable action) {
		AbstractWidget b = (AbstractWidget) VanillaMenus.button(x, y, w, 20, label, action);
		host.cubiqclient$addWidget(b);
		return b;
	}

	/** Knopftext + ausführlicher Tooltip (Tooltips gibt es ab 1.19.3). */
	private static void say(AbstractWidget b, String label, String tip) {
		if (b == null) return;
		VanillaMenus.setMessage(b, label);
		//? if >=1.19.3 {
		if (tip != null) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(VanillaMenus.text(tip)));
		//?}
	}

	private static boolean current(Screen s) {
		return s != null && Mc.screen() == s;
	}

	// --- Erneut verbinden ---

	private static void reconnect() {
		ServerData t = target;
		if (t == null) t = Mc.mc().getCurrentServer();
		if (t == null) {
			if (reconnect != null) VanillaMenus.setMessage(reconnect, I18n.tr("disconnect.noServer"));
			return;
		}
		QolHooks.connect(Mc.mc(), t);
	}

	// --- Fehler kopieren ---

	private static void copyError() {
		Screen s = screen;
		if (s == null) return;
		StringBuilder sb = new StringBuilder();
		String title = s.getTitle() == null ? "" : s.getTitle().getString();
		if (!title.isEmpty()) sb.append(title).append('\n');
		String reason = QolHooks.reason(s);
		if (!reason.isEmpty()) sb.append(reason).append('\n');
		ServerData t = target;
		if (t != null) sb.append("Server: ").append(t.ip).append('\n');
		sb.append("Cubiq Client");
		Mc.setClipboard(sb.toString());
		if (copy != null) VanillaMenus.setMessage(copy, I18n.tr("disconnect.copied"));
	}

	// --- Server-Status ---

	private static void checkStatus() {
		final Screen s = screen;
		final ServerData t = target;
		if (status == null) return;
		if (t == null) {
			VanillaMenus.setMessage(status, I18n.tr("disconnect.noServer"));
			return;
		}
		status.active = false;
		VanillaMenus.setMessage(status, I18n.tr("disconnect.statusChecking"));
		Thread th = new Thread(() -> {
			final StatusPing.Result r = StatusPing.ping(t.ip, 5000);
			Mc.mc().execute(() -> {
				if (!current(s) || status == null) return;
				status.active = true;
				int on = Math.max(0, r.online);
				int max = Math.max(0, r.max);
				long ms = Math.max(0, r.latencyMs);
				if (r.ok) say(status, I18n.tr("disconnect.statusOnline", on, max, ms), I18n.tr("disconnect.statusOnlineTip", on, max, ms));
				else say(status, I18n.tr("disconnect.statusOffline"), I18n.tr("disconnect.statusOfflineTip"));
			});
		}, "Cubiq-ServerStatus");
		th.setDaemon(true);
		th.start();
	}
}
