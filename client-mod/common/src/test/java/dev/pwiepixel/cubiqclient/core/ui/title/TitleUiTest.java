package dev.pwiepixel.cubiqclient.core.ui.title;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cubiq-Startbildschirm: Würfel-Szene, Farben, Tastatur-Bedienung, Seitenleiste, Sockel. */
class TitleUiTest {
	private static final int W = 480;
	private static final int H = 270;
	private static final int[][] RESERVED = {{140, 40, 340, 220}, {0, 256, 480, 270}};

	@Test
	void sceneIsDeterministicAndKeepsTheContentFree() {
		CubeScene a = new CubeScene();
		a.build(W, H, RESERVED, false);
		CubeScene b = new CubeScene();
		b.build(W, H, RESERVED, false);
		assertTrue(a.cubes().size() >= 3, "zu wenige Würfel: " + a.cubes().size());
		assertEquals(a.cubes().size(), b.cubes().size());
		for (int i = 0; i < a.cubes().size(); i++) {
			CubeScene.Cube x = a.cubes().get(i);
			CubeScene.Cube y = b.cubes().get(i);
			assertEquals(x.x, y.x);
			assertEquals(x.y, y.y);
			assertEquals(x.size, y.size);
		}
		for (CubeScene.Cube c : a.cubes()) {
			assertTrue(c.opacity >= 0.1f && c.opacity <= 0.24f, "Deckkraft wie im Launcher: " + c.opacity);
			assertTrue(c.period >= 16f && c.period <= 26f);
			int half = c.size / 2;
			for (int[] r : RESERVED) {
				boolean overlaps = c.x + half > r[0] && c.x - half < r[2] && c.y + half > r[1] && c.y - half < r[3];
				assertFalse(overlaps, "Würfel im freien Bereich bei " + c.x + "," + c.y);
			}
		}
	}

	@Test
	void sceneDrawsWithinBudgetOfRectangles() {
		CountingCanvas canvas = new CountingCanvas();
		CubeScene scene = new CubeScene();
		scene.draw(canvas, 960, 540, 1.3f, new int[][]{{380, 60, 580, 400}});
		int full = canvas.fills;
		assertTrue(full > 100 && full < 3000, "Rechtecke: " + full);
		CountingCanvas lite = new CountingCanvas();
		CubeScene simple = new CubeScene();
		simple.setSimple(true);
		simple.draw(lite, 960, 540, 1.3f, new int[][]{{380, 60, 580, 400}});
		assertTrue(lite.fills < full, "sparsam " + lite.fills + " / voll " + full);
		assertTrue(simple.isSimple());
	}

	@Test
	void titleDrawsNoRedstone() {
		Theme.set(Theme.of("dark", "cubiq", null));
		RecordingColors canvas = new RecordingColors();
		TitleUi ui = new TitleUi(new FakeHost());
		long until = System.nanoTime() + 2_000_000_000L;
		while (ui.alpha() < 0.999f && System.nanoTime() < until) {
			ui.render(new RecordingColors(), 854, 480, -1, -1);
			sleep();
		}
		ui.render(canvas, 854, 480, -1, -1);
		// Redstone-Block, Staub, Fackel, Lampe, Tiefenschiefer und Redstone-Rot der früheren Szene.
		int[] old = {0xFFA3170F, 0xFF6E0B06, 0xFFD8321F, 0xFF4A1410, 0xFFF2B454, 0xFF3B2A1D, 0xFF18181C, 0xFFE0281E, 0xFF4C3726};
		for (int c : old) assertFalse(canvas.colors.contains(c), String.format("Redstone-Farbe %08X gezeichnet", c));
		assertTrue(canvas.colors.contains(0xFF71A330), "Hauptknopf in Cubiq-Grün");
	}

	@Test
	void keyboardFocusCyclesAndActivatesAfterTheFlash() {
		Theme.set(Theme.of("dark", "cubiq", null));
		FakeHost host = new FakeHost();
		TitleUi ui = new TitleUi(host);
		CountingCanvas canvas = new CountingCanvas();
		ui.render(canvas, W, H, -1, -1);
		assertTrue(ui.keyPressed(0, UiKey.TAB, false));
		assertEquals("Singleplayer, button", host.narrated.get(host.narrated.size() - 1));
		ui.keyPressed(0, UiKey.DOWN, false);
		assertEquals("Multiplayer, button", host.narrated.get(host.narrated.size() - 1));
		// Rückwärts über den Anfang hinaus landet beim Link am Ende
		ui.keyPressed(0, UiKey.UP, false);
		ui.keyPressed(0, UiKey.UP, false);
		assertEquals("Classic title screen, button", host.narrated.get(host.narrated.size() - 1));
		ui.keyPressed(0, UiKey.TAB, false);
		assertTrue(ui.keyPressed(0, UiKey.ENTER, false));
		assertEquals(1, host.clicks);
		assertEquals(0, host.singleplayer, "erst nach dem Aufblitzen");
		long until = System.nanoTime() + 2_000_000_000L;
		while (host.singleplayer == 0 && System.nanoTime() < until) {
			ui.render(canvas, W, H, -1, -1);
			sleep();
		}
		assertEquals(1, host.singleplayer);
		// Esc schließt den Startbildschirm nicht
		assertFalse(ui.keyPressed(0, UiKey.ESCAPE, false));
		ui.requestClose();
		assertFalse(ui.isClosing());
	}

	@Test
	void layoutWithoutModsCentersQuit() {
		FakeHost host = new FakeHost();
		host.mods = false;
		TitleUi ui = new TitleUi(host);
		ui.render(new CountingCanvas(), W, H, -1, -1);
		ui.focus(4);
		ui.keyPressed(0, UiKey.ENTER, false);
		long until = System.nanoTime() + 2_000_000_000L;
		while (host.quit == 0 && System.nanoTime() < until) {
			ui.render(new CountingCanvas(), W, H, -1, -1);
			sleep();
		}
		assertEquals(1, host.quit);
		assertNotEquals(0, host.clicks);
	}

	@Test
	void sidebarAndFigureAdaptToTheWindow() {
		FakeHost host = new FakeHost();
		TitleUi ui = new TitleUi(host);
		// 854×480 mit GUI-Größe 1: volle Leiste, Figur
		ui.render(new CountingCanvas(), 854, 480, -1, -1);
		assertEquals(TitleUi.SIDE_FULL, ui.sideMode());
		assertTrue(ui.figureShown());
		assertTrue(ui.buttonRect("wardrobe") != null);
		// Minecraft-Standard 854×480 mit GUI 2 = 427×240: Symbole, Figur bleibt
		ui.render(new CountingCanvas(), 427, 240, -1, -1);
		assertEquals(TitleUi.SIDE_ICONS, ui.sideMode());
		assertTrue(ui.figureShown());
		// Sehr schmal (GUI 4): Symbolzeile oben, keine Figur, kein Konto-Knopf
		ui.render(new CountingCanvas(), 213, 120, -1, -1);
		assertEquals(TitleUi.SIDE_ROW, ui.sideMode());
		assertFalse(ui.figureShown());
		assertTrue(ui.buttonRect("wardrobe") == null);
		// Die Leiste liegt nie auf den Knöpfen der Mitte
		ui.render(new CountingCanvas(), 427, 240, -1, -1);
		int[] quit = ui.buttonRect("quit");
		for (String id : new String[]{"accounts", "friends", "clips", "cubiqSettings"}) {
			int[] r = ui.buttonRect(id);
			assertTrue(r != null, id);
			assertTrue(r[0] >= quit[0] + quit[2] + 10, id + " überlappt die Mitte");
		}
		// Garderobe-Knopf unter der Figur: links neben der Mitte und breit genug für „Garderobe“ mit Symbol
		int[] single = ui.buttonRect("singleplayer");
		int[] wardrobe = ui.buttonRect("wardrobe");
		assertTrue(wardrobe != null && wardrobe[0] + wardrobe[2] + 10 <= single[0], "Garderobe überlappt die Mitte");
		assertTrue(wardrobe[2] - 18 >= new CountingCanvas().textWidth("Wardrobe"), "Garderobe-Knopf nur " + wardrobe[2] + " px");
	}

	@Test
	void missingAreasSayComingSoonAndSettingsOpenTheMenu() {
		FakeHost host = new FakeHost();
		TitleUi ui = new TitleUi(host);
		CountingCanvas canvas = new CountingCanvas();
		ui.render(canvas, W, H, -1, -1);
		int[] r = ui.buttonRect("wardrobe");
		assertTrue(ui.mouseClicked(r[0] + 2, r[1] + 2, 0));
		renderUntil(ui, canvas, () -> ui.toast() != null);
		assertEquals("Coming soon: Wardrobe", ui.toast());
		r = ui.buttonRect("cubiqSettings");
		ui.mouseClicked(r[0] + 2, r[1] + 2, 0);
		renderUntil(ui, canvas, () -> host.cubiqMenu > 0);
		assertEquals(1, host.cubiqMenu);
		// Konten in der Seitenleiste (noch nicht da) → Hinweis
		host.narrated.clear();
		r = ui.buttonRect("accounts");
		ui.mouseClicked(r[0] + 2, r[1] + 2, 0);
		renderUntil(ui, canvas, () -> !host.narrated.isEmpty());
		assertEquals("Coming soon: Accounts", host.narrated.get(0));
	}

	private static void renderUntil(TitleUi ui, CountingCanvas canvas, java.util.function.BooleanSupplier done) {
		long until = System.nanoTime() + 2_000_000_000L;
		while (!done.getAsBoolean() && System.nanoTime() < until) {
			ui.render(canvas, W, H, -1, -1);
			sleep();
		}
		assertTrue(done.getAsBoolean());
	}

	@Test
	void turntableSpinsAndCanBeDragged() {
		Turntable t = new Turntable();
		float a0 = t.angle();
		t.tick(1f, true);
		assertEquals(Turntable.wrap(a0 + Turntable.AUTO_SPEED), t.angle(), 0.5f);
		t.grab(100);
		t.drag(110);
		assertEquals(Turntable.wrap(a0 + Turntable.AUTO_SPEED + 10 * Turntable.DRAG_DEG_PER_PX), t.angle(), 0.01f);
		float held = t.angle();
		t.tick(1f, true);
		assertEquals(held, t.angle(), 0.0001f, "beim Ziehen keine Eigendrehung");
		t.release();
		assertFalse(t.dragging());
		// Standbild ohne Animation
		Turntable still = new Turntable();
		still.tick(5f, false);
		still.tick(5f, false);
		float s = still.angle();
		still.tick(1f, false);
		assertEquals(s, still.angle(), 0.01f);
	}

	private static void sleep() {
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class FakeHost implements TitleHost {
		final List<String> narrated = new ArrayList<String>();
		boolean mods = true;
		int clicks;
		int singleplayer;
		int quit;

		@Override public void singleplayer() { singleplayer++; }
		@Override public void multiplayer() { }
		@Override public void options() { }
		int cubiqMenu;

		@Override public void cubiqMenu() { cubiqMenu++; }
		@Override public boolean hasMods() { return mods; }
		@Override public void mods() { }
		@Override public void quit() { quit++; }
		@Override public void classicTitle() { }
		@Override public String versionLine() { return "Minecraft test"; }
		@Override public void playClick() { clicks++; }
		@Override public void narrate(String text) { narrated.add(text); }
		@Override public boolean animated() { return true; }
		@Override public boolean simpleAnimation() { return false; }
	}

	/** Merkt sich alle deckenden Füllfarben. */
	private static final class RecordingColors implements Canvas {
		final java.util.Set<Integer> colors = new java.util.HashSet<Integer>();

		@Override public void fill(int x1, int y1, int x2, int y2, int argb) { colors.add(argb); }
		@Override public void text(String text, int x, int y, int argb, boolean shadow) { }
		@Override public int textWidth(String text) { return text.length() * 6; }
		@Override public int lineHeight() { return 9; }
		@Override public String clip(String text, int maxWidth) { return text.substring(0, Math.max(0, Math.min(text.length(), maxWidth / 6))); }
		@Override public void flush() { }
		@Override public void scissor(int x1, int y1, int x2, int y2) { }
		@Override public void noScissor() { }
		@Override public void raise(float z) { }
		@Override public void push() { }
		@Override public void translate(float x, float y) { }
		@Override public void scale(float factor) { }
		@Override public void pop() { }
	}

	private static final class CountingCanvas implements Canvas {
		int fills;

		@Override public void fill(int x1, int y1, int x2, int y2, int argb) { fills++; }
		@Override public void text(String text, int x, int y, int argb, boolean shadow) { }
		@Override public int textWidth(String text) { return text.length() * 6; }
		@Override public int lineHeight() { return 9; }
		@Override public String clip(String text, int maxWidth) { return text.substring(0, Math.max(0, Math.min(text.length(), maxWidth / 6))); }
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
