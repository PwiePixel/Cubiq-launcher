package dev.pwiepixel.cubiqclient.menus;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.net.StatusPing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Fehlerbildschirm (Verbindung getrennt/gekickt/Anmeldung fehlgeschlagen) für Forge 1.8.9–1.12.2: zwei Knopfreihen
 * unter „Zurück“ – Erneut verbinden, darunter Fehler kopieren und Server-Status. Stil über {@link LegacyMenus}
 * (Menü-Art „Fehler“).
 */
public final class LegacyDisconnect {
	private static final int FIRST_ID = 73210;

	private ServerData target;
	private GuiScreen screen;
	private GuiButton reconnect;
	private GuiButton copy;
	private GuiButton status;

	@SubscribeEvent
	public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
		GuiScreen s = Mc.eventGui(event);
		Minecraft mc = Mc.mc();
		// GuiConnecting setzt die Serverdaten im Konstruktor – hier merken (auch wenn die Anmeldung gleich scheitert).
		if (s instanceof GuiConnecting) {
			ServerData sd = mc.getCurrentServerData();
			if (sd != null) target = sd;
			return;
		}
		if (!(s instanceof GuiDisconnected)) return;
		screen = s;
		List<GuiButton> list = buttonList(event);
		int top = s.height / 2 + 40;
		for (GuiButton b : list) {
			if (b.id == 0) {
				top = yOf(b) + 20 + 8;
				break;
			}
		}
		if (top + 44 > s.height - 32) top = Math.max(4, s.height - 32 - 44);
		int gap = 4;
		int full = Math.min(s.width - 20, 310);
		int left = s.width / 2 - full / 2;
		int half = (full - gap) / 2;
		reconnect = new GuiButton(FIRST_ID + 1, left, top, full, 20, I18n.tr("disconnect.reconnect"));
		copy = new GuiButton(FIRST_ID + 3, left, top + 24, half, 20, I18n.tr("disconnect.copy"));
		status = new GuiButton(FIRST_ID + 4, left + half + gap, top + 24, full - half - gap, 20, I18n.tr("disconnect.status"));
		list.add(reconnect);
		list.add(copy);
		list.add(status);
	}

	@SubscribeEvent
	public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		GuiButton b = button(event);
		if (b == null || Mc.eventGui(event) != screen) return;
		if (b == reconnect) reconnect();
		else if (b == copy) copyError();
		else if (b == status) checkStatus();
		else return;
		event.setCanceled(true);
	}

	private boolean current(GuiScreen s) {
		return s != null && Mc.mc().currentScreen == s;
	}

	private void reconnect() {
		Minecraft mc = Mc.mc();
		ServerData t = target != null ? target : mc.getCurrentServerData();
		if (t == null) {
			if (reconnect != null) reconnect.displayString = I18n.tr("disconnect.noServer");
			return;
		}
		mc.displayGuiScreen(new GuiConnecting(new GuiMultiplayer(new GuiMainMenu()), mc, t));
	}

	private void copyError() {
		GuiScreen s = screen;
		if (s == null) return;
		StringBuilder sb = new StringBuilder();
		String reason = reasonOf(s);
		if (!reason.isEmpty()) sb.append(reason).append('\n');
		if (target != null) sb.append("Server: ").append(target.serverIP).append('\n');
		sb.append("Cubiq Client");
		Mc.setClipboard(sb.toString());
		if (copy != null) copy.displayString = I18n.tr("disconnect.copied");
	}

	private void checkStatus() {
		final GuiScreen s = screen;
		final ServerData t = target;
		final GuiButton b = status;
		if (b == null) return;
		if (t == null) {
			b.displayString = I18n.tr("disconnect.noServer");
			return;
		}
		b.enabled = false;
		b.displayString = I18n.tr("disconnect.statusChecking");
		Thread th = new Thread(() -> {
			final StatusPing.Result r = StatusPing.ping(t.serverIP, 5000);
			Mc.mc().addScheduledTask(() -> {
				if (!current(s)) return;
				b.enabled = true;
				b.displayString = r.ok
						? I18n.tr("disconnect.statusOnline", Math.max(0, r.online), Math.max(0, r.max), Math.max(0, r.latencyMs))
						: I18n.tr("disconnect.statusOffline");
			});
		}, "Cubiq-ServerStatus");
		th.setDaemon(true);
		th.start();
	}

	/** Grund-Text (Feld vom Typ der Chat-Komponente). */
	private static String reasonOf(GuiScreen s) {
		try {
			for (Field f : GuiDisconnected.class.getDeclaredFields()) {
				//? if >=1.9 {
				/*if (f.getType() != net.minecraft.util.text.ITextComponent.class) continue;
				f.setAccessible(true);
				Object v = f.get(s);
				return v == null ? "" : ((net.minecraft.util.text.ITextComponent) v).getUnformattedText();
				*///?} else {
				if (f.getType() != net.minecraft.util.IChatComponent.class) continue;
				f.setAccessible(true);
				Object v = f.get(s);
				return v == null ? "" : ((net.minecraft.util.IChatComponent) v).getUnformattedText();
				//?}
			}
		} catch (IllegalAccessException | RuntimeException ignored) {
			// kein Grund lesbar
		}
		return "";
	}

	@SuppressWarnings("unchecked")
	private static List<GuiButton> buttonList(GuiScreenEvent.InitGuiEvent.Post event) {
		//? if >=1.9 {
		/*return event.getButtonList();
		*///?} else
		return event.buttonList;
	}

	private static GuiButton button(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		//? if >=1.9 {
		/*return event.getButton();
		*///?} else
		return event.button;
	}

	private static int yOf(GuiButton b) {
		//? if >=1.11 {
		/*return b.y;
		*///?} else
		return b.yPosition;
	}

	// --- Nur für den Autotest ---

	public void testTarget(ServerData data) {
		target = data;
	}

	public String testLabels() {
		StringBuilder sb = new StringBuilder();
		for (GuiButton b : new GuiButton[]{reconnect, copy, status}) {
			if (b != null) sb.append('[').append(b.displayString).append("] ");
		}
		return sb.toString();
	}

	/** 0 = Erneut verbinden (hier nicht ausgelöst), 1 = Fehler kopieren, 2 = Server-Status. */
	public void testPress(int index) {
		GuiButton[] all = {reconnect, copy, status};
		GuiButton b = all[index];
		if (b == copy) copyError();
		else if (b == status) checkStatus();
	}
}
