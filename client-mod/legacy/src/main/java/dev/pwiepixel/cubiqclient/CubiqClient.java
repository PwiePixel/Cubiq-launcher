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
import dev.pwiepixel.cubiqclient.dev.HookStats;
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
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MouseHelper;
import dev.pwiepixel.cubiqclient.compat.BlockOutline;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.lwjgl.input.Mouse;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Einstiegspunkt des Cubiq Clients für Legacy-Forge (Minecraft 1.8.9 – 1.12.2, nur Client).
 * <p>
 * Kommt ohne Mixins/Coremod aus – alles läuft über Forge-Events:
 * HUD über {@link RenderGameOverlayEvent.Post}, eigenes Fadenkreuz durch Abbrechen von
 * {@link RenderGameOverlayEvent.Pre} (CROSSHAIRS), Zoom über {@link EntityViewRenderEvent.FOVModifier},
 * Freelook über {@link EntityViewRenderEvent.CameraSetup}, CPS und Zoom-Mausrad über {@link MouseEvent},
 * langsamere Maus über einen eigenen {@link MouseHelper}, Fullbright über einen kurzzeitig ersetzten
 * Gamma-Wert nur während der Lightmap-Berechnung, Cubiq-Startbildschirm über {@link GuiOpenEvent}.
 */
@Mod(modid = CubiqClient.MOD_ID, name = "Cubiq Client", version = BuildInfo.VERSION, useMetadata = true, clientSideOnly = true,
		acceptedMinecraftVersions = BuildInfo.ACCEPTED_MINECRAFT)
public final class CubiqClient {
	/** Suche in der Tastenbelegung (Steuerung); öffentlich für den Autotest. */
	public static final dev.pwiepixel.cubiqclient.menus.LegacyKeySearch keySearch = new dev.pwiepixel.cubiqclient.menus.LegacyKeySearch();
	/** Fehlerbildschirm mit Neu anmelden, Erneut verbinden …; öffentlich für den Autotest. */
	public static final dev.pwiepixel.cubiqclient.menus.LegacyDisconnect disconnect = new dev.pwiepixel.cubiqclient.menus.LegacyDisconnect();
	/** Kontowechsel (Spiel-Thread über den Client-Tick). */
	static dev.pwiepixel.cubiqclient.core.account.LegacySessionSwap accountSwap;
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
	private final PvpFeatures pvp = new PvpFeatures(modules);
	private final ChatFeatures chat = new ChatFeatures(modules);
	/** Redstone-Werkzeuge: Signalstärke, Takt-Messer, Signal-Overlay (Logik in core.redstone). */
	private final dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools redstone =
			new dev.pwiepixel.cubiqclient.core.redstone.RedstoneTools(modules);
	private long redstoneErrorLogged;
	/** Tastendruck-Erkennung für die Modul-Tasten (Wegpunkte, Text-Hotkeys). */
	private final dev.pwiepixel.cubiqclient.core.input.KeyPresses moduleKeys =
			new dev.pwiepixel.cubiqclient.core.input.KeyPresses(dev.pwiepixel.cubiqclient.compat.Keys::isDown);
	/** Module im Cubiq-Menü (ohne die, die unter Legacy-Forge nicht umsetzbar sind). */
	private final List<Module> menuModules;
	private ConfigStore config;
	private Waypoints waypoints;
	private HudManager hud;
	/** Tatsächlich benutztes senkrechtes Sichtfeld (aus dem FOV-Ereignis) – für die Wegpunkt-Projektion. */
	private double worldFov = 70;
	/** Welt des letzten Ticks – Wechsel verwirft Minimap-Speicher und Chat-Zusammenfassung. */
	private WorldClient lastWorld;
	/** Kein Schadens-Wackeln: ersetzter {@code hurtTime}-Wert, solange {@link #hurtSwapped}. */
	private int savedHurtTime;
	private boolean hurtSwapped;
	private String version = "?";
	/** Nur für den Autotest: Zoom ohne Tastendruck erzwingen. */
	private boolean forceZoom;
	/** Der nächste FOV-Aufruf gehört zur Hand (nicht zoomen). */
	private boolean handPass;
	/** Fullbright: ersetzter Gamma-Wert, solange {@link #gammaSwapped}. */
	private float savedGamma;
	private boolean gammaSwapped;
	/** Einmalig den Vanilla-Titelbildschirm zulassen ("Klassisch" auf dem Cubiq-Startbildschirm). */
	private boolean vanillaTitleOnce;

	public CubiqClient() {
		List<Module> list = new ArrayList<>(modules.registry.all());
		// Treffer-Farbe: in RendererLivingEntity fest einprogrammiert – ohne Coremod nicht änderbar.
		list.remove(modules.hitColor);
		// Niedriges Feuer braucht den Feuer-Overlay-Renderer – ohne Coremod nicht machbar.
		list.remove(modules.lowFire);
		// Titel verschieben/skalieren: Forge zeichnet Titel hier ohne Ereignis (siehe LegacyQol).
		list.remove(modules.qol.titles);
		// Schild-Position: Schilde gibt es erst ab 1.9, das Hand-Ereignis (RenderSpecificHandEvent) erst ab Forge für 1.10.2.
		//? if <1.10.2 {
		list.remove(modules.shieldPosition);
		//?}
		menuModules = Collections.unmodifiableList(list);
	}

	public static CubiqClient get() {
		return instance;
	}

	private static final boolean BENCH_VANILLA = Boolean.getBoolean("cubiqclient.bench.vanilla");

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		instance = this;
		version = event.getModMetadata().version;
		// Benchmark „Vanilla“ (-PcubiqBenchVanilla): Cubiq Client bleibt ganz aus, nur der Messablauf läuft (siehe init).
		if (BENCH_VANILLA) return;
		// Begleiter am Boden: unsichtbarer Anker im Entity-Durchgang (zeichnet Pinguine vor dem Wasser)
		dev.pwiepixel.cubiqclient.online.LegacyPets.registerRenderer();
		File file = new File(event.getModConfigurationDirectory(), "cubiqclient.json");
		// Farben des Launchers (config/cubiqclient/launcher-theme.json) – fehlt sie, gilt das Standard-Thema.
		dev.pwiepixel.cubiqclient.core.ui.Theme.loadFrom(file.getParentFile().toPath());
		dev.pwiepixel.cubiqclient.core.i18n.I18n.init(file.getParentFile().toPath());
		dev.pwiepixel.cubiqclient.core.clips.Clips.init(file.getParentFile().toPath());
		// Konten: Wechsel ohne Neustart (mit Cubiq Launcher dessen Konten, sonst eigene Anmeldung je Instanz).
		accountSwap = new dev.pwiepixel.cubiqclient.core.account.LegacySessionSwap(() -> Minecraft.getMinecraft(),
				net.minecraft.util.Session.class, () -> {
					net.minecraft.util.Session s = Minecraft.getMinecraft().getSession();
					return s == null ? null : new dev.pwiepixel.cubiqclient.core.account.SessionData(s.getPlayerID(), s.getUsername(), s.getToken(), null);
				}, () -> Mc.world() != null, file.getParentFile().toPath(), "Cubiq-Client/" + version + " (Minecraft " + Mc.version() + "; forge)", message -> LOGGER.info(message));
		dev.pwiepixel.cubiqclient.core.account.AccountManager.init(accountSwap);
		// Bug melden (Cubiq-Menü): Mod-Liste, Log und Screenshots aus dem Spielordner, Senden über die Cubiq API.
		dev.pwiepixel.cubiqclient.core.bugreport.BugReports.init(file.getParentFile().toPath(), version, Mc.version(), "forge");
		// Grafik-Modus „Schön“/„Max FPS“ (config/cubiqclient/fps-mode.json).
		dev.pwiepixel.cubiqclient.core.perf.FpsConfigMode.init(file.getParentFile().toPath());
		initWaypoints(event.getModConfigurationDirectory());
		// Karten (Minimap + Weltkarte): Kartenspeicher unter config/cubiqclient/maps.
		dev.pwiepixel.cubiqclient.core.map.MapEngine.init(modules, event.getModConfigurationDirectory().toPath());
		// Notizen je Welt (config/cubiqclient/notes): Seite im Cubiq-Menü, Taste, angeheftete Notiz im HUD.
		dev.pwiepixel.cubiqclient.core.notes.Notes.init(modules, event.getModConfigurationDirectory().toPath(), message -> LOGGER.info(message));
		// Kamera-Fahrten (config/cubiqclient-cinematic.json).
		dev.pwiepixel.cubiqclient.cinematic.LegacyCinematic.init(modules, event.getModConfigurationDirectory().toPath());
		config = new ConfigStore(file.toPath());
		ConfigStore.Status status = config.load(modules.registry);
		// Menü-Stil für Vanilla-Menüs (Pause, Serverliste, Laden, Optionen, Welten).
		dev.pwiepixel.cubiqclient.core.menus.MenuStyle.install(modules);
		if (status == ConfigStore.Status.RECOVERED) {
			LOGGER.warn("Config war beschädigt – Standardwerte geladen, Sicherung: {}", config.brokenFile());
		}
		LOGGER.info("Config {} ({})", status, config.file());
		// Cubiq API (Abzeichen, Cubiq-Umhänge, Presence) + Umhang-Physik; nichts davon blockiert den Start.
		dev.pwiepixel.cubiqclient.online.LegacyOnline.init(event.getModConfigurationDirectory().toPath(), modules,
				version, Mc.version(), message -> LOGGER.info(message));
		// Leistung (Dynamische FPS, Culling, Partikel, Welt-Details, FPS-Boost); Leistungs-Mods übernehmen ihre Teile.
		dev.pwiepixel.cubiqclient.perf.LegacyPerf.init(modules, Mc.version(), message -> LOGGER.info(message));
		// Netzwerk-Optimierung + Ping-Messung (Handler an der Client-Verbindung, siehe core.net).
		dev.pwiepixel.cubiqclient.net.NetHooks.init(modules, message -> LOGGER.info(message));
		// Cubiq Server Rules: Server sperrt/erzwingt Module (Kanäle Cubiq|Rules/Cubiq|Hello, Logik in core.serverrules).
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.init(modules, version, Mc.version(), message -> LOGGER.info(message));
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		if (BENCH_VANILLA) {
			LOGGER.info("Cubiq Client aus (Benchmark Vanilla)");
			AutoTest.installIfRequested();
			return;
		}
		CubiqKeys.register();
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
		installSocialOverlay();
		// Komfort-/PvP-Paket (Erwähnungen, Filter, Auto-Reconnect, Warnungen, Zähler, Streamer-Modus …).
		dev.pwiepixel.cubiqclient.qol.LegacyQol.init(modules);
		// Komfort-Paket 2: Tooltips, Server-Profile, Panorama (eigene Kamera-Drehung).
		dev.pwiepixel.cubiqclient.comfort.LegacyComfort.init(modules);
		// Screenshot-Werkzeuge: Vorschau nach F2, Chat-Aktionen, Bild-Editor, Essential-Vorschau ersetzen.
		dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.install(modules);
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.init(modules);
		hud = new HudManager(modules);
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(dev.pwiepixel.cubiqclient.qol.LegacyQol.get());
		MinecraftForge.EVENT_BUS.register(dev.pwiepixel.cubiqclient.comfort.LegacyComfort.get());
		MinecraftForge.EVENT_BUS.register(new dev.pwiepixel.cubiqclient.menus.LegacyMenus());
		MinecraftForge.EVENT_BUS.register(keySearch);
		MinecraftForge.EVENT_BUS.register(disconnect);
		MinecraftForge.EVENT_BUS.register(new dev.pwiepixel.cubiqclient.online.LegacyOnline.NameTags());
		// Creator-Programm: Fan-Symbole über dem Kopf und in der Tabliste.
		MinecraftForge.EVENT_BUS.register(new dev.pwiepixel.cubiqclient.online.LegacyFans());
		// Begleiter am Boden (Pinguine): Zeichnen nach der Welt
		MinecraftForge.EVENT_BUS.register(new dev.pwiepixel.cubiqclient.online.LegacyPets());
		// Kamera-Fahrten: Tasten, Hilfs-Kamera je Bild, keine Hand.
		MinecraftForge.EVENT_BUS.register(dev.pwiepixel.cubiqclient.cinematic.LegacyCinematic.get());
		MinecraftForge.EVENT_BUS.register(dev.pwiepixel.cubiqclient.perf.LegacyPerf.get());
		MinecraftForge.EVENT_BUS.register(dev.pwiepixel.cubiqclient.net.NetHooks.get());
		// Schild-Position (ab 1.10.2): Schild-Hand der 1. Person selbst zeichnen.
		dev.pwiepixel.cubiqclient.render.LegacyShield.install(modules);
		AutoTest.installIfRequested();
		// Legacy-Forge hat kein "Client stoppt"-Ereignis – beim Beenden trotzdem speichern.
		Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig, "Cubiq Client config save"));
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (dev.pwiepixel.cubiqclient.core.map.MapEngine.get() != null) dev.pwiepixel.cubiqclient.core.map.MapEngine.get().shutdown();
			if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().save();
		}, "Cubiq Client map save"));
	}

	/** Sozial-Benachrichtigungen (Toasts): Ton, Vollbild, Name der Schnelltaste. */
	private void installSocialOverlay() {
		try {
			dev.pwiepixel.cubiqclient.core.social.SocialOverlay.install(new dev.pwiepixel.cubiqclient.core.social.SocialPlatform() {
				@Override
				public void playToastSound() {
					try {
						Mc.toastSound();
					} catch (RuntimeException ignored) {
						// ohne Ton weiter
					}
				}

				@Override
				public boolean fullscreen() {
					return Mc.fullscreen();
				}

				@Override
				public String quickReplyKey() {
					if (CubiqKeys.quickReply == null || CubiqKeys.quickReply.getKeyCode() == org.lwjgl.input.Keyboard.KEY_NONE) return null;
					return net.minecraft.client.settings.GameSettings.getKeyDisplayString(CubiqKeys.quickReply.getKeyCode());
				}

				@Override
				public int vanillaToastBottom() {
					return vanillaNoticeBottom();
				}

				@Override
				public boolean openSocial() {
					try {
						if (!dev.pwiepixel.cubiqclient.screen.MenuScreens.friendsAvailable()) return false;
						Mc.setScreen(dev.pwiepixel.cubiqclient.screen.MenuScreens.social(null));
						return true;
					} catch (RuntimeException e) {
						return false;
					}
				}
			}, modules);
		} catch (RuntimeException e) {
			LOGGER.warn("Sozial-Benachrichtigungen nicht verfügbar: " + e);
		}
	}

	private static final dev.pwiepixel.cubiqclient.core.social.VanillaToastProbe TOAST_PROBE =
			new dev.pwiepixel.cubiqclient.core.social.VanillaToastProbe();
	private static final dev.pwiepixel.cubiqclient.core.social.VanillaToastProbe.Achievement ACHIEVEMENT_PROBE =
			new dev.pwiepixel.cubiqclient.core.social.VanillaToastProbe.Achievement();

	/**
	 * Unterkante der Vanilla-Meldungen oben rechts (Sozial-Toasts weichen darunter aus): bis 1.11.2 das Erfolgs-Fenster
	 * (fährt senkrecht herein/hinaus, auch der Tutorial-Hinweis „Inventar öffnen“), ab 1.12 die Toasts.
	 */
	static int vanillaNoticeBottom() {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			//? if >=1.12 {
			/*return TOAST_PROBE.bottom(mc.getToastGui());
			*///?} else {
			if (Mc.player() == null) return 0;
			return ACHIEVEMENT_PROBE.bottom(mc.guiAchievement, Minecraft.getSystemTime());
			//?}
		} catch (RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** Wegpunkt-Datei neben der Config (erst hier, weil das Verzeichnis aus preInit kommt). */
	private void initWaypoints(File configDir) {
		waypoints = new Waypoints(modules, new File(configDir, "cubiqclient-waypoints.json").toPath());
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		if (BENCH_VANILLA) return;
		Minecraft mc = Minecraft.getMinecraft();
		// Nur den Vanilla-MouseHelper ersetzen – hat ein anderer Mod schon einen eigenen, bleibt der.
		boolean ownMouse = mc.mouseHelper != null && mc.mouseHelper.getClass() == MouseHelper.class;
		if (ownMouse) mc.mouseHelper = new ZoomMouseHelper(this);
		else LOGGER.warn("MouseHelper ist bereits ersetzt ({}) – Zoom verlangsamt die Maus nicht, kein Freelook", mc.mouseHelper);
		LOGGER.info("Cubiq Client {} initialisiert (Minecraft {}) – {} Module, Forge-Events registriert, Maus-Hook {}",
				version, Mc.version(), modules.registry.all().size(), ownMouse ? "aktiv" : "inaktiv");
	}

	// --- Forge-Events ---

	/** Meldungen der Clips in der Aktionsleiste. */
	private static final dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar CLIP_MESSAGES = new dev.pwiepixel.cubiqclient.core.clips.Clips.ActionBar() {
		@Override
		public void show(String text) {
			Mc.actionBar(text);
		}

		@Override
		public String keyLabel(boolean record) {
			return net.minecraft.client.settings.GameSettings.getKeyDisplayString(
					(record ? CubiqKeys.toggleRecording : CubiqKeys.saveClip).getKeyCode());
		}
	};

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		if (accountSwap != null) accountSwap.drain();
		dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.tick();
		Minecraft mc = Minecraft.getMinecraft();
		dev.pwiepixel.cubiqclient.ui.BorderlessDisplay.tick(mc);
		dev.pwiepixel.cubiqclient.halloween.LegacyHalloween.tick(mc);
		// Touch-Modus (mobile Engine): feste Overlay-Tasten + Bildschirmtastatur; sonst nichts.
		dev.pwiepixel.cubiqclient.touch.TouchHooks.tick();
		migrateKeys(mc);
		while (CubiqKeys.hudProfile.isPressed()) {
			Mc.actionBar(I18n.tr("toast.hudProfile", modules.profiles.cycle()));
			saveConfig();
		}
		while (CubiqKeys.menu.isPressed()) {
			if (mc.currentScreen == null) mc.displayGuiScreen(new CubiqMenuScreen(null));
		}
		while (CubiqKeys.redstoneOverlay.isPressed()) {
			modules.redstoneOverlay.toggle();
			Mc.actionBar(I18n.tr("toast.redstoneOverlay", modules.redstoneOverlay.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled")));
			saveConfig();
		}
		// Clips & Aufnahme: aufgenommen wird im Launcher, hier nur die Tasten melden.
		while (CubiqKeys.saveClip.isPressed()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().saveClip();
		while (CubiqKeys.toggleRecording.isPressed()) dev.pwiepixel.cubiqclient.core.clips.Clips.get().toggleRecording();
		dev.pwiepixel.cubiqclient.core.clips.Clips.get().tick(modules.clips.isEnabled(), CLIP_MESSAGES);
		while (CubiqKeys.fullbright.isPressed()) {
			Mc.actionBar(modules.toggleGamma());
			saveConfig();
		}
		while (CubiqKeys.gammaCycle.isPressed()) {
			Mc.actionBar(modules.cycleGamma());
			saveConfig();
		}
		while (CubiqKeys.autoRefill.isPressed()) {
			modules.autoRefill.toggle();
			Mc.actionBar(I18n.tr("toast.autoRefill", modules.autoRefill.isEnabled() ? I18n.tr("common.enabled") : I18n.tr("common.disabled")));
			saveConfig();
		}
		// Emote-Rad: Taste halten öffnet es, Loslassen (im Rad abgefragt) spielt das gezeigte Emote.
		while (CubiqKeys.emoteWheel.isPressed()) {
			if (mc.currentScreen == null && Mc.player() != null && dev.pwiepixel.cubiqclient.online.LegacyEmotes.enabled()) {
				mc.displayGuiScreen(new dev.pwiepixel.cubiqclient.screen.EmoteWheelScreen());
			}
		}
		// Weltkarte (M)
		while (CubiqKeys.worldMap.isPressed()) {
			if (mc.currentScreen == null && Mc.player() != null && modules.worldMap.isEnabled()) {
				dev.pwiepixel.cubiqclient.screen.WorldMapScreen screen = dev.pwiepixel.cubiqclient.screen.WorldMapScreen.create();
				if (screen != null) mc.displayGuiScreen(screen);
			}
		}
		if (modules.keyDefaults.needsWorldMapKeyCheck() && mc.gameSettings != null) {
			modules.keyDefaults.markWorldMapKeyChecked();
			if (CubiqKeys.resolveWorldMapConflict()) LOGGER.info("Weltkarten-Taste M war doppelt belegt – freigegeben");
			saveConfig();
		}
		// Garderobe (Taste standardmäßig unbelegt)
		while (CubiqKeys.wardrobe.isPressed()) {
			if (mc.currentScreen == null && dev.pwiepixel.cubiqclient.screen.WardrobeScreen.available()) {
				mc.displayGuiScreen(dev.pwiepixel.cubiqclient.screen.WardrobeScreen.create(null));
			}
		}
		// Notizen (Taste standardmäßig unbelegt): Cubiq-Menü direkt auf der Notiz-Seite der aktuellen Welt.
		while (CubiqKeys.notes.isPressed()) {
			if (mc.currentScreen == null && modules.notes.notes.isEnabled()) {
				dev.pwiepixel.cubiqclient.core.notes.Notes.requestOpen();
				mc.displayGuiScreen(new CubiqMenuScreen(null));
			}
		}
		if (dev.pwiepixel.cubiqclient.core.notes.Notes.get() != null) dev.pwiepixel.cubiqclient.core.notes.Notes.get().tick(System.currentTimeMillis());
		// Sozial-Bildschirm (Taste standardmäßig unbelegt) und Schnelltaste zum neuesten Toast (Y).
		while (CubiqKeys.social.isPressed()) {
			if (mc.currentScreen == null && dev.pwiepixel.cubiqclient.screen.MenuScreens.friendsAvailable()) {
				mc.displayGuiScreen(dev.pwiepixel.cubiqclient.screen.MenuScreens.social(null));
			}
		}
		while (CubiqKeys.quickReply.isPressed()) {
			if (mc.currentScreen != null) continue;
			try {
				dev.pwiepixel.cubiqclient.core.social.SocialOverlay.QuickAction action =
						dev.pwiepixel.cubiqclient.core.social.SocialOverlay.takeQuickAction();
				if (action != null) mc.displayGuiScreen(dev.pwiepixel.cubiqclient.screen.MenuScreens.socialAction(action, null));
			} catch (RuntimeException e) {
				LOGGER.warn("Schnellantwort: " + e);
			}
		}
		// Wegpunkt- und Hotkey-Tasten gehören den Modulen (Tastenbelegung im Cubiq-Menü).
		if (mc.currentScreen == null) {
			if (moduleKeys.pressed(modules.waypointAddKey) && Mc.player() != null && modules.waypoints.isEnabled()) {
				mc.displayGuiScreen(new WaypointEditScreen(null, null));
			}
			if (moduleKeys.pressed(modules.waypointListKey) && modules.waypoints.isEnabled()) {
				mc.displayGuiScreen(new WaypointListScreen(null));
			}
			for (int i = 0; i < modules.hotkeyKeys.length; i++) {
				if (moduleKeys.pressed(modules.hotkeyKeys[i])) chat.onHotkey(i);
			}
		} else {
			moduleKeys.releaseAll();
		}
		checkWorldChange();
		pvp.tick(mc);
		dev.pwiepixel.cubiqclient.feature.AutoRefillHooks.tick();
		chat.tick(mc);
		waypoints.tick();
		hud.tick();
		tickRedstone();
		dev.pwiepixel.cubiqclient.online.LegacyOnline.tick(mc);
		dev.pwiepixel.cubiqclient.online.LegacyEmotes.tick(mc);
		dev.pwiepixel.cubiqclient.net.ServerRulesNet.tick(mc);
	}

	/** Weltwechsel erkennen (Legacy-Forge hat dafür kein eigenes Ereignis). */
	private void checkWorldChange() {
		WorldClient world = Mc.world();
		if (world == lastWorld) return;
		lastWorld = world;
		hud.onWorldChange();
		chat.onWorldChange();
		if (world == null) waypoints.onDisconnect();
	}

	/** Reichweite und Combo: jeder Schlag des eigenen Spielers (reine Anzeige). */
	@SubscribeEvent
	public void onAttackEntity(AttackEntityEvent event) {
		EntityPlayer player = Mc.player();
		//? if >=1.9 {
		/*if (event.getEntityPlayer() == player) pvp.onAttack(player, event.getTarget());
		*///?} else
		if (event.entityPlayer == player) pvp.onAttack(player, event.target);
	}

	/** Chat-Verbesserungen (zuletzt, damit andere Mods die Nachricht unverändert sehen). */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onChatReceived(ClientChatReceivedEvent event) {
		if (event.isCanceled()) return;
		chat.onChatReceived(event);
	}

	/** Eigene Farbe/Stärke für den Rahmen um den anvisierten Block. */
	@SubscribeEvent
	public void onBlockHighlight(DrawBlockHighlightEvent event) {
		if (!modules.blockOutline.isEnabled()) return;
		float alpha = (float) (modules.blockOutlineOpacity.get() / 100.0);
		if (BlockOutline.draw(event, modules.blockOutlineColor.rgb(), alpha, modules.blockOutlineWidth.getFloat())) {
			event.setCanceled(true);
		}
	}

	/** Strg+Klick im Chat kopiert die angeklickte Zeile. */
	@SubscribeEvent
	public void onGuiMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
		// Screenshot-Vorschau oben rechts: Klick in jedem Bildschirm.
		if (dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.onMouse(Mc.eventGui(event))) {
			event.setCanceled(true);
			return;
		}
		if (!(Mc.eventGui(event) instanceof GuiChat)) return;
		if (!Mouse.getEventButtonState()) return;
		// [Bearbeiten] [Link kopieren] [Bild kopieren] hinter „Screenshot gespeichert“.
		if (Mouse.getEventButton() == 0 && !GuiScreen.isShiftKeyDown() && !GuiScreen.isCtrlKeyDown()
				&& dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.onChatClick()) {
			event.setCanceled(true);
			return;
		}
		// Klick auf markierte Koordinaten → „Als Wegpunkt speichern“ (Umschalt fügt wie in Vanilla nur den Text ein).
		if (Mouse.getEventButton() == 0 && !GuiScreen.isShiftKeyDown() && !GuiScreen.isCtrlKeyDown()
				&& chat.onLinkClick(Mouse.getX(), Mouse.getY())) {
			event.setCanceled(true);
			return;
		}
		// Strg+Linksklick und/oder Rechtsklick (Einstellung „Zeile kopieren mit“).
		if (!dev.pwiepixel.cubiqclient.qol.LegacyQol.copyAllowed(Mouse.getEventButton(), GuiScreen.isCtrlKeyDown())) return;
		if (chat.onChatClick(Mouse.getX(), Mouse.getY())) event.setCanceled(true);
	}

	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (event.phase == TickEvent.Phase.START) {
			handPass = false;
			dev.pwiepixel.cubiqclient.render.ColorPass.frameStart();
			updateZoom(mc);
			// Fullbright: Gamma nur bis zur Lightmap-Berechnung ersetzen (siehe restoreGamma).
			// In den Video-Einstellungen nicht – dort zeigt/ändert der Regler den echten Wert.
			if (modules.fullbright.isEnabled() && Mc.world() != null && !(mc.currentScreen instanceof GuiVideoSettings)) {
				savedGamma = mc.gameSettings.gammaSetting;
				mc.gameSettings.gammaSetting = (float) modules.gammaValue();
				gammaSwapped = true;
				HookStats.lightmap++;
			}
			// Kein Schadens-Wackeln: hurtTime nur für dieses Bild auf 0 (die Kamera kippt dann nicht).
			if (modules.noHurtCam.isEnabled() && Mc.player() != null && Mc.player().hurtTime > 0) {
				savedHurtTime = Mc.player().hurtTime;
				Mc.player().hurtTime = 0;
				hurtSwapped = true;
			}
		} else {
			restoreGamma(mc);
			restoreHurtTime();
			// Farben bei ausgeblendeter Oberfläche (F1): dann gibt es kein Overlay-Ereignis.
			if (Mc.world() != null && Mc.hudHidden() && mc.currentScreen == null) {
				dev.pwiepixel.cubiqclient.render.ColorPass.afterLevel(event.renderTickTime);
			}
		}
	}

	private void restoreHurtTime() {
		if (!hurtSwapped) return;
		hurtSwapped = false;
		if (Mc.player() != null) Mc.player().hurtTime = savedHurtTime;
	}

	/**
	 * Lightmap ist berechnet (erstes Ereignis nach {@code updateLightmap} in {@code renderWorld}) →
	 * echten Gamma-Wert sofort zurück. So sieht nichts anderes (Menüs, Speichern der Optionen) je 16.0.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void onFogColors(EntityViewRenderEvent.FogColors event) {
		restoreGamma(Minecraft.getMinecraft());
	}

	@SubscribeEvent
	public void onFov(EntityViewRenderEvent.FOVModifier event) {
		HookStats.fov++;
		restoreGamma(Minecraft.getMinecraft());
		if (handPass) {
			// Hand nicht mitzoomen.
			handPass = false;
			return;
		}
		// Kamera-Fahrt: Sichtfeld der Fahrt (ohne Zoom).
		float cinematicFov = dev.pwiepixel.cubiqclient.cinematic.LegacyCinematic.fov();
		if (cinematicFov > 0) {
			event.setFOV(cinematicFov);
			return;
		}
		double factor = zoom.factor();
		if (factor != 1.0) event.setFOV((float) (event.getFOV() / factor));
		// Tatsächlich benutztes Sichtfeld merken – damit rechnet die Wegpunkt-Projektion.
		float fov = event.getFOV();
		if (fov > 1 && fov < 180) worldFov = fov;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onRenderHand(RenderHandEvent event) {
		// Direkt danach fragt renderHand() das Sichtfeld ab.
		handPass = true;
	}

	/** Freelook: Kamera mit eigenen Blickwinkeln um die Spielfigur drehen. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onCameraSetup(EntityViewRenderEvent.CameraSetup event) {
		if (!pvp.freelook().active()) return;
		HookStats.camera++;
		// Vanilla übergibt yaw + 180° (Blickrichtung → Kameradrehung).
		Mc.setCamera(event, pvp.freelook().yaw() + 180.0F, pvp.freelook().pitch());
	}

	@SubscribeEvent
	public void onMouse(MouseEvent event) {
		HookStats.mouseEvent++;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.currentScreen != null) return;
		int button = Mc.mouseButton(event);
		if (Mc.mouseButtonDown(event)) {
			long now = System.currentTimeMillis();
			if (button == 0) {
				leftClicks.record(now);
				// Angreifen/Abbauen beendet das eigene Emote (wie Bewegung).
				dev.pwiepixel.cubiqclient.online.LegacyEmotes.onAttack();
			} else if (button == 1) {
				rightClicks.record(now);
				// Rechtsklick auf den eigenen Begleiter am Boden: streicheln/füttern – Minecraft bekommt den Klick nicht
				// (nichts wird gegessen, nichts geht an den Server).
				if (dev.pwiepixel.cubiqclient.online.LegacyPets.onUse()) {
					event.setCanceled(true);
					return;
				}
			}
		}
		// Reines Mausrad-Ereignis während des Zooms → Zoomstufe statt Hotbar.
		int wheel = Mc.mouseWheel(event);
		if (button == -1 && wheel != 0 && zoom.isActive() && modules.zoomScroll.get()) {
			HookStats.scroll++;
			zoom.scroll(wheel);
			event.setCanceled(true);
		} else if (button == -1 && wheel != 0 && dev.pwiepixel.cubiqclient.cinematic.LegacyCinematic.scroll(wheel > 0 ? 1 : -1)) {
			// Freie Kamera: Mausrad ändert ihre Geschwindigkeit (statt der Hotbar).
			event.setCanceled(true);
		}
	}

	/** Farben: Welt und Hand sind gezeichnet, HUD und Menüs kommen erst danach. */
	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public void onOverlayColors(RenderGameOverlayEvent.Pre event) {
		if (Mc.overlayType(event) != RenderGameOverlayEvent.ElementType.ALL) return;
		dev.pwiepixel.cubiqclient.render.ColorPass.afterLevel(Mc.partialTicks(event));
	}

	/** Vanilla-Fadenkreuz ausblenden, solange das eigene aktiv ist. */
	@SubscribeEvent
	public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
		if (Mc.overlayType(event) == RenderGameOverlayEvent.ElementType.CROSSHAIRS && hud.crosshair().replacesVanilla()) {
			HookStats.crosshair++;
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onOverlay(RenderGameOverlayEvent.Post event) {
		if (Mc.overlayType(event) != RenderGameOverlayEvent.ElementType.ALL) return;
		HookStats.hud++;
		ScaledResolution res = Mc.resolution(event);
		hud.render(Gfx.of(res.getScaledWidth(), res.getScaledHeight()), Mc.partialTicks(event));
		// Halloween: Spuk-Effekte, Jumpscares, Toasts „Kürbis gefunden!“.
		dev.pwiepixel.cubiqclient.halloween.LegacyHalloween.hud(res.getScaledWidth(), res.getScaledHeight(), Mc.partialTicks(event));
		dev.pwiepixel.cubiqclient.chatheads.ChatHeads.render(res.getScaledHeight());
		// Sozial-Toasts im Spiel (über Bildschirmen zeichnet sie onScreenDrawn).
		if (Mc.screen() == null && !Mc.hudHidden()) drawToasts(res.getScaledWidth(), res.getScaledHeight());
		dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.hud(res.getScaledWidth(), res.getScaledHeight());
		// Welt-Hosting: rotes Abzeichen „Öffentlicher Link aktiv“.
		if (!Mc.hudHidden()) drawHostingBadge(res.getScaledWidth(), res.getScaledHeight(), -1, -1);
	}

	/** Sozial-Toasts über jedem Bildschirm (nach allem anderen, auch nach dem Redstone-Menü-Stil). */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onScreenDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
		GuiScreen s = Mc.eventGui(event);
		if (s == null) return;
		if (s instanceof net.minecraft.client.gui.GuiIngameMenu && dev.pwiepixel.cubiqclient.menus.LegacyMenus.linkBadgeX() >= 0) {
			drawHostingBadge(s.width, s.height, dev.pwiepixel.cubiqclient.menus.LegacyMenus.linkBadgeX(),
					dev.pwiepixel.cubiqclient.menus.LegacyMenus.linkBadgeY());
		}
		drawToasts(s.width, s.height);
		dev.pwiepixel.cubiqclient.halloween.LegacyHalloween.overScreen(s);
		dev.pwiepixel.cubiqclient.screenshot.LegacyScreenshots.afterScreen(s);
	}

	/** Abzeichen „Öffentlicher Link aktiv“: oben mittig (x &lt; 0) oder an fester Stelle (Pausemenü). */
	private static void drawHostingBadge(int width, int height, int x, int y) {
		try {
			if (!dev.pwiepixel.cubiqclient.core.hosting.HostingOverlay.linkActive()) return;
			Gfx g = Gfx.of(width, height);
			dev.pwiepixel.cubiqclient.ui.GfxCanvas c = dev.pwiepixel.cubiqclient.ui.GfxCanvas.of(g, Mc.font());
			c.push();
			c.raise(420f);
			if (x < 0) dev.pwiepixel.cubiqclient.core.hosting.HostingOverlay.renderHud(c, width, height);
			else dev.pwiepixel.cubiqclient.core.hosting.HostingOverlay.renderAt(c, x, y);
			c.pop();
		} catch (RuntimeException ignored) {
			// darf nie stören
		}
	}

	private static void drawToasts(int width, int height) {
		try {
			if (!dev.pwiepixel.cubiqclient.core.social.SocialOverlay.active()) return;
			Gfx g = Gfx.of(width, height);
			dev.pwiepixel.cubiqclient.ui.GfxCanvas c = dev.pwiepixel.cubiqclient.ui.GfxCanvas.of(g, Mc.font());
			c.push();
			c.raise(400f);
			dev.pwiepixel.cubiqclient.core.social.SocialOverlay.render(c, width, height);
			c.pop();
		} catch (RuntimeException ignored) {
			// Benachrichtigungen dürfen das Spiel nie stören.
		}
	}

	/** Ersetzt den Vanilla-Titelbildschirm durch den Cubiq-Startbildschirm (Modul "Startbildschirm"). */
	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent event) {
		GuiScreen screen = Mc.openedGui(event);
		if (screen == null || screen.getClass() != GuiMainMenu.class) return;
		if (vanillaTitleOnce) {
			vanillaTitleOnce = false;
			return;
		}
		if (modules.titleScreen.isEnabled()) Mc.setOpenedGui(event, new CubiqTitleScreen());
	}

	/** Öffnet einmalig den Vanilla-Titelbildschirm. */
	public void openVanillaTitle() {
		vanillaTitleOnce = true;
		Mc.setScreen(new GuiMainMenu());
	}

	// --- Logik ---

	private void updateZoom(Minecraft mc) {
		boolean wanted = modules.zoom.isEnabled()
				&& (forceZoom || (CubiqKeys.zoom.isKeyDown() && mc.currentScreen == null));
		// Kein Fernrohr vor 1.17; die filmische Kamera gilt auf Wunsch nur, solange gezoomt wird.
		zoom.frame(wanted, false, modules.zoomFactor.get(), modules.zoomSmooth.get(), System.nanoTime());
		boolean smooth = zoomCinematic.update(mc.gameSettings.smoothCamera, zoom.isActive() && modules.zoomCinematic.get());
		if (smooth != mc.gameSettings.smoothCamera) mc.gameSettings.smoothCamera = smooth;
	}

	private void restoreGamma(Minecraft mc) {
		if (!gammaSwapped) return;
		gammaSwapped = false;
		mc.gameSettings.gammaSetting = savedGamma;
	}

	/** Maus-Divisor während des Zooms (1 = unverändert). */
	public double mouseDivisor() {
		return zoom.mouseDivisor(modules.zoom.isEnabled() && modules.zoomSlowMouse.get());
	}

	/**
	 * Einmalige Umstellung alter Standard-Tasten: Zoom lag auf C, was ab Minecraft 1.12 mit
	 * "Schnellleiste speichern" kollidiert. Selbst belegte Tasten bleiben unangetastet.
	 */
	/** Redstone-Werkzeuge; ein Fehler darf nie das Spiel stören (höchstens einmal je Minute geloggt). */
	private void tickRedstone() {
		try {
			dev.pwiepixel.cubiqclient.compat.RedstoneProbe.tick(redstone);
		} catch (RuntimeException e) {
			redstone.reset();
			long now = System.currentTimeMillis();
			if (now - redstoneErrorLogged > 60000) {
				redstoneErrorLogged = now;
				LOGGER.warn("Redstone-Werkzeuge: " + e);
			}
		}
	}

	private void migrateKeys(Minecraft mc) {
		if (!modules.keyDefaults.needsZoomKeyMigration() || mc.gameSettings == null) return;
		if (CubiqKeys.migrateZoomKey()) {
			mc.gameSettings.saveOptions();
			LOGGER.info("Zoom-Taste von C auf V umgestellt (C ist ab 1.12 'Schnellleiste speichern')");
		}
		modules.keyDefaults.markMigrated();
		saveConfig();
	}

	/** Speichert die Einstellungen (Fehler nur loggen). */
	public void saveConfig() {
		if (config == null) return;
		try {
			config.saveLater(modules.registry);
		} catch (IOException e) {
			LOGGER.error("Config konnte nicht gespeichert werden: " + config.file(), e);
		}
	}

	// --- Zugriff ---

	public String version() {
		return version;
	}

	public CubiqModules modules() {
		return modules;
	}

	public List<Module> menuModules() {
		return menuModules;
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

	public ZoomState zoom() {
		return zoom;
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

	/** Tatsächlich benutztes senkrechtes Sichtfeld (Grad). */
	public double worldFov() {
		return worldFov;
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
