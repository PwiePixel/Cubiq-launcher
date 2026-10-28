package dev.pwiepixel.cubiqclient.core.map;

import dev.pwiepixel.cubiqclient.core.halloween.Halloween;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import dev.pwiepixel.cubiqclient.core.waypoint.Waypoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Karten 3: Maßstab, Entfernungen, Messen, „Gehe zu“ und Tasten der Weltkarte; Chunk-Raster, Blickkegel und Farben der
 * Minimap.
 */
class Maps3Test {
	/** Breit genug, dass unten der Bedienhinweis Platz hat. */
	private static final int W = 1000, H = 300;

	@BeforeEach
	void calm() {
		Halloween.force(Boolean.FALSE);
		Theme.set(Theme.of("dark", "cubiq", null));
	}

	@AfterEach
	void reset() {
		Halloween.force(null);
		Theme.set(Theme.of("dark", "cubiq", null));
		I18n.use("en");
	}

	// --- Maßstab und Entfernungen ---

	@Test
	void scaleBarUsesTheLongestRoundLengthThatFits() {
		assertEquals(50, WorldMapTools.scaleBlocks(2f, 120f));
		assertEquals(50, WorldMapTools.scaleBlocks(2f, 100f), "genau passend zählt");
		assertEquals(100, WorldMapTools.scaleBlocks(1f, 120f));
		assertEquals(5, WorldMapTools.scaleBlocks(16f, 120f));
		assertEquals(2000, WorldMapTools.scaleBlocks(0.05f, 120f));
		assertEquals(0, WorldMapTools.scaleBlocks(200f, 120f), "nicht einmal ein Block passt");
		assertEquals(0, WorldMapTools.scaleBlocks(0f, 120f));
		assertEquals(0, WorldMapTools.scaleBlocks(Float.NaN, 120f));
		for (float px = 0.002f; px < 100f; px *= 1.17f) {
			int b = WorldMapTools.scaleBlocks(px, 120f);
			if (b == 0) {
				assertTrue(px > 120f, "bei " + px);
				continue;
			}
			assertTrue(b * px <= 120f, b + " Blöcke bei " + px);
			assertTrue(round(b), b + " ist eine runde Zahl");
			assertTrue(nextRound(b) * px > 120f, "nächste runde Länge passt nicht mehr (" + px + ")");
		}
	}

	private static boolean round(int b) {
		while (b % 10 == 0) b /= 10;
		return b == 1 || b == 2 || b == 5;
	}

	private static long nextRound(int b) {
		long step = 1;
		while (step * 10 <= b) step *= 10;
		int k = (int) (b / step);
		return k == 1 ? 2 * step : (k == 2 ? 5 * step : 10 * step);
	}

	@Test
	void distancesReadInMetresAndKilometres() {
		assertEquals("87 m", WorldMapTools.distanceLabel(87.4));
		assertEquals("999 m", WorldMapTools.distanceLabel(999.4));
		assertEquals("1 km", WorldMapTools.distanceLabel(999.6));
		assertEquals("1.2 km", WorldMapTools.distanceLabel(1234));
		assertEquals("1.9 km", WorldMapTools.distanceLabel(1949));
		assertEquals("2 km", WorldMapTools.distanceLabel(1950), "ganze Kilometer ohne „.0“");
		assertEquals("25 km", WorldMapTools.distanceLabel(25_000));
		I18n.use("de");
		assertEquals("1,2 km", WorldMapTools.distanceLabel(1234), "Dezimalkomma der aktiven Sprache");
		assertEquals("5 km", WorldMapTools.distanceLabel(5000));
		assertEquals(5.0, WorldMapTools.distance(1, 2, 4, 6), 1e-9);
	}

	// --- „Gehe zu“: Koordinaten in der Suche ---

	@Test
	void coordinatesInTheSearchAreRecognised() {
		assertCoords(120, -40, "120 -40");
		assertCoords(120, -40, "  120,  -40 ");
		assertCoords(120, -40, "120 64 -40");
		assertCoords(120, -40, "120/64/-40");
		assertCoords(120, -40, "120; -40");
		assertCoords(100, -200, "x 100 z -200");
		assertCoords(100, -200, "X:100 Z:-200");
		assertCoords(100, -200, "z=-200 x=100");
		assertCoords(100, -200, "x100 y64 z-200");
		assertCoords(10, -4, "10.7 -3.2");
		assertCoords(5, 0, "+5 -0");
		assertCoords(-30_000_000, 30_000_000, "-30000000 30000000");
		for (String s : new String[]{null, "", "   ", "Base", "Mine 2", "42", "1 2 3 4", "x 100", "x 1 x 2", "x 1 y 2",
				"x 1 z 2 3", "30000001 0", "1e5 2", "box 1 2", "12abc 4"}) {
			assertNull(WorldMapTools.parseCoords(s), "keine Koordinaten: " + s);
		}
	}

	private static void assertCoords(int x, int z, String query) {
		int[] c = WorldMapTools.parseCoords(query);
		assertNotNull(c, query);
		assertEquals(x, c[0], query);
		assertEquals(z, c[1], query);
	}

	@Test
	void enterInTheSearchGoesToTypedCoordinates() {
		final List<int[]> went = new ArrayList<int[]>();
		final List<Waypoint> centered = new ArrayList<Waypoint>();
		WorldMapSidebar s = new WorldMapSidebar(new WorldMapSidebar.Host() {
			public MapEngine engine() { return null; }
			public void click() { }
			public void centerOnWaypoint(Waypoint w) { centered.add(w); }
			public void editWaypoint(Waypoint w) { }
			public void newWaypoint() { }
			public void goTo(int x, int z) { went.add(new int[]{x, z}); }
		});
		s.search.setText("x: 300 z: -75");
		assertArrayEquals(new int[]{300, -75}, s.goal());
		assertTrue(s.key(UiKey.ENTER));
		assertEquals(1, went.size());
		assertArrayEquals(new int[]{300, -75}, went.get(0));
		s.search.setText("Base");
		assertNull(s.goal());
		s.key(UiKey.ENTER);
		assertEquals(1, went.size(), "Name: kein „Gehe zu“");
		assertTrue(centered.isEmpty(), "keine Treffer, nichts zu zentrieren");
	}

	@Test
	void theGoToRowCentersTheMapOnThePointAndMarksIt(@TempDir Path dir) {
		WorldMapUi ui = worldMap(dir, new int[1]);
		ui.testSidebar(true, "120 -40");
		Texts c = new Texts();
		ui.render(c, W, H, -1, -1);
		assertEquals(0, ui.testListRows(), "kein Wegpunkt heißt so");
		int[] row = c.find(I18n.tr("map.goTo", 120, -40));
		assertNotNull(row, "Zeile „Gehe zu“ über der Liste: " + c.texts);
		assertTrue(ui.mouseClicked(row[0] + 4, row[1] + 2, 0));
		// Mitte der freien Fläche links neben der Liste.
		double[] at = ui.testWorldAt((W - WorldMapSidebar.WIDTH) / 2, H / 2);
		assertEquals(120.5, at[0], 1.0);
		assertEquals(-40.5, at[1], 1.0);
		c = new Texts();
		ui.render(c, W, H, -1, -1);
		assertNotNull(c.find("120 / -40"), "der Punkt bleibt beschriftet: " + c.texts);
	}

	// --- Messen ---

	@Test
	void measuringFollowsTheCursorUntilTheClickAndEscapeClearsItBeforeClosing(@TempDir Path dir) {
		WorldMapUi ui = worldMap(dir, new int[1]);
		ui.testSidebar(false, "");
		ui.render(new Texts(), W, H, -1, -1);
		assertTrue(Double.isNaN(ui.testMeasuredDistance()));

		ui.testContextMenu(120, 100);
		double[] start = ui.testWorldAt(120, 100);
		Texts c = new Texts();
		ui.render(c, W, H, 120, 100);
		int[] item = c.find(I18n.tr("map.menu.measure"));
		assertNotNull(item, "Kontextmenü bietet „Entfernung messen“: " + c.texts);
		assertTrue(ui.mouseClicked(item[0] + 2, item[1] + 2, 0));
		assertTrue(ui.testMeasuring());
		assertEquals(0, ui.testMeasuredDistance(), 1e-9);

		// Das Ende folgt dem Zeiger; der Messwert steht an der Linie.
		c = new Texts();
		ui.render(c, W, H, 260, 170);
		double[] end = ui.testWorldAt(260, 170);
		double expected = WorldMapTools.distance(start[0], start[1], end[0], end[1]);
		assertTrue(expected > 10, "eine sichtbare Strecke: " + expected);
		assertEquals(expected, ui.testMeasuredDistance(), 1e-6);
		assertNotNull(c.find(WorldMapTools.distanceLabel(expected)), "Messwert gezeichnet: " + c.texts);
		assertNotNull(c.find(I18n.tr("map.measure.hint")), "Hinweis unten erklärt Klick und Esc");

		// Klick legt das Ende fest, danach bleibt die Messung stehen.
		assertTrue(ui.mouseClicked(260, 170, 0));
		assertFalse(ui.testMeasuring());
		end = ui.testWorldAt(260, 170);
		double fixed = WorldMapTools.distance(start[0], start[1], end[0], end[1]);
		assertEquals(fixed, ui.testMeasuredDistance(), 1e-6);
		ui.render(new Texts(), W, H, 40, 60);
		assertEquals(fixed, ui.testMeasuredDistance(), 1e-9, "folgt dem Zeiger nicht mehr");

		// Rechtsklick bietet jetzt „Messung entfernen“.
		ui.testContextMenu(200, 120);
		c = new Texts();
		ui.render(c, W, H, 200, 120);
		assertNull(c.find(I18n.tr("map.menu.measure")));
		assertNotNull(c.find(I18n.tr("map.menu.measureClear")));
		assertTrue(ui.keyPressed(0, UiKey.ESCAPE, false), "Esc schließt zuerst das Menü");
		assertFalse(Double.isNaN(ui.testMeasuredDistance()));

		assertTrue(ui.keyPressed(0, UiKey.ESCAPE, false));
		assertTrue(Double.isNaN(ui.testMeasuredDistance()), "Esc entfernt die Messung …");
		assertFalse(ui.isClosing(), "… statt die Karte zu schließen");
		assertTrue(ui.keyPressed(0, UiKey.ESCAPE, false));
		assertTrue(ui.isClosing());
	}

	@Test
	void whileMeasuringTheHintWinsOverLongCoordinates(@TempDir Path dir) {
		// Cubiq-Aussehen: Koordinaten und Hinweis teilen sich die untere Leiste (klassisch: Maps4Test).
		WorldMapUi ui = worldMap(dir, new int[1], CubiqModules.MapLook.CUBIQ);
		ui.testSidebar(false, "");
		int w = 700;
		ui.render(new Texts(), w, H, -1, -1);
		Texts c = new Texts();
		ui.render(c, w, H, 200, 150);
		String full = null;
		for (String s : c.texts) if (s.startsWith("X ")) full = s;
		assertNotNull(full, "Koordinaten unter dem Zeiger: " + c.texts);
		assertNull(c.find(I18n.tr("map.measure.hint")));

		ui.testMeasure(0, 0, 10, 10, true);
		c = new Texts();
		ui.render(c, w, H, 200, 150);
		assertNotNull(c.find(I18n.tr("map.measure.hint")), "Hinweis beim Messen: " + c.texts);
		String shorter = null;
		for (String s : c.texts) if (s.startsWith("X ")) shorter = s;
		assertNotNull(shorter, "Koordinaten bleiben, nur kürzer");
		assertTrue(shorter.length() < full.length(), shorter + " / " + full);
	}

	@Test
	void aFixedMeasurementShowsItsLengthInMetresOrKilometres(@TempDir Path dir) {
		WorldMapUi ui = worldMap(dir, new int[1]);
		ui.testSidebar(false, "");
		ui.testMeasure(0, 0, 36, 77, false);
		Texts c = new Texts();
		ui.render(c, W, H, 10, 10);
		assertEquals(85, ui.testMeasuredDistance(), 1e-9);
		assertNotNull(c.find("85 m"), c.texts.toString());
		assertNotNull(c.find(I18n.tr("map.hint")), "nicht mehr beim Messen: normaler Hinweis");
		ui.testMeasure(0, 0, 1200, 500, false);
		c = new Texts();
		ui.render(c, W, H, 10, 10);
		assertNotNull(c.find("1.3 km"), c.texts.toString());
	}

	// --- Tasten der Weltkarte ---

	@Test
	void arrowKeysPanHomeReturnsAndPlusMinusZoom(@TempDir Path dir) {
		WorldMapUi ui = worldMap(dir, new int[1]);
		ui.testSidebar(false, "");
		ui.render(new Texts(), W, H, -1, -1);
		double[] a = ui.testWorldAt(W / 2, H / 2);
		assertTrue(ui.keyPressed(0, UiKey.RIGHT, false));
		double[] b = ui.testWorldAt(W / 2, H / 2);
		assertTrue(b[0] > a[0] + 1, "rechts: Karte schiebt nach Osten");
		assertEquals(a[1], b[1], 1e-9);
		ui.render(new Texts(), W, H, -1, -1);
		assertArrayEquals(b, ui.testWorldAt(W / 2, H / 2), 1e-9, "folgt danach nicht mehr dem Spieler");
		assertTrue(ui.keyPressed(0, UiKey.UP, false));
		assertTrue(ui.testWorldAt(W / 2, H / 2)[1] < b[1], "hoch: nach Norden");
		assertTrue(ui.keyPressed(0, UiKey.HOME, false));
		double[] home = ui.testWorldAt(W / 2, H / 2);
		assertEquals(8, home[0], 1e-6, "Pos1: zurück zum Spieler");
		assertEquals(8, home[1], 1e-6);

		float px = ui.testScreenPx();
		assertTrue(ui.charTyped('+'));
		assertTrue(ui.testScreenPx() > px, "+ zoomt hinein");
		float in = ui.testScreenPx();
		assertTrue(ui.charTyped('-'));
		assertTrue(ui.testScreenPx() < in, "− zoomt heraus");
		assertFalse(ui.charTyped('q'));
	}

	/** Weltkarte mit Spieler bei 8 / 8 in der Oberwelt; {@code closed[0]} zählt das Schließen. */
	private static WorldMapUi worldMap(Path dir, final int[] closed) {
		return worldMap(dir, closed, CubiqModules.MapLook.CLASSIC);
	}

	private static WorldMapUi worldMap(Path dir, final int[] closed, CubiqModules.MapLook look) {
		CubiqModules modules = new CubiqModules();
		modules.worldMap.setEnabled(true);
		modules.minimapStyle.set(look);
		MapEngine e = new MapEngine(modules, dir, true);
		MapTest.FakePlatform p = new MapTest.FakePlatform(dir);
		e.tick(p);
		assertTrue(e.inWorld());
		return new WorldMapUi(e, new WorldMapUi.Host() {
			public void closeScreen() { closed[0]++; }
			public void playClick() { }
			public boolean isMapKey(int rawKey) { return false; }
		});
	}

	// --- Minimap: Chunk-Raster ---

	@Test
	void roundGridLinesSitOnChunkBordersAndEndAtTheCircle() {
		float[] out = new float[5 * 64];
		double px = 8.3, pz = -3.7;
		float zoom = 2f, line = 0.5f, r = 25f;
		int n = MinimapRenderer.gridLines(px, pz, zoom, line, true, r * zoom, null, 0, out);
		// x: -16, 0, 16, 32 · z: -16, 0, 16
		assertEquals(7, n);
		Set<Long> xs = new HashSet<Long>(), zs = new HashSet<Long>();
		for (int i = 0; i < n; i++) {
			float x = out[i * 5], z = out[i * 5 + 1], w = out[i * 5 + 2], h = out[i * 5 + 3];
			boolean vertical = w == line;
			double world = vertical ? px + x + line / 2 : pz + z + line / 2;
			assertEquals(0, Math.floorMod(Math.round(world), 16L), "auf einer Chunk-Grenze: " + world);
			assertTrue((vertical ? xs : zs).add(Math.round(world)), "jede Linie einmal");
			float at = vertical ? x + line / 2 : z + line / 2, half = (vertical ? h : w) / 2;
			assertEquals(r * r, at * at + half * half, 0.01f, "endet am Kreis");
			assertEquals(-half, vertical ? z : x, 1e-4f, "mittig über die Karte");
			assertEquals(Math.round(world) == 0 ? 1f : 0f, out[i * 5 + 4], "Bereichsgrenze bei 0 kräftiger");
		}
		assertEquals(new HashSet<Long>(java.util.Arrays.asList(-16L, 0L, 16L, 32L)), xs);
		assertEquals(new HashSet<Long>(java.util.Arrays.asList(-16L, 0L, 16L)), zs);
		// Bereichsgrenzen (alle 128 Blöcke) auch bei negativen Koordinaten.
		n = MinimapRenderer.gridLines(-130, 1010, 1f, 1f, true, 20f, null, 0, out);
		int strong = 0;
		for (int i = 0; i < n; i++) if (out[i * 5 + 4] > 0) strong++;
		assertEquals(2, strong, "x = -128 und z = 1024");
	}

	@Test
	void squareGridLinesRunInOnePieceEvenOverManyStrips() {
		float[] bands = new float[4 * 512], out = new float[5 * 512];
		bands[0] = -40;
		bands[1] = 40;
		bands[2] = -40;
		bands[3] = 40;
		int n = MinimapRenderer.gridLines(0.5, 0.5, 1f, 1f, false, 0f, bands, 1, out);
		assertEquals(10, n, "x und z: -32, -16, 0, 16, 32");
		for (int i = 0; i < n; i++) assertEquals(80f, Math.max(out[i * 5 + 2], out[i * 5 + 3]), 1e-4f, "ganze Kante");

		// Gedrehtes Quadrat (ohne Scissor): viele schmale Streifen, trotzdem je Linie ein Stück und alles in der Form.
		float half = 40f, angle = 0.5f;
		int strips = MapShapes.rotatedSquare(half, angle, 2f, bands);
		assertTrue(strips > 20);
		n = MinimapRenderer.gridLines(3.0, -5.0, 1f, 1f, false, 0f, bands, strips, out);
		Set<Long> xs = new HashSet<Long>(), zs = new HashSet<Long>();
		for (int i = 0; i < n; i++) {
			float x = out[i * 5], z = out[i * 5 + 1], w = out[i * 5 + 2], h = out[i * 5 + 3];
			boolean vertical = w == 1f && h != 1f;
			long world = Math.round(vertical ? 3.0 + x + 0.5 : -5.0 + z + 0.5);
			assertTrue((vertical ? xs : zs).add(world), "Linie " + world + " nur einmal");
			float cx = vertical ? x + 0.5f : x, cz = vertical ? z : z + 0.5f;
			float ex = vertical ? cx : x + w, ez = vertical ? z + h : cz;
			assertTrue(insideRotated(cx, cz, half, angle) && insideRotated(ex, ez, half, angle),
					"Linie " + world + " bleibt im gedrehten Quadrat");
		}
		assertTrue(xs.size() >= 4 && zs.size() >= 4, xs + " / " + zs);
	}

	private static boolean insideRotated(float x, float y, float half, float angle) {
		double cos = Math.cos(-angle), sin = Math.sin(-angle);
		double u = x * cos - y * sin, v = x * sin + y * cos;
		return Math.abs(u) <= half + 1e-3 && Math.abs(v) <= half + 1e-3;
	}

	@Test
	void gridOnlyWhenChunksAreFarEnoughApart() {
		assertTrue(MinimapRenderer.showGrid(0.25f, 2.0), "8 Bildschirmpixel je Chunk");
		assertFalse(MinimapRenderer.showGrid(0.2f, 2.0));
		assertTrue(MinimapRenderer.showGrid(1f, 1.0));
		CubiqModules m = new CubiqModules();
		assertFalse(m.minimapGrid.get(), "Raster ist aus, bis man es einschaltet");
		assertTrue(m.minimapViewCone.get(), "Blickkegel ist an");
	}

	// --- Minimap: Blickkegel ---

	@Test
	void viewConeFansOutInFrontOfThePlayerOnly() {
		assertEquals(1f, MapSprites.coneAlpha(0f, -0.2f), 1e-6f, "kräftig vor der Spitze");
		assertEquals(0f, MapSprites.coneAlpha(0f, 0.3f), "nichts hinter dem Spieler");
		assertEquals(0f, MapSprites.coneAlpha(0.9f, -0.1f), "nichts neben dem Spieler");
		assertTrue(MapSprites.coneAlpha(0f, -0.99f) < 0.05f, "läuft am Ende weich aus");
		assertEquals(0f, MapSprites.coneAlpha(0f, -1f));
		float edge = (float) Math.sin(MapSprites.CONE_HALF) * 0.3f, depth = (float) Math.cos(MapSprites.CONE_HALF) * 0.3f;
		assertEquals(0.5f, MapSprites.coneAlpha(edge, -depth), 0.02f, "weiche Seitenkante");
		float prev = 2f;
		for (float d = 0.3f; d < 1f; d += 0.05f) {
			float a = MapSprites.coneAlpha(0f, -d);
			assertTrue(a <= prev, "wird nach vorn schwächer");
			assertEquals(MapSprites.coneAlpha(0.2f, -d), MapSprites.coneAlpha(-0.2f, -d), 1e-6f, "symmetrisch");
			prev = a;
		}
		int px = 32;
		assertTrue(MapSprites.conePixel(px / 2, 6, px) >>> 24 > 0x80, "vorn deckend");
		assertEquals(0xFFFFFF, MapSprites.conePixel(px / 2, 6, px) & 0xFFFFFF, "weiß, gefärbt wird beim Zeichnen");
		assertEquals(0, MapSprites.conePixel(px / 2, px - 6, px), "hinten leer");
		assertEquals(0, MapSprites.conePixel(1, px / 2 - 2, px), "seitlich leer");
	}

	@Test
	void coneLengthAndTintFollowTheMapSizeAndTheme() {
		assertEquals(14f, MinimapRenderer.coneLength(10f), 0f);
		assertEquals(0.55f * 50f, MinimapRenderer.coneLength(50f), 1e-4f);
		assertEquals(44f, MinimapRenderer.coneLength(200f), 0f);
		for (String mode : new String[]{"dark", "oled", "light"}) {
			Theme t = Theme.of(mode, "cubiq", null);
			assertEquals(0x78, MinimapRenderer.coneTint(t) >>> 24, mode);
		}
	}

	// --- Farben ---

	@Test
	void ownMarkerAndCompassStayReadableInEveryTheme() {
		for (String mode : new String[]{"dark", "oled", "light"}) {
			Theme t = Theme.of(mode, "cubiq", null);
			assertEquals(t.light ? t.accentHover : t.accentText, MapStyle.marker(t), mode);
			assertTrue(contrast(MapStyle.marker(t), MapStyle.VOID) >= 3.0, mode + ": eigener Pfeil auf dunkler Karte");
			assertTrue(contrast(MapStyle.badgeText(t, true), MapStyle.badgeFill(t, true)) >= 3.0, mode + ": N");
			assertTrue(contrast(MapStyle.badgeText(t, false), over(MapStyle.badgeFill(t, false), MapStyle.VOID)) >= 4.5,
					mode + ": O/S/W");
			assertEquals(t.accent, MapStyle.badgeFill(t, true), "Norden in der Akzentfarbe");
			assertEquals(t.border, MapStyle.badgeEdge(t, false));
		}
		int edge = MinimapRenderer.edgeOf(0xFF3BA0FF);
		assertTrue(lum(edge) < lum(0xFF3BA0FF) * 0.5, "Kante der Rand-Kästchen deutlich dunkler");
		assertEquals(0xFF, edge >>> 24);
	}

	private static int over(int argb, int below) {
		float a = (argb >>> 24) / 255f;
		int r = Math.round(((argb >> 16) & 0xFF) * a + ((below >> 16) & 0xFF) * (1 - a));
		int g = Math.round(((argb >> 8) & 0xFF) * a + ((below >> 8) & 0xFF) * (1 - a));
		int b = Math.round((argb & 0xFF) * a + (below & 0xFF) * (1 - a));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static double contrast(int a, int b) {
		double la = lum(a), lb = lum(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double lum(int argb) {
		double[] c = new double[3];
		for (int k = 0; k < 3; k++) {
			double v = ((argb >> (16 - 8 * k)) & 0xFF) / 255.0;
			c[k] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	/** Leinwand, die Texte mit Position mitschreibt (jedes Zeichen 6 Pixel breit). */
	static final class Texts implements Canvas {
		final List<String> texts = new ArrayList<String>();
		final List<int[]> at = new ArrayList<int[]>();

		int[] find(String text) {
			int i = texts.indexOf(text);
			return i < 0 ? null : at.get(i);
		}

		@Override public void text(String text, int x, int y, int argb, boolean shadow) {
			texts.add(text);
			at.add(new int[]{x, y});
		}

		@Override public void fill(int x1, int y1, int x2, int y2, int argb) { }
		@Override public int textWidth(String text) { return text.length() * 6; }
		@Override public int lineHeight() { return 9; }
		@Override public String clip(String text, int maxWidth) { return text.length() * 6 <= maxWidth ? text : text.substring(0, Math.max(0, maxWidth / 6)); }
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
