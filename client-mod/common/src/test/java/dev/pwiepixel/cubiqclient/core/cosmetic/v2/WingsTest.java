package dev.pwiepixel.cubiqclient.core.cosmetic.v2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getragene Teile mit Zustand: Flügel (Halloween 2026, Slot {@code wings} am Körper), Hut und Begleiter am Kopf (Winter
 * 2026). Zustände idle/walk/jump/air/sneak aus der Bewegung, Überblendung, Rückfall auf idle – und der Nachweis, dass
 * Modelle ohne walk/jump genau so wechseln wie in Cubiq Client 0.23.0.
 */
class WingsTest {
	/** Ein Knochen am Rücken; idle dreht 0 → 10°, air steht fest bei 40° (beides um y), sneak fehlt. */
	static String model(String slot, String attach) {
		return "{\"format\":2,\"id\":\"test_wings\",\"name\":\"Test\",\"slot\":\"" + slot + "\",\"attach\":\"" + attach + "\","
				+ "\"texture\":{\"file\":\"t.png\",\"width\":8,\"height\":8,\"scale\":1},"
				+ "\"bones\":[{\"id\":\"w\",\"pivot\":[0,-3,-2.5]}],"
				+ "\"cubes\":[{\"id\":\"c\",\"bone\":\"w\",\"from\":[0,-6,-3],\"to\":[4,-2,-3],\"faces\":{\"south\":{\"uv\":[0,0,4,4]}}}],"
				+ "\"animations\":["
				+ "{\"id\":\"i\",\"driver\":\"idle\",\"lengthMs\":1000,\"tracks\":[{\"bone\":\"w\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]},{\"t\":1000,\"v\":[0,10,0]}]}]},"
				+ "{\"id\":\"a\",\"driver\":\"air\",\"lengthMs\":1000,\"tracks\":[{\"bone\":\"w\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,40,0]},{\"t\":1000,\"v\":[0,40,0]}]}]}]}";
	}

	/**
	 * Wie {@link #model(String, String)} mit genau diesen Treibern, je eine Animation: idle dreht 0 → 10°, die übrigen
	 * stehen fest (walk 20°, sneak 30°, air 40°, jump 50°).
	 */
	static CosmeticV2 model(String slot, String attach, String... drivers) {
		StringBuilder anims = new StringBuilder();
		for (int i = 0; i < drivers.length; i++) {
			String d = drivers[i];
			int deg = d.equals("walk") ? 20 : d.equals("sneak") ? 30 : d.equals("air") ? 40 : d.equals("jump") ? 50 : 0;
			if (i > 0) anims.append(',');
			anims.append("{\"id\":\"a").append(i).append("\",\"driver\":\"").append(d)
					.append("\",\"lengthMs\":1000,\"tracks\":[{\"bone\":\"w\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,")
					.append(deg).append(",0]},{\"t\":1000,\"v\":[0,").append(d.equals("idle") ? 10 : deg).append(",0]}]}]}");
		}
		String json = "{\"format\":2,\"id\":\"test_states\",\"name\":\"Test\",\"slot\":\"" + slot + "\",\"attach\":\"" + attach + "\","
				+ "\"texture\":{\"file\":\"t.png\",\"width\":8,\"height\":8,\"scale\":1},"
				+ "\"bones\":[{\"id\":\"w\",\"pivot\":[0,-3,-2.5]}],"
				+ "\"cubes\":[{\"id\":\"c\",\"bone\":\"w\",\"from\":[0,-6,-3],\"to\":[4,-2,-3],\"faces\":{\"south\":{\"uv\":[0,0,4,4]}}}],"
				+ "\"animations\":[" + anims + "]}";
		CosmeticV2.Check c = CosmeticV2.check(json, -1, -1, -1, -1);
		assertTrue(c.ok(), c.errors.toString());
		return c.model;
	}

	/** Weihnachtsmützen-Art (privat, bis 0.23.0 schon im Spiel): nur idle und air. */
	static CosmeticV2 idleAirHat() {
		return model("hat", "head", "idle", "air");
	}

	/** Alle Flügel bis 0.23.0: idle, air, sneak. */
	static CosmeticV2 wings() {
		return model("wings", "body", "idle", "air", "sneak");
	}

	/** Begleiter am Kopf bis 0.23.0 (Fledermaus, Mini-Cubi, Sternschnuppe): nur idle. */
	static CosmeticV2 idleCompanion() {
		return model("companion", "head", "idle");
	}

	/** Mütze ab 0.24.0: alle fünf Zustände. */
	static CosmeticV2 wornHat() {
		return model("hat", "head", "idle", "walk", "jump", "air", "sneak");
	}

	@AfterEach
	void reset() {
		WingState.forced = -1;
	}

	@Test
	void wingsNeedBodyAttachAndHatsStayOnTheHead() {
		CosmeticV2.Check ok = CosmeticV2.check(model("wings", "body"), -1, -1, -1, -1);
		assertTrue(ok.ok(), ok.errors.toString());
		assertEquals(CosmeticV2.SLOT_WINGS, ok.model.slot);
		assertTrue(ok.model.hasDriver(CosmeticV2.driverIndex("air")));
		assertFalse(ok.model.hasDriver(CosmeticV2.driverIndex("sneak")));
		assertFalse(CosmeticV2.check(model("wings", "head"), -1, -1, -1, -1).ok());
		assertFalse(CosmeticV2.check(model("hat", "body"), -1, -1, -1, -1).ok());
		assertTrue(CosmeticV2.check(model("hat", "head"), -1, -1, -1, -1).ok());
	}

	/** Drehwinkel (Grad) des Knochens um y nach der Pose. */
	private static double yaw(CosmeticV2Renderer r) {
		double[] out = new double[6];
		double[] w = r.world(0);
		V2Math.transformPoint(w, 0, -3, -2.5, out, 0);
		V2Math.transformPoint(w, 1, -3, -2.5, out, 3);
		return Math.toDegrees(Math.atan2(-(out[5] - out[2]), out[3] - out[0]));
	}

	@Test
	void statesBlendAndFallBackToIdle() {
		CosmeticV2 m = CosmeticV2.parse(model("wings", "body"));
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		int idle = WingState.driver(WingState.IDLE);
		int air = WingState.driver(WingState.AIR);
		int sneak = WingState.driver(WingState.SNEAK);
		r.pose(m, 0, true, false, air, air, 1f);
		assertEquals(40, Math.abs(yaw(r)), 1e-3);
		r.pose(m, 0, true, false, idle, idle, 1f);
		assertEquals(0, yaw(r), 1e-3);
		// Halb überblendet: idle (0°) → air (40°) = 20°
		r.pose(m, 0, true, false, air, idle, 0.5f);
		assertEquals(20, Math.abs(yaw(r)), 1e-3);
		// sneak fehlt im Modell → idle (bei t = 500 ms: 5°)
		r.pose(m, 500, true, false, sneak, sneak, 1f);
		assertEquals(5, Math.abs(yaw(r)), 1e-3);
		// Ohne Zustand wie bisher: nur idle
		r.pose(m, 500, true, false);
		assertEquals(5, Math.abs(yaw(r)), 1e-3);
		// walk und jump fehlen auch → idle; ein Modell mit allen fünf spielt jeden Zustand selbst
		r.pose(m, 500, true, false, WingState.driver(WingState.WALK), idle, 1f);
		assertEquals(5, Math.abs(yaw(r)), 1e-3);
		CosmeticV2 hat = wornHat();
		r.pose(hat, 500, true, false, WingState.driver(WingState.WALK), idle, 1f);
		assertEquals(20, Math.abs(yaw(r)), 1e-3);
		r.pose(hat, 500, true, false, WingState.driver(WingState.JUMP), idle, 1f);
		assertEquals(50, Math.abs(yaw(r)), 1e-3);
	}

	@Test
	void stateTrackerFadesAndDelaysAir() {
		assertEquals(WingState.SNEAK, WingState.classify(true, true, false, false, false, false));
		assertEquals(WingState.AIR, WingState.classify(false, true, true, false, false, false));
		assertEquals(WingState.AIR, WingState.classify(false, false, false, false, false, false));
		assertEquals(WingState.IDLE, WingState.classify(false, false, false, true, false, false), "reiten");
		assertEquals(WingState.IDLE, WingState.classify(false, false, false, false, true, false), "Wasser");
		assertEquals(WingState.IDLE, WingState.classify(false, false, false, false, false, true), "Leiter");

		WingState ws = new WingState();
		WingState.Snapshot s = ws.update("p", WingState.IDLE, 1000);
		assertEquals(1f, s.blend, 1e-6);
		// Luft erst nach 100 ms
		s = ws.update("p", WingState.AIR, 1050);
		assertEquals(WingState.IDLE, s.state);
		s = ws.update("p", WingState.AIR, 1160);
		assertEquals(WingState.AIR, s.state);
		assertEquals(4, s.driver);
		assertEquals(0, s.prevDriver);
		assertEquals(0f, s.blend, 1e-6);
		s = ws.update("p", WingState.AIR, 1160 + 125);
		assertEquals(0.5f, s.blend, 1e-6);
		s = ws.update("p", WingState.AIR, 1160 + 250);
		assertEquals(1f, s.blend, 1e-6);
		// Schleichen sofort
		s = ws.update("p", WingState.SNEAK, 2000);
		assertEquals(2, s.driver);
		assertEquals(4, s.prevDriver);
		// Erzwungen (Selbsttest)
		WingState.forced = WingState.AIR;
		s = ws.update("q", WingState.IDLE, 3000);
		assertEquals(WingState.AIR, s.state);
		assertNotNull(s);
	}

	/** Die acht Werte von {@link WingState#classify(boolean, boolean, boolean, boolean, boolean, boolean, float, float)}. */
	private static int classify(boolean ground, boolean sneak, boolean fly, boolean ride, boolean liquid, boolean climb,
			double speed, double vy) {
		return WingState.classify(ground, sneak, fly, ride, liquid, climb, (float) speed, (float) vy);
	}

	@Test
	void classifyWalkAndJumpFromTheMovement() {
		assertEquals(0.04f, WingState.WALK_SPEED);
		assertEquals(150L, WingState.WALK_HOLD_MS);
		assertEquals(0.05f, WingState.JUMP_VY);
		// Gehen ~0,216 Blöcke/Tick (Sprinten ~0,28): walk; stehen oder kaum bewegt: idle
		assertEquals(WingState.WALK, classify(true, false, false, false, false, false, 0.216, 0), "gehen");
		assertEquals(WingState.WALK, classify(true, false, false, false, false, false, 0.28, 0), "sprinten");
		assertEquals(WingState.IDLE, classify(true, false, false, false, false, false, 0, 0), "steht");
		assertEquals(WingState.IDLE, classify(true, false, false, false, false, false, 0.04, 0), "genau an der Schwelle");
		assertEquals(WingState.WALK, classify(true, false, false, false, false, false, 0.041, 0));
		assertEquals(WingState.WALK, classify(true, false, false, false, false, false, 0.216, 0.5), "geht eine Stufe hoch");
		// Schleichen geht vor, auch in Bewegung und im Sprung
		assertEquals(WingState.SNEAK, classify(true, true, false, false, false, false, 0.065, 0), "schleicht und bewegt sich");
		assertEquals(WingState.SNEAK, classify(false, true, false, false, false, false, 0.2, 0.42), "schleicht im Sprung");
		// Absprung: in der Luft, nicht im Flug, steigt schneller als 0,05 (ein Sprung beginnt mit 0,42)
		assertEquals(WingState.JUMP, classify(false, false, false, false, false, false, 0.216, 0.42), "Absprung");
		assertEquals(WingState.JUMP, classify(false, false, false, false, false, false, 0, 0.051));
		assertEquals(WingState.AIR, classify(false, false, false, false, false, false, 0, 0.05), "genau an der Schwelle");
		assertEquals(WingState.AIR, classify(false, false, false, false, false, false, 0.216, 0.003), "Scheitelpunkt");
		assertEquals(WingState.AIR, classify(false, false, false, false, false, false, 0.216, -0.3), "fällt");
		// Bewegt sich, aber schwimmt, reitet oder klettert: idle (wie bisher)
		assertEquals(WingState.IDLE, classify(false, false, false, false, true, false, 0.2, 0.1), "schwimmt");
		assertEquals(WingState.IDLE, classify(true, false, false, false, true, false, 0.2, 0), "watet");
		assertEquals(WingState.IDLE, classify(true, false, false, true, false, false, 0.3, 0), "reitet");
		assertEquals(WingState.IDLE, classify(false, false, false, true, false, false, 0.3, 0.42), "Pferd springt");
		assertEquals(WingState.IDLE, classify(false, false, false, false, false, true, 0, 0.12), "klettert");
		assertEquals(WingState.IDLE, classify(true, false, false, false, false, true, 0.1, 0), "an der Leiter am Boden");
		// Im Flug (Kreativ, Elytra) air, auch steigend; Schleichen zählt im Flug nicht
		assertEquals(WingState.AIR, classify(false, false, true, false, false, false, 0.5, 0.3), "steigt im Flug");
		assertEquals(WingState.AIR, classify(true, false, true, false, false, false, 0.3, 0), "fliegt am Boden entlang");
		assertEquals(WingState.AIR, classify(false, true, true, false, false, false, 0, -0.3), "sinkt im Flug");
	}

	@Test
	void forModelMapsWalkAndJumpOnlyWhenTheModelHasThem() {
		CosmeticV2 hat = wornHat();
		CosmeticV2 old = idleAirHat();
		CosmeticV2 walkOnly = model("hat", "head", "idle", "walk");
		CosmeticV2 jumpOnly = model("companion", "head", "idle", "jump");
		for (int s : new int[] { WingState.IDLE, WingState.WALK, WingState.JUMP, WingState.AIR, WingState.SNEAK }) {
			assertEquals(s, WingState.forModel(hat, s), "alle fünf: unverändert");
		}
		assertEquals(WingState.IDLE, WingState.forModel(old, WingState.WALK));
		assertEquals(WingState.AIR, WingState.forModel(old, WingState.JUMP));
		assertEquals(WingState.AIR, WingState.forModel(old, WingState.AIR));
		assertEquals(WingState.SNEAK, WingState.forModel(old, WingState.SNEAK));
		assertEquals(WingState.IDLE, WingState.forModel(wings(), WingState.WALK));
		assertEquals(WingState.AIR, WingState.forModel(wings(), WingState.JUMP));
		assertEquals(WingState.WALK, WingState.forModel(walkOnly, WingState.WALK));
		// Ohne jump und ohne air: air – der Renderer spielt dann idle
		assertEquals(WingState.AIR, WingState.forModel(walkOnly, WingState.JUMP));
		assertEquals(WingState.IDLE, WingState.forModel(jumpOnly, WingState.WALK));
		assertEquals(WingState.JUMP, WingState.forModel(jumpOnly, WingState.JUMP));

		// Zustände braucht nur, wer mehr als idle animiert
		assertFalse(WingState.hasStates(idleCompanion()));
		assertTrue(WingState.hasStates(old));
		assertTrue(WingState.hasStates(wings()));
		assertTrue(WingState.hasStates(walkOnly));
		assertTrue(WingState.hasStates(jumpOnly));
		assertTrue(WingState.hasStates(model("hat", "head", "idle", "sneak")));
		// walk/jump spielen nur Clients ab 0.24.0: Treiber-Indizes wie im Format (§7)
		assertEquals(CosmeticV2.DRIVER_WALK, WingState.driver(WingState.WALK));
		assertEquals(CosmeticV2.DRIVER_JUMP, WingState.driver(WingState.JUMP));
		assertEquals(1, WingState.driver(WingState.WALK));
		assertEquals(3, WingState.driver(WingState.JUMP));
	}

	@Test
	void walkStartsAtOnceAndIsHeldFor150Ms() {
		WingState ws = new WingState();
		WingState.Snapshot s = ws.update("p", WingState.IDLE, 1000);
		s = ws.update("p", WingState.WALK, 1100);
		assertEquals(WingState.WALK, s.state, "Laufen sofort");
		assertEquals(1, s.driver);
		assertEquals(0, s.prevDriver);
		assertEquals(0f, s.blend, 1e-6);
		// Kurz nicht gemeldet (zwischen zwei Ticks, beim Abbremsen): Laufen hält 150 ms nach der letzten Meldung
		s = ws.update("p", WingState.IDLE, 1200);
		assertEquals(WingState.WALK, s.state);
		s = ws.update("p", WingState.IDLE, 1249);
		assertEquals(WingState.WALK, s.state);
		// … und verlängert sich dabei nicht selbst
		s = ws.update("p", WingState.IDLE, 1250);
		assertEquals(WingState.IDLE, s.state);
		assertEquals(0, s.driver);
		assertEquals(1, s.prevDriver);
		assertEquals(0f, s.blend, 1e-6);

		// Gehen mit Lücken unter 150 ms (jeder dritte Tick ohne Bewegung): kein Flackern, eine Überblendung
		s = ws.update("p", WingState.WALK, 2000);
		assertEquals(WingState.WALK, s.state);
		for (long t = 2000; t < 3000; t += 16) {
			int state = (t / 50) % 3 == 0 ? WingState.IDLE : WingState.WALK;
			s = ws.update("p", state, t);
			assertEquals(WingState.WALK, s.state, "t = " + t);
		}
		assertEquals(1f, s.blend, 1e-6);

		// Halten gilt nur gegenüber idle: Schleichen, Absprung und (nach 100 ms) Luft gehen sofort vor
		s = ws.update("p", WingState.SNEAK, 3010);
		assertEquals(WingState.SNEAK, s.state);
		s = ws.update("p", WingState.WALK, 3100);
		s = ws.update("p", WingState.JUMP, 3150);
		assertEquals(WingState.JUMP, s.state);
	}

	@Test
	void jumpStartsAtOnceAndAirFollowsWithoutDelay() {
		WingState ws = new WingState();
		WingState.Snapshot s = ws.update("p", WingState.WALK, 1000);
		s = ws.update("p", WingState.JUMP, 1050);
		assertEquals(WingState.JUMP, s.state, "Absprung sofort");
		assertEquals(3, s.driver);
		assertEquals(1, s.prevDriver);
		assertEquals(0f, s.blend, 1e-6);
		s = ws.update("p", WingState.JUMP, 1175);
		assertEquals(0.5f, s.blend, 1e-6);
		// Nach dem Absprung ohne die 100 ms Luft-Verzögerung
		s = ws.update("p", WingState.AIR, 1300);
		assertEquals(WingState.AIR, s.state);
		assertEquals(4, s.driver);
		assertEquals(3, s.prevDriver);
		assertEquals(0f, s.blend, 1e-6);
		// Landung
		s = ws.update("p", WingState.WALK, 1600);
		assertEquals(WingState.WALK, s.state);

		// Ohne Absprung (von einer Kante laufen, Treppe hinab) bleibt die Verzögerung: Luft erst nach 100 ms
		s = ws.update("p", WingState.AIR, 2000);
		assertEquals(WingState.WALK, s.state);
		s = ws.update("p", WingState.AIR, 2099);
		assertEquals(WingState.WALK, s.state);
		s = ws.update("p", WingState.AIR, 2100);
		assertEquals(WingState.AIR, s.state);
		// Aus dem Stand wie bisher
		s = ws.update("q", WingState.IDLE, 3000);
		s = ws.update("q", WingState.AIR, 3050);
		assertEquals(WingState.IDLE, s.state);
	}

	/**
	 * Hut und Begleiter am Kopf bilden walk/jump je nach Modell verschieden ab ({@link WingState#forModel}). Ein
	 * gemeinsamer Tracker springt beim Absprung in jedem Bild zwischen jump (Mütze) und air (Begleiter ohne jump) hin und
	 * her, und der Begleiter verlöre seine 100 ms Luft-Verzögerung. Mit eigenem Tracker je Fach bleibt der Begleiter
	 * genau wie in 0.23.0.
	 */
	@Test
	void eachSlotNeedsItsOwnTracker() {
		CosmeticV2 hat = wornHat();
		CosmeticV2 buddy = idleAirHat();
		List<Move> moves = new ArrayList<Move>();
		repeat(moves, Move.ground(0), 10);
		jump(moves, 0);
		repeat(moves, Move.ground(0), 10);
		WingState shared = new WingState();
		WingState hats = new WingState();
		WingState buddies = new WingState();
		Released0230 then = new Released0230();
		float[] blend = new float[1];
		int flips = 0;
		boolean hatJumped = false;
		for (int i = 0; i < moves.size(); i++) {
			Move mv = moves.get(i);
			int s = WingState.classify(mv.ground, mv.sneak, mv.fly, mv.ride, mv.liquid, mv.climb, mv.speed, mv.vy);
			for (int frame = 0; frame < 3; frame++) {
				long t = 1000L + i * 50L + frame * 16L;
				WingState.Snapshot a = shared.update("p", hat, s, t);
				WingState.Snapshot b = shared.update("p", buddy, s, t);
				if (a.state != b.state) flips++;
				hatJumped |= hats.update("p", hat, s, t).state == WingState.JUMP;
				WingState.Snapshot own = buddies.update("p", buddy, s, t);
				int[] o = then.update("p", Released0230.classify(mv.ground, mv.sneak, mv.fly, mv.ride, mv.liquid, mv.climb), t,
						blend);
				assertEquals(o[0], own.state, "t = " + t);
				assertEquals(o[2], own.prevDriver, "t = " + t);
				assertEquals(blend[0], own.blend, 0f, "t = " + t);
			}
		}
		assertTrue(flips > 5, "gemeinsamer Tracker springt hin und her: " + flips);
		assertTrue(hatJumped);
	}

	/**
	 * Der Tracker aus Cubiq Client 0.23.0 (unverändert kopiert, nur ohne Aufräumen): Zustände idle/air/sneak aus
	 * {@link #classify(boolean, boolean, boolean, boolean, boolean, boolean)}, Luft nach 100 ms, 250 ms Überblendung.
	 */
	static final class Released0230 {
		static final int IDLE = 0, AIR = 1, SNEAK = 2;
		static final long FADE_MS = 250L;
		static final long AIR_DELAY_MS = 100L;

		static final class Track {
			int state;
			int prev;
			long since;
			long airFrom;
		}

		private final Map<Object, Track> tracks = new HashMap<Object, Track>();

		static int classify(boolean onGround, boolean sneaking, boolean flying, boolean riding, boolean inLiquid,
				boolean climbing) {
			if (sneaking && !flying) return SNEAK;
			if ((!onGround || flying) && !riding && !inLiquid && !climbing) return AIR;
			return IDLE;
		}

		static int driver(int state) {
			switch (state) {
				case AIR:
					return 4;
				case SNEAK:
					return 2;
				default:
					return 0;
			}
		}

		/** {@code {state, driver, prevDriver}} und die Überblendung in {@code blend[0]}. */
		int[] update(Object key, int state, long now, float[] blend) {
			Track t = tracks.get(key);
			if (state == AIR) {
				if (t != null && t.state != AIR) {
					if (t.airFrom == 0) t.airFrom = now;
					if (now - t.airFrom < AIR_DELAY_MS) state = t.state;
				}
			} else if (t != null) {
				t.airFrom = 0;
			}
			if (t == null) {
				t = new Track();
				t.state = state;
				t.prev = state;
				t.since = now - FADE_MS;
				tracks.put(key, t);
			} else if (t.state != state) {
				float w = weight(t, now);
				t.prev = w >= 0.5f ? t.state : t.prev;
				t.state = state;
				t.since = now;
			}
			blend[0] = weight(t, now);
			return new int[] { t.state, driver(t.state), driver(t.prev) };
		}

		static float weight(Track t, long now) {
			long dt = now - t.since;
			if (dt >= FADE_MS || t.prev == t.state) return 1f;
			if (dt <= 0) return 0f;
			float u = dt / (float) FADE_MS;
			return u * u * (3 - 2 * u);
		}
	}

	/** Bewegung eines Ticks (wie sie ein Client sieht). */
	static final class Move {
		final boolean ground, sneak, fly, ride, liquid, climb;
		final float speed, vy;

		Move(boolean ground, boolean sneak, boolean fly, boolean ride, boolean liquid, boolean climb, double speed, double vy) {
			this.ground = ground;
			this.sneak = sneak;
			this.fly = fly;
			this.ride = ride;
			this.liquid = liquid;
			this.climb = climb;
			this.speed = (float) speed;
			this.vy = (float) vy;
		}

		static Move ground(double speed) {
			return new Move(true, false, false, false, false, false, speed, 0);
		}

		static Move air(double speed, double vy) {
			return new Move(false, false, false, false, false, false, speed, vy);
		}
	}

	/** Ein Sprung wie in Minecraft: 0,42 nach oben, je Tick −0,08 und ×0,98, bis wieder auf Höhe 0. */
	static void jump(List<Move> out, double speed) {
		double y = 0, vy = 0.42;
		do {
			y += vy;
			out.add(Move.air(speed, vy));
			vy = (vy - 0.08) * 0.98;
		} while (y + vy > 0);
		out.add(Move.ground(speed));
	}

	static void repeat(List<Move> out, Move m, int ticks) {
		for (int i = 0; i < ticks; i++) out.add(m);
	}

	/** Ein typischer Weg: stehen, gehen, springen, Treppe hinab, schleichen, fliegen, schwimmen, reiten, klettern. */
	static List<Move> walkAround() {
		List<Move> moves = new ArrayList<Move>();
		repeat(moves, Move.ground(0), 20);
		repeat(moves, Move.ground(0.216), 30);
		jump(moves, 0.216);
		repeat(moves, Move.ground(0.216), 4);
		jump(moves, 0.28);
		jump(moves, 0.28);
		repeat(moves, Move.ground(0.1), 3);
		repeat(moves, Move.ground(0), 10);
		jump(moves, 0);
		// Treppe hinab: je Stufe ein, zwei Ticks ohne Boden
		for (int i = 0; i < 6; i++) {
			moves.add(Move.air(0.216, -0.08));
			if (i % 2 == 0) moves.add(Move.air(0.216, -0.16));
			repeat(moves, Move.ground(0.216), 2);
		}
		// Von einer Kante: richtig fallen
		for (int i = 0; i < 12; i++) moves.add(Move.air(0.1, -0.08 * (i + 1)));
		repeat(moves, Move.ground(0), 8);
		repeat(moves, new Move(true, true, false, false, false, false, 0.065, 0), 20);
		repeat(moves, new Move(true, true, false, false, false, false, 0, 0), 10);
		moves.add(new Move(false, true, false, false, false, false, 0.06, 0.42));
		repeat(moves, Move.ground(0), 5);
		// Kreativflug: steigen, schweben, sinken, landen
		repeat(moves, new Move(false, false, true, false, false, false, 0.5, 0.3), 15);
		repeat(moves, new Move(false, false, true, false, false, false, 0.5, 0), 15);
		repeat(moves, new Move(false, true, true, false, false, false, 0, -0.3), 10);
		repeat(moves, Move.ground(0), 5);
		// Schwimmen, Reiten (auch ein Pferdesprung), Klettern
		repeat(moves, new Move(false, false, false, false, true, false, 0.12, 0.02), 25);
		repeat(moves, new Move(true, false, false, true, false, false, 0.3, 0), 20);
		repeat(moves, new Move(false, false, false, true, false, false, 0.3, 0.42), 6);
		repeat(moves, new Move(false, false, false, false, false, true, 0, 0.12), 20);
		repeat(moves, Move.ground(0.216), 10);
		repeat(moves, Move.ground(0), 30);
		return moves;
	}

	/** Zufällige Bewegung: Abschnitte von 1–30 Ticks mit festen Werten, dazwischen oft auch nur ein Tick. */
	static List<Move> random(long seed, int ticks) {
		Random r = new Random(seed);
		float[] speeds = { 0f, 0.02f, 0.04f, 0.041f, 0.065f, 0.1f, 0.216f, 0.28f, 0.6f };
		float[] vys = { -0.6f, -0.3f, -0.08f, 0f, 0.003f, 0.05f, 0.051f, 0.12f, 0.3f, 0.42f };
		List<Move> moves = new ArrayList<Move>();
		while (moves.size() < ticks) {
			Move m = new Move(r.nextInt(3) > 0, r.nextInt(5) == 0, r.nextInt(6) == 0, r.nextInt(8) == 0, r.nextInt(8) == 0,
					r.nextInt(8) == 0, speeds[r.nextInt(speeds.length)], vys[r.nextInt(vys.length)]);
			repeat(moves, m, r.nextBoolean() ? 1 : 1 + r.nextInt(30));
		}
		return moves;
	}

	/**
	 * Bilder in unregelmäßigen Abständen (4–45 ms, je Tick 50 ms eine Bewegung) – neuer Weg (acht Werte →
	 * {@link WingState#forModel} → Tracker) gegen 0.23.0 (sechs Werte → alter Tracker): Zustand, Treiber, voriger Treiber
	 * und Überblendung müssen in jedem Bild exakt gleich sein. Zwei Spieler wechseln sich ab.
	 */
	static int assertSameAs0230(CosmeticV2 m, List<Move> moves, long seed) {
		Random r = new Random(seed);
		WingState now = new WingState();
		Released0230 then = new Released0230();
		float[] blend = new float[1];
		long t = 1_000_000L;
		long end = t + moves.size() * 50L;
		int frames = 0;
		int[] seen = new int[3];
		while (t < end) {
			Move mv = moves.get((int) ((t - 1_000_000L) / 50L));
			for (String player : new String[] { "a", "b" }) {
				int s = WingState.classify(mv.ground, mv.sneak, mv.fly, mv.ride, mv.liquid, mv.climb, mv.speed, mv.vy);
				WingState.Snapshot n = now.update(player, m, s, t);
				int[] o = then.update(player, Released0230.classify(mv.ground, mv.sneak, mv.fly, mv.ride, mv.liquid, mv.climb), t,
						blend);
				String at = m.slot + " " + player + " t=" + t + " (Tick " + (t - 1_000_000L) / 50L + ")";
				assertEquals(o[0], n.state, at);
				assertEquals(o[1], n.driver, at);
				assertEquals(o[2], n.prevDriver, at);
				assertEquals(blend[0], n.blend, 0f, at);
				seen[n.state]++;
			}
			frames++;
			t += 4 + r.nextInt(42);
		}
		for (int i = 0; i < 3; i++) assertTrue(seen[i] > 0, m.slot + ": Zustand " + i + " kam nie vor");
		return frames;
	}

	/**
	 * Teile ohne walk/jump – die Weihnachtsmützen-Art (idle/air), alle Flügel (idle/air/sneak), Begleiter nur mit idle –
	 * wechseln den Zustand in genau den gleichen Momenten wie in Cubiq Client 0.23.0.
	 */
	@Test
	void releasedItemsSwitchStatesExactlyLikeCubiqClient0230() {
		// 1. Jede Bewegung: acht Werte und forModel ergeben den Zustand der sechs Werte von 0.23.0
		float[] speeds = { 0f, 0.04f, 0.041f, 0.216f, 1f };
		float[] vys = { -0.5f, 0f, 0.05f, 0.051f, 0.42f };
		for (CosmeticV2 m : new CosmeticV2[] { idleAirHat(), wings(), idleCompanion() }) {
			for (int bits = 0; bits < 64; bits++) {
				boolean ground = (bits & 1) != 0, sneak = (bits & 2) != 0, fly = (bits & 4) != 0, ride = (bits & 8) != 0,
						liquid = (bits & 16) != 0, climb = (bits & 32) != 0;
				int then = WingState.classify(ground, sneak, fly, ride, liquid, climb);
				assertEquals(then, Released0230.classify(ground, sneak, fly, ride, liquid, climb), "sechs Werte unverändert");
				for (float speed : speeds) {
					for (float vy : vys) {
						int now = WingState.forModel(m, WingState.classify(ground, sneak, fly, ride, liquid, climb, speed, vy));
						assertEquals(then, now, m.slot + " bits=" + bits + " speed=" + speed + " vy=" + vy);
					}
				}
			}
			// Zustände nur, wenn 0.23.0 sie auch hatte (dort: air oder sneak)
			boolean then = m.hasDriver(CosmeticV2.DRIVER_AIR) || m.hasDriver(CosmeticV2.DRIVER_SNEAK);
			assertEquals(then, WingState.hasStates(m), m.slot);
		}
		// 2. Über die Zeit: gleiche Momente, gleiche Überblendung – ein typischer Weg und lange Zufallsfolgen
		for (CosmeticV2 m : new CosmeticV2[] { idleAirHat(), wings() }) {
			List<Move> walk = walkAround();
			assertTrue(assertSameAs0230(m, walk, 7L) > walk.size(), "mehr Bilder als Ticks");
			for (long seed = 1; seed <= 4; seed++) assertSameAs0230(m, random(seed, 4000), seed * 31L);
		}
	}

	/** Gleicher Weg mit der neuen Mütze: geht, springt sofort, fliegt ohne Verzögerung weiter, landet. */
	@Test
	void hatWithAllStatesFollowsAWalkAndAJump() {
		CosmeticV2 hat = wornHat();
		WingState ws = new WingState();
		List<Move> moves = new ArrayList<Move>();
		repeat(moves, Move.ground(0), 10);
		repeat(moves, Move.ground(0.216), 10);
		jump(moves, 0.216);
		repeat(moves, Move.ground(0), 10);
		List<Integer> states = new ArrayList<Integer>();
		for (int i = 0; i < moves.size(); i++) {
			Move mv = moves.get(i);
			int s = WingState.classify(mv.ground, mv.sneak, mv.fly, mv.ride, mv.liquid, mv.climb, mv.speed, mv.vy);
			// Erstes Bild des Ticks
			WingState.Snapshot snap = ws.update("p", hat, s, 1000L + i * 50L);
			if (states.isEmpty() || states.get(states.size() - 1) != snap.state) states.add(snap.state);
		}
		List<Integer> expected = new ArrayList<Integer>();
		expected.add(WingState.IDLE);
		expected.add(WingState.WALK);
		expected.add(WingState.JUMP);
		expected.add(WingState.AIR);
		expected.add(WingState.WALK); // landet noch in Bewegung (letzter Tick des Sprungs)
		expected.add(WingState.IDLE);
		assertEquals(expected, states);
	}

	static String resource(String file) throws IOException {
		try (InputStream in = WingsTest.class.getResourceAsStream("/cosmetic-v2/" + file)) {
			assertNotNull(in, file);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * {@code christmas_hat.generated.json}: eingefrorene Kopie der Zipfelmütze, wie sie der Generator der Website
	 * schreibt (Stand: Entwurf aus der Planung, vor dem Website-Commit). Lädt ohne Warnung, nutzt nur die fünf
	 * Zustands-Treiber und spielt jeden davon sichtbar anders als idle.
	 */
	@Test
	void christmasHatLoadsWithoutWarningsAndPlaysEveryState() throws IOException {
		String json = resource("christmas_hat.generated.json");
		assertTrue(json.length() <= 512 * 1024, "höchstens 512 KiB im Client");
		CosmeticV2.Check c = CosmeticV2.check(json, -1, -1, -1, -1);
		assertTrue(c.ok(), c.errors.toString());
		assertTrue(c.warnings.isEmpty(), c.warnings.toString());
		CosmeticV2 m = c.model;
		assertEquals("christmas_hat", m.id);
		assertEquals(CosmeticV2.SLOT_HAT, m.slot);
		assertTrue(m.options.isEmpty(), "keine Optionen");
		assertFalse(m.hasDriver(CosmeticV2.DRIVER_PET), "keine Aktionen");
		assertFalse(m.hasDriver(CosmeticV2.DRIVER_EAT), "keine Aktionen");
		String[] states = { "idle", "walk", "jump", "air", "sneak" };
		for (String d : states) assertTrue(m.hasDriver(CosmeticV2.driverIndex(d)), d);
		for (String d : new String[] { "slide", "swim", "grab", "flee", "handsup" }) {
			assertFalse(m.hasDriver(CosmeticV2.driverIndex(d)), d + " ist kein Zustand eines Hutes");
		}
		assertTrue(WingState.hasStates(m));
		assertEquals(WingState.WALK, WingState.forModel(m, WingState.WALK));
		assertEquals(WingState.JUMP, WingState.forModel(m, WingState.JUMP));

		// Knochenkette cap → mid → tip → end → pom
		String[] chain = { "cap", "mid", "tip", "end", "pom" };
		int[] bone = new int[chain.length];
		for (int i = 0; i < chain.length; i++) {
			bone[i] = -1;
			for (int b = 0; b < m.bones.size(); b++) if (m.bones.get(b).id.equals(chain[i])) bone[i] = b;
			assertTrue(bone[i] >= 0, chain[i]);
			if (i > 0) assertEquals(bone[i - 1], m.bones.get(bone[i]).parent, chain[i] + " hängt an " + chain[i - 1]);
		}

		// Jeder Zustand bewegt den Bommel anders als idle
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		int pom = bone[chain.length - 1];
		CosmeticV2.Bone b = m.bones.get(pom);
		for (String d : states) {
			if (d.equals("idle")) continue;
			int driver = CosmeticV2.driverIndex(d);
			double most = 0;
			for (long t = 0; t < 4000; t += 50) {
				double[] a = new double[3], z = new double[3];
				r.pose(m, t, true, false, driver, driver, 1f);
				V2Math.transformPoint(r.world(pom), b.px, b.py, b.pz, a, 0);
				r.pose(m, t, true, false, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f);
				V2Math.transformPoint(r.world(pom), b.px, b.py, b.pz, z, 0);
				most = Math.max(most, Math.sqrt((a[0] - z[0]) * (a[0] - z[0]) + (a[1] - z[1]) * (a[1] - z[1])
						+ (a[2] - z[2]) * (a[2] - z[2])));
			}
			assertTrue(most > 0.5, d + ": Bommel bewegt sich nur " + most + " px anders als idle");
		}
	}
}
