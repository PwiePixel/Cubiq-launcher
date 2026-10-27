package dev.pwiepixel.cubiqclient.core.ui.social;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Hits;
import dev.pwiepixel.cubiqclient.core.ui.Icons;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.TextInput;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Bedienelemente der Sozial-Oberfläche (Knöpfe, Reiter, Eingabefelder, Bildlaufleisten) mit Klickflächen – wie in
 * {@link dev.pwiepixel.cubiqclient.core.ui.menus.WindowUi}, aber als eigenes Objekt, damit Teilflächen (Liste,
 * Unterhaltung, Dialoge) sie ohne Vererbung nutzen können.
 */
public final class Kit {
	public final Hits hits;
	private final Runnable click;

	public Kit(Hits hits, Runnable click) {
		this.hits = hits;
		this.click = click;
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public void click() {
		click.run();
	}

	/** Klickfläche mit Klickton. */
	public void area(int x, int y, int w, int h, final Runnable action) {
		hits.add(x, y, w, h, new Runnable() {
			@Override
			public void run() {
				click.run();
				action.run();
			}
		});
	}

	/** Klickfläche ohne Ton (Liste, Textfelder). */
	public void quiet(int x, int y, int w, int h, Runnable action) {
		hits.add(x, y, w, h, action);
	}

	/** Rechtsklick-Fläche. */
	public void right(int x, int y, int w, int h, Runnable action) {
		hits.add(x, y, w, h, 1, action);
	}

	/** Knopf (flach, primär in der Akzentfarbe). */
	public void button(Canvas c, int x, int y, int w, int h, String label, boolean primary, boolean enabled, int mx, int my,
			Runnable action) {
		Theme t = Theme.get();
		if (enabled) {
			Paint.button(c, x, y, w, h, label, primary, inside(mx, my, x, y, w, h));
			area(x, y, w, h, action);
		} else {
			Paint.panel(c, x, y, w, h, t.surface, t.border);
			String s = c.clip(label, w - 6);
			c.text(s, x + (w - c.textWidth(s)) / 2, y + (h - 8) / 2, t.textDim, false);
		}
	}

	/** Symbol-Knopf. */
	public void icon(Canvas c, int x, int y, int size, String icon, boolean primary, int mx, int my, Runnable action) {
		Paint.iconButton(c, x, y, size, icon, inside(mx, my, x, y, size, size), primary);
		area(x, y, size, size, action);
	}

	/** Symbol-Knopf, der gerade nicht geht. */
	public void iconDisabled(Canvas c, int x, int y, int size, String icon) {
		Theme t = Theme.get();
		Paint.panel(c, x, y, size, size, t.surface, t.border);
		Icons.draw(c, icon, x + (size - 8) / 2, y + (size - 8) / 2, 1, ColorMath.withAlpha(t.textDim, 120));
	}

	/** Reiter (aktiv = hervorgehoben). */
	public void tab(Canvas c, int x, int y, int w, int h, String label, boolean active, int mx, int my, Runnable action) {
		Theme t = Theme.get();
		boolean hov = inside(mx, my, x, y, w, h);
		if (active) {
			Paint.block(c, x, y, w, h, ColorMath.withAlpha(t.accent, 50));
			Paint.lineH(c, x + 2, x + w - 2, y + h - 2, t.accentText, 1f);
		} else if (hov) {
			Paint.block(c, x, y, w, h, t.surfaceHover);
			Paint.lineH(c, x + 2, x + w - 2, y + h - 2, t.track, 0f);
		}
		String s = c.clip(label, w - 6);
		c.text(s, x + (w - c.textWidth(s)) / 2, y + (h - 8) / 2 - 1, active || hov ? t.text : t.textDim, false);
		area(x, y, w, h, action);
	}

	/** Einzeiliges Feld (Suche, Name, Notiz); Klick fokussiert. */
	public void input(Canvas c, final TextInput in, int x, int y, int w, int h, String hint, final Runnable focus) {
		Theme t = Theme.get();
		Paint.well(c, x, y, w, h, in.focused() ? t.accent : t.border);
		boolean empty = in.isEmpty();
		String shown = empty && !in.focused() ? hint : in.text();
		int ty = y + (h - 8) / 2;
		String visible = shown;
		int maxW = w - 10;
		if (!empty || in.focused()) {
			// Ende zeigen, wenn der Text länger ist als das Feld.
			while (c.textWidth(visible) > maxW && visible.length() > 1) visible = visible.substring(1);
		} else {
			visible = c.clip(shown, maxW);
		}
		c.text(visible, x + 5, ty, empty && !in.focused() ? t.textDim : t.text, false);
		if (in.focused() && (System.currentTimeMillis() / 500) % 2 == 0) {
			int shift = shown.length() - visible.length();
			int cur = Math.max(0, Math.min(in.cursor(), shown.length()) - shift);
			int cx = x + 5 + c.textWidth(visible.substring(0, Math.min(cur, visible.length())));
			if (cx < x + w - 3) c.fill(cx, ty - 1, cx + 1, ty + 9, t.text);
		}
		quiet(x, y, w, h, new Runnable() {
			@Override
			public void run() {
				if (focus != null) focus.run();
				in.setFocused(true);
			}
		});
	}

	/** Schalter-Zeile mit Beschriftung. */
	public void toggle(Canvas c, int x, int y, int w, String label, boolean on, int mx, int my, Runnable action) {
		Theme t = Theme.get();
		Paint.textClipped(c, label, x, y + 3, w - 34, t.text, false);
		Paint.toggle(c, x + w - 28, y, 28, 14, on ? 1f : 0f, inside(mx, my, x + w - 28, y, 28, 14));
		area(x, y, w, 14, action);
	}

	/** Auswahlkreis-Zeile. */
	public void radio(Canvas c, int x, int y, int w, String label, boolean selected, int mx, int my, Runnable action) {
		Theme t = Theme.get();
		boolean hov = inside(mx, my, x, y, w, 14);
		if (hov) c.fill(x, y, x + w, y + 14, t.surfaceHover);
		Paint.pip(c, x + 3, y + 3, 8, selected ? 1f : 0f);
		Paint.textClipped(c, label, x + 16, y + 3, w - 18, selected ? t.text : t.textDim, false);
		area(x, y, w, 14, action);
	}

	/** Häkchen-Kästchen-Zeile. */
	public void check(Canvas c, int x, int y, int w, String label, boolean checked, boolean enabled, int mx, int my,
			Runnable action) {
		Theme t = Theme.get();
		boolean hov = enabled && inside(mx, my, x, y, w, 14);
		if (hov) c.fill(x, y, x + w, y + 14, t.surfaceHover);
		Paint.well(c, x + 2, y + 2, 10, 10, checked ? t.accent : t.border);
		if (checked) Icons.draw(c, "check", x + 3, y + 3, 1, t.text);
		Paint.textClipped(c, label, x + 16, y + 3, w - 18, enabled ? t.text : t.textDim, false);
		if (enabled) area(x, y, w, 14, action);
	}

	/** Senkrechte Bildlaufleiste. */
	public static void scrollbar(Canvas c, int x, int y, int h, int scroll, int maxScroll, int content) {
		if (maxScroll <= 0) return;
		Theme t = Theme.get();
		int barH = Math.max(12, h * h / Math.max(1, content));
		int barY = y + (h - barH) * scroll / Math.max(1, maxScroll);
		c.fill(x, barY, x + 2, barY + barH, t.border);
	}

	/** Knopf für endgültige Aktionen (Löschen, Sperren), siehe {@link Paint#dangerButton}. */
	public void dangerButton(Canvas c, int x, int y, int w, int h, String label, boolean enabled, int mx, int my,
			Runnable action) {
		if (!enabled) {
			button(c, x, y, w, h, label, false, false, mx, my, action);
			return;
		}
		Paint.dangerButton(c, x, y, w, h, label, inside(mx, my, x, y, w, h));
		area(x, y, w, h, action);
	}

	/** Kleines Zahlen-Abzeichen (Akzent-Pille, in den Jahreszeit-Designs eine Laterne); Rückgabe: Breite. */
	public static int badge(Canvas c, int right, int y, int count) {
		if (count <= 0) return 0;
		Theme t = Theme.get();
		String s = count > 99 ? "99+" : String.valueOf(count);
		int w = Math.max(10, c.textWidth(s) + 5);
		int x = right - w;
		if (t.halloween || t.winter || t.fox) {
			Paint.glow(c, x, y, w, 10, t.faceGlow, 0.3f);
			Paint.block(c, x, y, w, 10, t.faceOnEdge);
			c.fill(x + 1, y + 1, x + w - 1, y + 9, t.faceOn);
		} else {
			Paint.roundRect(c, x, y, w, 10, 5, t.accent);
		}
		c.text(s, x + (w - c.textWidth(s)) / 2 + 1, y + 1, t.faceTextOn, false);
		return w;
	}
}
