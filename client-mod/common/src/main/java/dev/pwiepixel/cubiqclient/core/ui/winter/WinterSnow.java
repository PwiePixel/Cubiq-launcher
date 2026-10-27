package dev.pwiepixel.cubiqclient.core.ui.winter;

import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.hash;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.wave;

/**
 * Schnee der Szene „Polarlicht“ (wie {@code app/utils/winter/snow.ts}): quadratische Pixel-Flocken in drei Tiefen – fern
 * (1 Texel, blass), mittel (1 Texel, hell) und nah (2 × 2). Ohne Zustand: wo eine Flocke steht, folgt allein aus ihrer
 * Nummer, dem Seed und dem Takt (80 ms) – im Spiel und im Launcher fällt zum selben Takt derselbe Schnee.
 */
public final class WinterSnow {
	/** Fallen, Wind und Pendeln je Tiefe (Texel je Takt bzw. Texel). */
	private static final double[] FALL = {0.16, 0.3, 0.5};
	private static final double[] WIND = {0.03, 0.05, 0.08};
	private static final double[] SWAY = {0.6, 1.1, 1.7};

	public final int count;
	public final int w;
	public final int h;
	private final byte[] depth;
	private final double[] x0;
	private final double[] y0;
	private final double[] fall;
	private final double[] sway;
	private final double[] period;
	private final double[] phase;

	public WinterSnow(int w, int h, int seed, double density) {
		this.w = w;
		this.h = h;
		count = Math.max(12, Math.min(600, (int) Math.floor((w * (double) h) / 420 * density)));
		depth = new byte[count];
		x0 = new double[count];
		y0 = new double[count];
		fall = new double[count];
		sway = new double[count];
		period = new double[count];
		phase = new double[count];
		for (int i = 0; i < count; i++) {
			double r = hash(i, 1, seed);
			int d = r < 0.5 ? 0 : (r < 0.84 ? 1 : 2);
			depth[i] = (byte) d;
			x0[i] = hash(i, 2, seed) * (w + 8);
			y0[i] = hash(i, 3, seed) * (h + 8);
			fall[i] = FALL[d] * (0.85 + 0.3 * hash(i, 4, seed));
			sway[i] = SWAY[d] * (0.5 + hash(i, 5, seed));
			period[i] = 70 + 60 * hash(i, 6, seed);
			phase[i] = hash(i, 7, seed);
		}
	}

	/** Tiefe der Flocke {@code i}: 0 = fern, 1 = mittel, 2 = nah. */
	public int depthOf(int i) {
		return depth[i];
	}

	/** Linke obere Ecke der Flocke {@code i} zum Takt {@code tick} (ganze Texel; die Flocken laufen am Rand um). */
	public int x(int i, int tick) {
		int d = depth[i];
		double span = w + 8;
		double v = x0[i] + WIND[d] * tick + sway[i] * wave(phase[i] + tick / period[i]);
		return (int) Math.floor(v - Math.floor(v / span) * span) - 4;
	}

	public int y(int i, int tick) {
		double span = h + 8;
		double v = y0[i] + fall[i] * tick;
		return (int) Math.floor(v - Math.floor(v / span) * span) - 4;
	}
}
