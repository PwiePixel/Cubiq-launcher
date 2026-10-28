package dev.pwiepixel.cubiqclient.core.ui.winter;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.TextureRef;
import dev.pwiepixel.cubiqclient.core.ui.Textures;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Hintergrund im Design „Polarlicht“ – dieselbe Szene wie {@code app/components/WinterScene.vue} im Launcher: Nachthimmel
 * mit Polarlicht-Bändern in Cubiq-Grün, schwebende Inseln aus verschneiten Würfeln mit Würfel-Tannen, ein Wolkenmeer und
 * Schnee in drei Tiefen; im hellen Farbschema ein heller Morgen. Seed, Aufbau, Farben und Rechnung sind dieselben wie im
 * Launcher – bei gleicher Größe Pixel für Pixel dasselbe Bild.
 *
 * <p>Gemalt wird in Texeln (ein Texel = 1 GUI-Pixel, bei sehr großen Fenstern mehr) in einen eigenen Puffer, der als
 * Textur hochgeladen und pixelig hochskaliert gezeichnet wird. Wie im Launcher entsteht alle 80 ms ein neues Bild,
 * dazwischen wird nur die Textur gezeichnet; die Szene tickt nur, solange sie gezeichnet wird. Ohne Animation (eigene
 * Einstellung „Animierter Hintergrund“ oder „Animationen reduzieren“ im Launcher) zeigt sie ein ruhiges Standbild.
 */
public final class WinterScene {
	/** Seed der Szene (wie im Launcher). */
	public static final int SEED = WinterLayout.SEED;
	/** Ein Bild alle 80 ms. */
	public static final double TICK = 0.08;
	/** Standbild: ein ruhiger Moment mit kräftigem Polarlicht (wie im Launcher). */
	static final int REST_TICK = 40;
	static final double REST_SECONDS = 21;
	/** Größte Texturbreite/-höhe in Texeln (darüber werden die Texel größer). */
	private static final int MAX_COLS = 640;
	private static final int MAX_ROWS = 400;

	private static final WinterScene SHARED = new WinterScene("winter/scene");

	/** Gemeinsame Szene für Startbildschirm, Menüs und Ladebilder. */
	public static WinterScene shared() {
		return SHARED;
	}

	private final String textureName;
	private WinterLayout layout;
	private WinterPainter painter;
	private WinterSnow snow;
	private final WinterPainter.Frame frame = new WinterPainter.Frame();
	private int[] pixels;
	private TextureRef texture;
	private boolean dirty = true;
	private int builtCols = -1;
	private int builtRows = -1;
	private String paletteKey = "";
	private long lastNanos;
	private double acc;
	private double seconds;
	private int tick;
	private boolean wasAnimated = true;
	private float avgMicros;
	private int frames;

	public WinterScene(String textureName) {
		this.textureName = textureName;
	}

	/** Mittlere CPU-Zeit je Bild in Mikrosekunden (inkl. Zusammensetzen und Hochladen, gemittelt). */
	public float averageMicros() {
		return avgMicros;
	}

	/** Aufbau des letzten Bildes (Tests) oder null. */
	public WinterLayout layout() {
		return layout;
	}

	/** Uhr der Szene auf {@code s} Sekunden stellen (Selbsttest: eine bestimmte Lage des Polarlichts zeigen). */
	public void setSeconds(double s) {
		seconds = Math.max(0, s);
		dirty = true;
	}

	/** Texel-Größe in GUI-Pixeln für ein Fenster dieser Größe. */
	static int texelFor(int width, int height) {
		return Math.max(1, Math.max((width + MAX_COLS - 1) / MAX_COLS, (height + MAX_ROWS - 1) / MAX_ROWS));
	}

	/**
	 * Zeichnet die Szene über die ganze Fläche.
	 *
	 * @param animated false = ruhiges Standbild
	 */
	public void draw(Canvas c, int width, int height, boolean animated) {
		if (width <= 0 || height <= 0) return;
		long start = System.nanoTime();
		Theme t = Theme.get();
		int texel = texelFor(width, height);
		int cols = (width + texel - 1) / texel;
		int rows = (height + texel - 1) / texel;
		if (cols != builtCols || rows != builtRows) {
			build(cols, rows);
			paletteKey = "";
		}
		if (!t.themeName.equals(paletteKey)) {
			paletteKey = t.themeName;
			painter = new WinterPainter(layout, WinterPalette.of(t.themeName));
			dirty = true;
		}
		if (animated != wasAnimated) {
			wasAnimated = animated;
			dirty = true;
		}
		advance(start, animated);
		if (dirty) {
			dirty = false;
			frame.tick = animated ? tick : REST_TICK;
			frame.seconds = animated ? seconds : REST_SECONDS;
			frame.level = animated ? WinterLayout.auroraLevel(seconds) : 0.8;
			frame.moving = animated;
			frame.snow = snow;
			painter.compose(pixels, frame);
			Textures.Store store = Textures.store();
			texture = store != null && c.images() ? store.upload(textureName, cols, rows, pixels) : null;
		}
		if (texture != null) {
			c.push();
			c.scale(texel);
			c.image(texture, 0, 0, cols, rows, 0xFFFFFFFF);
			c.pop();
		} else {
			fallback(c, width, height);
		}
		float us = (System.nanoTime() - start) / 1000f;
		frames++;
		avgMicros = frames == 1 ? us : avgMicros + (us - avgMicros) * 0.05f;
	}

	/** Takt: je 80 ms ein Bild; nach einer Pause geht es ohne Sprung weiter. */
	private void advance(long now, boolean animated) {
		double dt = lastNanos == 0 ? TICK : (now - lastNanos) / 1e9;
		lastNanos = now;
		if (dt < 0 || dt > 0.5) dt = TICK;
		if (!animated) return;
		seconds += dt;
		acc += dt;
		int steps = 0;
		while (acc >= TICK && steps < 3) {
			acc -= TICK;
			steps++;
			tick++;
		}
		if (acc > TICK * 3) acc = 0;
		if (steps > 0) dirty = true;
	}

	private void build(int cols, int rows) {
		builtCols = cols;
		builtRows = rows;
		layout = WinterLayout.build(cols, rows, SEED);
		// Schnee wie im Launcher-Hintergrund (Seed der Szene, volle Dichte).
		snow = new WinterSnow(layout.w, layout.h, SEED ^ 0x5e0, 1);
		pixels = new int[layout.w * layout.h];
		dirty = true;
	}

	/** Ohne Texturen (sollte es nicht geben): Himmel in Stufen und das Wolkenmeer als Flächen. */
	private void fallback(Canvas c, int width, int height) {
		WinterPalette p = painter.P;
		int sea = height * 86 / 100;
		for (int i = 0; i < p.sky.length; i++) c.fill(0, sea * i / p.sky.length, width, sea * (i + 1) / p.sky.length, p.sky[i]);
		c.fill(0, sea, width, height, p.cloudNear);
		c.fill(0, sea, width, sea + 1, p.cloudNearRim);
	}
}
