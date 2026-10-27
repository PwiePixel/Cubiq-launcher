package dev.pwiepixel.cubiqclient.ui;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import net.minecraft.client.gui.Font;

/** Markenfarben (ARGB) und kleine Zeichenhilfen im Cubiq-Stil. */
public final class Brand {
	/** Hintergrund (base-900 des Launchers). */
	public static final int BG = 0xFF111613;
	/** Flächen/Karten (base-800), unter der Maus heller; feine Kante (base-700). */
	public static final int SURFACE = 0xFF1D2520;
	public static final int SURFACE_HOVER = 0xFF242D27;
	public static final int BORDER = 0xFF29332C;
	/** Cubiq-Grün – Akzent (Hauptknöpfe, Markierungen). */
	public static final int ACCENT = 0xFF71A330;
	public static final int ACCENT_HOVER = 0xFF88BB45;
	/** Schrift auf dem Akzent (dunkel wie im Launcher). */
	public static final int ON_ACCENT = 0xFF0B1405;
	/** Helles Grün – "aktiv/an", Werte und Fokus. */
	public static final int ON = 0xFFA9D46B;
	public static final int OFF = 0xFF3D4A41;
	public static final int TEXT = 0xFFEEF4EF;
	public static final int TEXT_DIM = 0xFF869489;
	/** Halbtransparenter HUD-Hintergrund. */
	public static final int HUD_BG = 0x90111613;
	public static final int HUD_BG_PRESSED = 0xE071A330;
	public static final int SCRIM = 0xB00C100E;

	private Brand() {
	}

	/** Rahmen mit 1 px Stärke. */
	public static void outline(Gfx g, int x, int y, int w, int h, int color) {
		g.outline(x, y, w, h, color);
	}

	/** An/Aus-Pille; liefert die Breite. */
	public static int pill(Gfx g, Font font, int x, int y, boolean on, boolean hover) {
		String label = on ? I18n.tr("common.on") : I18n.tr("common.off");
		int w = 26;
		int h = 11;
		int bg = on ? ACCENT : (hover ? 0xFF4A574D : OFF);
		g.fill(x, y, x + w, y + h, bg);
		// Kleiner "Schalter"-Knopf
		int knobX = on ? x + w - 4 : x + 1;
		g.fill(knobX, y + 1, knobX + 3, y + h - 1, on ? 0xFFFFFFFF : 0xFF869489);
		int tx = on ? x + 3 : x + 6;
		g.text(font, label, tx, y + 2, on ? ON_ACCENT : TEXT, false);
		return w;
	}

	/** Umrandeter Text-Button. */
	public static void button(Gfx g, Font font, int x, int y, int w, int h, String label, boolean primary, boolean hover) {
		if (primary) {
			g.fill(x, y, x + w, y + h, hover ? ACCENT_HOVER : ACCENT);
		} else {
			g.fill(x, y, x + w, y + h, hover ? SURFACE_HOVER : SURFACE);
			outline(g, x, y, w, h, hover ? TEXT_DIM : BORDER);
		}
		if (primary) {
			// Dunkle Schrift auf dem Grün ohne Schatten (mit Schatten wirkt sie verschmiert).
			g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, ON_ACCENT, false);
		} else {
			g.centered(font, label, x + w / 2, y + (h - 8) / 2, TEXT);
		}
	}
}
