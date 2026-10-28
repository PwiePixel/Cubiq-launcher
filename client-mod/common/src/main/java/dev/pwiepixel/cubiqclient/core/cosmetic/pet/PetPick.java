package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Klick-Test für Begleiter am Boden (Streicheln/Füttern per Rechtsklick): Umriss des Modells in Ruhe als Kasten im
 * Modellraum (Pixel) und ein Strahl dagegen. Der Strahl wird mit derselben Umrechnung wie {@link PetDraw#set} (Kamera
 * im Modellraum) in den Modellraum gebracht – so trifft der Test genau, was gezeichnet wird (Gieren, Größe). Reine
 * Mathematik ohne Minecraft-Klassen.
 */
public final class PetPick {
	/** Rand um den Umriss (Pixel) – kleine Begleiter lassen sich so leichter treffen. */
	static final float MARGIN_PX = 1f;
	/** Mindestmaß je Achse (Pixel), z. B. für den flachen Axolotl. */
	static final float MIN_SIZE_PX = 6f;
	/** Umriss ohne Modell: Radius und Höhe der Simulation ({@link PetSim#RADIUS}, {@link PetSim#HEIGHT}) in Pixeln. */
	static final float[] DEFAULT_BOX = { (float) (-PetSim.RADIUS * 16), 0f, (float) (-PetSim.RADIUS * 16),
			(float) (PetSim.RADIUS * 16), (float) (PetSim.HEIGHT * 16), (float) (PetSim.RADIUS * 16) };

	/** Umrisse je Modell (Modelle sind unveränderlich; schwache Schlüssel – fallen mit dem Modell weg). */
	private static final Map<CosmeticV2, float[]> BOXES = new WeakHashMap<CosmeticV2, float[]>();

	private PetPick() {
	}

	/**
	 * Umriss des Modells in Ruhe (Knochen mit ihrer Ruhe-Drehung, keine Animation) als
	 * {@code {minX, minY, minZ, maxX, maxY, maxZ}} in Pixeln, mit {@link #MARGIN_PX} Rand und mindestens
	 * {@link #MIN_SIZE_PX} je Achse; null-Modell → {@link #DEFAULT_BOX}. Gemerkt je Modell. Nur Spiel-/Render-Thread.
	 */
	public static float[] bounds(CosmeticV2 m) {
		if (m == null) return DEFAULT_BOX;
		synchronized (BOXES) {
			float[] b = BOXES.get(m);
			if (b == null) {
				b = compute(m);
				BOXES.put(m, b);
			}
			return b;
		}
	}

	static float[] compute(CosmeticV2 m) {
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 0L, false);
		final double[] e = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
				-Double.MAX_VALUE };
		r.faces(m, -1, new CosmeticV2Renderer.FaceSink() {
			@Override
			public void face(double[] corners, float[] uv, int material, double nx, double ny, double nz) {
				for (int k = 0; k < 4; k++) {
					for (int a = 0; a < 3; a++) {
						double v = corners[k * 3 + a];
						if (v < e[a]) e[a] = v;
						if (v > e[a + 3]) e[a + 3] = v;
					}
				}
			}
		});
		if (e[0] > e[3]) return DEFAULT_BOX;
		float[] box = new float[6];
		for (int a = 0; a < 3; a++) {
			double lo = e[a] - MARGIN_PX, hi = e[a + 3] + MARGIN_PX;
			if (hi - lo < MIN_SIZE_PX) {
				double mid = (lo + hi) / 2;
				lo = mid - MIN_SIZE_PX / 2;
				hi = mid + MIN_SIZE_PX / 2;
			}
			box[a] = (float) lo;
			box[a + 3] = (float) hi;
		}
		return box;
	}

	/**
	 * Strahl vom Auge ({@code ex, ey, ez}) in Richtung ({@code dx, dy, dz}, normiert) gegen den Kasten {@code box}
	 * (Pixel im Modellraum) eines Begleiters mit Ursprung ({@code px, py, pz}) am Boden, Gieren {@code yawDeg} und
	 * Größe {@code scale}. Liefert die Entfernung bis zum Eintritt in Blöcken (0, wenn das Auge im Kasten liegt) oder
	 * NaN ohne Treffer bzw. wenn der Kasten hinter dem Auge liegt.
	 */
	public static double ray(double ex, double ey, double ez, double dx, double dy, double dz, double px, double py,
			double pz, double yawDeg, double scale, float[] box) {
		if (box == null) box = DEFAULT_BOX;
		double s = scale > 0 && !Double.isInfinite(scale) ? scale : 1.0;
		double h = Math.toRadians(yawDeg);
		double cos = Math.cos(h), sin = Math.sin(h);
		double k = 16.0 / s;
		// Wie PetDraw#set: Modell x = links (cos, 0, sin), z = vorne (−sin, 0, cos), y = oben; Pixel = Blöcke · 16 / Größe
		double cx = ex - px, cy = ey - py, cz = ez - pz;
		double[] o = { k * (cx * cos + cz * sin), k * cy, k * (-cx * sin + cz * cos) };
		double[] d = { k * (dx * cos + dz * sin), k * dy, k * (-dx * sin + dz * cos) };
		double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
		for (int a = 0; a < 3; a++) {
			double lo = box[a], hi = box[a + 3];
			if (Math.abs(d[a]) < 1e-12) {
				if (o[a] < lo || o[a] > hi) return Double.NaN;
				continue;
			}
			double t1 = (lo - o[a]) / d[a], t2 = (hi - o[a]) / d[a];
			if (t1 > t2) {
				double t = t1;
				t1 = t2;
				t2 = t;
			}
			if (t1 > near) near = t1;
			if (t2 < far) far = t2;
			if (near > far) return Double.NaN;
		}
		if (far < 0) return Double.NaN;
		return Math.max(0, near);
	}

	/**
	 * Schneidet der Kasten {@code box} (Pixel im Modellraum) eines Begleiters mit Ursprung ({@code px, py, pz}), Gieren
	 * {@code yawDeg} und Größe {@code scale} die Blockzelle ({@code bx, by, bz}), also den Würfel von (bx, by, bz) bis
	 * (bx + 1, by + 1, bz + 1) in der Welt? Genau für den gedrehten Kasten (waagerecht zwei Trennachsen der Welt und zwei
	 * des Modells, dazu die Höhe); bloßes Berühren zählt nicht.
	 */
	public static boolean overlapsCell(float[] box, double px, double py, double pz, double yawDeg, double scale, int bx, int by,
			int bz) {
		if (box == null) box = DEFAULT_BOX;
		double s = scale > 0 && !Double.isInfinite(scale) ? scale : 1.0;
		double k = s / 16.0; // Pixel → Blöcke
		if (!(py + box[1] * k < by + 1 && py + box[4] * k > by)) return false;
		double h = Math.toRadians(yawDeg);
		double cos = Math.cos(h), sin = Math.sin(h);
		// Achsen der Welt: Ecken des Kastens in die Welt (wie PetDraw#set: Modell x = links (cos, sin), z = vorne (−sin, cos))
		double x0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, z0 = Double.POSITIVE_INFINITY,
				z1 = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < 4; i++) {
			double mx = (i & 1) == 0 ? box[0] : box[3], mz = (i & 2) == 0 ? box[2] : box[5];
			double wx = px + (mx * cos - mz * sin) * k, wz = pz + (mx * sin + mz * cos) * k;
			x0 = Math.min(x0, wx);
			x1 = Math.max(x1, wx);
			z0 = Math.min(z0, wz);
			z1 = Math.max(z1, wz);
		}
		if (!(x0 < bx + 1 && x1 > bx && z0 < bz + 1 && z1 > bz)) return false;
		// Achsen des Modells: Ecken der Zelle in den Modellraum (wie ray)
		double m0 = Double.POSITIVE_INFINITY, m1 = Double.NEGATIVE_INFINITY, n0 = Double.POSITIVE_INFINITY,
				n1 = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < 4; i++) {
			double cx = bx + (i & 1) - px, cz = bz + (i >> 1) - pz;
			double mx = (cx * cos + cz * sin) / k, mz = (-cx * sin + cz * cos) / k;
			m0 = Math.min(m0, mx);
			m1 = Math.max(m1, mx);
			n0 = Math.min(n0, mz);
			n1 = Math.max(n1, mz);
		}
		return m0 < box[3] && m1 > box[0] && n0 < box[5] && n1 > box[2];
	}
}
