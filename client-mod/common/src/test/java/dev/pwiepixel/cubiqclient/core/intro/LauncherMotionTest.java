package dev.pwiepixel.cubiqclient.core.intro;

import dev.pwiepixel.cubiqclient.core.halloween.Halloween;
import dev.pwiepixel.cubiqclient.core.menus.MenuStyle;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiScreen;
import dev.pwiepixel.cubiqclient.core.ui.menu.HudItem;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuAction;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import dev.pwiepixel.cubiqclient.core.ui.menus.MenuSkin;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleHost;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleUi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * „Animationen“ des Launchers ({@code launcher-theme.json → motion}): „reduced“ beruhigt den Cubiq Client zusätzlich zur
 * eigenen Einstellung „Animierter Hintergrund“, ohne sie zu ändern – die Einführung ist sofort da, Startbildschirm,
 * Ladebilder und unbestimmte Ladeanzeigen stehen still.
 */
class LauncherMotionTest {
	private static final int W = 480;
	private static final int H = 270;

	@BeforeEach
	void before() {
		reset();
	}

	@AfterEach
	void after() {
		reset();
	}

	private static void reset() {
		Theme.motionFromLauncher(null);
		Theme.set(Theme.of("dark", "redstone", null));
		MenuStyle.install(null);
		Halloween.reset();
	}

	@Test
	void launcherFileCarriesTheMotion(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("cubiqclient").resolve("launcher-theme.json");
		Files.createDirectories(file.getParent());
		write(file, "{\"version\":1,\"theme\":\"dark\",\"accent\":\"cubiq\",\"design\":\"cubiq\",\"motion\":\"reduced\"}");
		assertTrue(Theme.loadFrom(dir));
		assertTrue(Theme.launcherReducedMotion());
		write(file, "{\"version\":1,\"theme\":\"dark\",\"accent\":\"cubiq\",\"motion\":\"full\"}");
		assertTrue(Theme.loadFrom(dir));
		assertFalse(Theme.launcherReducedMotion());
		// Ältere Launcher ohne Feld und unbekannte Werte: volle Bewegung („system“ löst der Launcher selbst auf).
		for (String json : new String[]{"{\"version\":1,\"theme\":\"dark\"}", "{\"motion\":null}", "{\"motion\":\"system\"}",
				"{\"motion\":\"wobbly\"}", "{\"motion\":1}", "{\"motion\":true}"}) {
			Theme.motionFromLauncher(Theme.MOTION_REDUCED);
			write(file, json);
			assertTrue(Theme.loadFrom(dir), json);
			assertFalse(Theme.launcherReducedMotion(), json);
		}
	}

	@Test
	void launcherOnlyCalmsAndNeverChangesTheOwnSetting() {
		CubiqModules modules = new CubiqModules();
		MenuStyle.install(modules);
		assertTrue(MenuStyle.animated(), "ab Werk bewegt");
		Theme.motionFromLauncher(Theme.MOTION_REDUCED);
		assertFalse(MenuStyle.animated());
		assertTrue(modules.titleAnimated.get(), "die eigene Einstellung bleibt an");
		Theme.motionFromLauncher(Theme.MOTION_FULL);
		assertTrue(MenuStyle.animated());
		// „Immer an“ im Launcher überstimmt die eigene Einstellung nicht.
		modules.titleAnimated.set(false);
		assertFalse(MenuStyle.animated());
		assertFalse(MenuStyle.animated(false));
		assertTrue(MenuStyle.animated(true));
		Theme.motionFromLauncher(Theme.MOTION_REDUCED);
		assertFalse(MenuStyle.animated(true));
	}

	@Test
	void introAppearsAtOnceWithoutMotion() {
		Theme.motionFromLauncher(Theme.MOTION_REDUCED);
		IntroUi intro = new IntroUi(new Host(new CubiqModules()));
		assertTrue(intro.still());
		assertEquals(1f, intro.alpha(), 0.0001f, "sofort ganz da – ohne Einblenden");
		String first = frame(intro);
		pause(120);
		assertEquals(first, frame(intro), "kein Hineingleiten, keine pulsierende Lampe");
		intro.next();
		String step = frame(intro);
		pause(120);
		assertEquals(step, frame(intro), "Schrittwechsel ohne Gleiten");

		// Eigene Einstellung aus wirkt genauso; mit Bewegung blendet die Einführung ein.
		CubiqModules own = new CubiqModules();
		own.titleAnimated.set(false);
		Theme.motionFromLauncher(Theme.MOTION_FULL);
		assertTrue(new IntroUi(new Host(own)).still());
		IntroUi moving = new IntroUi(new Host(new CubiqModules()));
		assertFalse(moving.still());
		assertEquals(0f, moving.alpha(), 0.0001f);
		String opening = frame(moving);
		pause(120);
		assertNotEquals(opening, frame(moving), "mit Bewegung gleitet sie herein");
	}

	@Test
	void titleScreenStandsStillAlthoughTheOwnSettingIsOn() {
		// Ein Hinweis aus einem anderen Test würde als Toast mitlaufen.
		IntroGate.takeNotice();
		Theme.motionFromLauncher(Theme.MOTION_REDUCED);
		TitleUi still = new TitleUi(new Title());
		String a = settled(still);
		pause(150);
		assertEquals(a, frame(still), "Standbild");

		Theme.motionFromLauncher(Theme.MOTION_FULL);
		TitleUi moving = new TitleUi(new Title());
		String b = settled(moving);
		pause(150);
		assertNotEquals(b, frame(moving), "ohne Launcher-Signal läuft die Schaltung");
	}

	@Test
	void indeterminateProgressStandsStill() {
		for (String design : new String[]{Theme.REDSTONE, Theme.WINTER, Theme.FOX, Halloween.ID}) {
			if (Halloween.ID.equals(design)) Halloween.force(true);
			else Theme.set(Theme.of("dark", "cubiq", null, design));
			Theme.motionFromLauncher(Theme.MOTION_REDUCED);
			String still = lamps(-1f, 0.3f);
			assertEquals(still, lamps(-1f, 1.7f), design);
			assertEquals(still, lamps(-1f, 4.1f), design);
			assertEquals(lamps(0.4f, 0.3f), lamps(0.4f, 1.7f), design + ": auch Glitzer und Glut stehen");
			Theme.motionFromLauncher(Theme.MOTION_FULL);
			assertNotEquals(lamps(-1f, 0.3f), lamps(-1f, 1.7f), design + ": sonst läuft das Signal durch");
		}
	}

	@Test
	void loadingScreensStandStill() {
		Theme.set(Theme.of("dark", "cubiq", null, Theme.FOX));
		Theme.motionFromLauncher(Theme.MOTION_REDUCED);
		String overlay = overlay();
		String loading = loading();
		pause(200);
		assertEquals(overlay, overlay(), "Ladebild beim Start: Blätter und Glühwürmchen stehen");
		assertEquals(loading, loading(), "Welt laden: Szene und Ladeanzeige stehen");

		Theme.motionFromLauncher(Theme.MOTION_FULL);
		overlay = overlay();
		loading = loading();
		pause(200);
		assertNotEquals(overlay, overlay());
		assertNotEquals(loading, loading());
	}

	private static String lamps(float progress, float time) {
		RecordingCanvas c = new RecordingCanvas();
		MenuSkin.lamps(c, W / 2, 40, 176, progress, time);
		return c.log.toString();
	}

	private static String overlay() {
		RecordingCanvas c = new RecordingCanvas();
		MenuSkin.resourceOverlay(c, W, H, 0.5f, 1f);
		return c.log.toString();
	}

	private static String loading() {
		RecordingCanvas c = new RecordingCanvas();
		MenuSkin.loading(c, W, H, "Loading terrain", "", -1f, false);
		return c.log.toString();
	}

	private static String frame(UiScreen ui) {
		RecordingCanvas c = new RecordingCanvas();
		ui.render(c, W, H, -1, -1);
		return c.log.toString();
	}

	/** Rendert, bis Einblenden und Übergänge ausgelaufen sind; liefert das nächste Bild. */
	private static String settled(UiScreen ui) {
		long until = System.nanoTime() + 800_000_000L;
		while (System.nanoTime() < until) {
			frame(ui);
			pause(10);
		}
		return frame(ui);
	}

	private static void write(Path file, String json) throws IOException {
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
	}

	private static void pause(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Startbildschirm ohne Spiel; die eigene Einstellung „Animierter Hintergrund“ ist an. */
	private static final class Title implements TitleHost {
		@Override public void singleplayer() { }
		@Override public void multiplayer() { }
		@Override public void options() { }
		@Override public void cubiqMenu() { }
		@Override public boolean hasMods() { return false; }
		@Override public void mods() { }
		@Override public void quit() { }
		@Override public void classicTitle() { }
		@Override public String versionLine() { return "Minecraft test"; }
		@Override public void playClick() { }
		@Override public void narrate(String text) { }
		@Override public boolean animated() { return true; }
		@Override public boolean simpleAnimation() { return false; }
	}

	/** Menü ohne Spiel: nur die Module. */
	private static final class Host implements MenuHost {
		private final CubiqModules modules;

		Host(CubiqModules modules) {
			this.modules = modules;
		}

		@Override public CubiqModules modules() { return modules; }
		@Override public void playClick() { }
		@Override public void closeScreen() { }
		@Override public void openHudEditor() { }
		@Override public void openMenu() { }
		@Override public void openPacks() { }
		@Override public boolean hasPacks() { return true; }
		@Override public boolean supports(Module module) { return true; }
		@Override public List<MenuAction> actions(Module module) { return Collections.emptyList(); }
		@Override public void save() { }
		@Override public boolean shiftDown() { return false; }
		@Override public String keyLabel(String keyName) { return "?"; }
		@Override public String keyNameOf(int rawKey) { return null; }
		@Override public String menuKeyLabel() { return "Right Shift"; }
		@Override public String profileKeyLabel() { return ""; }
		@Override public List<HudItem> hudItems() { return Collections.emptyList(); }
		@Override public boolean inWorld() { return false; }
	}

	/** Schreibt jeden Zeichenbefehl mit – zwei gleiche Protokolle sind dasselbe Bild. */
	private static final class RecordingCanvas implements Canvas {
		final StringBuilder log = new StringBuilder();

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			log.append('f').append(x1).append(',').append(y1).append(',').append(x2).append(',').append(y2).append(',')
					.append(Integer.toHexString(argb)).append('\n');
		}

		@Override
		public void text(String text, int x, int y, int argb, boolean shadow) {
			log.append('t').append(text).append('@').append(x).append(',').append(y).append(',').append(Integer.toHexString(argb))
					.append('\n');
		}

		@Override public int textWidth(String text) { return text.length() * 6; }
		@Override public int lineHeight() { return 9; }
		@Override public String clip(String text, int maxWidth) { return text.substring(0, Math.max(0, Math.min(text.length(), maxWidth / 6))); }
		@Override public void flush() { }
		@Override public void scissor(int x1, int y1, int x2, int y2) { log.append('s').append(x1).append(',').append(y1).append('\n'); }
		@Override public void noScissor() { }
		@Override public void raise(float z) { }
		@Override public void push() { }
		@Override public void translate(float x, float y) { log.append('m').append(x).append(',').append(y).append('\n'); }
		@Override public void scale(float factor) { log.append('x').append(factor).append('\n'); }
		@Override public void pop() { }
	}
}
