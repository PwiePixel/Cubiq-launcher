package dev.pwiepixel.cubiqclient.core.ui.winter;

import java.util.List;

import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.between;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.ease;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.glide;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.hash;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.over;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.round;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.threshold;
import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.wave;

/**
 * Malt die Szene „Polarlicht“ als ARGB in Texeln – Zeile für Zeile wie {@code app/utils/winter/painter.ts}, Pixel für Pixel
 * dasselbe Bild. Was sich nicht bewegt (Himmel mit Sternen und der fernen Wolkenbank, die Inseln), entsteht einmal je
 * Aufbau und Farbschema; je Bild kommen das Polarlicht, funkelnde Sterne, die treibende nahe Wolkenbank, schwebende
 * Inseln und der Schnee dazu. Übergänge sind mit einem festen Raster gestuft, damit alles Pixel-Art bleibt. Was je Texel
 * oft gebraucht wird (Farbe und Leuchtkraft je Höhe des Vorhangs, Licht je Stufe), ist beim Aufbau vorgerechnet.
 */
public final class WinterPainter {
	/** Helligkeitsstufen des Polarlichts. */
	private static final int STEPS = 10;
	/** Höhenstufen des Vorhangs von der unteren Kante (0) bis ganz oben – je Stufe eine Farbe und Leuchtkraft. */
	private static final int BANDS = 20;

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
	/** Farbe des Vorhangs je Höhenstufe. */
	private final int[] tone = new int[BANDS];
	/** Leuchtkraft je Höhenstufe, schon mal STEPS. */
	private final double[] glow = new double[BANDS];
	/** Licht je Höhen- und Helligkeitsstufe, schon mit der Deckkraft verrechnet: [Höhe][Stufe][Kanal]. */
	private final int[] lit = new int[BANDS * (STEPS + 1) * 3];

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
		int[] a = P.aurora;
		for (int b = 0; b < BANDS; b++) {
			// Mitte der Stufe: 0 an der unteren Kante, 1 ganz oben.
			double f = (b + 0.5) / BANDS;
			int c;
			double shine;
			if (b == 0) {
				// Die untere Kante ist der hellste Teil des Vorhangs – auch dort, wo ein Strahl schwach ist.
				c = a[0];
				shine = 1.4;
			} else if (b == 1) {
				c = a[1];
				shine = 1.25;
			} else {
				// Nach oben verglimmt der Vorhang (g·√g) und wird kühler: Smaragd → Türkis → Eisblau → Violett.
				double g = 1 - f;
				shine = 0.9 * g * Math.sqrt(g);
				c = f < 0.3 ? a[2] : (f < 0.55 ? between(a[2], a[3], (f - 0.3) / 0.25) : (f < 0.8 ? between(a[3], a[4], (f - 0.55) / 0.25)
						: between(a[4], a[5], (f - 0.8) / 0.2)));
			}
			tone[b] = c;
			glow[b] = shine * STEPS;
			for (int q = 0; q <= STEPS; q++) {
				for (int ch = 0; ch < 3; ch++) lit[(b * (STEPS + 1) + q) * 3 + ch] = (((c >>> (16 - 8 * ch)) & 255) * alphaOf[q] + 127) / 255;
			}
		}
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
	 * Polarlicht als Vorhang: lange Bänder, die sich in Wellen legen und dabei verdrehen. Aus jedem Band hängt nach oben ein
	 * Vorhang aus Lichtstrahlen – unten eine helle Kante, darüber verglimmt er über Smaragd, Türkis und Eisblau bis in ein
	 * zartes Violett. Jede Spalte ist ein eigener Strahl: zwei langsam wandernde Rauschen machen ihn heller oder dunkler und
	 * unterschiedlich lang; wo sich das Band dreht, wird der Vorhang kürzer und etwas heller.
	 */
	private void aurora(int[] out, double t, double level) {
		int w = L.w;
		int h = L.h;
		boolean glows = P.auroraGlows;
		// Hintere (höhere) Bänder zuerst.
		for (int k = L.ribbons.size() - 1; k >= 0; k--) {
			WinterLayout.Ribbon r = L.ribbons.get(k);
			double fade = (r.to - r.from) * 0.18;
			// Feine Strahlen wandern je Band in die andere Richtung.
			double drift = k % 2 == 0 ? 0.7 : -0.7;
			int end = (int) Math.min(w, Math.ceil(r.to));
			for (int x = (int) Math.max(0, Math.ceil(r.from)); x < end; x++) {
				double taper = ease(r.from, r.from + fade, x) * ease(r.to, r.to - fade, x);
				if (taper <= 0) continue;
				double u = x / (double) w;
				double center = h * (r.base + r.amp * wave(u / r.len + t * r.speed + r.phase)
						+ 0.35 * r.amp * wave(u / (0.41 * r.len) - 1.6 * t * r.speed + 2 * r.phase));
				double turn = wave(u / r.twist + 1.8 * t * r.speed + 3 * r.phase);
				double face = 0.35 + 0.65 * turn * turn;
				double coarse = glide(x / 6.5 - t * 0.25, 0x7b + k);
				double rays = 0.22 + 0.78 * (0.55 * glide(x / 1.6 + t * drift, 0x5a + k) + 0.45 * coarse);
				double bottom = center + h * r.thick * 0.9;
				double tall = h * r.thick * (3.4 + 4.2 * coarse) * (0.6 + 0.4 * face);
				double strength = 1.3 * level * taper * r.power * rays * (1.15 - 0.15 * face);
				// Höhenstufe je Texel aus dem Abstand d zur unteren Kante: der Saum ist der erste Texel, die Kante der zweite,
				// darüber verteilt sich der Vorhang auf die übrigen Stufen. Alle Werte sind hier positiv – (int) rundet wie floor
				// ab (im Launcher „| 0“), die Rasterschwellen der Spalte liegen schon bereit: das hält die Schleife schnell.
				double per = BANDS / tall;
				int yb = (int) Math.min(horizon - 1, Math.floor(bottom));
				double t0 = threshold(x, 0);
				double t1 = threshold(x, 1);
				double t2 = threshold(x, 2);
				double t3 = threshold(x, 3);
				for (int y = (int) Math.max(0, Math.floor(bottom - tall)); y <= yb; y++) {
					double d = bottom - (y + 0.5);
					if (d < 0) continue;
					int b = d < 1 ? 0 : (d < 2 ? 1 : (int) (d * per));
					if (b < 2 && d >= 2) b = 2;
					if (b >= BANDS) continue;
					int m = y & 3;
					int q = (int) (glow[b] * strength + (m == 0 ? t0 : (m == 1 ? t1 : (m == 2 ? t2 : t3))));
					if (q <= 0) continue;
					if (q > STEPS) q = STEPS;
					int i = y * w + x;
					if (glows) {
						// Wie light(), nur mit vorgerechneten Stufen (gleiche Zahlen, schneller).
						int v = out[i];
						int o = (b * (STEPS + 1) + q) * 3;
						int cr = (v >> 16) & 255;
						int cg = (v >> 8) & 255;
						int cb = v & 255;
						int sr = lit[o];
						int sg = lit[o + 1];
						int sb = lit[o + 2];
						out[i] = 0xFF000000 | ((cr + sr - (cr * sr + 127) / 255) << 16) | ((cg + sg - (cg * sg + 127) / 255) << 8) | (cb + sb - (cb * sb + 127) / 255);
					} else {
						out[i] = over(out[i], tone[b], alphaOf[q]);
					}
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
