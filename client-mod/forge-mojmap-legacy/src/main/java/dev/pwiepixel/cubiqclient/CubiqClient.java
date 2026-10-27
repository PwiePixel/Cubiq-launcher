package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.config.ConfigStore;
import dev.pwiepixel.cubiqclient.core.input.ClickCounter;
import dev.pwiepixel.cubiqclient.core.input.ToggleState;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.zoom.ZoomState;
import dev.pwiepixel.cubiqclient.dev.AutoTest;
import dev.pwiepixel.cubiqclient.feature.ChatFeatures;
import dev.pwiepixel.cubiqclient.feature.PvpFeatures;
import dev.pwiepixel.cubiqclient.feature.Waypoints;
import dev.pwiepixel.cubiqclient.hud.HudManager;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointEditScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointListScreen;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
//? if >=1.19 {
/*import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
*///?} else {
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?}
//? if >=1.18 && <1.19 {
/*import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.network.NetworkConstants;
*///?} elif >=1.17 && <1.18 {
/*import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.gui.ForgeIngameGui;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fmlclient.ConfigGuiHandler;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;
*///?} elif <1.17 {
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
//?}
//? if <1.15 {
/*import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
*///?}

/**
 * Cubiq Client für Forge 1.14.4–1.19.4 (nur Client). HUD, Fadenkreuz, Tick, Tasten und Titelbildschirm
 * laufen über Forge-Events; Zoom, Maus, Fullbright, Freelook und Treffer-Farbe über Mixins
 * (Forge liefert Mixin erst ab 1.15.2 mit – auf 1.14.4 gibt es Event-Ersatz, siehe {@link #legacyHooks}).
 */
public final class CubiqClient {
	public static final String MOD_ID = "cubiqclient";
	public static final Logger LOGGER = LogManager.getLogger("Cubiq Client");

	private static CubiqClient instance;

	private final CubiqModules modules = new CubiqModules();
	private final ClickCounter leftClicks = new ClickCounter();
	private final ClickCounter rightClicks = new ClickCounter();
	private final ZoomState zoom = new ZoomState();
	/** Filmische Kamera nur während des Zooms (stellt den Wert des Spielers danach wieder her). */
	private final dev.pwiepixel.cubiqclient.core.util.FlagOverride zoomCinematic =
			new dev.pwiepixel.cubiqclient.core.util.FlagOverride();
	private final ConfigStore config;
	private final HudManager hud;
	private final PvpFeatures pvp = new PvpFeatures(modules);
	private final ChatFeatures chat = new ChatFeatures(modules);
	/** Redstone-Werkzeuge: Signalstärke, Takt-Messer, Signal-Overlay (Logik in core.redstone). */
	private final dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone =
			new dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools(modules);
	private long redstoneErrorLogged;
	private final Waypoints waypoints;
	/** Tastendruck-Erkennung für die Modul-Tasten (Wegpunkte, Text-Hotkeys). */
	private final dev.pwiepixel.cubiqclient.core.input.KeyPresses moduleKeys =
			new dev.pwiepixel.cubiqclient.core.input.KeyPresses(dev.pwiepixel.cubiqclient.compat.Keys::isDown);
	private final List<Module> visibleModules = new ArrayList<>();
	/** Einmalig den Vanilla-Titelbildschirm zulassen ("Klassisch" auf dem Cubiq-Startbildschirm). */
	private boolean vanillaTitleOnce;
	/** Nur für den Autotest: Zoom ohne Tastendruck erzwingen. */
	private boolean forceZoom;
	/** Zuletzt benutztes Welt-Sichtfeld (für die Wegpunkt-Projektion), aus dem FOV-Hook. */
	private double worldFov = 70;
	/** Welt, zu der der Kartenspeicher der Minimap gehört (Wechsel → leeren). */
	private boolean hadLevel;

	public static CubiqClient get() {
		return instance;
	}

	static void init() {
		instance = new CubiqClient();
	}

	private CubiqClient() {
		instance = this;
		// Farben des Launchers (config/cubiqclient/launcher-theme.json) – fehlt sie, gilt das Standard-Thema.
		dev.pwiepixel.cubiqclient.core.ui.Theme.loadFrom(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
		dev.pwiepixel.cubiqclient.core.i18n.I18n.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
		dev.pwiepixel.cubiqclient.core.clips.Clips.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
		// Grafik-Modus „Schön“/„Max FPS“ (config/cubiqclient/fps-mode.json).
		dev.pwiepixel.cubiqclient.core.perf.FpsConfigMode.init(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());
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
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (dev.pwiepixel.cubiqclient.core.map.MapEngine.get() != null) dev.pwiepixel.cubiqclient.core.map.MapEngine.get().shutdown();
			if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().save();
		}, "Cubiq Client map save"));
		if (status == ConfigStore.Status.RECOVERED) {
			LOGGER.warn("Config war beschädigt – Standardwerte geladen, Sicherung: {}", config.brokenFile());
		}
		waypoints = new Waypoints(modules, FMLPaths.CONFIGDIR.get().resolve("cubiqclient-waypoints.json"));
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.init(modules);
		hud = new HudManager(modules);
		for (Module m : modules.registry.all()) {
			// Ohne Mixin (Forge 1.14.4) fehlen alle Module, die einen Mixin-Hook brauchen.
			if (!PvpFeatures.mixinFeatures() && (m == modules.freelook || m == modules.hitColor
					|| m == modules.reach || m == modules.combo || m == modules.chat || m == modules.autoGg
					|| m == modules.noHurtCam || m == modules.lowFire || m == modules.blockOutline || m == modules.shieldPosition
					|| m == modules.capePhysics || m == modules.emotes || m == modules.colors
					|| m == modules.entityCulling || m == modules.particles || m == modules.worldDetails
					|| m == modules.menuStyle || m == modules.netOptimize || m == modules.lowLatency
					|| m == modules.cinematic.cinematic)) {
				continue;
			}
			visibleModules.add(m);
		}

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		IEventBus bus = MinecraftForge.EVENT_BUS;
		//? if >=1.19 {
		/*modBus.addListener((RegisterKeyMappingsEvent e) -> CubiqKeys.register(e));
		bus.addListener((RenderGuiEvent.Post e) -> hud.render(Gfx.of(e.getPoseStack())));
		bus.addListener((RenderGuiOverlayEvent.Pre e) -> {
			if (e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && hud.crosshair().replacesVanilla()) e.setCanceled(true);
		});
		bus.addListener((ScreenEvent.Opening e) -> e.setNewScreen(replaceScreen(e.getNewScreen())));
		*///?} else {
		modBus.addListener((FMLClientSetupEvent e) -> CubiqKeys.register());
		bus.addListener((RenderGameOverlayEvent.Post e) -> {
			//? if >=1.16 {
			if (e.getType() == RenderGameOverlayEvent.ElementType.ALL) hud.render(Gfx.of(e.getMatrixStack()));
			//?} else
			/*if (e.getType() == RenderGameOverlayEvent.ElementType.ALL) hud.render(Gfx.of());*/
		});
		//?}
		//? if >=1.17 && <1.19 {
		/*bus.addListener((RenderGameOverlayEvent.PreLayer e) -> {
			if (e.getOverlay() == ForgeIngameGui.CROSSHAIR_ELEMENT && hud.crosshair().replacesVanilla()) e.setCanceled(true);
		});
		*///?} elif <1.17 {
		bus.addListener((RenderGameOverlayEvent.Pre e) -> {
			if (e.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS && hud.crosshair().replacesVanilla()) e.setCanceled(true);
		});
		//?}
		//? if >=1.18 && <1.19 {
		/*bus.addListener((ScreenOpenEvent e) -> e.setScreen(replaceScreen(e.getScreen())));
		*///?} elif <1.18 {
		bus.addListener((GuiOpenEvent e) -> e.setGui(replaceScreen(e.getGui())));
		//?}
		// Sozial-Toasts über jedem Bildschirm, auch Vanilla-Menüs (bis 1.19.3; ab 1.19.4 zeichnet sie MenuScreenMixin).
		//? if >=1.19 {
		/*bus.addListener((ScreenEvent.Render.Post e) -> {
			if (dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreenPending()) dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreen(Gfx.of(e.getPoseStack()));
		});
		*///?} elif >=1.18 {
		/*bus.addListener((net.minecraftforge.client.event.ScreenEvent.DrawScreenEvent.Post e) -> {
			if (dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreenPending()) dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreen(Gfx.of(e.getPoseStack()));
		});
		*///?} elif >=1.16 {
		bus.addListener((net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post e) -> {
			if (dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreenPending()) dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreen(Gfx.of(e.getMatrixStack()));
		});
		//?} else {
		/*bus.addListener((net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post e) -> {
			if (dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreenPending()) dev.pwiepixel.cubiqclient.social.SocialHooks.afterScreen(Gfx.of());
		});
		*///?}

		bus.addListener((TickEvent.ClientTickEvent e) -> {
			// Vor der Spieler-Bewegung: Toggle-Tasten, Freelook, Treffer-Farbe; danach Menü-Tasten.
			if (e.phase == TickEvent.Phase.START) {
				pvp.tick(Minecraft.getInstance());
				// Kamera-Fahrten (Kamera-Hook nur mit Mixin, also nicht auf Forge 1.14.4).
				if (PvpFeatures.mixinFeatures()) dev.pwiepixel.cubiqclient.cinematic.CinematicHooks.tick(Minecraft.getInstance());
			} else {
				onTick(Minecraft.getInstance());
			}
		});
		//? if <1.15
		/*legacyHooks(bus);*/

		// "Config" in der Mod-Liste öffnet das Cubiq-Menü; reine Client-Mod → Server-Versionsabgleich egal.
		ModLoadingContext ctx = ModLoadingContext.get();
		//? if >=1.19 {
		/*ctx.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new CubiqMenuScreen(parent)));
		*///?} elif >=1.17 {
		/*ctx.registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
				() -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new CubiqMenuScreen(parent)));
		*///?} else
		ctx.registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY, () -> (mc, parent) -> new CubiqMenuScreen(parent));
		//? if >=1.18 && <1.19 {
		/*ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
				() -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, network) -> true));
		*///?} elif >=1.17 && <1.18 {
		/*ctx.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
				() -> new IExtensionPoint.DisplayTest(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, network) -> true));
		*///?} elif <1.17 {
		ctx.registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
				() -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (remote, network) -> true));
		//?}
		// Cubiq API (Abzeichen, Cubiq-Umhänge, Presence) + Umhang-Physik; nichts davon blockiert den Start.
		dev.pwiepixel.cubiqclient.online.OnlineHooks.init(FMLPaths.CONFIGDIR.get(), modules, Mc.modVersion(MOD_ID),
				Mc.modVersion("minecraft"), "forge", message -> LOGGER.info(message));
		// Konten: Wechsel ohne Neustart (mit Cubiq Launcher dessen Konten, sonst eigene Anmeldung je Instanz).
		dev.pwiepixel.cubiqclient.core.account.AccountManager.init(new dev.pwiepixel.cubiqclient.online.SessionSwap(
				FMLPaths.CONFIGDIR.get(), "Cubiq-Client/" + Mc.modVersion(MOD_ID) + " (Minecraft " + Mc.modVersion("minecraft") + "; forge)", message -> LOGGER.info(message)));
		// Bug melden (Cubiq-Menü): Mod-Liste, Log und Screenshots aus dem Spielordner, Senden über die Cubiq API.
		dev.pwiepixel.cubiqclient.core.bugreport.BugReports.init(FMLPaths.CONFIGDIR.get(), Mc.modVersion(MOD_ID), Mc.modVersion("minecraft"), "forge");
		// Leistung (Dynamische FPS, Culling, Partikel, Welt-Details, FPS-Boost); Leistungs-Mods übernehmen ihre Teile.
		// Forge 1.14.4 hat kein Mixin – dort nur Dynamische FPS (Bild-Event in legacyHooks).
		dev.pwiepixel.cubiqclient.perf.PerfHooks.init(modules, id -> net.minecraftforge.fml.ModList.get().isLoaded(id),
				dev.pwiepixel.cubiqclient.core.perf.PerfCompat.FORGE, Mc.modVersion("minecraft"), message -> LOGGER.info(message),
				//? if >=1.15 {
				true);
				//?} else
				/*false);*/
		// Netzwerk-Optimierung + Ping-Messung (Handler an der Client-Verbindung, siehe core.net).
		dev.pwiepixel.cubiqclient.net.NetHooks.init(modules, message -> LOGGER.info(message));
		// Cubiq Server Rules: Server sperrt/erzwingt Module (Kanäle cubiqclient:rules/hello, Logik in core.serverrules).
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.init(modules, Mc.modVersion(MOD_ID), Mc.modVersion("minecraft"),
				message -> LOGGER.info(message));
		AutoTest.installIfRequested();

		LOGGER.info("Cubiq Client {} initialisiert (Forge {}) – {} Module, Config {} ({})",
				Mc.modVersion(MOD_ID), Mc.modVersion("forge"), visibleModules.size(), status, config.file());
	}

	//? if <1.15 {
	/*private boolean handRendering;
	private double savedGamma = Double.NaN;

	/^*
	 * Forge 1.14.4 liefert noch kein Mixin mit – dort übernehmen Events die Mixin-Hooks:
	 * Zoom über das FOV-Event (die Hand wird nach RenderWorldLastEvent gezeichnet und nicht gezoomt),
	 * CPS/Mausrad über InputEvent, Fullbright über Gamma nur zwischen Frame-Beginn und Lightmap-Update.
	 * Langsamere Maus, Freelook und Treffer-Farbe gibt es hier nicht.
	 ^/
	private void legacyHooks(IEventBus bus) {
		bus.addListener((EntityViewRenderEvent.FOVModifier e) -> {
			dev.pwiepixel.cubiqclient.dev.HookStats.fov++;
			restoreGamma();
			if (handRendering) return;
			double factor = updateZoom();
			if (factor != 1.0) e.setFOV(e.getFOV() / factor);
			// Sichtfeld der Welt merken – daraus rechnet die Wegpunkt-Anzeige ihre Positionen.
			setWorldFov(e.getFOV());
		});
		bus.addListener((RenderWorldLastEvent e) -> handRendering = true);
		bus.addListener((TickEvent.RenderTickEvent e) -> {
			if (e.phase == TickEvent.Phase.START) {
				dev.pwiepixel.cubiqclient.perf.PerfHooks.beforeFrame();
				handRendering = false;
				if (fullbright() && Mc.mc().level != null && !(Mc.screen() instanceof net.minecraft.client.gui.screens.VideoSettingsScreen)) {
					savedGamma = Mc.gamma();
					Mc.setGamma(16.0);
					dev.pwiepixel.cubiqclient.dev.HookStats.lightmap++;
				}
			} else {
				restoreGamma();
			}
		});
		bus.addListener((InputEvent.MouseInputEvent e) -> {
			dev.pwiepixel.cubiqclient.dev.HookStats.press++;
			if (e.getAction() == GLFW.GLFW_PRESS) onMouseClick(e.getButton());
		});
		bus.addListener((InputEvent.MouseScrollEvent e) -> {
			dev.pwiepixel.cubiqclient.dev.HookStats.scroll++;
			if (e.getScrollDelta() != 0 && onScroll(e.getScrollDelta())) e.setCanceled(true);
		});
	}

	private void restoreGamma() {
		if (!Double.isNaN(savedGamma)) {
			Mc.setGamma(savedGamma);
			savedGamma = Double.NaN;
		}
	}
	*///?}

	/** Ersetzt den Vanilla-Titelbildschirm durch den Cubiq-Startbildschirm, solange das Modul an ist. */
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

	/** Meldungen der Clips in der Aktionsleiste. */
	private static final dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar CLIP_MESSAGES = new dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar() {
		@Override
		public void show(String text) {
			Mc.actionBar(text);
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
			Mc.actionBar(I18n.tr("toast.hudProfile", name));
			saveConfig();
		}
		while (CubiqKeys.menu.consumeClick()) {
			if (Mc.screen() == null) Mc.setScreen(new CubiqMenuScreen(null));
		}
		while (CubiqKeys.redstoneOverlay.consumeClick()) {
			modules.redstoneOverlay.toggle();
			Mc.actionBar(I18n.tr("toast.redstoneOverlay", modules.redstoneOverlay.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled")));
			saveConfig();
		}
		// Clips & Aufnahme: aufgenommen wird im Launcher, hier nur die Tasten melden.
		while (CubiqKeys.saveClip.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().saveClip();
		while (CubiqKeys.toggleRecording.consumeClick()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().toggleRecording();
		dev.pwiepixel.cubiqclient.core.clips.Clips.get().tick(modules.clips.isEnabled(), CLIP_MESSAGES);
		while (CubiqKeys.fullbright.consumeClick()) {
			Mc.actionBar(modules.toggleGamma());
			saveConfig();
		}
		while (CubiqKeys.gammaCycle.consumeClick()) {
			Mc.actionBar(modules.cycleGamma());
			saveConfig();
		}
		while (CubiqKeys.autoRefill.consumeClick()) {
			modules.autoRefill.toggle();
			Mc.actionBar(I18n.tr("toast.autoRefill", modules.autoRefill.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled")));
			saveConfig();
		}
		// Emote-Rad: Taste halten öffnet es, Loslassen (im Rad abgefragt) spielt das gezeigte Emote.
		while (CubiqKeys.emoteWheel.consumeClick()) {
			if (Mc.screen() == null && mc.player != null && visibleModules.contains(modules.emotes)
					&& dev.pwiepixel.cubiqclient.online.EmoteHooks.enabled()) {
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
		// Welt verlassen/betreten: Kartenspeicher der Minimap leeren.
		boolean hasLevel = mc.level != null;
		if (hasLevel != hadLevel) {
			hud.onWorldChange();
			hadLevel = hasLevel;
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
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			leftClicks.record(now);
			// Angreifen/Abbauen beendet das eigene Emote (wie Bewegung).
			dev.pwiepixel.cubiqclient.online.EmoteHooks.onAttack();
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightClicks.record(now);
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

	/** Module, die es auf dieser Version gibt (Menü-Reihenfolge). */
	public List<Module> visibleModules() {
		return visibleModules;
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

	public ChatFeatures chat() {
		return chat;
	}

	public dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone() {
		return redstone;
	}

	public Waypoints waypoints() {
		return waypoints;
	}

	/** Zuletzt gezeichnetes Sichtfeld der Welt (Grad) – Grundlage der Wegpunkt-Projektion. */
	public double worldFov() {
		return worldFov;
	}

	/** Aus dem FOV-Hook: das tatsächlich benutzte Sichtfeld merken. */
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
