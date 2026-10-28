package dev.pwiepixel.cubiqclient.core.ui.title;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Hintergrund des Cubiq-Startbildschirms, wie die Szene des Launchers ({@code app/components/CubiqScene.vue}): ein
 * ruhiger Verlauf in den Flächenfarben, ein weicher Schein in der Akzentfarbe hinter den Knöpfen und oben rechts, ein
 * feines isometrisches Raster (die Kreuzungen des Würfelrasters, zur Mitte hin sichtbar) und ein paar Würfel-Umrisse,
 * die langsam auf und ab schweben.
 *
 * <p>Alles aus Rechtecken im GUI-Raster. Lage und Größe der Würfel hängen nur von Fenstergröße und freien Bereichen ab
 * (gleiche Größe = gleiche Szene) und werden nur dann neu berechnet. Ist das Zeichnen zu teuer (alte Versionen zeichnen
 * jedes Rechteck einzeln), schaltet die Szene selbst auf eine sparsamere Darstellung ohne Raster und Bewegung um.</p>
 */
public final class CubeScene {
	/** Ab dieser mittleren Zeichenzeit (Mikrosekunden) wird die sparsame Darstellung genutzt. */
	private static final float BUDGET_MICROS = 2500f;
	/** Breite und Höhe einer Raute des Würfelrasters (GUI-Pixel); die Kreuzungen werden gezeichnet. */
	static final int GRID_W = 26;
	static final int GRID_H = 15;
	/** Bänder des Verlaufs. */
	private static final int BANDS = 24;

	/** Ein schwebender Würfel-Umriss. */
	static final class Cube {
		/** Mitte und Kantenlänge (GUI-Pixel). */
		final int x;
		final int y;
		final int size;
		/** Deckkraft der Linien (wie im Launcher 0,10 … 0,24). */
		final float opacity;
		/** Dauer einer Auf-und-ab-Bewegung (Sekunden) und Versatz. */
		final float period;
		final float phase;

		Cube(int x, int y, int size, float opacity, float period, float phase) {
			this.x = x;
			this.y = y;
			this.size = size;
			this.opacity = opacity;
			this.period = period;
			this.phase = phase;
		}
	}

	private final List<Cube> cubes = new ArrayList<Cube>();
	private int builtW = -1;
	private int builtH = -1;
	private int builtKey;
	private boolean simple;
	private boolean autoSimple;
	private boolean alwaysDark;
	private float avgMicros;
	private int frames;
	/** Mitte des Scheins hinter den Knöpfen. */
	private int focusX;
	private int focusY;

	/** Sparsame Darstellung erzwingen (alte Minecraft-Versionen, ausgeschaltete Animation). */
	public void setSimple(boolean simple) {
		this.simple = simple;
		builtW = -1;
	}

	public boolean isSimple() {
		return simple || autoSimple;
	}

	/** Immer die dunklen Farben, auch im hellen Thema (Vanilla-Menüs schreiben weiß). */
	public void setAlwaysDark(boolean alwaysDark) {
		this.alwaysDark = alwaysDark;
	}

	/** Mittlere Zeichenzeit der Szene in Mikrosekunden (CPU). */
	public float averageMicros() {
		return avgMicros;
	}

	/** Würfel der Szene (Tests). */
	List<Cube> cubes() {
		return cubes;
	}

	/**
	 * Zeichnet die Szene.
	 * @param time Sekunden (fortlaufend)
	 * @param reserved Rechtecke {x1, y1, x2, y2}, die frei bleiben (Schriftzug, Knöpfe, Fußzeile); das erste bestimmt
	 *                 die Lage des Scheins. Darf null sein.
	 */
	public void draw(Canvas c, int width, int height, float time, int[][] reserved) {
		long start = System.nanoTime();
		int key = Arrays.deepHashCode(reserved);
		if (width != builtW || height != builtH || key != builtKey) {
			build(width, height, reserved, isSimple());
			builtW = width;
			builtH = height;
			builtKey = key;
		}
		Theme t = Theme.get();
		boolean light = t.light && !alwaysDark;
		boolean lite = isSimple();
		int top = light ? t.background : (alwaysDark || !"oled".equals(t.themeName) ? 0xFF111613 : 0xFF050706);
		int bottom = light ? t.deep : (alwaysDark || !"oled".equals(t.themeName) ? 0xFF0C100E : 0xFF000000);
		background(c, width, height, top, bottom);
		// Schein oben rechts (wie der Verlauf der Szene im Launcher) und hinter den Knöpfen.
		if (!lite) Paint.radialGlow(c, width, 0, Math.round(width * 0.62f), Math.round(height * 0.8f), t.accent, light ? 20 : 30, width, height);
		Paint.radialGlow(c, focusX, focusY, Math.round(width * 0.36f), Math.round(height * 0.42f), t.accent, light ? 30 : (lite ? 32 : 46),
				width, height);
		int line = t.accentHover;
		if (!lite) grid(c, width, height, line, light ? 0.4f : 0.3f);
		for (int i = 0; i < cubes.size(); i++) {
			Cube cube = cubes.get(i);
			float drift = lite ? 0f : (float) (0.5 - 0.5 * Math.cos((time + cube.phase) * Math.PI * 2 / cube.period));
			int bg = ColorMath.lerp(top, bottom, cube.y / (float) Math.max(1, height));
			int color = ColorMath.lerp(bg, line, Math.min(1f, cube.opacity * (light ? 1.6f : 1.25f)));
			c.push();
			c.translate(0, -drift * Math.max(3f, height * 0.012f));
			cube(c, cube.x, cube.y, cube.size, color);
			c.pop();
		}
		// Gepufferte Rechtecke jetzt zeichnen – so zählt ihr Hochladen mit zur gemessenen Zeit.
		c.flush();
		measure(System.nanoTime() - start);
	}

	private void measure(long nanos) {
		float micros = nanos / 1000f;
		frames++;
		avgMicros = frames == 1 ? micros : avgMicros + (micros - avgMicros) * 0.05f;
		// Erst nach einer Anlaufphase entscheiden (JIT, Schrift-Cache).
		if (!autoSimple && !simple && frames > 90 && avgMicros > BUDGET_MICROS) {
			autoSimple = true;
			builtW = -1;
		}
	}

	// --- Aufbau ---

	/** Legt Schein und Würfel für eine Fenstergröße fest (deterministisch: gleiche Größe = gleiche Szene). */
	void build(int width, int height, int[][] reserved, boolean lite) {
		cubes.clear();
		int[] focus = reserved != null && reserved.length > 0 && reserved[0] != null && reserved[0].length >= 4
				&& reserved[0][2] > reserved[0][0] ? reserved[0] : null;
		focusX = focus != null ? (focus[0] + focus[2]) / 2 : width / 2;
		focusY = focus != null ? (focus[1] + focus[3]) / 2 : Math.round(height * 0.55f);
		Random rnd = new Random(0xC0B1_0000L + width * 131L + height);
		int target = lite ? 3 : 5;
		int attempts = 0;
		while (cubes.size() < target && attempts < 80) {
			attempts++;
			int size = Math.max(14, Math.min(64, Math.round(width * (0.045f + rnd.nextFloat() * 0.06f)))) & ~1;
			int x = Math.round(width * (0.06f + rnd.nextFloat() * 0.88f));
			int y = Math.round(height * (0.1f + rnd.nextFloat() * 0.72f));
			if (blocked(x, y, size, reserved) || crowded(x, y, size)) continue;
			cubes.add(new Cube(x, y, size, 0.1f + rnd.nextFloat() * 0.14f, 16f + rnd.nextFloat() * 10f, rnd.nextFloat() * 18f));
		}
	}

	/** Würde der Würfel (mit etwas Abstand) einen freizuhaltenden Bereich berühren? */
	private static boolean blocked(int x, int y, int size, int[][] reserved) {
		if (reserved == null) return false;
		int half = size / 2 + 6;
		for (int[] r : reserved) {
			if (r == null || r.length < 4 || r[2] <= r[0] || r[3] <= r[1]) continue;
			if (x + half > r[0] && x - half < r[2] && y + half > r[1] && y - half < r[3]) return true;
		}
		return false;
	}

	/** Liegt schon ein Würfel zu nah daneben? */
	private boolean crowded(int x, int y, int size) {
		for (Cube c : cubes) {
			int need = (size + c.size) / 2 + 12;
			if (Math.abs(c.x - x) < need && Math.abs(c.y - y) < need) return true;
		}
		return false;
	}

	// --- Zeichnen ---

	/** Senkrechter Verlauf in Bändern (oben heller, unten der tiefste Grund). */
	private static void background(Canvas c, int width, int height, int top, int bottom) {
		for (int i = 0; i < BANDS; i++) {
			int y1 = height * i / BANDS;
			int y2 = height * (i + 1) / BANDS;
			c.fill(0, y1, width, y2, ColorMath.lerp(top, bottom, i / (float) (BANDS - 1)));
		}
	}

	/**
	 * Isometrisches Raster als kleine Striche an den Kreuzungen der Würfelkanten – wie im Launcher zur Mitte hin
	 * sichtbar und nach außen ausgeblendet (dort ist die Fläche frei für Text).
	 */
	private void grid(Canvas c, int width, int height, int argb, float strength) {
		float mx = width * 0.7f;
		float my = height * 0.9f;
		for (int row = 0, y = GRID_H / 2; y < height; row++, y += GRID_H) {
			int shift = (row & 1) == 0 ? 0 : GRID_W / 2;
			for (int x = shift; x < width; x += GRID_W) {
				float dx = (x - focusX) / mx;
				float dy = (y - focusY) / my;
				float d = (float) Math.sqrt(dx * dx + dy * dy);
				// Maske wie im Launcher: deckend bis 10 %, bei 75 % durchsichtig.
				float m = d <= 0.1f ? 1f : (d >= 0.75f ? 0f : 1f - (d - 0.1f) / 0.65f);
				int a = Math.round(255 * strength * m);
				if (a < 6) continue;
				c.fill(x - 1, y, x + 2, y + 1, ColorMath.withAlpha(argb, a));
			}
		}
	}

	/**
	 * Umriss eines isometrischen Würfels (wie {@code cubePath} im Launcher): Sechseck mit Spitze oben, dazu die Kanten
	 * von der Mitte zur oberen linken und rechten Ecke und nach unten. Deckende Farbe – so werden Ecken nicht heller.
	 */
	static void cube(Canvas c, int cx, int cy, int size, int argb) {
		float r = size * 0.44f;
		int stroke = size >= 40 ? 2 : 1;
		int dx = Math.round(r * 0.866f);
		int dy = Math.round(r * 0.5f);
		int rr = Math.round(r);
		int top = cy - rr;
		int bottom = cy + rr;
		// Außen: oben → rechts oben → rechts unten → unten → links unten → links oben.
		slant(c, cx, top, cx + dx, cy - dy, stroke, argb);
		slant(c, cx - dx, cy - dy, cx, top, stroke, argb);
		c.fill(cx + dx - stroke, cy - dy, cx + dx, cy + dy, argb);
		c.fill(cx - dx, cy - dy, cx - dx + stroke, cy + dy, argb);
		slant(c, cx, bottom - stroke, cx + dx, cy + dy - stroke, stroke, argb);
		slant(c, cx - dx, cy + dy - stroke, cx, bottom - stroke, stroke, argb);
		// Innen: Mitte → links oben, Mitte → rechts oben, Mitte → unten.
		slant(c, cx - dx, cy - dy, cx, cy, stroke, argb);
		slant(c, cx, cy, cx + dx, cy - dy, stroke, argb);
		c.fill(cx - stroke / 2, cy, cx - stroke / 2 + stroke, bottom, argb);
	}

	/**
	 * Schräge Kante (flacher als 45°) von (x1, y1) nach (x2, y2) mit x1 &lt; x2: je Pixelzeile ein Stück, {@code
	 * stroke} Pixel dick.
	 */
	static void slant(Canvas c, int x1, int y1, int x2, int y2, int stroke, int argb) {
		int rows = Math.abs(y2 - y1);
		if (rows == 0) {
			c.fill(x1, y1, x2, y1 + stroke, argb);
			return;
		}
		int dir = y2 > y1 ? 1 : -1;
		for (int i = 0; i < rows; i++) {
			int a = x1 + Math.round((x2 - x1) * (i / (float) rows));
			int b = x1 + Math.round((x2 - x1) * ((i + 1) / (float) rows));
			int y = y1 + i * dir + (dir < 0 ? -1 : 0);
			c.fill(a, y, Math.max(a + 1, b), y + stroke, argb);
		}
	}
}
