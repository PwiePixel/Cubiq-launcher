package dev.pwiepixel.cubiqclient.core.ui;

import dev.pwiepixel.cubiqclient.core.intro.LookPicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cubiq-Design: Farben wie im Launcher, frühere Namen, Grundformen und das Cubiq-Zeichen. */
class CubiqLookTest {
	@AfterEach
	void reset() {
		Theme.set(Theme.of("dark", "cubiq", null));
	}

	@Test
	void darkThemeUsesTheLauncherTokens() {
		Theme d = Theme.of("dark", "cubiq", null);
		assertEquals(Theme.CUBIQ, d.designName);
		// app/assets/css/main.css: base-950/900/850/800/700/400/50 und primary-300/400/500, on-primary.
		assertEquals(0xFF0C100E, d.deep);
		assertEquals(0xFF111613, d.background);
		assertEquals(0xFF161C18, d.surface);
		assertEquals(0xFF1D2520, d.surfaceHigh);
		assertEquals(0xFF29332C, d.border);
		assertEquals(0xFF869489, d.textDim);
		assertEquals(0xFFEEF4EF, d.text);
		assertEquals(0xFF71A330, d.accent);
		assertEquals(0xFF88BB45, d.accentHover);
		assertEquals(0xFFA9D46B, d.accentText);
		assertEquals(0xFF0B1405, d.faceTextOn, "dunkle Schrift auf dem Grün");
		assertEquals(0xFFFDA4A4, d.danger);
		assertEquals(0xFFF8CD7A, d.caution);
		Theme oled = Theme.of("oled", "cubiq", null);
		assertEquals(0xFF000000, oled.deep);
		assertEquals(0xFF050706, oled.background);
		Theme light = Theme.of("light", "cubiq", null);
		assertTrue(light.light);
		assertEquals(0xFFFFFFFF, light.background);
		assertEquals(0xFF0F1411, light.text);
		assertEquals(0xFFB42318, light.danger);
		assertEquals(0xFF8A5A00, light.caution);
	}

	@Test
	void textOnTheAccentFollowsTheLauncher() {
		for (String a : new String[]{"cubiq", "amber", "emerald", "ice", "orange"}) {
			assertEquals(0xFF0B1405, Theme.of("dark", a, null).faceTextOn, a);
		}
		for (String a : new String[]{"lapis", "amethyst"}) {
			assertEquals(0xFFFFFFFF, Theme.of("dark", a, null).faceTextOn, a);
		}
		assertEquals(0xFFFFFFFF, Theme.of("dark", "custom", "#123456").faceTextOn, "dunkle eigene Farbe → weiße Schrift");
		// Fehler bleiben rot, auch wenn der Akzent rot-ähnlich oder grün ist.
		assertEquals(Theme.of("dark", "orange", null).danger, Theme.of("dark", "emerald", null).danger);
	}

	@Test
	void earlierNamesMapToTheLauncherNames() {
		Theme r = Theme.of("dark", "redstone", null, "redstone");
		assertEquals("cubiq", r.accentName);
		assertEquals(0xFF71A330, r.accent, "kein Redstone-Rot mehr");
		assertEquals(Theme.CUBIQ, r.designName);
		assertEquals("amber", Theme.of("dark", "lamp", null).accentName);
		assertEquals(0xFFE0900C, Theme.of("dark", "lamp", null).accent);
		assertEquals("orange", Theme.of("dark", "fox", null).accentName);
		assertEquals("cubiq", Theme.designAccent(Theme.CUBIQ));
		assertEquals("cubiq", Theme.accentForDesign("cubiq", "orange"));
	}

	@Test
	void pickerOffersExactlyTheLauncherAccents() {
		// Gleiche Namen und Reihenfolge wie Accent in src-tauri/crates/core/src/settings.rs – sonst käme eine Wahl im
		// Spiel beim Launcher als „unbekannt“ (Cubiq-Grün) an.
		assertArrayEquals(new String[]{"cubiq", "amber", "emerald", "lapis", "amethyst", "ice", "orange"}, LookPicker.ACCENTS);
		int[] colors = {0xFF71A330, 0xFFE0900C, 0xFF17A34A, 0xFF3563E9, 0xFF9B4DDF, 0xFF2B97D6, 0xFFE8702A};
		for (int i = 0; i < colors.length; i++) assertEquals(colors[i], Theme.accentColor(LookPicker.ACCENTS[i], null));
		assertEquals(Theme.CUBIQ, LookPicker.DESIGNS[0]);
	}

	@Test
	void roundOutlineIsTheClosedEdgeOfRoundRect() {
		for (int[] box : new int[][]{{20, 12, 4}, {40, 20, 6}, {16, 16, 8}, {30, 9, 4}, {10, 10, 3}, {64, 30, 12}}) {
			Pixels fill = new Pixels(box[0], box[1]);
			Paint.roundRect(fill, 0, 0, box[0], box[1], box[2], 0xFFFFFFFF);
			Pixels outline = new Pixels(box[0], box[1]);
			Paint.roundOutline(outline, 0, 0, box[0], box[1], box[2], 0xFFFFFFFF);
			for (int y = 0; y < box[1]; y++) {
				for (int x = 0; x < box[0]; x++) {
					boolean edge = fill.on(x, y) && (!fill.on(x - 1, y) || !fill.on(x + 1, y) || !fill.on(x, y - 1) || !fill.on(x, y + 1));
					assertEquals(edge, outline.on(x, y), "Rand " + box[0] + "x" + box[1] + " r" + box[2] + " bei " + x + "," + y);
				}
			}
		}
	}

	@Test
	void markIsSymmetricWithAllParts() {
		for (int cells : new int[]{16, 24, 32}) {
			int[][] rows = CubiqMark.rows(cells);
			assertEquals(cells, rows.length);
			boolean[][] on = new boolean[cells][cells];
			boolean[] kinds = new boolean[8];
			for (int r = 0; r < cells; r++) {
				for (int i = 0; i + 2 < rows[r].length; i += 3) {
					assertTrue(rows[r][i] >= 0 && rows[r][i + 1] <= cells && rows[r][i] < rows[r][i + 1]);
					kinds[rows[r][i + 2]] = true;
					for (int c = rows[r][i]; c < rows[r][i + 1]; c++) on[r][c] = true;
					// Linke Würfelseite links der Mitte, rechte rechts davon.
					if (rows[r][i + 2] == 3) assertTrue(rows[r][i + 1] <= cells / 2 + 1);
					if (rows[r][i + 2] == 4) assertTrue(rows[r][i] >= cells / 2 - 1);
				}
			}
			for (int k = 1; k <= 4; k++) assertTrue(kinds[k], "Teil " + k + " fehlt bei " + cells);
			for (int r = 0; r < cells; r++) {
				for (int c = 0; c < cells; c++) assertEquals(on[r][c], on[r][cells - 1 - c], "spiegelgleich bei " + cells);
			}
		}
		// Kleine Zeichen ohne Rauten-Spur, große mit.
		assertFalse(hasTrail(CubiqMark.rows(32)));
		assertTrue(hasTrail(CubiqMark.rows(64)));
	}

	private static boolean hasTrail(int[][] rows) {
		for (int[] row : rows) {
			for (int i = 0; i + 2 < row.length; i += 3) if (row[i + 2] >= 5) return true;
		}
		return false;
	}

	/** Zeichenfläche als Pixelraster (nur für Formen). */
	private static final class Pixels implements Canvas {
		private final boolean[][] px;
		private final int w;
		private final int h;

		Pixels(int w, int h) {
			this.w = w;
			this.h = h;
			px = new boolean[h][w];
		}

		boolean on(int x, int y) {
			return x >= 0 && y >= 0 && x < w && y < h && px[y][x];
		}

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			for (int y = Math.max(0, y1); y < Math.min(h, y2); y++) {
				for (int x = Math.max(0, x1); x < Math.min(w, x2); x++) px[y][x] = true;
			}
		}

		@Override public void text(String text, int x, int y, int argb, boolean shadow) { }
		@Override public int textWidth(String text) { return text.length() * 6; }
		@Override public int lineHeight() { return 9; }
		@Override public String clip(String text, int maxWidth) { return text; }
		@Override public void flush() { }
		@Override public void scissor(int x1, int y1, int x2, int y2) { }
		@Override public void noScissor() { }
		@Override public void raise(float z) { }
		@Override public void push() { }
		@Override public void translate(float x, float y) { }
		@Override public void scale(float factor) { }
		@Override public void pop() { }
	}
}
