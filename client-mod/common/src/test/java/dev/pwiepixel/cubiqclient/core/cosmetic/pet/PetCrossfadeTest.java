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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Überblendung zwischen zwei Zuständen eines Begleiters, jede Seite auf ihrer eigenen Zeitbasis: Bewegung nach der Uhr
 * (+ Versatz), {@code grab} und die Aktionen ab ihrem eigenen Beginn. Bis 0.23.0 lief die ausgehende Seite auf der Zeit
 * der eingehenden – der sitzende Fuchs stand beim Schnappen schlagartig auf, am Ende des Schnappens sprang er in eine
 * zufällige Phase. Gemischt wird sonst genau wie bis 0.23.0 und wie bei Hüten und Flügeln (gewichtet addiert, Drehungen
 * je Euler-Winkel geradlinig – kein kürzester Weg); nur Effekt-Knochen ({@code fx_*}) stehen sofort in der Pose des
 * Ziels. Mit beiden Füchsen ({@link PetActionsFormatTest#FOXES}); was ein Test erwartet, leitet er aus dem Modell ab
 * (Längen, Spuren, wo idle sitzt).
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
	void withoutAFadeThePetPathIsExactlyTheSingleTimePath() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			for (CosmeticV2 m : new CosmeticV2[] { PetActionsFormatTest.foxModel(file),
					CosmeticV2.parse(PetActionsFormatTest.foxWithout(file)) }) {
				CosmeticV2Renderer a = new CosmeticV2Renderer();
				CosmeticV2Renderer b = new CosmeticV2Renderer();
				int[] drivers = { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_PET,
						CosmeticV2.DRIVER_EAT };
				for (long t = 0; t < 20000; t += 777) {
					for (int d : drivers) {
						for (int pd : drivers) {
							// fertig eingeblendet: unverändert, Bit für Bit
							a.pose(m, t, true, false, d, pd, 1f, 0L);
							b.pose(m, t, t, true, false, d, pd, 1f, 0L);
							for (int i = 0; i < m.bones.size(); i++) assertArrayEquals(a.world(i), b.world(i), 0.0, file);
							// derselbe Treiber, der wirklich spielt (z. B. pet ohne Aktion = idle): nichts zu überblenden
							if (CosmeticV2Renderer.effectiveDriver(m, d) != CosmeticV2Renderer.effectiveDriver(m, pd)) continue;
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
	}

	/**
	 * Kleiner Begleiter für die Regeln der Überblendung: {@code spin} dreht sich um z (idle {@code idleZ}°, walk
	 * {@code walkZ}°, eat 0°), der Effekt-Knochen {@code fx_spark} sitzt in Ruhe im Körper und nur die Aktion pet hebt ihn
	 * an (bei 500 ms 8 px hoch, doppelt so groß).
	 */
	static String fadeModel(double idleZ, double walkZ) {
		return fadeModel(still("idle", "spin", idleZ), still("walk", "spin", walkZ));
	}

	/** {@link #fadeModel(double, double)} mit zwei eigenen Animationen (JSON) statt idle und walk. */
	private static String fadeModel(String first, String second) {
		String faces = ",\"faces\":{\"north\":{\"uv\":[0,0,2,2]},\"south\":{\"uv\":[2,0,4,2]}}}";
		return "{\"format\":2,\"id\":\"test_fade\",\"name\":\"Test\",\"slot\":\"pet\",\"attach\":\"ground\","
				+ "\"texture\":{\"file\":\"t.png\",\"width\":16,\"height\":16,\"scale\":1},"
				+ "\"bones\":[{\"id\":\"root\",\"pivot\":[0,0,0]},{\"id\":\"spin\",\"parent\":\"root\",\"pivot\":[0,4,0]},"
				+ "{\"id\":\"fx_spark\",\"parent\":\"root\",\"pivot\":[0,2,0]}],"
				+ "\"cubes\":[{\"id\":\"b\",\"bone\":\"root\",\"from\":[-2,0,-2],\"to\":[2,4,2]" + faces + ","
				+ "{\"id\":\"s\",\"bone\":\"spin\",\"from\":[-1,4,-1],\"to\":[1,8,1]" + faces + ","
				+ "{\"id\":\"f\",\"bone\":\"fx_spark\",\"from\":[-0.5,1.5,-0.5],\"to\":[0.5,2.5,0.5]" + faces + "],"
				+ "\"animations\":[" + first + "," + second + "],"
				+ "\"actions\":[{\"id\":\"pet\",\"driver\":\"pet\",\"lengthMs\":1000,\"tracks\":["
				+ "{\"bone\":\"fx_spark\",\"channel\":\"position\",\"keys\":[{\"t\":0,\"v\":[0,0,0]},{\"t\":500,\"v\":[0,8,0]},{\"t\":1000,\"v\":[0,0,0]}]},"
				+ "{\"bone\":\"fx_spark\",\"channel\":\"scale\",\"keys\":[{\"t\":0,\"v\":[1,1,1]},{\"t\":500,\"v\":[2,2,2]},{\"t\":1000,\"v\":[1,1,1]}]}]},"
				+ "{\"id\":\"eat\",\"driver\":\"eat\",\"lengthMs\":1000,\"tracks\":["
				+ "{\"bone\":\"spin\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0,0]}]}]}]}";
	}

	/** Eine Animation, die den Knochen {@code bone} fest um {@code z}° um die z-Achse dreht. */
	private static String still(String driver, String bone, double z) {
		return "{\"id\":\"" + driver + "\",\"driver\":\"" + driver + "\",\"lengthMs\":1000,\"tracks\":[{\"bone\":\"" + bone
				+ "\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[0,0," + z + "]}]}]}";
	}

	/** Welt-Matrix von {@code spin}, wenn er fest um {@code z}° gedreht ist (Bezug für die Überblendung). */
	private static double[] spinAt(double z) {
		CosmeticV2 ref = CosmeticV2.parse(fadeModel(z, z));
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(ref, 0, true);
		return r.world(PetActionsFormatTest.bone(ref, "spin")).clone();
	}

	/**
	 * Eine Animation, die {@code spin} fest in die Pose {@code p} stellt: {rx, ry, rz, px, py, pz, sx, sy, sz} (Grad, Pixel,
	 * Faktor).
	 */
	private static String stillPose(String driver, double[] p) {
		return "{\"id\":\"" + driver + "\",\"driver\":\"" + driver + "\",\"lengthMs\":1000,\"tracks\":["
				+ spinKey("rotation", p[0], p[1], p[2]) + "," + spinKey("position", p[3], p[4], p[5]) + ","
				+ spinKey("scale", p[6], p[7], p[8]) + "]}";
	}

	private static String spinKey(String channel, double x, double y, double z) {
		return "{\"bone\":\"spin\",\"channel\":\"" + channel + "\",\"keys\":[{\"t\":0,\"v\":[" + x + "," + y + "," + z + "]}]}";
	}

	/** Welt-Matrix von {@code spin}, wenn er fest in der Pose {@code p} steht ({@link #stillPose}). */
	private static double[] spinPose(double[] p) {
		CosmeticV2 ref = CosmeticV2.parse(fadeModel(stillPose("idle", p), stillPose("walk", p)));
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(ref, 0, true);
		return r.world(PetActionsFormatTest.bone(ref, "spin")).clone();
	}

	/** Eine Schleife, die {@code spin} in {@code lengthMs} gleichmäßig von {@code from}° auf {@code to}° um x dreht. */
	private static String spinning(String driver, int lengthMs, double from, double to) {
		return "{\"id\":\"" + driver + "\",\"driver\":\"" + driver + "\",\"lengthMs\":" + lengthMs + ",\"tracks\":[{\"bone\":"
				+ "\"spin\",\"channel\":\"rotation\",\"keys\":[{\"t\":0,\"v\":[" + from + ",0,0]},{\"t\":" + lengthMs + ",\"v\":["
				+ to + ",0,0]}]}]}";
	}

	/** Drehwinkel in Grad zwischen den Lagen zweier Welt-Matrizen (Drehanteil ohne Größe). */
	private static double angle(double[] a, double[] b) {
		double trace = 0;
		for (int c = 0; c < 3; c++) {
			for (int r = 0; r < 3; r++) trace += a[c * 4 + r] * b[c * 4 + r];
		}
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2))));
	}

	@Test
	void petFadesTurnEveryAngleTheLinearWayLikeHatsAndTheReleasedClient() {
		double[][] pairs = { { 170, -170 }, { -170, 170 }, { 10, 350 }, { 350, 10 }, { 0, 180 }, { 0, 720 }, { 30, 60 },
				{ -90, 450 }, { 0, -190 } };
		for (double[] pair : pairs) {
			double from = pair[0], to = pair[1];
			CosmeticV2 m = CosmeticV2.parse(fadeModel(from, to));
			int spin = PetActionsFormatTest.bone(m, "spin");
			CosmeticV2Renderer r = new CosmeticV2Renderer(), hat = new CosmeticV2Renderer();
			for (float w : new float[] { 0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f }) {
				String what = from + "° → " + to + "° bei " + w;
				// Begleiter: idle (von) blendet nach walk (nach) – je Euler-Winkel geradlinig, auch über mehr als 180°
				r.pose(m, 400, 9000, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, w, 0L);
				assertArrayEquals(spinAt(from + (to - from) * w), r.world(spin), 1e-5, what);
				// … Bit für Bit wie Hüte und Flügel, also wie bis 0.23.0 (beide Seiten stehen hier still, die Zeiten zählen nicht)
				hat.pose(m, 400, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, w, 0L);
				for (int b = 0; b < m.bones.size(); b++) assertArrayEquals(hat.world(b), r.world(b), 0.0, what + " (Hut), Knochen " + b);
			}
			// fertig eingeblendet: genau das Ziel, auch bei mehr als einer Umdrehung
			r.pose(m, 400, 9000, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, 1f, 0L);
			assertArrayEquals(spinAt(to), r.world(spin), 1e-9, from + "° → " + to + "° am Ende");
		}
		// 170° → −170° läuft wie bis 0.23.0 rückwärts über 0°, nicht auf dem kürzesten Weg über 180°
		CosmeticV2 m = CosmeticV2.parse(fadeModel(170, -170));
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 400, 9000, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, 0.5f, 0L);
		assertArrayEquals(spinAt(0), r.world(PetActionsFormatTest.bone(m, "spin")), 1e-5);
	}

	@Test
	void petFadesMovePositionsLinearlyAndScaleLikeTheReleasedClient() {
		// idle und walk stellen spin in eine ganze Pose: Drehung über mehr als 180°, Verschiebung, Größe auf beiden Seiten
		double[] idle = { 20, -100, 170, 0, 2, 0, 0.5, 1, 2 };
		double[] walk = { -30, 120, -170, 0, 0, 4, 2, 1.5, 0.5 };
		CosmeticV2 m = CosmeticV2.parse(fadeModel(stillPose("idle", idle), stillPose("walk", walk)));
		int spin = PetActionsFormatTest.bone(m, "spin");
		CosmeticV2Renderer r = new CosmeticV2Renderer(), hat = new CosmeticV2Renderer();
		for (float w : new float[] { 0.1f, 0.25f, 0.5f, 0.75f, 0.9f }) {
			String what = "bei " + w;
			// wie bis 0.23.0: Drehung und Position geradlinig, die Größe als Faktor je Seite, 1 + w · (v − 1)
			double[] want = new double[9], plain = new double[9];
			for (int j = 0; j < 9; j++) {
				plain[j] = idle[j] + (walk[j] - idle[j]) * w;
				want[j] = j < 6 ? plain[j] : (1 + w * (walk[j] - 1)) * (1 + (1 - w) * (idle[j] - 1));
			}
			r.pose(m, 400, 9000, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, w, 0L);
			assertArrayEquals(spinPose(want), r.world(spin), 1e-5, what);
			hat.pose(m, 400, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, w, 0L);
			assertArrayEquals(hat.world(spin), r.world(spin), 0.0, what + " (Hut)");
			// Probe: eine geradlinig gemischte Größe sähe hier anders aus
			assertTrue(shift(spinPose(plain), r.world(spin)) > 0.01, what + ": Größe nicht geradlinig");
		}
	}

	@Test
	void aPetFadeFollowsAMovingTargetWithoutAJump() {
		// Beide Seiten bewegen sich um x: idle dreht spin gleichmäßig 250° → 610° in 2000 ms (wie Drehungen bei Hände hoch),
		// slide rollt ihn 0° → 720° in 960 ms (wie Cubis Rolle beim Rutschen) – 12° je Bild
		CosmeticV2 m = CosmeticV2.parse(fadeModel(spinning("idle", 2000, 250, 610), spinning("slide", 960, 0, 720)));
		int spin = PetActionsFormatTest.bone(m, "spin");
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		// idle → slide, jede Seite auf ihrer eigenen Zeit ab Phase 0: im ganzen Fenster beginnt keine Schleife von vorn
		long target = 10 * 960L, source = 2 * 2000L;
		double[] last = null;
		double lastShort = 0, worst = 0, worstShort = 0;
		for (long dt = 0; dt <= Pet.FADE_MS + 2 * FRAME; dt += FRAME) {
			float w = fade(dt);
			double from = 250 + 360.0 * dt / 2000, to = 720.0 * dt / 960;
			r.pose(m, target + dt, source + dt, true, false, CosmeticV2.DRIVER_SLIDE, CosmeticV2.DRIVER_IDLE, w, 0L);
			double[] now = r.world(spin).clone();
			// jedes Bild genau die geradlinige Mischung aus beiden laufenden Seiten
			assertArrayEquals(spinPose(new double[] { from + (to - from) * w, 0, 0, 0, 0, 0, 1, 1, 1 }), now, 1e-4, "+" + dt + " ms");
			// zum Vergleich der kürzeste Weg, jedes Bild neu gewählt: d = ((nach − von) % 360 + 540) % 360 − 180
			double turnShort = from + (((to - from) % 360 + 540) % 360 - 180) * w;
			if (last != null) {
				worst = Math.max(worst, angle(last, now));
				double step = Math.abs(turnShort - lastShort) % 360;
				worstShort = Math.max(worstShort, Math.min(step, 360 - step));
			}
			last = now;
			lastShort = turnShort;
		}
		// höchstens etwa die Bewegung der Rolle selbst je Bild – kein Sprung …
		assertTrue(worst < 15, "größter Schritt je Bild: " + worst + "°");
		// … wo der kürzeste Weg mitten in der Überblendung die Richtung wechselt und das Bild umspringen lässt (Probe)
		assertTrue(worstShort > 150, "Probe – kürzester Weg: " + worstShort + "°");
	}

	/**
	 * {@code json} mit den Animationen des Treibers {@code driver} um {@code deltaMs} versetzt ({@code offsetMs}): zur Zeit
	 * t zeigen sie, was das Original zur Zeit t + deltaMs zeigt.
	 */
	private static CosmeticV2 shifted(String json, int driver, long deltaMs) {
		CosmeticV2 m = CosmeticV2.parse(json);
		JsonObject o = new JsonParser().parse(json).getAsJsonObject();
		for (JsonElement e : o.getAsJsonArray("animations")) {
			JsonObject a = e.getAsJsonObject();
			for (CosmeticV2.Animation an : m.animations) {
				if (an.driver == driver && an.id.equals(a.get("id").getAsString())) a.addProperty("offsetMs", an.offsetMs + deltaMs);
			}
		}
		return CosmeticV2.parse(o.toString());
	}

	/** Lokale Matrix eines Knochens (im Raum seines Elternknochens) nach der letzten Pose. */
	private static double[] local(CosmeticV2Renderer r, CosmeticV2 m, int bone) {
		int parent = m.bones.get(bone).parent;
		if (parent < 0) return r.world(bone).clone();
		double[] inv = new double[16], out = new double[16];
		assertTrue(dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Math.invertAffine(r.world(parent), inv));
		dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Math.multiply(out, inv, r.world(bone), new double[16]);
		return out;
	}

	@Test
	void everyBoneButTheEffectsMixesExactlyLikeTheReleasedClientEachSideOnItsOwnTime() throws IOException {
		int[] drivers = { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_SNEAK, CosmeticV2.DRIVER_JUMP,
				CosmeticV2.DRIVER_AIR, CosmeticV2.DRIVER_SLIDE, CosmeticV2.DRIVER_SWIM, CosmeticV2.DRIVER_GRAB,
				CosmeticV2.DRIVER_FLEE, CosmeticV2.DRIVER_HANDSUP, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_EAT };
		long[][] times = { { 5300, 5300 }, { 700, 9100 }, { 9100, 700 }, { 1450, 12345 } };
		float[] weights = { fade(FRAME), 0.37f, 0.5f, fade(200) };
		int fades = 0;
		for (String file : PetActionsFormatTest.FOXES) {
			for (String json : new String[] { PetActionsFormatTest.fox(file), PetActionsFormatTest.foxWithout(file) }) {
				CosmeticV2 m = CosmeticV2.parse(json);
				// Probe: kein gewöhnlicher Knochen hängt an einem Effekt-Knochen (sonst rückte er mit dessen Ziel-Pose mit)
				for (int b = 0; b < m.bones.size(); b++) {
					for (int p = m.bones.get(b).parent; p >= 0; p = m.bones.get(p).parent) assertTrue(fx(m, b) || !fx(m, p), file);
				}
				Map<String, CosmeticV2> refs = new HashMap<String, CosmeticV2>();
				CosmeticV2Renderer pet = new CosmeticV2Renderer(), hat = new CosmeticV2Renderer(), aim = new CosmeticV2Renderer();
				for (int d : drivers) {
					for (int pd : drivers) {
						int cur = CosmeticV2Renderer.effectiveDriver(m, d), prev = CosmeticV2Renderer.effectiveDriver(m, pd);
						if (cur == prev) continue; // nichts zu überblenden
						for (long[] tt : times) {
							long t = tt[0], tPrev = tt[1];
							// Bezug: die eine Uhr der Hüte und Flügel (die Mischung bis 0.23.0) auf einem Modell, in dem eine Seite um
							// den Abstand der Zeitbasen versetzt läuft – so sieht sie jede Seite zu deren eigener Zeit
							int moved;
							long delta, at;
							if (t == tPrev) {
								moved = -1;
								delta = 0;
								at = t;
							} else if (prev < CosmeticV2.DRIVER_PET) {
								moved = prev;
								delta = tPrev - t;
								at = t;
							} else if (cur < CosmeticV2.DRIVER_PET) {
								moved = cur;
								delta = t - tPrev;
								at = tPrev;
							} else {
								continue; // zwei Aktionen (ohne offsetMs): nur auf derselben Zeit vergleichbar
							}
							String key = moved + "/" + delta;
							CosmeticV2 ref = moved < 0 ? m : refs.get(key);
							if (ref == null) refs.put(key, ref = shifted(json, moved, delta));
							for (float w : weights) {
								String what = file + " " + d + " ← " + pd + " zu " + t + "/" + tPrev + " ms bei " + w;
								pet.pose(m, t, tPrev, true, false, d, pd, w, 0L);
								hat.pose(ref, at, true, false, d, pd, w, 0L);
								aim.pose(m, t, t, true, false, d, d, 1f, 0L);
								for (int b = 0; b < m.bones.size(); b++) {
									String bone = what + ", " + m.bones.get(b).id;
									// gewöhnliche Knochen Bit für Bit wie bis 0.23.0, Effekt-Knochen in der eigenen Pose des Ziels
									if (!fx(m, b)) assertArrayEquals(hat.world(b), pet.world(b), 0.0, bone);
									else assertArrayEquals(local(aim, m, b), local(pet, m, b), 1e-9, bone);
								}
								fades++;
							}
						}
					}
				}
			}
		}
		assertTrue(fades > 1000, "geprüfte Überblendungen: " + fades);
	}

	@Test
	void effectBonesTakeTheTargetPoseInEveryPetFade() {
		CosmeticV2 m = CosmeticV2.parse(fadeModel(30, 60));
		int fx = PetActionsFormatTest.bone(m, "fx_spark"), spin = PetActionsFormatTest.bone(m, "spin");
		assertEquals(1L << fx, m.fxBones, "fx_* sind Effekt-Knochen");
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 0, false);
		double[] rest = r.world(fx).clone();
		r.pose(m, 500, 500, true, false, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_PET, 1f, 0L);
		double[] flying = r.world(fx).clone();
		assertTrue(Math.abs(flying[13] - rest[13]) > 5, "Probe: bei 500 ms ist der Funke draußen");
		for (float w : new float[] { 0f, fade(FRAME), 0.3f, 0.5f, 0.9f }) {
			// Beginn: sofort in der Pose der Aktion (nicht w · Pose)
			r.pose(m, 500, 7000, true, false, CosmeticV2.DRIVER_PET, CosmeticV2.DRIVER_IDLE, w, 0L);
			assertArrayEquals(flying, r.world(fx), 0.0, "Beginn bei " + w);
			// Ende/Abbruch (zu idle, walk) und Unterbrechung durch die andere Aktion: sofort in Ruhe (das Ziel bewegt ihn nicht)
			for (int target : new int[] { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_EAT }) {
				r.pose(m, 7000, 500, true, false, target, CosmeticV2.DRIVER_PET, w, 0L);
				assertArrayEquals(rest, r.world(fx), 0.0, "Abbruch nach " + target + " bei " + w);
			}
			// … während alle anderen Knochen weiter überblenden
			r.pose(m, 7000, 9000, true, false, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_IDLE, w, 0L);
			assertArrayEquals(spinAt(30 + 30 * w), r.world(spin), 1e-5, "spin bei " + w);
		}
		// Hüte/Flügel (eine Zeit) mischen wie bisher auch fx_*-Knochen
		r.pose(m, 500, true, false, CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_PET, 0.3f, 0L);
		assertTrue(Math.abs(r.world(fx)[13] - rest[13]) > 3, "Hut: gemischt");
	}

	/** Ecken eines Würfels nach der letzten Pose in der Welt (Anhängepunkt-Raum). */
	private static double[][] cubeCorners(CosmeticV2Renderer r, CosmeticV2.Cube c) {
		double[] w = r.world(c.bone);
		double[][] out = new double[8][];
		int k = 0;
		for (double x : new double[] { c.x0 - c.inflate, c.x1 + c.inflate }) {
			for (double y : new double[] { c.y0 - c.inflate, c.y1 + c.inflate }) {
				for (double z : new double[] { c.z0 - c.inflate, c.z1 + c.inflate }) {
					out[k++] = new double[] { w[0] * x + w[4] * y + w[8] * z + w[12], w[1] * x + w[5] * y + w[9] * z + w[13],
							w[2] * x + w[6] * y + w[10] * z + w[14] };
				}
			}
		}
		return out;
	}

	/** Wie weit ragt der Würfel {@code c} aus dem Würfel {@code host} heraus (Pixel; ≤ 0 = ganz darin)? */
	private static double outside(CosmeticV2Renderer r, CosmeticV2.Cube c, CosmeticV2.Cube host) {
		double[] inv = new double[16];
		assertTrue(dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Math.invertAffine(r.world(host.bone), inv));
		double worst = Double.NEGATIVE_INFINITY;
		for (double[] p : cubeCorners(r, c)) {
			double x = inv[0] * p[0] + inv[4] * p[1] + inv[8] * p[2] + inv[12];
			double y = inv[1] * p[0] + inv[5] * p[1] + inv[9] * p[2] + inv[13];
			double z = inv[2] * p[0] + inv[6] * p[1] + inv[10] * p[2] + inv[14];
			worst = Math.max(worst, Math.max(Math.max(host.x0 - host.inflate - x, x - host.x1 - host.inflate),
					Math.max(Math.max(host.y0 - host.inflate - y, y - host.y1 - host.inflate),
							Math.max(host.z0 - host.inflate - z, z - host.z1 - host.inflate))));
		}
		return worst;
	}

	/** Je Effekt-Würfel sein Versteck: ein Würfel ohne fx-Knochen, der ihn in Ruhe ganz umschließt (sonst −1). */
	private static int[] hosts(CosmeticV2 m) {
		CosmeticV2Renderer r = new CosmeticV2Renderer();
		r.pose(m, 0, false);
		int[] host = new int[m.cubes.size()];
		for (int i = 0; i < host.length; i++) {
			host[i] = -1;
			CosmeticV2.Cube c = m.cubes.get(i);
			if (!fx(m, c.bone)) continue;
			for (int j = 0; j < host.length && host[i] < 0; j++) {
				CosmeticV2.Cube h = m.cubes.get(j);
				if (!fx(m, h.bone) && outside(r, c, h) <= 0) host[i] = j;
			}
			assertTrue(host[i] >= 0, c.id + ": in Ruhe versteckt");
		}
		return host;
	}

	/** Wie weit ragt der am weitesten herausstehende Effekt-Würfel aus seinem Versteck (nach der letzten Pose)? */
	private static double fxOutside(CosmeticV2Renderer r, CosmeticV2 m, int[] host) {
		double worst = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < host.length; i++) {
			if (host[i] >= 0) worst = Math.max(worst, outside(r, m.cubes.get(i), m.cubes.get(host[i])));
		}
		return worst;
	}

	@Test
	void anInterruptedActionHidesItsHeartsAndCrumbsAtOnce() throws IOException {
		int foxes = 0;
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			if (m.fxBones == 0L) continue; // der handgeschriebene Fuchs hat keine Effekte
			foxes++;
			CosmeticV2Renderer r = new CosmeticV2Renderer();
			int[] host = hosts(m);
			long clock = farFromRest(r, m);
			for (int drv : actions(m)) {
				CosmeticV2.Animation a = m.action(drv);
				String what = file + " " + a.id;
				// mitten im Flug: wann ragen Herzchen/Krümel am weitesten heraus? (Probe: dann sieht man sie wirklich)
				long out = 0;
				double most = Double.NEGATIVE_INFINITY;
				for (long t = 0; t <= a.lengthMs; t += 50) {
					r.pose(m, t, t, true, false, drv, drv, 1f, 0L);
					double o = fxOutside(r, m, host);
					if (o > most) {
						most = o;
						out = t;
					}
				}
				assertTrue(most > 1, what + ": Probe – die Effekte fliegen sichtbar (" + most + " px)");
				// abgebrochen (Besitzer läuft weg, fällt, schwimmt …) oder von der anderen Aktion unterbrochen – in jedem
				// Moment der Überblendung bleibt kein Effekt-Würfel außerhalb seines Verstecks
				int other = drv == CosmeticV2.DRIVER_PET ? CosmeticV2.DRIVER_EAT : CosmeticV2.DRIVER_PET;
				int[] targets = { CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_WALK, CosmeticV2.DRIVER_SNEAK, CosmeticV2.DRIVER_AIR,
						CosmeticV2.DRIVER_SWIM, CosmeticV2.DRIVER_SLIDE, CosmeticV2.DRIVER_GRAB, CosmeticV2.DRIVER_FLEE,
						CosmeticV2.DRIVER_HANDSUP, other };
				for (int target : targets) {
					long time = CosmeticV2.oneShot(target) ? FRAME : clock + FRAME;
					for (float w : new float[] { fade(FRAME), 0.5f, fade(200) }) {
						r.pose(m, time, out + FRAME, true, false, target, drv, w, 0L);
						double o = fxOutside(r, m, host);
						assertTrue(o <= 1e-6, what + " → Treiber " + target + " bei " + w + ": " + o + " px draußen");
					}
				}
			}
		}
		assertTrue(foxes > 0, "mindestens ein Fuchs mit Effekt-Knochen (fox_kit.generated.json)");
	}

	@Test
	void theRendererPosesACompanionOnBothTimeBasesAndHatsAndWingsOnTheClock() throws IOException {
		for (String file : PetActionsFormatTest.FOXES) {
			CosmeticV2 m = PetActionsFormatTest.foxModel(file);
			CosmeticV2Renderer r = new CosmeticV2Renderer(), ref = new CosmeticV2Renderer();
			long clock = farFromRest(ref, m);
			V2Hat<String> base = new V2Hat<String>(m, "base", null, null, 5000L, false);
			for (int drv : actions(m)) {
				String what = file + " " + m.action(drv).id;
				// wie petFrame: der sitzende Fuchs (idle auf der Uhr des Begleiters) blendet in die Aktion, die seit ihrem eigenen
				// Beginn läuft (halb durch)
				long t = m.action(drv).lengthMs / 2;
				V2Hat<String> pet = base.forPet(clock, t, clock, drv, CosmeticV2.DRIVER_IDLE, 0.4f);
				r.pose(pet);
				ref.pose(m, t, clock, true, false, drv, CosmeticV2.DRIVER_IDLE, 0.4f, 0L);
				for (int b = 0; b < m.bones.size(); b++) assertArrayEquals(ref.world(b), r.world(b), 0.0, what + ", Knochen " + b);
				// … nicht beide Seiten auf der einen Uhr des Bildes, wie Hüte und Flügel (dort stünde die Aktion schon am Ende)
				double[] two = body(r, m);
				ref.pose(m, pet.now, true, false, drv, CosmeticV2.DRIVER_IDLE, 0.4f, 0L);
				assertTrue(shift(body(ref, m), two) > 0.25, what + ": eigene Zeitbasen: " + shift(body(ref, m), two));
				// abgeschaltete Knochen reisen mit
				r.pose(pet.withHidden(2L));
				ref.pose(m, t, clock, true, false, drv, CosmeticV2.DRIVER_IDLE, 0.4f, 2L);
				assertArrayEquals(corners(ref, m), corners(r, m), 0.0, what + ": ohne Knochen 1");
			}
		}
		// Hüte und Flügel: eine Zeit, alle Knochen gewichtet addiert wie bisher – auch fx_*. Ein Begleiter mischt die übrigen
		// Knochen genauso, stellt fx_* aber sofort in die Pose des Ziels: Streicheln (hebt den Funken an, lässt spin in Ruhe)
		// blendet zu 30 % nach walk (spin −170°) aus
		CosmeticV2 spark = CosmeticV2.parse(fadeModel(170, -170));
		int spin = PetActionsFormatTest.bone(spark, "spin"), fx = PetActionsFormatTest.bone(spark, "fx_spark");
		CosmeticV2Renderer r = new CosmeticV2Renderer(), rest = new CosmeticV2Renderer();
		rest.pose(spark, 0, false);
		V2Hat<String> hat = new V2Hat<String>(spark, "base", null, null, 500L, false).withState(CosmeticV2.DRIVER_WALK,
				CosmeticV2.DRIVER_PET, 0.3f);
		r.pose(hat);
		double[] hatSpin = r.world(spin).clone();
		assertArrayEquals(spinAt(-51), hatSpin, 1e-5, "Hut/Flügel: geradlinig");
		assertTrue(Math.abs(r.world(fx)[13] - rest.world(fx)[13]) > 3, "Hut/Flügel: fx_* gemischt");
		r.pose(new V2Hat<String>(spark, "base", null, null, 500L, false).forPet(500L, 500L, 500L, CosmeticV2.DRIVER_WALK,
				CosmeticV2.DRIVER_PET, 0.3f));
		assertArrayEquals(hatSpin, r.world(spin), 0.0, "Begleiter: gemischt wie Hüte");
		assertArrayEquals(rest.world(fx), r.world(fx), 0.0, "Begleiter: fx_* sofort im Ziel (walk bewegt ihn nicht)");
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
		// nur Begleiter überblenden nach den Regeln der Aktionen; Hüte und Flügel wie bisher
		assertTrue(p.pet && hidden.pet && p.withState(CosmeticV2.DRIVER_IDLE, CosmeticV2.DRIVER_PET, 0.5f).pet);
		assertTrue(!hat.pet && !wings.pet && !v.pet && !v.withHidden(2L).pet);
	}
}
