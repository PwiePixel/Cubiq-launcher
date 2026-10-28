package dev.pwiepixel.cubiqclient.core.map;

import dev.pwiepixel.cubiqclient.core.chat.ChatWaypoints;
import dev.pwiepixel.cubiqclient.core.format.HudFormat;
import dev.pwiepixel.cubiqclient.core.halloween.Halloween;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import dev.pwiepixel.cubiqclient.core.waypoint.Waypoint;
import dev.pwiepixel.cubiqclient.core.waypoint.WaypointShare;
import dev.pwiepixel.cubiqclient.core.waypoint.WaypointStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Karten 4: klassisches Aussehen von Minimap und Weltkarte, geteilte Wegpunkte auf der Weltkarte speichern und
 * Wegpunkte im Chat teilen.
 */
class Maps4Test {
	private static final int W = 1000, H = 300;

	@BeforeEach
	void calm() {
		Halloween.force(Boolean.FALSE);
		Theme.set(Theme.of("dark", "cubiq", null));
		I18n.use("en");
	}

	@AfterEach
	void reset() {
		Halloween.force(null);
		Theme.set(Theme.of("dark", "cubiq", null));
		I18n.use("en");
	}

	/** Weltkarte mit Spieler bei 8 / 64 / 8 in der Oberwelt. */
	private static final class Map {
		final MapEngine e;
		final MapTest.FakePlatform p;
		final WorldMapUi ui;

		Map(Path dir, CubiqModules.MapLook look) {
			CubiqModules modules = new CubiqModules();
			modules.worldMap.setEnabled(true);
			modules.minimapStyle.set(look);
			e = new MapEngine(modules, dir, true);
			p = new MapTest.FakePlatform(dir);
			e.tick(p);
			assertTrue(e.inWorld());
			ui = new WorldMapUi(e, new WorldMapUi.Host() {
				public void closeScreen() { }
				public void playClick() { }
				public boolean isMapKey(int rawKey) { return false; }
			});
			ui.testSidebar(false, "");
			ui.render(new Maps3Test.Texts(), W, H, -1, -1);
		}

		Maps3Test.Texts render(int mx, int my) {
			Maps3Test.Texts c = new Maps3Test.Texts();
			ui.render(c, W, H, mx, my);
			return c;
		}

		/** Bildschirmstelle eines Weltpunkts (über die Umrechnung der Karte selbst). */
		int[] screen(double wx, double wz) {
			double[] o = ui.testWorldAt(0, 0), d = ui.testWorldAt(W, H);
			return new int[]{(int) Math.round((wx - o[0]) / (d[0] - o[0]) * W), (int) Math.round((wz - o[1]) / (d[1] - o[1]) * H)};
		}
	}

	private static String coordsLine(Maps3Test.Texts c) {
		for (String s : c.texts) if (s.startsWith("X ")) return s;
		return null;
	}

	// --- Weltkarte: Aussehen ---

	@Test
	void classicWorldMapHasNoBarsAndShowsCoordinatesTopLeft(@TempDir Path dir) {
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		Maps3Test.Texts c = m.render(300, 150);
		assertNull(c.find(I18n.tr("map.title")), "kein Titel – die Karte reicht bis an den Rand: " + c.texts);
		String coords = coordsLine(c);
		assertNotNull(coords, "Koordinaten unter dem Zeiger: " + c.texts);
		int[] at = c.find(coords);
		assertTrue(at[0] < 20 && at[1] < 30, "oben links: " + at[0] + "/" + at[1]);
		int[] dim = c.find(I18n.tr("map.dim.overworld"));
		assertNotNull(dim, "Dimension darüber: " + c.texts);
		assertTrue(dim[1] < at[1]);
		int[] hint = c.find(I18n.tr("map.hint"));
		assertNotNull(hint, "Bedienhilfe unten: " + c.texts);
		assertTrue(hint[1] > H - 20);

		// Beim Messen: Hinweis und dieselben (vollen) Koordinaten zugleich – sie stehen nicht mehr in einer Zeile.
		m.ui.testMeasure(0, 0, 10, 10, true);
		c = m.render(300, 150);
		assertNotNull(c.find(I18n.tr("map.measure.hint")), "Hinweis beim Messen: " + c.texts);
		assertEquals(coords, coordsLine(c));
	}

	@Test
	void cubiqWorldMapKeepsItsBars(@TempDir Path dir) {
		Map m = new Map(dir, CubiqModules.MapLook.CUBIQ);
		Maps3Test.Texts c = m.render(300, 150);
		assertNotNull(c.find(I18n.tr("map.title")), "Titel in der oberen Leiste: " + c.texts);
		int[] at = c.find(coordsLine(c));
		assertTrue(at[1] > H - WorldMapUi.BAR_H, "Koordinaten in der unteren Leiste");
	}

	@Test
	void classicClicksReachTheMapRightUpToTheEdges(@TempDir Path dir) {
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		// Ganz unten (wo sonst die Leiste ist) öffnet der Rechtsklick das Kontextmenü.
		m.ui.testContextMenu(300, H - 5);
		Maps3Test.Texts c = m.render(300, H - 5);
		assertNotNull(c.find(I18n.tr("map.menu.create")), "Kontextmenü: " + c.texts);
	}

	// --- Im Chat teilen ---

	@Test
	void shareInChatPutsTheSpotIntoTheChatLine(@TempDir Path dir) {
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		m.ui.testContextMenu(300, 150);
		double[] at = m.ui.testWorldAt(300, 150);
		Maps3Test.Texts c = m.render(300, 150);
		int[] item = c.find(I18n.tr("map.menu.shareChat"));
		assertNotNull(item, "„Im Chat teilen“ im Kontextmenü: " + c.texts);
		assertTrue(m.ui.mouseClicked(item[0] + 2, item[1] + 2, 0));
		assertNotNull(m.p.chat, "Chatzeile geöffnet");
		ChatWaypoints.Shared s = ChatWaypoints.parse(m.p.chat);
		assertNotNull(s, m.p.chat);
		assertEquals((int) Math.floor(at[0]), s.x);
		assertEquals((int) Math.floor(at[1]), s.z);
		assertEquals(I18n.tr("waypoint.share.spot"), s.name);
		assertEquals("minecraft:overworld", s.dimension);
	}

	@Test
	void shareInChatKeepsNameHeightAndDimensionOfAWaypoint(@TempDir Path dir) {
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		m.e.addWaypoint("Base", 20, 71, 12, 0xFF5555);
		int[] sp = m.screen(20.5, 12.5);
		m.ui.testContextMenu(sp[0], sp[1]);
		Maps3Test.Texts c = m.render(sp[0], sp[1]);
		assertNotNull(c.find(I18n.tr("map.menu.edit")), "Menü des Wegpunkts: " + c.texts);
		int[] item = c.find(I18n.tr("map.menu.shareChat"));
		assertTrue(m.ui.mouseClicked(item[0] + 2, item[1] + 2, 0));
		assertEquals("xaero-waypoint:Base:B:20:71:12:12:false:0:Internal-overworld-waypoints", m.p.chat);
	}

	// --- Geteilten Punkt speichern ---

	@Test
	void aSharedPointOnTheMapCanBeSavedAsWaypoint(@TempDir Path dir) {
		WorldMapUi.requestFocus("Shop", 40, 72, -20, 0x5555FF);
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		int[] sp = m.screen(40.5, -19.5);
		m.ui.testContextMenu(sp[0], sp[1]);
		Maps3Test.Texts c = m.render(sp[0], sp[1]);
		int[] save = c.find(I18n.tr("map.menu.saveFocus"));
		assertNotNull(save, "„Als Wegpunkt speichern“: " + c.texts);
		assertNotNull(c.find(I18n.tr("map.menu.clearFocus")));
		assertNull(c.find(I18n.tr("map.menu.create")), "statt „Wegpunkt hier anlegen“");
		assertTrue(m.ui.mouseClicked(save[0] + 2, save[1] + 2, 0));
		// Dialog mit Name, Farbe und Höhe des geteilten Punkts – Enter speichert.
		assertTrue(m.ui.keyPressed(0, UiKey.ENTER, false));
		List<Waypoint> all = m.e.allWaypoints();
		assertEquals(1, all.size());
		Waypoint w = all.get(0);
		assertEquals("Shop", w.name);
		assertEquals(40, w.x);
		assertEquals(72, w.y);
		assertEquals(-20, w.z);
		assertEquals(0x5555FF, w.color);
		// Die Markierung hat ausgedient: Rechtsklick dort trifft jetzt den Wegpunkt.
		m.ui.testContextMenu(sp[0], sp[1]);
		c = m.render(sp[0], sp[1]);
		assertNull(c.find(I18n.tr("map.menu.saveFocus")));
		assertNotNull(c.find(I18n.tr("map.menu.edit")));
	}

	@Test
	void theMarkerOfASharedPointCanBeRemoved(@TempDir Path dir) {
		WorldMapUi.requestFocus("Shop", 40, 72, -20, 0x5555FF);
		Map m = new Map(dir, CubiqModules.MapLook.CLASSIC);
		int[] sp = m.screen(40.5, -19.5);
		assertNotNull(m.render(sp[0] + 200, sp[1]).find("Shop"), "Name am markierten Punkt");
		m.ui.testContextMenu(sp[0], sp[1]);
		int[] clear = m.render(sp[0], sp[1]).find(I18n.tr("map.menu.clearFocus"));
		assertTrue(m.ui.mouseClicked(clear[0] + 2, clear[1] + 2, 0));
		assertNull(m.render(sp[0] + 200, sp[1]).find("Shop"), "Markierung entfernt");
		assertTrue(m.e.allWaypoints().isEmpty());
	}

	@Test
	void savingASharedWaypointUsesItsDimension(@TempDir Path dir) {
		WaypointStore store = new WaypointStore(dir.resolve("wp.json"));
		String key = WaypointStore.serverKey("play.example.net");
		assertEquals(WaypointShare.Result.ADDED,
				WaypointShare.save(store, key, "minecraft:overworld", "Hub", 10, 70, 20, "minecraft:the_nether", 0xFF5555));
		Waypoint w = store.all(key).get(0);
		assertEquals("minecraft:the_nether", w.dimension);
		assertEquals(0xFF5555, w.color);
		assertEquals(WaypointShare.Result.ALREADY,
				WaypointShare.save(store, key, "minecraft:overworld", "Hub", 10, 70, 20, "minecraft:the_nether", 0xFF5555));
		// Gleicher Name und Ort in einer anderen Dimension ist ein eigener Wegpunkt.
		assertEquals(WaypointShare.Result.ADDED,
				WaypointShare.save(store, key, "minecraft:overworld", "Hub", 10, 70, 20, "minecraft:overworld", 0xFF5555));
		// Alte Versionen schreiben Dimensionen als Nummer.
		WaypointShare.save(store, "mp:old", "dim0", "Hub", 1, 2, 3, "minecraft:the_nether", 0);
		assertEquals("dim-1", store.all("mp:old").get(0).dimension);
		// Ohne Angabe: die Dimension des Spielers.
		WaypointShare.save(store, "mp:end", "minecraft:the_end", "A", 1, 2, 3, null, 0);
		assertEquals("minecraft:the_end", store.all("mp:end").get(0).dimension);
		assertEquals(WaypointShare.Result.NO_WORLD, WaypointShare.save(store, "", "minecraft:overworld", "A", 1, 2, 3, null, 0));
	}

	// --- Minimap: Aussehen ---

	@Test
	void classicMinimapShowsLettersOnTheEdgeWaypointInitialsAndPlainCoordinates(@TempDir Path dir) {
		CubiqModules modules = new CubiqModules();
		modules.minimap.setEnabled(true);
		assertEquals(CubiqModules.MapLook.CLASSIC, modules.minimapStyle.get(), "klassisch ist der Standard");
		assertEquals(CubiqModules.MapShape.SQUARE, modules.minimapShape.get(), "eckig ist der Standard");
		MapEngine e = new MapEngine(modules, dir, true);
		MapTest.FakePlatform p = new MapTest.FakePlatform(dir);
		for (int i = 0; i < 5; i++) e.tick(p);
		e.addWaypoint("Home", 20, 64, 8, 0x55CCFF);
		e.addWaypoint("far away", 5000, 64, 8, 0xF2C94C);
		MinimapRenderer r = new MinimapRenderer();
		Maps3Test.Texts c = new Maps3Test.Texts();
		r.draw(c, e, false, 2, null);
		for (String dirKey : new String[]{"map.north", "map.east", "map.south", "map.west"}) {
			assertNotNull(c.find(I18n.tr(dirKey)), dirKey + ": " + c.texts);
		}
		assertNotNull(c.find("H"), "Kästchen mit Anfangsbuchstaben auch innerhalb der Karte: " + c.texts);
		assertNotNull(c.find("F"), "und am Rand: " + c.texts);
		assertNotNull(c.find("8, 64, 8"), "Koordinaten „x, y, z“: " + c.texts);

		// Cubiq-Aussehen: Koordinaten mit Achsen, Wegpunkte in der Karte als Raute (kein Buchstabe).
		modules.minimapStyle.set(CubiqModules.MapLook.CUBIQ);
		c = new Maps3Test.Texts();
		r.draw(c, e, false, 2, null);
		assertNotNull(c.find(HudFormat.coords(8, 64, 8)), c.texts.toString());
		assertNull(c.find("H"));
		assertNotNull(c.find("F"), "Rand-Kästchen gibt es in beiden");
	}

	@Test
	void classicFrameIsThinAndTheSameInEveryTheme() {
		CubiqModules m = new CubiqModules();
		assertEquals(3, MinimapRenderer.pad(m));
		// Ring: dunkle Linie außen, grauer Rand, dunkle Linie zur Karte – und nichts darüber hinaus.
		float r = 56f;
		assertEquals(0, MapSprites.classicRingColor(r + 1.5f, r + 1.5f, 0, r));
		assertEquals(MapStyle.CLASSIC_OUTLINE, MapSprites.classicRingColor(r + 0.5f, r + 0.5f, 0, r));
		int inner = MapSprites.classicRingColor(r - MapSprites.ringIn(r) - 0.5f, 0, r - MapSprites.ringIn(r) - 0.5f, r);
		assertEquals(MapStyle.CLASSIC_INNER, inner);
		assertEquals(0, MapSprites.classicRingColor(r - MapSprites.ringIn(r) - 1.5f, 0, 1, r), "die Karte bleibt frei");
		// Licht von links oben: oben links heller als unten rechts.
		float d = r - 1f;
		int topLeft = MapSprites.classicRingColor(d, -d * 0.7071f, -d * 0.7071f, r);
		int bottomRight = MapSprites.classicRingColor(d, d * 0.7071f, d * 0.7071f, r);
		assertTrue((topLeft & 0xFF) > (bottomRight & 0xFF), Integer.toHexString(topLeft) + " / " + Integer.toHexString(bottomRight));
	}
}
