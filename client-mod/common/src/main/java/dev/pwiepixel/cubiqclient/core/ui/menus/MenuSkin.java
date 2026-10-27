package dev.pwiepixel.cubiqclient.core.ui.menus;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.CubiqMark;
import dev.pwiepixel.cubiqclient.core.ui.FadeCanvas;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.fox.Ember;
import dev.pwiepixel.cubiqclient.core.ui.fox.FoxScene;
import dev.pwiepixel.cubiqclient.core.ui.Icons;
import dev.pwiepixel.cubiqclient.core.ui.PixelFont;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook;
import dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenPalette;
import dev.pwiepixel.cubiqclient.core.ui.halloween.HorrorLayer;
import dev.pwiepixel.cubiqclient.core.ui.halloween.MoonBar;
import dev.pwiepixel.cubiqclient.core.ui.halloween.NightScene;
import dev.pwiepixel.cubiqclient.core.ui.title.CubeScene;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleUi;
import dev.pwiepixel.cubiqclient.core.ui.winter.Frost;
import dev.pwiepixel.cubiqclient.core.ui.winter.WinterScene;

import java.util.List;

/**
 * Cubiq-Stil für Vanilla-Menüs (Pause, Serverliste, Ladebildschirme, Optionen, Weltenliste) – flach wie der Launcher.
 * Zeichnet nur Flächen – Hintergrund, Kopf-/Fußleiste, Listen-Mulde, Knopfflächen, Serverkarten, Ping-Anzeige,
 * Ladeanzeige – die Beschriftungen und Bedienung bleiben die von Minecraft (und damit die anderer Mods).
 * Versionsunabhängig; die Loader-Varianten rufen es aus ihren Hooks auf.
 *
 * <p>Vanilla schreibt weiß auf die Knöpfe; darum sind die Flächen hier immer dunkel (auch im hellen Theme),
 * nur die Akzentfarbe kommt aus dem {@link Theme}. Winternacht, Fuchsbau und Halloween haben eigene Flächen.
 */
public final class MenuSkin {
	/** Höhe der Kopfleiste (Vanilla-Titel steht bei y = 15…20). */
	public static final int HEADER = 32;

	/** Würfel-Szene des Startbildschirms – hier immer dunkel. */
	private static final CubeScene SCENE = new CubeScene();

	static {
		SCENE.setAlwaysDark(true);
	}

	private static final long START = System.nanoTime();
	/** Mittlere Zeichenzeit des Hintergrunds (µs, gleitend) – für den Selbsttest. */
	private static float avgMicros;

	// Cubiq: flache Flächen in den dunklen Farben des Launchers (base-800/850/700/600).
	private static final int SURFACE = 0xFF1D2520;
	private static final int SURFACE_HOVER = 0xFF242D27;
	private static final int SURFACE_OFF = 0xFF141A16;
	private static final int EDGE = 0xFF29332C;
	private static final int BEVEL_LIGHT = 0xFF3D4A41;
	private static final int BEVEL_DARK = 0xFF0C100E;
	/** Tiefster Grund (base-950) für Schleier und Felder. */
	private static final int DEEP = 0xFF0C100E;
	/** Linke Kante des Zeichens in der Kopfleiste (GUI-Pixel). */
	private static final int HEADER_LOGO_X = 8;

	// Winternacht: Eis- und Schneeplatten (immer dunkel – Vanilla schreibt weiß).
	private static final int ICE = 0xFF1C2C48;
	private static final int ICE_HOVER = 0xFF26406A;
	private static final int ICE_OFF = 0xFF121B2D;
	private static final int ICE_EDGE = 0xFF050A14;
	private static final int FROST_LIGHT = 0xFF6F8FBF;
	private static final int FROST_DARK = 0xFF0A1222;
	private static final int ICE_FIELD = 0xFF060B16;
	private static final int ICE_FIELD_EDGE = 0xFF2D4166;

	// Fuchsbau: Platten aus dunklem Pflaumenholz mit Abendlicht-Kante (immer dunkel – Vanilla schreibt weiß).
	private static final int WOOD = 0xFF2F1F2C;
	private static final int WOOD_HOVER = 0xFF412A38;
	private static final int WOOD_OFF = 0xFF201520;
	private static final int WOOD_EDGE = 0xFF0E070C;
	private static final int DUSK_LIGHT = 0xFF7A5468;
	private static final int DUSK_DARK = 0xFF140A12;
	private static final int FOX_FIELD = 0xFF0E080D;
	private static final int FOX_FIELD_EDGE = 0xFF4A3446;

	private static boolean winter() {
		return Theme.get().winter;
	}

	private static boolean fox() {
		return Theme.get().fox;
	}

	/** Lichtkante (oben) passend zum Design. */
	private static int bevelLight(Theme t) {
		return t.winter ? FROST_LIGHT : (t.fox ? DUSK_LIGHT : BEVEL_LIGHT);
	}

	/** Schattenkante (unten) passend zum Design. */
	private static int bevelDark(Theme t) {
		return t.winter ? FROST_DARK : (t.fox ? DUSK_DARK : BEVEL_DARK);
	}

	/** Grund von Kopf- und Fußleiste. */
	private static int barFill(Theme t, boolean inWorld) {
		if (t.winter) return inWorld ? 0x8C060B16 : 0xD80B1222;
		if (t.fox) return inWorld ? 0x8C0E080D : 0xD8170E15;
		return inWorld ? 0x8C0C100E : 0xD8111613;
	}

	private MenuSkin() {
	}

	private static float seconds() {
		return (System.nanoTime() - START) / 1_000_000_000f;
	}

	/** Sekunden seit dem Start (Animationen). */
	public static float time() {
		return seconds();
	}

	public static float averageMicros() {
		return avgMicros;
	}

	private static void measure(long start) {
		float us = (System.nanoTime() - start) / 1000f;
		avgMicros = avgMicros == 0 ? us : avgMicros + (us - avgMicros) * 0.05f;
	}

	// --- Hintergrund ---

	/**
	 * Hintergrund eines Menüs. Ohne Welt: die Würfel-Szene des Startbildschirms; in der Welt: ein ruhiger, dunkler,
	 * halbdurchsichtiger Schleier (die Welt bleibt sichtbar).
	 *
	 * @param headerH Höhe der Kopfleiste (0 = keine)
	 * @param footerTop obere Kante der Fußleiste (≥ height = keine)
	 */
	public static void background(Canvas c, int width, int height, boolean inWorld, int headerH, int footerTop) {
		background(c, width, height, inWorld, headerH, footerTop, true);
	}

	/**
	 * Wie {@link #background(Canvas, int, int, boolean, int, int)}; {@code logo} = Cubiq-Zeichen links in der Kopfleiste
	 * (aus, wenn dort Bedienelemente liegen, siehe {@link #headerLogoFits}).
	 */
	public static void background(Canvas c, int width, int height, boolean inWorld, int headerH, int footerTop, boolean logo) {
		long start = System.nanoTime();
		Theme t = Theme.get();
		float time = seconds();
		if (t.halloween) {
			halloweenBackground(c, width, height, inWorld, headerH, footerTop, logo);
			measure(start);
			return;
		}
		if (inWorld && t.winter) {
			// Winternacht in der Welt: nachtblauer Schleier, unten Frostschein und eine Frostlinie.
			c.fill(0, 0, width, height, 0x9A060B16);
			for (int i = 0; i < 6; i++) {
				int band = Math.max(4, height / 24);
				int y2 = height - i * band;
				c.fill(0, y2 - band, width, y2, ColorMath.withAlpha(t.faceGlow, Math.max(0, 18 - i * 3)));
			}
			Paint.lineH(c, 0, width, height - 2, ColorMath.lerp(t.track, t.accentText, 0.55f + 0.2f * pulse(time)), 0.4f);
		} else if (inWorld && t.fox) {
			// Fuchsbau in der Welt: Schleier in später Dämmerung, unten warmer Feuerschein und ein glimmender Ast.
			c.fill(0, 0, width, height, 0x9A100A10);
			for (int i = 0; i < 6; i++) {
				int band = Math.max(4, height / 24);
				int y2 = height - i * band;
				c.fill(0, y2 - band, width, y2, ColorMath.withAlpha(t.faceGlow, Math.max(0, 20 - i * 3)));
			}
			Paint.lineH(c, 0, width, height - 2, ColorMath.lerp(t.track, t.accentText, 0.55f + 0.2f * pulse(time)), 0.4f);
		} else if (inWorld) {
			// Cubiq: Schleier in base-950, nach unten etwas dichter.
			for (int i = 0; i < 8; i++) {
				c.fill(0, height * i / 8, width, height * (i + 1) / 8, ColorMath.withAlpha(DEEP, 0x84 + i * 5));
			}
		} else {
			scene(c, width, height, time);
		}
		if (headerH > 0) header(c, width, headerH, inWorld, logo);
		if (footerTop < height) footer(c, width, footerTop, height, inWorld);
		measure(start);
	}

	/**
	 * Halloween: ohne Welt die Mondnacht (gedämpft, damit Vanilla-Schrift lesbar bleibt), in der Welt ein Pflaumen-Schleier
	 * mit Nebelschein von unten. Darüber Kopf-/Fußleiste und die Horror-Schicht (Vignette, Korn, Blitz).
	 */
	private static void halloweenBackground(Canvas c, int width, int height, boolean inWorld, int headerH, int footerTop, boolean logo) {
		if (inWorld) {
			c.fill(0, 0, width, height, 0x9E07060C);
			for (int i = 0; i < 6; i++) {
				int band = Math.max(4, height / 24);
				int y2 = height - i * band;
				c.fill(0, y2 - band, width, y2, ColorMath.withAlpha(HalloweenPalette.VIOLET_LIGHT, Math.max(0, 16 - i * 3)));
			}
			c.fill(0, height - 1, width, height, 0x50CFD5E3);
		} else {
			NightScene.shared().draw(c, width, height, dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated());
			c.fill(0, 0, width, height, 0x6607060C);
		}
		if (headerH > 0) header(c, width, headerH, inWorld, logo);
		if (footerTop < height) footer(c, width, footerTop, height, inWorld);
		HorrorLayer.draw(c, width, height, inWorld);
	}

	/** Szene des Startbildschirms (ruhig, wenn der animierte Hintergrund aus ist). */
	private static void scene(Canvas c, int width, int height, float time) {
		boolean animated = dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated();
		if (Theme.get().halloween) {
			NightScene.shared().draw(c, width, height, animated);
			return;
		}
		if (winter()) {
			WinterScene.shared().draw(c, width, height, animated);
			return;
		}
		if (fox()) {
			FoxScene.shared().draw(c, width, height, animated);
			return;
		}
		if (sceneSimple == null || sceneSimple != !animated) {
			// setSimple baut die Szene neu – nur beim Umschalten aufrufen.
			sceneSimple = !animated;
			SCENE.setSimple(!animated);
		}
		SCENE.draw(c, width, height, animated ? time : 0f, null);
	}

	private static Boolean sceneSimple;

	private static float pulse(float time) {
		return 0.5f + 0.5f * (float) Math.sin(time * 2.2);
	}

	/** Kopfleiste mit dem kleinen Cubiq-Zeichen und Schriftzug links, feine Linie unten. */
	public static void header(Canvas c, int width, int h, boolean inWorld) {
		header(c, width, h, inWorld, true);
	}

	/** Kopfleiste; {@code logo} = Cubiq-Zeichen links zeichnen (aus, wenn dort Bedienelemente liegen). */
	public static void header(Canvas c, int width, int h, boolean inWorld, boolean logo) {
		Theme t = Theme.get();
		if (t.halloween) {
			c.fill(0, 0, width, h, inWorld ? 0x8C07060C : 0xD80A0812);
			c.fill(0, 0, width, 1, 0x40CFD5E3);
			c.fill(0, h - 1, width, h, 0x803B3358);
			int lx = HEADER_LOGO_X;
			int ly = (h - PixelFont.HEIGHT * 2) / 2;
			if (logo) logo(c, lx, ly, 2, 1f);
			HalloweenLook.menuHeader(c, 0, 0, width, h, logo ? lx + PixelFont.width("CUBIQ") * 2 + 2 : 0, seconds());
			return;
		}
		c.fill(0, 0, width, h, barFill(t, inWorld));
		if (t.winter || t.fox) {
			c.fill(0, 0, width, 1, ColorMath.withAlpha(bevelLight(t), 0x60));
			Paint.lineH(c, 0, width, h - 2, ColorMath.lerp(t.track, t.accentText, 0.8f), 0.7f);
		} else {
			c.fill(0, h - 1, width, h, EDGE);
		}
		if (logo) logo(c, HEADER_LOGO_X, (h - PixelFont.HEIGHT * 2) / 2, 2, 1f);
	}

	/**
	 * Hat das Cubiq-Zeichen links in der Kopfleiste Platz? Nein, sobald ein Bedienelement in den Bereich ragt, den es
	 * bräuchte – z. B. die Reiter von „Neue Welt“ oder „Mit Code beitreten“ in der Serverliste; dann bleibt es weg, statt
	 * halb verdeckt zu sein. {@code rects} = {x, y, Breite, Höhe} der sichtbaren Bedienelemente.
	 */
	public static boolean headerLogoFits(List<int[]> rects, int headerH) {
		int right = HEADER_LOGO_X + logoWidth(2) + 6;
		for (int[] r : rects) {
			if (r == null || r.length < 4 || r[2] <= 0 || r[3] <= 0) continue;
			if (r[0] < right && r[0] + r[2] > 0 && r[1] < headerH && r[1] + r[3] > 0) return false;
		}
		return true;
	}

	/** Fußleiste (Knopfbereich unter einer Liste). */
	public static void footer(Canvas c, int width, int top, int height, boolean inWorld) {
		Theme t = Theme.get();
		if (t.halloween) {
			c.fill(0, top, width, height, inWorld ? 0x8C07060C : 0xD80A0812);
			c.fill(0, top, width, top + 1, 0x803B3358);
			return;
		}
		c.fill(0, top, width, height, barFill(t, inWorld));
		if (t.winter || t.fox) Paint.lineH(c, 0, width, top, ColorMath.lerp(t.track, t.accentText, 0.8f), 0.7f);
		else c.fill(0, top, width, top + 1, EDGE);
	}

	/**
	 * Cubiq-Zeichen mit „Cubiq“ in der Pixelschrift (Winternacht/Fuchsbau: glühender Schriftzug mit Schnee bzw. Blättern,
	 * Halloween: Mondsilber); {@code scale} = Pixel je Schriftpunkt, (x, y) = linke obere Ecke. Breite: {@link #logoWidth}.
	 */
	public static void logo(Canvas c, int x, int y, int scale, float alpha) {
		Theme t = Theme.get();
		if (t.halloween) {
			HalloweenLook.silverWord(c, "CUBIQ", x, y, scale, alpha, 1f);
			return;
		}
		if (!t.winter && !t.fox) {
			CubiqMark.lockup(c, x, y, scale, alpha);
			return;
		}
		List<int[]> rects = PixelFont.rects("CUBIQ");
		int glow = ColorMath.withAlpha(t.glow, Math.round(40 * alpha));
		for (int[] r : rects) c.fill(x + r[0] * scale - 1, y + r[1] * scale - 1, x + r[2] * scale + 1, y + r[3] * scale + 1, glow);
		int light = ColorMath.lerp(t.accent, 0xFFFFFFFF, 0.3f);
		for (int[] r : rects) {
			int col = r[1] == 0 ? light : t.accent;
			c.fill(x + r[0] * scale, y + r[1] * scale, x + r[2] * scale, y + r[3] * scale, ColorMath.fade(col, alpha));
		}
		if (t.winter && alpha > 0.5f) {
			// Schnee auf dem Schriftzug und die kleine Schneeflocke daneben (wie im Launcher).
			TitleUi.snowCaps(c, rects, x, y, scale);
			int fx = x + PixelFont.width("CUBIQ") * scale + scale * 2;
			int px = Math.max(1, scale / 2);
			Frost.snowflake(c, fx, y + (PixelFont.HEIGHT * scale - 9 * px) / 2, px, 0xFFB8E7FF, Frost.glint(seconds()));
		} else if (t.fox && alpha > 0.5f) {
			// Ein paar Blätter auf dem Schriftzug und das kleine Fuchsgesicht daneben (wie im Launcher).
			Ember.leafCaps(c, rects, x, y, scale);
			int fx = x + PixelFont.width("CUBIQ") * scale + scale * 2;
			int px = Math.max(1, scale / 2);
			Ember.foxMarkGlow(c, fx, y + (PixelFont.HEIGHT * scale - 7 * px) / 2, px, ColorMath.lerp(t.accent, 0xFFFFFFFF, 0.2f), seconds(),
					dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated(), 1f);
		}
	}

	/** Breite von {@link #logo} mit {@code scale}. */
	public static int logoWidth(int scale) {
		Theme t = Theme.get();
		int word = PixelFont.width("CUBIQ") * scale;
		return t.halloween || t.winter || t.fox ? word : CubiqMark.textOffset(scale) + word;
	}

	/** Eingelassene Mulde hinter einer Liste (Server, Welten, Optionen). */
	public static void listWell(Canvas c, int x1, int y1, int x2, int y2, boolean inWorld) {
		if (x2 - x1 < 4 || y2 - y1 < 4) return;
		boolean w = winter();
		if (Theme.get().halloween) {
			c.fill(x1, y1, x2, y2, inWorld ? 0x7807060C : 0xB007060C);
			c.fill(x1, y1, x2, y1 + 1, 0x80000000);
			c.fill(x1, y2 - 1, x2, y2, 0x603B3358);
			return;
		}
		boolean f = fox();
		c.fill(x1, y1, x2, y2, w ? (inWorld ? 0x70020610 : 0xA8050A16) : (f ? (inWorld ? 0x700A0408 : 0xA80E070C) : (inWorld ? 0x700C100E : 0xA80C100E)));
		c.fill(x1, y1, x2, y1 + 1, 0x80000000);
		c.fill(x1, y2 - 1, x2, y2, ColorMath.withAlpha(bevelLight(Theme.get()), 0x50));
	}

	/** Trennlinien einer Liste (oben/unten): feine Linien; Winternacht/Fuchsbau/Halloween mit Frost, Ast bzw. Faden. */
	public static void listSeparators(Canvas c, int x1, int y1, int x2, int y2) {
		Theme t = Theme.get();
		if (t.halloween || t.winter || t.fox) {
			int line = ColorMath.lerp(t.track, t.accentText, 0.6f);
			Paint.lineH(c, x1, x2, y1 - 2, line, 0.3f);
			Paint.lineH(c, x1, x2, y2, line, 0.3f);
			return;
		}
		c.fill(x1, y1 - 1, x2, y1, EDGE);
		c.fill(x1, y2, x2, y2 + 1, EDGE);
	}

	// --- Knöpfe ---

	/**
	 * Fläche eines Vanilla-Knopfs (die Beschriftung zeichnet Minecraft darüber): flache, abgerundete Fläche mit feiner
	 * Kante; beim Überfahren heller mit Kante und weichem Schein in der Akzentfarbe, gesperrte Knöpfe sind dunkler.
	 */
	public static void button(Canvas c, int x, int y, int w, int h, boolean hover, boolean active) {
		if (w < 3 || h < 3) return;
		Theme t = Theme.get();
		boolean hot = hover && active;
		if (t.halloween) {
			HalloweenLook.vanillaButton(c, x, y, w, h, hot, active);
			return;
		}
		if (t.winter) {
			iceButton(c, t, x, y, w, h, hot, active);
			return;
		}
		if (t.fox) {
			woodButton(c, t, x, y, w, h, hot, active);
			return;
		}
		int fill = !active ? SURFACE_OFF : (hot ? SURFACE_HOVER : SURFACE);
		int r = Paint.radius(w, h);
		if (hot) Paint.glow(c, x, y, w, h, t.glow, 0.4f);
		Paint.roundRect(c, x, y, w, h, r, hot ? ColorMath.lerp(EDGE, t.accent, 0.75f) : (active ? EDGE : ColorMath.lerp(EDGE, SURFACE_OFF, 0.5f)));
		if (h < 5) return;
		Paint.roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		if (active) c.fill(x + Math.max(2, r), y + 1, x + w - Math.max(2, r), y + 2, hot ? 0x18FFFFFF : 0x0CFFFFFF);
	}

	/**
	 * Winternacht: Eisplatte mit Schneekante oben, Reif in der Ecke und frostiger Kante; beim Überfahren leuchtet das Eis
	 * und eine Frostlinie glimmt unten auf.
	 */
	private static void iceButton(Canvas c, Theme t, int x, int y, int w, int h, boolean hot, boolean active) {
		int fill = !active ? ICE_OFF : (hot ? ICE_HOVER : ICE);
		if (hot) Paint.glow(c, x, y, w, h, t.faceGlow, 0.55f);
		Paint.block(c, x, y, w, h, hot ? ColorMath.lerp(ICE_EDGE, t.faceGlow, 0.6f) : ICE_EDGE);
		if (h < 5) return;
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		// Schnee auf der Oberkante, an ein paar Stellen etwas dicker.
		int snow = active ? (hot ? 0xFFF2FAFF : 0xFFB9CDE9) : 0xFF52617A;
		c.fill(x + 1, y + 1, x + w - 1, y + 2, snow);
		if (active && w > 12) {
			int lump = ColorMath.withAlpha(snow, 0x90);
			for (int sx = x + 4 + Math.floorMod(x * 5 + y, 7); sx < x + w - 6; sx += 13 + Math.floorMod(sx * 3, 9)) {
				c.fill(sx, y + 2, sx + 3 + Math.floorMod(sx, 3), y + 3, lump);
			}
		}
		c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, FROST_DARK);
		if (active && w >= 10 && h >= 8) {
			int rime = ColorMath.withAlpha(0xFFFFFFFF, hot ? 0x70 : 0x34);
			c.fill(x + w - 4, y + h - 3, x + w - 2, y + h - 2, rime);
			c.fill(x + w - 3, y + h - 5, x + w - 2, y + h - 3, rime);
		}
		if (hot) {
			Paint.lineH(c, x + 3, x + w - 3, y + h - 4, t.faceOn, 0f);
		} else if (active && w > 12) {
			c.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, ColorMath.withAlpha(t.track, 0x90));
		}
	}

	/**
	 * Fuchsbau: Platte aus Pflaumenholz, die tiefe Sonne legt eine warme Kante oben drauf, ab und zu liegt ein Blatt
	 * darauf; beim Überfahren glimmt die Platte wie Glut und ein Ast leuchtet unten auf.
	 */
	private static void woodButton(Canvas c, Theme t, int x, int y, int w, int h, boolean hot, boolean active) {
		int fill = !active ? WOOD_OFF : (hot ? WOOD_HOVER : WOOD);
		if (hot) Paint.glow(c, x, y, w, h, t.faceGlow, 0.55f);
		Paint.block(c, x, y, w, h, hot ? ColorMath.lerp(WOOD_EDGE, t.faceGlow, 0.6f) : WOOD_EDGE);
		if (h < 5) return;
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		// Abendlicht auf der Oberkante.
		int rim = active ? (hot ? 0xFFFFD49A : 0xFF8A5A5E) : 0xFF3E2A36;
		c.fill(x + 1, y + 1, x + w - 1, y + 2, rim);
		if (hot) c.fill(x + 1, y + 2, x + w - 1, y + h - 2, ColorMath.withAlpha(t.faceGlow, 0x1C));
		if (active && w > 24) {
			// Ein Blatt auf der Kante (fest je Lage), selten eine Kirschblüte.
			int lx = x + 5 + Math.floorMod(x * 5 + y * 3, Math.max(1, w - 14));
			int k = Math.floorMod(x * 7 + y, 9);
			int leaf = k == 0 ? Ember.PETAL : (k < 4 ? Ember.LEAF : Ember.FALL[k % Ember.FALL.length]);
			c.fill(lx, y + 1, lx + 2, y + 2, hot ? ColorMath.lerp(leaf, 0xFFFFFFFF, 0.2f) : leaf);
			c.fill(lx + 1, y, lx + 2, y + 1, ColorMath.withAlpha(leaf, 0xC0));
		}
		c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, DUSK_DARK);
		if (active && w >= 10 && h >= 8) {
			int sun = ColorMath.withAlpha(0xFFFFB878, hot ? 0x60 : 0x2C);
			c.fill(x + 1, y + 2, x + 2, y + 4, sun);
		}
		if (hot) {
			Paint.lineH(c, x + 3, x + w - 3, y + h - 4, t.faceOn, 0f);
		} else if (active && w > 12) {
			c.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, ColorMath.withAlpha(t.track, 0x90));
		}
	}

	/**
	 * Fläche eines Vanilla-Textfelds (Text und Schreibmarke zeichnet Minecraft darüber): eingelassenes Feld wie die
	 * Cubiq-Eingabefelder, mit Kante und weichem Schein in der Akzentfarbe, solange das Feld den Fokus hat.
	 * Wie bei den Knöpfen immer dunkel – Vanilla schreibt hellgrau.
	 */
	public static void textField(Canvas c, int x, int y, int w, int h, boolean focused, boolean hover, boolean active) {
		if (w < 3 || h < 3) return;
		Theme t = Theme.get();
		if (focused && active) Paint.glow(c, x, y, w, h, t.glow, 0.45f);
		int fieldEdge = t.halloween ? HalloweenPalette.BUTTON_EDGE : (t.winter ? ICE_FIELD_EDGE : (t.fox ? FOX_FIELD_EDGE : FIELD_EDGE));
		int edge = focused && active ? t.accent : (hover && active ? ColorMath.lerp(fieldEdge, t.accent, 0.4f) : fieldEdge);
		int r = Paint.radius(w, h);
		Paint.roundRect(c, x, y, w, h, r, edge);
		if (h < 5) return;
		int fill = t.halloween ? (active ? HalloweenPalette.NIGHT : 0xFF0B0912)
				: (active ? (t.winter ? ICE_FIELD : (t.fox ? FOX_FIELD : FIELD)) : (t.winter ? ICE_OFF : (t.fox ? WOOD_OFF : SURFACE_OFF)));
		Paint.roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		if (!t.halloween && !t.winter && !t.fox) return;
		c.fill(x + 1, y + 1, x + w - 1, y + 2, bevelDark(t));
		c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, ColorMath.lerp(fill, bevelLight(t), 0.35f));
		if (focused && active && w > 8) Paint.lineH(c, x + 2, x + w - 2, y + h - 1, t.accentText, 0f);
	}

	private static final int FIELD = DEEP;
	private static final int FIELD_EDGE = BEVEL_LIGHT;

	/**
	 * Fläche hinter einem Formular (Direkt verbinden, Server hinzufügen/bearbeiten): Mulde wie hinter den Listen,
	 * oben und unten eine feine Linie – Beschriftungen und Felder stehen lesbar auf ruhigem Grund.
	 */
	public static void formPanel(Canvas c, int x1, int y1, int x2, int y2, boolean inWorld) {
		if (x2 - x1 < 8 || y2 - y1 < 8) return;
		listWell(c, x1, y1, x2, y2, inWorld);
		Theme t = Theme.get();
		if (t.halloween || t.winter || t.fox) {
			Paint.lineH(c, x1, x2, y1, ColorMath.lerp(t.track, t.accentText, 0.6f), 0.3f);
			Paint.lineH(c, x1, x2, y2 - 2, ColorMath.withAlpha(t.track, 0xC0), 0f);
			return;
		}
		c.fill(x1, y1, x2, y1 + 1, EDGE);
		c.fill(x1, y2 - 1, x2, y2, EDGE);
	}

	/** Symbol links auf einem Knopf (Cubiq-Knöpfe im Pausenmenü). */
	public static void buttonIcon(Canvas c, int x, int y, int h, String icon, boolean active) {
		Theme t = Theme.get();
		Icons.draw(c, icon, x + 5, y + (h - 8) / 2, 1, active ? t.accentText : ColorMath.fade(t.textDim, 0.8f));
	}

	/** Zähler-Abzeichen (z. B. offene Freundschaftsanfragen) oben rechts an einem Knopf. */
	public static void badge(Canvas c, int right, int top, int count) {
		if (count <= 0) return;
		Theme t = Theme.get();
		String s = count > 99 ? "99+" : String.valueOf(count);
		int w = Math.max(9, c.textWidth(s) + 5);
		int x = right - w + 3;
		int y = top - 3;
		if (t.halloween || t.winter || t.fox) {
			Paint.glow(c, x, y, w, 10, t.faceGlow, 0.6f);
			Paint.block(c, x, y, w, 10, t.faceOnEdge);
			c.fill(x + 1, y + 1, x + w - 1, y + 9, t.faceOn);
		} else {
			// Zähler wie im Launcher: Pille in der Akzentfarbe, mit dunklem Rand vom Knopf abgesetzt.
			Paint.roundRect(c, x - 1, y - 1, w + 2, 12, 6, DEEP);
			Paint.roundRect(c, x, y, w, 10, 5, t.accent);
		}
		c.text(s, x + (w - c.textWidth(s)) / 2 + 1, y + 1, t.faceTextOn, false);
	}

	// --- Serverliste ---

	/** Karte hinter einem Server-/Welt-Eintrag (Vanilla-Inhalt wird darüber gezeichnet). */
	public static void card(Canvas c, int x, int y, int w, int h, boolean selected, boolean hover, boolean pinned) {
		Theme t = Theme.get();
		if (w < 4 || h < 4) return;
		if (selected) Paint.glow(c, x, y, w, h, t.glow, 0.5f);
		int e0 = t.halloween ? HalloweenPalette.LINE : (t.winter ? ICE_EDGE : (t.fox ? WOOD_EDGE : EDGE));
		int edge = selected ? ColorMath.lerp(e0, t.accent, 0.8f) : (hover ? ColorMath.lerp(e0, t.accent, 0.35f) : e0);
		int r = Paint.radius(w, h);
		Paint.roundRect(c, x, y, w, h, r, edge);
		int fill = cardFill(selected, hover);
		Paint.roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		if (t.halloween || t.winter || t.fox) {
			c.fill(x + 2, y + 1, x + w - 2, y + 2, ColorMath.withAlpha(t.halloween ? HalloweenPalette.SILVER : bevelLight(t), t.halloween ? 0x18 : 0x70));
		}
		if (t.halloween && w >= 24 && h >= 16) HalloweenLook.cobweb(c, x + w - 2, y + 1, Math.min(9, h / 2), 1, 0x30CFD5E3);
		if (t.winter && w >= 12 && h >= 10) {
			int rime = ColorMath.withAlpha(0xFFFFFFFF, selected ? 0x60 : 0x30);
			c.fill(x + 1, y + 2, x + 4, y + 3, rime);
			c.fill(x + 1, y + 3, x + 2, y + 5, rime);
		}
		if (t.fox && w >= 12 && h >= 10) {
			int sun = ColorMath.withAlpha(0xFFFFB878, selected ? 0x60 : 0x2C);
			c.fill(x + 1, y + 2, x + 4, y + 3, sun);
			c.fill(x + 1, y + 3, x + 2, y + 5, sun);
		}
		if (pinned) {
			// Angeheftet: kräftige Linie in der Akzentfarbe an der linken Kante.
			Paint.lineV(c, x + 1, y + 2, y + h - 2, t.accentText, 0.8f);
		}
	}

	/** Füllfarbe einer Karte (deckend – die Ping-Anzeige deckt damit die Vanilla-Balken ab). */
	public static int cardFill(boolean selected, boolean hover) {
		if (Theme.get().halloween) return selected ? 0xFF261B3B : (hover ? 0xFF1C1630 : 0xFF13101F);
		if (winter()) return selected ? 0xFF1F3354 : (hover ? 0xFF18284A : 0xFF111B31);
		if (fox()) return selected ? 0xFF3A2434 : (hover ? 0xFF2F1E2B : 0xFF221620);
		// Cubiq: base-850, unter der Maus etwas heller, gewählt mit einem Hauch Akzent.
		return selected ? 0xFF1E2A20 : (hover ? 0xFF1A221C : 0xFF141A16);
	}

	/** Markierung „angeheftet“: Stecknadel in der Akzentfarbe (Feld 8×12 ab x, y). */
	public static void pin(Canvas c, int x, int y) {
		Theme t = Theme.get();
		Icons.draw(c, "pin", x, y + 3, 1, t.halloween || t.winter || t.fox ? t.faceOn : t.accentText);
	}

	/**
	 * Ping als fünf Balken (wie die Vanilla-Balken, in der Akzentfarbe); deckt die Vanilla-Anzeige (10×8 bei x, y) ab.
	 * {@code ping} &lt; 0 = keine Antwort (rotes Kreuz), {@code pinging} = läuft noch (ein Licht läuft durch).
	 */
	public static void pingBars(Canvas c, int x, int y, long ping, boolean pinging, int coverColor) {
		Theme t = Theme.get();
		c.fill(x - 1, y - 1, x + 11, y + 9, coverColor);
		int lit;
		if (pinging) {
			lit = -1;
		} else if (ping < 0) {
			lit = 0;
		} else if (ping < 150) {
			lit = 5;
		} else if (ping < 300) {
			lit = 4;
		} else if (ping < 600) {
			lit = 3;
		} else if (ping < 1000) {
			lit = 2;
		} else {
			lit = 1;
		}
		int running = (int) (seconds() * 8) % 8;
		boolean seasonal = t.halloween || t.winter || t.fox;
		int on = seasonal ? t.accentText : t.accent;
		int off = seasonal ? t.track : EDGE;
		for (int i = 0; i < 5; i++) {
			int bh = 2 + i * 1 + (i > 2 ? 1 : 0) + 1;
			int bx = x + i * 2;
			int by = y + 8 - bh;
			int color;
			if (lit < 0) color = (i == running || i == running - 1) ? on : off;
			else if (lit == 0) color = off;
			else color = i < lit ? on : off;
			c.fill(bx, by, bx + 1, y + 8, color);
		}
		if (lit == 0) {
			// Kein Signal: kleines rotes Kreuz.
			c.fill(x + 3, y + 2, x + 4, y + 3, t.danger);
			c.fill(x + 6, y + 2, x + 7, y + 3, t.danger);
			c.fill(x + 4, y + 3, x + 6, y + 5, t.danger);
			c.fill(x + 3, y + 5, x + 4, y + 6, t.danger);
			c.fill(x + 6, y + 5, x + 7, y + 6, t.danger);
		}
	}

	// --- Laden ---

	/**
	 * Vollständiger Ladebildschirm (Welt laden, verbinden, speichern …): Szene, Cubiq-Zeichen mit Schriftzug, Titel,
	 * ein Fortschrittsbalken ({@code progress} 0..1, &lt; 0 = unbestimmt: ein Lichtstreifen läuft durch) und eine Zeile
	 * darunter.
	 */
	public static void loading(Canvas c, int width, int height, String title, String detail, float progress, boolean inWorld) {
		loading(c, width, height, title, detail, progress, inWorld, -1, -1);
	}

	/** Abstand zwischen Inhalt (Panel) und den Knöpfen des Bildschirms. */
	private static final int LOADING_GAP = 6;

	/** Höhe des Inhalts (Logo, Titel, Fortschritt, Zeile) bei Logo-Maßstab {@code scale}. */
	static int loadingBlockHeight(int scale) {
		return PixelFont.HEIGHT * scale + 14 + 12 + 22 + 12;
	}

	/**
	 * Lage des Ladebildschirm-Inhalts: {top, scale}. Mittig, aber nie über den Knöpfen des Bildschirms
	 * ({@code avoidTop}..{@code avoidBottom}, −1 = keine; Vanilla setzt „Abbrechen“ auf h/4+132, bei großen
	 * Fenstern also oberhalb der Mitte): der Inhalt rückt nach oben, wird notfalls kleiner und steht nur,
	 * wenn darüber gar kein Platz ist, unter den Knöpfen. Das Panel reicht 10 Pixel über den Inhalt hinaus.
	 */
	static int[] loadingLayout(int height, int avoidTop, int avoidBottom) {
		int first = height >= 300 ? 4 : 3;
		for (int scale = first; scale >= 2; scale--) {
			int blockH = loadingBlockHeight(scale);
			int centered = Math.max(8, (height - blockH) / 2 - 10);
			if (avoidTop < 0) return new int[]{centered, scale};
			int maxTop = avoidTop - LOADING_GAP - 10 - blockH;
			if (maxTop >= 12) return new int[]{Math.min(centered, maxTop), scale};
		}
		int blockH = loadingBlockHeight(2);
		int below = avoidBottom + LOADING_GAP + 10;
		if (below + blockH + 10 <= height) return new int[]{below, 2};
		return new int[]{Math.max(8, (height - loadingBlockHeight(first)) / 2 - 10), first};
	}

	/**
	 * Wie {@link #loading(Canvas, int, int, String, String, float, boolean)}; {@code avoidTop}/{@code avoidBottom}
	 * = senkrechter Bereich der Knöpfe des Bildschirms (−1 = keine). Reihenfolge von oben: Logo, Titel,
	 * Fortschritt, Zeile, darunter die Knöpfe.
	 */
	public static void loading(Canvas c, int width, int height, String title, String detail, float progress, boolean inWorld,
			int avoidTop, int avoidBottom) {
		long start = System.nanoTime();
		Theme t = Theme.get();
		float time = seconds();
		dev.pwiepixel.cubiqclient.core.halloween.Jumpscares.LoadingView scare = null;
		if (t.halloween) {
			scare = dev.pwiepixel.cubiqclient.core.halloween.Jumpscares.get().loading(progress, System.currentTimeMillis());
			if (scare != null) progress = scare.progress;
		}
		if (t.halloween && inWorld) {
			c.fill(0, 0, width, height, 0xE007060C);
		} else if (t.halloween) {
			scene(c, width, height, time);
			c.fill(0, 0, width, height, 0x8C07060C);
		} else if (inWorld) {
			c.fill(0, 0, width, height, t.winter ? 0xE0060B16 : (t.fox ? 0xE00E080D : 0xE00C100E));
		} else {
			scene(c, width, height, time);
			c.fill(0, 0, width, height, t.winter ? (t.light ? 0x30000000 : 0x50030814) : (t.fox ? (t.light ? 0x30000000 : 0x500E0610) : 0x400C100E));
		}
		int cx = width / 2;
		int[] layout = loadingLayout(height, avoidTop, avoidBottom);
		int top = layout[0];
		int scale = layout[1];
		int logoW = logoWidth(scale);
		int blockH = loadingBlockHeight(scale);
		// Panel hinter dem Inhalt, damit Text auf der Szene lesbar bleibt.
		int pw = Math.min(width - 16, Math.max(220, Math.max(c.textWidth(title), c.textWidth(detail == null ? "" : detail)) + 40));
		int ph = blockH + 20;
		Paint.window(c, cx - pw / 2, top - 10, pw, ph);
		logo(c, cx - logoW / 2, top, scale, 1f);
		int y = top + PixelFont.HEIGHT * scale + 12;
		if (title != null && !title.isEmpty()) {
			String s = c.clip(title, pw - 16);
			c.text(s, cx - c.textWidth(s) / 2, y, t.text, t.halloween || t.winter || t.fox);
		}
		y += 16;
		progress(c, cx, y, Math.min(pw - 24, 176), progress, time);
		y += 22;
		if (detail != null && !detail.isEmpty()) {
			String s = c.clip(detail, pw - 16);
			c.text(s, cx - c.textWidth(s) / 2, y, t.textDim, false);
		}
		if (t.halloween) {
			HalloweenLook.drips(c, cx - pw / 2 + 2, cx + pw / 2 - 2, top - 10, 3, time);
			HorrorLayer.draw(c, width, height, inWorld);
			if (scare != null) loadingScare(c, width, height, scare);
		}
		measure(start);
	}

	/**
	 * Fortschritt mittig um {@code cx} (Höhe 12 ab {@code y}): im Cubiq-Design ein schlanker Balken wie im Launcher
	 * ({@link Paint#progress}), in Winternacht ein Eisbalken, im Fuchsbau ein glimmender Ast, zu Halloween der Mondlauf.
	 * {@code progress} 0..1, &lt; 0 = unbestimmt.
	 */
	public static void progress(Canvas c, int cx, int y, int maxWidth, float progress, float time) {
		Theme t = Theme.get();
		if (t.halloween) {
			// Halloween: „Mondlauf“ – der Mond nimmt mit dem Fortschritt zu.
			int w = Math.max(40, maxWidth - 16);
			MoonBar.draw(c, cx - w / 2, y - 1, w, progress, time, dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated());
			return;
		}
		if (t.winter) {
			// Winternacht: ein Eisbalken, der von links zufriert.
			Frost.bar(c, cx, y + 3, maxWidth, progress, time);
			return;
		}
		if (t.fox) {
			// Fuchsbau: ein glimmender Ast, durch den sich die Glut von links frisst.
			Ember.bar(c, cx, y + 3, maxWidth, progress, time);
			return;
		}
		int w = Math.max(40, maxWidth - 16);
		Paint.progress(c, cx - w / 2, y + 3, w, 6, progress, time, dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated());
	}

	/**
	 * Schreck am Ladebildschirm (Jumpscare „Ladebildschirm“): Licht geht aus, zwei rote Augen öffnen sich in der Mitte.
	 */
	private static void loadingScare(Canvas c, int width, int height, dev.pwiepixel.cubiqclient.core.halloween.Jumpscares.LoadingView v) {
		if (v.dark > 0.004f) c.fill(0, 0, width, height, ColorMath.withAlpha(0xFF000000, Math.round(255 * ColorMath.clamp01(v.dark))));
		float e = ColorMath.clamp01(v.eyes);
		if (e <= 0.01f) return;
		int px = Math.max(2, Math.min(width, height) / 90);
		int ew = 5 * px;
		int gap = 6 * px;
		int cy = height / 2;
		int open = Math.max(1, Math.round(3 * px * e));
		for (int side = -1; side <= 1; side += 2) {
			int ex = width / 2 + side * (gap / 2 + ew / 2) - ew / 2;
			c.fill(ex - px, cy - open - px, ex + ew + px, cy + open + px, ColorMath.withAlpha(0xFFFF2A1A, Math.round(50 * e)));
			c.fill(ex, cy - open, ex + ew, cy + open, ColorMath.withAlpha(0xFFFF3B2F, Math.round(255 * e)));
			int pupil = Math.max(1, px);
			c.fill(ex + ew / 2 - pupil / 2, cy - Math.min(open, pupil), ex + ew / 2 + (pupil + 1) / 2, cy + Math.min(open, pupil), 0xFF1A0204);
		}
	}

	/**
	 * Überblendung beim Laden der Ressourcen (statt Mojang-Logo). Nur Rechtecke, keine Schrift – beim ersten
	 * Start ist die Schrift noch nicht geladen. {@code alpha} = Deckkraft der Überblendung.
	 */
	public static void resourceOverlay(Canvas raw, int width, int height, float progress, float alpha) {
		if (alpha <= 0.004f) return;
		Canvas c = FadeCanvas.of(raw, Math.min(1f, alpha));
		Theme t = Theme.get();
		int cell = 32;
		if (t.halloween) {
			halloweenResources(c, width, height, progress);
			return;
		}
		if (t.winter) {
			// Winternacht: Nachtblau mit Eisfliesen und leise fallenden Schneeflocken.
			c.fill(0, 0, width, height, 0xFF0D1424);
			for (int x = cell / 2; x < width; x += cell) c.fill(x, 0, x + 1, height, 0x0CBFE3FF);
			for (int y = cell / 2; y < height; y += cell) c.fill(0, y, width, y + 1, 0x0CBFE3FF);
			float drift = seconds() * 6f;
			for (int i = 0; i < 40; i++) {
				int fx = Math.floorMod(i * 97 + (int) (drift * (0.4f + (i % 3) * 0.3f)), Math.max(1, width));
				int fy = Math.floorMod(i * 57 + (int) (drift * (1f + (i % 3))), Math.max(1, height));
				int size = i % 7 == 0 ? 2 : 1;
				c.fill(fx, fy, fx + size, fy + size, i % 3 == 0 ? 0xC0F4F9FF : 0x709FB4D6);
			}
		} else if (t.fox) {
			foxResources(c, width, height);
		} else {
			cubiqResources(c, t, width, height, progress);
			return;
		}
		int scale = Math.max(2, Math.min(6, width / 110));
		String word = "Cubiq CLIENT";
		int w = PixelFont.width(word) * scale;
		int x = (width - w) / 2;
		int y = height / 2 - PixelFont.HEIGHT * scale - 6;
		List<int[]> rects = PixelFont.rects(word);
		int glow = ColorMath.withAlpha(t.glow, 46);
		for (int[] r : rects) c.fill(x + r[0] * scale - 1, y + r[1] * scale - 1, x + r[2] * scale + 1, y + r[3] * scale + 1, glow);
		int light = ColorMath.lerp(t.accent, 0xFFFFFFFF, 0.3f);
		for (int[] r : rects) c.fill(x + r[0] * scale, y + r[1] * scale, x + r[2] * scale, y + r[3] * scale, r[1] == 0 ? light : t.accent);
		if (t.winter) TitleUi.snowCaps(c, rects, x, y, scale);
		if (t.fox) Ember.leafCaps(c, rects, x, y, scale);
		progress(c, width / 2, height / 2 + 14, Math.min(width - 40, 220), progress, seconds());
	}

	/**
	 * Cubiq beim Laden der Ressourcen – wie die Start-Animation des Launchers: tiefster Grund, ein weicher Schein in der
	 * Akzentfarbe, das Cubiq-Zeichen, darunter „CUBIQ CLIENT“ und der Fortschritt. Immer dunkel, nur Rechtecke.
	 */
	private static void cubiqResources(Canvas c, Theme t, int width, int height, float progress) {
		c.fill(0, 0, width, height, DEEP);
		int scale = Math.max(2, Math.min(5, width / 150));
		int px = Math.min(width, height) >= 360 ? 2 : 1;
		int cells = Math.max(24, Math.min(60, Math.min(width, height) / (3 * px)));
		int mark = cells * px;
		int wordH = PixelFont.HEIGHT * scale;
		int top = Math.max(8, (height - (mark + 14 + wordH + 18 + 12)) / 2);
		int cx = width / 2;
		Paint.radialGlow(c, cx, top + mark / 2, mark * 3 / 2, mark * 3 / 2, t.accent, 54, width, height);
		CubiqMark.draw(c, cx - mark / 2, top, cells, px, 1f);
		// „CUBIQ“ hell, „CLIENT“ in der Akzentfarbe (wie „Cubiq Launcher“ in der Start-Animation).
		int gap = scale * 4;
		int wCubiq = PixelFont.width("CUBIQ") * scale;
		int wClient = PixelFont.width("CLIENT") * scale;
		int x = cx - (wCubiq + gap + wClient) / 2;
		int y = top + mark + 14;
		for (int[] r : PixelFont.rects("CUBIQ")) c.fill(x + r[0] * scale, y + r[1] * scale, x + r[2] * scale, y + r[3] * scale, 0xFFEEF4EF);
		x += wCubiq + gap;
		for (int[] r : PixelFont.rects("CLIENT")) c.fill(x + r[0] * scale, y + r[1] * scale, x + r[2] * scale, y + r[3] * scale, t.accentHover);
		progress(c, cx, y + wordH + 14, Math.min(width - 40, 200), progress, seconds());
	}

	/**
	 * Fuchsbau beim Laden der Ressourcen: nur Rechtecke – Dämmerungshimmel in Bändern (Farben der Szene), eckige Sonne
	 * über blockigen Hügeln, ein dunkler Waldrand und langsam treibende Blätter und Glühwürmchen.
	 */
	private static void foxResources(Canvas c, int width, int height) {
		int[] sky = {0xFF2A1A3A, 0xFF3E2044, 0xFF56284E, 0xFF7A3A5A, 0xFF9A4A5C, 0xFFC05E5A, 0xFFE0785A, 0xFFF09A64};
		int horizon = height * 70 / 100;
		for (int i = 0; i < sky.length; i++) c.fill(0, horizon * i / sky.length, width, horizon * (i + 1) / sky.length, sky[i]);
		int half = Math.max(4, height / 26);
		int sx = width * 62 / 100;
		int sy = horizon - half;
		c.fill(sx - half * 3, sy - half * 2, sx + half * 3, sy + half * 2, 0x22FFB070);
		c.fill(sx - half, sy - half, sx + half, sy + half, 0xFFFFD27A);
		c.fill(sx - half + 2, sy - half + 2, sx + half - 2, sy + half - 2, 0xFFFFF2C0);
		int block = Math.max(4, height / 40);
		for (int x = 0; x < width; x += block) {
			int hgt = block * (2 + Math.floorMod((int) Math.round(Math.sin(x * 0.011) * 3 + Math.sin(x * 0.037) * 2), 4));
			c.fill(x, horizon - hgt, x + block, horizon + 2, 0xFF8A4566);
			c.fill(x, horizon - hgt, x + block, horizon - hgt + 1, 0xFFB0647A);
		}
		// Waldrand: Fichten-Schattenrisse auf der Wiese.
		c.fill(0, horizon, width, height, 0xFF3C3A2C);
		for (int x = 0; x < width; x += block * 2) {
			int tiers = 3 + Math.floorMod(x * 7, 4);
			for (int i = 0; i < tiers; i++) {
				int hw = i == 0 ? 0 : (i % 2 == 1 ? 1 : 2);
				c.fill(x - hw * block / 2, horizon - (tiers - i) * block / 2 - block / 2, x + block / 2 + hw * block / 2, horizon - (tiers - i - 1) * block / 2 - block / 2, 0xFF2A1424);
			}
		}
		c.fill(0, horizon + block, width, height, 0xFF261C22);
		c.fill(0, height * 92 / 100, width, height, 0xFF170D14);
		float drift = seconds() * 6f;
		for (int i = 0; i < 36; i++) {
			int fx = Math.floorMod(i * 97 + (int) (drift * (0.6f + (i % 3) * 0.3f)), Math.max(1, width));
			int fy = Math.floorMod(i * 57 + (int) (drift * (0.5f + (i % 3) * 0.4f)), Math.max(1, height));
			int col = i % 5 == 0 ? Ember.PETAL : Ember.FALL[i % Ember.FALL.length];
			c.fill(fx, fy, fx + (i % 4 == 0 ? 2 : 1), fy + 1, ColorMath.withAlpha(col, 0xC0));
		}
		for (int i = 0; i < 14; i++) {
			float a = 0.5f + 0.5f * (float) Math.sin(seconds() * (0.8 + (i % 4) * 0.3) + i * 1.7);
			if (a < 0.4f) continue;
			int fx = Math.floorMod(i * 211 + 37, Math.max(1, width));
			int fy = horizon + 4 + Math.floorMod(i * 89, Math.max(1, height - horizon - 8));
			c.fill(fx - 1, fy - 1, fx + 2, fy + 2, ColorMath.withAlpha(0xFFFFE27A, Math.round(40 * a)));
			c.fill(fx, fy, fx + 1, fy + 1, ColorMath.withAlpha(0xFFFFF4A8, Math.round(255 * a)));
		}
	}

	/**
	 * Halloween-Überblendung beim Laden der Ressourcen: nur Rechtecke (die Schrift ist beim ersten Start noch nicht da) –
	 * Nachthimmel mit Sternen, Blutmond, Hügel, „Cubiq CLIENT“ in Mondsilber und der Mondlauf.
	 */
	private static void halloweenResources(Canvas c, int width, int height, float progress) {
		int[] sky = {0xFF06050B, 0xFF0B0816, 0xFF110C20, 0xFF19122D, 0xFF21173A};
		int horizon = height * 3 / 4;
		for (int i = 0; i < sky.length; i++) c.fill(0, horizon * i / sky.length, width, horizon * (i + 1) / sky.length, sky[i]);
		c.fill(0, horizon, width, height, sky[sky.length - 1]);
		float time = seconds();
		for (int i = 0; i < 70; i++) {
			int sx = Math.floorMod(i * 977 + 31, Math.max(1, width));
			int sy = Math.floorMod(i * 571 + 17, Math.max(1, horizon - 8));
			float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * (0.6 + (i % 5) * 0.4) + i));
			c.fill(sx, sy, sx + 1, sy + 1, ColorMath.withAlpha(0xFFE6E9F2, Math.round(200 * tw * ((i % 3) + 1) / 3f)));
		}
		int mr = Math.max(6, height / 11);
		int mx = width * 82 / 100;
		int my = height * 22 / 100;
		for (int ring = 3; ring >= 1; ring--) disc(c, mx, my, mr + ring * mr / 3, ColorMath.withAlpha(0xFF962024, 22));
		disc(c, mx, my, mr, 0xFFD8553B);
		disc(c, mx + mr / 4, my + mr / 5, mr * 2 / 3, 0x40801E18);
		for (int x = 0; x < width; x += 2) {
			int g = horizon - height / 20 + (int) Math.round(Math.sin(x * 0.021) * height * 0.035 + Math.sin(x * 0.057) * height * 0.014);
			c.fill(x, g, x + 2, height, 0xFF1E1638);
			int m = height * 83 / 100 + (int) Math.round(Math.sin(x * 0.03 + 2) * height * 0.02);
			c.fill(x, m, x + 2, height, 0xFF120E20);
		}
		c.fill(0, height * 93 / 100, width, height, 0xFF08070E);
		int scale = Math.max(2, Math.min(6, width / 110));
		String word = "Cubiq CLIENT";
		int w = PixelFont.width(word) * scale;
		int x = (width - w) / 2;
		int y = height / 2 - PixelFont.HEIGHT * scale - 6;
		HalloweenLook.silverWord(c, word, x, y, scale, 1f, 1f);
		int bw = Math.min(width - 40, 220);
		MoonBar.draw(c, width / 2 - bw / 2, height / 2 + 12, bw, progress, time, dev.pwiepixel.cubiqclient.core.menus.MenuStyle.animated());
		HorrorLayer.vignette(c, width, height, 0.45f);
	}

	private static void disc(Canvas c, int cx, int cy, int r, int argb) {
		for (int y = -r; y < r; y++) {
			int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - (y + 0.5) * (y + 0.5))));
			if (hw > 0) c.fill(cx - hw, cy + y, cx + hw, cy + y + 1, argb);
		}
	}
}
