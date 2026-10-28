package dev.pwiepixel.cubiqclient.core.online;

import dev.pwiepixel.cubiqclient.core.cape.CapePhysics;
import dev.pwiepixel.cubiqclient.core.cape.CapeSettings;
import dev.pwiepixel.cubiqclient.core.cape.CapeTextures;
import dev.pwiepixel.cubiqclient.core.cape.ClothMesh;
import dev.pwiepixel.cubiqclient.core.cape.ClothSim;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.skin.LocalSkin;
import dev.pwiepixel.cubiqclient.core.skin.PlayerLook;
import dev.pwiepixel.cubiqclient.core.ui.TextureRef;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Alles rund um Cubiq-Abzeichen, Cubiq-Umhänge und Umhang-Physik an einer Stelle – versionsunabhängig.
 * Jede Minecraft-Version liefert nur: Konto/Server ({@link OnlinePlatform}), das Hochladen von Texturen
 * ({@link CapeTextures.Backend}), einmal pro Tick die sichtbaren Spieler, und die Hooks zum Zeichnen.
 *
 * @param <T> Textur-Kennung der Version (z. B. ResourceLocation)
 */
public final class OnlineFeatures<T> {
	private final CubiqModules modules;
	private final CubiqOnline online;
	private final CapeTextures<T> textures;
	private final CapePhysics physics = new CapePhysics();
	private final ClothMesh mesh = new ClothMesh();
	/** Kopf-Kosmetik (Quietscheente): Rig-Zustände je Träger und Mesh-Baukasten (nur Render-Thread). */
	private final dev.pwiepixel.cubiqclient.core.cosmetic.DuckRig hatRig = new dev.pwiepixel.cubiqclient.core.cosmetic.DuckRig();
	private final dev.pwiepixel.cubiqclient.core.cosmetic.CosmeticMesh hatMesh = new dev.pwiepixel.cubiqclient.core.cosmetic.CosmeticMesh();
	/** Kopf-Kosmetik im Format 2: Texturen je Teil und der Renderer (nur Render-Thread). */
	private final dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticAssets<T> cosmetics;
	private final dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer v2Renderer =
			new dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer();
	private final dev.pwiepixel.cubiqclient.core.emote.EmoteController emotes;
	/** Fan-Symbole vor Namen (Creator-Programm, nur Render-Thread). */
	private final dev.pwiepixel.cubiqclient.core.creator.FanSymbols<T> fanSymbols;
	/** Profil-Abzeichen vor dem Namen (Halloween 2026). */
	private final dev.pwiepixel.cubiqclient.core.creator.EventBadges<T> eventBadges;
	private volatile Thread gameThread;
	/** Wiederverwendet: Umhang-Einstellungen je Tick (keine Allokation). */
	private final CapeSettings capeSettings = new CapeSettings();

	/** Eigener Skin für Menüs (null in Tests ohne Konfig-Ordner). */
	private final LocalSkin localSkin;
	/** Zuletzt gelieferter Cubiq-Umhang als {@link TextureRef} (keine Allokation je Bild). */
	private TextureRef lastCapeRef;

	public OnlineFeatures(CubiqModules modules, CubiqOnline online, CapeTextures.Backend<T> backend) {
		this(modules, online, backend, null);
	}

	public OnlineFeatures(CubiqModules modules, CubiqOnline online, CapeTextures.Backend<T> backend, LocalSkin localSkin) {
		this.modules = modules;
		this.online = online;
		this.textures = new CapeTextures<>(backend, online::loadCape);
		this.cosmetics = new dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticAssets<>(backend, online::loadCosmetic);
		this.emotes = new dev.pwiepixel.cubiqclient.core.emote.EmoteController(modules, online);
		this.fanSymbols = new dev.pwiepixel.cubiqclient.core.creator.FanSymbols<>(backend, online::loadSymbol);
		this.eventBadges = new dev.pwiepixel.cubiqclient.core.creator.EventBadges<>(backend, online::loadBadge);
		this.localSkin = localSkin;
	}

	/** Standard-Aufbau: Konfiguration aus {@code configDir}, HTTP über HttpURLConnection. */
	public static <T> OnlineFeatures<T> create(CubiqModules modules, Path configDir, OnlinePlatform platform,
			String modVersion, CapeTextures.Backend<T> backend) {
		String userAgent = "Cubiq-Client/" + modVersion + " (Minecraft " + platform.minecraftVersion() + "; " + platform.loader() + ")";
		java.util.concurrent.ThreadPoolExecutor skinWorker = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30,
				java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(4), r -> {
					Thread t = new Thread(r, "Cubiq-Skin");
					t.setDaemon(true);
					t.setPriority(Thread.MIN_PRIORITY + 1);
					return t;
				});
		skinWorker.allowCoreThreadTimeOut(true);
		LocalSkin skin = new LocalSkin(configDir.resolve("cubiqclient").resolve("skins"), platform::session,
				new Http.UrlConnection(userAgent), skinWorker, platform::log);
		CubiqOnline online = CubiqOnline.create(configDir, platform, modVersion);
		OnlineFeatures<T> features = new OnlineFeatures<>(modules, online, backend, skin);
		// Client-Sync (Einstellungen je Cubiq-Konto, Launcher-Aussehen live) – läuft über die Anmeldung von CubiqOnline.
		features.sync = dev.pwiepixel.cubiqclient.core.sync.ClientSync.create(modules, online, configDir, userAgent, modVersion,
				platform::log);
		// Notizen je Welt: eigener Sync (Notiz für Notiz), läuft über dieselbe Cubiq-Anmeldung.
		features.notesSync = dev.pwiepixel.cubiqclient.core.notes.NotesSync.create(modules, online, configDir, userAgent, platform::log);
		return features;
	}

	/** Client-Sync (null in Tests ohne Konfig-Ordner). */
	private dev.pwiepixel.cubiqclient.core.sync.ClientSync sync;

	/** Notiz-Sync (null in Tests ohne Konfig-Ordner). */
	private dev.pwiepixel.cubiqclient.core.notes.NotesSync notesSync;

	public dev.pwiepixel.cubiqclient.core.sync.ClientSync sync() {
		return sync;
	}

	/**
	 * Aussehen des eigenen Spielers für Menüs (Startbildschirm, Garderobe): Name, Skin (eigener oder Standard),
	 * Umhang – der Cubiq-Umhang (aktuelles Bild) hat Vorrang vor dem Mojang-Umhang. Nur Render-Thread; null ohne Skin-Quelle.
	 */
	public PlayerLook look() {
		if (localSkin == null) return null;
		long now = System.currentTimeMillis();
		PlayerLook look;
		try {
			look = localSkin.look(now);
		} catch (RuntimeException e) {
			online.reportError(e);
			return null;
		}
		UUID self = localSkin.uuid();
		TextureRef cubiq = self == null ? null : capeRef(self);
		if (cubiq != null) {
			look.cape = cubiq;
			look.cubiqCape = true;
		}
		return look;
	}

	/** Eigener Skin für Menüs (null in Tests). */
	public LocalSkin localSkin() {
		return localSkin;
	}

	/** Cubiq-Umhang eines Spielers (aktuelles Animationsbild) als zeichenbare Textur, sonst null. */
	public TextureRef capeRef(UUID uuid) {
		T tex = capeTexture(uuid);
		if (tex == null) return null;
		CapeInfo info = online.info(uuid).cape;
		int w = textures.width(info);
		int h = textures.height(info);
		if (w <= 0 || h <= 0) return null;
		TextureRef last = lastCapeRef;
		if (last != null && last.id.equals(tex) && last.width == w && last.height == h) return last;
		lastCapeRef = new TextureRef(tex, w, h);
		return lastCapeRef;
	}

	/**
	 * Einmal pro Client-Tick (Spiel-Thread): API-Verkehr anstoßen, Texturen aufräumen, Umhänge simulieren.
	 *
	 * @param visible UUIDs der Spieler in Tabliste und Sichtweite ({@code null} = unverändert, siehe {@link CubiqOnline#tick})
	 * @param samples Zustand der Spieler für die Physik (darf leer sein)
	 */
	public void tick(Collection<UUID> visible, List<CapePhysics.Sample> samples) {
		gameThread = Thread.currentThread();
		long now = System.currentTimeMillis();
		// Nichts hiervon darf je das Spiel abstürzen lassen.
		try {
			online.wantBadges(modules.badgeTab.get() || modules.badgeNametag.get() || modules.fanSymbolTab.get()
					|| modules.fanSymbolNametag.get());
			online.tick(now, visible, modules.cubiqOnline.isEnabled());
			textures.cleanup(now);
			fanSymbols.cleanup(now);
			cosmetics.cleanup(now);
			if (sync != null) sync.tick(now);
			if (notesSync != null) notesSync.tick(now);
		} catch (RuntimeException e) {
			online.reportError(e);
		}
		try {
			physics.tick(samples, modules.capePhysics.isEnabled(),
					modules.capeScope.get() == CubiqModules.CapeScope.OWN, modules.capeSettings(capeSettings));
		} catch (RuntimeException e) {
			physics.clear();
			online.reportError(e);
		}
	}

	/**
	 * Textur des Cubiq-Umhangs eines Spielers (aktuelles Animationsbild) oder null (dann Vanilla/OptiFine).
	 * Nur im Spiel-/Render-Thread wirksam – aus anderen Threads immer null.
	 */
	public T capeTexture(UUID uuid) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled() || !modules.cubiqCapes.get()) return null;
		PlayerInfo info = online.info(uuid);
		if (info.cape == null) return null;
		try {
			return textures.texture(info.cape, System.currentTimeMillis(), isOwn(uuid));
		} catch (RuntimeException e) {
			online.reportError(e);
			return null;
		}
	}

	/** Zuletzt umgerechnete eigene UUID (32 Hex aus {@link CubiqOnline#ownUuid()}) – keine Allokation je Bild. */
	private String ownHex;
	private UUID ownParsed;

	/** Ist das der eigene Spieler (angemeldetes Konto)? Dessen Umhang bleibt beim Textur-Budget in voller Qualität. */
	private boolean isOwn(UUID uuid) {
		String hex = online.ownUuid();
		if (hex == null || uuid == null) return false;
		if (!hex.equals(ownHex)) {
			ownHex = hex;
			ownParsed = null;
			String h = hex.replace("-", "");
			if (h.length() == 32) {
				try {
					ownParsed = new UUID(Long.parseUnsignedLong(h.substring(0, 16), 16), Long.parseUnsignedLong(h.substring(16), 16));
				} catch (NumberFormatException ignored) {
					// keine gültige UUID → nie „eigener“ Umhang
				}
			}
		}
		return uuid.equals(ownParsed);
	}

	/**
	 * Textur der Kopf-Kosmetik eines Spielers oder null (nichts tragen, Modul/Einstellung aus, noch nicht geladen,
	 * Vorlage unbekannt). Nur im Spiel-/Render-Thread wirksam.
	 */
	public T hatTexture(UUID uuid) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled() || !modules.cubiqCosmetics.get()) return null;
		PlayerInfo info = online.info(uuid);
		if (info.hat == null || info.hat.v2() || dev.pwiepixel.cubiqclient.core.cosmetic.CosmeticModels.get(info.hat) == null) return null;
		try {
			return textures.texture(info.hat.texture, System.currentTimeMillis());
		} catch (RuntimeException e) {
			online.reportError(e);
			return null;
		}
	}

	/**
	 * Zeichnet die Kopf-Kosmetik (nach {@link #hatTexture}) in den Kopf-Raum des Modells; Tier-Vorlagen bewegen
	 * sich nach {@code wearer}. Fehler landen im Fehlerbericht, nie im Spiel.
	 */
	public void emitHat(UUID uuid, dev.pwiepixel.cubiqclient.core.cosmetic.Wearer wearer, ClothMesh.QuadSink sink) {
		PlayerInfo info = online.info(uuid);
		if (info.hat == null) return;
		dev.pwiepixel.cubiqclient.core.cosmetic.CosmeticModel model =
				dev.pwiepixel.cubiqclient.core.cosmetic.CosmeticModels.get(info.hat);
		if (model == null) return;
		try {
			wearer.emote = emotes.animating(uuid);
			dev.pwiepixel.cubiqclient.core.cosmetic.DuckRig.Pose pose = model.rig != null
					? hatRig.update(wearer, System.nanoTime()) : null;
			hatMesh.emit(model, pose, System.currentTimeMillis(), wearer.helmet, sink);
		} catch (RuntimeException e) {
			online.reportError(e);
		}
	}

	/**
	 * Kopf-Kosmetik im Format 2 (Studio-Modell) für dieses Bild oder null (nichts/v1 tragen, Modul aus, noch nicht
	 * geladen). Kopf-Kosmetik bleibt immer sichtbar: Mit Helm ({@code helmet}) wird sie auf den Helm gesetzt – waagerecht
	 * gestreckt und angehoben ({@code CosmeticV2Renderer#HELMET_SCALE}), wie die Ente (v1) sich mit Helm anhebt.
	 * Nur im Spiel-/Render-Thread wirksam.
	 */
	public dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hatV2(UUID uuid, boolean helmet) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled() || !modules.cubiqCosmetics.get()) return null;
		return v2(online.info(uuid).hat, helmet);
	}

	/**
	 * Wie {@link #hatV2(UUID, boolean)} mit Zustands-Animation ({@code state} = {@code WingState} IDLE/AIR/SNEAK aus der
	 * Bewegung, überblendet 250 ms) – z. B. die Weihnachtsmütze im Sprung. Modelle ohne Animation des Zustands spielen
	 * {@code idle} (Format §7).
	 */
	public dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hatV2(UUID uuid, boolean helmet, int state) {
		return withState(uuid, hatV2(uuid, helmet), state);
	}

	/** Wie {@link #companionV2(UUID, boolean)} mit Zustands-Animation (siehe {@link #hatV2(UUID, boolean, int)}). */
	public dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> companionV2(UUID uuid, boolean helmet, int state) {
		return withState(uuid, companionV2(uuid, helmet), state);
	}

	private dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> withState(UUID uuid,
			dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hat, int state) {
		if (hat == null || !hat.model.hasDriver(dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2.DRIVER_AIR)
				&& !hat.model.hasDriver(dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2.DRIVER_SNEAK)) {
			return hat; // nur idle: keine Zustände nötig
		}
		dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState.Snapshot s = headStates.update(uuid, state, hat.now);
		return hat.withState(s.driver, s.prevDriver, s.blend);
	}

	/**
	 * Begleiter (Slot {@code companion}, Format 2) neben dem Hut. Gleiche Regeln wie {@link #hatV2}: nur Spiel-Thread,
	 * Module an, mit Helm auf den Helm gesetzt. null = nichts.
	 */
	public dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> companionV2(UUID uuid, boolean helmet) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled() || !modules.cubiqCosmetics.get()) return null;
		return v2(online.info(uuid).companion, helmet);
	}

	/** Zustand der Flügel je Spieler (idle/air/sneak mit Überblendung). */
	private final dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState wingStates = new dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState();
	/** Gleicher Zustand für Kopf-Kosmetik und Begleiter am Kopf (eigene Überblendung, unabhängig von den Flügeln). */
	private final dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState headStates = new dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState();

	/**
	 * Flügel (Slot {@code wings}, Format 2, {@code attach: "body"}) für dieses Bild oder null: nichts getragen, Module
	 * aus, noch nicht geladen, oder der Spieler trägt eine Elytra ({@code elytra}). {@code state} = {@code WingState}
	 * IDLE/AIR/SNEAK aus der Bewegung (überblendet 250 ms). {@code self} = eigener Spieler (für die Selbsttest-Flügel
	 * {@code -Dcubiqclient.devWings}). Nur im Spiel-/Render-Thread wirksam.
	 */
	public dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> wingsV2(UUID uuid, boolean self, boolean elytra, int state) {
		if (Thread.currentThread() != gameThread || elytra) return null;
		if (!modules.cubiqOnline.isEnabled() || !modules.cubiqCosmetics.get()) return null;
		HatInfo piece = online.info(uuid).wings;
		if (piece == null && self) piece = dev.pwiepixel.cubiqclient.core.cosmetic.v2.DevWings.info(online);
		dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hat = v2(piece, false);
		if (hat == null) return null;
		dev.pwiepixel.cubiqclient.core.cosmetic.v2.WingState.Snapshot s = wingStates.update(uuid, state, hat.now);
		return hat.withState(s.driver, s.prevDriver, s.blend);
	}

	private dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> v2(HatInfo piece, boolean helmet) {
		if (piece == null || !piece.v2()) return null;
		try {
			java.util.function.LongSupplier clock = v2Clock;
			long wall = System.currentTimeMillis();
			long now = clock != null ? clock.getAsLong() : wall;
			dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticAssets.Entry<T> e = cosmetics.get(piece, wall);
			if (e == null) return null;
			T halo = e.model.halos.isEmpty() ? null : cosmetics.halo();
			// Abgeschaltete Optionen (z. B. Bart aus): Knochen samt Kindern nicht zeichnen.
			long hidden = e.model.options.isEmpty() ? 0L : e.model.hiddenBones(piece.options);
			return new dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T>(e.model, e.base(now), e.glow(now), halo, now, helmet, e,
					0, 0, 1f, hidden);
		} catch (RuntimeException ex) {
			online.reportError(ex);
			return null;
		}
	}

	// =====================================================================================================
	// Begleiter am Boden (Slot pet, Winter 2026)
	// =====================================================================================================

	/** Höchstens so viele Begleiter am Boden werden je Bild gezeichnet (alle Spieler zusammen). */
	public static final int MAX_PETS_DRAWN = 48;
	/** Weiter weg (Blöcke) werden Begleiter nicht gezeichnet. */
	public static final double PET_DRAW_DISTANCE = 64.0;

	private final dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSim pets = new dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSim();
	private final dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet[] petBuffer =
			new dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet[MAX_PETS_DRAWN];
	@SuppressWarnings("unchecked")
	private final dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetDraw<T>[] petDraws =
			new dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetDraw[MAX_PETS_DRAWN];
	private boolean petsActive;

	/** Simulation der Begleiter (Selbsttests lesen Zustände). */
	public dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSim pets() {
		return pets;
	}

	/**
	 * Beginn des Begleiter-Ticks (einmal je Client-Tick, Spiel-Thread). {@code worldKey} = aktuelle Welt (ein Wechsel
	 * leert alles). false = Module aus, dann {@link #petOwner} nicht aufrufen.
	 */
	public boolean petsBegin(Object worldKey) {
		gameThread = Thread.currentThread();
		boolean on = worldKey != null && modules.cubiqOnline.isEnabled() && modules.cubiqCosmetics.get();
		if (!on) {
			if (petsActive) pets.clear();
			petsActive = false;
			return false;
		}
		petsActive = true;
		pets.begin(worldKey, System.currentTimeMillis());
		return true;
	}

	/**
	 * Ein Spieler in der Welt (nach {@link #petsBegin}): hat er Begleiter am Boden ({@code cosmetics.pet}, Anzahl
	 * {@code count}), laufen sie ihm nach. {@code hidden} = nicht zeichnen (unsichtbar, Zuschauer). Fehler landen im
	 * Fehlerbericht, nie im Spiel.
	 */
	public void petOwner(UUID uuid, boolean self, double x, double y, double z, float bodyYaw, boolean hidden,
			dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetWorld world) {
		petOwner(uuid, self, x, y, z, bodyYaw, false, hidden, world);
	}

	/**
	 * Wie {@link #petOwner(UUID, boolean, double, double, double, float, boolean, dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetWorld)}
	 * plus {@code sneaking} (Schleichen-Taste gedrückt, nicht im Fahrzeug) – Überfall-Begleiter schleichen dann mit.
	 */
	public void petOwner(UUID uuid, boolean self, double x, double y, double z, float bodyYaw, boolean sneaking, boolean hidden,
			dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetWorld world) {
		if (!petsActive || uuid == null) return;
		if (self) selfPetOwner = uuid;
		try {
			HatInfo piece = online.info(uuid).pet;
			if (piece == null && self) piece = dev.pwiepixel.cubiqclient.core.cosmetic.pet.DevPets.info(online);
			int count = piece == null || !piece.v2() ? 0 : piece.count;
			int forced = dev.pwiepixel.cubiqclient.core.cosmetic.pet.DevPets.forcedCount;
			if (count > 0 && self && forced > 0) count = forced;
			double[] extras = null;
			int[] abilities = null;
			int[] actionMs = null;
			float[][] boxes = null;
			if (count > 0) {
				int n = piece.extras.size();
				if (n > 0) {
					extras = petScales(n);
					for (int i = 0; i < n; i++) extras[i] = piece.extras.get(i).petScale;
				}
				// Was die Modelle können (Anhang E: sneak/grab/flee/handsup, dazu pet/eat) – erst, wenn sie geladen sind
				abilities = petAbilities[n];
				if (abilities == null) petAbilities[n] = abilities = new int[n + 1];
				// Nur die eigenen lassen sich streicheln/füttern: Länge der Aktionen und Umriss für den Klick-Test – nur für
				// Teile, die petFrame auch zeichnet (geladen, Begleiter am Boden); ohne Umriss nicht anklickbar.
				if (self) {
					actionMs = petActionMs[n];
					if (actionMs == null) petActionMs[n] = actionMs = new int[2 * (n + 1)];
					boxes = petBoxes[n];
					if (boxes == null) petBoxes[n] = boxes = new float[n + 1][];
				}
				for (int k = 0; k <= n; k++) {
					dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> h = v2(k == 0 ? piece : piece.extras.get(k - 1), false);
					dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2 model = h == null ? null : h.model;
					abilities[k] = dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.abilitiesOf(model);
					if (actionMs != null) {
						actionMs[2 * k] = dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.actionMs(model,
								dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2.DRIVER_PET);
						actionMs[2 * k + 1] = dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.actionMs(model,
								dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2.DRIVER_EAT);
						boxes[k] = model == null || !model.pet() ? null
								: dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetPick.bounds(model);
					}
				}
			}
			pets.owner(uuid, count, piece == null ? 1.0 : piece.petScale, extras, abilities, actionMs, boxes, x, y, z, bodyYaw,
					sneaking, hidden, self, world);
		} catch (RuntimeException e) {
			online.reportError(e);
		}
	}

	/** Wiederverwendete Größen-Felder für das Gefolge (je Länge eins, nur Spiel-Thread). */
	private final double[][] petScaleBuffers = new double[HatInfo.MAX_EXTRAS + 1][];

	/** Wiederverwendete Fähigkeits-Felder je Gefolge-Länge + 1 (nur Spiel-Thread). */
	private final int[][] petAbilities = new int[HatInfo.MAX_EXTRAS + 1][];
	/** Wiederverwendet: Länge der Aktionen pet/eat je Teil ({@code [kind·2]}, {@code [kind·2+1]}) und Umriss je Teil. */
	private final int[][] petActionMs = new int[HatInfo.MAX_EXTRAS + 1][];
	private final float[][][] petBoxes = new float[HatInfo.MAX_EXTRAS + 1][][];

	private double[] petScales(int n) {
		double[] b = petScaleBuffers[n];
		if (b == null) petScaleBuffers[n] = b = new double[n];
		return b;
	}

	/** Teil eines Begleiters ({@link dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet#kind}: 0 = Haupt-Teil, sonst Extra). */
	private static HatInfo petPiece(HatInfo main, int kind) {
		if (main == null || kind <= 0) return main;
		return kind - 1 < main.extras.size() ? main.extras.get(kind - 1) : null;
	}

	/** Ende des Begleiter-Ticks. */
	public void petsEnd() {
		if (petsActive) pets.end();
	}

	/**
	 * Rechtsklick des eigenen Spielers (Spiel-Thread, bevor Minecraft etwas benutzt): Auge und Blickrichtung in Welt-
	 * Koordinaten, {@code vanillaDist} = Entfernung bis zu Minecrafts eigenem Treffer (Block/Wesen; unendlich ohne
	 * Treffer), {@code softBlock} = dieser Treffer ist ein Block ohne Kollisionsform (kurzes Gras, Blumen, Getreide,
	 * Schneeschicht, Hebel …) in der Zelle ({@code blockX, blockY, blockZ}) – steht der Begleiter darin, zählt der Treffer
	 * nicht –, was in Haupt- und Zweithand liegt ({@code PetInteract.HELD_*}), Schleichen, Zuschauer-Modus, offener
	 * Bildschirm. Trifft der Blick zuerst einen eigenen Begleiter am Boden, wird er gestreichelt bzw. gefüttert
	 * ({@code PetInteract}) und das Ergebnis ist true: Minecraft darf den Klick nicht benutzen – auch wenn der Begleiter
	 * gerade nicht kann (Pause, Aktion läuft), sonst würde das Essen in der Hand gegessen. Nichts geht an den Server,
	 * nichts wird verbraucht. false = Minecraft wie immer.
	 */
	public boolean petUse(double ex, double ey, double ez, double dx, double dy, double dz, double vanillaDist, boolean softBlock,
			int blockX, int blockY, int blockZ, int mainHeld, int offHeld, boolean sneaking, boolean spectator, boolean screenOpen) {
		if (!petsActive || Thread.currentThread() != gameThread || !modules.petInteract.get()) return false;
		try {
			return dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetInteract.use(pets, ex, ey, ez, dx, dy, dz, vanillaDist, softBlock,
					blockX, blockY, blockZ, mainHeld, offHeld, sneaking, spectator, screenOpen, pets.alpha(System.nanoTime()),
					System.currentTimeMillis());
		} catch (RuntimeException e) {
			online.reportError(e);
			return false;
		}
	}

	/**
	 * Effekte dieses Begleiter-Ticks (nach {@link #petsEnd}): Anzahl, {@link #petFx(int)} liefert sie – Herzchen über
	 * Begleitern, die gestreichelt/gefüttert werden, aber keine eigene Aktion im Modell haben (die Loader zeigen sie als
	 * Partikel). Nur Spiel-Thread.
	 */
	public int petFxCount() {
		return petsActive ? pets.fxCount() : 0;
	}

	/** Effekt {@code i} dieses Ticks (wiederverwendet, nur bis zum nächsten Tick gültig). */
	public dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSim.Fx petFx(int i) {
		return pets.fx(i);
	}

	/**
	 * Begleiter für dieses Bild (Render-Thread): sichtbare, geladene Teile in der Nähe der Kamera, höchstens
	 * {@link #MAX_PETS_DRAWN}, eigene zuerst. Danach {@link #petDraw}{@code (0 … n−1)}. Kamera = Position und Blickwinkel
	 * (Gieren/Nicken wie Minecraft).
	 */
	public int petFrame(dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetWorld world, double camX, double camY, double camZ,
			float camYaw, float camPitch) {
		if (!petsActive || Thread.currentThread() != gameThread) return 0;
		try {
			double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch);
			double fx = -Math.sin(yr) * Math.cos(pr), fy = -Math.sin(pr), fz = Math.cos(yr) * Math.cos(pr);
			float alpha = pets.alpha(System.nanoTime());
			// eigene Kamera für „Hände hoch“ (Anhang E)
			pets.viewer(camX, camY, camZ, fx, fy, fz);
			int n = pets.visible(camX, camY, camZ, fx, fy, fz, PET_DRAW_DISTANCE, MAX_PETS_DRAWN, alpha, petBuffer);
			java.util.function.LongSupplier clock = v2Clock;
			long wall = System.currentTimeMillis();
			long base = clock != null ? clock.getAsLong() : wall;
			int out = 0;
			UUID lastOwner = null;
			HatInfo lastPiece = null;
			// Teile des zuletzt gesehenen Besitzers je kind (Haupt-Teil + Extras), erst bei Bedarf geladen
			java.util.Arrays.fill(petHats, null);
			java.util.Arrays.fill(petHatsTried, false);
			for (int i = 0; i < n; i++) {
				dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet p = petBuffer[i];
				if (!p.owner.equals(lastOwner)) {
					HatInfo piece = online.info(p.owner).pet;
					if (piece == null && p.owner.equals(ownParsedUuid())) {
						piece = dev.pwiepixel.cubiqclient.core.cosmetic.pet.DevPets.info(online);
					}
					lastOwner = p.owner;
					lastPiece = piece;
					java.util.Arrays.fill(petHats, null);
					java.util.Arrays.fill(petHatsTried, false);
				}
				int kind = Math.max(0, Math.min(petHats.length - 1, p.kind));
				if (!petHatsTried[kind]) {
					HatInfo piece = petPiece(lastPiece, p.kind);
					petHats[kind] = piece == null ? null : v2(piece, false);
					petHatsTried[kind] = true;
				}
				dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hat = petHats[kind];
				if (hat == null || !hat.model.pet()) continue;
				int st = p.state();
				dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetDraw<T> d = petDraws[out];
				if (d == null) petDraws[out] = d = new dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetDraw<T>();
				d.pet = p;
				long fixed = dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetSim.forceTimeMs;
				int drv = dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.driver(st);
				int prevDrv = dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.driver(p.prevState());
				// Jede Seite der Überblendung auf ihrer eigenen Zeitbasis, nach dem Treiber, der wirklich spielt: „grab“ und
				// die Aktionen (Streicheln/Füttern) einmal ab ihrem Beginn, alles andere – auch idle als Ersatz für eine
				// fehlende Aktion – nach der Uhr mit eigenem Versatz. Leucht-Frames und Höfe immer nach der Uhr.
				long own = fixed >= 0 ? fixed : base + p.offsetMs;
				long t = fixed >= 0 ? fixed
						: dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.poseTime(hat.model, st, wall - p.stateSince(), own);
				long tPrev = fixed >= 0 ? fixed
						: dev.pwiepixel.cubiqclient.core.cosmetic.pet.Pet.poseTime(hat.model, p.prevState(), wall - p.prevStateSince(),
								own);
				d.hat = hat.forPet(own, t, tPrev, drv, prevDrv, p.blend(wall));
				double px = p.x(alpha), py = p.y(alpha), pz = p.z(alpha);
				d.set(px - camX, py - camY, pz - camZ, p.yaw(alpha), p.scale);
				d.light = world == null ? dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetWorld.packLight(15, 15)
						: world.light(floorInt(px), floorInt(py + 0.25 * p.scale), floorInt(pz));
				out++;
			}
			return out;
		} catch (RuntimeException e) {
			online.reportError(e);
			return 0;
		}
	}

	/** Geladene Teile je {@code kind} des gerade gezeichneten Besitzers (nur Render-Thread, in {@link #petFrame}). */
	@SuppressWarnings("unchecked")
	private final dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T>[] petHats =
			new dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat[HatInfo.MAX_EXTRAS + 1];
	private final boolean[] petHatsTried = new boolean[HatInfo.MAX_EXTRAS + 1];

	/** Ein Begleiter dieses Bildes (nach {@link #petFrame}). */
	public dev.pwiepixel.cubiqclient.core.cosmetic.pet.PetDraw<T> petDraw(int i) {
		return petDraws[i];
	}

	/** Eigener Spieler aus dem letzten Begleiter-Tick (für die Selbsttest-Begleiter), sonst null. */
	private UUID selfPetOwner;

	private UUID ownParsedUuid() {
		return selfPetOwner;
	}

	private static int floorInt(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}

	/** Nur für Selbsttests: feste Uhrzeit für v2-Kosmetik (wie {@code ?t=} der Studio-Werkbank); null = Wanduhr. */
	public static volatile java.util.function.LongSupplier v2Clock;

	/** Nur für Selbsttests: bekommt jeden gezeichneten v2-Durchgang (Tempo der Animation belegen). */
	public static volatile java.util.function.BiConsumer<dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<?>, Integer> v2Trace;

	/**
	 * Einen Durchgang der v2-Kosmetik in den Kopf-Raum des Modells ausgeben (siehe {@code CosmeticV2Renderer.PASS_*}).
	 * {@code eye} = Kamera im Anhängepunkt-Raum (nur für Höfe, null = keine). Fehler landen im Fehlerbericht, nie im Spiel.
	 */
	public void emitV2(dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<T> hat, int pass, double[] eye,
			dev.pwiepixel.cubiqclient.core.cosmetic.v2.CosmeticV2Renderer.VertexSink sink) {
		if (hat == null) return;
		java.util.function.BiConsumer<dev.pwiepixel.cubiqclient.core.cosmetic.v2.V2Hat<?>, Integer> trace = v2Trace;
		if (trace != null) trace.accept(hat, pass);
		try {
			// Begleiter: jede Seite der Überblendung auf ihrer Zeit, gemischt wie in den Vorschauen; Hüte/Flügel wie bisher
			if (hat.pet) {
				v2Renderer.pose(hat.model, hat.poseMs, hat.prevPoseMs, true, hat.helmet, hat.driver, hat.prevDriver, hat.blend,
						hat.hiddenBones);
			} else {
				v2Renderer.pose(hat.model, hat.now, true, hat.helmet, hat.driver, hat.prevDriver, hat.blend, hat.hiddenBones);
			}
			v2Renderer.emit(hat.model, pass, hat.now, eye, sink);
		} catch (RuntimeException e) {
			online.reportError(e);
		}
	}

	/** Zeigt der Spieler das Cubiq-Abzeichen? {@code tab}: Tabliste, sonst Namensschild. */
	public boolean badge(UUID uuid, boolean tab) {
		if (!modules.cubiqOnline.isEnabled()) return false;
		if (tab ? !modules.badgeTab.get() : !modules.badgeNametag.get()) return false;
		return online.info(uuid).badge;
	}

	/**
	 * Profil-Abzeichen vor dem Namen (Halloween 2026: Kürbisjäger vor Mond-Fledermaus) oder null: keins, Abzeichen-Schalter
	 * aus, Modul aus. Gleiche Schalter wie das Cubiq-Abzeichen ({@code tab}: Tabliste, sonst Namensschild). Nur Spiel-Thread.
	 */
	public TextureRef eventBadge(UUID uuid, boolean tab) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled()) return null;
		if (tab ? !modules.badgeTab.get() : !modules.badgeNametag.get()) return null;
		String id = dev.pwiepixel.cubiqclient.core.creator.EventBadges.pick(online.info(uuid).badges);
		if (id == null) return null;
		try {
			return eventBadges.texture(id, System.currentTimeMillis());
		} catch (RuntimeException e) {
			online.reportError(e);
			return null;
		}
	}

	/**
	 * Fan-Symbol vor dem Namen (aktuelles Bild, Meilenstein-Rahmen eingezeichnet) oder null: kein Fan, Einstellung aus,
	 * noch nicht geladen. {@code tab}: Tabliste, sonst Namensschild. Nur im Spiel-/Render-Thread wirksam.
	 */
	public TextureRef fanSymbol(UUID uuid, boolean tab) {
		if (Thread.currentThread() != gameThread) return null;
		if (!modules.cubiqOnline.isEnabled() || !(tab ? modules.fanSymbolTab.get() : modules.fanSymbolNametag.get())) return null;
		FanInfo fan = online.info(uuid).fan;
		if (fan == null) return null;
		try {
			return fanSymbols.texture(fan, System.currentTimeMillis());
		} catch (RuntimeException e) {
			online.reportError(e);
			return null;
		}
	}

	/**
	 * Creator-Stern vor dem Namen (Stufe = Farbe) oder null. Hängt an denselben Schaltern wie die Fan-Symbole
	 * ({@code tab}: Tabliste, sonst Namensschild).
	 */
	public dev.pwiepixel.cubiqclient.core.creator.CreatorTier creatorStar(UUID uuid, boolean tab) {
		if (!modules.cubiqOnline.isEnabled() || !(tab ? modules.fanSymbolTab.get() : modules.fanSymbolNametag.get())) return null;
		return online.info(uuid).creator;
	}

	/** Umhang-Simulation einer Entity (null = starr/Vanilla zeichnen). */
	public ClothSim sim(int entityId) {
		if (!modules.capePhysics.isEnabled()) return null;
		return physics.sim(entityId);
	}

	/**
	 * Zeichnet den simulierten Umhang im eingestellten Stil (glatt oder Stufen) – für alle Loader gleich.
	 */
	public void emitCape(ClothSim sim, float partial, ClothMesh.QuadSink sink) {
		mesh.emit(sim, partial, physics.blocky(), sink);
	}

	/** Gemeinsamer Mesh-Baukasten (nur Render-Thread). */
	public ClothMesh mesh() {
		return mesh;
	}

	/** Emotes (Rad, Animationen, Ereignisse anderer Spieler). */
	public dev.pwiepixel.cubiqclient.core.emote.EmoteController emotes() {
		return emotes;
	}

	public CubiqOnline online() {
		return online;
	}

	public CapePhysics physics() {
		return physics;
	}
}
