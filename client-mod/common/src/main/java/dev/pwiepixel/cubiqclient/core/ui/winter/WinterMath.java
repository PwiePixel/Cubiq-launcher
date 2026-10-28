package dev.pwiepixel.cubiqclient.core.ui.winter;

/**
 * Rechenkern der Szene „Polarlicht“ – Zeile für Zeile wie {@code app/utils/winter/math.ts} im Launcher: Ganzzahl-Hashes
 * (Überlauf wie {@code int}), sonst nur Grundrechenarten, {@code floor} und {@code sqrt}, die IEEE 754 exakt festlegt –
 * keine Winkel- oder Exponentialfunktionen. Gleiche Größe, gleicher Seed und gleicher Takt ergeben so im Spiel und im
 * Launcher Pixel für Pixel dasselbe Bild (WinterTest prüft dieselben Prüfsummen wie {@code tests/winter-design.test.ts}).
 */
public final class WinterMath {
	/** Geordnetes 4 × 4-Raster als Schwellen 0…1. */
	private static final double[] ORDER = new double[16];

	static {
		int[] order = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
		for (int i = 0; i < 16; i++) ORDER[i] = (order[i] + 0.5) / 16;
	}

	private WinterMath() {
	}

	/** Durchmischt 32 Bit (Konstanten aus „hash prospector“, gemeinfrei). */
	private static int scramble(int v) {
		v ^= v >>> 16;
		v *= 0x7feb352d;
		v ^= v >>> 15;
		v *= 0x846ca68b;
		v ^= v >>> 16;
		return v;
	}

	/** Gleichverteilter Wert 0 ≤ h &lt; 1 für eine ganzzahlige Stelle (x, y) und einen Kanal {@code salt}. */
	public static double hash(int x, int y, int salt) {
		return (scramble(x * 0x27d4eb2d ^ y * 0x165667b1 ^ salt * 0x9e3779b9) & 0xFFFFFFFFL) / 4294967296.0;
	}

	/** Glattes Rauschen 0…1 entlang einer Achse: Zufallswerte an ganzen Stellen, dazwischen weich verbunden. */
	public static double glide(double x, int salt) {
		int i = (int) Math.floor(x);
		double f = x - i;
		double a = hash(i, 0, salt);
		return a + (hash(i + 1, 0, salt) - a) * f * f * (3 - 2 * f);
	}

	/** Sinusähnliche Welle aus zwei Parabelbögen, Periode 1: 0 → 1 (¼) → 0 (½) → −1 (¾). */
	public static double wave(double u) {
		double f = u - Math.floor(u);
		return f < 0.5 ? 16 * f * (0.5 - f) : 16 * (f - 0.5) * (f - 1);
	}

	/** Weicher Übergang 0…1 zwischen {@code a} und {@code b} (auch fallend, wenn b &lt; a). */
	public static double ease(double a, double b, double v) {
		double t = Math.min(1, Math.max(0, (v - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	/** Kaufmännisch gerundet (x,5 nach oben) – wie {@code round} im Launcher. */
	public static int round(double v) {
		return (int) Math.floor(v + 0.5);
	}

	public static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	/** Schwelle 0…1 des geordneten Rasters an einem Texel – weiche Übergänge bleiben so Pixel-Art. */
	public static double threshold(int x, int y) {
		return ORDER[((y & 3) << 2) | (x & 3)];
	}

	/** Deckende Farbe aus "#rrggbb" (nur für feste Werte). */
	public static int rgb(String value) {
		return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
	}

	/** Zwischenfarbe zweier deckender Farben, {@code t} = 0 … 1. */
	public static int between(int a, int b, double t) {
		int r = round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = round((a & 255) + ((b & 255) - (a & 255)) * t);
		return 0xFF000000 | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(bl, 0, 255);
	}

	/** {@code src} mit Deckkraft {@code alpha} (0…255) über {@code dst} legen. */
	public static int over(int dst, int src, int alpha) {
		int k = 255 - alpha;
		int r = (((src >> 16) & 255) * alpha + ((dst >> 16) & 255) * k + 127) / 255;
		int g = (((src >> 8) & 255) * alpha + ((dst >> 8) & 255) * k + 127) / 255;
		int b = ((src & 255) * alpha + (dst & 255) * k + 127) / 255;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** Licht {@code src} mit Stärke {@code alpha} (0…255) auf {@code dst} – hellt nur auf. */
	public static int light(int dst, int src, int alpha) {
		int out = 0xFF000000;
		for (int shift = 16; shift >= 0; shift -= 8) {
			int d = (dst >> shift) & 255;
			int s = (((src >> shift) & 255) * alpha + 127) / 255;
			out |= (d + s - (d * s + 127) / 255) << shift;
		}
		return out;
	}

	/** Prüfsumme (FNV-1a über alle Bytes) eines ARGB-Bildes, vorzeichenlos – Vergleich Spiel ↔ Launcher. */
	public static long checksum(int[] pixels) {
		int h = 0x811c9dc5;
		for (int v : pixels) {
			for (int shift = 0; shift < 32; shift += 8) h = (h ^ ((v >>> shift) & 255)) * 0x01000193;
		}
		return h & 0xFFFFFFFFL;
	}

	/** Zufallsfolge 0 ≤ r &lt; 1 aus einem Startwert (SplitMix-Schritt, danach durchmischt) – wie {@code sequence}. */
	public static final class Sequence {
		private int s;

		public Sequence(int seed) {
			s = seed;
		}

		public double next() {
			s += 0x9e3779b9;
			int z = (s ^ (s >>> 16)) * 0x21f0aaad;
			z = (z ^ (z >>> 15)) * 0x735a2d97;
			return ((z ^ (z >>> 15)) & 0xFFFFFFFFL) / 4294967296.0;
		}
	}
}
