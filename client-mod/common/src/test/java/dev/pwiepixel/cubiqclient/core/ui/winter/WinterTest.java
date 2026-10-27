package dev.pwiepixel.cubiqclient.core.ui.winter;

import dev.pwiepixel.cubiqclient.core.config.CubiqConfig;
import dev.pwiepixel.cubiqclient.core.intro.ClientState;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design „Polarlicht“: Theme-Achse und Launcher-Datei, die Farben wie in winter.css, der Schneesaum wie im Launcher –
 * und vor allem, dass die Szene Pixel für Pixel dieselbe ist wie im Launcher ({@code tests/winter-design.test.ts} prüft
 * dieselben Zahlen).
 */
class WinterTest {
	@AfterEach
	void reset() {
		Theme.set(Theme.of("dark", "cubiq", null));
	}

	// --- Design im Theme ---

	@Test
	void designParsing() {
		assertTrue(Theme.of("dark", "ice", null, "winter").winter);
		assertEquals(Theme.WINTER, Theme.of("dark", "ice", null, " Winter ").designName);
		assertFalse(Theme.of("dark", "cubiq", null).winter, "ohne Design: Cubiq");
		assertEquals(Theme.CUBIQ, Theme.of("dark", "cubiq", null, null).designName);
		assertEquals(Theme.CUBIQ, Theme.of("dark", "cubiq", null, "neon").designName);
		assertEquals(Theme.CUBIQ, Theme.normalizeDesign("winterwinterwinterwinterwinter"));
		assertEquals(0xFF2B97D6, Theme.of("dark", "ice", null, "winter").accent);
	}

	@Test
	void colorsAreTheLauncherOnes() {
		// winter.css: base-900 Fenster, base-850 Kacheln, base-800 Knöpfe, base-700 Kanten, base-50 Schrift, base-400 gedämpft.
		Theme w = Theme.of("dark", "ice", null, "winter");
		assertEquals(0xFF0A1222, w.background);
		assertEquals(0xFF0E182C, w.surface);
		assertEquals(0xFF142138, w.surfaceHigh);
		assertEquals(0xFF1E2E4A, w.border);
		assertEquals(0xFFEEF5FC, w.text);
		assertEquals(0xFFA2B3CD, w.textDim);
		assertEquals(0xFF070C18, w.deep);
		// Leuchtende Flächen sind die Akzentfarbe (wie die Knöpfe im Launcher), ab Werk Eisblau.
		assertEquals(w.accent, w.faceOn);
		assertEquals(0xFF7FD3FF, w.accentHover);
		Theme oled = Theme.of("oled", "ice", null, "winter");
		assertEquals(0xFF04070E, oled.background);
		assertEquals(0xFF000000, oled.deep);
		Theme morning = Theme.of("light", "ice", null, "winter");
		assertTrue(morning.light);
		assertEquals(0xFFFFFFFF, morning.background);
		assertEquals(0xFFEAF2F9, morning.deep);
		assertEquals(0xFF0C1422, morning.text);
		// Andere Akzente bleiben frei wählbar.
		assertEquals(0xFF17A34A, Theme.of("dark", "emerald", null, "winter").faceOn);
		// Die Szene kennt die Varianten.
		assertEquals(WinterPalette.LIGHT, WinterPalette.variantOf("light"));
		assertEquals(WinterPalette.OLED, WinterPalette.variantOf("oled"));
		assertEquals(WinterPalette.DARK, WinterPalette.variantOf("system"));
		assertEquals(WinterMath.rgb("#71a330"), WinterPalette.AURORA[1], "Polarlicht in Cubiq-Grün");
	}

	@Test
	void accentFollowsDesignLikeTheLauncher() {
		assertEquals("ice", Theme.accentForDesign("winter", "cubiq"));
		assertEquals("cubiq", Theme.accentForDesign("cubiq", "ice"));
		assertEquals("emerald", Theme.accentForDesign("winter", "emerald"));
		assertEquals("lapis", Theme.accentForDesign("cubiq", "lapis"));
	}

	@Test
	void launcherFileCarriesTheDesign(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("cubiqclient").resolve("launcher-theme.json");
		Files.createDirectories(file.getParent());
		write(file, "{\"version\":1,\"theme\":\"oled\",\"accent\":\"ice\",\"accentColor\":\"#2B97D6\",\"language\":\"de\","
				+ "\"design\":\"winter\",\"event\":\"halloween\"}");
		try {
			assertTrue(Theme.loadFrom(dir));
			// Das Halloween-Event legt sich als eigener Look darüber – das eingestellte Thema bleibt Polarlicht.
			assertTrue(Theme.get().halloween);
			assertFalse(Theme.get().winter);
			assertTrue(Theme.plain().winter);
			assertEquals("oled", Theme.plain().themeName);
			assertEquals(0xFF2B97D6, Theme.plain().accent);
		} finally {
			dev.pwiepixel.cubiqclient.core.halloween.Halloween.reset();
		}
		// Ältere Launcher ohne Design-Feld → Cubiq; unbekanntes Design → Cubiq.
		write(file, "{\"version\":1,\"theme\":\"dark\",\"accent\":\"lapis\"}");
		assertTrue(Theme.loadFrom(dir));
		assertFalse(Theme.get().winter);
		write(file, "{\"version\":1,\"theme\":\"dark\",\"accent\":\"lapis\",\"design\":\"neon\"}");
		assertTrue(Theme.loadFrom(dir));
		assertEquals(Theme.CUBIQ, Theme.get().designName);
	}

	@Test
	void clientStateRemembersTheDesign() {
		ClientState cs = new ClientState();
		cs.chooseLook("dark", "ice", "de", "winter", 1234L);
		CubiqConfig config = new CubiqConfig();
		cs.write(config);
		assertEquals("winter", config.clientState.lookDesign);
		ClientState back = new ClientState();
		back.read(config);
		assertEquals("winter", back.lookDesign());
		assertEquals("ice", back.lookAccent());
		config.clientState.lookDesign = null;
		back.read(config);
		assertNull(back.lookDesign());
	}

	// --- Gleich wie im Launcher ---

	@Test
	void mathMatchesTheLauncher() {
		assertEquals(0.10170144238509238, WinterMath.hash(1, 2, 3));
		assertEquals(0.7964508407749236, WinterMath.hash(-5, 7, 1440));
		assertEquals(0.7557331561110914, WinterMath.hash(12345, 678, 0x2c1b));
		assertEquals(0.2455291334335925, WinterMath.glide(3.25, 7));
		assertEquals(0.5092066435609013, WinterMath.glide(-1.5, 99));
		assertEquals(0.0, WinterMath.wave(0));
		assertEquals(1.0, WinterMath.wave(0.25));
		assertEquals(0.96, WinterMath.wave(0.3));
		assertEquals(0.6399999999999999, WinterMath.wave(-0.6));
		WinterMath.Sequence r = new WinterMath.Sequence(WinterLayout.SEED ^ 0x51ce);
		assertEquals(0.5623783876653761, r.next());
		assertEquals(0.8647929569706321, r.next());
		assertEquals(0.6829025782644749, r.next());
		assertEquals(0xFF808080, WinterMath.between(0xFF000000, 0xFFFFFFFF, 0.5));
		assertEquals(0xFF808080, WinterMath.over(0xFF000000, 0xFFFFFFFF, 128));
		assertEquals(0xFFA0A0A0, WinterMath.light(0xFF404040, 0xFF808080, 255));
		assertEquals(0xFF63904F, WinterMath.light(0xFF102030, 0xFF71A330, 200));
		assertEquals(0.75, WinterLayout.auroraLevel(0), 1e-12);
		assertEquals(0.95, WinterLayout.auroraLevel(16), 1e-12);
		assertEquals(0.55, WinterLayout.auroraLevel(48), 1e-12);
		assertArrayEquals(new int[]{8, 6, 4}, WinterLayout.pineTiers(12));
		assertArrayEquals(new int[]{-18, -25, -29}, WinterLayout.pineTops(new int[]{8, 6, 4}));
		assertArrayEquals(new int[]{2}, WinterLayout.pineTiers(4));
	}

	/** Prüfsummen und Zählwerte – dieselben Werte stehen in tests/winter-design.test.ts. */
	private static final Object[][] GOLDEN = {
			// Größe, Sterne, Würfel, Summe nahe Wolken, Summe ferne Wolken, {Flocken, x, y, Tiefe von Flocke 0 bei Takt 100},
			// {bewegt, still} je dunkel, OLED, hell
			{240, 135, 125, 39, 27497, 25259, new int[]{77, 100, 116, 1},
					new long[]{2172306274L, 947558308L, 2523732370L, 3691853699L, 716548140L, 4218033526L}},
			{427, 240, 394, 42, 87152, 80666, new int[]{244, 176, 185, 1},
					new long[]{594324932L, 1511407891L, 1687684407L, 3493181329L, 3951712061L, 1430334280L}}};

	@Test
	void sceneMatchesTheLauncherPixelForPixel() {
		for (Object[] g : GOLDEN) {
			int w = (Integer) g[0];
			int h = (Integer) g[1];
			String size = w + "x" + h;
			WinterLayout L = WinterLayout.build(w, h, WinterLayout.SEED);
			assertEquals((int) (Integer) g[2], L.stars.size(), size);
			int voxels = 0;
			for (WinterLayout.Island isl : L.islands) voxels += isl.voxels.size();
			assertEquals((int) (Integer) g[3], voxels, size);
			assertEquals((int) (Integer) g[4], sum(L.cloudNear.top), size);
			assertEquals((int) (Integer) g[5], sum(L.cloudFar.top), size);
			WinterSnow snow = new WinterSnow(L.w, L.h, 7, 1);
			assertArrayEquals((int[]) g[6], new int[]{snow.count, snow.x(0, 100), snow.y(0, 100), snow.depthOf(0)}, size);
			long[] frames = (long[]) g[7];
			for (int variant = 0; variant < 3; variant++) {
				WinterPainter painter = new WinterPainter(L, new WinterPalette(variant));
				int[] px = new int[L.w * L.h];
				WinterPainter.Frame f = new WinterPainter.Frame();
				f.tick = 30;
				f.seconds = 21.5;
				f.level = 0.8;
				f.moving = true;
				f.snow = snow;
				painter.compose(px, f);
				long moving = WinterMath.checksum(px);
				f.tick = 40;
				f.seconds = 21;
				f.moving = false;
				f.snow = null;
				painter.compose(px, f);
				long still = WinterMath.checksum(px);
				assertEquals(frames[variant * 2], moving, size + " Variante " + variant + " bewegt");
				assertEquals(frames[variant * 2 + 1], still, size + " Variante " + variant + " still");
			}
		}
	}

	private static int sum(short[] a) {
		int s = 0;
		for (short v : a) s += v;
		return s;
	}

	// --- Szene ---

	@Test
	void layoutIsStableAndInsideTheScene() {
		WinterLayout a = WinterLayout.build(427, 240, WinterLayout.SEED);
		WinterLayout b = WinterLayout.build(427, 240, WinterLayout.SEED);
		assertEquals(a.islands.size(), b.islands.size());
		assertArrayEquals(a.cloudNear.top, b.cloudNear.top);
		assertEquals(3, a.ribbons.size());
		int heroes = 0;
		for (WinterLayout.Island isl : a.islands) {
			if (isl.depth == 2) heroes++;
			assertFalse(isl.voxels.isEmpty());
			assertTrue(isl.ox + isl.x0 >= 1 && isl.ox + isl.x1 <= a.w - 1 && isl.oy + isl.y0 >= 1, "Insel im Bild");
			assertEquals(0, isl.a % 2);
		}
		assertEquals(1, heroes);
		// Über dem Spielen-Knopf (wie auf der Startseite des Launchers).
		WinterLayout home = WinterLayout.build(505, 160, WinterLayout.SEED, 360, 128, 480);
		for (WinterLayout.Island isl : home.islands) {
			if (isl.depth != 2) continue;
			assertTrue(Math.abs(isl.ox + (isl.x0 + isl.x1) / 2.0 - 420) <= 1);
			assertEquals(125, isl.oy + isl.y1);
		}
		WinterLayout tiny = WinterLayout.build(3, 2, 1);
		assertEquals(16, tiny.w);
		assertTrue(tiny.islands.isEmpty());
	}

	@Test
	void auroraOnlyLightsTheSky() {
		WinterLayout L = WinterLayout.build(240, 135, WinterLayout.SEED);
		WinterPainter painter = new WinterPainter(L, new WinterPalette(WinterPalette.DARK));
		WinterPainter.Frame f = new WinterPainter.Frame();
		f.seconds = 20;
		int[] calm = new int[L.w * L.h];
		painter.compose(calm, f);
		f.level = 1;
		int[] lit = new int[calm.length];
		painter.compose(lit, f);
		int changed = 0;
		for (int i = 0; i < calm.length; i++) {
			assertEquals(0xFF, calm[i] >>> 24, "deckend");
			if (calm[i] == lit[i]) continue;
			changed++;
			assertTrue(i / L.w < L.h * 0.6, "Polarlicht nur am Himmel");
			for (int shift = 16; shift >= 0; shift -= 8) assertTrue(((lit[i] >> shift) & 255) >= ((calm[i] >> shift) & 255), "hellt nur auf");
		}
		assertTrue(changed > 1500, "Polarlicht sichtbar: " + changed);
	}

	@Test
	void snowIsStatelessAndStaysInTheFrame() {
		WinterSnow a = new WinterSnow(190, 100, 5, 1);
		WinterSnow b = new WinterSnow(190, 100, 5, 1);
		assertEquals(45, a.count);
		for (int i = 0; i < a.count; i++) {
			assertEquals(a.x(i, 77), b.x(i, 77));
			for (int t = 0; t < 4000; t += 37) {
				assertTrue(a.x(i, t) >= -4 && a.x(i, t) < 194);
				assertTrue(a.y(i, t) >= -4 && a.y(i, t) < 104);
			}
		}
	}

	@Test
	void texelGrowsOnlyForHugeWindows() {
		assertEquals(1, WinterScene.texelFor(427, 240));
		assertEquals(1, WinterScene.texelFor(640, 360));
		assertEquals(2, WinterScene.texelFor(960, 540));
		assertEquals(3, WinterScene.texelFor(1920, 1080));
	}

	/** Die Flächen und Schriftfarben im Spiel sind genau die Grundskala aus winter.css (dunkel, OLED, heller Morgen). */
	@Test
	void colorsMatchTheLauncherCss() throws IOException {
		Path css = clientModRoot().getParent().resolve("app").resolve("assets").resolve("css").resolve("winter.css");
		org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(css), "Launcher-Quellen fehlen");
		String text = new String(Files.readAllBytes(css), StandardCharsets.UTF_8);
		java.util.Map<String, Integer> dark = scale(text, ":root[data-design=\"winter\"]:not([data-event=\"halloween\"]) {");
		java.util.Map<String, Integer> oled = new java.util.HashMap<String, Integer>(dark);
		oled.putAll(scale(text, ":root[data-design=\"winter\"][data-theme=\"oled\"]:not([data-event=\"halloween\"]) {"));
		java.util.Map<String, Integer> light = new java.util.HashMap<String, Integer>(dark);
		light.putAll(scale(text, ":root[data-design=\"winter\"][data-theme=\"light\"]:not([data-event=\"halloween\"]) {"));
		for (String name : new String[]{"dark", "oled"}) {
			java.util.Map<String, Integer> c = "dark".equals(name) ? dark : oled;
			Theme t = Theme.of(name, "ice", null, "winter");
			assertEquals(c.get("900"), t.background, name + ": Fenster = base-900");
			assertEquals(c.get("850"), t.surface, name + ": Kacheln = base-850");
			assertEquals(c.get("800"), t.surfaceHigh, name + ": Knöpfe = base-800");
			assertEquals(c.get("700"), t.border, name + ": Kanten = base-700");
			assertEquals(c.get("50"), t.text, name + ": Schrift = base-50");
			assertEquals(c.get("400"), t.textDim, name + ": gedämpft = base-400");
			assertEquals(c.get("950"), t.deep, name + ": Grund = base-950");
		}
		Theme morning = Theme.of("light", "ice", null, "winter");
		assertEquals(light.get("900"), morning.background);
		assertEquals(light.get("850"), morning.surface);
		assertEquals(light.get("800"), morning.surfaceHover);
		assertEquals(light.get("950"), morning.surfaceHigh);
		assertEquals(light.get("700"), morning.border);
		assertEquals(light.get("50"), morning.text);
		assertEquals(light.get("400"), morning.textDim);
		assertEquals(light.get("950"), morning.deep);
	}

	/** {@code --color-base-NNN: #rrggbb;} eines Blocks (bis zur schließenden Klammer) als Stufe → ARGB. */
	private static java.util.Map<String, Integer> scale(String css, String head) {
		int start = css.indexOf(head);
		assertTrue(start >= 0, "Block fehlt: " + head);
		String block = css.substring(start, css.indexOf('}', start));
		java.util.Map<String, Integer> out = new java.util.HashMap<String, Integer>();
		Matcher m = Pattern.compile("--color-base-(\\d+): #([0-9a-f]{6});").matcher(block);
		while (m.find()) out.put(m.group(1), 0xFF000000 | Integer.parseInt(m.group(2), 16));
		assertFalse(out.isEmpty(), "keine Grundskala in " + head);
		return out;
	}

	// --- Details wie im Launcher ---

	/** Der Schneesaum hat genau das Profil von {@code --winter-lip} in winter.css (Tiefe je Spalte). */
	@Test
	void snowLipMatchesTheLauncherCss() throws IOException {
		Path css = clientModRoot().getParent().resolve("app").resolve("assets").resolve("css").resolve("winter.css");
		org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(css), "Launcher-Quellen fehlen");
		String text = new String(Files.readAllBytes(css), StandardCharsets.UTF_8);
		Matcher svg = Pattern.compile("--winter-lip: url\\(\"data:image/svg\\+xml,(.*?)\"\\);").matcher(text);
		int found = 0;
		while (svg.find()) {
			found++;
			// Pfade: Schatten (je Spalte in Zeile = Tiefe), Schnee (Zeilen 1 … Tiefe − 1), Kante (Zeile 0).
			List<int[]> shade = rects(svg.group(1), 0);
			int[] depth = new int[Frost.LIP.length];
			for (int[] r : shade) {
				for (int x = r[0]; x < r[0] + r[2]; x++) depth[x] = r[1];
			}
			assertArrayEquals(Frost.LIP, depth);
		}
		assertEquals(2, found, "dunkel und hell");
	}

	/** Rechtecke {x, y, w} des {@code n}-ten Pfads einer SVG (nur „Mx yhWv1h-Wz“). */
	private static List<int[]> rects(String svg, int n) {
		Matcher paths = Pattern.compile("d='([^']*)'").matcher(svg);
		for (int i = 0; i <= n; i++) assertTrue(paths.find());
		List<int[]> out = new ArrayList<int[]>();
		Matcher m = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-\\d+z").matcher(paths.group(1));
		while (m.find()) out.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
		return out;
	}

	@Test
	void snowLipAndIcyBarDraw() {
		Theme.set(Theme.of("dark", "ice", null, "winter"));
		Rec c = new Rec();
		Frost.lip(c, 0, 0, 48, 1f);
		assertTrue(c.fills > 10);
		assertEquals(0xFFFFFFFF, c.last, "oben die helle Kante");
		// Fortschritt: ohne Bewegung steht alles (auch der Schimmer), mit Bewegung wandert er.
		assertEquals(bar(0.4f, 0.3f, false), bar(0.4f, 1.7f, false));
		assertEquals(bar(-1f, 0.3f, false), bar(-1f, 1.7f, false));
		assertNotEquals(bar(0.4f, 0.3f, true), bar(0.4f, 1.3f, true));
		assertNotEquals(bar(-1f, 0.3f, true), bar(-1f, 0.9f, true));
	}

	private static String bar(float progress, float time, boolean animated) {
		Rec c = new Rec();
		Frost.bar(c, 100, 10, 176, progress, time, animated);
		return c.log.toString();
	}

	private static void write(Path file, String json) throws IOException {
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
	}

	/** client-mod/ – Tests laufen in common/. */
	private static Path clientModRoot() {
		Path p = Paths.get("").toAbsolutePath();
		for (int i = 0; i < 4 && p != null; i++, p = p.getParent()) {
			if (Files.isDirectory(p.resolve("common").resolve("src"))) return p;
		}
		return Paths.get("..").toAbsolutePath().normalize();
	}

	/** Zeichenfläche, die nur mitschreibt. */
	private static final class Rec implements Canvas {
		final StringBuilder log = new StringBuilder();
		int fills;
		int last;

		@Override
		public void fill(int x1, int y1, int x2, int y2, int argb) {
			fills++;
			last = argb;
			log.append(x1).append(',').append(y1).append(',').append(x2).append(',').append(y2).append(',').append(Integer.toHexString(argb)).append('\n');
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
