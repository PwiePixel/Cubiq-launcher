package dev.pwiepixel.cubiqclient.core.cosmetic.v2;

/**
 * Eine v2-Kopf-Kosmetik für genau ein Bild: Modell, Texturen des aktuellen Bildes und die feste Uhrzeit, mit der alle
 * Durchgänge (Grund, Leuchten, Höfe) gerechnet werden – so liegen Grund- und Leucht-Schicht exakt aufeinander, auch
 * wenn die Version erst später zeichnet (ab 1.21.9 wird eingesammelt).
 *
 * @param <T> Textur-Kennung der Version
 */
public final class V2Hat<T> {
	/** Texturen je Entfernungs-Stufe ({@link CosmeticV2Renderer#lodLevel}); Stufe 0 = volle Auflösung. */
	public interface Lod<T> {
		T base(long now, int level);

		T glow(long now, int level);
	}

	public final CosmeticV2 model;
	public final T base;
	/** null = keine Leucht-Schicht. */
	public final T glow;
	/** null = keine Höfe. */
	public final T halo;
	/** Uhr dieses Bildes: Leucht-Frames, Puls der Höfe, Bild der Entfernungs-Stufen (Begleiter: Wanduhr + Versatz). */
	public final long now;
	/**
	 * Zeit der Pose für {@link #driver} bzw. {@link #prevDriver} – jede Seite einer Überblendung auf ihrer eigenen
	 * Zeitbasis (Begleiter: Uhr + Versatz, oder Zeit seit dem Beginn von {@code grab}/einer Aktion). Sonst beide
	 * {@link #now}.
	 */
	public final long poseMs, prevPoseMs;
	/** Träger hat einen Helm auf: Teil wird auf den Helm gesetzt (siehe {@code CosmeticV2Renderer#HELMET_SCALE}). */
	public final boolean helmet;

	/**
	 * Zustands-Animation (Flügel): Treiber des aktuellen Zustands, des vorigen und die Überblendung dazwischen (0 = nur
	 * {@link #prevDriver}, 1 = nur {@link #driver}). Hüte: immer {@code idle} (0, 0, 1).
	 */
	public final int driver;
	public final int prevDriver;
	public final float blend;

	/**
	 * Knochen, die nicht gezeichnet werden (vom Spieler abgeschaltete Optionen, {@link CosmeticV2#hiddenBones}); 0 =
	 * alles.
	 */
	public final long hiddenBones;

	/**
	 * Begleiter am Boden ({@link #forPet}): gezeichnet mit der Überblendung der Begleiter
	 * ({@code CosmeticV2Renderer#pose(CosmeticV2, long, long, boolean, boolean, int, int, float, long)} – jede Seite auf
	 * ihrer Zeit, gemischt wie bisher, Effekt-Knochen sofort im Ziel). Hüte und Flügel: false, wie bisher.
	 */
	public final boolean pet;

	private final Lod<T> lod;

	public V2Hat(CosmeticV2 model, T base, T glow, T halo, long now, boolean helmet) {
		this(model, base, glow, halo, now, helmet, null);
	}

	public V2Hat(CosmeticV2 model, T base, T glow, T halo, long now, boolean helmet, Lod<T> lod) {
		this(model, base, glow, halo, now, helmet, lod, 0, 0, 1f);
	}

	public V2Hat(CosmeticV2 model, T base, T glow, T halo, long now, boolean helmet, Lod<T> lod, int driver, int prevDriver,
			float blend) {
		this(model, base, glow, halo, now, helmet, lod, driver, prevDriver, blend, 0L);
	}

	public V2Hat(CosmeticV2 model, T base, T glow, T halo, long now, boolean helmet, Lod<T> lod, int driver, int prevDriver,
			float blend, long hiddenBones) {
		this(model, base, glow, halo, now, now, now, helmet, lod, driver, prevDriver, blend, hiddenBones, false);
	}

	private V2Hat(CosmeticV2 model, T base, T glow, T halo, long now, long poseMs, long prevPoseMs, boolean helmet, Lod<T> lod,
			int driver, int prevDriver, float blend, long hiddenBones, boolean pet) {
		this.pet = pet;
		this.poseMs = poseMs;
		this.prevPoseMs = prevPoseMs;
		this.hiddenBones = hiddenBones;
		this.driver = driver;
		this.prevDriver = prevDriver;
		this.blend = blend;
		this.lod = lod;
		this.model = model;
		this.base = base;
		this.glow = glow;
		this.halo = halo;
		this.now = now;
		this.helmet = helmet;
	}

	/**
	 * Entfernungs-Stufe für dieses Bild aus der Kamera ({@code eye} im Anhängepunkt-Raum, null = volle Auflösung),
	 * dem senkrechten Sichtfeld und der Bildhöhe in Pixeln.
	 */
	public int level(double[] eye, double fovDegrees, int screenHeight) {
		return lod == null ? 0 : CosmeticV2Renderer.lodLevel(model.scale, eye, fovDegrees, screenHeight);
	}

	/** Grundtextur in der Stufe {@code level} (0 = {@link #base}). */
	public T base(int level) {
		if (level <= 0 || lod == null) return base;
		T t = lod.base(now, level);
		return t == null ? base : t;
	}

	/** Leucht-Schicht in der Stufe {@code level} (0 = {@link #glow}); null = keine. */
	public T glow(int level) {
		if (level <= 0 || lod == null || glow == null) return glow;
		T t = lod.glow(now, level);
		return t == null ? glow : t;
	}

	/** Gleiches Teil mit einer Zustands-Animation (Flügel: idle/air/sneak, siehe {@link WingState}). */
	public V2Hat<T> withState(int driver, int prevDriver, float blend) {
		return new V2Hat<T>(model, base, glow, halo, now, poseMs, prevPoseMs, helmet, lod, driver, prevDriver, blend, hiddenBones,
				pet);
	}

	/** Gleiches Teil mit abgeschalteten Knochen ({@link CosmeticV2#hiddenBones}). */
	public V2Hat<T> withHidden(long hidden) {
		return hidden == hiddenBones ? this
				: new V2Hat<T>(model, base, glow, halo, now, poseMs, prevPoseMs, helmet, lod, driver, prevDriver, blend, hidden, pet);
	}

	/**
	 * Ein Begleiter am Boden in diesem Bild: Zustand ({@code core.cosmetic.pet.PetSim}) und eigene Zeiten –
	 * {@code clockMs} = Wanduhr + Versatz je Begleiter (damit mehrere nicht im Gleichschritt laufen; Leucht-Frames, Puls
	 * der Höfe), {@code poseMs}/{@code prevPoseMs} = Zeit der Pose für {@code driver} bzw. {@code prevDriver}: Zeit seit
	 * dem eigenen Beginn bei {@code grab} und den Aktionen, sonst ebenfalls die Uhr des Begleiters.
	 */
	public V2Hat<T> forPet(long clockMs, long poseMs, long prevPoseMs, int driver, int prevDriver, float blend) {
		// Leucht-Frames auch in voller Auflösung zur Uhr des Begleiters (die Stufen darunter liest base/glow ohnehin so).
		T b = base;
		T g = glow;
		if (lod != null) {
			T x = lod.base(clockMs, 0);
			if (x != null) b = x;
			if (glow != null) {
				x = lod.glow(clockMs, 0);
				if (x != null) g = x;
			}
		}
		return new V2Hat<T>(model, b, g, halo, clockMs, poseMs, prevPoseMs, false, lod, driver, prevDriver, blend, hiddenBones, true);
	}

	/** Gibt es in diesem Durchgang etwas zu zeichnen? */
	public boolean has(int pass) {
		switch (pass) {
			case CosmeticV2Renderer.PASS_CUTOUT:
				return model.has(CosmeticV2.CUTOUT);
			case CosmeticV2Renderer.PASS_EMISSIVE:
				return model.has(CosmeticV2.EMISSIVE);
			case CosmeticV2Renderer.PASS_TRANSLUCENT:
				return model.has(CosmeticV2.TRANSLUCENT);
			case CosmeticV2Renderer.PASS_GLOW:
				return glow != null;
			default:
				return halo != null && !model.halos.isEmpty();
		}
	}
}
