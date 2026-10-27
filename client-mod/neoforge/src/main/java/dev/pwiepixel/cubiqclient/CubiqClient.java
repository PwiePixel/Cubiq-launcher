package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.config.ConfigStore;
import dev.pwiepixel.cubiqclient.core.input.ClickCounter;
import dev.pwiepixel.cubiqclient.core.input.ToggleState;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.zoom.ZoomState;
import dev.pwiepixel.cubiqclient.dev.AutoTest;
import dev.pwiepixel.cubiqclient.feature.PvpFeatures;
import dev.pwiepixel.cubiqclient.hud.HudManager;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointEditScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointListScreen;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
//? if >=1.20.5 {
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
//?} else {
/*import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay;
import net.neoforged.neoforge.event.TickEvent;
*///?}
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} elif >=1.21.6 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Einstiegspunkt des Cubiq Clients für NeoForge (nur Client; auf einem Server macht die Mod nichts).
 * NeoForge-Events für Einstieg, Tasten, Ticks, HUD und Fadenkreuz; Zoom, Maus, Fullbright, Freelook,
 * Treffer-Farbe und Startbildschirm über dieselben Vanilla-Mixins wie der Fabric-Build
 * (NeoForge liefert Mixin + MixinExtras mit).
 */
@Mod(CubiqClient.MOD_ID)
public final class CubiqClient {
	public static final String MOD_ID = "cubiqclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cubiq Client");

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

	// @Mod(dist = CLIENT) gibt es erst ab NeoForge 20.5 – daher die Seite als Konstruktor-Argument (ab 20.2).
	public CubiqClient(IEventBus modBus, ModContainer container, Dist dist) {
		if (dist != Dist.CLIENT) {
			LOGGER.info("Cubiq Client ist eine reine Client-Mod – auf dem Server inaktiv");
			return;
		}
		instance = this;
		// Farben des Launchers (config/cubiqclient/launcher-theme.json) – fehlt sie, gilt das Standard-Thema.
		dev.pwiepixel.cubiqclient.core.ui.Theme.loadFrom(FMLPaths.CONFIGDIR.get());
		dev.pwiepixel.cubiqclient.core.i18n.I18n.init(FMLPaths.CONFIGDIR.get());
		dev.pwiepixel.cubiqclient.core.clips.Clips.init(FMLPaths.CONFIGDIR.get());
		// Grafik-Modus „Schön“/„Max FPS“ (config/cubiqclient/fps-mode.json).
		dev.pwiepixel.cubiqclient.core.perf.FpsConfigMode.init(FMLPaths.CONFIGDIR.get());
		config = new ConfigStore(FMLPaths.CONFIGDIR.get().resolve("cubiqclient.json"));
		ConfigStore.Status status = config.load(modules.registry);
		// Menü-Stil für Vanilla-Menüs (Pause, Serverliste, Laden, Optionen, Welten).
		dev.pwiepixel.cubiqclient.core.menus.MenuStyle.install(modules);
		// Sozial-Benachrichtigungen (Toasts): Ton, Vollbild, Schnelltaste.
		dev.pwiepixel.cubiqclient.social.SocialHooks.install(modules);
		// Zoom-/Freelook-Taste sind Vanilla-Belegungen – im Cubiq-Menü ändern sie dieselbe Belegung.
		modules.zoomKey.link(CubiqKeys.link(() -> CubiqKeys.zoom));
		modules.freelookKey.link(CubiqKeys.link(() -> CubiqKeys.freelook));
		modules.worldMapKey.link(CubiqKeys.link(() -> CubiqKeys.worldMap));
		modules.notes.openKey.link(CubiqKeys.link(() -> CubiqKeys.notes));
		modules.cinematic.addKey.link(CubiqKeys.link(() -> CubiqKeys.cinematicAdd));
		modules.cinematic.playKey.link(CubiqKeys.link(() -> CubiqKeys.cinematicPlay));
		modules.cinematic.removeKey.link(CubiqKeys.link(() -> CubiqKeys.cinematicRemove));
		modules.cinematic.clearKey.link(CubiqKeys.link(() -> CubiqKeys.cinematicClear));
		modules.cinematic.freecamKey.link(CubiqKeys.link(() -> CubiqKeys.cinematicFreecam));
		// Kamera-Fahrten (config/cubiqclient-cinematic.json).
		dev.pwiepixel.cubiqclient.cinematic.CinematicHooks.init(modules, FMLPaths.CONFIGDIR.get());
		// Karten (Minimap + Weltkarte): Kartenspeicher unter config/cubiqclient/maps.
		// Komfort-/PvP-Paket (Erwähnungen, Filter, Auto-Reconnect, Warnungen, Zähler, Streamer-Modus …).
		dev.pwiepixel.cubiqclient.qol.QolHooks.init(modules);
		// Komfort-Paket 2: Tooltips, Server-Profile, Panorama.
		dev.pwiepixel.cubiqclient.comfort.ComfortHooks.init(modules);
		// Screenshot-Werkzeuge: Vorschau nach F2, Chat-Aktionen, Bild-Editor, Essential-Vorschau ersetzen.
		dev.pwiepixel.cubiqclient.screenshot.ScreenshotHooks.install(modules);
		dev.pwiepixel.cubiqclient.core.map.MapEngine.init(modules, FMLPaths.CONFIGDIR.get());
		// Notizen je Welt (config/cubiqclient/notes): Seite im Cubiq-Menü, Taste, angeheftete Notiz im HUD.
		dev.pwiepixel.cubiqclient.core.notes.Notes.init(modules, FMLPaths.CONFIGDIR.get(), message -> LOGGER.info(message));
		if (status == ConfigStore.Status.RECOVERED) {
			LOGGER.warn("Config war beschädigt – Standardwerte geladen, Sicherung: {}", config.brokenFile());
		}

		modBus.addListener(RegisterKeyMappingsEvent.class, CubiqKeys::register);
		waypoints = new dev.pwiepixel.cubiqclient.feature.Waypoints(modules,
				FMLPaths.CONFIGDIR.get().resolve("cubiqclient-waypoints.json"));
		IEventBus bus = NeoForge.EVENT_BUS;
		// HUD über allem anderen (nach der Vanilla-GUI), das eigene Fadenkreuz statt der Vanilla-Ebene.
		bus.addListener(RenderGuiEvent.Post.class, e -> hud().render(Gfx.of(e.getGuiGraphics())));
		//? if >=1.20.5 {
		bus.addListener(RenderGuiLayerEvent.Pre.class, e -> {
			if (e.getName().equals(VanillaGuiLayers.CROSSHAIR) && hud().crosshair().replacesVanilla()) {
				e.setCanceled(true);
				hud().crosshair().drawInGame(Gfx.of(e.getGuiGraphics()));
			}
		});
		//?} else {
		/*bus.addListener(RenderGuiOverlayEvent.Pre.class, e -> {
			if (e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && hud().crosshair().replacesVanilla()) {
				e.setCanceled(true);
				hud().crosshair().drawInGame(Gfx.of(e.getGuiGraphics()));
			}
		});
		*///?}
		// Vor der Spieler-Bewegung: Toggle-Tasten, Freelook, Treffer-Farbe. Danach: Menü-/Fullbright-Taste.
		onStartTick(pvp::tick);
		// Kamera-Fahrten: Tasten, Ende der Fahrt, Vorschau – und während der Fahrt alle Eingaben loslassen.
		onStartTick(dev.pwiepixel.cubiqclient.cinematic.CinematicHooks::tick);
		onEndTick(this::onTick);
		bus.addListener(GameShuttingDownEvent.class, e -> {
			saveConfig();
			waypoints.save();
			if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().save();
			if (dev.pwiepixel.cubiqclient.core.map.MapEngine.get() != null) dev.pwiepixel.cubiqclient.core.map.MapEngine.get().shutdown();
		});

		// "Konfigurieren" in der Mod-Liste öffnet das Cubiq-Menü.
		//? if >=1.20.5 {
		container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new CubiqMenuScreen(parent));
		//?} else
		/*container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new CubiqMenuScreen(parent)));*/
		// Cubiq API (Abzeichen, Cubiq-Umhänge, Presence) + Umhang-Physik; nichts davon blockiert den Start.
		dev.pwiepixel.cubiqclient.online.OnlineHooks.init(FMLPaths.CONFIGDIR.get(), modules, modVersion(MOD_ID),
				modVersion("minecraft"), "neoforge", message -> LOGGER.info(message));
		// Konten: Wechsel ohne Neustart (mit Cubiq Launcher dessen Konten, sonst eigene Anmeldung je Instanz).
		dev.pwiepixel.cubiqclient.core.account.AccountManager.init(new dev.pwiepixel.cubiqclient.online.SessionSwap(
				FMLPaths.CONFIGDIR.get(), "Cubiq-Client/" + modVersion(MOD_ID) + " (Minecraft " + modVersion("minecraft") + "; neoforge)", message -> LOGGER.info(message)));
		// Bug melden (Cubiq-Menü): Mod-Liste, Log und Screenshots aus dem Spielordner, Senden über die Cubiq API.
		dev.pwiepixel.cubiqclient.core.bugreport.BugReports.init(FMLPaths.CONFIGDIR.get(), modVersion(MOD_ID), modVersion("minecraft"), "neoforge");
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.init(modules);
		// Leistung (Dynamische FPS, Culling, Partikel, Welt-Details, FPS-Boost); Leistungs-Mods übernehmen ihre Teile.
		dev.pwiepixel.cubiqclient.perf.PerfHooks.init(modules, id -> ModList.get().isLoaded(id),
				dev.pwiepixel.cubiqclient.core.perf.PerfCompat.NEOFORGE, modVersion("minecraft"), message -> LOGGER.info(message), true);
		// Netzwerk-Optimierung + Ping-Messung (Handler an der Client-Verbindung, siehe core.net).
		dev.pwiepixel.cubiqclient.net.NetHooks.init(modules, message -> LOGGER.info(message));
		// Cubiq Server Rules: Server sperrt/erzwingt Module (Kanäle cubiqclient:rules/hello, Logik in core.serverrules).
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.init(modBus, modules, modVersion(MOD_ID), modVersion("minecraft"),
				message -> LOGGER.info(message));
		AutoTest.installIfRequested();

		LOGGER.info("Cubiq Client {} initialisiert (NeoForge {}) – {} Module, Config {} ({})",
				modVersion(MOD_ID), modVersion("neoforge"), modules.registry.all().size(), status, config.file());
	}

	/** Am Anfang jedes Client-Ticks (ClientTickEvent.Pre bzw. TickEvent-Phase START bis 1.20.4). */
	public static void onStartTick(Consumer<Minecraft> handler) {
		//? if >=1.20.5 {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> handler.accept(Minecraft.getInstance()));
		//?} else {
		/*NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, e -> {
			if (e.phase == TickEvent.Phase.START) handler.accept(Minecraft.getInstance());
		});
		*///?}
	}

	/** Am Ende jedes Client-Ticks (ClientTickEvent.Post bzw. TickEvent-Phase END bis 1.20.4). */
	public static void onEndTick(Consumer<Minecraft> handler) {
		//? if >=1.20.5 {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> handler.accept(Minecraft.getInstance()));
		//?} else {
		/*NeoForge.EVENT_BUS.addListener(TickEvent.ClientTickEvent.class, e -> {
			if (e.phase == TickEvent.Phase.END) handler.accept(Minecraft.getInstance());
		});
		*///?}
	}

	/** Version einer geladenen Mod ("minecraft", "neoforge", "cubiqclient"), sonst "?". */
	public static String modVersion(String modId) {
		return ModList.get().getModContainerById(modId)
				.map(c -> c.getModInfo().getVersion().toString()).orElse("?");
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
			Mc.actionBar(Component.literal(text));
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
			Mc.actionBar(Component.literal(I18n.tr("toast.hudProfile", name)));
			saveConfig();
		}
		while (CubiqKeys.menu.consumeClick()) {
			if (Mc.screen() == null) Mc.setScreen(new CubiqMenuScreen(null));
		}
		while (CubiqKeys.redstoneOverlay.consumeClick()) {
			modules.redstoneOverlay.toggle();
			Mc.actionBar(Component.literal(I18n.tr("toast.redstoneOverlay", modules.redstoneOverlay.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled"))));
			saveConfig();
		}
		// Clips & Aufnahme: aufgenommen wird im Launcher, hier nur die Tasten melden.
		while (CubiqKeys.saveClip.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().saveClip();
		while (CubiqKeys.toggleRecording.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().toggleRecording();
		dev.pwiepixel.cubiqclient.core.clips.Clips.get().tick(modules.clips.isEnabled(), CLIP_MESSAGES);
		while (CubiqKeys.fullbright.consumeClick()) {
			Mc.actionBar(Component.literal(modules.toggleGamma()));
			saveConfig();
		}
		while (CubiqKeys.gammaCycle.consumeClick()) {
			Mc.actionBar(Component.literal(modules.cycleGamma()));
			saveConfig();
		}
		while (CubiqKeys.autoRefill.consumeClick()) {
			modules.autoRefill.toggle();
			Mc.actionBar(Component.literal(I18n.tr("toast.autoRefill", modules.autoRefill.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled"))));
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
		if (CubiqKeys.social != null) {
			while (CubiqKeys.social.consumeClick()) dev.pwiepixel.cubiqclient.social.SocialHooks.onSocialKey();
		}
		if (CubiqKeys.quickReply != null) {
			while (CubiqKeys.quickReply.consumeClick()) dev.pwiepixel.cubiqclient.social.SocialHooks.onQuickReplyKey();
		}
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
		hud().tick();
		tickRedstone();
		dev.pwiepixel.cubiqclient.online.OnlineHooks.tick(mc);
		dev.pwiepixel.cubiqclient.online.EmoteHooks.tick(mc);
		dev.pwiepixel.cubiqclient.perf.PerfHooks.tick(mc);
		dev.pwiepixel.cubiqclient.net.NetHooks.tick(mc);
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
	 * "Hotbar speichern" kollidiert. Selbst belegte Tasten bleiben unangetastet.
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
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			leftClicks.record(now);
			// Angreifen/Abbauen beendet das eigene Emote (wie Bewegung).
			dev.pwiepixel.cubiqclient.online.EmoteHooks.onAttack();
		}
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) rightClicks.record(now);
	}

	/** Pro Frame aus getFov: aktualisiert den Zoom und liefert den FOV-Divisor. */
	public double updateZoom() {
		// Das Fernrohr hat Vorrang; die filmische Kamera gilt auf Wunsch nur, solange gezoomt wird.
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

	/** Maus-Divisor während des Zooms (1 = unverändert). */
	public double mouseDivisor() {
		return zoom.mouseDivisor(modules.zoomSlowMouse.get());
	}

	public boolean fullbright() {
		return modules.fullbright.isEnabled();
	}

	/** Gamma für die Lightmap (vorhanden = benutzen, leer = Vanilla); von der Lightmap je Frame gelesen. */
	public java.util.OptionalDouble gammaOverride() {
		return modules.gammaOverride();
	}

	// --- Zugriff ---

	public CubiqModules modules() {
		return modules;
	}

	/**
	 * HUD-Elemente erst bei Bedarf anlegen: ab NeoForge 21.6 wird die Mod konstruiert, bevor es
	 * Minecraft.getInstance() gibt (die HUD-Klassen merken sich die Instanz).
	 */
	public HudManager hud() {
		if (hud == null) hud = new HudManager(modules);
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

	public dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone() {
		return redstone;
	}

	public dev.pwiepixel.cubiqclient.feature.Waypoints waypoints() {
		return waypoints;
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
