package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSimTest.FakeWorld;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Klick-Test für Streicheln/Füttern: Strahl vom Auge gegen den Umriss des Begleiters in Ruhe, mit derselben Umrechnung
 * wie beim Zeichnen ({@link PetDraw}) – Gieren, Größe, schwebende Begleiter; Auswahl nur unter den eigenen, der nächste
 * gewinnt; Umriss aus dem Modell samt Ruhe-Drehung der Knochen.
 */
class PetPickTest {
	static final UUID OWNER = PetSimTest.OWNER;
	static final UUID OTHER = PetSimTest.OTHER;
	/** 8 × 12 × 8 Pixel: ±0,25 Blöcke breit, 0,75 hoch. */
	static final float[] BOX = { -4, 0, -4, 4, 12, 4 };
	/** Lang nach vorne (z ±8 px = ±0,5 Blöcke), schmal (x ±2 px = ±0,125), flach (4 px). */
	static final float[] LONG = { -2, 0, -8, 2, 4, 8 };
	/** Schwebt wie das Glühwürmchen (y 5,25…11,31 px). */
	static final float[] HOVER = { -4.6f, 5.25f, -5.75f, 4.6f, 11.31f, 3.71f };
	private static final double EPS = 1e-9;

	@AfterEach
	void reset() {
		PetSim.forceSlide = Double.NaN;
	}

	private static double ray(double ex, double ey, double ez, double dx, double dy, double dz, double yaw, double scale,
			float[] box) {
		return PetPick.ray(ex, ey, ez, dx, dy, dz, 0, 64, 0, yaw, scale, box);
	}

	@Test
	void hitsFromTheFrontTheSideAndAboveAndMissesBesideAndBehind() {
		assertEquals(2.75, ray(0, 64.4, 3, 0, 0, -1, 0, 1, BOX), EPS, "von vorne");
		assertEquals(2.75, ray(3, 64.4, 0, -1, 0, 0, 0, 1, BOX), EPS, "von der Seite");
		assertEquals(2.25, ray(0, 67, 0, 0, -1, 0, 0, 1, BOX), EPS, "von oben");
		assertTrue(Double.isNaN(ray(1, 64.4, 3, 0, 0, -1, 0, 1, BOX)), "daneben");
		assertTrue(Double.isNaN(ray(0, 64.4, 3, 0, 0, 1, 0, 1, BOX)), "weggeschaut (Kasten hinter dem Auge)");
		assertTrue(Double.isNaN(ray(0, 65.0, 3, 0, 0, -1, 0, 1, BOX)), "darüber hinweg");
		assertEquals(0, ray(0, 64.4, 0, 0, 0, -1, 0, 1, BOX), EPS, "Auge im Kasten");
		// schräg von oben auf die Mitte: trifft vor der Mitte
		double cx = -2, cy = 66, cz = 2, tx = 0, ty = 64.375, tz = 0;
		double d = Math.sqrt((tx - cx) * (tx - cx) + (ty - cy) * (ty - cy) + (tz - cz) * (tz - cz));
		double t = ray(cx, cy, cz, (tx - cx) / d, (ty - cy) / d, (tz - cz) / d, 0, 1, BOX);
		assertTrue(t > 0 && t < d, "schräg: " + t);
		// ohne Umriss: Radius/Höhe der Simulation
		assertEquals(3 - PetSim.RADIUS, PetPick.ray(0, 64.4, 3, 0, 0, -1, 0, 64, 0, 0, 1, null), 1e-6);
	}

	@Test
	void yawTurnsTheBoxLikeTheDrawnModel() {
		// Gieren 0: vorne = +z → lang entlang z
		assertEquals(3 - 0.125, ray(3, 64.1, 0.4, -1, 0, 0, 0, 1, LONG), EPS);
		assertTrue(Double.isNaN(ray(0.4, 64.1, 3, 0, 0, -1, 0, 1, LONG)));
		// Gieren 90: vorne = −x → lang entlang x
		assertTrue(Double.isNaN(ray(3, 64.1, 0.4, -1, 0, 0, 90, 1, LONG)));
		assertEquals(3 - 0.125, ray(0.4, 64.1, 3, 0, 0, -1, 90, 1, LONG), EPS);
		// Gieren −135: vorne = (sin 135°, 0, cos −135°) – lang entlang der Diagonale (1, 0, −1)
		double s = Math.sqrt(0.5);
		assertEquals(2 * Math.sqrt(2) - 0.5, ray(2, 64.1, -2, -s, 0, s, -135, 1, LONG), 1e-6, "Ende der langen Achse");
		assertEquals(2 * Math.sqrt(2) - 0.125, ray(2, 64.1, 2, -s, 0, -s, -135, 1, LONG), 1e-6, "schmale Seite");
	}

	/** Welt-Position (Begleiter am Ursprung) eines Modellpunkts in Pixeln – über {@link PetDraw}, also wie gezeichnet. */
	static double[] world(double x, double y, double z, double yaw, double scale) {
		PetDraw<String> d = new PetDraw<String>();
		d.set(0, 0, 0, yaw, scale);
		final double[] out = new double[3];
		d.wrap((wx, wy, wz, u, v, nx, ny, nz, argb) -> {
			out[0] = wx;
			out[1] = wy;
			out[2] = wz;
		}).vertex((float) (x / 16), (float) (-y / 16), (float) (-z / 16), 0, 0, 0, 0, 0, -1);
		return out;
	}

	@Test
	void sameAxesAsTheDrawnModelForEveryYawAndScale() {
		for (int yaw = -180; yaw < 180; yaw += 15) {
			for (double scale : new double[] { 0.5, 1, 1.25, 2 }) {
				double h = Math.toRadians(yaw);
				double fx = -Math.sin(h), fz = Math.cos(h); // vorne in der Welt
				// Strahl entlang „vorne“ durch die Mitte des Kastens: Treffer; 1,5 px links daneben: vorbei
				double[] in = world(0, 6, 0, yaw, scale);
				double[] out = world(BOX[3] + 1.5, 6, 0, yaw, scale);
				double t = PetPick.ray(in[0] - fx * 3, in[1], in[2] - fz * 3, fx, 0, fz, 0, 0, 0, yaw, scale, BOX);
				assertEquals(3 - 0.25 * scale, t, 1e-4, "Gieren " + yaw + ", Größe " + scale);
				assertTrue(Double.isNaN(PetPick.ray(out[0] - fx * 3, out[1], out[2] - fz * 3, fx, 0, fz, 0, 0, 0, yaw, scale, BOX)),
						"daneben: Gieren " + yaw + ", Größe " + scale);
			}
		}
	}

	@Test
	void scaleGrowsAndShrinksTheBox() {
		assertEquals(3 - 0.5, ray(0, 64.4, 3, 0, 0, -1, 0, 2, BOX), EPS, "doppelt so groß");
		assertEquals(3 - 0.3125, ray(0, 64.4, 3, 0, 0, -1, 0, 1.25, BOX), EPS, "Kaiserpinguin");
		assertEquals(3 - 0.125, ray(0, 64.2, 3, 0, 0, -1, 0, 0.5, BOX), EPS, "halb so groß");
		assertTrue(Double.isNaN(ray(0.2, 64.2, 3, 0, 0, -1, 0, 0.5, BOX)), "klein: daneben");
		assertTrue(ray(0.2, 64.2, 3, 0, 0, -1, 0, 1, BOX) > 0, "normal: getroffen");
		assertTrue(Double.isNaN(ray(0, 65.2, 3, 0, 0, -1, 0, 1, BOX)), "normal: darüber");
		assertTrue(ray(0, 65.2, 3, 0, 0, -1, 0, 2, BOX) > 0, "groß: getroffen");
	}

	@Test
	void aHoveringCompanionIsNotHitAtItsFeet() {
		assertTrue(Double.isNaN(ray(0, 64.1, 3, 0, 0, -1, 0, 1, HOVER)), "unter dem Glühwürmchen durch");
		assertEquals(3 - 3.71 / 16, ray(0, 64.5, 3, 0, 0, -1, 0, 1, HOVER), 1e-6);
	}

	// --- Auswahl in der Simulation ----------------------------------------------------------------------------

	private static void tick(PetSim sim, FakeWorld w, int i, boolean hidden) {
		sim.begin(w, i * 50L);
		sim.owner(OWNER, 2, 1.0, null, null, null, new float[][] { BOX }, 0.5, 64, 0.5, 0f, false, hidden, true, w);
		sim.owner(OTHER, 1, 1.0, null, null, 0.5, 64, 0.5, 0f, false, false, false, w);
		sim.end();
	}

	private static void put(Pet p, double x, double z) {
		p.x = p.px = x;
		p.y = p.py = 64;
		p.z = p.pz = z;
		p.yaw = p.pyaw = 0;
	}

	@Test
	void picksTheNearestOwnCompanionAndIgnoresOthers() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		PetSim.forceSlide = 0;
		tick(sim, w, 0, false);
		List<Pet> own = sim.pets(OWNER);
		Pet a = own.get(0), b = own.get(1), foreign = sim.pets(OTHER).get(0);
		assertArrayEquals(BOX, a.box, 0f, "Umriss je Teil aus owner()");
		assertNull(foreign.box, "ohne Umriss: Radius/Höhe");
		put(foreign, 0.5, 1.5);
		put(a, 0.5, 2.5);
		put(b, 0.5, 4.5);
		// Blick nach Süden (+z): der fremde steht näher, zählt aber nicht; vom Eigenen gewinnt der nähere
		assertSame(a, sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
		assertSame(a, sim.pick(0.5, 64.4, 0, 0, 0, 5, PetInteract.REACH, 1f), "Richtung wird normiert");
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, 2.0, 1f), "weiter als erlaubt (Treffer bei 2,25)");
		assertSame(a, sim.pick(0.5, 64.4, 0, 0, 0, 1, 2.3, 1f));
		put(a, 3.5, 2.5);
		assertSame(b, sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f), "dahinter der nächste eigene");
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, -1, PetInteract.REACH, 1f), "weggeschaut");
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 0, PetInteract.REACH, 1f), "keine Richtung");
		// gezeichnete Stelle zwischen zwei Ticks: alpha 0 = voriger Tick
		put(b, 0.5, 4.5);
		b.px = 6.5;
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 0f));
		assertSame(b, sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
	}

	@Test
	void hiddenOrUnreportedOwnersCannotBePicked() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		PetSim.forceSlide = 0;
		tick(sim, w, 0, false);
		Pet a = sim.pets(OWNER).get(0);
		put(a, 0.5, 2.5);
		assertSame(a, sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
		// unsichtbar (Trank, Zuschauer): nicht anklickbar
		tick(sim, w, 1, true);
		put(a, 0.5, 2.5);
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
		// in diesem Tick nicht gemeldet
		sim.begin(w, 100);
		sim.end();
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
	}

	@Test
	void anOwnCompanionThatIsNotDrawnCannotBePicked() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		PetSim.forceSlide = 0;
		// Haupt-Teil gezeichnet, das Extra (kind 1) noch ohne Modell: kein Umriss
		sim.begin(w, 0);
		sim.owner(OWNER, 1, 1.0, new double[] { 1.0 }, null, null, new float[][] { BOX, null }, 0.5, 64, 0.5, 0f, false, false,
				true, w);
		sim.end();
		List<Pet> own = sim.pets(OWNER);
		Pet drawn = own.get(0), missing = own.get(1);
		assertNull(missing.box);
		put(missing, 0.5, 2.5);
		put(drawn, 0.5, 4.5);
		assertSame(drawn, sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f), "der nicht gezeichnete davor ist durchlässig");
		put(drawn, 3.5, 4.5);
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f), "nichts Gezeichnetes im Blick");
		// ohne Umrisse überhaupt (ältere owner()-Aufrufe): niemand anklickbar
		sim.begin(w, 50);
		sim.owner(OWNER, 1, 1.0, null, 0.5, 64, 0.5, 0f, false, true, w);
		sim.end();
		Pet only = sim.pets(OWNER).get(0);
		put(only, 0.5, 2.5);
		assertNull(sim.pick(0.5, 64.4, 0, 0, 0, 1, PetInteract.REACH, 1f));
	}

	// --- Minecrafts Treffer: Pflanzen um den Begleiter sind durchsichtig (PetSight) ------------------------------------

	/** Umrisse in einer Blockzelle (Pixel 0…16), so wie Minecrafts Klick-Strahl sie trifft. */
	static final double[] GRASS = { 2, 0, 2, 14, 13, 14 }, FLOWER = { 5, 0, 5, 11, 10, 11 }, FULL = { 0, 0, 0, 16, 16, 16 },
			LEVER = { 5, 3, 10, 11, 13, 16 };

	/** Eintritt des Strahls in den Umriss {@code px16} der Zelle (bx, by, bz) in Blöcken, unendlich ohne Treffer. */
	static double outline(double[] eye, double[] dir, int bx, int by, int bz, double[] px16) {
		double[] lo = { bx + px16[0] / 16, by + px16[1] / 16, bz + px16[2] / 16 };
		double[] hi = { bx + px16[3] / 16, by + px16[4] / 16, bz + px16[5] / 16 };
		double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
		for (int a = 0; a < 3; a++) {
			if (Math.abs(dir[a]) < 1e-12) {
				if (eye[a] < lo[a] || eye[a] > hi[a]) return Double.POSITIVE_INFINITY;
				continue;
			}
			double t1 = (lo[a] - eye[a]) / dir[a], t2 = (hi[a] - eye[a]) / dir[a];
			near = Math.max(near, Math.min(t1, t2));
			far = Math.min(far, Math.max(t1, t2));
		}
		return near > far || far < 0 ? Double.POSITIVE_INFINITY : Math.max(0, near);
	}

	/** Normierte Richtung von {@code from} nach {@code to}. */
	static double[] dir(double[] from, double... to) {
		double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2], d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		return new double[] { dx / d, dy / d, dz / d };
	}

	/**
	 * Klick-Test wie {@link PetInteract#use}: bis {@link PetSight#reach} – Minecrafts Treffer {@code vanilla} ist eine
	 * durchsichtige Pflanze, wenn {@code plant}; gesucht wird dann in der Welt {@code world} weiter.
	 */
	private static Pet pick(PetSim sim, double[] eye, double[] d, double vanilla, boolean plant, PetSightTest.Grid world) {
		double reach = PetSight.reach(eye[0], eye[1], eye[2], d[0], d[1], d[2], vanilla, plant, world.aim(eye, d));
		return sim.pick(eye[0], eye[1], eye[2], d[0], d[1], d[2], reach, 1f);
	}

	@Test
	void aCompanionStandingInGrassIsStillPickedButBlocksInFrontStillWin() {
		PetSim sim = new PetSim();
		FakeWorld w = new FakeWorld();
		PetSim.forceSlide = 0;
		tick(sim, w, 0, false);
		Pet a = sim.pets(OWNER).get(0), b = sim.pets(OWNER).get(1);
		put(a, 0.5, 2.5); // mitten in der Zelle (0, 64, 2): 8 × 12 × 8 px passen ganz in den Umriss des Grases (12 × 13 × 12)
		put(b, 6.5, 9.5);
		double[] eye = { 0.5, 64 + 1.62, -0.5 };
		double[] d = dir(eye, 0.5, 64.2, 2.5);
		double pet = PetPick.ray(eye[0], eye[1], eye[2], d[0], d[1], d[2], 0.5, 64, 2.5, 0, 1, BOX);
		assertTrue(pet > 2.5 && pet < 3.5, "Begleiter getroffen: " + pet);
		PetSightTest.Grid grass = new PetSightTest.Grid().put(0, 64, 2, PetSightTest.GRASS);

		// kurzes Gras in seiner Zelle: Minecraft trifft dessen Umriss zuerst
		double inGrass = outline(eye, d, 0, 64, 2, GRASS);
		assertTrue(inGrass < pet, "Gras vor dem Begleiter: " + inGrass + " < " + pet);
		assertNull(sim.pick(eye[0], eye[1], eye[2], d[0], d[1], d[2], PetInteract.reach(inGrass), 1f), "bisher: verfehlt");
		assertSame(a, pick(sim, eye, d, inGrass, true, grass), "Gras ohne Kollisionsform zählt nicht");
		assertNull(pick(sim, eye, d, inGrass, false, grass), "mit Kollisionsform gewinnt der Block");

		// fester Block in der Zelle davor gewinnt
		double wall = outline(eye, d, 0, 64, 1, FULL);
		assertTrue(wall < pet);
		assertNull(pick(sim, eye, d, wall, false, new PetSightTest.Grid().put(0, 64, 1, PetSightTest.STONE).put(0, 64, 2,
				PetSightTest.GRASS)));
		// eine Blume in der Zelle davor ist ebenso durchsichtig wie das Gras in seiner eigenen …
		double flower = outline(eye, d, 0, 64, 1, FLOWER);
		assertTrue(flower < inGrass, "Blume davor: " + flower);
		assertSame(a, pick(sim, eye, d, flower, true, new PetSightTest.Grid().put(0, 64, 1, PetSightTest.FLOWER).put(0, 64, 2,
				PetSightTest.GRASS)));
		// … ein Hebel dort (keine Pflanze, auch ohne Kollisionsform) gewinnt weiter
		double lever = outline(eye, d, 0, 64, 1, LEVER);
		assertTrue(lever < pet, "Hebel davor: " + lever);
		assertNull(pick(sim, eye, d, lever, false, new PetSightTest.Grid().put(0, 64, 1, PetSightTest.LEVER)));
		// … auch hinter einer Blume
		assertNull(pick(sim, eye, d, flower, true, new PetSightTest.Grid().put(0, 64, 1, PetSightTest.FLOWER).put(0, 64, 2,
				new PetSightTest.Block("Knopf", false, false, true, 0, new double[] { 5, 6, 0, 11, 10, 2 }))));
		// steht er am Rand der Zelle, ragt er in die davor – das Gras dort ist ebenso durchsichtig
		put(a, 0.5, 2.1);
		double[] d2 = dir(eye, 0.5, 64.2, 2.1);
		double edge = outline(eye, d2, 0, 64, 1, GRASS);
		assertTrue(edge < PetPick.ray(eye[0], eye[1], eye[2], d2[0], d2[1], d2[2], 0.5, 64, 2.1, 0, 1, BOX));
		assertSame(a, pick(sim, eye, d2, edge, true, new PetSightTest.Grid().put(0, 64, 1, PetSightTest.GRASS)));
		put(a, 0.5, 2.5);

		// Blöcke hinter dem Begleiter spielen keine Rolle, fest oder nicht
		double behind = outline(eye, d, 0, 64, 3, FULL);
		assertTrue(behind > pet);
		assertSame(a, pick(sim, eye, d, behind, false, new PetSightTest.Grid()));
		assertSame(a, pick(sim, eye, d, outline(eye, d, 0, 64, 3, GRASS), true, new PetSightTest.Grid().put(0, 64, 3,
				PetSightTest.GRASS)));
		assertSame(a, pick(sim, eye, d, Double.POSITIVE_INFINITY, false, new PetSightTest.Grid()), "kein Treffer");

		// der Strahl muss den Begleiter selbst treffen: daneben ins Gras geschaut bleibt Minecrafts Klick
		double[] beside = dir(eye, 0.2, 64.3, 2.2);
		assertTrue(Double.isNaN(PetPick.ray(eye[0], eye[1], eye[2], beside[0], beside[1], beside[2], 0.5, 64, 2.5, 0, 1, BOX)));
		assertNull(pick(sim, eye, beside, outline(eye, beside, 0, 64, 2, GRASS), true, grass));
		// zu weit weg (mehr als 4,5 Blöcke): auch im Gras nicht
		put(a, 0.5, 5.5);
		double[] far = dir(eye, 0.5, 64.2, 5.5);
		assertNull(pick(sim, eye, far, outline(eye, far, 0, 64, 5, GRASS), true, new PetSightTest.Grid().put(0, 64, 5,
				PetSightTest.GRASS)));
	}

	// --- Umriss aus dem Modell --------------------------------------------------------------------------------

	/** Körper 4 × 6 × 4 und ein um 90° (z) gedrehter Arm am Knochen {@code arm} – zeigt in Ruhe zur Seite. */
	static String armModel() {
		String faces = ",\"faces\":{\"north\":{\"uv\":[0,0,4,6]},\"south\":{\"uv\":[4,0,8,6]}}}";
		return "{\"format\":2,\"id\":\"test_arm\",\"name\":\"Test\",\"slot\":\"pet\",\"attach\":\"ground\","
				+ "\"texture\":{\"file\":\"t.png\",\"width\":16,\"height\":16,\"scale\":1},"
				+ "\"bones\":[{\"id\":\"body\",\"pivot\":[0,0,0]},{\"id\":\"arm\",\"parent\":\"body\",\"pivot\":[0,8,0],\"rotation\":[0,0,90]}],"
				+ "\"cubes\":[{\"id\":\"b\",\"bone\":\"body\",\"from\":[-2,0,-2],\"to\":[2,6,2]" + faces + ","
				+ "{\"id\":\"a\",\"bone\":\"arm\",\"from\":[0,4,-1],\"to\":[1,8,1]" + faces + "]}";
	}

	@Test
	void restBoundsFollowTheRotatedRestPoseWithMarginAndMinimumSize() throws IOException {
		CosmeticV2 arm = CosmeticV2.parse(armModel());
		float[] b = PetPick.bounds(arm);
		// Arm: Rz(+90) dreht +x nach +y → um den Drehpunkt (0, 8) liegt er bei x 0…4, y 8…9; dazu der Körper, +1 px Rand
		assertArrayEquals(new float[] { -3, -1, -3, 5, 10, 3 }, b, 1e-4f);
		assertSame(b, PetPick.bounds(arm), "gemerkt je Modell");
		// winzig: auf 6 px je Achse um die Mitte vergrößert
		String tiny = armModel().replace("[-2,0,-2],\"to\":[2,6,2]", "[-0.5,0,-0.5],\"to\":[0.5,1,0.5]")
				.replace(",{\"id\":\"a\",\"bone\":\"arm\",\"from\":[0,4,-1],\"to\":[1,8,1],\"faces\":{\"north\":{\"uv\":[0,0,4,6]},\"south\":{\"uv\":[4,0,8,6]}}}", "")
				.replace("[0,0,4,6]", "[0,0,1,1]").replace("[4,0,8,6]", "[1,0,2,1]");
		assertArrayEquals(new float[] { -3, -2.5f, -3, 3, 3.5f, 3 }, PetPick.bounds(CosmeticV2.parse(tiny)), 1e-4f);
		assertSame(PetPick.DEFAULT_BOX, PetPick.bounds(null));
		assertArrayEquals(new float[] { -5.6f, 0, -5.6f, 5.6f, 12.8f, 5.6f }, PetPick.DEFAULT_BOX, 1e-4f);
		// die Füchse: der Umriss ist die Ruhe-Pose ihrer Würfel + 1 px Rand (jede Achse ist größer als 6 px); versteckte
		// Herzchen/Krümel (fx-Knochen, in Ruhe im Körper) machen ihn nicht größer. Er steht auf y = 0 (−1 mit Rand) und ist
		// länger als breit.
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			float[] fox = PetPick.bounds(m);
			double[] all = restExtent(m, false), body = restExtent(m, true);
			for (int a = 0; a < 3; a++) {
				assertEquals(all[a] - 1, fox[a], 1e-4, file + " min " + a);
				assertEquals(all[a + 3] + 1, fox[a + 3], 1e-4, file + " max " + a);
				assertTrue(fox[a + 3] - fox[a] > PetPick.MIN_SIZE_PX, file);
				assertEquals(body[a], all[a], 1e-9, file + ": fx-Würfel liegen im Körper");
				assertEquals(body[a + 3], all[a + 3], 1e-9, file);
			}
			assertEquals(-1, fox[1], 1e-3, file + ": untere Kante");
			assertTrue(fox[5] - fox[2] > fox[3] - fox[0], file + ": länger als breit");
		}
	}

	/** Ausdehnung {minX, minY, minZ, maxX, maxY, maxZ} der Würfel in Ruhe (Pixel), ohne {@code fx_*}-Knochen auf Wunsch. */
	static double[] restExtent(CosmeticV2 m, boolean skipFx) {
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 0, false);
		double[] e = { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
		for (CosmeticV2.Cube c : m.cubes) {
			if (skipFx && m.bones.get(c.bone).id.startsWith("fx_")) continue;
			double[] w = r.world(c.bone);
			for (double x : new double[] { c.x0 - c.inflate, c.x1 + c.inflate }) {
				for (double y : new double[] { c.y0 - c.inflate, c.y1 + c.inflate }) {
					for (double z : new double[] { c.z0 - c.inflate, c.z1 + c.inflate }) {
						double[] p = { w[0] * x + w[4] * y + w[8] * z + w[12], w[1] * x + w[5] * y + w[9] * z + w[13],
								w[2] * x + w[6] * y + w[10] * z + w[14] };
						for (int a = 0; a < 3; a++) {
							e[a] = Math.min(e[a], p[a]);
							e[a + 3] = Math.max(e[a + 3], p[a]);
						}
					}
				}
			}
		}
		return e;
	}
}
