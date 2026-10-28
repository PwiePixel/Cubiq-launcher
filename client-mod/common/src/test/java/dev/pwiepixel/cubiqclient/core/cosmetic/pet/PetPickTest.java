package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSimTest.FakeWorld;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
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
		// der Fuchs: steht auf y = 0 (−1 mit Rand) und ist länger als breit
		float[] fox = PetPick.bounds(PetActionsFormatTest.foxModel());
		assertEquals(-1, fox[1], 1e-3, "untere Kante");
		assertTrue(fox[4] > 10 && fox[4] < 14, "Höhe " + fox[4]);
		assertTrue(fox[5] - fox[2] > fox[3] - fox[0], "länger als breit");
	}
}
