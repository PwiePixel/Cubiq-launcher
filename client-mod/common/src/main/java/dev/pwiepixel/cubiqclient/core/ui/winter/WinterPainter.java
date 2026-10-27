package dev.pwiepixel.cubiqclient.core.ui.winter;

import java.util.List;

import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.between;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.ease;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.hash;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.light;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.over;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.round;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.threshold;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.wave;

/**
 * Malt die Szene „Polarlicht“ als ARGB in Texeln – Zeile für Zeile wie {@code app/utils/winter/painter.ts}, Pixel für Pixel
 * dasselbe Bild. Was sich nicht bewegt (Himmel mit Sternen und der fernen Wolkenbank, die Inseln), entsteht einmal je
 * Aufbau und Farbschema; je Bild kommen das Polarlicht, funkelnde Sterne, die treibende nahe Wolkenbank, schwebende
 * Inseln und der Schnee dazu. Übergänge sind mit einem festen Raster gestuft, damit alles Pixel-Art bleibt.
 */
public final class WinterPainter {
	/** Stufen der Polarlicht-Deckkraft. */
	private static final int STEPS = 6;

	/** Zustand eines Bildes. */
	public static final class Frame {
		/** Takt (80 ms) – Schnee und Funkeln. */
		public int tick;
		/** Sekunden – Polarlicht, Wolken, Schweben. */
		public double seconds;
		/** Stärke des Polarlichts 0…1. */
		public double level;
		/** Inseln schweben, Wolken treiben (aus = Standbild). */
		public boolean moving;
		public WinterSnow snow;
	}

	/** Fertig gemalte Insel (0 = durchsichtig). */
	private static final class Sprite {
		final int x;
		final int y;
		final int w;
		final int h;
		final int[] px;
		final WinterLayout.Island island;

		Sprite(int x, int y, int w, int h, int[] px, WinterLayout.Island island) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.px = px;
			this.island = island;
		}
	}

	/** Gemalte Wolkenbank: Farbe und Deckkraft je Texel (Spalten wiederholen sich nach w), ab Zeile {@code top}. */
	private static final class Bank {
		final int[] color;
		final byte[] alpha;
		final double drift;
		final int top;

		Bank(int[] color, byte[] alpha, double drift, int top) {
			this.color = color;
			this.alpha = alpha;
			this.drift = drift;
			this.top = top;
		}
	}

	public final WinterLayout L;
	public final WinterPalette P;
	/** Himmel mit Sternen und der fernen Wolkenbank. */
	final int[] backdrop;
	/** Erste Zeile der fernen Wolkenbank – Polarlicht und funkelnde Sterne bleiben darüber. */
	private final int horizon;
	private final Sprite[] sprites;
	private final Bank near;
	private final int[] alphaOf = new int[STEPS + 1];

	public WinterPainter(WinterLayout L, WinterPalette P) {
		this.L = L;
		this.P = P;
		backdrop = paintSky(L, P);
		Bank far = paintBank(L, L.cloudFar, P.cloudFarRim, P.cloudFar, 120, 8, 200);
		clouds(backdrop, far, 0);
		horizon = far.top;
		sprites = new Sprite[L.islands.size()];
		for (int i = 0; i < sprites.length; i++) sprites[i] = paintIsland(L.islands.get(i), P);
		near = paintBank(L, L.cloudNear, P.cloudNearRim, P.cloudNear, 210, 5, 255);
		for (int q = 0; q <= STEPS; q++) alphaOf[q] = round((q / (double) STEPS) * P.auroraAlpha);
	}

	/** Setzt ein ganzes Bild in {@code out} (Länge w·h, ARGB) zusammen. */
	public void compose(int[] out, Frame f) {
		System.arraycopy(backdrop, 0, out, 0, backdrop.length);
		twinkle(out, f.tick);
		if (f.level > 0.02) aurora(out, f.seconds, f.level);
		for (Sprite s : sprites) if (s.island.depth == 0) blit(out, L.w, L.h, s, 0);
		if (f.snow != null) flakes(out, f.snow, f.tick, 0);
		for (Sprite s : sprites) {
			if (s.island.depth > 0) blit(out, L.w, L.h, s, f.moving ? round(wave(f.seconds / 22 + s.island.phase)) : 0);
		}
		clouds(out, near, f.moving ? f.seconds : 0);
		if (f.snow != null) {
			flakes(out, f.snow, f.tick, 1);
			flakes(out, f.snow, f.tick, 2);
		}
	}

	/** Funkelnde Sterne: jeder vierte bis fünfte wechselt alle gut 0,6 s zwischen schwach und hell. */
	private void twinkle(int[] out, int tick) {
		int[] tones = P.stars;
		if (tones == null) return;
		List<WinterLayout.Star> stars = L.stars;
		for (int n = 0; n < stars.size(); n++) {
			WinterLayout.Star s = stars.get(n);
			if (!s.twinkle || s.y >= horizon) continue;
			double r = hash(n, (int) Math.floor((tick + n * 3) / 8.0), 0x7a1);
			if (r < 0.3) out[s.y * L.w + s.x] = tones[0];
			else if (r > 0.82) out[s.y * L.w + s.x] = tones[2];
		}
	}

	/**
	 * Polarlicht: lange Bänder, die sich in Wellen legen und dabei verdrehen. Liegt ein Band flach, ist es breit: unten ein
	 * heller Saum, darüber Cubiq-Grün, oben Türkis, darüber ein Schein, der in Eis-Cyan ausläuft. Wo es sich dreht, wird es
	 * schmal und leuchtet heller. Im Grün glitzern Pixel-Rauten wie im Cubiq-Zeichen.
	 */
	private void aurora(int[] out, double t, double level) {
		int w = L.w;
		int h = L.h;
		int sparkle = (int) Math.floor(t * 2);
		// Hintere (höhere) Bänder zuerst.
		for (int k = L.ribbons.size() - 1; k >= 0; k--) {
			WinterLayout.Ribbon r = L.ribbons.get(k);
			double fade = (r.to - r.from) * 0.18;
			int end = (int) Math.min(w, Math.ceil(r.to));
			for (int x = (int) Math.max(0, Math.ceil(r.from)); x < end; x++) {
				double taper = ease(r.from, r.from + fade, x) * ease(r.to, r.to - fade, x);
				if (taper <= 0) continue;
				double u = x / (double) w;
				double center = h * (r.base + r.amp * wave(u / r.len + t * r.speed + r.phase)
						+ 0.35 * r.amp * wave(u / (0.41 * r.len) - 1.6 * t * r.speed + 2 * r.phase));
				double turn = wave(u / r.twist + 1.8 * t * r.speed + 3 * r.phase);
				double face = 0.25 + 0.75 * turn * turn;
				double half = Math.max(1.2, h * r.thick * face);
				double strength = level * taper * r.power * (1.3 - 0.3 * face);
				double reach = h * r.thick * r.reach * (0.55 + 0.45 * face);
				int bottom = (int) Math.min(horizon - 1, Math.floor(center + half));
				for (int y = (int) Math.max(0, Math.floor(center - half - reach)); y <= bottom; y++) {
					double d = y + 0.5 - center;
					int tone;
					double glow;
					if (d > half) continue;
					if (d > half - 1.2) {
						tone = 0;
						glow = 1;
					} else if (d > -0.25 * half) {
						tone = 1;
						glow = 0.82;
					} else if (d > -half) {
						tone = 2;
						glow = 0.68;
					} else {
						double g = (-half - d) / reach;
						glow = 0.55 * (1 - g) * (1 - g);
						tone = g < 0.35 ? 2 : 3;
					}
					int q = (int) Math.floor(glow * strength * STEPS + threshold(x, y));
					if (q <= 0) continue;
					if (tone == 1 && (x + y + sparkle) % 6 == 0 && Math.floorMod(x - y, 6) == 0) q++;
					if (q > STEPS) q = STEPS;
					int i = y * w + x;
					out[i] = P.auroraGlows ? light(out[i], P.aurora[tone], alphaOf[q]) : over(out[i], P.aurora[tone], alphaOf[q]);
				}
			}
		}
	}

	/** Wolkenbank darüberlegen; sie treibt mit {@code drift} Texeln je Sekunde nach rechts. */
	private void clouds(int[] out, Bank bank, double seconds) {
		int w = L.w;
		int shift = (int) (((long) Math.floor(seconds * bank.drift)) % w);
		for (int y = bank.top; y < L.h; y++) {
			int row = y * w;
			for (int x = 0; x < w; x++) {
				int src = x - shift;
				if (src < 0) src += w;
				int a = bank.alpha[row + src] & 255;
				if (a == 255) out[row + x] = bank.color[row + src];
				else if (a != 0) out[row + x] = over(out[row + x], bank.color[row + src], a);
			}
		}
	}

	/** Flocken einer Tiefe: fern 1 Texel blass, mittel 1 Texel hell, nah 2 × 2. */
	private void flakes(int[] out, WinterSnow snow, int tick, int depth) {
		int w = L.w;
		int h = L.h;
		int color = depth == 0 ? P.flakeFar : P.flake;
		int size = depth == 2 ? 2 : 1;
		for (int i = 0; i < snow.count; i++) {
			if (snow.depthOf(i) != depth) continue;
			int x = snow.x(i, tick);
			int y = snow.y(i, tick);
			for (int dy = 0; dy < size; dy++) {
				for (int dx = 0; dx < size; dx++) {
					if (x + dx >= 0 && x + dx < w && y + dy >= 0 && y + dy < h) out[(y + dy) * w + x + dx] = color;
				}
			}
		}
	}

	// --- Himmel ---------------------------------------------------------------------------------------------------

	/** Himmel in Stufen von oben nach unten – jede Stufe flächig, nur der Übergang zur nächsten gerastert. */
	static int[] paintSky(WinterLayout L, WinterPalette P) {
		int[] out = new int[L.w * L.h];
		int stops = P.sky.length - 1;
		for (int y = 0; y < L.h; y++) {
			double pos = (y / (double) Math.max(1, L.h - 1)) * stops;
			int i = Math.min(stops - 1, (int) Math.floor(pos));
			double mixed = Math.min(1, Math.max(0, (pos - i - 0.3) / 0.4));
			for (int x = 0; x < L.w; x++) out[y * L.w + x] = mixed > threshold(x, y) ? P.sky[i + 1] : P.sky[i];
		}
		if (P.stars != null) {
			for (WinterLayout.Star s : L.stars) out[s.y * L.w + s.x] = P.stars[s.tone];
		}
		return out;
	}

	// --- Wolkenmeer -----------------------------------------------------------------------------------------------

	/**
	 * Wolkenbank unter ihrer Oberkante: ein heller Rand (vom Polarlicht beschienen, oben leicht ausgefranst), dann ein
	 * gerasterter Übergang in die Wolke, die nach unten dichter wird.
	 */
	private static Bank paintBank(WinterLayout L, WinterLayout.CloudBank bank, int rim, int body, int alpha0, int step, int alphaMax) {
		int[] color = new int[L.w * L.h];
		byte[] alpha = new byte[L.w * L.h];
		int soft = between(rim, body, 0.5);
		int first = L.h;
		for (int x = 0; x < L.w; x++) {
			int top = bank.top[x];
			first = Math.min(first, Math.max(0, top));
			for (int y = Math.max(0, top); y < L.h; y++) {
				int d = y - top;
				double th = threshold(x, y);
				if (d == 0 && th > 0.75) continue;
				int i = y * L.w + x;
				color[i] = d == 0 ? rim : (d == 1 ? (th > 0.5 ? rim : soft) : (d < 4 && th * 3 > d - 1 ? soft : body));
				alpha[i] = (byte) Math.min(alphaMax, alpha0 + d * step);
			}
		}
		return new Bank(color, alpha, bank.drift, first);
	}

	// --- Inseln ---------------------------------------------------------------------------------------------------

	/** Farben eines Würfels: Oberseite, heller Rand, Seiten, Schneesaum, Tupfen, untere Kante. */
	private static final class Faces {
		final int top;
		final int rim;
		final int left;
		final int right;
		final int lipLeft;
		final int lipRight;
		final int speck;
		final int edgeLeft;
		final int edgeRight;

		Faces(int top, int rim, int left, int right, int lipLeft, int lipRight, int speck, int edgeLeft, int edgeRight) {
			this.top = top;
			this.rim = rim;
			this.left = left;
			this.right = right;
			this.lipLeft = lipLeft;
			this.lipRight = lipRight;
			this.speck = speck;
			this.edgeLeft = edgeLeft;
			this.edgeRight = edgeRight;
		}
	}

	/**
	 * Isometrischer Würfel mit oberer Spitze (X, Y): 2a breit, Oberseite a hoch, Seiten a hoch. Gerechnet wird an
	 * Pixelmitten in doppelten bzw. vierfachen Koordinaten – keine Pixelmitte liegt je genau auf einer Kante. Gemalt werden
	 * nur freie Flächen; liegt die Oberseite frei, hängt Schnee mit ein paar Tropfen über die Seiten. {@code rims}: Bit 1 =
	 * hintere linke, Bit 2 = hintere rechte Kante frei.
	 */
	private static void cube(int[] px, int sw, int sh, int X, int Y, int a, boolean top, boolean left, boolean right, int rims, Faces c,
			int salt) {
		for (int yy = 0; yy < 2 * a; yy++) {
			int y = Y + yy;
			if (y < 0 || y >= sh) continue;
			int V = 4 * yy + 2;
			for (int xx = -a; xx < a; xx++) {
				int x = X + xx;
				if (x < 0 || x >= sw) continue;
				int U = 2 * xx + 1;
				int au = U < 0 ? -U : U;
				if (V < au) continue;
				int color;
				if (V < 4 * a - au) {
					if (!top) continue;
					color = V - au < 4 && (rims & (U < 0 ? 1 : 2)) != 0 ? c.rim : c.top;
				} else if (V < 8 * a - au) {
					boolean isLeft = U < 0;
					if (isLeft ? !left : !right) continue;
					int row = (V - (4 * a - au)) >> 2;
					double n = hash(x, salt, 0x11);
					int lip = top ? 1 + (n < 0.35 ? 1 : 0) + (n < 0.12 ? 1 : 0) : 0;
					if (row < lip) color = isLeft ? c.lipLeft : c.lipRight;
					else if (row >= a - 1) color = isLeft ? c.edgeLeft : c.edgeRight;
					else if (hash(x, y, salt) < 0.06) color = c.speck;
					else color = isLeft ? c.left : c.right;
				} else {
					continue;
				}
				px[y * sw + x] = color;
			}
		}
	}

	private static Faces faces(WinterPalette P, int left, int right, double deep, double haze) {
		return new Faces(tint(P.snowTop, P, haze), tint(P.snowLit, P, haze), tint(between(left, P.rockDeep, deep), P, haze),
				tint(between(right, P.rockDeep, deep), P, haze), tint(P.snowLeft, P, haze), tint(P.snowRight, P, haze),
				tint(P.rockDeep, P, haze), tint(between(left, P.rockDeep, 0.5), P, haze), tint(between(right, P.rockDeep, 0.5), P, haze));
	}

	private static int tint(int c, WinterPalette P, double haze) {
		return haze > 0 ? between(c, P.haze, haze) : c;
	}

	private static boolean has(WinterLayout.Island isl, int i, int j, int k) {
		for (WinterLayout.Voxel v : isl.voxels) if (v.i == i && v.j == j && v.k == k) return true;
		return false;
	}

	private static Sprite paintIsland(WinterLayout.Island isl, WinterPalette P) {
		double haze = isl.depth == 0 ? 0.45 : (isl.depth == 1 ? 0.12 : 0);
		// Tiefere Lagen dunkler – die Insel bekommt Volumen.
		Faces[] layers = new Faces[4];
		for (int k = 0; k < 4; k++) layers[k] = faces(P, P.rockLeft, P.rockRight, Math.min(0.5, k * 0.16), haze);
		Faces base = faces(P, P.pineLeft, P.pineRight, 0, haze);
		Faces pine = new Faces(base.top, base.rim, base.left, base.right, base.lipLeft, base.lipRight, tint(P.pineRight, P, haze),
				tint(P.pineLeft, P, haze), tint(P.pineRight, P, haze));
		int sw = isl.x1 - isl.x0;
		int sh = isl.y1 - isl.y0;
		int[] px = new int[sw * sh];
		int a = isl.a;
		int s = isl.seed & 0xffff;
		for (WinterLayout.Voxel v : isl.voxels) {
			int rims = (has(isl, v.i - 1, v.j, v.k) ? 0 : 1) | (has(isl, v.i, v.j - 1, v.k) ? 0 : 2);
			cube(px, sw, sh, WinterLayout.voxelX(a, v) - isl.x0, WinterLayout.voxelY(a, v) - isl.y0, a, !has(isl, v.i, v.j, v.k - 1),
					!has(isl, v.i, v.j + 1, v.k), !has(isl, v.i + 1, v.j, v.k), rims, layers[Math.min(v.k, 3)], s + v.k * 31);
		}
		int[] tiers = WinterLayout.pineTiers(a);
		int[] tops = WinterLayout.pineTops(tiers);
		int trunk = tint(P.trunk, P, haze);
		for (WinterLayout.Voxel p : isl.pines) paintPine(px, sw, sh, isl, p, tiers, tops, trunk, pine, s);
		// Eiszapfen unter den untersten Würfeln (nicht bei fernen Inseln).
		if (isl.depth > 0) {
			for (int x = 0; x < sw; x++) {
				int bottom = -1;
				for (int y = sh - 1; y >= 0 && bottom < 0; y--) if (px[y * sw + x] != 0) bottom = y;
				if (bottom < 0 || hash(x, s, 0x1c) >= 0.16) continue;
				int len = 2 + (int) Math.floor(hash(x, s, 0x1d) * 3);
				for (int k = 1; k <= len && bottom + k < sh; k++) px[(bottom + k) * sw + x] = tint(k == len ? P.iceTip : P.ice, P, haze);
			}
		}
		return new Sprite(isl.ox + isl.x0, isl.oy + isl.y0, sw, sh, px, isl);
	}

	/** Tanne aus Würfeln: kurzer Stamm, darüber kleiner werdende Würfel-Stufen, jede mit Schnee obenauf. */
	private static void paintPine(int[] px, int sw, int sh, WinterLayout.Island isl, WinterLayout.Voxel p, int[] tiers, int[] tops, int trunk,
			Faces c, int s) {
		int a = isl.a;
		int bx = WinterLayout.voxelX(a, p) - isl.x0;
		int by = WinterLayout.voxelY(a, p) + a / 2 - isl.y0;
		for (int y = by - WinterLayout.pineTrunk(tiers); y < by; y++) {
			for (int x = bx - 1; x <= bx; x++) if (x >= 0 && x < sw && y >= 0 && y < sh) px[y * sw + x] = trunk;
		}
		for (int n = 0; n < tiers.length; n++) cube(px, sw, sh, bx, by + tops[n], tiers[n], true, true, true, 3, c, s + 97 + n);
	}

	private static void blit(int[] out, int w, int h, Sprite s, int dy) {
		for (int y = 0; y < s.h; y++) {
			int ty = s.y + y + dy;
			if (ty < 0 || ty >= h) continue;
			for (int x = 0; x < s.w; x++) {
				int tx = s.x + x;
				if (tx < 0 || tx >= w) continue;
				int v = s.px[y * s.w + x];
				if (v != 0) out[ty * w + tx] = v;
			}
		}
	}
}
