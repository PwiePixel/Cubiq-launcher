package dev.pwiepixel.cubiqclient.core.intro;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import dev.pwiepixel.cubiqclient.core.ui.menu.HudItem;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuAction;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Start-Animation der Einführung: Cubiq-Zeichen wie im Launcher, überspringbar, ohne Animationen gar nicht. */
class IntroSplashTest {
	@BeforeEach
	void setUp() {
		I18n.use("en");
		Theme.set(Theme.of("dark", "cubiq", null));
	}

	@AfterEach
	void tearDown() {
		// „Einführung übersprungen“ gilt sonst für den nächsten Startbildschirm (auch in anderen Tests).
		IntroGate.takeNotice();
	}

	@Test
	void splashShowsTheMarkAndNameThenTheFirstStep() {
		IntroUi ui = new IntroUi(new FakeHost());
		assertTrue(ui.splashing(), "zuerst das Cubiq-Zeichen");
		Recording c = new Recording();
		runUntilSplashEnds(ui, c);
		assertFalse(ui.splashing(), "endet von selbst nach rund 2 s");
		assertTrue(c.accentFills > 0, "Zeichen in der Akzentfarbe");
		assertTrue(c.texts.contains("Client"), "Name wie „Cubiq Launcher“ im Launcher");
		assertTrue(c.texts.contains(I18n.tr("intro.splash.skip")), "Hinweis zum Überspringen");
		assertEquals(0, ui.step(), "danach der erste Schritt");
		assertFalse(ui.isClosing());
	}

	@Test
	void clickLeavesTheSplashWithoutSkippingTheIntro() {
		FakeHost host = new FakeHost();
		IntroUi ui = new IntroUi(host);
		ui.render(new Recording(), 854, 480, -1, -1);
		assertTrue(ui.mouseClicked(400, 200, 0), "die ganze Fläche überspringt");
		assertTrue(ui.splashing(), "blendet noch kurz aus");
		long until = System.nanoTime() + 2_000_000_000L;
		while (ui.splashing() && System.nanoTime() < until) {
			ui.render(new Recording(), 854, 480, -1, -1);
			sleep();
		}
		assertFalse(ui.splashing(), "nach dem Ausblenden (0,38 s) vorbei");
		assertEquals(0, ui.step());
		assertFalse(ui.isClosing(), "die Einführung selbst bleibt offen");
		assertEquals(0, host.saves);
	}

	@Test
	void anyKeyLeavesTheSplashEvenEscape() {
		IntroUi ui = new IntroUi(new FakeHost());
		ui.render(new Recording(), 854, 480, -1, -1);
		assertTrue(ui.keyPressed(256, UiKey.ESCAPE, false));
		assertFalse(ui.isClosing(), "Esc beendet nur die Start-Animation");
		long until = System.nanoTime() + 2_000_000_000L;
		while (ui.splashing() && System.nanoTime() < until) {
			ui.render(new Recording(), 854, 480, -1, -1);
			sleep();
		}
		assertFalse(ui.splashing());
		// Danach wieder wie gewohnt: Esc überspringt die Einführung.
		assertTrue(ui.keyPressed(256, UiKey.ESCAPE, false));
		assertTrue(ui.isClosing());
	}

	@Test
	void noSplashWithoutAnimations() {
		FakeHost host = new FakeHost();
		host.modules.titleAnimated.set(false);
		IntroUi ui = new IntroUi(host);
		assertFalse(ui.splashing(), "„Animierter Hintergrund“ aus = Bewegung reduziert");
		Recording c = new Recording();
		ui.render(c, 854, 480, -1, -1);
		assertFalse(c.texts.contains(I18n.tr("intro.splash.skip")));
	}

	@Test
	void autotestJumpsEndTheSplash() {
		IntroUi ui = new IntroUi(new FakeHost());
		ui.goTo(2);
		assertFalse(ui.splashing());
		assertEquals(2, ui.step());
		IntroUi other = new IntroUi(new FakeHost());
		other.skipSplash();
		assertFalse(other.splashing());
		assertEquals(0, other.step());
	}

	private static void runUntilSplashEnds(IntroUi ui, Recording c) {
		long until = System.nanoTime() + 4_000_000_000L;
		while (ui.splashing() && System.nanoTime() < until) {
			ui.render(c, 854, 480, -1, -1);
			sleep();
		}
	}

	private static void sleep() {
		try {
			Thread.sleep(40);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Merkt sich Texte und zählt Flächen in der Akzentfarbe (Deckkraft egal). */
	private static final class Recording implements Canvas {
		final List<String> texts = new ArrayList<String>();
		int accentFills;

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			if ((argb & 0xFFFFFF) == (Theme.get().accent & 0xFFFFFF) && (argb >>> 24) > 0) accentFills++;
		}

		@Override
		public void text(String text, int x, int y, int argb, boolean shadow) {
			texts.add(text);
		}

		@Override
		public int textWidth(String text) {
			return text.length() * 6;
		}

		@Override
		public int lineHeight() {
			return 9;
		}

		@Override
		public String clip(String text, int maxWidth) {
			return text;
		}

		@Override
		public void flush() {
		}

		@Override
		public void scissor(int x1, int y1, int x2, int y2) {
		}

		@Override
		public void noScissor() {
		}

		@Override
		public void raise(float z) {
		}

		@Override
		public void push() {
		}

		@Override
		public void translate(float x, float y) {
		}

		@Override
		public void scale(float factor) {
		}

		@Override
		public void pop() {
		}
	}

	/** Host ohne Minecraft: zählt nur das Speichern. */
	private static final class FakeHost implements MenuHost {
		final CubiqModules modules = new CubiqModules();
		int saves;

		@Override
		public CubiqModules modules() {
			return modules;
		}

		@Override
		public void playClick() {
		}

		@Override
		public void closeScreen() {
		}

		@Override
		public void openHudEditor() {
		}

		@Override
		public void openMenu() {
		}

		@Override
		public void openPacks() {
		}

		@Override
		public boolean hasPacks() {
			return true;
		}

		@Override
		public boolean supports(Module module) {
			return true;
		}

		@Override
		public List<MenuAction> actions(Module module) {
			return Collections.emptyList();
		}

		@Override
		public void save() {
			saves++;
		}

		@Override
		public boolean shiftDown() {
			return false;
		}

		@Override
		public String keyLabel(String keyName) {
			return "?";
		}

		@Override
		public String keyNameOf(int rawKey) {
			return null;
		}

		@Override
		public String menuKeyLabel() {
			return "Right Shift";
		}

		@Override
		public String profileKeyLabel() {
			return "";
		}

		@Override
		public List<HudItem> hudItems() {
			return Collections.emptyList();
		}

		@Override
		public boolean inWorld() {
			return false;
		}
	}
}
