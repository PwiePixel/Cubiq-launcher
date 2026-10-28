package dev.pwiepixel.cubiqclient.core.cosmetic.pet;

/**
 * Streicheln/Füttern der eigenen Begleiter am Boden per Rechtsklick (2026-10-28) – die Regeln ohne Minecraft-Klassen.
 * Leere Hand streichelt; Essen in der Haupthand (oder leere Haupthand und Essen in der Zweithand) füttert – rein
 * optisch und nur bei diesem Spieler: nichts wird gegessen, nichts geht an den Server. Schleichen, ein offener
 * Bildschirm oder der Zuschauer-Modus lassen Minecraft den Klick wie immer behandeln, ebenso jeder andere Gegenstand in
 * der Haupthand (Blöcke setzen, Bogen, Schild …).
 */
public final class PetInteract {
	/** Was in einer Hand liegt: nichts, etwas Essbares, etwas anderes. */
	public static final int HELD_EMPTY = 0, HELD_FOOD = 1, HELD_OTHER = 2;
	/** Ergebnis von {@link #action}: Minecraft machen lassen, streicheln ({@link Pet#PET}), füttern ({@link Pet#EAT}). */
	public static final int NONE = -1, PET = Pet.PET, FEED = Pet.EAT;
	/** Reichweite ab dem Auge in Blöcken – nie weiter als Minecrafts eigener Treffer (Block oder Wesen davor gewinnt). */
	public static final double REACH = 4.5;

	private PetInteract() {
	}

	/** Was ein Rechtsklick mit diesen Händen auf den eigenen Begleiter tut ({@link #NONE} = Minecraft wie immer). */
	public static int action(int mainHeld, int offHeld, boolean sneaking, boolean spectator, boolean screenOpen) {
		if (sneaking || spectator || screenOpen) return NONE;
		if (mainHeld == HELD_FOOD) return FEED;
		if (mainHeld != HELD_EMPTY) return NONE;
		return offHeld == HELD_FOOD ? FEED : PET;
	}

	/**
	 * Wie weit der Klick-Test sucht: {@link #REACH}, höchstens bis zu Minecrafts eigenem Treffer {@code vanillaDist}
	 * (Blöcke ab dem Auge; unendlich bzw. NaN = kein Treffer).
	 */
	public static double reach(double vanillaDist) {
		return vanillaDist >= 0 && vanillaDist < REACH ? vanillaDist : REACH;
	}

	/**
	 * Wie {@link #use(PetSim, double, double, double, double, double, double, double, boolean, int, int, int, int, int,
	 * boolean, boolean, boolean, float, long)}, wenn Minecrafts Treffer kein Block ohne Kollisionsform ist.
	 */
	public static boolean use(PetSim sim, double ex, double ey, double ez, double dx, double dy, double dz, double vanillaDist,
			int mainHeld, int offHeld, boolean sneaking, boolean spectator, boolean screenOpen, float alpha, long nowMs) {
		return use(sim, ex, ey, ez, dx, dy, dz, vanillaDist, false, 0, 0, 0, mainHeld, offHeld, sneaking, spectator, screenOpen,
				alpha, nowMs);
	}

	/**
	 * Ein Rechtsklick des eigenen Spielers: Auge ({@code ex, ey, ez}) und Blickrichtung ({@code dx, dy, dz}) in der Welt,
	 * Entfernung bis zu Minecrafts eigenem Treffer, ob dieser ein Block ohne Kollisionsform ist ({@code softBlock}, z. B.
	 * Gras, in dem der Begleiter steht) samt seiner Zelle ({@code bx, by, bz}), die Hände und Zustände wie bei
	 * {@link #action}. Trifft der Strahl zuerst einen eigenen Begleiter ({@link PetSim#pick(double, double, double, double,
	 * double, double, double, boolean, int, int, int, float)}), wird er gestreichelt bzw. gefüttert ({@link PetSim#interact})
	 * und das Ergebnis ist true – auch wenn er gerade nicht kann (kurze Pause, Aktion läuft schon): Minecraft darf den
	 * Klick dann trotzdem nicht benutzen, sonst würde das Essen in der Hand gegessen. false = Minecraft wie immer.
	 */
	public static boolean use(PetSim sim, double ex, double ey, double ez, double dx, double dy, double dz, double vanillaDist,
			boolean softBlock, int bx, int by, int bz, int mainHeld, int offHeld, boolean sneaking, boolean spectator,
			boolean screenOpen, float alpha, long nowMs) {
		int action = action(mainHeld, offHeld, sneaking, spectator, screenOpen);
		if (action == NONE || sim == null) return false;
		Pet p = sim.pick(ex, ey, ez, dx, dy, dz, vanillaDist, softBlock, bx, by, bz, alpha);
		if (p == null) return false;
		sim.interact(p, action, nowMs);
		return true;
	}
}
