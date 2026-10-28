package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aktionen im Format 2 (Streicheln/Füttern, 2026-10-28): eigene Liste {@code actions} neben {@code animations}. Gültige
 * Aktionen ändern das Prüf-Ergebnis nicht (keine Warnung), jedes Problem darin gibt genau eine Warnung und lässt das
 * Modell gültig; {@code pet}/{@code eat} in {@code animations} bleiben ein Fehler (wie bei älteren Clients).
 */
class PetActionsFormatTest {
	/**
	 * Eingefrorene Kopie des generierten {@code fox_kit.json} der Website (Stand c1575f0, noch ohne Aktionen) plus ein
	 * handgeschriebener, gültiger Block {@code actions} (pet 2200 ms, eat 2600 ms).
	 */
	static String fox() throws IOException {
		try (InputStream in = PetActionsFormatTest.class.getResourceAsStream("/cosmetic-v2/fox_kit.json")) {
			assertNotNull(in, "fox_kit.json");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	/** Derselbe Fuchs ohne den Block {@code actions} (so liefert ihn die Website heute aus). */
	static String foxWithout() throws IOException {
		JsonObject m = new JsonParser().parse(fox()).getAsJsonObject();
		m.remove("actions");
		return m.toString();
	}

	static CosmeticV2 foxModel() throws IOException {
		CosmeticV2.Check c = check(fox());
		assertTrue(c.ok(), c.errors.toString());
		return c.model;
	}

	static CosmeticV2.Check check(String json) {
		return CosmeticV2.check(json, -1, -1, -1, -1);
	}

	/** Kleiner Begleiter (oder Hut bei {@code slot} = hat) mit idle und dem Block {@code actions} (roh, null = keiner). */
	static String model(String slot, String actions) {
		boolean pet = CosmeticV2.SLOT_PET.equals(slot);
		return "{\"format\":2,\"id\":\"test_actions\",\"name\":\"Test\",\"slot\":\"" + slot + "\",\"attach\":\""
				+ (pet ? "ground" : "head") + "\","
				+ "\"texture\":{\"file\":\"t.png\",\"width\":16,\"height\":16,\"scale\":1},"
				+ "\"bones\":[{\"id\":\"body\",\"pivot\":[0,1,0]},{\"id\":\"head\",\"parent\":\"body\",\"pivot\":[0,6,0]}],"
				+ "\"cubes\":[{\"id\":\"b\",\"bone\":\"body\",\"from\":[-2,0,-2],\"to\":[2,6,2],\"faces\":{\"south\":{\"uv\":[0,0,4,6]}}}],"
				+ "\"animations\":[{\"id\":\"idle\",\"driver\":\"idle\",\"lengthMs\":1000,\"tracks\":["
				+ "{\"bone\":\"body\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]},{\"t\":1000,\"v\":[0,10,0]}]}]}]"
				+ (actions == null ? "" : ",\"actions\":" + actions) + "}";
	}

	static final String TRACKS = "[{\"bone\":\"head\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]},"
			+ "{\"t\":500,\"v\":[20,0,0]},{\"t\":1000,\"v\":[0,0,0]}]}]";

	/** Eine Aktion; {@code extra} wird vor {@code tracks} eingefügt (z. B. {@code ,"loop":true}). */
	static String action(String id, String driver, int lengthMs, String extra, String tracks) {
		return "{\"id\":\"" + id + "\",\"driver\":\"" + driver + "\",\"lengthMs\":" + lengthMs + extra + ",\"tracks\":" + tracks
				+ "}";
	}

	static String pet(String extra) {
		return action("pet", "pet", 1000, extra, TRACKS);
	}

	static String eat() {
		return action("eat", "eat", 1200, "", TRACKS);
	}

	// --- der Fuchs mit Aktionen -------------------------------------------------------------------------------

	@Test
	void foxKitActionsAreReadWithoutAnyWarning() throws IOException {
		CosmeticV2.Check c = check(fox());
		assertTrue(c.ok(), c.errors.toString());
		assertEquals(Collections.<String>emptyList(), c.warnings, "gültige Aktionen melden nichts");
		CosmeticV2 m = c.model;
		assertEquals(10, m.animations.size(), "animations unverändert (ältere Clients sehen genau diese)");
		assertEquals(2, m.actions.size());
		CosmeticV2.Animation pet = m.action(CosmeticV2.DRIVER_PET);
		CosmeticV2.Animation eat = m.action(CosmeticV2.DRIVER_EAT);
		assertNotNull(pet);
		assertNotNull(eat);
		assertEquals("pet", pet.id);
		assertEquals(CosmeticV2.DRIVER_PET, pet.driver);
		assertEquals(2200, pet.lengthMs);
		assertFalse(pet.loop);
		assertEquals(0, pet.offsetMs);
		assertEquals(6, pet.tracks.length);
		assertEquals(CosmeticV2.SMOOTH, pet.tracks[0].interpolation, "optionale Interpolation wie bei animations");
		assertEquals(2600, eat.lengthMs);
		assertTrue(m.hasDriver(CosmeticV2.DRIVER_PET));
		assertTrue(m.hasDriver(CosmeticV2.DRIVER_EAT));
		assertNull(m.action(CosmeticV2.DRIVER_IDLE), "Aktionen nur über ihre eigenen Treiber");

		// Treiber/Zustände: pet/eat hinter den Bewegungs-Treibern, Reihenfolge wie Website und Launcher
		assertEquals(10, CosmeticV2.driverIndex("pet"));
		assertEquals(11, CosmeticV2.driverIndex("eat"));
		assertEquals(-1, CosmeticV2.driverIndex("dance"));
		assertEquals(9, CosmeticV2.driverIndex("handsup"), "bestehende Indizes bleiben");
		assertEquals(CosmeticV2.DRIVER_PET, Pet.driver(Pet.PET));
		assertEquals(CosmeticV2.DRIVER_EAT, Pet.driver(Pet.EAT));
		assertEquals("pet", Pet.stateNameOf(Pet.PET));
		assertEquals("eat", Pet.stateNameOf(Pet.EAT));
		assertTrue(Pet.fromStateStart(Pet.PET) && Pet.fromStateStart(Pet.EAT) && Pet.fromStateStart(Pet.GRAB));
		assertFalse(Pet.fromStateStart(Pet.FLEE) || Pet.fromStateStart(Pet.IDLE));
		assertTrue(CosmeticV2.oneShot(CosmeticV2.DRIVER_GRAB));
		assertTrue(CosmeticV2.oneShot(CosmeticV2.DRIVER_PET));
		assertTrue(CosmeticV2.oneShot(CosmeticV2.DRIVER_EAT));
		assertFalse(CosmeticV2.oneShot(CosmeticV2.DRIVER_IDLE));
		assertFalse(CosmeticV2.oneShot(CosmeticV2.DRIVER_FLEE));
		assertFalse(CosmeticV2.oneShot(CosmeticV2.DRIVER_HANDSUP));

		// Fähigkeiten und Längen für die Simulation
		int heist = Pet.CAN_SNEAK | Pet.CAN_GRAB | Pet.CAN_FLEE | Pet.CAN_HANDSUP;
		assertEquals(heist | Pet.CAN_PET | Pet.CAN_EAT, Pet.abilitiesOf(m));
		assertEquals(2200, Pet.actionMs(m, CosmeticV2.DRIVER_PET));
		assertEquals(2600, Pet.actionMs(m, CosmeticV2.DRIVER_EAT));
	}

	@Test
	void validActionsLeaveTheCheckExactlyAsWithoutThem() throws IOException {
		CosmeticV2.Check with = check(fox());
		CosmeticV2.Check without = check(foxWithout());
		assertTrue(without.ok(), without.errors.toString());
		assertEquals(without.errors, with.errors);
		assertEquals(without.warnings, with.warnings);
		assertEquals(without.model.animations.size(), with.model.animations.size());
		assertEquals(without.model.bones.size(), with.model.bones.size());
		assertEquals(without.model.cubes.size(), with.model.cubes.size());
		assertEquals(without.model.faceCount, with.model.faceCount);
		// ohne Block: keine Aktion, keine Fähigkeit, Länge 0 (Begleiter steht dann still, Herzchen statt Animation)
		assertTrue(without.model.actions.isEmpty());
		assertFalse(without.model.hasDriver(CosmeticV2.DRIVER_PET));
		assertEquals(0, Pet.actionMs(without.model, CosmeticV2.DRIVER_EAT));
		assertEquals(0, Pet.abilitiesOf(without.model) & (Pet.CAN_PET | Pet.CAN_EAT));
		assertEquals(0, Pet.actionMs(null, CosmeticV2.DRIVER_PET));
	}

	// --- Probleme in actions: genau eine Warnung, Modell bleibt gültig -----------------------------------------

	/** Prüft {@code json}: gültig, genau eine Warnung; liefert das Modell. */
	private static CosmeticV2 oneWarning(String json, String what) {
		CosmeticV2.Check c = check(json);
		assertTrue(c.ok(), what + ": " + c.errors);
		assertEquals(1, c.warnings.size(), what + ": " + c.warnings);
		assertTrue(c.warnings.get(0).endsWith("ignoriert"), c.warnings.get(0));
		return c.model;
	}

	@Test
	void aBrokenBlockIsOneWarningAndIgnored() {
		assertTrue(oneWarning(model("pet", "{}"), "kein Array").actions.isEmpty());
		assertTrue(oneWarning(model("pet", "null"), "null").actions.isEmpty());
		assertTrue(oneWarning(model("pet", "\"pet\""), "Text").actions.isEmpty());
		// nur Begleiter am Boden: beim Hut fällt der ganze Block weg, der Hut bleibt gültig
		assertTrue(oneWarning(model("hat", "[" + pet("") + "]"), "slot hat").actions.isEmpty());
		// mehr als 16 Einträge → der ganze Block
		StringBuilder many = new StringBuilder("[");
		for (int i = 0; i < CosmeticV2.MAX_ACTIONS + 1; i++) many.append(i == 0 ? "" : ",").append(pet(""));
		assertTrue(oneWarning(model("pet", many + "]"), "17 Einträge").actions.isEmpty());
		// 16 sind erlaubt (doppelte Treiber: je eine Warnung, aber kein Block-Abbruch)
		StringBuilder sixteen = new StringBuilder("[").append(pet("")).append(',').append(eat());
		for (int i = 2; i < CosmeticV2.MAX_ACTIONS; i++) sixteen.append(',').append(action("x" + i, "dance", 1000, "", TRACKS));
		CosmeticV2.Check c = check(model("pet", sixteen + "]"));
		assertTrue(c.ok());
		assertEquals(CosmeticV2.MAX_ACTIONS - 2, c.warnings.size(), "je unbekanntem Treiber eine Warnung");
		assertEquals(2, c.model.actions.size());
	}

	@Test
	void eachProblemInAnEntryIsOneWarningAndOnlyThatEntryIsIgnored() {
		String[][] cases = {
				{ "Objekt erwartet", "1" },
				{ "unbekannter Treiber", action("dance", "dance", 1000, "", TRACKS) },
				{ "ohne Treiber", "{\"id\":\"pet\",\"lengthMs\":1000,\"tracks\":" + TRACKS + "}" },
				{ "ungültige id", action("Pet!", "pet", 1000, "", TRACKS) },
				{ "ohne id", "{\"driver\":\"pet\",\"lengthMs\":1000,\"tracks\":" + TRACKS + "}" },
				{ "zu kurz", action("pet", "pet", 100, "", "[{\"bone\":\"head\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") },
				{ "zu lang", action("pet", "pet", 20000, "", TRACKS) },
				{ "nicht ganzzahlig", "{\"id\":\"pet\",\"driver\":\"pet\",\"lengthMs\":1000.5,\"tracks\":" + TRACKS + "}" },
				{ "loop true", pet(",\"loop\":true") },
				{ "loop kein Wahrheitswert", pet(",\"loop\":\"no\"") },
				{ "offsetMs", pet(",\"offsetMs\":0") },
				{ "unbekannter Knochen", action("pet", "pet", 1000, "",
						"[{\"bone\":\"tail\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") },
				{ "unbekannter Kanal", action("pet", "pet", 1000, "",
						"[{\"bone\":\"head\",\"channel\":\"spin\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") },
				{ "Schlüssel ungeordnet", action("pet", "pet", 1000, "",
						"[{\"bone\":\"head\",\"channel\":\"rotation\",\"keys\":[{\"t\":500,\"v\":[0,0,0]},{\"t\":100,\"v\":[1,0,0]}]}]") },
				{ "t hinter lengthMs", action("pet", "pet", 1000, "",
						"[{\"bone\":\"head\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]},{\"t\":1001,\"v\":[1,0,0]}]}]") },
				{ "Größe 0", action("pet", "pet", 1000, "",
						"[{\"bone\":\"head\",\"channel\":\"scale\",\"keys\":[{\"t\":0,\"v\":[1,1,1]},{\"t\":500,\"v\":[0,1,1]}]}]") },
				{ "unbekannte Interpolation", action("pet", "pet", 1000, "",
						"[{\"bone\":\"head\",\"channel\":\"rotation\",\"interpolation\":\"bouncy\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") },
				{ "keine Spuren", action("pet", "pet", 1000, "", "[]") },
				{ "keine Schlüssel", action("pet", "pet", 1000, "", "[{\"bone\":\"head\",\"channel\":\"rotation\",\"keys\":[]}]") },
				{ "mehrere Probleme", action("Pet!", "pet", 99, ",\"loop\":true,\"offsetMs\":5", "[]") },
		};
		for (String[] k : cases) {
			// das kaputte Stück neben einer gültigen eat-Aktion: nur das kaputte fällt weg
			CosmeticV2 m = oneWarning(model("pet", "[" + k[1] + "," + eat() + "]"), k[0]);
			assertNull(m.action(CosmeticV2.DRIVER_PET), k[0]);
			assertNotNull(m.action(CosmeticV2.DRIVER_EAT), k[0] + ": die gültige Aktion bleibt");
			assertEquals(1, m.animations.size(), k[0]);
		}
		// mehrere Probleme eines Eintrags (id, lengthMs, loop, offsetMs, Spuren): trotzdem genau eine Warnung, mit der Anzahl
		// der weiteren
		CosmeticV2.Check c = check(model("pet", "[" + action("Pet!", "pet", 99, ",\"loop\":true,\"offsetMs\":5", "[]") + "]"));
		assertEquals("actions[0]: ungültige id (+4) – ignoriert", c.warnings.get(0));
	}

	@Test
	void secondEntryForTheSameDriverIsIgnoredTheFirstWins() {
		String second = action("pet_two", "pet", 2000, "", TRACKS);
		CosmeticV2 m = oneWarning(model("pet", "[" + pet("") + "," + second + "]"), "doppelt");
		assertEquals(1, m.actions.size());
		assertEquals("pet", m.action(CosmeticV2.DRIVER_PET).id);
		assertEquals(1000, m.action(CosmeticV2.DRIVER_PET).lengthMs);
		// ist der erste ungültig, zählt der nächste gültige (je Treiber der erste gültige)
		CosmeticV2 n = oneWarning(model("pet", "[" + pet(",\"loop\":true") + "," + second + "]"), "erster kaputt");
		assertEquals("pet_two", n.action(CosmeticV2.DRIVER_PET).id);
	}

	/** Spur mit einer Skalierung {@code s} (roh, so wie im JSON) auf dem Kopf. */
	static String scaleTrack(String s) {
		return "[{\"bone\":\"head\",\"channel\":\"scale\",\"keys\":[{\"t\":0,\"v\":[1,1,1]},{\"t\":500,\"v\":[" + s + ",1,1]}]}]";
	}

	@Test
	void scaleLimitsAreCheckedOnTheJsonNumberNotOnTheFloat() {
		// 4,0000001 ist zu groß (als float wäre es genau 4), 1e-46 ist größer als 0 (als float wäre es 0) – wie Website und
		// Launcher. In actions: eine Warnung, nur der Eintrag fällt weg.
		for (String bad : new String[] { "4.0000001", "0", "-1e-46", "-1", "1e300" }) {
			CosmeticV2 m = oneWarning(model("pet", "[" + action("pet", "pet", 1000, "", scaleTrack(bad)) + "," + eat() + "]"), bad);
			assertNull(m.action(CosmeticV2.DRIVER_PET), bad);
			assertNotNull(m.action(CosmeticV2.DRIVER_EAT), bad);
		}
		for (String ok : new String[] { "1e-46", "4", "4.0", "0.05" }) {
			CosmeticV2.Check c = check(model("pet", "[" + action("pet", "pet", 1000, "", scaleTrack(ok)) + "]"));
			assertTrue(c.ok(), ok + ": " + c.errors);
			assertEquals(Collections.<String>emptyList(), c.warnings, ok);
			assertNotNull(c.model.action(CosmeticV2.DRIVER_PET), ok);
		}
		// animations: dieselbe Grenze, dort bleibt es ein Fehler
		String plain = model("pet", null);
		String rotation = "[0,10,0]}]}]";
		assertTrue(plain.contains(rotation));
		for (String bad : new String[] { "4.0000001", "0" }) {
			CosmeticV2.Check c = check(plain.replace(rotation, "[0,10,0]}]}," + scaleTrack(bad).substring(1)));
			assertFalse(c.ok(), bad);
			assertTrue(c.errors.toString().contains("scale 0…4"), c.errors.toString());
		}
		CosmeticV2.Check tiny = check(plain.replace(rotation, "[0,10,0]}]}," + scaleTrack("1e-46").substring(1)));
		assertTrue(tiny.ok(), tiny.errors.toString());
		assertEquals(2, tiny.model.animations.get(0).tracks.length);
	}

	@Test
	void explicitLoopFalseAndMinimalValuesAreValid() {
		String block = "[" + pet(",\"loop\":false") + "," + action("eat", "eat", CosmeticV2.MIN_ACTION_MS, "",
				"[{\"bone\":\"head\",\"channel\":\"position\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") + "]";
		CosmeticV2.Check c = check(model("pet", block));
		assertTrue(c.ok(), c.errors.toString());
		assertTrue(c.warnings.isEmpty(), c.warnings.toString());
		assertEquals(2, c.model.actions.size());
		assertEquals(CosmeticV2.MIN_ACTION_MS, c.model.action(CosmeticV2.DRIVER_EAT).lengthMs);
		CosmeticV2.Check longest = check(model("pet", "[" + action("pet", "pet", CosmeticV2.MAX_ACTION_MS, "", TRACKS) + "]"));
		assertTrue(longest.warnings.isEmpty(), longest.warnings.toString());
	}

	// --- animations bleibt streng (ältere Clients) ------------------------------------------------------------

	@Test
	void petAndEatInsideAnimationsStayAnErrorAndActionsDoNotCountTowardsTheLimit() {
		assertFalse(check(PetHeistTest.model("idle", "pet")).ok(), "pet in animations lehnen auch ältere Clients ab");
		assertFalse(check(PetHeistTest.model("idle", "eat")).ok());
		assertFalse(check(PetHeistTest.model("idle", "dance")).ok());
		// 12 Animationen + 2 Aktionen: erlaubt (actions zählen nicht zu den 12)
		String twelve = PetHeistTest.model("idle", "walk", "slide", "swim", "air", "sneak", "grab", "flee", "handsup", "idle", "walk",
				"jump");
		String withActions = twelve.substring(0, twelve.length() - 1) + ",\"actions\":[" + action("pet", "pet", 1000, "",
				"[{\"bone\":\"body\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]") + "]}";
		CosmeticV2.Check c = check(withActions);
		assertTrue(c.ok(), c.errors.toString());
		assertTrue(c.warnings.isEmpty(), c.warnings.toString());
		assertEquals(CosmeticV2.MAX_ANIMATIONS, c.model.animations.size());
		assertEquals(1, c.model.actions.size());
	}
}
