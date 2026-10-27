package dev.pwiepixel.cubiqclient.core.ui.fox;

/** Rauchwölkchen über dem Lagerfeuer: steigen auf, treiben mit dem Wind und vergehen (höchstens 24). */
public final class Smoke {
	public static final int MAX = 24;
	public final double[] x = new double[MAX];
	public final double[] y = new double[MAX];
	public final int[] life = new int[MAX];
	public final int[] max = new int[MAX];
	public int count;
	private final FoxMath.Rng rand;

	public Smoke(FoxMath.Rng rand) {
		this.rand = rand;
	}

	/** Wind je Takt: meist ein leichter Zug nach rechts, ab und zu dreht er kurz. */
	static double wind(int tick) {
		return 0.06 + 0.12 * Math.sin(tick * 0.009) + 0.05 * Math.sin(tick * 0.041 + 1);
	}

	/** {@code originX/Y} = Ursprung; {@code has} = false: keine neuen Wölkchen. */
	public void step(boolean has, double originX, double originY, int tick) {
		double wnd = wind(tick);
		int n = 0;
		for (int i = 0; i < count; i++) {
			y[i] -= 0.22;
			x[i] += 0.05 + wnd * 0.6 + (rand.next() - 0.5) * 0.15;
			life[i]--;
			if (life[i] > 0) {
				x[n] = x[i];
				y[n] = y[i];
				life[n] = life[i];
				max[n] = max[i];
				n++;
			}
		}
		count = n;
		if (has && tick % 7 == 0 && count < MAX) {
			int m = 46 + (int) Math.floor(rand.next() * 18);
			x[count] = originX + (rand.next() - 0.5);
			y[count] = originY;
			life[count] = m;
			max[count] = m;
			count++;
		}
	}
}
