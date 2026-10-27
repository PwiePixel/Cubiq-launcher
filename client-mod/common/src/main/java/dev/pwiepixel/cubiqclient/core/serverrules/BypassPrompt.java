package dev.pwiepixel.cubiqclient.core.serverrules;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.module.BoolSetting;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.Setting;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Hits;
import dev.pwiepixel.cubiqclient.core.ui.Icons;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;

import java.util.List;

/**
 * Nachfrage für das Cubiq-Team im Cubiq-Stil: „Dieser Server sperrt „Minimap“. Als Cubiq-Team trotzdem benutzen?“ (Ja/Nein).
 * Ja hebt die Sperre des Moduls bis zum Trennen auf ({@link ServerRules#bypass}); der Server erfährt davon nichts.
 *
 * <p>Liegt als Ebene über jedem {@code UiScreen} (Menü, HUD-Editor) und schluckt solange alle Eingaben. Nur aus dem
 * Render-Thread benutzen.
 */
public final class BypassPrompt {
	private static String moduleId;
	private static Runnable then;

	private BypassPrompt() {
	}

	/** Ist die Nachfrage offen? */
	public static boolean isOpen() {
		return moduleId != null;
	}

	/** Modul der offenen Nachfrage (Selbsttest), sonst null. */
	public static String openFor() {
		return moduleId;
	}

	/** Darf für diese Einstellung gefragt werden (Server-Sperre, Team-Recht, keine eigene Sperre)? */
	public static boolean available(Setting s) {
		if (s == null || !s.serverLocked() || !ServerRules.canBypass() || ServerRules.get() == null) return false;
		return !(s instanceof BoolSetting) || !((BoolSetting) s).localLocked();
	}

	/** Darf für dieses Modul gefragt werden? */
	public static boolean available(Module m) {
		return m != null && m.serverRestricted() && ServerRules.canBypass() && ServerRules.get() != null;
	}

	/**
	 * Nachfrage öffnen; nach „Ja“ läuft {@code afterUnlock} (z. B. der eigentliche Klick), nach „Nein“ nichts.
	 *
	 * @return false, wenn der Spieler nicht fragen darf (kein Team-Recht, keine Regeln)
	 */
	public static boolean ask(String module, Runnable afterUnlock) {
		if (module == null || !ServerRules.canBypass() || ServerRules.get() == null) return false;
		moduleId = module;
		then = afterUnlock;
		return true;
	}

	/** „Nein“ / Esc. */
	public static void cancel() {
		moduleId = null;
		then = null;
	}

	/** „Ja“ / Enter. */
	public static void confirm() {
		String id = moduleId;
		Runnable after = then;
		cancel();
		ServerRules r = ServerRules.get();
		if (id == null || r == null || !r.bypass(id)) return;
		if (after != null) {
			try {
				after.run();
			} catch (RuntimeException ignored) {
				// Folgeaktion ist nur Komfort.
			}
		}
	}

	/** Taste, solange die Nachfrage offen ist (alle werden geschluckt). */
	public static boolean key(UiKey key) {
		if (!isOpen()) return false;
		if (key == UiKey.ESCAPE) cancel();
		else if (key == UiKey.ENTER) confirm();
		return true;
	}

	/** Zeichnet die Nachfrage über den Bildschirm (nichts, wenn keine offen ist). */
	public static void draw(Canvas c, Hits hits, int width, int height, int mx, int my) {
		if (!isOpen()) return;
		ServerRules r = ServerRules.get();
		Module m = r == null ? null : r.module(moduleId);
		if (m == null) {
			cancel();
			return;
		}
		Theme t = Theme.get();
		c.flush();
		c.push();
		c.raise(420f);
		c.fill(0, 0, width, height, t.scrim);
		hits.noClip();
		// Alles darunter ist gesperrt, solange gefragt wird.
		hits.add(0, 0, width, height, new Runnable() {
			@Override
			public void run() {
			}
		});
		hits.add(0, 0, width, height, 1, new Runnable() {
			@Override
			public void run() {
			}
		});

		int w = Math.min(280, width - 20);
		int textW = w - 20;
		List<String> question = Paint.wrap(c, I18n.tr("serverrules.bypass.text", m.name()), textW);
		List<String> detail = Paint.wrap(c, I18n.tr("serverrules.bypass.detail"), textW);
		int h = 30 + question.size() * 10 + 4 + detail.size() * 10 + 10 + 18 + 10;
		int x = (width - w) / 2;
		int y = Math.max(4, (height - h) / 2);
		Paint.window(c, x, y, w, h);
		Icons.draw(c, "lock", x + 10, y + 9, 1, t.accentText);
		c.text(c.clip(I18n.tr("serverrules.bypass.title"), w - 34), x + 24, y + 9, t.text, false);
		c.fill(x + 1, y + 22, x + w - 1, y + 23, t.border);
		int ty = y + 30;
		for (String line : question) {
			c.text(line, x + 10, ty, t.text, false);
			ty += 10;
		}
		ty += 4;
		int dim = ColorMath.lerp(t.textDim, t.accent, 0.25f);
		for (String line : detail) {
			c.text(line, x + 10, ty, dim, false);
			ty += 10;
		}
		int bw = Math.min(110, (w - 26) / 2);
		int by = y + h - 28;
		int yesX = x + w - 10 - bw;
		int noX = yesX - 6 - bw;
		Paint.button(c, yesX, by, bw, 18, I18n.tr("serverrules.bypass.yes"), true, inside(mx, my, yesX, by, bw, 18));
		Paint.button(c, noX, by, bw, 18, I18n.tr("serverrules.bypass.no"), false, inside(mx, my, noX, by, bw, 18));
		hits.add(yesX, by, bw, 18, new Runnable() {
			@Override
			public void run() {
				confirm();
			}
		});
		hits.add(noX, by, bw, 18, new Runnable() {
			@Override
			public void run() {
				cancel();
			}
		});
		c.pop();
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
