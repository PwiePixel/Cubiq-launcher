package dev.pwiepixel.cubiqclient.core.ui.winter;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.CubiqMark;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.PixelFont;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Die Details des Designs „Polarlicht“ im Spiel – dieselben wie im Launcher ({@code app/assets/css/winter.css}): der Saum
 * aus Pixel-Schnee oben auf Knöpfen, Karten und Fenstern, der Hauch Schnee auf allen oberen Kanten des Cubiq-Zeichens und
 * der eisige Fortschrittsbalken mit wanderndem Schimmer. Alles aus Rechtecken im GUI-Raster; die Flächen selbst sind wie
 * im Cubiq-Design aufgebaut, nur in den Farben von {@link Theme} (Zweig „winter“).
 */
public final class Frost {
	private Frost() {
	}

	/**
	 * Profil des Schneesaums: Tiefe je Spalte (wiederholt sich alle 24 Spalten) – genau wie {@code --winter-lip} in
	 * winter.css. Oberste Zeile hell, darunter Schnee, darunter ein Schatten.
	 */
	public static final int[] LIP = {2, 2, 2, 2, 3, 3, 2, 2, 2, 2, 2, 3, 4, 5, 3, 2, 2, 2, 2, 2, 3, 2, 2, 2};
	/** Farben des Saums wie im Launcher: Kante, Schnee, Schatten – dunkel bzw. am hellen Morgen. */
	private static final int TOP = 0xFFFFFFFF;
	private static final int SNOW = 0xFFEEF6FF;
	private static final int SHADE = 0xFF9DB9D9;
	private static final int SNOW_LIGHT = 0xFFE6F1FB;
	private static final int SHADE_LIGHT = 0xFFB3C9E1;
	/** Eis für Schimmer und Schein ({@code --winter-ice}). */
	public static final int ICE = 0xFF9FE7FF;

	// --- Schneesaum ---

	/**
	 * Schneesaum entlang der Oberkante {@code y} von {@code x} bis {@code x + w} (ohne die abgerundeten Ecken – der Aufrufer
	 * gibt die gerade Strecke an); {@code alpha} 0..1 blendet ihn ein.
	 */
	public static void lip(Canvas c, int x, int y, int w, float alpha) {
		float a = ColorMath.clamp01(alpha);
		if (w <= 0 || a < 0.02f) return;
		boolean day = Theme.get().light;
		int snow = ColorMath.fade(day ? SNOW_LIGHT : SNOW, a);
		int shade = ColorMath.withAlpha(day ? SHADE_LIGHT : SHADE, Math.round(200 * a));
		int col = 0;
		while (col < w) {
			int depth = LIP[col % LIP.length];
			int end = col + 1;
			while (end < w && LIP[end % LIP.length] == depth) end++;
			if (depth > 1) c.fill(x + col, y + 1, x + end, y + depth, snow);
			c.fill(x + col, y + depth, x + end, y + depth + 1, shade);
			col = end;
		}
		c.fill(x, y, x + w, y + 1, ColorMath.fade(TOP, a));
	}

	/** Schneesaum auf einer abgerundeten Fläche (Knopf, Karte): nur auf der geraden Oberkante. */
	public static void lipOn(Canvas c, int x, int y, int w, int h, float alpha) {
		if (h < 8 || w < 12) return;
		int r = Paint.radius(w, h);
		lip(c, x + Math.max(1, r), y + 1, w - 2 * Math.max(1, r), alpha);
	}

	// --- Cubiq-Zeichen mit Schnee ---

	/** Das Cubiq-Zeichen mit Schnee auf allen oberen Kanten ({@code cells}×{@code cells} Punkte zu {@code px} Pixeln). */
	public static void mark(Canvas c, int x, int y, int cells, int px, float alpha) {
		Theme t = Theme.get();
		// Farbe wie --winter-dust in winter.css (dunkel bzw. am hellen Morgen).
		int dust = t.light ? 0xE678A0CD : 0xCCF4FAFF;
		CubiqMark.silhouette(c, x, y - px, cells, px, ColorMath.fade(dust, alpha));
		CubiqMark.draw(c, x, y, cells, px, alpha);
	}

	/** Wie {@link CubiqMark#lockup}: Zeichen (mit Schnee) und „CUBIQ“ in der Akzentfarbe; liefert die Breite. */
	public static int lockup(Canvas c, int x, int y, int scale, float alpha) {
		Theme t = Theme.get();
		int px = Math.max(1, scale / 3);
		int cells = CubiqMark.markSize(scale) / px;
		mark(c, x, y + (PixelFont.HEIGHT * scale - cells * px) / 2, cells, px, alpha);
		int tx = x + CubiqMark.textOffset(scale);
		int color = ColorMath.fade(t.accent, alpha);
		for (int[] r : PixelFont.rects("CUBIQ")) c.fill(tx + r[0] * scale, y + r[1] * scale, tx + r[2] * scale, y + r[3] * scale, color);
		return CubiqMark.textOffset(scale) + PixelFont.width("CUBIQ") * scale;
	}

	// --- Fortschritt ---

	/**
	 * Eisiger Fortschrittsbalken mittig um {@code cx} (wie {@code .progress} im Launcher): schlanke Pille, gefüllt von der
	 * Akzentfarbe über ihr Helles zu Eis-Weiß, mit weichem Schein und einem hellen Schimmer, der alle 2,2 s darüber wandert.
	 * {@code progress} &lt; 0 = unbekannt: ein Lichtstreifen läuft durch. {@code animated} = false → alles steht: der
	 * Schimmer liegt fest bei drei Vierteln, unbekannter Fortschritt als ruhiger, halb heller Balken.
	 */
	public static void bar(Canvas c, int cx, int y, int maxWidth, float progress, float time, boolean animated) {
		Theme t = Theme.get();
		int w = Math.max(24, Math.min(maxWidth, 200));
		int h = 6;
		int r = h / 2;
		int x0 = cx - w / 2;
		int a600 = t.faceOnEdge;
		int a400 = t.accentHover;
		int frost = ColorMath.lerp(ICE, 0xFFFFFFFF, 0.3f);
		Paint.roundRect(c, x0, y, w, h, r, ColorMath.lerp(t.surfaceHigh, ICE, 0.09f));
		c.fill(x0 + r, y, x0 + w - r, y + 1, 0x40000000);
		if (progress < 0) {
			int streak = ColorMath.lerp(a400, 0xFFF2FBFF, 0.35f);
			if (!animated) {
				Paint.roundRect(c, x0, y, w, h, r, ColorMath.withAlpha(streak, 0x80));
				return;
			}
			// Ein Streifen (35 % der Breite) läuft von links außerhalb nach rechts außerhalb, in der Mitte am hellsten.
			float p = (time % 1.3f) / 1.3f;
			int sw = Math.max(h * 2, Math.round(w * 0.35f));
			int sx = x0 - sw + Math.round((w + sw) * p);
			int[] colors = {a400, streak, 0xFFF2FBFF, streak, a400};
			for (int i = 0; i < 5; i++) {
				int a = Math.max(x0 + 1, sx + sw * i / 5);
				int b = Math.min(x0 + w - 1, sx + sw * (i + 1) / 5);
				if (b > a) c.fill(a, y + 1, b, y + h - 1, ColorMath.withAlpha(colors[i], i == 2 ? 0xF0 : 0xB0));
			}
			return;
		}
		int fw = Math.round(w * ColorMath.clamp01(progress));
		if (fw <= 0) return;
		Paint.glow(c, x0, y, fw, h, ICE, 0.45f);
		// Verlauf in flächigen Stufen: Akzent (600) → Akzent hell (400, bei 70 %) → Eis-Weiß.
		Paint.roundRect(c, x0, y, fw, h, r, a600);
		int steps = Math.max(1, Math.min(10, fw / 8));
		for (int i = 0; i < steps; i++) {
			int sx = Math.max(x0 + r, x0 + fw * i / steps);
			int ex = Math.min(x0 + fw - r, x0 + fw * (i + 1) / steps);
			if (ex <= sx) continue;
			float u = (i + 0.5f) / steps;
			c.fill(sx, y, ex, y + h, u < 0.7f ? ColorMath.lerp(a600, a400, u / 0.7f) : ColorMath.lerp(a400, frost, (u - 0.7f) / 0.3f));
		}
		if (fw >= 2 * r) Paint.roundRect(c, x0 + fw - 2 * r, y, 2 * r, h, r, steps > 1 ? frost : a400);
		c.fill(x0 + r, y, x0 + fw - r, y + 1, ColorMath.withAlpha(0xFFFFFFFF, 0x60));
		// Schimmer: ein heller Streifen wandert von links nach rechts über die Füllung (ruhig: fest bei drei Vierteln).
		float s = animated ? (time % 2.2f) / 2.2f : 0.64f;
		int center = x0 - Math.round(fw * 0.4f) + Math.round(fw * 1.8f * s);
		int half = Math.max(2, fw / 8);
		for (int k = 2; k >= 1; k--) {
			int a = Math.max(x0 + r, center - half * k);
			int b = Math.min(x0 + fw - r, center + half * k);
			if (b > a) c.fill(a, y + 1, b, y + h - 1, ColorMath.withAlpha(0xFFFFFFFF, k == 1 ? 0x70 : 0x30));
		}
	}
}
