package dev.pwiepixel.cubiqclient.ui;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Markenfarben (ARGB) und kleine Zeichenhilfen im Cubiq-Stil (1.13.2-Variante). */
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

	private Brand() {
	}

	/** Gefülltes Rechteck (x, y, Breite, Höhe). */
	public static void rect(int x, int y, int w, int h, int color) {
		if (w <= 0 || h <= 0) return;
		Gui.drawRect(x, y, x + w, y + h, color);
	}

	/** Gefülltes Rechteck mit Eckpunkten (x2/y2 exklusiv). */
	public static void fill(int x1, int y1, int x2, int y2, int color) {
		if (x2 <= x1 || y2 <= y1) return;
		Gui.drawRect(x1, y1, x2, y2, color);
	}

	/** Rahmen mit 1 px Stärke. */
	public static void outline(int x, int y, int w, int h, int color) {
		Gui.drawRect(x, y, x + w, y + 1, color);
		Gui.drawRect(x, y + h - 1, x + w, y + h, color);
		Gui.drawRect(x, y + 1, x + 1, y + h - 1, color);
		Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** Text zeichnen; setzt vorher die GL-Farbe zurück (sonst färbt drawRect nach). */
	public static void text(FontRenderer font, String s, int x, int y, int color, boolean shadow) {
		GlStateManager.color4f(1f, 1f, 1f, 1f);
		if (shadow) font.drawStringWithShadow(s, x, y, color);
		else font.drawString(s, x, y, color);
	}

	public static void centered(FontRenderer font, String s, int centerX, int y, int color, boolean shadow) {
		text(font, s, centerX - font.getStringWidth(s) / 2, y, color, shadow);
	}

	/** An/Aus-Pille; liefert die Breite. */
	public static int pill(FontRenderer font, int x, int y, boolean on, boolean hover) {
		String label = on ? I18n.tr("common.on") : I18n.tr("common.off");
		int w = 26;
		int h = 11;
		int bg = on ? ACCENT : (hover ? 0xFF4A574D : OFF);
		rect(x, y, w, h, bg);
		// Kleiner "Schalter"-Knopf
		int knobX = on ? x + w - 4 : x + 1;
		rect(knobX, y + 1, 3, h - 2, on ? 0xFFFFFFFF : 0xFF869489);
		int tx = on ? x + 3 : x + 6;
		text(font, label, tx, y + 2, on ? ON_ACCENT : TEXT, false);
		return w;
	}

	/** Umrandeter Text-Button. */
	public static void button(FontRenderer font, int x, int y, int w, int h, String label, boolean primary, boolean hover) {
		if (primary) {
			rect(x, y, w, h, hover ? ACCENT_HOVER : ACCENT);
		} else {
			rect(x, y, w, h, hover ? SURFACE_HOVER : SURFACE);
			outline(x, y, w, h, hover ? TEXT_DIM : BORDER);
		}
		centered(font, label, x + w / 2, y + (h - 8) / 2, primary ? ON_ACCENT : TEXT, false);
	}

	/** Zeichnen auf das Rechteck (GUI-Koordinaten, x2/y2 exklusiv) beschränken. */
	public static void scissor(int x1, int y1, int x2, int y2) {
		MainWindow window = Minecraft.getInstance().mainWindow;
		double scale = window.getGuiScaleFactor();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor((int) (x1 * scale), (int) (window.getFramebufferHeight() - y2 * scale),
				(int) Math.max(0, (x2 - x1) * scale), (int) Math.max(0, (y2 - y1) * scale));
	}

	public static void noScissor() {
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
