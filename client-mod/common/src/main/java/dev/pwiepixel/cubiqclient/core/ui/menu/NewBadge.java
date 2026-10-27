package dev.pwiepixel.cubiqclient.core.ui.menu;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Kleines „NEU“-Schild für Kacheln, Einstellungszeilen und die Leiste: eine Pille in der Akzentfarbe mit dunkler Schrift
 * (wie die Abzeichen im Launcher), in Winternacht/Fuchsbau/Halloween eine sanft pulsierende Laterne.
 */
public final class NewBadge {
	public static final int HEIGHT = 10;

	private NewBadge() {
	}

	public static String label() {
		return I18n.tr("intro.newBadge");
	}

	/** Breite des Schilds. */
	public static int width(Canvas c) {
		return c.textWidth(label()) + 6;
	}

	/** Zeichnet das Schild mit der linken oberen Ecke (x, y); liefert die Breite. */
	public static int draw(Canvas c, int x, int y) {
		Theme t = Theme.get();
		int w = width(c);
		if (t.halloween || t.winter || t.fox) {
			// Sanftes Pulsieren, damit neue Einträge ins Auge fallen (ohne zu blinken).
			float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 380.0);
			Paint.glow(c, x, y, w, HEIGHT, t.faceGlow, 0.35f * pulse);
			Paint.block(c, x, y, w, HEIGHT, t.faceOnEdge);
			c.fill(x + 1, y + 1, x + w - 1, y + HEIGHT - 1, ColorMath.lerp(t.faceOn, t.faceHot, 0.25f * pulse));
		} else {
			Paint.roundRect(c, x, y, w, HEIGHT, HEIGHT / 2, t.accent);
		}
		c.text(label(), x + 3, y + 1, t.faceTextOn, false);
		return w;
	}

	/** Kleiner Punkt (Leiste: „hier gibt es Neues“). */
	public static void dot(Canvas c, int x, int y) {
		Paint.pip(c, x, y, 5, 1f);
	}
}
