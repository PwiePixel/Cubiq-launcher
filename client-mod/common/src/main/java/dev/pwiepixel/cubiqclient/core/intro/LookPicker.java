package dev.pwiepixel.cubiqclient.core.intro;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.sync.ClientSync;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.CubiqMark;
import dev.pwiepixel.cubiqclient.core.ui.Hits;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.winter.Frost;
import dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath;
import dev.pwiepixel.cubiqclient.core.ui.winter.WinterPalette;

/**
 * Aussehen im Client wählen (Einführung und Cubiq-Menü): Farbschema, Akzent und Design (Cubiq | Polarlicht | Fuchsbau). Die Wahl
 * gilt sofort, wird lokal gemerkt und – Thema, Akzent, Sprache – mit dem Cubiq-Konto synchronisiert; das Design bleibt
 * auf diesem PC (die Konto-Einstellungen kennen es nicht), der Launcher übergibt beim Start sein eigenes. Die Akzente
 * heißen wie im Launcher, damit die Wahl dort ankommt.
 */
public final class LookPicker {
	public static final String[] THEMES = {"dark", "oled", "light"};
	public static final String[] ACCENTS = {"cubiq", "amber", "emerald", "lapis", "amethyst", "ice", "orange"};
	public static final String[] DESIGNS = {Theme.CUBIQ, Theme.WINTER, Theme.FOX};
	/** Breite der Design-Wahl (drei Knöpfe). */
	public static final int DESIGNS_WIDTH = 456;

	private LookPicker() {
	}

	/**
	 * Übernimmt eine Wahl (null = unverändert). Beim Designwechsel folgt der Akzent (Cubiq-Grün, Eisblau, Orange), eine
	 * selbst gewählte Farbe bleibt.
	 */
	public static void choose(CubiqModules modules, String theme, String accent, String language, String design) {
		Theme cur = Theme.get();
		String th = theme != null ? theme : cur.themeName;
		String ac = accent != null ? Theme.canonicalAccent(accent) : cur.accentName;
		String la = language != null ? language : I18n.code();
		String de = design != null ? Theme.normalizeDesign(design) : cur.designName;
		if (design != null && accent == null && !de.equals(cur.designName)) ac = Theme.accentForDesign(de, ac);
		long now = System.currentTimeMillis();
		ClientSync sync = ClientSync.get();
		if (sync != null) {
			sync.chooseLook(th, ac, la, de, now);
		} else if (modules != null) {
			modules.clientState.chooseLook(th, ac, la, de, now);
			ClientSync.applyLook(th, ac, la, de);
		} else {
			ClientSync.applyLook(th, ac, la, de);
		}
		if (modules != null) IntroGate.save(modules);
	}

	/** Name eines Designs in der aktuellen Sprache. */
	public static String designName(String design) {
		return I18n.tr("look.design." + Theme.normalizeDesign(design));
	}

	/**
	 * Wahl-Knopf eines Designs: kleines Vorschaubild und Name; gewählt = in der Akzentfarbe (Fuchsbau: Laterne).
	 * Liefert nichts – die Klickfläche registriert der Aufrufer.
	 */
	public static void designChip(Canvas c, int x, int y, int w, int h, String design, boolean selected, boolean hover) {
		Theme t = Theme.get();
		if (selected) {
			Paint.accentFace(c, x, y, w, h, 1f, 0f);
		} else {
			Paint.panel(c, x, y, w, h, hover ? t.surfaceHover : t.surfaceHigh, hover ? Paint.hoverEdge(t) : t.border);
		}
		int ph = h - 6;
		int pw = Math.min(w / 3, Math.round(ph * 1.5f));
		preview(c, x + 3, y + 3, pw, ph, design);
		String label = c.clip(designName(design), w - pw - 10);
		int color = selected ? Paint.accentFaceText(1f) : t.text;
		int room = w - pw - 6;
		c.text(label, x + pw + 6 + Math.max(0, (room - 4 - c.textWidth(label)) / 2), y + (h - 8) / 2, color, false);
	}

	/**
	 * Kleines Vorschaubild eines Designs (nur Rechtecke): dunkler Grund mit dem Cubiq-Zeichen, Nachthimmel mit Polarlicht
	 * und verschneiter Würfel-Insel, oder Dämmerung mit Sonne, Fichten und Lagerfeuer.
	 */
	public static void preview(Canvas c, int x, int y, int w, int h, String design) {
		if (w < 6 || h < 6) return;
		if (Theme.FOX.equals(Theme.normalizeDesign(design))) {
			// Dämmerung in Bändern, eckige Sonne über rosa Hügeln, Fichten, Wiese, Lagerfeuer mit Fuchs.
			int[] sky = {0xFF3E2044, 0xFF7A3A5A, 0xFFC05E5A, 0xFFF09A64};
			int horizon = y + h * 3 / 5;
			for (int i = 0; i < sky.length; i++) c.fill(x, y + (horizon - y) * i / sky.length, x + w, y + (horizon - y) * (i + 1) / sky.length, sky[i]);
			int sx = x + w * 3 / 5;
			c.fill(sx - 1, horizon - 4, sx + 2, horizon - 1, 0xFFFFF2C0);
			for (int i = 0; i < w; i += 2) {
				int hh = 1 + Math.floorMod(i * 7 + 3, 3);
				c.fill(x + i, horizon - hh, x + i + 2, horizon, 0xFFB4607A);
			}
			c.fill(x, horizon, x + w, y + h, 0xFF5A5634);
			c.fill(x, horizon, x + w, horizon + 1, 0xFF8A7A46);
			c.fill(x, y + h - 2, x + w, y + h, 0xFF6E4630);
			int[] trees = {1, 4, w - 4};
			for (int tx : trees) {
				if (tx < 0 || tx + 3 > w) continue;
				c.fill(x + tx + 1, horizon - 5, x + tx + 2, horizon - 3, 0xFF1F3A2E);
				c.fill(x + tx, horizon - 3, x + tx + 3, horizon + 1, 0xFF1F3A2E);
				c.fill(x + tx + 1, horizon + 1, x + tx + 2, horizon + 2, 0xFF3B2614);
			}
			int fx = x + w / 3;
			c.fill(fx, y + h - 4, fx + 2, y + h - 2, 0xFFFFB340);
			c.fill(fx, y + h - 5, fx + 1, y + h - 4, 0xFFFFF2A8);
			c.fill(fx + 3, y + h - 3, fx + 6, y + h - 2, 0xFFE47C3A);
			c.fill(fx + 3, y + h - 4, fx + 4, y + h - 3, 0xFFE47C3A);
			Paint.frame(c, x - 1, y - 1, w + 2, h + 2, 0x60FF9A52);
			return;
		}
		if (Theme.WINTER.equals(Theme.normalizeDesign(design))) {
			// Polarlicht: Nachthimmel, ein Band Polarlicht (heller Saum, Cubiq-Grün, Türkis), eine verschneite Würfel-Insel
			// über dem Wolkenmeer und ein paar Flocken – die Szene des Launchers im Kleinen, in denselben Farben.
			WinterPalette p = new WinterPalette(WinterPalette.DARK);
			int sea = y + h - Math.max(2, h / 5);
			for (int i = 0; i < 3; i++) c.fill(x, y + (sea - y) * i / 3, x + w, y + (sea - y) * (i + 1) / 3, p.sky[i * 2 + 1]);
			for (int i = 0; i < w; i++) {
				int band = y + h / 3 + (int) Math.round(WinterMath.wave(i / (double) Math.max(8, w)) * Math.max(1, h / 9));
				c.fill(x + i, band - 2, x + i + 1, band - 1, ColorMath.withAlpha(WinterPalette.AURORA[2], 0x90));
				c.fill(x + i, band - 1, x + i + 1, band, WinterPalette.AURORA[1]);
				c.fill(x + i, band, x + i + 1, band + 1, WinterPalette.AURORA[0]);
			}
			// Insel: ein Würfel (Schnee oben, Fels an den Seiten) mit einem Eiszapfen.
			int a = Math.max(2, Math.min(w, h) / 5);
			int ix = x + w * 2 / 3;
			int iy = y + h / 2 - a / 2;
			for (int r = 0; r < a; r++) {
				int half = Math.min(a, r + 1);
				c.fill(ix - half, iy + r, ix + half, iy + r + 1, p.snowTop);
			}
			c.fill(ix - a, iy + a, ix, iy + 2 * a, p.rockLeft);
			c.fill(ix, iy + a, ix + a, iy + 2 * a, p.rockRight);
			c.fill(ix - a, iy + a, ix + a, iy + a + 1, p.snowLeft);
			c.fill(ix, iy + 2 * a, ix + 1, iy + 2 * a + 1, p.ice);
			c.fill(x, sea, x + w, y + h, p.cloudNear);
			c.fill(x, sea, x + w, sea + 1, p.cloudNearRim);
			c.fill(x + 2, y + 2, x + 3, y + 3, p.flake);
			c.fill(x + w / 3, y + h * 2 / 3, x + w / 3 + 1, y + h * 2 / 3 + 1, p.flake);
			c.fill(x + w - 3, y + 3, x + w - 2, y + 4, p.flakeFar);
			Paint.frame(c, x - 1, y - 1, w + 2, h + 2, ColorMath.withAlpha(Frost.ICE, 0x60));
			return;
		}
		// Cubiq: dunkler Verlauf mit einem Hauch Grün, grüner Schein oben rechts, das Zeichen in der Mitte.
		int accent = Theme.of("dark", "cubiq", null).accent;
		c.fill(x, y, x + w, y + h / 2, 0xFF111613);
		c.fill(x, y + h / 2, x + w, y + h, 0xFF0C100E);
		c.fill(x + w / 2, y, x + w, y + h / 3, ColorMath.withAlpha(accent, 0x22));
		int cells = Math.min(h, w);
		CubiqMark.draw(c, x + (w - cells) / 2, y + (h - cells) / 2, cells, 1, accent, 1f);
		Paint.frame(c, x - 1, y - 1, w + 2, h + 2, ColorMath.withAlpha(accent, 0x60));
	}

	/**
	 * Akzent-Farbfeld wie im Launcher: ein runder Punkt; gewählt mit hellem Ring im Abstand, beim Überfahren mit einem
	 * gedämpften. Füllt {@code size}×{@code size} (Ring außen, Punkt mit 2 Pixeln Abstand).
	 */
	public static void swatch(Canvas c, int x, int y, int size, int color, boolean selected, boolean hover) {
		Theme t = Theme.get();
		if (selected || hover) {
			Paint.roundOutline(c, x, y, size, size, size / 2, selected ? t.text : ColorMath.withAlpha(t.textDim, 0xA0));
		}
		Paint.roundRect(c, x + 2, y + 2, size - 4, size - 4, (size - 4) / 2, color);
	}

	/**
	 * Zeichnet die Design-Wahl (drei Knöpfe nebeneinander) und registriert die Klicks.
	 *
	 * @return Höhe des Bereichs
	 */
	public static int drawDesigns(Canvas c, Hits hits, final CubiqModules modules, int x, int y, int w, int mx, int my,
			final Runnable click) {
		Theme t = Theme.get();
		int gap = 4;
		int h = 18;
		int n = DESIGNS.length;
		int cw = Math.min(150, (w - gap * (n - 1)) / n);
		for (int i = 0; i < DESIGNS.length; i++) {
			final String d = DESIGNS[i];
			int cx = x + i * (cw + gap);
			boolean hover = mx >= cx && mx < cx + cw && my >= y && my < y + h;
			designChip(c, cx, y, cw, h, d, d.equals(t.designName), hover);
			hits.add(cx, y, cw, h, new Runnable() {
				@Override
				public void run() {
					if (click != null) click.run();
					choose(modules, null, null, null, d);
				}
			});
		}
		return h;
	}
}
