package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

import dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSimTest.FakeWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pflanzen sind für den Klick-Test durchsichtig ({@link PetSight}): steht der Begleiter in einem Weizenfeld, in hohem Gras
 * oder zwischen Blumen, wird er gestreichelt bzw. gefüttert, statt dass Minecraft das Essen in der Hand isst. Alles andere –
 * feste Blöcke, Hebel, Knöpfe, Schilder, Türen, Spinnweben, Süßbeerensträucher, Wesen – gewinnt weiter, in jeder Zelle.
 * Die Welt ist ein Gitter aus Zellen mit ihren Umrissen wie in Minecraft ({@link Grid}), der Begleiter der Fuchs der
 * Website in der Simulation.
 */
class PetSightTest {
	static final UUID OWNER = PetSimTest.OWNER;
	static final int E = PetInteract.HELD_EMPTY, F = PetInteract.HELD_FOOD;

	// --- Blöcke: Umrisse (Pixel 0…16 in der Zelle) wie Minecrafts Fadenkreuz sie trifft, und was die Loader wissen ----

	/** Ein Block im Gitter: Umrisse und die Angaben für {@link PetSight#seeThrough}. */
	static final class Block {
		final String name;
		final double[][] boxes;
		final boolean vegetation, berryBush, noCollision;
		final int snowLayers;

		Block(String name, boolean vegetation, boolean berryBush, boolean noCollision, int snowLayers, double[]... boxes) {
			this.name = name;
			this.vegetation = vegetation;
			this.berryBush = berryBush;
			this.noCollision = noCollision;
			this.snowLayers = snowLayers;
			this.boxes = boxes;
		}

		boolean seeThrough() {
			return PetSight.seeThrough(vegetation, berryBush, noCollision, snowLayers);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/** Pflanze ohne Kollisionsform (Getreide, Gras, Blumen …). */
	static Block plant(String name, double... box) {
		return new Block(name, true, false, true, 0, box);
	}

	/** Block ohne Kollisionsform, der keine Pflanze ist (Hebel, Knopf, Schild, Druckplatte, Spinnweben …). */
	static Block thin(String name, double... box) {
		return new Block(name, false, false, true, 0, box);
	}

	/** Block mit Kollisionsform. */
	static Block solid(String name, double... box) {
		return new Block(name, false, false, false, 0, box);
	}

	static final Block WHEAT = plant("reifer Weizen", 0, 0, 0, 16, 16, 16);
	static final Block YOUNG_WHEAT = plant("junger Weizen", 0, 0, 0, 16, 6, 16);
	static final Block GRASS = plant("kurzes Gras", 2, 0, 2, 14, 13, 14);
	static final Block FLOWER = plant("Mohn", 5, 0, 5, 11, 10, 11);
	/** Hohes Gras, großer Farn, Sonnenblume: beide Hälften füllen ihre Zelle. */
	static final Block TALL_GRASS = plant("hohes Gras", 0, 0, 0, 16, 16, 16);
	static final Block STONE = solid("Stein", 0, 0, 0, 16, 16, 16);
	/** Tür (untere Hälfte, Angeln an der Südseite der Zelle). */
	static final Block DOOR = solid("Tür", 0, 0, 13, 16, 16, 16);
	/** Tür an der Nordseite der Zelle. */
	static final Block DOOR_NORTH = solid("Tür (Nordseite)", 0, 0, 0, 16, 16, 3);
	static final Block SIGN = thin("Schild", 4, 0, 4, 12, 16, 12);
	/** Hebel an der Südwand der Zelle bzw. an der Nordwand. */
	static final Block LEVER = thin("Hebel", 5, 3, 10, 11, 13, 16);
	static final Block LEVER_NORTH = thin("Hebel (Nordwand)", 5, 3, 0, 11, 13, 6);
	/** Knopf an der Südwand bzw. Nordwand (6…10 px hoch). */
	static final Block BUTTON = thin("Knopf", 5, 6, 14, 11, 10, 16);
	static final Block BUTTON_NORTH = thin("Knopf (Nordwand)", 5, 6, 0, 11, 10, 2);
	static final Block COBWEB = thin("Spinnweben", 0, 0, 0, 16, 16, 16);
	static final Block PRESSURE_PLATE = thin("Druckplatte", 1, 0, 1, 15, 1, 15);
	/** Süßbeerenstrauch, ausgewachsen: eine Pflanze ohne Kollisionsform – aber ein Rechtsklick pflückt, und er sticht. */
	static final Block BERRY_BUSH = new Block("Süßbeerenstrauch", true, true, true, 0, new double[] { 1, 0, 1, 15, 16, 15 });
	/** Seerose: eine Pflanze mit Kollisionsform. */
	static final Block LILY_PAD = new Block("Seerose", true, false, false, 0, new double[] { 1, 0, 1, 15, 1.5, 15 });

	/** Schneeschicht mit {@code layers} Lagen (2 px je Lage; Kollisionsform erst ab zwei Lagen). */
	static Block snow(int layers) {
		return new Block(layers + " Schneeschicht(en)", false, false, layers == 1, layers, new double[] { 0, 0, 0, 16, 2 * layers, 16 });
	}

	// --- Die Welt entlang des Strahls ---------------------------------------------------------------------------------

	/**
	 * Testwelt für {@link PetSight.Ray}: Blöcke je Zelle, fester Boden unter y = 64 (wie {@link FakeWorld}), Wesen als
	 * Kästen. Treffer wie Minecrafts Fadenkreuz: der erste Umriss ab {@code from} – liegt {@code from} schon in einem, dort.
	 */
	static final class Grid implements PetSight.Ray {
		final Map<Long, Block> cells = new HashMap<Long, Block>();
		final Map<Long, int[]> where = new HashMap<Long, int[]>();
		final List<double[]> entities = new ArrayList<double[]>();
		double[] eye = new double[3], dir = new double[3];
		int blockCalls, entityCalls;

		Grid put(int x, int y, int z, Block b) {
			long k = FakeWorld.key(x, y, z);
			cells.put(k, b);
			where.put(k, new int[] { x, y, z });
			return this;
		}

		/** Feld: {@code b} in allen Zellen x0…x1, z0…z1 der Schicht y. */
		Grid field(int x0, int x1, int y, int z0, int z1, Block b) {
			for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) put(x, y, z, b);
			return this;
		}

		/** Ein Wesen (Kasten in der Welt, Blöcke). */
		Grid entity(double x0, double y0, double z0, double x1, double y1, double z1) {
			entities.add(new double[] { x0, y0, z0, x1, y1, z1 });
			return this;
		}

		/** Strahl für die folgenden Abfragen (Auge, normierte Richtung) – wie der Loader für einen Klick. */
		Grid aim(double[] eye, double[] dir) {
			this.eye = eye.clone();
			this.dir = dir.clone();
			return this;
		}

		@Override
		public double block(double from, double to, int[] cell) {
			blockCalls++;
			double best = Double.NaN;
			for (Map.Entry<Long, Block> e : cells.entrySet()) {
				int[] c = where.get(e.getKey());
				for (double[] px : e.getValue().boxes) {
					double t = hit(from, c[0] + px[0] / 16, c[1] + px[1] / 16, c[2] + px[2] / 16, c[0] + px[3] / 16,
							c[1] + px[4] / 16, c[2] + px[5] / 16);
					if (t <= to && !(t >= best)) {
						best = t;
						System.arraycopy(c, 0, cell, 0, 3);
					}
				}
			}
			// Boden: Oberkante der Zellen bei y = 63
			if (dir[1] < 0) {
				double t = Math.max(from, (64 - eye[1]) / dir[1]);
				if (t <= to && !(t >= best)) {
					best = t;
					cell[0] = (int) Math.floor(eye[0] + dir[0] * t);
					cell[1] = 63;
					cell[2] = (int) Math.floor(eye[2] + dir[2] * t);
					if (!cells.containsKey(FakeWorld.key(cell[0], 63, cell[2]))) put(cell[0], 63, cell[2], STONE);
				}
			}
			return best;
		}

		/** Erster Punkt des Strahls ab {@code from} im Kasten (Welt), sonst NaN. */
		private double hit(double from, double x0, double y0, double z0, double x1, double y1, double z1) {
			double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
			double[] lo = { x0, y0, z0 }, hi = { x1, y1, z1 };
			for (int a = 0; a < 3; a++) {
				if (Math.abs(dir[a]) < 1e-12) {
					if (eye[a] < lo[a] || eye[a] > hi[a]) return Double.NaN;
					continue;
				}
				double t1 = (lo[a] - eye[a]) / dir[a], t2 = (hi[a] - eye[a]) / dir[a];
				near = Math.max(near, Math.min(t1, t2));
				far = Math.min(far, Math.max(t1, t2));
			}
			if (near > far || far < from) return Double.NaN;
			return Math.max(near, from);
		}

		@Override
		public boolean seeThrough(int x, int y, int z) {
			Block b = cells.get(FakeWorld.key(x, y, z));
			return b != null && b.seeThrough();
		}

		@Override
		public double entity(double to) {
			entityCalls++;
			double best = Double.POSITIVE_INFINITY;
			for (double[] b : entities) {
				best = Math.min(best, PetSight.enter(eye[0], eye[1], eye[2], dir[0], dir[1], dir[2], b[0], b[1], b[2], b[3], b[4],
						b[5]));
			}
			return best <= to ? best : Double.POSITIVE_INFINITY;
		}

		/**
		 * Minecrafts eigener Treffer für diesen Strahl ({@code mc.hitResult}): der erste Block bis 4,5 Blöcke, ein Wesen davor
		 * gewinnt. {@code [0]} = Entfernung (unendlich = keiner), {@code [1]} = 1, wenn er eine durchsichtige Pflanze ist.
		 */
		double[] vanilla() {
			int[] c = new int[3];
			double block = block(0, PetInteract.REACH, c);
			double entity = entity(PetInteract.REACH);
			blockCalls = entityCalls = 0;
			if (entity < block || Double.isNaN(block) && entity < Double.POSITIVE_INFINITY) return new double[] { entity, 0 };
			if (Double.isNaN(block)) return new double[] { Double.POSITIVE_INFINITY, 0 };
			return new double[] { block, seeThrough(c[0], c[1], c[2]) ? 1 : 0 };
		}
	}

	// --- Begleiter -----------------------------------------------------------------------------------------------------

	@AfterEach
	void reset() {
		PetSim.forceSlide = Double.NaN;
	}

	/** Umriss des Fuchses der Website (1,05 Blöcke lang, 0,8 hoch). */
	static float[] fox() throws IOException {
		return PetPick.bounds(PetActionsFormatTest.foxModel("fox_kit.generated.json"));
	}

	/** Besitzer bei (0,5 | 64 | 0,5), ein eigener Fuchs, 60 Ticks: steht ruhig und kann gestreichelt/gefüttert werden. */
	static PetSim settled(float[] box) {
		PetSim sim = new PetSim();
		PetSim.forceSlide = 0;
		FakeWorld w = new FakeWorld();
		for (int i = 0; i < 60; i++) {
			sim.begin(w, i * 50L);
			sim.owner(OWNER, 1, 1.0, null, new int[] { Pet.CAN_PET | Pet.CAN_EAT }, PetInteractTest.MS, new float[][] { box }, 0.5,
					64, 0.5, 0f, false, false, true, w);
			sim.end();
		}
		return sim;
	}

	/** Begleiter an ({@code x}, 64, {@code z}) mit Gieren {@code yaw}. */
	static Pet put(PetSim sim, double x, double z, double yaw) {
		Pet p = sim.pets(OWNER).get(0);
		p.x = p.px = x;
		p.y = p.py = 64;
		p.z = p.pz = z;
		p.yaw = p.pyaw = (float) yaw;
		return p;
	}

	/** Augen des Besitzers. */
	static final double[] EYE = { 0.5, 64 + 1.62, 0.5 };

	/** Mitte des Umrisses von {@code p} in der Welt. */
	static double[] middle(Pet p) {
		double h = Math.toRadians(p.yaw), cos = Math.cos(h), sin = Math.sin(h);
		double mx = (p.box[0] + p.box[3]) / 32, my = (p.box[1] + p.box[4]) / 32, mz = (p.box[2] + p.box[5]) / 32;
		return new double[] { p.x + mx * cos - mz * sin, p.y + my, p.z + mx * sin + mz * cos };
	}

	/** Entfernung bis zum Umriss von {@code p} auf dem Strahl (NaN = verfehlt). */
	static double at(Pet p, double[] eye, double[] d) {
		return PetPick.ray(eye[0], eye[1], eye[2], d[0], d[1], d[2], p.x, p.y, p.z, p.yaw, p.scale, p.box);
	}

	/**
	 * Rechtsklick mit Essen vom Auge {@code eye} in Richtung {@code d} in der Welt {@code g}, Minecrafts Treffer wie dort:
	 * true = der eigene Begleiter wurde getroffen (gefüttert, Minecraft isst nichts).
	 */
	static boolean feed(PetSim sim, Grid g, double[] eye, double[] d) {
		g.aim(eye, d);
		double[] v = g.vanilla();
		return PetInteract.use(sim, eye[0], eye[1], eye[2], d[0], d[1], d[2], v[0], v[1] > 0, g, F, E, false, false, false, 1f,
				59 * 50L + 20);
	}

	/** Bisher (nur bis zu Minecrafts Treffer): wäre der Begleiter getroffen worden? */
	static boolean before(PetSim sim, Grid g, double[] eye, double[] d) {
		g.aim(eye, d);
		return sim.pick(eye[0], eye[1], eye[2], d[0], d[1], d[2], PetInteract.reach(g.vanilla()[0]), 1f) != null;
	}

	// --- Regeln -----------------------------------------------------------------------------------------------------

	@Test
	void plantsWithoutCollisionAndASingleSnowLayerAreSeeThrough() {
		for (Block b : new Block[] { WHEAT, YOUNG_WHEAT, GRASS, FLOWER, TALL_GRASS }) assertTrue(b.seeThrough(), b.name);
		assertTrue(snow(1).seeThrough(), "eine Schneeschicht");
		assertTrue(PetSight.seeThrough(true, false, true, 0), "Setzling, Farn, toter Busch …");
		for (Block b : new Block[] { STONE, DOOR, SIGN, LEVER, BUTTON, COBWEB, PRESSURE_PLATE, BERRY_BUSH, LILY_PAD, snow(2),
				snow(8) }) {
			assertFalse(b.seeThrough(), b.name);
		}
		assertFalse(PetSight.seeThrough(false, false, true, 0), "Redstone, Fackel, Schiene: keine Pflanze");
		assertFalse(PetSight.seeThrough(true, true, true, 0), "Süßbeerenstrauch");
		assertFalse(PetSight.seeThrough(true, false, false, 0), "Pflanze mit Kollisionsform (Azalee)");
	}

	@Test
	void aHitThatIsNoPlantStaysTheLimitWithoutLookingFurther() {
		PetSight.Ray never = new PetSight.Ray() {
			@Override
			public double block(double from, double to, int[] cell) {
				throw new AssertionError("nicht gefragt");
			}

			@Override
			public boolean seeThrough(int x, int y, int z) {
				throw new AssertionError("nicht gefragt");
			}

			@Override
			public double entity(double to) {
				throw new AssertionError("nicht gefragt");
			}
		};
		assertEquals(2.25, PetSight.reach(0, 65, 0, 0, 0, 1, 2.25, false, never), 0, "Block/Wesen davor");
		assertEquals(PetInteract.REACH, PetSight.reach(0, 65, 0, 0, 0, 1, Double.POSITIVE_INFINITY, false, never), 0, "nichts");
		assertEquals(PetInteract.REACH, PetSight.reach(0, 65, 0, 0, 0, 1, 7, false, never), 0, "weiter weg als 4,5");
		assertEquals(1.5, PetSight.reach(0, 65, 0, 0, 0, 1, 1.5, true, null), 0, "ohne Welt: wie bisher");
		assertEquals(1.5, PetSight.reach(0, 65, 0, 0, 0, 0, 1.5, true, never), 0, "ohne Richtung");
	}

	// --- Felder und Wiesen ------------------------------------------------------------------------------------------------

	@Test
	void aCompanionInAMatureWheatFieldIsFed() throws IOException {
		PetSim sim = settled(fox());
		// Weizen in jeder Zelle bis hinter den Fuchs, auch unter dem Besitzer – der Fuchs schaut zum Besitzer (1,8–2,2 Blöcke)
		Grid g = new Grid().field(-2, 3, 64, -1, 6, WHEAT);
		for (double z : new double[] { 2.3, 2.5, 2.7, 3.5 }) {
			Pet p = put(sim, 0.5, z, 180);
			double[] d = PetPickTest.dir(EYE, middle(p));
			g.aim(EYE, d);
			double[] v = g.vanilla();
			double pet = at(p, EYE, d);
			assertEquals(1, v[1], 0, "Minecraft trifft den Weizen");
			assertTrue(v[0] < pet, z + ": Weizen vor dem Fuchs (" + v[0] + " < " + pet + ")");
			assertFalse(before(sim, g, EYE, d), z + ": bisher verfehlt – Minecraft aß das Essen");
			// gesucht wird bis zum ersten Treffer, der keine Pflanze ist: der Boden hinter dem Fuchs
			double floor = (64 - EYE[1]) / d[1];
			assertEquals(floor, PetSight.reach(EYE[0], EYE[1], EYE[2], d[0], d[1], d[2], v[0], true, g.aim(EYE, d)), 1e-3, "" + z);
			assertTrue(floor > pet && floor < PetInteract.REACH, z + ": Boden hinter dem Fuchs");
			assertTrue(feed(sim, g, EYE, d), z + ": gefüttert");
		}
		assertEquals(Pet.EAT, sim.pets(OWNER).get(0).state());
	}

	@Test
	void youngCropsShortGrassFlowersAndThinSnowInFrontAreSeeThrough() throws IOException {
		PetSim sim = settled(fox());
		Pet p = put(sim, 0.5, 3.5, 180);
		// tief auf seine Vorderseite gezielt: der Strahl streift davor auch niedrige Pflanzen und eine Schneeschicht
		double[] d = PetPickTest.dir(EYE, 0.5, 64.05, 3.0);
		double pet = at(p, EYE, d);
		for (Block b : new Block[] { YOUNG_WHEAT, GRASS, FLOWER, snow(1) }) {
			Grid g = new Grid().field(-1, 2, 64, 1, 5, b);
			g.aim(EYE, d);
			double[] v = g.vanilla();
			assertEquals(1, v[1], 0, b.name + ": Minecrafts Treffer");
			assertTrue(v[0] < pet, b.name + " vor dem Fuchs: " + v[0] + " < " + pet);
			assertFalse(before(sim, g, EYE, d), b.name + ": bisher verfehlt");
			assertTrue(feed(sim, g, EYE, d), b.name);
		}
		// zwei Schneeschichten haben eine Kollisionsform: sie gewinnen
		assertFalse(feed(sim, new Grid().field(-1, 2, 64, 1, 5, snow(2)), EYE, d), "zwei Schneeschichten");
		// gemischte Wiese: Blumen vor ihm, kurzes Gras in seiner Zelle, junger Weizen daneben
		Grid meadow = new Grid().field(-1, 2, 64, 1, 2, FLOWER).field(-1, 2, 64, 3, 3, GRASS).field(-1, 2, 64, 4, 5, YOUNG_WHEAT);
		assertTrue(feed(sim, meadow, EYE, d));
	}

	@Test
	void aCompanionInATallGrassPatchIsFedFromOutsideAndFromInside() throws IOException {
		PetSim sim = settled(fox());
		for (double[] at : new double[][] { { 0.5, 3.5, 180 }, { 1.7, 2.9, 135 }, { -0.6, 3.2, -150 } }) {
			Pet p = put(sim, at[0], at[1], at[2]);
			int cx = (int) Math.floor(at[0]), cz = (int) Math.floor(at[1]);
			// 3 × 3 Zellen um ihn, zwei Blöcke hoch; der Besitzer steht draußen
			Grid patch = new Grid().field(cx - 1, cx + 1, 64, cz - 1, cz + 1, TALL_GRASS).field(cx - 1, cx + 1, 65, cz - 1, cz + 1,
					TALL_GRASS);
			double[] d = PetPickTest.dir(EYE, middle(p));
			patch.aim(EYE, d);
			assertEquals(1, patch.vanilla()[1], 0, "Minecraft trifft das hohe Gras");
			assertFalse(before(sim, patch, EYE, d), "bisher verfehlt");
			assertTrue(feed(sim, patch, EYE, d), "von draußen");
			// der Besitzer steht selbst im hohen Gras: das Auge liegt im Umriss der oberen Hälfte (Treffer bei 0)
			Grid inside = new Grid().field(-2, 3, 64, -2, 5, TALL_GRASS).field(-2, 3, 65, -2, 5, TALL_GRASS);
			inside.aim(EYE, d);
			assertEquals(0, inside.vanilla()[0], 1e-9, "Auge im Gras");
			assertTrue(feed(sim, inside, EYE, d), "von drinnen");
		}
	}

	// --- Alles andere gewinnt, in jeder Zelle ------------------------------------------------------------------------------

	@Test
	void blocksThatAreNoPlantsStillWinInFrontAndInItsOwnCell() throws IOException {
		PetSim sim = settled(fox());
		// Fuchs hinten in seiner Zelle (0, 64, 3), schaut zum Besitzer: vorne in der Zelle ist Platz
		Pet p = put(sim, 0.5, 3.9, 180);
		double[] d = PetPickTest.dir(EYE, middle(p));
		double pet = at(p, EYE, d);
		Grid field = new Grid().field(-2, 3, 64, -1, 6, WHEAT);
		assertTrue(feed(sim, field, EYE, d), "Probe: im Weizen gefüttert");
		// in der Zelle davor (0, 64, 2) …
		for (Block b : new Block[] { STONE, DOOR, SIGN, LEVER, COBWEB, BERRY_BUSH, snow(8) }) {
			Grid g = new Grid().field(-2, 3, 64, -1, 6, WHEAT).put(0, 64, 2, b);
			assertBlocks(sim, g, d, pet, b, 2);
		}
		// … und in seiner eigenen Zelle vor ihm
		for (Block b : new Block[] { DOOR_NORTH, SIGN, LEVER_NORTH, BERRY_BUSH }) {
			Grid g = new Grid().field(-2, 3, 64, -1, 6, WHEAT).put(0, 64, 3, b);
			assertBlocks(sim, g, d, pet, b, 3);
		}
		// Hinter ihm spielen Blöcke keine Rolle
		assertTrue(feed(sim, new Grid().field(-2, 3, 64, -1, 6, WHEAT).put(0, 64, 5, STONE).put(0, 65, 5, STONE), EYE, d));
	}

	/**
	 * {@code b} in Zelle (0, 64, {@code z}) liegt auf dem Strahl vor dem Begleiter und gewinnt: gesucht wird genau bis zum
	 * Eintritt in seinen Umriss – auch wenn er an der Grenze zur Pflanzen-Zelle davor beginnt.
	 */
	private static void assertBlocks(PetSim sim, Grid g, double[] d, double pet, Block b, int z) {
		double hit = PetPickTest.outline(EYE, d, 0, 64, z, b.boxes[0]);
		assertTrue(hit < pet, b.name + " in Zelle z = " + z + " vor dem Begleiter: " + hit + " < " + pet);
		g.aim(EYE, d);
		assertEquals(hit, PetSight.reach(EYE[0], EYE[1], EYE[2], d[0], d[1], d[2], g.vanilla()[0], true, g.aim(EYE, d)), 1e-9,
				b.name);
		assertFalse(feed(sim, g, EYE, d), b.name + " in Zelle z = " + z + " gewinnt");
	}

	@Test
	void wallButtonsInFrontOfItStillWin() throws IOException {
		PetSim sim = settled(fox());
		Pet p = put(sim, 0.5, 3.9, 180);
		// tiefer gezielt: der Strahl läuft in Knopfhöhe (6…10 px) über die Grenze der Zellen 2 und 3
		double[] d = PetPickTest.dir(EYE, 0.5, 64.276, 3.5);
		double pet = at(p, EYE, d);
		assertTrue(feed(sim, new Grid().field(-2, 3, 64, -1, 6, WHEAT), EYE, d), "Probe: im Weizen gefüttert");
		assertBlocks(sim, new Grid().field(-2, 3, 64, -1, 6, WHEAT).put(0, 64, 2, BUTTON), d, pet, BUTTON, 2);
		assertBlocks(sim, new Grid().field(-2, 3, 64, -1, 6, WHEAT).put(0, 64, 3, BUTTON_NORTH), d, pet, BUTTON_NORTH, 3);
	}

	@Test
	void anAnimalOrArmorStandBehindThePlantStillWins() throws IOException {
		PetSim sim = settled(fox());
		Pet p = put(sim, 0.5, 3.9, 180);
		double[] d = PetPickTest.dir(EYE, middle(p));
		double pet = at(p, EYE, d);
		// Huhn im Weizen zwischen Minecrafts Treffer (der Weizen) und dem Fuchs: Minecraft hat nur bis zum Weizen gesucht
		Grid g = new Grid().field(-2, 3, 64, -1, 6, WHEAT).entity(0.3, 64, 2.7, 0.7, 64.7, 3.1);
		g.aim(EYE, d);
		double[] v = g.vanilla();
		assertEquals(1, v[1], 0, "Minecrafts Treffer ist der Weizen");
		double chicken = g.entity(PetInteract.REACH);
		assertTrue(v[0] < chicken && chicken < pet, v[0] + " < " + chicken + " < " + pet);
		assertEquals(chicken, PetSight.reach(EYE[0], EYE[1], EYE[2], d[0], d[1], d[2], v[0], true, g.aim(EYE, d)), 1e-9);
		assertFalse(feed(sim, g, EYE, d), "das Huhn gewinnt");
		// hinter dem Fuchs: egal
		assertTrue(feed(sim, new Grid().field(-2, 3, 64, -1, 6, WHEAT).entity(0.3, 64, 4.6, 0.7, 64.7, 5.0), EYE, d));
	}

	@Test
	void tooFarAwayIsNeverPickedNotEvenThroughPlants() throws IOException {
		PetSim sim = settled(fox());
		Pet p = put(sim, 0.5, 5.6, 180);
		double[] d = PetPickTest.dir(EYE, middle(p));
		assertTrue(at(p, EYE, d) > PetInteract.REACH, "weiter als 4,5 Blöcke");
		Grid g = new Grid().field(-2, 3, 64, -1, 8, WHEAT);
		assertFalse(feed(sim, g, EYE, d));
		assertFalse(feed(sim, new Grid(), EYE, d), "auch ohne Pflanzen");
		g.aim(EYE, d);
		assertTrue(PetSight.reach(EYE[0], EYE[1], EYE[2], d[0], d[1], d[2], g.vanilla()[0], true, g.aim(EYE, d))
				<= PetInteract.REACH);
	}

	// --- Die Suche ist begrenzt ---------------------------------------------------------------------------------------------

	@Test
	void aDiagonalLookAcrossAWideFieldNeedsFewerSearchStepsThanTheLimit() throws IOException {
		PetSim sim = settled(fox());
		// flach und schräg über das Feld: viele Zellen auf dem Strahl
		Pet p = put(sim, 3.6, 3.4, -135);
		double[] eye = { 0.5, 64.9, 0.5 };
		double[] d = PetPickTest.dir(eye, middle(p));
		Grid g = new Grid().field(-1, 6, 64, -1, 6, WHEAT);
		assertTrue(at(p, eye, d) > 3.5, "weit weg: " + at(p, eye, d));
		g.aim(eye, d);
		double[] v = g.vanilla();
		assertEquals(0, v[0], 1e-9, "das Auge steckt im Weizen");
		double cap = PetSight.reach(eye[0], eye[1], eye[2], d[0], d[1], d[2], v[0], true, g.aim(eye, d));
		assertTrue(g.blockCalls <= PetSight.MAX_CELLS, "Schritte: " + g.blockCalls);
		assertTrue(g.blockCalls >= 6, "Probe: viele Zellen übersprungen (" + g.blockCalls + ")");
		assertTrue(cap > at(p, eye, d), "bis hinter den Fuchs gesucht: " + cap);
		assertTrue(feed(sim, g, eye, d));
	}

	@Test
	void aWorldThatNeverEndsInPlantsStopsTheSearchCautiously() {
		// meldet immer wieder dieselbe Pflanzen-Zelle am Auge (fehlerhafte Welt): höchstens MAX_CELLS Schritte, und dann wird
		// nicht weiter gesucht als bis zur letzten geprüften Stelle
		final int[] calls = { 0 };
		PetSight.Ray stuck = new PetSight.Ray() {
			@Override
			public double block(double from, double to, int[] cell) {
				calls[0]++;
				cell[0] = 0;
				cell[1] = 65;
				cell[2] = 0;
				return from;
			}

			@Override
			public boolean seeThrough(int x, int y, int z) {
				return true;
			}

			@Override
			public double entity(double to) {
				return Double.POSITIVE_INFINITY;
			}
		};
		double cap = PetSight.reach(0.5, 65.62, 0.5, 0, -0.3, 1, 0, true, stuck);
		assertEquals(PetSight.MAX_CELLS, calls[0]);
		assertTrue(cap < 1.5, "vorsichtig: " + cap);
		// eine Welt voller Pflanzen bis zum Horizont: nach der vollen Reichweite ist Schluss
		calls[0] = 0;
		PetSight.Ray endless = new PetSight.Ray() {
			@Override
			public double block(double from, double to, int[] cell) {
				calls[0]++;
				double t = from + 0.05;
				cell[0] = 0;
				cell[1] = 65;
				cell[2] = (int) Math.floor(0.5 + t);
				return t <= to ? t : Double.NaN;
			}

			@Override
			public boolean seeThrough(int x, int y, int z) {
				return true;
			}

			@Override
			public double entity(double to) {
				return Double.POSITIVE_INFINITY;
			}
		};
		assertEquals(PetInteract.REACH, PetSight.reach(0.5, 65.5, 0.5, 0, 0, 1, 0.05, true, endless), 0);
		assertTrue(calls[0] <= PetSight.MAX_CELLS, "Schritte: " + calls[0]);
	}

	@Test
	void aBlockRightBehindAPlantCellStartsAtItsBorder() {
		// Auge in einer Pflanzen-Zelle (0, 65, 0), direkt dahinter (0, 65, 1) ein Block, dessen Umriss an der Grenze beginnt
		// (Spinnweben, Tür, Hebel an der Wand): der nächste Schritt beginnt darin – Minecraft meldet ihn dann 0,1 % der
		// Strecke dahinter, Legacy am Beginn. Er beginnt aber schon an der Grenze.
		final double[] eye = { 0.5, 65.5, 0.5 };
		final double[] d = { 0, 0, 1 };
		final double border = PetSight.exit(eye[0], eye[1], eye[2], d[0], d[1], d[2], 0, 65, 0);
		assertEquals(0.5, border, 1e-12);
		for (final double offset : new double[] { 0.001, 0 }) {
			PetSight.Ray ray = new PetSight.Ray() {
				@Override
				public double block(double from, double to, int[] cell) {
					cell[0] = 0;
					cell[1] = 65;
					cell[2] = from < border ? 0 : 1;
					return from < border ? from : from + offset * (to - from);
				}

				@Override
				public boolean seeThrough(int x, int y, int z) {
					return z == 0;
				}

				@Override
				public double entity(double to) {
					return Double.POSITIVE_INFINITY;
				}
			};
			assertEquals(border, PetSight.reach(eye[0], eye[1], eye[2], d[0], d[1], d[2], 0, true, ray), 1e-12, "Meldung + " + offset);
		}
		// ein Block, der erst weiter hinten in seiner Zelle beginnt, zählt dort
		PetSight.Ray later = new PetSight.Ray() {
			@Override
			public double block(double from, double to, int[] cell) {
				cell[0] = 0;
				cell[1] = 65;
				cell[2] = from < border ? 0 : 1;
				return from < border ? from : 1.25;
			}

			@Override
			public boolean seeThrough(int x, int y, int z) {
				return z == 0;
			}

			@Override
			public double entity(double to) {
				return Double.POSITIVE_INFINITY;
			}
		};
		assertEquals(1.25, PetSight.reach(eye[0], eye[1], eye[2], d[0], d[1], d[2], 0, true, later), 0);
	}

	@Test
	void aSearchStepLeavesThePlantCellExactly() {
		// Auge mitten in (0, 65, 0), Blick nach Süden und leicht nach unten
		double[] d = PetPickTest.dir(new double[] { 0.5, 65.5, 0.5 }, 0.5, 65.0, 3.0);
		double exit = PetSight.exit(0.5, 65.5, 0.5, d[0], d[1], d[2], 0, 65, 0);
		assertEquals(1.0, 0.5 + d[2] * exit, 1e-9, "verlässt die Zelle an ihrer Südseite");
		assertTrue(Double.isNaN(PetSight.exit(0.5, 65.5, 0.5, d[0], d[1], d[2], 3, 65, 0)), "Zelle daneben: verfehlt");
		assertEquals(0, PetSight.enter(0.5, 65.5, 0.5, d[0], d[1], d[2], 0, 65, 0, 1, 66, 1), 0, "Auge darin");
		assertEquals(Double.POSITIVE_INFINITY, PetSight.enter(0.5, 65.5, 0.5, 0, 0, -1, 0, 65, 2, 1, 66, 3), 0, "hinter dem Auge");
	}

	@Test
	void theCompanionInGrassOfItsOwnCellIsStillPickedAndOnlyItself() throws IOException {
		PetSim sim = settled(fox());
		// quer in der Zelle (0, 64, 2): vorne und hinten im Umriss des Grases
		Pet p = put(sim, 0.5, 2.5, 90);
		double[] d = PetPickTest.dir(EYE, middle(p));
		Grid g = new Grid().put(0, 64, 2, GRASS);
		g.aim(EYE, d);
		assertTrue(g.vanilla()[0] < at(p, EYE, d), "Gras vor dem Fuchs getroffen");
		assertTrue(feed(sim, g, EYE, d));
		assertSame(p, sim.pets(OWNER).get(0));
		assertEquals(Pet.EAT, p.state());
		// über ihn hinweg ins Gras geschaut: der Strahl muss den Fuchs selbst treffen
		double[] beside = PetPickTest.dir(EYE, 0.5, 64.8, 2.8);
		assertTrue(Double.isNaN(at(p, EYE, beside)));
		g.aim(EYE, beside);
		assertEquals(1, g.vanilla()[1], 0, "Minecraft trifft das Gras");
		assertFalse(feed(sim, g, EYE, beside));
		assertNull(sim.pick(EYE[0], EYE[1], EYE[2], beside[0], beside[1], beside[2], PetInteract.REACH, 1f));
	}
}
