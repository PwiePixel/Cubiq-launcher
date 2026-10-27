package dev.pwiepixel.cubiqclient.core.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Das Cubiq-Zeichen (wie {@code public/logo.png} des Launchers), im Code gerastert: ein sechseckiger Rahmen aus drei
 * Armen in der Akzentfarbe – Lücken oben links, oben rechts und unten, an jeder Lücke eine kurze Spur aus Pixel-Rauten
 * – und in der Mitte ein Würfel (linke Seite Nachtblau, rechte in der Akzentfarbe, oben dazwischen). Keine Textur,
 * damit es in jeder Minecraft-Version gleich aussieht; die Raster-Zeilen werden je Größe einmal berechnet.
 */
public final class CubiqMark {
	/** Nachtblau aus dem Logo (Hintergrund des Würfels). */
	public static final int NAVY = 0xFF0A0A30;

	private static final int NONE = 0;
	private static final int FRAME = 1;
	private static final int TOP = 2;
	private static final int LEFT = 3;
	private static final int RIGHT = 4;
	/** Rauten der Spur: 5, 6, 7 = nach außen immer blasser. */
	private static final int TRAIL = 5;

	/** Umkreis des Rahmens, Innenkante des Rahmens und Würfel – in Einheiten der halben Zeichenbreite. */
	private static final double R = 0.80;
	private static final double FRAME_IN = 0.57;
	private static final double CUBE = 0.40;
	private static final double SQRT3_2 = Math.sqrt(3) / 2;

	/** Zeilen je Rastergröße: je Zeile {Spalte von, Spalte bis, Art …}. */
	private static final Map<Integer, int[][]> CACHE = new HashMap<Integer, int[][]>();

	private CubiqMark() {
	}

	/**
	 * Zeichnet das Zeichen mit der linken oberen Ecke (x, y) auf {@code cells}×{@code cells} Rasterpunkten zu je {@code px}
	 * GUI-Pixeln, in den Farben des Themas; {@code alpha} blendet es ein.
	 */
	public static void draw(Canvas c, int x, int y, int cells, int px, float alpha) {
		Theme t = Theme.get();
		draw(c, x, y, cells, px, t.accent, alpha);
	}

	/** Wie {@link #draw(Canvas, int, int, int, int, float)} mit eigener Akzentfarbe. */
	public static void draw(Canvas c, int x, int y, int cells, int px, int accent, float alpha) {
		if (cells < 4 || px <= 0 || alpha <= 0.004f) return;
		int[] colors = {0, accent, top(accent), left(accent), right(accent), accent, accent, accent};
		int[] trailAlpha = {0, 255, 255, 255, 255, 230, 160, 95};
		int[][] rows = rows(cells);
		for (int row = 0; row < rows.length; row++) {
			int[] runs = rows[row];
			for (int i = 0; i + 2 < runs.length; i += 3) {
				int kind = runs[i + 2];
				int argb = ColorMath.withAlpha(colors[kind], Math.round(trailAlpha[kind] * ColorMath.clamp01(alpha)));
				c.fill(x + runs[i] * px, y + row * px, x + runs[i + 1] * px, y + (row + 1) * px, argb);
			}
		}
	}

	/**
	 * Umriss des Zeichens in einer Farbe ({@code argb} mit Deckkraft), Rauten der Spur so blass wie in {@link #draw}. Das
	 * Design „Polarlicht“ legt ihn leicht nach oben versetzt hinter das Zeichen – Schnee auf allen oberen Kanten.
	 */
	public static void silhouette(Canvas c, int x, int y, int cells, int px, int argb) {
		if (cells < 4 || px <= 0 || (argb >>> 24) == 0) return;
		int[] trailAlpha = {0, 255, 255, 255, 255, 230, 160, 95};
		int[][] rows = rows(cells);
		for (int row = 0; row < rows.length; row++) {
			int[] runs = rows[row];
			for (int i = 0; i + 2 < runs.length; i += 3) {
				int a = Math.round((argb >>> 24) * trailAlpha[runs[i + 2]] / 255f);
				c.fill(x + runs[i] * px, y + row * px, x + runs[i + 1] * px, y + (row + 1) * px, ColorMath.withAlpha(argb, a));
			}
		}
	}

	/**
	 * Zeichen und Schriftzug „CUBIQ“ nebeneinander (Kopfzeilen, Ladebildschirm): das Zeichen etwas höher als der
	 * Schriftzug, beide in der Akzentfarbe. {@code scale} = GUI-Pixel je Punkt der Pixelschrift, (x, y) = linke obere
	 * Ecke des Schriftzugs (das Zeichen ragt etwas darüber hinaus). Liefert die Breite.
	 */
	public static int lockup(Canvas c, int x, int y, int scale, float alpha) {
		Theme t = Theme.get();
		int px = Math.max(1, scale / 3);
		int cells = markSize(scale) / px;
		draw(c, x, y + (PixelFont.HEIGHT * scale - cells * px) / 2, cells, px, alpha);
		int tx = x + textOffset(scale);
		int color = ColorMath.fade(t.accent, alpha);
		for (int[] r : PixelFont.rects("CUBIQ")) c.fill(tx + r[0] * scale, y + r[1] * scale, tx + r[2] * scale, y + r[3] * scale, color);
		return textOffset(scale) + PixelFont.width("CUBIQ") * scale;
	}

	/** Kantenlänge des Zeichens neben einem Schriftzug mit {@code scale} (etwas mehr als die Schrifthöhe). */
	public static int markSize(int scale) {
		return PixelFont.HEIGHT * scale + scale * 2;
	}

	/** Wo der Schriftzug in {@link #lockup} beginnt (Abstand zu x). */
	public static int textOffset(int scale) {
		return markSize(scale) + scale * 2;
	}

	/** Obere Würfelseite: Akzent ins Nachtblau gezogen. */
	static int top(int accent) {
		return ColorMath.lerp(accent, NAVY, 0.45f);
	}

	/** Linke Würfelseite: Nachtblau mit einem Hauch Akzent. */
	static int left(int accent) {
		return ColorMath.lerp(NAVY, accent, 0.12f);
	}

	/** Rechte Würfelseite: die Akzentfarbe, etwas gedämpft. */
	static int right(int accent) {
		return ColorMath.lerp(accent, NAVY, 0.12f);
	}

	/** Gerasterte Zeilen (Lauflängen) für {@code cells}×{@code cells}; Ergebnis wird zwischengespeichert. */
	static int[][] rows(int cells) {
		synchronized (CACHE) {
			int[][] cached = CACHE.get(cells);
			if (cached != null) return cached;
			int[][] rows = new int[cells][];
			int[] line = new int[cells];
			for (int row = 0; row < cells; row++) {
				for (int col = 0; col < cells; col++) {
					double u = (col + 0.5) / cells * 2 - 1;
					double v = (row + 0.5) / cells * 2 - 1;
					line[col] = kind(u, v, cells);
				}
				rows[row] = runs(line);
			}
			if (CACHE.size() > 64) CACHE.clear();
			CACHE.put(cells, rows);
			return rows;
		}
	}

	private static int[] runs(int[] line) {
		int[] out = new int[line.length * 3];
		int n = 0;
		int col = 0;
		while (col < line.length) {
			int kind = line[col];
			int end = col + 1;
			while (end < line.length && line[end] == kind) end++;
			if (kind != NONE) {
				out[n++] = col;
				out[n++] = end;
				out[n++] = kind;
			}
			col = end;
		}
		int[] trimmed = new int[n];
		System.arraycopy(out, 0, trimmed, 0, n);
		return trimmed;
	}

	/** Was liegt am Punkt (u, v)? Mitte = (0, 0), Rand = ±1, y nach unten. */
	static int kind(double u, double v, int cells) {
		double d = hexDistance(u, v);
		if (d <= CUBE) {
			// Würfel von schräg oben: oben die Raute über den Linien zu den oberen Ecken, darunter links und rechts.
			if (-v * R > Math.abs(u) * R / Math.sqrt(3)) return TOP;
			return u < 0 ? LEFT : RIGHT;
		}
		if (d >= FRAME_IN && d <= R && !inGap(u, v)) return FRAME;
		if (cells >= 40) return trail(u, v);
		return NONE;
	}

	/**
	 * Abstand zur Mitte in „Sechseck-Einheiten“ (spitze Ecke oben): 1 = auf dem Rand eines Sechsecks mit Umkreis 1. Die
	 * Normalen der Kanten zeigen nach 0°, 60° und 120°.
	 */
	static double hexDistance(double u, double v) {
		double a = Math.abs(u);
		double b = Math.abs(0.5 * u + SQRT3_2 * v);
		double c = Math.abs(-0.5 * u + SQRT3_2 * v);
		return Math.max(a, Math.max(b, c)) / SQRT3_2;
	}

	/** Richtungen der Lücken (oben links, oben rechts, unten), y nach unten. */
	private static final double[][] GAPS = {{-SQRT3_2, -0.5}, {SQRT3_2, -0.5}, {0, 1}};

	/**
	 * Liegt der Punkt in einer der drei Lücken? Die Arme enden quer zu ihrer Kante kurz vor der Innenecke – an der Ecke
	 * bleibt eine Kerbe, wie im Logo.
	 */
	private static boolean inGap(double u, double v) {
		for (double[] g : GAPS) {
			// Senkrecht zur Richtung der Ecke liegt die Linie Mitte–Ecke; zu ihr hat eine Lücke die halbe Breite.
			double along = u * g[0] + v * g[1];
			double across = Math.abs(-u * g[1] + v * g[0]);
			if (along > 0 && across < 0.075 + (along - FRAME_IN) * 0.18) return true;
		}
		return false;
	}

	/** Pixel-Rauten außen an den Lücken: zwei je Lücke, schräg nach außen, kleiner und blasser (erst ab 40 Punkten). */
	private static int trail(double u, double v) {
		for (int i = 0; i < GAPS.length; i++) {
			double[] g = GAPS[i];
			// Seitlich versetzt (im Uhrzeigersinn), wie die Spuren im Logo.
			double sx = -g[1];
			double sy = g[0];
			for (int k = 0; k < 2; k++) {
				double dist = R + 0.04 + k * 0.1;
				double side = 0.07 + k * 0.1;
				double cx = g[0] * dist + sx * side;
				double cy = g[1] * dist + sy * side;
				double s = 0.08 - k * 0.02;
				// Raute im Isometrie-Verhältnis (Breite : Höhe = 1 : 0,58).
				if (Math.abs(u - cx) / s + Math.abs(v - cy) / (s * 0.58) <= 1) return TRAIL + k;
			}
		}
		return NONE;
	}
}
