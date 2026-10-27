package dev.pwiepixel.cubiqclient.core.ui.winter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.clamp;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.round;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.wave;

/**
 * Aufbau der Szene „Polarlicht“ (nur Daten, testbar) – Schritt für Schritt wie {@code app/utils/winter/layout.ts}: Sterne,
 * zwei bis drei Polarlicht-Bänder, schwebende Inseln aus isometrischen Würfeln (dem Würfel aus dem Cubiq-Zeichen) mit
 * Schnee obenauf und Tannen aus kleinen Würfeln, darunter ein Wolkenmeer aus zwei Bänken. Maße in Texeln; alle
 * Zufallszahlen kommen in derselben Reihenfolge aus einer Folge – gleiche Größe und gleicher Seed ergeben im Spiel und im
 * Launcher dieselbe Szene.
 */
public final class WinterLayout {
	/** Seed der Szene (Launcher-Hintergrund, Vorschau und Spiel). */
	public static final int SEED = 0x2c1b;

	public static final class Star {
		public final int x;
		public final int y;
		/** 0 = schwach, 1 = mittel, 2 = hell. */
		public final int tone;
		public final boolean twinkle;

		Star(int x, int y, int tone, boolean twinkle) {
			this.x = x;
			this.y = y;
			this.tone = tone;
			this.twinkle = twinkle;
		}
	}

	/**
	 * Ein Polarlicht-Band: ein langes Tuch, das sich in Wellen legt und dabei verdreht. Lagen und Längen als Anteile der
	 * Szene, Geschwindigkeiten in Wellen je Sekunde.
	 */
	public static final class Ribbon {
		/** Anfang und Ende in Texeln (die Enden laufen weich aus). */
		public final double from;
		public final double to;
		/** Mittellinie als Anteil der Höhe. */
		public final double base;
		public final double amp;
		/** Wellenlänge als Anteil der Breite. */
		public final double len;
		public final double speed;
		/** Länge einer Drehung als Anteil der Breite. */
		public final double twist;
		/** Halbe Dicke (Anteil der Höhe), wo das Band flach liegt. */
		public final double thick;
		/** Schein über dem Band, in halben Dicken. */
		public final double reach;
		public final double power;
		public final double phase;

		Ribbon(double from, double to, double base, double amp, double len, double speed, double twist, double thick, double reach,
				double power, double phase) {
			this.from = from;
			this.to = to;
			this.base = base;
			this.amp = amp;
			this.len = len;
			this.speed = speed;
			this.twist = twist;
			this.thick = thick;
			this.reach = reach;
			this.power = power;
			this.phase = phase;
		}
	}

	/** Ein Würfel einer Insel: {@code i}, {@code j} waagrecht, {@code k} = Lage (0 oben, darunter 1, 2 …). */
	public static final class Voxel {
		public final int i;
		public final int j;
		public final int k;

		Voxel(int i, int j, int k) {
			this.i = i;
			this.j = j;
			this.k = k;
		}
	}

	public static final class Island {
		/** Obere Spitze des Würfels (0, 0, 0) in Texeln. */
		public final int ox;
		public final int oy;
		/** Halbe Würfelbreite (gerade): ein Würfel ist 2a breit und 2a hoch. */
		public final int a;
		/** Zeichenreihenfolge: hinten zuerst. */
		public final List<Voxel> voxels;
		/** Tannen auf den oberen Würfeln (i, j, 0), hinten zuerst. */
		public final List<Voxel> pines;
		/** 0 = fern (blass, ruhig), 1 = Nebeninsel, 2 = Hauptinsel. */
		public final int depth;
		/** Phase des Schwebens 0…1. */
		public final double phase;
		public final int seed;
		/** Umriss relativ zu (ox, oy), mit Tannen und Platz für Eiszapfen. */
		public final int x0;
		public final int y0;
		public final int x1;
		public final int y1;

		Island(int ox, int oy, int a, List<Voxel> voxels, List<Voxel> pines, int depth, double phase, int seed, int x0, int y0, int x1,
				int y1) {
			this.ox = ox;
			this.oy = oy;
			this.a = a;
			this.voxels = voxels;
			this.pines = pines;
			this.depth = depth;
			this.phase = phase;
			this.seed = seed;
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
		}

		Island at(int x, int y) {
			return new Island(x, y, a, voxels, pines, depth, phase, seed, x0, y0, x1, y1);
		}
	}

	/** Oberkante einer Wolkenbank je Spalte; sie wiederholt sich nach {@code w} Spalten und treibt langsam nach rechts. */
	public static final class CloudBank {
		public final short[] top;
		/** Texel je Sekunde. */
		public final double drift;

		CloudBank(short[] top, double drift) {
			this.top = top;
			this.drift = drift;
		}
	}

	public final int w;
	public final int h;
	public final int seed;
	public final List<Star> stars = new ArrayList<Star>();
	public final List<Ribbon> ribbons = new ArrayList<Ribbon>();
	public final List<Island> islands = new ArrayList<Island>();
	public final CloudBank cloudFar;
	public final CloudBank cloudNear;

	private final WinterMath.Sequence rand;

	private WinterLayout(int cols, int rows, int seed, double heroX, int anchorLeft, int anchorTop, int anchorRight) {
		w = Math.max(16, cols);
		h = Math.max(16, rows);
		this.seed = seed;
		rand = new WinterMath.Sequence(seed ^ 0x51ce);

		for (int n = round((w * (double) h) / 260); n > 0; n--) {
			int x = (int) Math.floor(rand.next() * w);
			double r1 = rand.next();
			double r2 = rand.next();
			int y = (int) Math.floor(Math.min(r1, r2) * h * 0.74);
			double r = rand.next();
			boolean twinkle = rand.next() < 0.2;
			stars.add(new Star(x, y, r < 0.62 ? 0 : (r < 0.9 ? 1 : 2), twinkle));
		}

		for (int k = 0, count = h >= 100 ? 3 : 2; k < count; k++) {
			// Das vordere Band ist das längste; die hinteren sind kürzer und liegen höher.
			double span = w * (0.95 - 0.2 * k + 0.25 * rand.next());
			double middle = w * (0.42 + 0.25 * rand.next());
			double base = 0.3 - 0.085 * k + 0.03 * rand.next();
			double amp = 0.022 + 0.02 * rand.next();
			double len = 0.38 + 0.3 * rand.next();
			double speed = (0.012 + 0.01 * rand.next()) * (k % 2 == 0 ? 1 : -1);
			double twist = 0.14 + 0.12 * rand.next();
			double thick = 0.045 - 0.008 * k + 0.015 * rand.next();
			double reach = 1.8 + 0.9 * rand.next();
			double phase = rand.next();
			ribbons.add(new Ribbon(middle - span / 2, middle + span / 2, base, amp, len, speed, twist, thick, reach, 1 - 0.22 * k, phase));
		}

		// Wolkenmeer: die ferne Bank höher, mit kleineren Buckeln und still; die nahe treibt langsam.
		cloudFar = cloudBank(h * 0.82, Math.max(3, h * 0.05), 0);
		cloudNear = cloudBank(h * 0.91, Math.max(4, h * 0.08), 0.5);

		int half = clamp(round(Math.min(h / 30.0, w / 70.0)), 2, 7);

		// Hauptinsel: über dem Spielen-Knopf, sonst rechts der Mitte.
		if (h >= 60 && w >= 120) {
			int W = rand.next() < 0.5 ? 4 : 3;
			Island isl = make(2, half * 2, W, 3, rand.next() < 0.5 ? 3 : 2);
			int ox;
			int oy;
			if (anchorRight > anchorLeft) {
				ox = round((anchorLeft + anchorRight) / 2.0) - round((isl.x0 + isl.x1) / 2.0);
				oy = anchorTop - 3 - isl.y1;
			} else {
				ox = round(w * heroX) - round((isl.x0 + isl.x1) / 2.0);
				oy = round(h * 0.42) - round((isl.y0 + isl.y1) / 2.0);
			}
			ox = clamp(ox, 2 - isl.x0, w - 2 - isl.x1);
			oy = Math.max(oy, 2 - isl.y0);
			islands.add(isl.at(ox, oy));
		}
		// Nebeninseln, eher rechts.
		if (h >= 80 && w >= 100) {
			for (int n = 0, count = w >= 160 ? Math.max(1, round(w / 200.0)) : 1; n < count; n++) {
				int a = Math.max(4, half * 2 - 4);
				int W = rand.next() < 0.5 ? 3 : 2;
				int D = rand.next() < 0.5 ? 3 : 2;
				Island isl = make(1, a, W, D, rand.next() < 0.4 ? 2 : 1);
				for (int t = 0; t < 12; t++) {
					int ox = round(w * (0.32 + 0.63 * rand.next())) - round((isl.x0 + isl.x1) / 2.0);
					int oy = round(h * (0.18 + 0.37 * rand.next())) - round((isl.y0 + isl.y1) / 2.0);
					if (ox + isl.x0 < 2 || ox + isl.x1 > w - 2 || oy + isl.y0 < 2 || !free(ox + isl.x0, oy + isl.y0, ox + isl.x1, oy + isl.y1)) {
						continue;
					}
					islands.add(isl.at(ox, oy));
					break;
				}
			}
		}
		// Ferne kleine Würfel überall am Himmel.
		if (h >= 30 && w >= 60) {
			for (int n = 0, count = Math.max(1, round(w / 120.0)); n < count; n++) {
				int a = Math.max(2, half * 2 - 8);
				int W = rand.next() < 0.5 ? 2 : 1;
				Island isl = make(0, a, W, 1, a >= 4 && rand.next() < 0.5 ? 1 : 0);
				for (int t = 0; t < 12; t++) {
					int ox = round(w * (0.04 + 0.92 * rand.next())) - round((isl.x0 + isl.x1) / 2.0);
					int oy = round(h * (0.1 + 0.4 * rand.next())) - round((isl.y0 + isl.y1) / 2.0);
					if (ox + isl.x0 < 1 || ox + isl.x1 > w - 1 || oy + isl.y0 < 1 || !free(ox + isl.x0, oy + isl.y0, ox + isl.x1, oy + isl.y1)) {
						continue;
					}
					islands.add(isl.at(ox, oy));
					break;
				}
			}
		}
		// Fern hinten, Hauptinsel vorn (stabil sortiert).
		Collections.sort(islands, new Comparator<Island>() {
			@Override
			public int compare(Island p, Island q) {
				return p.depth - q.depth;
			}
		});
	}

	/** Szene ohne Anker (Spiel): Hauptinsel rechts der Mitte. */
	public static WinterLayout build(int cols, int rows, int seed) {
		return new WinterLayout(cols, rows, seed, 0.72, 0, 0, 0);
	}

	/** Szene, deren Hauptinsel über dem Bereich {@code anchorLeft…anchorRight} schwebt (oberhalb von {@code anchorTop}). */
	public static WinterLayout build(int cols, int rows, int seed, int anchorLeft, int anchorTop, int anchorRight) {
		return new WinterLayout(cols, rows, seed, 0.72, anchorLeft, anchorTop, anchorRight);
	}

	/** Szene ohne Anker mit der Hauptinsel bei {@code heroX} (Anteil der Breite). */
	public static WinterLayout build(int cols, int rows, int seed, double heroX) {
		return new WinterLayout(cols, rows, seed, heroX, 0, 0, 0);
	}

	private boolean free(int x0, int y0, int x1, int y1) {
		for (Island b : islands) {
			boolean apart = x1 + 8 <= b.ox + b.x0 || x0 - 8 >= b.ox + b.x1 || y1 + 6 <= b.oy + b.y0 || y0 - 6 >= b.oy + b.y1;
			if (!apart) return false;
		}
		return true;
	}

	private Island make(int depth, int a, int W, int D, int pines) {
		List<Voxel> voxels = new ArrayList<Voxel>();
		List<Voxel> trees = new ArrayList<Voxel>();
		shape(W, D, pines, voxels, trees);
		double phase = rand.next();
		int islandSeed = (int) Math.floor(rand.next() * 0x7fffffff);
		int[] b = outline(a, voxels, trees);
		return new Island(0, 0, a, voxels, trees, depth, phase, islandSeed, b[0], b[1], b[2], b[3]);
	}

	/**
	 * Form einer Insel: obere Lage W × D Würfel (vorn fehlt manchmal eine Ecke); unter jeder Zelle hängt eine Säule, umso
	 * länger, je näher sie der Mitte liegt – die Insel läuft nach unten zu einer gestuften Spitze aus.
	 */
	private void shape(int W, int D, int pines, List<Voxel> cells, List<Voxel> trees) {
		int corner = W > 1 && D > 1 && rand.next() < 0.45 ? (int) Math.floor(rand.next() * 3) : -1;
		for (int j = 0; j < D; j++) {
			for (int i = 0; i < W; i++) {
				int extra = rand.next() < 0.3 ? 1 : 0;
				if ((corner == 0 && i == W - 1 && j == D - 1) || (corner == 1 && i == 0 && j == D - 1) || (corner == 2 && i == W - 1 && j == 0)) {
					continue;
				}
				int inner = (W - 1 - Math.abs(2 * i - (W - 1)) + (D - 1 - Math.abs(2 * j - (D - 1)))) >> 1;
				for (int k = 0; k <= inner + extra; k++) cells.add(new Voxel(i, j, k));
			}
		}
		// Hinten zuerst (kleines i + j − k); bei Gleichstand die tiefere Lage zuerst.
		Collections.sort(cells, new Comparator<Voxel>() {
			@Override
			public int compare(Voxel p, Voxel q) {
				int c = (p.i + p.j - p.k) - (q.i + q.j - q.k);
				if (c != 0) return c;
				if (p.k != q.k) return q.k - p.k;
				return p.i - q.i;
			}
		});
		List<Voxel> tops = new ArrayList<Voxel>();
		for (Voxel c : cells) if (c.k == 0) tops.add(c);
		for (int n = 0; n < pines * 3 && trees.size() < pines && !tops.isEmpty(); n++) {
			Voxel c = tops.get((int) Math.floor(rand.next() * tops.size()));
			boolean taken = false;
			for (Voxel t : trees) if (t.i == c.i && t.j == c.j) taken = true;
			if (!taken) trees.add(new Voxel(c.i, c.j, 0));
		}
		Collections.sort(trees, new Comparator<Voxel>() {
			@Override
			public int compare(Voxel p, Voxel q) {
				int c = (p.i + p.j) - (q.i + q.j);
				return c != 0 ? c : p.i - q.i;
			}
		});
	}

	/** Umriss {x0, y0, x1, y1} relativ zur Insel. */
	private static int[] outline(int a, List<Voxel> voxels, List<Voxel> pines) {
		int x0 = 0;
		int y0 = 0;
		int x1 = 0;
		int y1 = 0;
		for (int n = 0; n < voxels.size(); n++) {
			Voxel v = voxels.get(n);
			int x = voxelX(a, v);
			int y = voxelY(a, v);
			if (n == 0 || x - a < x0) x0 = x - a;
			if (n == 0 || x + a > x1) x1 = x + a;
			if (n == 0 || y < y0) y0 = y;
			if (n == 0 || y + 2 * a > y1) y1 = y + 2 * a;
		}
		int[] tiers = pineTiers(a);
		int[] tops = pineTops(tiers);
		int peak = tops[tops.length - 1];
		for (Voxel p : pines) y0 = Math.min(y0, voxelY(a, p) + a / 2 + peak);
		return new int[]{x0, y0, x1, y1 + 4};
	}

	private CloudBank cloudBank(double base, double size, double drift) {
		List<double[]> bumps = new ArrayList<double[]>();
		for (double cx = rand.next() * size; cx < w; ) {
			double r = size * (0.75 + 0.7 * rand.next());
			bumps.add(new double[]{cx, r, r * (0.5 + 0.35 * rand.next())});
			cx += r * (0.85 + 0.6 * rand.next());
		}
		short[] top = new short[w];
		for (int x = 0; x < w; x++) {
			double y = base;
			for (double[] b : bumps) {
				// Die Bank wiederholt sich nach w Spalten: auch die Nachbarn links und rechts zählen.
				for (int copy = -1; copy <= 1; copy++) {
					double dx = (x - b[0] - copy * w) / b[1];
					if (dx <= -1 || dx >= 1) continue;
					double lift = base - b[2] * Math.sqrt(1 - dx * dx);
					if (lift < y) y = lift;
				}
			}
			top[x] = (short) round(y);
		}
		return new CloudBank(top, drift);
	}

	/** Bildschirm-Lage der oberen Spitze eines Würfels relativ zur Insel ({@code a} ist gerade). */
	public static int voxelX(int a, Voxel v) {
		return (v.i - v.j) * a;
	}

	public static int voxelY(int a, Voxel v) {
		return (v.i + v.j) * (a / 2) + v.k * a;
	}

	/** Halbe Breiten der Tannen-Stufen (unten zuerst, gerade) für Würfel der Größe {@code a}. */
	public static int[] pineTiers(int a) {
		int[] tmp = new int[3];
		int n = 0;
		for (int b = Math.max(2, 2 * round(a * 0.32)); b >= 2 && n < 3; b -= 2) tmp[n++] = b;
		return Arrays.copyOf(tmp, n);
	}

	/** Sichtbarer Stamm unter der untersten Stufe. */
	public static int pineTrunk(int[] tiers) {
		return Math.max(1, tiers[0] >> 2);
	}

	/** Obere Spitze jeder Stufe relativ zum Fußpunkt der Tanne (Mitte der Würfel-Oberseite). */
	public static int[] pineTops(int[] tiers) {
		int[] tops = new int[tiers.length];
		int y = -pineTrunk(tiers) - 2 * tiers[0];
		for (int n = 0; n < tiers.length; n++) {
			// Jede Stufe sitzt knapp unter der Mitte der Oberseite der vorigen.
			if (n > 0) y += tiers[n - 1] / 2 + 1 - 2 * tiers[n];
			tops[n] = y;
		}
		return tops;
	}

	/** Polarlicht im Ruhezustand: atmet in gut einer Minute zwischen 0,55 und 0,95. */
	public static double auroraLevel(double seconds) {
		return 0.75 + 0.2 * wave(seconds / 64);
	}
}
