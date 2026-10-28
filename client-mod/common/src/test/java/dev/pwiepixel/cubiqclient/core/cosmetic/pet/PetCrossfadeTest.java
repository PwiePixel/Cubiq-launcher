package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Überblendung zwischen zwei Zuständen eines Begleiters, jede Seite auf ihrer eigenen Zeitbasis: Bewegung nach der Uhr
 * (+ Versatz), {@code grab} und die Aktionen ab ihrem eigenen Beginn. Bis 0.23.0 lief die ausgehende Seite auf der Zeit
 * der eingehenden – der sitzende Fuchs stand beim Schnappen schlagartig auf, am Ende des Schnappens sprang er in eine
 * zufällige Phase. Mit dem eingefrorenen fox_kit (siehe {@link PetActionsFormatTest#fox}).
 */
class PetCrossfadeTest {
	/** Ein Bild bei 60 fps. */
	private static final long FRAME = 16;

	/** Alle Ecken aller Flächen nach der letzten Pose (Anhängepunkt-Raum, Pixel). */
	static double[] corners(CosmeticV2Renderer r, CosmeticV2 m) {
		final List<Double> list = new ArrayList<Double>();
		r.faces(m, -1, (c, uv, material, nx, ny, nz) -> {
			for (double v : c) list.add(v);
		});
		double[] out = new double[list.size()];
		for (int i = 0; i < out.length; i++) out[i] = list.get(i);
		return out;
	}

	/** Größte Verschiebung einer Ecke zwischen zwei Posen (Pixel). */
	static double shift(double[] a, double[] b) {
		assertEquals(a.length, b.length);
		double max = 0;
		for (int i = 0; i < a.length; i++) max = Math.max(max, Math.abs(a[i] - b[i]));
		return max;
	}

	/** Überblendung {@code dt} ms nach dem Wechsel (wie {@link Pet#blend}). */
	static float fade(long dt) {
		Pet p = new Pet(PetSimTest.OWNER, 0, 1L);
		p.setState(Pet.WALK, 0);
		return p.blend(dt);
	}

	private static double[] pose(CosmeticV2Renderer r, CosmeticV2 m, long t, long prevT, int driver, int prevDriver, float blend) {
		r.pose(m, t, prevT, true, false, driver, prevDriver, blend, 0L);
		return corners(r, m);
	}

	private static int bone(CosmeticV2 m, String id) {
		for (int i = 0; i < m.bones.size(); i++) if (m.bones.get(i).id.equals(id)) return i;
		throw new AssertionError(id);
	}

	@Test
	void petStartsFromTheSittingIdleWithoutAPop() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		long clock = 8000; // idle sitzt hier (etwa 3–13,6 s der 16-s-Schleife)
		double[] before = pose(r, m, clock, clock, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f);
		double[] rest = pose(r, m, 0, 0, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f);
		assertTrue(shift(before, rest) > 3, "Probe: der Fuchs sitzt wirklich");
		// erstes Bild nach dem Klick: PET ab seinem Beginn, das ausgehende idle nach der Uhr weiter
		double[] first = pose(r, m, FRAME, clock + FRAME, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, fade(FRAME));
		assertTrue(shift(before, first) < 0.3, "kein Sprung: " + shift(before, first));
		// bis 0.23.0: beide Seiten auf der Zeit der Aktion – idle sprang auf Phase 0 (stand plötzlich)
		double[] old = pose(r, m, FRAME, FRAME, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, fade(FRAME));
		assertTrue(shift(before, old) > 3, "alte Zeitbasis springt: " + shift(before, old));
		// der ganze Übergang Bild für Bild ohne Sprung
		double[] last = before;
		for (long dt = FRAME; dt <= 400; dt += FRAME) {
			double[] now = pose(r, m, dt, clock + dt, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, fade(dt));
			assertTrue(shift(last, now) < 1.5, "dt=" + dt + ": " + shift(last, now));
			last = now;
		}
	}

	@Test
	void grabEndsFromItsLastFrameNotFromARandomPhase() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		long grab = PetSim.GRAB_TICKS * 50L; // GRAB endet nach 2400 ms (Clip 2600 ms, wiederholt sich)
		long clock = 6200; // Uhr des Begleiters beim Wechsel zu FLEE: 6200 mod 2600 = 1000 – ganz andere Phase
		double[] last = pose(r, m, grab, grab, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_GRAB, 1f);
		double[] first = pose(r, m, clock + FRAME, grab + FRAME, CosmeticV2.DRIVER_FLEE, CosmeticV2.DRIVER_GRAB, fade(FRAME));
		assertTrue(shift(last, first) < 1.0, "weiter ab dem letzten Bild: " + shift(last, first));
		double[] old = pose(r, m, clock + FRAME, clock + FRAME, CosmeticV2.DRIVER_FLEE, CosmeticV2.DRIVER_GRAB, fade(FRAME));
		assertTrue(shift(last, old) > 3, "alte Zeitbasis springt: " + shift(last, old));
	}

	@Test
	void anActionEndsIntoTheNextStateFromItsOwnTime() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		long len = m.action(CosmeticV2.DRIVER_PET).lengthMs;
		long clock = 5000; // idle sitzt
		// zu Ende gespielt: das letzte Bild ist die Ruhe, das Ausblenden hält es (auf len begrenzt)
		double[] end = pose(r, m, len, len, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f);
		double[] next = pose(r, m, clock + FRAME, len + FRAME, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_PET, fade(FRAME));
		assertTrue(shift(end, next) < 0.3, "Ende → idle: " + shift(end, next));
		// vorzeitig abgebrochen (Besitzer läuft weg): die Aktion läuft beim Ausblenden auf ihrer Zeit weiter
		long cut = 1000;
		double[] at = pose(r, m, cut, cut, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f);
		double[] walk = pose(r, m, clock + FRAME, cut + FRAME, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_PET, fade(FRAME));
		assertTrue(shift(at, walk) < 0.6, "Abbruch → walk: " + shift(at, walk));
	}

	@Test
	void actionsPlayAloneAndClampToTheirLength() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 0, false);
		double[] rest = corners(r, m);
		// Beginn und Ende in Ruhe, davor/danach begrenzt
		assertTrue(shift(rest, pose(r, m, -500, -500, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f)) < 1e-9);
		assertTrue(shift(rest, pose(r, m, 99_999, 99_999, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f)) < 1e-9);
		assertTrue(shift(rest, pose(r, m, 99_999, 99_999, CosmeticV2.DRIVER_EAT, CosmeticV2.DRIVER_EAT, 1f)) < 1e-9);
		// allein: idle bewegt den Wurzel-Knochen (Wippen) – während PET bleibt er in Ruhe
		int root = bone(m, "root");
		r.pose(m, 0, false);
		double[] restRoot = r.world(root).clone();
		r.pose(m, 1000, 1000, true, false, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f, 0L);
		boolean idleMoves = false;
		for (int i = 0; i < 16; i++) idleMoves |= Math.abs(r.world(root)[i] - restRoot[i]) > 1e-6;
		r.pose(m, 1000, 1000, true, false, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f, 0L);
		assertArrayEquals(restRoot, r.world(root), 1e-9, "keine Animation dazugemischt");
		// Probe: im Ruhe-Zeitpunkt 1000 bewegt idle die Wurzel wirklich – sonst sagt der Vergleich nichts
		for (long t = 0; t < 16000 && !idleMoves; t += 250) {
			r.pose(m, t, t, true, false, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f, 0L);
			for (int i = 0; i < 16; i++) idleMoves |= Math.abs(r.world(root)[i] - restRoot[i]) > 1e-6;
		}
		assertTrue(idleMoves, "idle bewegt die Wurzel");
		// mitten in der Aktion sieht man sie
		assertTrue(shift(rest, pose(r, m, 1000, 1000, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f)) > 1);
	}

	@Test
	void withoutTheActionIdlePlaysOnTheClock() throws IOException {
		CosmeticV2 with = PetActionsFormatTest.foxModel();
		CosmeticV2 without = CosmeticV2.parse(PetActionsFormatTest.foxWithout());
		long since = 500, clock = 9000;
		// Zeitbasis nach dem Treiber, der wirklich spielt
		assertEquals(since, Pet.poseTime(with, Pet.PET, since, clock));
		assertEquals(since, Pet.poseTime(with, Pet.EAT, since, clock));
		assertEquals(clock, Pet.poseTime(without, Pet.PET, since, clock), "ohne Aktion: idle nach der Uhr, kein Sprung auf 0");
		assertEquals(clock, Pet.poseTime(without, Pet.EAT, since, clock));
		assertEquals(since, Pet.poseTime(without, Pet.GRAB, since, clock), "der Fuchs kann grab");
		assertEquals(clock, Pet.poseTime(with, Pet.IDLE, since, clock));
		assertEquals(clock, Pet.poseTime(with, Pet.FLEE, since, clock));
		assertEquals(0, Pet.poseTime(with, Pet.PET, -20, clock), "nie vor dem Beginn");
		CosmeticV2 plain = CosmeticV2.parse(PetHeistTest.model("idle", "walk"));
		assertEquals(clock, Pet.poseTime(plain, Pet.GRAB, since, clock), "ohne grab: idle nach der Uhr");
		assertEquals(clock, Pet.poseTime(null, Pet.PET, since, clock));
		// gezeichnet: PET ohne Aktion im Modell = idle
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		double[] idle = pose(r, without, clock, clock, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f);
		double[] pet = pose(r, without, clock, clock, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, 0.4f);
		assertTrue(shift(idle, pet) < 1e-9);
		assertEquals(CosmeticV2.DRIVER_IDLE, CosmeticV2Renderer.effectiveDriver(without, CosmeticV2.DRIVER_PET));
		assertEquals(CosmeticV2.DRIVER_PET, CosmeticV2Renderer.effectiveDriver(with, CosmeticV2.DRIVER_PET));
	}

	@Test
	void singleTimeOverloadsAreUnchanged() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		CosmeticV2Renderer a = new CosmeticV2Renderer();
		CosmeticV2Renderer b = new CosmeticV2Renderer();
		int[] drivers = { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_PET };
		for (long t = 0; t < 20000; t += 777) {
			for (int d : drivers) {
				for (int pd : drivers) {
					a.pose(m, t, true, false, d, pd, 0.37f, 0L);
					b.pose(m, t, t, true, false, d, pd, 0.37f, 0L);
					for (int i = 0; i < m.bones.size(); i++) assertArrayEquals(a.world(i), b.world(i), 0.0);
				}
			}
			a.pose(m, t, true);
			b.pose(m, t, t, true, false, 0, 0, 1f, 0L);
			for (int i = 0; i < m.bones.size(); i++) assertArrayEquals(a.world(i), b.world(i), 0.0);
		}
	}

	@Test
	void v2HatCarriesThePetClockAndBothPoseTimes() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel();
		// Hüte/Flügel: eine Zeit für alles
		V2Hat<String> hat = new V2Hat<String>(m, "base", null, null, 5000L, false);
		assertEquals(5000L, hat.now);
		assertEquals(5000L, hat.poseMs);
		assertEquals(5000L, hat.prevPoseMs);
		V2Hat<String> wings = hat.withState(CosmeticV2.DRIVER_AIR, 0, 0.5f);
		assertEquals(5000L, wings.poseMs);
		// Begleiter: Uhr (Leucht-Frames, Höfe) und die Zeiten beider Seiten getrennt
		V2Hat.Lod<String> lod = new V2Hat.Lod<String>() {
			@Override
			public String base(long now, int level) {
				return "base@" + now + "/" + level;
			}

			@Override
			public String glow(long now, int level) {
				return "glow@" + now + "/" + level;
			}
		};
		V2Hat<String> v = new V2Hat<String>(m, "base", "glow", null, 5000L, true, lod);
		V2Hat<String> p = v.forPet(9000L, 120L, 8000L, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, 0.25f);
		assertEquals(9000L, p.now);
		assertEquals(120L, p.poseMs);
		assertEquals(8000L, p.prevPoseMs);
		assertEquals(CosmeticV2.DRIVER_PET, p.driver);
		assertEquals(CosmeticV2.DRIVER_IDLE, p.prevDriver);
		assertEquals(0.25f, p.blend, 0f);
		assertEquals(false, p.helmet, "Begleiter tragen keinen Helm");
		assertEquals("base@9000/0", p.base, "Bild der Grundtextur zur Uhr des Begleiters");
		assertEquals("glow@9000/0", p.glow, "Leucht-Frames zur Uhr des Begleiters");
		V2Hat<String> hidden = p.withHidden(2L);
		assertEquals(120L, hidden.poseMs);
		assertEquals(8000L, hidden.prevPoseMs);
		assertEquals(9000L, hidden.now);
	}
}
