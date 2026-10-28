package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer;
import dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Überblendung zwischen zwei Zuständen eines Begleiters, jede Seite auf ihrer eigenen Zeitbasis: Bewegung nach der Uhr
 * (+ Versatz), {@code grab} und die Aktionen ab ihrem eigenen Beginn. Bis 0.23.0 lief die ausgehende Seite auf der Zeit
 * der eingehenden – der sitzende Fuchs stand beim Schnappen schlagartig auf, am Ende des Schnappens sprang er in eine
 * zufällige Phase. Mit beiden Füchsen ({@link PetActionsFormatTest#FOXES}); was ein Test erwartet, leitet er aus dem
 * Modell ab (Längen, Spuren, wo idle sitzt).
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

	/** Ist der Knochen ein Effekt-Knochen ({@code fx_*}: Herzchen, Krümel)? */
	static boolean fx(CosmeticV2 m, int bone) {
		return m.bones.get(bone).id.startsWith("fx_");
	}

	/**
	 * Die acht Ecken jedes Würfels außer denen an {@code fx_*}-Knochen nach der letzten Pose: der sichtbare Körper. Herzchen
	 * und Krümel fliegen schnell und springen beim Abbruch absichtlich zurück ins Versteck.
	 */
	static double[] body(CosmeticV2Renderer r, CosmeticV2 m) {
		List<Double> list = new ArrayList<Double>();
		for (CosmeticV2.Cube c : m.cubes) {
			if (fx(m, c.bone)) continue;
			double[] w = r.world(c.bone);
			for (double x : new double[] { c.x0 - c.inflate, c.x1 + c.inflate }) {
				for (double y : new double[] { c.y0 - c.inflate, c.y1 + c.inflate }) {
					for (double z : new double[] { c.z0 - c.inflate, c.z1 + c.inflate }) {
						list.add(w[0] * x + w[4] * y + w[8] * z + w[12]);
						list.add(w[1] * x + w[5] * y + w[9] * z + w[13]);
						list.add(w[2] * x + w[6] * y + w[10] * z + w[14]);
					}
				}
			}
		}
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

	/** Pose (Begleiter: zwei Zeitbasen) und danach die Ecken des Körpers ({@link #body}). */
	private static double[] pose(CosmeticV2Renderer r, CosmeticV2 m, long t, long prevT, int driver, int prevDriver, float blend) {
		r.pose(m, t, prevT, true, false, driver, prevDriver, blend, 0L);
		return body(r, m);
	}

	/** Die erste Animation mit diesem Treiber. */
	static CosmeticV2.Animation anim(CosmeticV2 m, int driver) {
		for (CosmeticV2.Animation a : m.animations) if (a.driver == driver) return a;
		throw new AssertionError("Treiber " + driver);
	}

	/** Uhrzeit, zu der idle am weitesten von der Ruhe weg ist (der Fuchs sitzt) – gesucht über die längste idle-Schleife. */
	static long farFromRest(CosmeticV2Renderer r, CosmeticV2 m) {
		r.pose(m, 0, false);
		double[] rest = body(r, m);
		int len = 0;
		for (CosmeticV2.Animation a : m.animations) if (a.driver == CosmeticV2.DRIVER_IDLE) len = Math.max(len, a.lengthMs);
		long best = 0;
		double far = -1;
		for (long t = 0; t < len; t += 250) {
			double d = shift(rest, pose(r, m, t, t, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f));
			if (d > far) {
				far = d;
				best = t;
			}
		}
		return best;
	}

	/** Die Aktionen des Modells (pet, eat). */
	static int[] actions(CosmeticV2 m) {
		int[] out = new int[m.actions.size()];
		for (int i = 0; i < out.length; i++) out[i] = m.actions.get(i).driver;
		return out;
	}

	@Test
	void anActionStartsFromTheSittingIdleWithoutAPop() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			long clock = farFromRest(r, m);
			for (int drv : actions(m)) {
				String what = file + " " + m.action(drv).id;
				double[] before = pose(r, m, clock, clock, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f);
				double[] start = pose(r, m, 0, 0, drv, drv, 1f);
				assertTrue(shift(before, start) > 3, what + ": Probe – der Fuchs sitzt wirklich (" + clock + " ms)");
				// erstes Bild nach dem Klick: die Aktion ab ihrem Beginn, das ausgehende idle nach der Uhr weiter
				double[] first = pose(r, m, FRAME, clock + FRAME, drv, CosmeticV2.DRIVER_IDLE, fade(FRAME));
				assertTrue(shift(before, first) < 0.3, what + ": kein Sprung: " + shift(before, first));
				// bis 0.23.0: beide Seiten auf der Zeit der Aktion – idle sprang auf seinen Anfang (stand plötzlich)
				double[] old = pose(r, m, FRAME, FRAME, drv, CosmeticV2.DRIVER_IDLE, fade(FRAME));
				assertTrue(shift(before, old) > 3, what + ": alte Zeitbasis springt: " + shift(before, old));
				// der ganze Übergang Bild für Bild ohne Sprung
				double[] last = before;
				for (long dt = FRAME; dt <= 400; dt += FRAME) {
					double[] now = pose(r, m, dt, clock + dt, drv, CosmeticV2.DRIVER_IDLE, fade(dt));
					assertTrue(shift(last, now) < 1.5, what + " dt=" + dt + ": " + shift(last, now));
					last = now;
				}
			}
		}
	}

	@Test
	void grabEndsFromItsLastFrameNotFromARandomPhase() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			CosmeticV2.Animation grab = anim(m, CosmeticV2.DRIVER_GRAB);
			long end = PetSim.GRAB_TICKS * 50L; // so lange schnappt er (der Clip kann länger sein)
			long clock = end + grab.lengthMs / 2 + 3L * grab.lengthMs; // Uhr beim Wechsel zu FLEE: eine ganz andere Phase
			double[] last = pose(r, m, end, end, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_GRAB, 1f);
			double[] first = pose(r, m, clock + FRAME, end + FRAME, CosmeticV2.DRIVER_FLEE, CosmeticV2.DRIVER_GRAB, fade(FRAME));
			assertTrue(shift(last, first) < 1.0, file + ": weiter ab dem letzten Bild: " + shift(last, first));
			// bis 0.23.0 lief grab beim Ausblenden auf der Uhr weiter – das springt nur, wenn grab sich wiederholt (ohne
			// loop: false); ein Clip mit loop: false bleibt ohnehin auf seinem letzten Bild stehen
			if (grab.loop) {
				double[] old = pose(r, m, clock + FRAME, clock + FRAME, CosmeticV2.DRIVER_FLEE, CosmeticV2.DRIVER_GRAB, fade(FRAME));
				assertTrue(shift(last, old) > 3, file + ": alte Zeitbasis springt: " + shift(last, old));
			}
		}
	}

	@Test
	void anActionEndsIntoTheNextStateFromItsOwnTime() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			long clock = farFromRest(r, m); // idle sitzt
			for (int drv : actions(m)) {
				String what = file + " " + m.action(drv).id;
				long len = m.action(drv).lengthMs;
				// zu Ende gespielt: das letzte Bild ist die Ruhe, das Ausblenden hält es (auf len begrenzt)
				double[] end = pose(r, m, len, len, drv, drv, 1f);
				double[] next = pose(r, m, clock + FRAME, len + FRAME, CosmeticV2.DRIVER_IDLE, drv, fade(FRAME));
				assertTrue(shift(end, next) < 0.3, what + ": Ende → idle: " + shift(end, next));
				// vorzeitig abgebrochen (Besitzer läuft weg): die Aktion läuft beim Ausblenden auf ihrer Zeit weiter
				long cut = len / 2;
				double[] at = pose(r, m, cut, cut, drv, drv, 1f);
				double[] walk = pose(r, m, clock + FRAME, cut + FRAME, CosmeticV2.DRIVER_WALK, drv, fade(FRAME));
				assertTrue(shift(at, walk) < 0.6, what + ": Abbruch → walk: " + shift(at, walk));
			}
		}
	}

	/**
	 * Der Fuchs mit der Aktion {@code driver} als einziger Animation (Treiber idle, {@code loop: false}, ohne actions) –
	 * so sieht die Aktion ganz allein aus.
	 */
	static CosmeticV2 alone(String file, String driver) throws IOException {
		JsonObject m = new JsonParser().parse(PetActionsFormatTest.fox(file)).getAsJsonObject();
		JsonObject action = null;
		for (JsonElement e : m.getAsJsonArray("actions")) {
			if (driver.equals(e.getAsJsonObject().get("driver").getAsString())) action = e.getAsJsonObject();
		}
		assertNotNull(action, driver);
		JsonObject anim = new JsonObject();
		anim.addProperty("id", "alone");
		anim.addProperty("driver", "idle");
		anim.add("lengthMs", action.get("lengthMs"));
		anim.addProperty("loop", false);
		anim.add("tracks", action.get("tracks"));
		JsonArray anims = new JsonArray();
		anims.add(anim);
		m.add("animations", anims);
		m.remove("actions");
		return CosmeticV2.parse(m.toString());
	}

	@Test
	void actionsPlayExactlyTheirOwnTracksAndClampToTheirLength() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			CosmeticV2Renderer ref = new CosmeticV2Renderer();
			CosmeticV2Renderer idle = new CosmeticV2Renderer();
			CosmeticV2Renderer still = new CosmeticV2Renderer();
			still.pose(m, 0, false);
			r.pose(m, 0, false);
			double[] rest = corners(r, m);
			for (int drv : actions(m)) {
				CosmeticV2.Animation a = m.action(drv);
				String what = file + " " + a.id;
				CosmeticV2 own = alone(file, a.id);
				// allein: genau die Pose aus den eigenen Spuren der Aktion, nichts dazugemischt – obwohl idle zur selben Zeit
				// Knochen aus der Ruhe bewegt (Probe: sonst fiele ein Dazumischen gar nicht auf)
				boolean idleMoves = false;
				for (long t = -500; t <= a.lengthMs + 500; t += 100) {
					r.pose(m, t, t, true, false, drv, drv, 1f, 0L);
					ref.pose(own, t, true);
					idle.pose(m, t, t, true, false, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f, 0L);
					for (int b = 0; b < m.bones.size(); b++) {
						assertArrayEquals(ref.world(b), r.world(b), 0.0, what + " t=" + t + " " + m.bones.get(b).id);
						double[] sw = still.world(b), iw = idle.world(b);
						for (int i = 0; i < 16; i++) idleMoves |= Math.abs(sw[i] - iw[i]) > 1e-6;
					}
				}
				assertTrue(idleMoves, what + ": Probe – idle bewegt Knochen aus der Ruhe");
				// vor dem Beginn = Beginn, nach dem Ende = Ende (begrenzt, keine Wiederholung)
				double[] start = cornersAt(r, m, 0, drv), stop = cornersAt(r, m, a.lengthMs, drv);
				assertArrayEquals(start, cornersAt(r, m, -500, drv), 0.0, what);
				assertArrayEquals(stop, cornersAt(r, m, a.lengthMs + 1, drv), 0.0, what);
				assertArrayEquals(stop, cornersAt(r, m, 99_999, drv), 0.0, what);
				// Beginn und Ende in Ruhe (Vertrag: höchstens 1°, 0,05 px, 1 % daneben)
				assertTrue(shift(rest, start) < 0.5, what + ": Beginn " + shift(rest, start));
				assertTrue(shift(rest, stop) < 0.5, what + ": Ende " + shift(rest, stop));
				// dazwischen sieht man sie
				double most = 0;
				for (long t = 0; t <= a.lengthMs; t += 100) most = Math.max(most, shift(rest, cornersAt(r, m, t, drv)));
				assertTrue(most > 1, what + ": bewegt sich " + most);
			}
		}
	}

	/** Alle Flächen-Ecken der Aktion {@code driver} allein zur Zeit {@code t}. */
	private static double[] cornersAt(CosmeticV2Renderer r, CosmeticV2 m, long t, int driver) {
		r.pose(m, t, t, true, false, driver, driver, 1f, 0L);
		return corners(r, m);
	}

	@Test
	void withoutTheActionIdlePlaysOnTheClock() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 with = PetActionsFormatTest.foxModel(file);
			CosmeticV2 without = CosmeticV2.parse(PetActionsFormatTest.foxWithout(file));
			long since = 500, clock = 9000;
			// Zeitbasis nach dem Treiber, der wirklich spielt
			assertEquals(since, Pet.poseTime(with, Pet.PET, since, clock), file);
			assertEquals(since, Pet.poseTime(with, Pet.EAT, since, clock), file);
			assertEquals(clock, Pet.poseTime(without, Pet.PET, since, clock), file + ": ohne Aktion idle nach der Uhr, kein Sprung auf 0");
			assertEquals(clock, Pet.poseTime(without, Pet.EAT, since, clock), file);
			assertEquals(since, Pet.poseTime(without, Pet.GRAB, since, clock), file + ": der Fuchs kann grab");
			assertEquals(clock, Pet.poseTime(with, Pet.IDLE, since, clock), file);
			assertEquals(clock, Pet.poseTime(with, Pet.FLEE, since, clock), file);
			assertEquals(0, Pet.poseTime(with, Pet.PET, -20, clock), file + ": nie vor dem Beginn");
			// gezeichnet: PET ohne Aktion im Modell = idle
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			double[] idle = pose(r, without, clock, clock, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_IDLE, 1f);
			double[] pet = pose(r, without, clock, clock, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, 0.4f);
			assertTrue(shift(idle, pet) < 1e-9, file);
			assertEquals(CosmeticV2.DRIVER_IDLE, CosmeticV2Renderer.effectiveDriver(without, CosmeticV2.DRIVER_PET), file);
			assertEquals(CosmeticV2.DRIVER_PET, CosmeticV2Renderer.effectiveDriver(with, CosmeticV2.DRIVER_PET), file);
		}
		CosmeticV2 plain = CosmeticV2.parse(PetHeistTest.model("idle", "walk"));
		assertEquals(9000, Pet.poseTime(plain, Pet.GRAB, 500, 9000), "ohne grab: idle nach der Uhr");
		assertEquals(9000, Pet.poseTime(null, Pet.PET, 500, 9000));
	}

	@Test
	void singleTimeOverloadsAreUnchanged() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer a = new CosmeticV2Renderer();
			CosmeticV2Renderer b = new CosmeticV2Renderer();
			int[] drivers = { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_PET };
			for (long t = 0; t < 20000; t += 777) {
				for (int d : drivers) {
					for (int pd : drivers) {
						a.pose(m, t, true, false, d, pd, 0.37f, 0L);
						b.pose(m, t, t, true, false, d, pd, 0.37f, 0L);
						for (int i = 0; i < m.bones.size(); i++) assertArrayEquals(a.world(i), b.world(i), 0.0, file);
					}
				}
				a.pose(m, t, true);
				b.pose(m, t, t, true, false, 0, 0, 1f, 0L);
				for (int i = 0; i < m.bones.size(); i++) assertArrayEquals(a.world(i), b.world(i), 0.0, file);
			}
		}
	}

	@Test
	void v2HatCarriesThePetClockAndBothPoseTimes() throws IOException {
		CosmeticV2 m = PetActionsFormatTest.foxModel(PetActionsFormatTest.FOXES[0]);
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
