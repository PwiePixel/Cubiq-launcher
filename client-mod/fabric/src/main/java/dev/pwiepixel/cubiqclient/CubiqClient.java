package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.config.ConfigStore;
import dev.pwiepixel.cubiqclient.core.input.ClickCounter;
import dev.pwiepixel.cubiqclient.core.input.ToggleState;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.zoom.ZoomState;
import dev.pwiepixel.cubiqclient.dev.AutoTest;
import dev.pwiepixel.cubiqclient.feature.PvpFeatures;
import dev.pwiepixel.cubiqclient.hud.HudManager;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointEditScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointListScreen;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
// Logging: SLF4J ab 1.17, davor nur Log4j (gleiche {}-Platzhalter).
//? if >=1.17 {
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//?} else {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
*///?}

import java.io.IOException;
//? if >=1.21.6 {
/*import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
*///?} elif >=1.15
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} elif >=1.21.6 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** Einstiegspunkt des Cubiq Clients (nur Client). */
public final class CubiqClient implements ClientModInitializer {
	public static final String MOD_ID = "cubiqclient";
	//? if >=1.17 {
	public static final Logger LOGGER = LoggerFactory.getLogger("Cubiq Client");
	//?} else
	//public static final Logger LOGGER = LogManager.getLogger("Cubiq Client");

	private static CubiqClient instance;

	private final CubiqModules modules = new CubiqModules();
	private final ClickCounter leftClicks = new ClickCounter();
	private final ClickCounter rightClicks = new ClickCounter();
	private final ZoomState zoom = new ZoomState();
	/** Filmische Kamera nur während des Zooms (stellt den Wert des Spielers danach wieder her). */
	private final dev.pwiepixel.cubiqclient.core.util.FlagOverride zoomCinematic =
			new dev.pwiepixel.cubiqclient.core.util.FlagOverride();
	private ConfigStore config;
	private HudManager hud;
	private final PvpFeatures pvp = new PvpFeatures(modules);
	private final dev.pwiepixel.cubiqclient.feature.ChatFeatures chat =
			new dev.pwiepixel.cubiqclient.feature.ChatFeatures(modules);
	/** Redstone-Werkzeuge: Signalstärke, Takt-Messer, Signal-Overlay (Logik in core.redstone). */
	private final dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone =
			new dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools(modules);
	private long redstoneErrorLogged;
	private dev.pwiepixel.cubiqclient.feature.Waypoints waypoints;
	/** Tastendruck-Erkennung für die Modul-Tasten (Wegpunkte, Text-Hotkeys). */
	private final dev.pwiepixel.cubiqclient.core.input.KeyPresses moduleKeys =
			new dev.pwiepixel.cubiqclient.core.input.KeyPresses(dev.pwiepixel.cubiqclient.compat.Keys::isDown);
	/** Zuletzt benutztes Welt-Sichtfeld (für die Wegpunkt-Projektion), aus dem FOV-Mixin. */
	private double worldFov = 70;
	/** Einmalig den Vanilla-Titelbildschirm zulassen ("Klassisch" auf dem Cubiq-Startbildschirm). */
	private boolean vanillaTitleOnce;
	/** Nur für den Autotest: Zoom ohne Tastendruck erzwingen. */
	private boolean forceZoom;

	public static CubiqClient get() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		// Benchmark „Vanilla“ (-PcubiqBenchVanilla): Cubiq Client bleibt ganz aus, nur der Messablauf läuft.
		if (Boolean.getBoolean("cubiqclient.bench.vanilla")) {
			LOGGER.info("Cubiq Client aus (Benchmark Vanilla)");
			AutoTest.installIfRequested();
			return;
		}
		// Farben des Launchers (config/cubiqclient/launcher-theme.json) – fehlt sie, gilt das Standard-Thema.
		Theme.loadFrom(FabricLoader.getInstance().getConfigDir());
		dev.pwiepixel.cubiqclient.core.i18n.I18n.init(FabricLoader.getInstance().getConfigDir());
		dev.pwiepixel.cubiqclient.core.clips.Clips.init(FabricLoader.getInstance().getConfigDir());
		// Grafik-Modus „Schön“/„Max FPS“ (config/cubiqclient/fps-mode.json).
		dev.pwiepixel.cubiqclient.core.perf.FpsConfigMode.init(FabricLoader.getInstance().getConfigDir());
		config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("cubiqclient.json"));
		ConfigStore.Status status = config.load(modules.registry);
		if (status == ConfigStore.Status.RECOVERED) {
			LOGGER.warn("Config war beschädigt – Standardwerte geladen, Sicherung: {}", config.brokenFile());
		}

		// Menü-Stil für Vanilla-Menüs (Pause, Serverliste, Laden, Optionen, Welten).
		dev.pwiepixel.cubiqclient.core.menus.MenuStyle.install(modules);
		CubiqKeys.register();
		// Sozial-Benachrichtigungen (Toasts): Ton, Vollbild, Schnelltaste.
		dev.pwiepixel.cubiqclient.social.SocialHooks.install(modules);
		// MiyakoMC (persönlich, nur seine UUID): Intro-Video beim ersten Start (nur moderner Fabric-Pfad).
		dev.pwiepixel.cubiqclient.core.halloween.Miyako.get().dir(
				FabricLoader.getInstance().getConfigDir().resolve("cubiqclient").resolve("miyako"));
		dev.pwiepixel.cubiqclient.halloween.MiyakoHooks.install();
		// Zoom-/Freelook-Taste sind Vanilla-Belegungen – im Cubiq-Menü ändern sie dieselbe Belegung.
		modules.zoomKey.link(CubiqKeys.link(CubiqKeys.zoom));
		modules.freelookKey.link(CubiqKeys.link(CubiqKeys.freelook));
		modules.worldMapKey.link(CubiqKeys.link(CubiqKeys.worldMap));
		modules.notes.openKey.link(CubiqKeys.link(CubiqKeys.notes));
		modules.cinematic.addKey.link(CubiqKeys.link(CubiqKeys.cinematicAdd));
		modules.cinematic.playKey.link(CubiqKeys.link(CubiqKeys.cinematicPlay));
		modules.cinematic.removeKey.link(CubiqKeys.link(CubiqKeys.cinematicRemove));
		modules.cinematic.clearKey.link(CubiqKeys.link(CubiqKeys.cinematicClear));
		modules.cinematic.freecamKey.link(CubiqKeys.link(CubiqKeys.cinematicFreecam));
		// Karten (Minimap + Weltkarte): Kartenspeicher unter config/cubiqclient/maps.
		// Komfort-/PvP-Paket (Erwähnungen, Filter, Auto-Reconnect, Warnungen, Zähler, Streamer-Modus …).
		dev.pwiepixel.cubiqclient.qol.QolHooks.init(modules);
		// Komfort-Paket 2: Tooltips, Server-Profile, Panorama.
		dev.pwiepixel.cubiqclient.comfort.ComfortHooks.init(modules);
		// Screenshot-Werkzeuge: Vorschau nach F2, Chat-Aktionen, Bild-Editor, Essential-Vorschau ersetzen.
		dev.pwiepixel.cubiqclient.screenshot.ScreenshotHooks.install(modules);
		dev.pwiepixel.cubiqclient.core.map.MapEngine.init(modules, FabricLoader.getInstance().getConfigDir());
		// Notizen je Welt (config/cubiqclient/notes): Seite im Cubiq-Menü, Taste, angeheftete Notiz im HUD.
		dev.pwiepixel.cubiqclient.core.notes.Notes.init(modules, FabricLoader.getInstance().getConfigDir(), message -> LOGGER.info(message));
		// Kamera-Fahrten (config/cubiqclient-cinematic.json, Tasten in den Steuerungen).
		dev.pwiepixel.cubiqclient.cinematic.CinematicHooks.init(modules, FabricLoader.getInstance().getConfigDir());
		waypoints = new dev.pwiepixel.cubiqclient.feature.Waypoints(modules,
				FabricLoader.getInstance().getConfigDir().resolve("cubiqclient-waypoints.json"));
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.init(modules);
		hud = new HudManager(modules);
		// Kosten-Messung (-Dcubiqclient.bench.noHud): HUD gar nicht erst einhängen.
		if (!Boolean.getBoolean("cubiqclient.bench.noHud")) registerHud();
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		// Vor der Spieler-Bewegung: Toggle-Tasten, Freelook, Treffer-Farbe, PvP-Zähler.
		ClientTickEvents.START_CLIENT_TICK.register(pvp::tick);
		// Kamera-Fahrten: Tasten, Ende der Fahrt, Vorschau – und während der Fahrt alle Eingaben loslassen.
		ClientTickEvents.START_CLIENT_TICK.register(dev.pwiepixel.cubiqclient.cinematic.CinematicHooks::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			saveConfig();
			waypoints.save();
			if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().save();
			if (dev.pwiepixel.cubiqclient.core.map.MapEngine.get() != null) dev.pwiepixel.cubiqclient.core.map.MapEngine.get().shutdown();
		});
		String version = FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String minecraft = FabricLoader.getInstance().getModContainer("minecraft")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		// Cubiq API (Abzeichen, Cubiq-Umhänge, Presence) + Umhang-Physik; nichts davon blockiert den Start.
		dev.pwiepixel.cubiqclient.online.OnlineHooks.init(FabricLoader.getInstance().getConfigDir(), modules, version,
				minecraft, "fabric", message -> LOGGER.info(message));
		// Konten: Wechsel ohne Neustart (mit Cubiq Launcher dessen Konten, sonst eigene Anmeldung je Instanz).
		dev.pwiepixel.cubiqclient.core.account.AccountManager.init(new dev.pwiepixel.cubiqclient.online.SessionSwap(
				FabricLoader.getInstance().getConfigDir(), "Cubiq-Client/" + version + " (Minecraft " + minecraft + "; fabric)", message -> LOGGER.info(message)));
		// Bug melden (Cubiq-Menü): Mod-Liste, Log und Screenshots aus dem Spielordner, Senden über die Cubiq API.
		dev.pwiepixel.cubiqclient.core.bugreport.BugReports.init(FabricLoader.getInstance().getConfigDir(), version, minecraft, "fabric");
		// Leistung (Dynamische FPS, Culling, Partikel, Welt-Details, FPS-Boost); Leistungs-Mods übernehmen ihre Teile.
		dev.pwiepixel.cubiqclient.perf.PerfHooks.init(modules, id -> FabricLoader.getInstance().isModLoaded(id),
				dev.pwiepixel.cubiqclient.core.perf.PerfCompat.FABRIC, minecraft, message -> LOGGER.info(message), true);
		// Netzwerk-Optimierung + Ping-Messung (Handler an der Client-Verbindung, siehe core.net).
		dev.pwiepixel.cubiqclient.net.NetHooks.init(modules, message -> LOGGER.info(message));
		// Cubiq Server Rules: Server sperrt/erzwingt Module (Kanäle cubiqclient:rules/hello, Logik in core.serverrules).
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.init(modules, version, minecraft, message -> LOGGER.info(message));
		// Eingebaute Optimierungen (Jar-in-Jar): welche Fassung lädt Fabric gerade?
		dev.pwiepixel.cubiqclient.core.perf.BundledMods.setLoaded(id -> FabricLoader.getInstance().getModContainer(id)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null));
		AutoTest.installIfRequested();

		LOGGER.info("Cubiq Client {} initialisiert – {} Module, Config {} ({})",
				version, modules.registry.all().size(), status, config.file());
	}

	/**
	 * HUD einhängen: Fabric-HUD-Ebenen ab 1.21.6 (dort ersetzt Cubiq auch die Fadenkreuz-Ebene),
	 * davor der klassische HUD-Callback (Vanilla-Fadenkreuz blendet dann CrosshairMixin aus).
	 *
	 * <p>Nicht {@code addLast}: Fabric hängt solche Ebenen an die Untertitel, und die zeichnet Minecraft ab 1.21.9
	 * verzögert – mit offenem Inventar/Truhe/Ofen erst nach dem abgedunkelten Menü-Hintergrund. Das ganze Cubiq-HUD
	 * (Hunger-Anzeige, Anzeigen) lag dann hell über dem Hintergrund statt wie die Vanilla-Leisten darunter. Direkt
	 * nach der Spielerliste bleibt es über allen Vanilla-Anzeigen, aber im normalen HUD-Durchgang.
	 */
	private void registerHud() {
		//? if >=1.21.6 {
		/*HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST, id("hud"), (g, delta) -> hud.render(Gfx.of(g)));
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			if (hud.crosshair().replacesVanilla()) hud.crosshair().drawInGame(Gfx.of(g));
			//? if >=26.1 {
			else vanilla.extractRenderState(g, delta);
			//?} else
			else vanilla.render(g, delta);
		});
		*///?} elif >=1.16 {
		HudRenderCallback.EVENT.register((g, delta) -> hud.render(Gfx.of(g)));
		//?} elif >=1.15 {
		/*HudRenderCallback.EVENT.register(delta -> hud.render(Gfx.of()));
		*///?}
		// 1.14 hat keinen HUD-Callback: dort ruft HudMixin (Gui#render) renderHud() auf.
	}

	/** HUD zeichnen – aus dem HUD-Callback bzw. in 1.14 aus HudMixin. */
	public void renderHud(Gfx g) {
		hud.render(g);
	}

	/**
	 * Aus dem Mixin in setScreen: ersetzt den Vanilla-Titelbildschirm durch den Cubiq-Startbildschirm,
	 * solange das Modul "Startbildschirm" an ist.
	 */
	public Screen replaceScreen(Screen screen) {
		if (screen == null || screen.getClass() != TitleScreen.class) return screen;
		if (vanillaTitleOnce) {
			vanillaTitleOnce = false;
			return screen;
		}
		return modules.titleScreen.isEnabled() ? new CubiqTitleScreen() : screen;
	}

	/** Öffnet einmalig den Vanilla-Titelbildschirm. */
	public void openVanillaTitle() {
		vanillaTitleOnce = true;
		Mc.setScreen(new TitleScreen());
	}

	//? if >=1.21.11 {
	/*public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
	*///?} elif >=1.21.6 {
	/*public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
	*///?}

	/** Meldungen der Clips in der Aktionsleiste. */
	private static final dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar CLIP_MESSAGES = new dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar() {
		@Override
		public void show(String text) {
			Mc.actionBar(Mc.text(text));
		}

		@Override
		public String keyLabel(boolean record) {
			return Mc.keyName(record ? CubiqKeys.toggleRecording : CubiqKeys.saveClip);
		}
	};

	private void onTick(Minecraft mc) {
		migrateKeys(mc);
		dev.pwiepixel.cubiqclient.menus.KeySearchUi.poll();
		// Touch-Modus (mobile Engine): feste Overlay-Tasten + Bildschirmtastatur; sonst nichts.
		dev.pwiepixel.cubiqclient.touch.TouchHooks.tick();
		while (CubiqKeys.hudProfile.consumeClick()) {
			String name = modules.profiles.cycle();
			Mc.actionBar(Mc.text(I18n.tr("toast.hudProfile", name)));
			saveConfig();
		}
		while (CubiqKeys.menu.consumeClick()) {
			if (Mc.screen() == null) Mc.setScreen(new CubiqMenuScreen(null));
		}
		while (CubiqKeys.redstoneOverlay.consumeClick()) {
			modules.redstoneOverlay.toggle();
			Mc.actionBar(Mc.text(I18n.tr("toast.redstoneOverlay", modules.redstoneOverlay.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled"))));
			saveConfig();
		}
		// Clips & Aufnahme: aufgenommen wird im Launcher, hier nur die Tasten melden.
		while (CubiqKeys.saveClip.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().saveClip();
		while (CubiqKeys.toggleRecording.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().toggleRecording();
		dev.pwiepixel.cubiqclient.core.clips.Clips.get().tick(modules.clips.isEnabled(), CLIP_MESSAGES);
		while (CubiqKeys.fullbright.consumeClick()) {
			Mc.actionBar(Mc.text(modules.toggleGamma()));
			saveConfig();
		}
		while (CubiqKeys.gammaCycle.consumeClick()) {
			Mc.actionBar(Mc.text(modules.cycleGamma()));
			saveConfig();
		}
		while (CubiqKeys.autoRefill.consumeClick()) {
			modules.autoRefill.toggle();
			Mc.actionBar(Mc.text(I18n.tr("toast.autoRefill", modules.autoRefill.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled"))));
			saveConfig();
		}
		// Emote-Rad: Taste halten öffnet es, Loslassen (im Rad abgefragt) spielt das gezeigte Emote.
		while (CubiqKeys.emoteWheel.consumeClick()) {
			if (Mc.screen() == null && mc.player != null && dev.pwiepixel.cubiqclient.online.EmoteHooks.enabled()) {
				Mc.setScreen(new dev.pwiepixel.cubiqclient.screen.EmoteWheelScreen());
			}
		}
		// Garderobe (Taste standardmäßig unbelegt)
		while (CubiqKeys.worldMap.consumeClick()) {
			if (mc.player != null && modules.worldMap.isEnabled() && Mc.screen() == null) {
				dev.pwiepixel.cubiqclient.screen.WorldMapScreen screen = dev.pwiepixel.cubiqclient.screen.WorldMapScreen.create();
				if (screen != null) Mc.setScreen(screen);
			}
		}
		if (modules.keyDefaults.needsWorldMapKeyCheck() && mc.options != null) {
			modules.keyDefaults.markWorldMapKeyChecked();
			if (CubiqKeys.resolveWorldMapConflict()) LOGGER.info("Weltkarten-Taste M war doppelt belegt – freigegeben");
			saveConfig();
		}
		while (CubiqKeys.wardrobe.consumeClick()) {
			if (Mc.screen() == null && dev.pwiepixel.cubiqclient.screen.WardrobeScreen.available()) {
				Mc.setScreen(dev.pwiepixel.cubiqclient.screen.WardrobeScreen.create(null));
			}
		}
		// Notizen (Taste standardmäßig unbelegt): Cubiq-Menü direkt auf der Notiz-Seite der aktuellen Welt.
		if (CubiqKeys.notes != null) {
			while (CubiqKeys.notes.consumeClick()) {
				if (Mc.screen() == null && modules.notes.notes.isEnabled()) {
					dev.pwiepixel.cubiqclient.core.notes.Notes.requestOpen();
					Mc.setScreen(new CubiqMenuScreen(null));
				}
			}
		}
		if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().tick(System.currentTimeMillis());
		// Sozial (Taste standardmäßig unbelegt) und Schnellantwort zum neuesten Toast (Y).
		while (CubiqKeys.social.consumeClick()) dev.pwiepixel.cubiqclient.social.SocialHooks.onSocialKey();
		while (CubiqKeys.quickReply.consumeClick()) dev.pwiepixel.cubiqclient.social.SocialHooks.onQuickReplyKey();
		// Wegpunkt- und Hotkey-Tasten gehören den Modulen (Tastenbelegung im Cubiq-Menü).
		if (Mc.screen() == null) {
			if (moduleKeys.pressed(modules.waypointAddKey) && mc.player != null && modules.waypoints.isEnabled()) {
				Mc.setScreen(new WaypointEditScreen(null, null));
			}
			if (moduleKeys.pressed(modules.waypointListKey) && modules.waypoints.isEnabled()) {
				Mc.setScreen(new WaypointListScreen(null));
			}
			for (int i = 0; i < modules.hotkeyKeys.length; i++) {
				if (moduleKeys.pressed(modules.hotkeyKeys[i])) chat.onHotkey(i);
			}
		} else {
			moduleKeys.releaseAll();
		}
		waypoints.tick(mc);
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.tick();
		chat.tick(mc);
		dev.pwiepixel.cubiqclient.qol.QolHooks.tick(mc);
		dev.pwiepixel.cubiqclient.halloween.HalloweenHooks.tick(mc);
		if (dev.pwiepixel.cubiqclient.comfort.ComfortHooks.tick(mc)) saveConfig();
		dev.pwiepixel.cubiqclient.screenshot.ScreenshotHooks.tick();
		hud.tick();
		tickRedstone();
		dev.pwiepixel.cubiqclient.online.OnlineHooks.tick(mc);
		dev.pwiepixel.cubiqclient.online.EmoteHooks.tick(mc);
		dev.pwiepixel.cubiqclient.perf.PerfHooks.tick(mc);
		dev.pwiepixel.cubiqclient.net.NetHooks.tick(mc);
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.tick(mc);
	}

	/** Redstone-Werkzeuge; ein Fehler darf nie das Spiel stören (höchstens einmal je Minute geloggt). */
	private void tickRedstone() {
		try {
			dev.pwiepixel.cubiqclient.compat.RedstoneProbe.tick(redstone);
		} catch (RuntimeException e) {
			redstone.reset();
			long now = System.currentTimeMillis();
			if (now - redstoneErrorLogged > 60_000) {
				redstoneErrorLogged = now;
				LOGGER.warn("Redstone-Werkzeuge: {}", e.toString());
			}
		}
	}

	/**
	 * Einmalige Umstellung alter Standard-Tasten: Zoom lag auf C, was ab Minecraft 1.12 mit
	 * "Hotbar speichern" kollidiert. Umgestellt wird nur, wenn die Taste noch auf dem alten
	 * Standard liegt – selbst belegte Tasten bleiben unangetastet.
	 */
	private void migrateKeys(Minecraft mc) {
		if (!modules.keyDefaults.needsZoomKeyMigration() || mc.options == null) return;
		if (CubiqKeys.migrateZoomKey()) {
			mc.options.save();
			LOGGER.info("Zoom-Taste von C auf V umgestellt (C ist ab 1.12 'Hotbar speichern')");
		}
		modules.keyDefaults.markMigrated();
		saveConfig();
	}

	/** Speichert die Einstellungen (Fehler nur loggen). */
	public void saveConfig() {
		try {
			config.saveLater(modules.registry);
		} catch (IOException e) {
			LOGGER.error("Config konnte nicht gespeichert werden: {}", config.file(), e);
		}
	}

	// --- Hooks aus den Mixins ---

	/** Mausklick im Spiel (nicht in Menüs) → CPS-Zähler. */
	public void onMouseClick(int button) {
		if (Mc.screen() != null) return;
		long now = System.currentTimeMillis();
		if (button == Keys.MOUSE_LEFT) {
			leftClicks.record(now);
			// Angreifen/Abbauen beendet das eigene Emote (wie Bewegung).
			dev.pwiepixel.cubiqclient.online.EmoteHooks.onAttack();
		}
		if (button == Keys.MOUSE_RIGHT) rightClicks.record(now);
	}

	/**
	 * Pro Frame aus getFov: aktualisiert den Zoom und liefert den FOV-Divisor. Das Fernrohr (ab 1.17)
	 * hat Vorrang; die filmische Kamera gilt auf Wunsch nur, solange gezoomt wird.
	 */
	public double updateZoom() {
		boolean wanted = modules.zoom.isEnabled()
				&& (forceZoom || (CubiqKeys.zoom.isDown() && Mc.screen() == null));
		double factor = zoom.frame(wanted, Mc.scoping(), modules.zoomFactor.get(), modules.zoomSmooth.get(), System.nanoTime());
		boolean smooth = zoomCinematic.update(Mc.smoothCamera(), zoom.isActive() && modules.zoomCinematic.get());
		if (smooth != Mc.smoothCamera()) Mc.setSmoothCamera(smooth);
		return factor;
	}

	/** Mausrad während des Zooms; true = Ereignis verbraucht (Hotbar bleibt). */
	public boolean onScroll(double amount) {
		if (!zoom.isActive() || !modules.zoomScroll.get() || Mc.screen() != null) {
			// Freie Kamera: Mausrad ändert ihre Geschwindigkeit (statt der Hotbar).
			return dev.pwiepixel.cubiqclient.cinematic.CinematicHooks.scroll(amount);
		}
		zoom.scroll(amount);
		return true;
	}

	/** Maus-Divisor während des Zooms (1 = unverändert), proportional zur Zoomstufe. */
	public double mouseDivisor() {
		return zoom.mouseDivisor(modules.zoomSlowMouse.get());
	}

	public boolean fullbright() {
		return modules.fullbright.isEnabled();
	}

	/** Gamma für die Lightmap (vorhanden = benutzen, leer = Vanilla); vom LightmapMixin je Frame gelesen. */
	public java.util.OptionalDouble gammaOverride() {
		return modules.gammaOverride();
	}

	// --- Zugriff ---

	public CubiqModules modules() {
		return modules;
	}

	public HudManager hud() {
		return hud;
	}

	public ClickCounter leftClicks() {
		return leftClicks;
	}

	public ClickCounter rightClicks() {
		return rightClicks;
	}

	public PvpFeatures pvp() {
		return pvp;
	}

	public dev.pwiepixel.cubiqclient.feature.ChatFeatures chat() {
		return chat;
	}

	public dev.pwiepixel.cubiqclient.feature.Waypoints waypoints() {
		return waypoints;
	}

	public dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone() {
		return redstone;
	}

	/** Zuletzt gezeichnetes Sichtfeld der Welt (Grad) – Grundlage der Wegpunkt-Projektion. */
	public double worldFov() {
		return worldFov;
	}

	/** Aus dem FOV-Mixin: das tatsächlich benutzte Sichtfeld merken. */
	public void setWorldFov(double fov) {
		if (fov > 1 && fov < 180) worldFov = fov;
	}

	public ToggleState sprintToggle() {
		return pvp.sprint();
	}

	public ToggleState sneakToggle() {
		return pvp.sneak();
	}

	public void setForceZoom(boolean forceZoom) {
		this.forceZoom = forceZoom;
	}
}
