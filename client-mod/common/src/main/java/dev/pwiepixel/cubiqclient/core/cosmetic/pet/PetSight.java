package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

/**
 * Was zwischen Auge und Begleiter liegt, wenn der Besitzer ihn per Rechtsklick streichelt oder füttert: Pflanzen sind
 * durchsichtig. Minecrafts Fadenkreuz trifft den Umriss von Weizen, Gras oder Blumen vor dem Begleiter – reifer Weizen und
 * doppelte Pflanzen füllen ihre ganze Zelle, in einem Feld oder einer Wiese liegt also fast immer eine Pflanze davor, und
 * Minecraft würde das Essen in der Hand essen. Ist Minecrafts Treffer eine durchsichtige Pflanze ({@link #seeThrough}),
 * sucht der Klick-Test deshalb bis zum ersten Treffer auf dem Strahl, der keine ist – Block oder Wesen –, sonst bis
 * {@link PetInteract#REACH}. Alles andere gewinnt, wo immer es liegt: feste Blöcke, Wesen, Hebel, Knöpfe, Druckplatten,
 * Redstone, Schilder, Türen, Falltüren, Rahmen, der Süßbeerenstrauch … Reine Regeln ohne Minecraft-Klassen; was der Strahl
 * trifft und was ein Block ist, liefern die Loader ({@link Ray}).
 */
public final class PetSight {
	/**
	 * Höchstens so viele Pflanzen-Zellen werden übersprungen. Ein Strahl von {@link PetInteract#REACH} = 4,5 Blöcken kreuzt
	 * höchstens 1 + 3 + 4,5 · √3 ≈ 11,8, also 11 Zellen – die Suche ist also immer vorher am Ende.
	 */
	static final int MAX_CELLS = 12;
	/** So weit hinter dem Ausgang einer Pflanzen-Zelle (Blöcke) sucht der nächste Schritt weiter. */
	static final double STEP = 1e-4;
	/**
	 * Ein Treffer bis so weit (Blöcke) hinter dem Beginn eines Schritts lag schon an dessen Beginn: Beginnt der Strahl in
	 * einem Umriss, meldet Minecraft den Treffer 0,1 % der Strecke dahinter (höchstens 4,5 mm), Legacy am Anfang
	 * ({@code Ray#block}).
	 */
	static final double INSIDE = 0.005;

	/**
	 * Die Welt entlang des Blick-Strahls (je Loader, nur Spiel-Thread, nur für einen Klick): Auge und normierte
	 * Blickrichtung wie beim Klick-Test, Entfernungen in Blöcken ab dem Auge.
	 */
	public interface Ray {
		/**
		 * Erster Block, dessen Umriss der Strahl zwischen {@code from} und {@code to} trifft – wie Minecrafts Fadenkreuz,
		 * Flüssigkeiten zählen nicht; liegt {@code from} schon in einem Umriss, ist das der Treffer (bei {@code from} bzw.
		 * knapp dahinter). Ergebnis: Entfernung des Treffers, NaN = keiner; seine Zelle in {@code cell} (x, y, z).
		 */
		double block(double from, double to, int[] cell);

		/** Ist der Block in der Zelle (x, y, z) durchsichtig ({@link PetSight#seeThrough})? */
		boolean seeThrough(int x, int y, int z);

		/**
		 * Entfernung des ersten Wesens auf dem Strahl bis {@code to}, das Minecrafts Fadenkreuz treffen würde (anklickbar,
		 * kein Zuschauer, nicht das eigene Reittier); unendlich = keins.
		 */
		double entity(double to);
	}

	private PetSight() {
	}

	/**
	 * Ist ein Block für den Klick-Test durchsichtig? Eine Pflanze ({@code vegetation}: Getreide, Setzlinge, Blumen, kurzes
	 * und hohes Gras, Farne, doppelte Pflanzen, toter Busch …) ohne Kollisionsform ({@code noCollision}) – außer dem
	 * Süßbeerenstrauch ({@code berryBush}: ein Rechtsklick pflückt, und er sticht) –, oder eine einzelne Schneeschicht
	 * ({@code snowLayers} = 1; 0 = kein Schnee). Pflanzen mit Kollisionsform (Seerose, Azalee), dickerer Schnee und alle
	 * anderen Blöcke halten den Strahl auf.
	 */
	public static boolean seeThrough(boolean vegetation, boolean berryBush, boolean noCollision, int snowLayers) {
		if (snowLayers > 0) return snowLayers == 1;
		return vegetation && noCollision && !berryBush;
	}

	/**
	 * Wie weit der Klick-Test vom Auge ({@code ex, ey, ez}) in Blickrichtung ({@code dx, dy, dz}) sucht: bis zu Minecrafts
	 * eigenem Treffer ({@code vanillaDist}, siehe {@link PetInteract#reach}) – ist dieser eine durchsichtige Pflanze
	 * ({@code vanillaPlant}), bis zum ersten Treffer auf dem Strahl, der keine ist (Block oder Wesen aus {@code ray}),
	 * höchstens {@link PetInteract#REACH}. Jede Pflanze auf dem Weg wird bis zum Ausgang ihrer Zelle übersprungen; nach
	 * {@link #MAX_CELLS} Zellen endet die Suche vorsichtig dort, wo sie gerade ist.
	 */
	public static double reach(double ex, double ey, double ez, double dx, double dy, double dz, double vanillaDist,
			boolean vanillaPlant, Ray ray) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!vanillaPlant || ray == null || !(len > 1e-9)) return PetInteract.reach(vanillaDist);
		dx /= len;
		dy /= len;
		dz /= len;
		double end = PetInteract.REACH;
		// Wesen hinter der Pflanze gewinnen ebenso (Minecraft hat nur bis zur Pflanze nach ihnen gesucht)
		double entity = ray.entity(end);
		if (entity >= 0 && entity < end) end = entity;
		int[] cell = new int[3];
		double from = 0, last = 0;
		for (int i = 0; i < MAX_CELLS; i++) {
			if (from >= end) return end;
			double t = ray.block(from, end, cell);
			if (!(t <= end)) return end; // nichts mehr bis zum Ende
			if (!ray.seeThrough(cell[0], cell[1], cell[2])) {
				// gleich am Beginn eines Schritts getroffen: der Block beginnt schon am Ausgang der Pflanzen-Zelle davor (beim
				// ersten Schritt am Auge)
				return t - from <= INSIDE ? last : Math.max(0, t);
			}
			// hinter dem Ausgang der Pflanzen-Zelle weiter (immer vorwärts, auch wenn die Rechnung die Zelle knapp verfehlt)
			double next = Math.max(from, t), out = exit(ex, ey, ez, dx, dy, dz, cell[0], cell[1], cell[2]);
			last = out > next ? out : next;
			from = last + STEP;
		}
		return Math.min(from, end);
	}

	/** Ausgang des Strahls (Richtung normiert) aus der Zelle (x, y, z) in Blöcken ab dem Auge; NaN, wenn er sie verfehlt. */
	static double exit(double ex, double ey, double ez, double dx, double dy, double dz, int x, int y, int z) {
		return clip(ex, ey, ez, dx, dy, dz, x, y, z, x + 1, y + 1, z + 1, true);
	}

	/**
	 * Eintritt des Strahls vom Auge ({@code ex, ey, ez}) in Richtung ({@code dx, dy, dz}, normiert) in den Kasten von
	 * ({@code x0, y0, z0}) bis ({@code x1, y1, z1}) (Welt, Blöcke) in Blöcken ab dem Auge: 0 = Auge darin, unendlich =
	 * verfehlt oder hinter dem Auge. Für die Wesen-Suche der Loader ohne eigene Schnitt-Rechnung (Legacy).
	 */
	public static double enter(double ex, double ey, double ez, double dx, double dy, double dz, double x0, double y0, double z0,
			double x1, double y1, double z1) {
		double t = clip(ex, ey, ez, dx, dy, dz, x0, y0, z0, x1, y1, z1, false);
		return Double.isNaN(t) ? Double.POSITIVE_INFINITY : t;
	}

	/** Strahl gegen einen achsenparallelen Kasten: Eintritt (≥ 0) bzw. Austritt ({@code exit}); NaN ohne Schnitt. */
	private static double clip(double ex, double ey, double ez, double dx, double dy, double dz, double x0, double y0, double z0,
			double x1, double y1, double z1, boolean exit) {
		double[] o = { ex, ey, ez }, d = { dx, dy, dz }, lo = { x0, y0, z0 }, hi = { x1, y1, z1 };
		double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
		for (int a = 0; a < 3; a++) {
			if (Math.abs(d[a]) < 1e-12) {
				if (o[a] < lo[a] || o[a] > hi[a]) return Double.NaN;
				continue;
			}
			double t1 = (lo[a] - o[a]) / d[a], t2 = (hi[a] - o[a]) / d[a];
			near = Math.max(near, Math.min(t1, t2));
			far = Math.min(far, Math.max(t1, t2));
		}
		if (near > far || far < 0) return Double.NaN;
		return exit ? far : Math.max(0, near);
	}
}
