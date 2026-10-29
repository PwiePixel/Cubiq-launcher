package dev.pwiepixel.cubiqclient.core.emote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.pwiepixel.cubiqclient.core.emote.Channel.*;

/**
 * Alle Emotes der Cubiq API ({@code api/server/lib/emotes.ts}, gleiche IDs und Reihenfolge) als Animationen.
 * IDs sind dauerhaft; unbekannte IDs aus der API werden ignoriert. Winkel in Grad, Verschiebungen in Pixeln.
 */
public final class Emotes {
	private static final Map<String, EmoteDef> BY_ID = new LinkedHashMap<>();
	public static final List<EmoteDef> ALL;

	static {
		List<EmoteDef> all = new ArrayList<>();

		// Winken: rechter Arm hoch, Hand pendelt seitlich.
		all.add(EmoteDef.builder("winken", 2000, false, 0, "wave")
				.hold(R_ARM_X, -160)
				.hold(R_ARM_Y, -8)
				.key(R_ARM_Z, 0, 25, 250, 45, 500, 10, 750, 45, 1000, 10, 1250, 45, 1500, 10, 1750, 35, 2000, 25)
				.key(HEAD_Z, 0, 0, 400, -6, 1600, -6, 2000, 0)
				.key(TORSO_ROLL, 0, 0, 400, -3, 1600, -3, 2000, 0)
				.build());

		// Klatschen: beide Arme vor, Hände treffen sich in der Mitte.
		all.add(EmoteDef.builder("klatschen", 2500, false, 0, "clap")
				.hold(R_ARM_X, -70)
				.hold(L_ARM_X, -70)
				.osc(R_ARM_Y, 10, -26, 170, 170, 2210)
				.osc(L_ARM_Y, -10, 26, 170, 170, 2210)
				.osc(HEAD_X, 0, 6, 170, 170, 2210)
				.hold(TORSO_LEAN, 4)
				.build());

		// Jubeln: Arme hoch, kleine Sprünge, Blick nach oben.
		all.add(EmoteDef.builder("jubeln", 2500, false, 0, "cheer")
				.osc(R_ARM_X, -150, -172, 250, 0, 2500)
				.hold(R_ARM_Z, 22)
				.osc(L_ARM_X, -150, -172, 250, 0, 2500)
				.hold(L_ARM_Z, -22)
				.osc(ROOT_Y, 0, 1.5f, 250, 0, 2500)
				.hold(HEAD_X, -18)
				.osc(TORSO_LEAN, 0, -6, 250, 0, 2500)
				.build());

		// Verbeugen: aus der Hüfte vorbeugen, rechte Hand vor den Bauch, linke hinter den Rücken.
		all.add(EmoteDef.builder("verbeugen", 2000, false, 0, "bow")
				.key(TORSO_LEAN, 0, 0, 550, 55, 1300, 55, 1850, 0)
				.key(R_ARM_X, 0, 0, 550, -55, 1300, -55, 1850, 0)
				.key(R_ARM_Y, 0, 0, 550, -40, 1300, -40, 1850, 0)
				.key(L_ARM_X, 0, 0, 550, 35, 1300, 35, 1850, 0)
				.key(L_ARM_Y, 0, 0, 550, -30, 1300, -30, 1850, 0)
				.key(HEAD_X, 0, 0, 550, 20, 1300, 20, 1850, 0)
				.build());

		// Facepalm: rechte Hand vors Gesicht, Kopf gesenkt und schüttelnd.
		all.add(EmoteDef.builder("facepalm", 2000, false, 0, "facepalm")
				.key(R_ARM_X, 0, -30, 300, -133, 1750, -133)
				.hold(R_ARM_Y, -38)
				.key(HEAD_X, 0, 0, 300, 22, 1750, 22, 2000, 0)
				.key(HEAD_Y, 0, 0, 500, 0, 700, -10, 900, 10, 1100, -10, 1300, 10, 1500, 0)
				.hold(TORSO_LEAN, 8)
				.build());

		// Schulterzucken: Schultern hoch, Arme leicht nach außen, Kopf schief.
		all.add(EmoteDef.builder("schulterzucken", 1500, false, 0, "shrug")
				.key(SHOULDERS, 0, 0, 300, 1.5f, 1100, 1.5f, 1500, 0)
				.key(R_ARM_X, 0, 0, 300, -25, 1100, -25, 1500, 0)
				.key(R_ARM_Y, 0, 0, 300, 25, 1100, 25, 1500, 0)
				.key(R_ARM_Z, 0, 0, 300, 28, 1100, 28, 1500, 0)
				.key(L_ARM_X, 0, 0, 300, -25, 1100, -25, 1500, 0)
				.key(L_ARM_Y, 0, 0, 300, -25, 1100, -25, 1500, 0)
				.key(L_ARM_Z, 0, 0, 300, -28, 1100, -28, 1500, 0)
				.key(HEAD_Z, 0, 0, 300, 12, 1100, 12, 1500, 0)
				.key(HEAD_X, 0, 0, 300, -6, 1100, -6, 1500, 0)
				.build());

		// Daumen hoch: rechter Arm nach vorn, zweimal nicken.
		all.add(EmoteDef.builder("daumen_hoch", 1500, false, 0, "thumb")
				.key(R_ARM_X, 0, -40, 350, -85, 1500, -85)
				.hold(R_ARM_Y, -12)
				.hold(R_ARM_Z, 6)
				.key(HEAD_X, 0, 0, 450, 0, 650, 12, 850, 0, 1050, 12, 1250, 0)
				.key(TORSO_LEAN, 0, 0, 350, -4, 1500, -4)
				.build());

		// Tanzen (Schleife 1 s): Arme im Wechsel hoch, Hüfte schwingt, federn.
		all.add(EmoteDef.builder("tanzen", 6000, true, 1000, "dance")
				.key(ROOT_Y, 0, 0, 250, 1.5f, 500, 0, 750, 1.5f)
				.key(TORSO_ROLL, 0, -7, 500, 7)
				.key(TORSO_TWIST, 0, -12, 500, 12)
				.key(R_ARM_X, 0, -155, 500, -35)
				.key(R_ARM_Z, 0, 30, 500, 8)
				.key(L_ARM_X, 0, -35, 500, -155)
				.key(L_ARM_Z, 0, -8, 500, -30)
				.key(R_LEG_Z, 0, 10, 500, 0)
				.key(L_LEG_Z, 0, 0, 500, -10)
				.key(HEAD_Z, 0, 8, 500, -8)
				.key(HEAD_X, 0, 0, 250, 8, 500, 0, 750, 8)
				.build());

		// Salutieren: Hand an die Stirn, Haltung.
		all.add(EmoteDef.builder("salutieren", 2000, false, 0, "salute")
				.key(R_ARM_X, 0, -60, 250, -146, 1600, -146, 1850, -60)
				.key(R_ARM_Y, 0, 0, 250, -34, 1600, -34, 1850, 0)
				.hold(L_ARM_X, 0)
				.hold(L_ARM_Z, -2)
				.hold(R_LEG_X, 0)
				.hold(L_LEG_X, 0)
				.hold(HEAD_X, -6)
				.hold(TORSO_LEAN, -3)
				.build());

		// Luftgitarre (Schleife 0,6 s): links Griffbrett, rechts schlagen, Kopfnicken im Takt.
		all.add(EmoteDef.builder("luftgitarre", 5000, true, 600, "guitar")
				.hold(L_ARM_X, -58)
				.hold(L_ARM_Y, -28)
				.hold(L_ARM_Z, -10)
				.key(R_ARM_X, 0, -28, 150, -55, 300, -28, 450, -55)
				.hold(R_ARM_Y, -42)
				.key(HEAD_X, 0, -8, 300, 24)
				.key(TORSO_LEAN, 0, -8, 300, 6)
				.hold(R_LEG_Z, 9)
				.hold(L_LEG_Z, -9)
				.key(ROOT_Y, 0, 0, 300, -0.8f)
				.build());

		// Redstone-Tanz (Schleife 1,2 s): Roboter-Posen im Takt eines Verstärkers, zackige Übergänge.
		all.add(EmoteDef.builder("redstone_tanz", 6000, true, 1200, "redstone")
				.ease(Track.Ease.SNAP)
				.key(R_ARM_X, 0, -90, 300, 0, 600, -180, 900, -90)
				.key(R_ARM_Z, 0, 0, 300, 90, 600, 10, 900, 0)
				.key(L_ARM_X, 0, 0, 300, -90, 600, -180, 900, -90)
				.key(L_ARM_Z, 0, -90, 300, 0, 600, -10, 900, 0)
				.key(TORSO_TWIST, 0, 0, 300, 20, 600, 0, 900, -20)
				.key(HEAD_Y, 0, 0, 300, 25, 600, 0, 900, -25)
				.key(ROOT_Y, 0, 0, 300, 0, 600, 1.5f, 900, 0)
				.key(R_LEG_Z, 0, 0, 300, 12, 600, 0, 900, 0)
				.key(L_LEG_Z, 0, 0, 300, 0, 600, 0, 900, -12)
				.build());

		// Party (Schleife 0,8 s): Hüpfen, Arme abwechselnd hoch in die Luft, Kopf wippt – Belohnung für 10 Freunde.
		all.add(EmoteDef.builder("party", 6000, true, 800, "party")
				.key(ROOT_Y, 0, 0, 200, 2.2f, 400, 0, 600, 2.2f)
				.key(R_ARM_X, 0, -170, 200, -115, 400, -170, 600, -115)
				.key(R_ARM_Z, 0, 18, 400, 34)
				.key(L_ARM_X, 0, -115, 200, -170, 400, -115, 600, -170)
				.key(L_ARM_Z, 0, -34, 400, -18)
				.key(HEAD_X, 0, -10, 200, 8, 400, -10, 600, 8)
				.key(TORSO_TWIST, 0, -10, 400, 10)
				.key(R_LEG_X, 0, 0, 200, -22, 400, 0)
				.key(L_LEG_X, 0, 0, 400, 0, 600, -22)
				.build());

		// --- Halloween 2026 (Belohnungen aus „Süßes oder Saures“) ---

		// Graf-Verbeugung: rechter Arm schwingt den Umhang weit zur Seite, dann tiefe Verbeugung, linke Hand auf dem Rücken.
		all.add(EmoteDef.builder("count_bow", 2500, false, 0, "countBow")
				.key(R_ARM_X, 0, 0, 450, -100, 900, -60, 1500, -60, 2200, 0)
				.key(R_ARM_Z, 0, 0, 450, 70, 900, 25, 1500, 25, 2200, 0)
				.key(TORSO_LEAN, 0, 0, 500, -4, 1000, 62, 1700, 62, 2300, 0)
				.key(L_ARM_X, 0, 0, 900, 40, 1700, 40, 2300, 0)
				.key(L_ARM_Y, 0, 0, 900, -35, 1700, -35, 2300, 0)
				.key(HEAD_X, 0, 0, 500, -10, 1000, 22, 1700, 22, 2300, 0)
				.key(R_LEG_X, 0, 0, 900, -12, 1700, -12, 2300, 0)
				.build());

		// Spuk-Schweben (Schleife 2 s): langsam aufsteigen und auf und ab schweben, Arme nach vorn, leichtes Wiegen.
		all.add(EmoteDef.builder("ghost_float", 4000, true, 2000, "float")
				.key(ROOT_Y, 0, 2.2f, 1000, 4f)
				.hold(R_ARM_X, -88)
				.hold(L_ARM_X, -88)
				.key(R_ARM_Z, 0, 4, 1000, 9)
				.key(L_ARM_Z, 0, -4, 1000, -9)
				.key(TORSO_ROLL, 0, -3, 1000, 3)
				.hold(R_LEG_X, 8)
				.hold(L_LEG_X, 14)
				.key(HEAD_Z, 0, -5, 1000, 5)
				.build());

		// Zombie-Gang (Schleife 1 s): Arme steif nach vorn, schlurfende Beine, Kopf schief, Oberkörper schwankt.
		all.add(EmoteDef.builder("zombie_walk", 5000, true, 1000, "zombie")
				.key(R_ARM_X, 0, -92, 500, -84)
				.key(L_ARM_X, 0, -84, 500, -92)
				.hold(R_ARM_Z, 3)
				.hold(L_ARM_Z, -3)
				.key(R_LEG_X, 0, 18, 500, -14)
				.key(L_LEG_X, 0, -14, 500, 18)
				.key(TORSO_ROLL, 0, -5, 500, 5)
				.hold(TORSO_LEAN, 6)
				.hold(HEAD_X, 12)
				.key(HEAD_Z, 0, 10, 500, 4)
				.key(ROOT_Y, 0, 0, 250, -0.6f, 500, 0, 750, -0.6f)
				.build());

		// Mondheulen: Kopf in den Nacken, Arme leicht nach hinten, heulen (Kopf zittert), dann zurück.
		all.add(EmoteDef.builder("moon_howl", 3000, false, 0, "howl")
				.key(HEAD_X, 0, 0, 600, -48, 900, -44, 1200, -50, 1500, -44, 1800, -50, 2100, -46, 2500, -46, 2900, 0)
				.key(TORSO_LEAN, 0, 0, 600, -12, 2500, -12, 2900, 0)
				.key(R_ARM_X, 0, 0, 600, 20, 2500, 20, 2900, 0)
				.key(L_ARM_X, 0, 0, 600, 20, 2500, 20, 2900, 0)
				.key(R_ARM_Z, 0, 0, 600, 14, 2500, 14, 2900, 0)
				.key(L_ARM_Z, 0, 0, 600, -14, 2500, -14, 2900, 0)
				.key(ROOT_Y, 0, 0, 600, 0.6f, 2500, 0.6f, 2900, 0)
				.build());

		// --- Winter 2026 (für alle frei) ---

		// Schneeballwurf: tief bücken und Schnee zusammenschieben, aufrichten und vor dem Bauch formen (zweimal klopfen,
		// Blick auf den Ball), ausholen (Brust nach rechts, nach hinten lehnen, Hand hinter den Kopf, linker Arm zeigt aufs
		// Ziel), Schritt vor und werfen, nachschwingen.
		all.add(EmoteDef.builder("snowball_throw", 2400, false, 0, "snowball")
				.key(TORSO_LEAN, 0, 0, 450, 70, 700, 70, 1050, 4, 1400, -12, 1600, 16, 1900, 10, 2300, 0)
				.key(TORSO_TWIST, 0, 0, 1050, 0, 1400, 25, 1600, -18, 1900, -14, 2300, 0)
				.key(ROOT_Y, 0, 0, 450, -0.8f, 700, -0.8f, 1050, 0)
				.key(HEAD_X, 0, 0, 450, 6, 700, 6, 1050, 28, 1250, 24, 1400, 6, 1600, -8, 1900, -6, 2300, 0)
				.key(HEAD_Y, 0, 0, 1050, 0, 1400, -25, 1600, 18, 1900, 14, 2300, 0)
				.key(R_ARM_X, 0, 0, 450, -84, 600, -72, 700, -84, 1050, -58, 1200, -66, 1400, -175, 1600, -60, 1900, -40, 2300, 0)
				.key(R_ARM_Y, 0, 0, 450, -14, 700, -24, 1050, -36, 1130, -26, 1210, -36, 1400, 0, 1600, -14, 1900, -10, 2300, 0)
				.key(R_ARM_Z, 0, 0, 1050, 0, 1400, -14, 1600, 6, 2300, 0)
				.key(L_ARM_X, 0, 0, 450, -84, 600, -72, 700, -84, 1050, -58, 1200, -66, 1400, -80, 1600, 22, 1900, 14, 2300, 0)
				.key(L_ARM_Y, 0, 0, 450, 14, 700, 24, 1050, 36, 1130, 26, 1210, 36, 1400, -22, 1600, 0, 2300, 0)
				.key(L_ARM_Z, 0, 0, 1400, -6, 1600, -14, 2300, 0)
				.key(L_LEG_X, 0, 0, 1050, 0, 1400, -14, 1600, -20, 1900, -16, 2300, 0)
				.key(R_LEG_X, 0, 0, 1050, 0, 1400, 6, 1600, 10, 1900, 8, 2300, 0)
				.build());

		// Bibbern (Schleife 1,2 s): Arme vor der Brust verschränkt, Schultern hoch, Kinn runter; schnelles Zittern
		// (60 ms je Richtung), Zähneklappern; einmal je Durchgang ein „Brrr“ (Kopf schüttelt, Schultern zucken).
		all.add(EmoteDef.builder("shiver", 4800, true, 1200, "snowflake")
				.hold(R_ARM_X, -62)
				.hold(R_ARM_Y, -48)
				.hold(L_ARM_X, -68)
				.hold(L_ARM_Y, 48)
				.hold(TORSO_LEAN, 8)
				.key(SHOULDERS, 0, 1.2f, 600, 1.2f, 750, 2f, 950, 2f, 1100, 1.2f)
				.osc(TORSO_ROLL, -1.6f, 1.6f, 60, 0, 1140)
				.osc(ROOT_X, -0.25f, 0.25f, 60, 0, 1140)
				.osc(HEAD_X, 12, 15, 60, 0, 1140)
				.key(HEAD_Y, 0, 0, 600, 0, 680, -12, 760, 12, 840, -10, 920, 10, 1000, 0)
				.build());

		// Hände wärmen (Schleife 1,25 s): Hände vor der Brust aneinander reiben (Arme im Gegentakt), dann vor den Mund
		// heben und hineinpusten (Kopf leicht vor, Schultern hoch), zurück.
		all.add(EmoteDef.builder("warm_hands", 5000, true, 1250, "warmHands")
				.key(R_ARM_X, 0, -76, 120, -88, 240, -76, 360, -88, 480, -76, 600, -88, 720, -82, 850, -120, 1100, -120)
				.key(L_ARM_X, 0, -88, 120, -76, 240, -88, 360, -76, 480, -88, 600, -76, 720, -82, 850, -120, 1100, -120)
				.key(R_ARM_Y, 0, -30, 720, -30, 850, -36, 1100, -36)
				.key(L_ARM_Y, 0, 30, 720, 30, 850, 36, 1100, 36)
				.key(HEAD_X, 0, 14, 650, 14, 850, 4, 1100, 4)
				.key(SHOULDERS, 0, 0.8f, 650, 0.8f, 850, 1.4f, 1100, 1.4f)
				.hold(TORSO_LEAN, 6)
				.build());

		// Freudensprung: in die Hocke (Arme zurück), zwei Sprünge mit Armen hoch (V) und Fersen nach hinten, Landung
		// federt, zum Schluss V-Pose, zurück. Gehobene Arme: R_ARM_Z negativ = nach außen.
		all.add(EmoteDef.builder("happy_jump", 2000, false, 0, "jump")
				.key(ROOT_Y, 0, 0, 220, -0.8f, 480, 3.2f, 720, -0.6f, 980, 3.2f, 1220, -0.6f, 1550, 0)
				.key(TORSO_LEAN, 0, 0, 220, 14, 480, -8, 720, 12, 980, -8, 1220, 10, 1550, -4, 1900, 0)
				.key(R_ARM_X, 0, 0, 220, 35, 480, -165, 720, -40, 980, -165, 1220, -40, 1550, -150, 1900, 0)
				.key(R_ARM_Z, 0, 0, 220, 6, 480, -24, 720, 18, 980, -24, 1220, 18, 1550, -30, 1900, 0)
				.key(L_ARM_X, 0, 0, 220, 35, 480, -165, 720, -40, 980, -165, 1220, -40, 1550, -150, 1900, 0)
				.key(L_ARM_Z, 0, 0, 220, -6, 480, 24, 720, -18, 980, 24, 1220, -18, 1550, 30, 1900, 0)
				.key(HEAD_X, 0, 0, 220, 6, 480, -16, 720, 4, 980, -16, 1220, 4, 1550, -10, 1900, 0)
				.key(R_LEG_X, 0, 0, 220, 0, 480, 22, 720, 0, 980, 22, 1220, 0)
				.key(L_LEG_X, 0, 0, 220, 0, 480, 22, 720, 0, 980, 22, 1220, 0)
				.build());

		// Fäustlings-Winken: beide Arme hoch, Hände winken wie Scheibenwischer (beide zugleich nach rechts, dann nach
		// links), federn auf den Zehen, Kopf wiegt mit. Fäustlinge selbst kann ein Emote nicht zeigen (nur Posen).
		all.add(EmoteDef.builder("mitten_wave", 2500, false, 0, "mitten")
				.key(R_ARM_X, 0, -30, 300, -160, 2200, -160, 2500, -30)
				.key(L_ARM_X, 0, -30, 300, -160, 2200, -160, 2500, -30)
				.key(R_ARM_Z, 0, 12, 300, -10, 550, -30, 800, 2, 1050, -30, 1300, 2, 1550, -30, 1800, 2, 2050, -10, 2500, 12)
				.key(L_ARM_Z, 0, -12, 300, 10, 550, -2, 800, 30, 1050, -2, 1300, 30, 1550, -2, 1800, 30, 2050, 10, 2500, -12)
				.key(ROOT_Y, 0, 0, 300, 0, 550, 0.8f, 800, 0, 1050, 0.8f, 1300, 0, 1550, 0.8f, 1800, 0)
				.key(HEAD_Z, 0, 0, 550, -8, 800, 8, 1050, -8, 1300, 8, 1550, -8, 1800, 8, 2050, 0)
				.hold(HEAD_X, -8)
				.build());

		for (EmoteDef d : all) BY_ID.put(d.id(), d);
		ALL = Collections.unmodifiableList(all);
	}

	private Emotes() {
	}

	/** Emote zu einer API-ID oder null (unbekannt → ignorieren). */
	public static EmoteDef byId(String id) {
		return id == null ? null : BY_ID.get(id);
	}
}
