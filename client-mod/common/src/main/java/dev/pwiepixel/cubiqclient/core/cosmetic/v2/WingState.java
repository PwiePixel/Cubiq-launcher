package dev.pwiepixel.cubiqclient.core.cosmetic.v2;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Bewegungs-Zustand getragener Teile je Spieler – Flügel (Vertrag „Wings“), Kopf-Kosmetik und Begleiter am Kopf:
 * {@link #IDLE} steht, {@link #WALK} läuft am Boden, {@link #JUMP} springt ab (steigt), {@link #AIR} ohne Boden (Fall,
 * Kreativflug, Elytra), {@link #SNEAK} schleicht. Vorrang sneak > jump > air > walk > idle ({@link #classify}).
 * Wechselt der Zustand, wird {@link #FADE_MS} lang vom alten in den neuen überblendet; in die Luft erst nach
 * {@link #AIR_DELAY_MS} (außer direkt nach dem Absprung), Laufen hält {@link #WALK_HOLD_MS} nach, der Absprung beginnt
 * sofort. Fehlt dem Modell die Animation des Zustands, spielt der Renderer {@code idle}.
 *
 * <p>walk und jump kennen erst Clients ab 0.24.0. Ältere spielen nur idle/air/sneak: Laufen zeigt idle, ein Sprung air
 * (nach 100 ms). {@link #forModel} bildet beide für Modelle ohne diese Animationen genau so ab – deren Zustände wechseln
 * also in den gleichen Momenten wie in den älteren Clients.
 *
 * <p>Nur aus dem Spiel-/Render-Thread benutzen; je Fach (Hut, Begleiter am Kopf, Flügel) eine eigene Instanz.
 */
public final class WingState {
	public static final int IDLE = 0, AIR = 1, SNEAK = 2, WALK = 3, JUMP = 4;
	/** Laufen ab dieser waagerechten Geschwindigkeit (Blöcke/Tick; Gehen ~0,216, Schleichen ~0,065). */
	static final float WALK_SPEED = 0.04f;
	/** Laufen endet erst so lange nach der letzten Meldung (kein Flackern zwischen Ticks und beim Abbremsen). */
	static final long WALK_HOLD_MS = 150L;
	/** Absprung = in der Luft und steigt schneller als das (Blöcke/Tick; ein Sprung beginnt mit 0,42). */
	static final float JUMP_VY = 0.05f;
	/** Überblendung zwischen zwei Zuständen. */
	public static final long FADE_MS = 250L;
	/** Einträge, die so lange nicht gefragt wurden, fallen weg. */
	static final long IDLE_MS = 60_000L;

	/**
	 * Nur Selbsttests: Zustand für alle erzwingen (-1 = aus). Gilt ohne {@link #forModel} – fehlt dem Modell die
	 * Animation, spielt der Renderer {@code idle}.
	 */
	public static volatile int forced = -1;

	/** Ergebnis für ein Bild: Treiber (Index wie {@code CosmeticV2#driverIndex}) jetzt/vorher und Überblendung 0..1. */
	public static final class Snapshot {
		public final int state;
		public final int driver;
		public final int prevDriver;
		public final float blend;

		Snapshot(int state, int driver, int prevDriver, float blend) {
			this.state = state;
			this.driver = driver;
			this.prevDriver = prevDriver;
			this.blend = blend;
		}
	}

	private static final class Track {
		int state;
		int prev;
		long since;
		long seen;
		/** Seit wann ohne Boden gemeldet (0 = nicht). */
		long airFrom;
		/** Wann zuletzt {@link #WALK} gemeldet wurde. */
		long walkSeen;
	}

	private final Map<Object, Track> tracks = new HashMap<Object, Track>();
	private long lastCleanup;

	/** In die Luft erst nach so langer Zeit ohne Boden (Treppenstufen/Stufen sollen nicht flattern). */
	static final long AIR_DELAY_MS = 100L;

	/**
	 * Zustand aus der Bewegung (FORMAT.md der Flügel), wie bis 0.23.0: {@link #SNEAK} beim Schleichen (nicht im Flug),
	 * {@link #AIR} ohne Boden oder im Flug – nicht beim Reiten, in Wasser/Lava, an Leiter/Ranke –, sonst {@link #IDLE}.
	 */
	public static int classify(boolean onGround, boolean sneaking, boolean flying, boolean riding, boolean inLiquid,
			boolean climbing) {
		if (sneaking && !flying) return SNEAK;
		if ((!onGround || flying) && !riding && !inLiquid && !climbing) return AIR;
		return IDLE;
	}

	/**
	 * Wie {@link #classify(boolean, boolean, boolean, boolean, boolean, boolean)}, dazu Laufen und Absprung (ab 0.24.0):
	 * {@link #JUMP} in der Luft, nicht im Flug und steigend ({@code vy} > {@link #JUMP_VY}), sonst {@link #AIR};
	 * {@link #WALK} am Boden – nicht beim Reiten, in Wasser/Lava, an Leiter/Ranke – schneller als {@link #WALK_SPEED}.
	 * Vorrang sneak > jump > air > walk > idle.
	 *
	 * @param speed waagerechte Geschwindigkeit seit dem letzten Tick, √(dx² + dz²) in Blöcken/Tick
	 * @param vy    senkrechte Geschwindigkeit seit dem letzten Tick ({@code y − yo}, positiv = nach oben)
	 */
	public static int classify(boolean onGround, boolean sneaking, boolean flying, boolean riding, boolean inLiquid,
			boolean climbing, float speed, float vy) {
		int s = classify(onGround, sneaking, flying, riding, inLiquid, climbing);
		if (s == AIR) return !flying && vy > JUMP_VY ? JUMP : AIR;
		if (s == IDLE && onGround && !riding && !inLiquid && !climbing && speed > WALK_SPEED) return WALK;
		return s;
	}

	/**
	 * Zustand für ein bestimmtes Modell, vor {@link #update}: {@link #WALK} ohne {@code walk}-Animation wird
	 * {@link #IDLE}, {@link #JUMP} ohne {@code jump}-Animation wird {@link #AIR} (fehlt auch {@code air}, spielt der
	 * Renderer {@code idle}). So verläuft der Zustand (Überblendung, Luft-Verzögerung) für Modelle ohne diese Animationen
	 * genau wie in älteren Clients. Hut und Begleiter am Kopf bilden deshalb verschieden ab – je Fach ein eigener Tracker.
	 */
	public static int forModel(CosmeticV2 m, int state) {
		if (state == WALK && !m.hasDriver(CosmeticV2.DRIVER_WALK)) return IDLE;
		if (state == JUMP && !m.hasDriver(CosmeticV2.DRIVER_JUMP)) return AIR;
		return state;
	}

	/**
	 * Hat das Modell eine Zustands-Animation ({@code walk}, {@code jump}, {@code air} oder {@code sneak})? Sonst spielt es
	 * nur {@code idle} und braucht keinen Zustand (Kopf-Kosmetik und Begleiter am Kopf).
	 */
	public static boolean hasStates(CosmeticV2 m) {
		return m.hasDriver(CosmeticV2.DRIVER_AIR) || m.hasDriver(CosmeticV2.DRIVER_SNEAK) || m.hasDriver(CosmeticV2.DRIVER_WALK)
				|| m.hasDriver(CosmeticV2.DRIVER_JUMP);
	}

	/**
	 * Treiber-Index eines Zustands ({@code idle} 0, {@code walk} 1, {@code sneak} 2, {@code jump} 3, {@code air} 4 –
	 * siehe Format §7).
	 */
	public static int driver(int state) {
		switch (state) {
			case AIR:
				return CosmeticV2.DRIVER_AIR;
			case SNEAK:
				return CosmeticV2.DRIVER_SNEAK;
			case WALK:
				return CosmeticV2.DRIVER_WALK;
			case JUMP:
				return CosmeticV2.DRIVER_JUMP;
			default:
				return CosmeticV2.DRIVER_IDLE;
		}
	}

	/**
	 * Zustand aus der Bewegung ({@link #classify}) für das Modell {@code m} melden: erst {@link #forModel}, dann
	 * {@link #update(Object, int, long)}.
	 */
	public Snapshot update(Object key, CosmeticV2 m, int state, long now) {
		return update(key, forModel(m, state), now);
	}

	/** Neuen Zustand von {@code key} (Spieler) melden und den Stand für dieses Bild holen. */
	public Snapshot update(Object key, int state, long now) {
		int f = forced;
		if (f >= 0) state = f;
		Track t = tracks.get(key);
		boolean walking = state == WALK;
		// Laufen kurz halten: zwischen zwei Ticks und beim Abbremsen nicht auf idle flackern.
		if (state == IDLE && f < 0 && t != null && t.state == WALK && now - t.walkSeen < WALK_HOLD_MS) state = WALK;
		if (state == AIR && f < 0) {
			// Erst nach AIR_DELAY_MS in die Luft (kurze Lücken über Stufen zählen nicht) – nach dem Absprung sofort.
			if (t != null && t.state != AIR && t.state != JUMP) {
				if (t.airFrom == 0) t.airFrom = now;
				if (now - t.airFrom < AIR_DELAY_MS) state = t.state;
			}
		} else if (t != null) {
			t.airFrom = 0;
		}
		if (t == null) {
			t = new Track();
			t.state = state;
			t.prev = state;
			t.since = now - FADE_MS;
			tracks.put(key, t);
		} else if (t.state != state) {
			// Mitten in einer Überblendung zurück: vom überwiegend sichtbaren Zustand aus weiterblenden.
			float w = weight(t, now);
			t.prev = w >= 0.5f ? t.state : t.prev;
			t.state = state;
			t.since = now;
		}
		t.seen = now;
		if (walking) t.walkSeen = now;
		if (now - lastCleanup > 10_000L) cleanup(now);
		return new Snapshot(t.state, driver(t.state), driver(t.prev), weight(t, now));
	}

	static float weight(Track t, long now) {
		long dt = now - t.since;
		if (dt >= FADE_MS || t.prev == t.state) return 1f;
		if (dt <= 0) return 0f;
		float u = dt / (float) FADE_MS;
		return u * u * (3 - 2 * u);
	}

	private void cleanup(long now) {
		lastCleanup = now;
		Iterator<Track> it = tracks.values().iterator();
		while (it.hasNext()) {
			if (now - it.next().seen > IDLE_MS) it.remove();
		}
	}

	int size() {
		return tracks.size();
	}
}
