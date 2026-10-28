package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSimTest.FakeWorld;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Streicheln/Füttern in der Simulation: Dauer aus der Länge der Aktion (sonst 2 s / 2,4 s mit Herzchen), still stehen und
 * zum Besitzer schauen, kein Neustart einer laufenden Aktion, nur wenn der Begleiter ruhig steht, Vorrang vor „Hände
 * hoch“ (und danach Pause), Abbruch, wenn der Besitzer wegläuft.
 */
class PetInteractTest {
	static final UUID OWNER = PetSimTest.OWNER;
	/** Kann alles (Überfall-Zustände) und hat beide Aktionen im Modell. */
	static final int ALL = PetHeistTest.ALL | Pet.CAN_PET | Pet.CAN_EAT;
	/** Länge der Aktionen pet/eat in ms (wie der Fuchs). */
	static final int[] MS = { 2200, 2600 };

	@AfterEach
	void reset() {
		PetSim.forceSlide = Double.NaN;
		PetSim.forceGrab = false;
	}

	/** Ein Tick: ein Begleiter, Besitzer bei ({@code ox}, {@code oy}, {@code oz}). */
	private static void tick(PetSim sim, PetWorld w, int i, double ox, double oy, double oz, int can, int[] ms) {
		sim.begin(w, i * 50L);
		sim.owner(OWNER, 1, 1.0, null, new int[] { can }, ms, null, ox, oy, oz, 0f, false, false, true, w);
		sim.end();
	}

	private static void tick(PetSim sim, PetWorld w, int i, int can, int[] ms) {
		tick(sim, w, i, 0.5, 64, 0.5, can, ms);
	}

	/** 60 Ticks bei stehendem Besitzer: der Begleiter steht ruhig auf seinem Platz. */
	private static Pet settled(PetSim sim, FakeWorld w, int can, int[] ms) {
		PetSim.forceSlide = 0;
		for (int i = 0; i < 60; i++) tick(sim, w, i, can, ms);
		Pet p = sim.pets(OWNER).get(0);
		assertEquals(Pet.IDLE, p.state());
		return p;
	}

	/** Klick kurz nach dem letzten Tick (59 · 50 ms). */
	private static final long CLICK = 59 * 50L + 20;

	/** Blickrichtung (Gieren) vom Begleiter zum Besitzer bei (0,5 | 0,5). */
	private static double towardOwner(Pet p) {
		return Math.toDegrees(Math.atan2(-(0.5 - p.x), 0.5 - p.z));
	}

	@Test
	void petPlaysTheClipLengthStandingStillAndFacingTheOwner() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		p.yaw = p.pyaw = 90; // schaut gerade weg
		double x = p.x, z = p.z;
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.PET, CLICK));
		assertEquals(Pet.PET, p.state(), "sofort, nicht erst im nächsten Tick");
		assertEquals(Pet.PET, p.action());
		assertEquals(CLICK, p.stateSince(), "Zeitbasis der Aktion = Klick");
		assertEquals(Pet.IDLE, p.prevState());
		int ticks = 0, i = 60;
		for (; i < 200 && p.state() == Pet.PET; i++) {
			tick(sim, w, i, ALL, MS);
			assertEquals(0, sim.fxCount(), "mit eigener Aktion im Modell keine Herzchen");
			if (p.state() != Pet.PET) break;
			ticks++;
			assertEquals(x, p.x, 1e-9, "steht still");
			assertEquals(z, p.z, 1e-9);
		}
		assertEquals((MS[0] + 49) / 50, ticks, "so lang wie die Aktion im Modell");
		assertEquals(0, Pet.wrap(p.yaw - towardOwner(p)), 1.0, "zum Besitzer gedreht");
		assertEquals(Pet.IDLE, p.state());
		assertEquals(Pet.PET, p.prevState(), "blendet aus der Aktion aus");
		assertEquals(CLICK, p.prevStateSince(), "… auf ihrer eigenen Zeitbasis");
		assertEquals(-1, p.action());
		// kurze Pause danach, dann geht es wieder
		assertEquals(PetSim.REFUSED, sim.interact(p, Pet.EAT, i * 50L));
		for (int k = 0; k < PetSim.INTERACT_COOLDOWN; k++) tick(sim, w, ++i, ALL, MS);
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.EAT, i * 50L + 10));
		assertEquals(Pet.EAT, p.state());
		int eat = 0;
		for (int k = 0; k < 100; k++) {
			tick(sim, w, ++i, ALL, MS);
			if (p.state() == Pet.EAT) eat++;
		}
		assertEquals((MS[1] + 49) / 50, eat, "Füttern so lang wie seine Aktion");
	}

	@Test
	void evenTheLongestValidActionPlaysToItsEnd() {
		assertEquals((CosmeticV2.MAX_ACTION_MS + 49) / 50, PetSim.ACTION_MAX_TICKS, "deckt die längste gültige Aktion (10 s)");
		assertEquals(200, PetSim.ACTION_MAX_TICKS);
		for (int ms : new int[] { 8000, CosmeticV2.MAX_ACTION_MS, 9999 }) {
			PetSim sim = new PetSim();
			FakeWorld w = new FakeWorld();
			int[] lengths = { ms, ms };
			Pet p = settled(sim, w, ALL, lengths);
			assertEquals(PetSim.STARTED, sim.interact(p, Pet.PET, CLICK));
			int ticks = 0;
			for (int i = 60; i < 400 && p.state() == Pet.PET; i++) {
				tick(sim, w, i, ALL, lengths);
				if (p.state() == Pet.PET) ticks++;
			}
			assertEquals((ms + 49) / 50, ticks, ms + " ms: so lang wie die Aktion im Modell");
			assertEquals(Pet.IDLE, p.state(), ms + " ms");
		}
	}

	@Test
	void withoutTheClipItStandsStillWithHeartsForTheDefaultDuration() {
		for (int action : new int[] { Pet.PET, Pet.EAT }) {
			PetSim sim = new PetSim();
			FakeWorld w = new FakeWorld();
			Pet p = settled(sim, w, PetHeistTest.ALL, null);
			double x = p.x, z = p.z;
			assertEquals(PetSim.STARTED, sim.interact(p, action, CLICK));
			int ticks = 0, hearts = 0;
			for (int i = 60; i < 200; i++) {
				tick(sim, w, i, PetHeistTest.ALL, null);
				for (int k = 0; k < sim.fxCount(); k++) {
					PetSim.Fx fx = sim.fx(k);
					assertEquals(PetSim.Fx.HEART, fx.kind);
					assertTrue(fx.y > p.y + 0.8 && fx.y < p.y + 1.3, "über dem Kopf: " + (fx.y - p.y));
					assertTrue(Math.hypot(fx.x - p.x, fx.z - p.z) < 0.3, "über ihm");
					hearts++;
				}
				if (p.state() != action) break;
				ticks++;
				assertEquals(x, p.x, 1e-9);
				assertEquals(z, p.z, 1e-9);
			}
			int expected = action == Pet.PET ? PetSim.PET_TICKS : PetSim.EAT_TICKS;
			assertEquals(expected, ticks, Pet.stateNameOf(action));
			assertEquals((expected + PetSim.HEART_EVERY - 1) / PetSim.HEART_EVERY, hearts, "alle 0,5 s ein Herzchen");
		}
	}

	@Test
	void aRunningActionIsNeverRestarted() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.PET, CLICK));
		int i = 60;
		for (int k = 0; k < 10; k++) tick(sim, w, i++, ALL, MS);
		assertEquals(PetSim.BUSY, sim.interact(p, Pet.PET, i * 50L), "noch einmal geklickt");
		assertEquals(PetSim.BUSY, sim.interact(p, Pet.EAT, i * 50L), "auch Füttern wartet");
		assertEquals(Pet.PET, p.state());
		assertEquals(CLICK, p.stateSince(), "kein Neustart (kein Sprung auf den Anfang)");
		int more = 0;
		while (p.state() == Pet.PET && more < 100) {
			tick(sim, w, i++, ALL, MS);
			if (p.state() == Pet.PET) more++;
		}
		assertEquals((MS[0] + 49) / 50 - 10, more, "die Dauer bleibt die des ersten Klicks");
		assertEquals(PetSim.REFUSED, sim.interact(null, Pet.PET, i * 50L));
		assertEquals(PetSim.REFUSED, sim.interact(p, Pet.HANDSUP, i * 50L), "nur Streicheln/Füttern");
	}

	@Test
	void onlyWhenCalmNotSwimmingFlyingGrabbingFleeingOrNeededElsewhere() {
		// schwimmt
		PetSim sea = new PetSim();
		FakeWorld water = new FakeWorld();
		water.box(-8, 60, -8, 8, 63, 8, PetSimTest.WATER);
		water.box(-8, 59, -8, 8, 59, 8, PetSimTest.STONE);
		PetSim.forceSlide = 0;
		for (int i = 0; i < 120; i++) tick(sea, water, i, 0.5, 63.6, 0.5, ALL, MS);
		Pet swimmer = sea.pets(OWNER).get(0);
		assertEquals(Pet.SWIM, swimmer.state());
		assertEquals(PetSim.REFUSED, sea.interact(swimmer, Pet.PET, 119 * 50L + 10));
		assertEquals(Pet.SWIM, swimmer.state());

		// in der Luft (Sprung)
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		p.onGround = false;
		p.vy = 0.3;
		tick(sim, w, 60, ALL, MS);
		assertEquals(PetSim.REFUSED, sim.interact(p, Pet.EAT, 60 * 50L + 10));
		for (int i = 61; i < 80; i++) tick(sim, w, i, ALL, MS);
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.EAT, 79 * 50L + 10), "wieder am Boden");

		// schnappt / flieht
		PetSim heist = new PetSim();
		Pet h = settled(heist, w, ALL, MS);
		PetSim.forceGrab = true;
		boolean grabbed = false, fled = false;
		for (int i = 60; i < 260 && !(fled && h.state() != Pet.FLEE); i++) {
			tick(heist, w, i, ALL, MS);
			if (h.state() == Pet.GRAB) {
				PetSim.forceGrab = false;
				grabbed = true;
				assertEquals(PetSim.REFUSED, heist.interact(h, Pet.PET, i * 50L + 10), "nicht beim Schnappen");
			}
			if (h.state() == Pet.FLEE) {
				fled = true;
				assertEquals(PetSim.REFUSED, heist.interact(h, Pet.PET, i * 50L + 10), "nicht auf der Flucht");
			}
		}
		assertTrue(grabbed && fled);

		// Besitzer schon weit weg: er müsste ohnehin nachlaufen
		PetSim far = new PetSim();
		Pet f = settled(far, w, ALL, MS);
		tick(far, w, 60, 0.5, 64, 6.5, ALL, MS);
		assertEquals(PetSim.REFUSED, far.interact(f, Pet.PET, 60 * 50L + 10));
	}

	/** Kamera 3 Blöcke südlich des Begleiters, Blick auf seine Mitte. */
	private static void look(PetSim sim, Pet p) {
		double cx = p.x, cy = p.y + 1.2, cz = p.z + 3.0;
		double dx = p.x - cx, dy = p.y + 0.4 - cy, dz = p.z - cz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		sim.viewer(cx, cy, cz, dx / d, dy / d, dz / d);
	}

	@Test
	void petBeatsHandsUpAndHandsUpWaitsAfterwards() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		int i = 60;
		for (int k = 0; k < 12; k++, i++) {
			look(sim, p);
			tick(sim, w, i, ALL, MS);
		}
		assertEquals(Pet.HANDSUP, p.state(), "angesehen: Hände hoch");
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.PET, i * 50L - 30));
		assertEquals(Pet.PET, p.state(), "Streicheln hat Vorrang");
		assertEquals(Pet.HANDSUP, p.prevState(), "blendet aus Hände hoch über");
		// die ganze Aktion lang angesehen – trotzdem keine Hände hoch
		int petted = 0;
		while (petted < 100) {
			look(sim, p);
			tick(sim, w, i++, ALL, MS);
			if (p.state() != Pet.PET) break;
			petted++;
		}
		assertEquals((MS[0] + 49) / 50, petted);
		assertNotEquals(Pet.HANDSUP, p.state());
		// wird er weiter angesehen: erst nach der Pause (8 s ab dem Ende) wieder Hände hoch
		int after = 1;
		while (p.state() != Pet.HANDSUP && after < 400) {
			look(sim, p);
			tick(sim, w, i++, ALL, MS);
			after++;
		}
		assertEquals(PetSim.HANDSUP_COOLDOWN, after, "Hände hoch erst nach der Pause");
	}

	@Test
	void theOwnerWalkingAwayEndsTheActionWithAFadeToWalk() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.PET, CLICK));
		int petted = 0, i = 60;
		for (int k = 0; k < 60 && p.state() == Pet.PET; k++, i++) {
			tick(sim, w, i, 0.5, 64, 0.5 + k * 0.15, ALL, MS);
			if (p.state() == Pet.PET) petted++;
		}
		assertTrue(petted > 5 && petted < (MS[0] + 49) / 50, "vorzeitig zu Ende: " + petted);
		assertEquals(Pet.WALK, p.state(), "läuft nach");
		assertEquals(Pet.PET, p.prevState());
		assertEquals(CLICK, p.prevStateSince());
		assertEquals(-1, p.action());
		assertTrue(p.interactCooldown > 0 && p.handsupCooldown > 0, "Pausen wie nach dem Ende");
	}

	@Test
	void aTeleportEndsTheAction() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		Pet p = settled(sim, w, ALL, MS);
		assertEquals(PetSim.STARTED, sim.interact(p, Pet.EAT, CLICK));
		tick(sim, w, 60, ALL, MS);
		tick(sim, w, 61, 40.5, 64, 0.5, ALL, MS);
		assertEquals(1, p.teleports);
		assertEquals(-1, p.action());
		assertFalse(p.state() == Pet.EAT, "beim Besitzer neu abgesetzt");
	}
}
