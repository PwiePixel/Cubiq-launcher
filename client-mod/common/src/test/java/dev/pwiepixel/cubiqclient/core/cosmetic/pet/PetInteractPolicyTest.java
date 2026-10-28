package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSimTest.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regeln für den Rechtsklick auf den eigenen Begleiter: welche Hand was tut, wann Minecraft den Klick behält, wie weit
 * gesucht wird – und dass ein Treffer auf den eigenen Begleiter den Klick IMMER schluckt (auch in der Pause oder während
 * einer laufenden Aktion), sonst würde das Essen in der Hand gegessen.
 */
class PetInteractPolicyTest {
	static final UUID OWNER = PetSimTest.OWNER;
	static final UUID OTHER = PetSimTest.OTHER;
	static final int E = PetInteract.HELD_EMPTY, F = PetInteract.HELD_FOOD, O = PetInteract.HELD_OTHER;
	private static final double FAR = Double.POSITIVE_INFINITY;

	@AfterEach
	void reset() {
		PetSim.forceSlide = Double.NaN;
	}

	@Test
	void theHandsDecidePetFeedOrMinecraft() {
		assertEquals(Pet.PET, PetInteract.PET);
		assertEquals(Pet.EAT, PetInteract.FEED);
		assertEquals(PetInteract.PET, PetInteract.action(E, E, false, false, false), "leere Hände: streicheln");
		assertEquals(PetInteract.PET, PetInteract.action(E, O, false, false, false), "Fackel/Schild in der Zweithand");
		assertEquals(PetInteract.FEED, PetInteract.action(E, F, false, false, false), "Essen nur in der Zweithand");
		assertEquals(PetInteract.FEED, PetInteract.action(F, E, false, false, false));
		assertEquals(PetInteract.FEED, PetInteract.action(F, O, false, false, false));
		assertEquals(PetInteract.FEED, PetInteract.action(F, F, false, false, false));
		assertEquals(PetInteract.NONE, PetInteract.action(O, E, false, false, false), "Block, Bogen, Schwert …: Minecraft");
		assertEquals(PetInteract.NONE, PetInteract.action(O, F, false, false, false), "Haupthand zuerst");
		assertEquals(PetInteract.NONE, PetInteract.action(O, O, false, false, false));
	}

	@Test
	void sneakingSpectatingOrAnOpenScreenLeaveTheClickToMinecraft() {
		for (int main : new int[] { E, F, O }) {
			for (int off : new int[] { E, F, O }) {
				assertEquals(PetInteract.NONE, PetInteract.action(main, off, true, false, false), "schleicht");
				assertEquals(PetInteract.NONE, PetInteract.action(main, off, false, true, false), "Zuschauer");
				assertEquals(PetInteract.NONE, PetInteract.action(main, off, false, false, true), "Bildschirm offen");
			}
		}
	}

	@Test
	void reachStopsAtMinecraftsOwnHit() {
		assertEquals(4.5, PetInteract.REACH, 0);
		assertEquals(PetInteract.REACH, PetInteract.reach(FAR), 0, "kein Treffer");
		assertEquals(PetInteract.REACH, PetInteract.reach(Double.NaN), 0);
		assertEquals(PetInteract.REACH, PetInteract.reach(7), 0, "Block weiter weg als 4,5");
		assertEquals(2.25, PetInteract.reach(2.25), 0, "Block/Wesen davor gewinnt");
		assertEquals(0, PetInteract.reach(0), 0);
	}

	// --- ganzer Ablauf mit der Simulation ---------------------------------------------------------------------

	private static void tick(PetSim sim, FakeWorld w, int i, UUID owner, boolean self) {
		tick(sim, w, i, owner, self, PetInteractTest.DRAWN);
	}

	private static void tick(PetSim sim, FakeWorld w, int i, UUID owner, boolean self, float[][] boxes) {
		sim.begin(w, i * 50L);
		sim.owner(owner, 1, 1.0, null, new int[] { PetInteractTest.ALL }, PetInteractTest.MS, boxes, 0.5, 64, 0.5, 0f, false,
				false, self, w);
		sim.end();
	}

	/** Augen des Besitzers (steht bei 0,5 | 64 | 0,5). */
	private static final double EX = 0.5, EY = 64 + 1.62, EZ = 0.5;

	/** {@link PetInteract#use} mit Blick auf die Mitte des Begleiters {@code p}. */
	private static boolean click(PetSim sim, Pet p, double vanilla, int main, int off, boolean sneaking, long now) {
		double dx = p.x - EX, dy = p.y + 0.3 - EY, dz = p.z - EZ;
		return PetInteract.use(sim, EX, EY, EZ, dx, dy, dz, vanilla, main, off, sneaking, false, false, 1f, now);
	}

	private static PetSim settled(FakeWorld w, UUID owner, boolean self) {
		PetSim sim = new PetSim();
		PetSim.forceSlide = 0;
		for (int i = 0; i < 60; i++) tick(sim, w, i, owner, self);
		return sim;
	}

	@Test
	void anEligibleHitOnTheOwnCompanionAlwaysSwallowsTheClick() {
		FakeWorld w = new FakeWorld();
		PetSim sim = settled(w, OWNER, true);
		Pet p = sim.pets(OWNER).get(0);
		long click = 59 * 50L + 20;
		assertTrue(click(sim, p, FAR, E, E, false, click), "gestreichelt");
		assertEquals(Pet.PET, p.state());
		// läuft schon: geschluckt, kein Neustart – auch mit Essen in der Hand (sonst würde es gegessen)
		assertTrue(click(sim, p, FAR, E, E, false, click + 100));
		assertTrue(click(sim, p, FAR, F, E, false, click + 150));
		assertEquals(Pet.PET, p.state());
		assertEquals(click, p.stateSince());
		int i = 60;
		while (p.state() == Pet.PET && i < 200) tick(sim, w, i++, OWNER, true);
		// kurze Pause danach: abgelehnt, aber trotzdem geschluckt
		assertTrue(click(sim, p, FAR, F, E, false, i * 50L));
		assertNotEquals(Pet.EAT, p.state(), "in der Pause beginnt nichts");
		for (int k = 0; k < PetSim.INTERACT_COOLDOWN; k++) tick(sim, w, i++, OWNER, true);
		assertTrue(click(sim, p, FAR, F, E, false, i * 50L), "gefüttert");
		assertEquals(Pet.EAT, p.state());
		// Block weiter hinten (Minecraft hätte ihn getroffen): der Begleiter ist näher und gewinnt
		assertTrue(click(sim, p, 4.0, E, E, false, i * 50L + 5));
	}

	@Test
	void everythingElseStaysWithMinecraft() {
		FakeWorld w = new FakeWorld();
		PetSim sim = settled(w, OWNER, true);
		Pet p = sim.pets(OWNER).get(0);
		long now = 59 * 50L + 20;
		assertFalse(click(sim, p, FAR, O, E, false, now), "anderer Gegenstand in der Hand");
		assertFalse(click(sim, p, FAR, E, E, true, now), "schleicht");
		assertFalse(PetInteract.use(sim, EX, EY, EZ, p.x - EX, p.y + 0.3 - EY, p.z - EZ, FAR, E, E, false, true, false, 1f, now),
				"Zuschauer");
		assertFalse(PetInteract.use(sim, EX, EY, EZ, p.x - EX, p.y + 0.3 - EY, p.z - EZ, FAR, E, E, false, false, true, 1f, now),
				"Bildschirm offen");
		assertFalse(click(sim, p, 1.0, E, E, false, now), "Block/Wesen vor dem Begleiter");
		assertFalse(PetInteract.use(sim, EX, EY, EZ, -(p.x - EX), 0, -(p.z - EZ), FAR, E, E, false, false, false, 1f, now),
				"weggeschaut");
		assertFalse(PetInteract.use(null, EX, EY, EZ, 0, 0, 1, FAR, E, E, false, false, false, 1f, now));
		assertEquals(Pet.IDLE, p.state(), "nichts angestoßen");
		// Begleiter eines anderen Spielers: nicht anklickbar, Minecraft behält den Klick
		PetSim other = settled(w, OTHER, false);
		Pet q = other.pets(OTHER).get(0);
		assertFalse(click(other, q, FAR, E, E, false, now));
		assertFalse(click(other, q, FAR, F, E, false, now));
		assertEquals(Pet.IDLE, q.state());
	}

	@Test
	void aCompanionInShortGrassIsFedInsteadOfMinecraftEatingTheFood() {
		FakeWorld w = new FakeWorld();
		PetSim sim = settled(w, OWNER, true);
		Pet p = sim.pets(OWNER).get(0);
		// mitten in einer Zelle mit kurzem Gras: sein Umriss (11,2 × 12,8 × 11,2 px) liegt ganz im Umriss des Grases
		p.x = p.px = -0.5;
		p.z = p.pz = -1.5;
		p.yaw = p.pyaw = 0;
		int bx = -1, by = 64, bz = -2;
		double[] eye = { EX, EY, EZ };
		double[] d = PetPickTest.dir(eye, p.x, p.y + 0.3, p.z);
		double grass = PetPickTest.outline(eye, d, bx, by, bz, PetPickTest.GRASS);
		assertTrue(grass < PetPick.ray(EX, EY, EZ, d[0], d[1], d[2], p.x, p.y, p.z, p.yaw, p.scale, p.box), "Gras zuerst getroffen");
		long now = 59 * 50L + 20;
		// Block mit Kollisionsform an derselben Stelle: Minecraft behält den Klick
		assertFalse(PetInteract.use(sim, EX, EY, EZ, d[0], d[1], d[2], grass, false, bx, by, bz, F, E, false, false, false, 1f, now));
		assertEquals(Pet.IDLE, p.state());
		// das Gras: der Begleiter wird gefüttert, der Klick geschluckt (Minecraft isst nichts)
		assertTrue(PetInteract.use(sim, EX, EY, EZ, d[0], d[1], d[2], grass, true, bx, by, bz, F, E, false, false, false, 1f, now));
		assertEquals(Pet.EAT, p.state());
	}

	@Test
	void anOwnCompanionThatIsNotDrawnDoesNotTakeTheClick() {
		// Modell noch nicht geladen (owner() ohne Umriss): petFrame zeichnet ihn nicht – der Klick geht an Minecraft, als
		// wäre dort nichts (sonst würde ein unsichtbarer Begleiter Klicks schlucken und Herzchen zeigen)
		FakeWorld w = new FakeWorld();
		PetSim sim = new PetSim();
		PetSim.forceSlide = 0;
		for (int i = 0; i < 60; i++) tick(sim, w, i, OWNER, true, new float[1][]);
		Pet p = sim.pets(OWNER).get(0);
		long now = 59 * 50L + 20;
		assertFalse(click(sim, p, FAR, E, E, false, now));
		assertFalse(click(sim, p, FAR, F, E, false, now));
		assertEquals(Pet.IDLE, p.state());
		assertEquals(-1, p.action());
		// sobald er gezeichnet wird (Umriss gemeldet), wieder wie gewohnt
		tick(sim, w, 60, OWNER, true);
		assertTrue(click(sim, p, FAR, F, E, false, 60 * 50L + 20));
		assertEquals(Pet.EAT, p.state());
	}
}
