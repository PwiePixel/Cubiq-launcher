package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.config.CubiqConfig;
import dev.pwiepixel.cubiqclient.core.hud.Crosshair;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.pvp.ComboTracker;
import dev.pwiepixel.cubiqclient.screen.CrosshairEditorScreen;
import dev.pwiepixel.cubiqclient.screen.HudEditorScreen;
import dev.pwiepixel.cubiqclient.screen.PackScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.WaypointListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Entwickler-Selbsttest, nur aktiv mit {@code -Dcubiqclient.autotest=true}
 * ({@code ./gradlew :<minecraft>:runClient -PcubiqAutotest}): Cubiq-Startbildschirm und Menü, Testwelt laden,
 * Screenshots von HUD (inkl. Rüstung/Effekte/Koordinaten/…, eigenes Fadenkreuz), Zoom, Nacht ohne/mit
 * Fullbright, Freelook, Menü, Fadenkreuz-Editor, Resourcepacks, HUD-Profilen, HUD-Editor sowie
 * Wegpunkte + Minimap + Chat-Zeitstempel/Zusammenfassung, Wegpunkt-Liste und dem Menü mit
 * Text-Einstellungen, dann beenden.
 * Screenshots landen in {@code run/forge-<minecraft>/screenshots/cubiqclient-<minecraft>-*.png}.
 */
public final class AutoTest {
	private final String mcVersion = Mc.version();
	private final String world = "cubiq-autotest-" + mcVersion;

	private int step;
	private int wait;
	private int titlePhase;
	private boolean figureTurned;
	private int titleScaleBefore;
	private int reopenCount;
	/** Bildschirm, den der Test gerade erwartet (null = Spiel ohne Menü). */
	private Class<? extends GuiScreen> expected;
	private GuiScreen expectedInstance;
	/** Modul-Zustand vor dem Test – wird am Ende wiederhergestellt (die Config bleibt sauber). */
	private CubiqConfig before;

	private final CapeTest capeTest = new CapeTest();
	private final CreatorTest creatorTest = new CreatorTest();
	private final CinematicTest cinematicTest = new CinematicTest();
	private final FreecamTest freecamTest = new FreecamTest();
	private final EmoteTest emoteTest = new EmoteTest();
	private final RedstoneTest redstoneTest = new RedstoneTest();
	private final PerfTest perfTest = new PerfTest();
	private final ClipsTest clipsTest = new ClipsTest();
	private final CapeColorTest capeColorTest = new CapeColorTest();

	private AutoTest() {
	}

	public static void installIfRequested() {
		if (!Boolean.getBoolean("cubiqclient.autotest")) return;
		// -PcubiqAutotestOnly=realbench: nur der realistische Benchmark (eigener Ablauf, ohne Menü-Tests)
		if (RealBenchRunner.installIfRequested()) return;
		CubiqClient.LOGGER.info("[Autotest] aktiv");
		if ("accounts".equals(System.getProperty("cubiqclient.autotest.only"))) {
			AccountsTest.install();
			return;
		}
		if ("intro".equals(System.getProperty("cubiqclient.autotest.only"))) {
			IntroTest.install();
			return;
		}
		if ("wardrobe".equals(System.getProperty("cubiqclient.autotest.only"))) {
			WardrobeTest.install();
			return;
		}
		// -PcubiqAutotestOnly=qol: Komfort & PvP (Chat, Zaehler, Warnungen, Hitmarker, Scoreboard, Streamer, Reconnect)
		if ("qol".equals(System.getProperty("cubiqclient.autotest.only"))) {
			QolTest.install();
			return;
		}
		// -PcubiqAutotestOnly=comfort: Tooltips, Server-Profil, Panorama (Komfort-Paket 2)
		if ("comfort".equals(System.getProperty("cubiqclient.autotest.only"))) {
			ComfortTest.install();
			return;
		}
		// -PcubiqAutotestOnly=circuits: Schaltungs-Bibliothek (Liste, Vorschau, Geisterblöcke, Platzieren)
		if ("circuits".equals(System.getProperty("cubiqclient.autotest.only"))) {
			CircuitTest.install();
			return;
		}
		// -PcubiqAutotestOnly=disconnect: Fehlerbildschirm (Neu anmelden, kopieren, Server-Status)
		if ("disconnect".equals(System.getProperty("cubiqclient.autotest.only"))) {
			DisconnectTest.install();
			return;
		}
		// -PcubiqAutotestOnly=screenshots: Screenshot-Werkzeuge (Vorschau, Chat, Editor, Favoriten)
		if ("screenshots".equals(System.getProperty("cubiqclient.autotest.only"))) {
			ScreenshotTest.install();
			return;
		}
		// -PcubiqAutotestOnly=notes: Notizen je Welt (HUD, Liste, Lesen, Bearbeiten, Suche, Koordinaten, Wegpunkt, Karte)
		if ("notes".equals(System.getProperty("cubiqclient.autotest.only"))) {
			NotesTest.install();
			return;
		}
		// -PcubiqAutotestOnly=keysearch: Suche in der Tastenbelegung (Steuerung)
		if ("keysearch".equals(System.getProperty("cubiqclient.autotest.only"))) {
			KeySearchTest.install();
			return;
		}
		if ("connect".equals(System.getProperty("cubiqclient.autotest.only"))) {
			ConnectTest.install();
			return;
		}
		// -PcubiqAutotestOnly=serverrules: Cubiq Server Rules gegen einen echten Server mit Plugin (-PcubiqAutotestServer, RCON)
		if ("serverrules".equals(System.getProperty("cubiqclient.autotest.only"))) {
			ServerRulesTest.install();
			return;
		}
		if ("menus".equals(System.getProperty("cubiqclient.autotest.only"))) {
			MenusTest.install();
			return;
		}
		// -PcubiqAutotestOnly=halloween: Halloween 2026 (Startbildschirm, Seite Halloween, Mondlauf, Spuk-Effekte, Jumpscares)
		if ("halloween".equals(System.getProperty("cubiqclient.autotest.only"))) {
			HalloweenTest.install();
			return;
		}
		// -PcubiqAutotestOnly=winter: Design „Polarlicht“ (Startbildschirm Nacht/Morgen, Cubiq-Menü, Serverliste, Laden, Pause)
		if ("winter".equals(System.getProperty("cubiqclient.autotest.only"))) {
			WinterTest.install();
			return;
		}
		// -PcubiqAutotestOnly=fox: Design „Fuchsbau“ (Startbildschirm, Einführung, Cubiq-Menü, Laden, Pause)
		if ("fox".equals(System.getProperty("cubiqclient.autotest.only"))) {
			FoxDenTest.install();
			return;
		}
		// -PcubiqAutotestOnly=addserver: Formulare der Serverliste mit breiter GUI und fremdem Eck-Knopf
		if ("addserver".equals(System.getProperty("cubiqclient.autotest.only"))) {
			MenusTest.installAddServer();
			return;
		}
		if ("maps".equals(System.getProperty("cubiqclient.autotest.only"))) {
			MapsTest.install();
			return;
		}
		if ("social".equals(System.getProperty("cubiqclient.autotest.only"))) {
			SocialTest.install();
			return;
		}
		// -PcubiqAutotestOnly=hosting: Welt-Hosting mit zwei Spielen (Host/Gast, siehe HostingTest)
		if ("hosting".equals(System.getProperty("cubiqclient.autotest.only"))) {
			HostingTest.install();
			return;
		}
		// -PcubiqAutotestOnly=fastconnect: Schnell verbinden – schnellste Adresse vorab, Paket vorladen, Serverwechsel
		if ("fastconnect".equals(System.getProperty("cubiqclient.autotest.only"))) {
			FastConnectTest.install();
			return;
		}
		// -PcubiqAutotestOnly=ping: Ping-Test der Serverliste, Ping-HUD, Entpacken vorher/nachher, Eingabeverzögerung
		if ("ping".equals(System.getProperty("cubiqclient.autotest.only"))) {
			PingTest.install();
			return;
		}
		// -PcubiqAutotestOnly=socialtoasts: nur Sozial-Toasts neben dem Vanilla-Erfolgsfenster (Ausweichen) und über einem Menü
		if ("socialtoasts".equals(System.getProperty("cubiqclient.autotest.only"))) {
			SocialTest.installToasts();
			return;
		}
		// -PcubiqAutotestOnly=bugreport: „Bug melden“ – Formular, Vorschau, Senden an die Attrappe, Fehlerfall
		if ("bugreport".equals(System.getProperty("cubiqclient.autotest.only"))) {
			BugReportTest.install();
			return;
		}
		MinecraftForge.EVENT_BUS.register(new AutoTest());
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) tick(Minecraft.getMinecraft());
	}

	private void tick(Minecraft mc) {
		// Das Spielfenster bekommt den Fokus – Tastendrücke landen im Spiel (Esc → Pausenmenü …).
		// Für saubere Screenshots den erwarteten Zustand wiederherstellen (höchstens 5-mal).
		if (step >= 4 && step < 18 && Mc.world() != null && !isExpected(mc.currentScreen) && reopenCount < 5) {
			reopenCount++;
			CubiqClient.LOGGER.warn("[Autotest] fremdes Menü geschlossen/ersetzt: {}", mc.currentScreen);
			mc.displayGuiScreen(expectedInstance);
			KeyBinding.unPressAllKeys();
			if (wait < 5) wait = 5;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		// Verbindung verloren (z. B. Server-Timeout bei überlasteter Maschine) → Test abbrechen statt abstürzen.
		if (step >= 4 && step < 18 && Mc.player() == null) {
			CubiqClient.LOGGER.error("[Autotest] Welt/Spieler verloren in Schritt {} – Abbruch", step);
			step = 18;
		}
		CubiqModules modules = CubiqClient.get().modules();
		switch (step) {
			case 0:
				if (!(mc.currentScreen instanceof CubiqTitleScreen) && !(mc.currentScreen instanceof GuiMainMenu)) return; // lädt noch
				mc.gameSettings.pauseOnLostFocus = false;
				//? if >=1.12 {
				/*mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
				*///?}
				before = modules.registry.capture();
				modules.titleScreen.setEnabled(true);
				seedServers(mc);
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm erreicht: {}", mc.currentScreen.getClass().getSimpleName());
				mc.displayGuiScreen(new CubiqTitleScreen());
				next(20);
				break;
			case 1:
				if (mc.currentScreen instanceof CubiqTitleScreen && titleStep(mc, (CubiqTitleScreen) mc.currentScreen)) return;
				mc.displayGuiScreen(new CubiqMenuScreen(mc.currentScreen));
				next(20);
				break;
			case 2:
				shot(mc, "menu-title");
				mc.displayGuiScreen(null);
				startWorld(mc);
				next(0);
				break;
			case 3: {
				EntityPlayerSP player = Mc.player();
				if (Mc.world() == null || player == null) return;
				if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)) return; // Ladebildschirm
				CubiqClient.LOGGER.info("[Autotest] Welt geladen");
				mc.displayGuiScreen(null);
				KeyBinding.unPressAllKeys();
				expect(null);
				// Leicht nach unten schauen, damit Gelände (und damit Fullbright) im Bild ist.
				player.rotationPitch = 15.0F;
				player.prevRotationPitch = 15.0F;
				String name = player.getName();
				command(mc, "replaceitem entity " + name + " slot.armor.head minecraft:iron_helmet");
				command(mc, "replaceitem entity " + name + " slot.armor.chest minecraft:diamond_chestplate");
				command(mc, "replaceitem entity " + name + " slot.armor.feet minecraft:golden_boots");
				command(mc, "replaceitem entity " + name + " slot.hotbar.0 minecraft:diamond_sword");
				command(mc, "effect " + name + " minecraft:speed 300 1 true");
				command(mc, "effect " + name + " minecraft:night_vision 120 0 true");
				command(mc, "time set 1000");
				for (Module m : new Module[]{modules.armor, modules.effects, modules.coords, modules.clock, modules.memory,
						modules.packs, modules.toggleSprint, modules.crosshair}) {
					m.setEnabled(true);
				}
				CubiqClient.get().sprintToggle().set(true);
				// Toggle-Schleichen nur für das HUD-Bild: beide Anzeigen "[… (umgeschaltet)]".
				modules.toggleSneak.setEnabled(true);
				CubiqClient.get().sneakToggle().set(true);
				modules.crosshairShape.set(Crosshair.Shape.CROSS_DOT);
				modules.crosshairColor.set(0xFFB84D);
				// Chat-Meldungen der Befehle verblassen lassen (Chat verblasst nach 10 s)
				next(230);
				break;
			}
			case 4:
				// -PcubiqAutotestOnly=redstone: nur den Redstone-Teil prüfen (schneller Durchlauf)
				// -PcubiqAutotestOnly=clips: nur Clips (F9/F10, mit Launcher-Attrappe auch „Clips einschalten“)
				if ("redstone".equals(System.getProperty("cubiqclient.autotest.only"))
						|| "perf".equals(System.getProperty("cubiqclient.autotest.only"))
						|| "clips".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 18;
					break;
				}
				// -PcubiqAutotestOnly=capecolor: nur Umhang-Einstellungen/Vorschau und Farben
				if ("capecolor".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 19;
					break;
				}
				// -PcubiqAutotestOnly=creator: Fan-Symbole + Creator-Sterne (braucht -PcubiqApi mit scripts/mock-creator-api.mjs)
				if ("creator".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 25;
					break;
				}
				// -PcubiqAutotestOnly=cinematic: Kamera-Fahrt (Keyframes, Abspielen, HUD, Kamera)
				if ("cinematic".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 26;
					break;
				}
				// -PcubiqAutotestOnly=freecam: freie Kamera (Fair Play an Stein, durch Glas/Tür, ohne Fair Play durch Stein)
				if ("freecam".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 27;
					break;
				}
				// -PcubiqAutotestOnly=cosmetics2: Kopf-Kosmetik v2 (braucht -PcubiqApi mit scratchpad/cos2mod/mock-api.mjs)
				if ("cosmetics2".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 24;
					break;
				}
				// -PcubiqAutotestOnly=pets: Winter-Kosmetik (Mütze mit Bart, Pinguine; braucht -PcubiqApi mit scripts/mock-winter-api.mjs)
				if ("pets".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 28;
					break;
				}
				shot(mc, "hud");
				CubiqClient.LOGGER.info("[Autotest] Toggle-Anzeige: {} {}", CubiqClient.get().pvp().toggles().sprintStatus().text(),
						CubiqClient.get().pvp().toggles().sneakStatus().text());
				CubiqClient.get().sneakToggle().set(false);
				CubiqClient.get().setForceZoom(true);
				next(30);
				break;
			case 5:
				shot(mc, "zoom");
				CubiqClient.LOGGER.info("[Autotest] Zoom-Faktor beim Screenshot: {}", CubiqClient.get().zoom().factor());
				CubiqClient.get().setForceZoom(false);
				command(mc, "effect " + Mc.player().getName() + " clear");
				command(mc, "time set 18000");
				modules.fullbright.setEnabled(false);
				next(40);
				break;
			case 6:
				shot(mc, "night");
				modules.fullbright.setEnabled(true);
				next(20);
				break;
			case 7:
				shot(mc, "fullbright");
				CubiqClient.LOGGER.info("[Autotest] Gamma-Option nach Fullbright unverändert: {}", mc.gameSettings.gammaSetting);
				modules.fullbright.setEnabled(false);
				command(mc, "time set 1000");
				CubiqClient.get().pvp().forceFreelook(150F);
				next(20);
				break;
			case 8:
				shot(mc, "freelook");
				CubiqClient.get().pvp().forceFreelook(Float.NaN);
				expect(new CubiqMenuScreen(null).select(modules.freelook));
				next(20);
				break;
			case 9:
				shot(mc, "menu");
				expect(new CrosshairEditorScreen(null));
				next(20);
				break;
			case 10:
				shot(mc, "crosshair-editor");
				expect(new PackScreen(null));
				next(20);
				break;
			case 11:
				shot(mc, "packs");
				expect(new CubiqMenuScreen(null).showProfiles());
				next(20);
				break;
			case 12:
				shot(mc, "profiles");
				expect(new HudEditorScreen(null).selectFirst());
				next(20);
				break;
			case 13: {
				shot(mc, "hud-editor");
				expect(null);
				// Neue Module einschalten und mit Beispielwerten füttern (reine Anzeigen).
				for (Module m : new Module[]{modules.reach, modules.combo, modules.speed, modules.minimap,
						modules.waypoints, modules.chat, modules.blockOutline, modules.hitboxes, modules.noHurtCam}) {
					m.setEnabled(true);
				}
				modules.chatTimestamps.set(true);
				long now = System.currentTimeMillis();
				CubiqClient.get().pvp().reach().record(3.04, now);
				ComboTracker combo = CubiqClient.get().pvp().combo();
				for (int i = 0; i < 3; i++) {
					combo.onAttack(4242, now);
					combo.onTargetHurt(4242, now);
				}
				EntityPlayerSP player = Mc.player();
				if (player != null) {
					// Wegpunkt 20 Blöcke vor dem Spieler, damit die Markierung im Bild liegt.
					double yaw = Math.toRadians(player.rotationYaw);
					int wx = (int) Math.floor(player.posX - Math.sin(yaw) * 20);
					int wz = (int) Math.floor(player.posZ + Math.cos(yaw) * 20);
					CubiqClient.get().waypoints().createAt("Testpunkt", wx, (int) Math.floor(player.posY), wz, 0xFFB84D);
				}
				// Gleiche Nachricht dreimal → Zusammenfassung "(x3)" im Chat.
				for (int i = 0; i < 3; i++) command(mc, "say Cubiq-Selbsttest Chat");
				next(30);
				break;
			}
			case 14:
				shot(mc, "waypoints-minimap");
				expect(new WaypointListScreen(null));
				next(20);
				break;
			case 15:
				shot(mc, "waypoint-list");
				// Menü mit Text-Einstellungen (Auto-GG) – Textzeilen liegen jetzt im Einstellungs-Bereich.
				expect(new CubiqMenuScreen(null).select(modules.autoGg));
				next(20);
				break;
			case 16:
				shot(mc, "menu-text");
				CubiqClient.LOGGER.info("[Autotest] Wegpunkte: {}, Combo: {}, Reichweite: {}",
						CubiqClient.get().waypoints().all().size(),
						CubiqClient.get().pvp().combo().combo(),
						CubiqClient.get().pvp().reach().distance());
				expect(null);
				next(5);
				break;
			case 17:
				// Cubiq-Umhang, Umhang-Physik und Abzeichen (mit lokaler API-Attrappe, siehe -PcubiqApi)
				CapeTest.Actions actions = new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				};
				if (capeTest.step(mc, modules, actions)) return;
				// Emote-Rad und Emotes (gleiche Attrappe)
				if (emoteTest.step(mc, modules, actions)) return;
				next(5);
				break;
			case 18:
				// Clips & Aufnahme (nur mit -PcubiqAutotestOnly=clips)
				if ("clips".equals(System.getProperty("cubiqclient.autotest.only"))) {
					if (clipsTest.step(mc, modules, new CapeTest.Actions() {
						@Override
						public void shot(String name) {
							AutoTest.this.shot(mc, name);
						}

						@Override
						public void command(String command) {
							AutoTest.command(mc, command);
						}
					})) return;
					next(5);
					break;
				}
				// Redstone-Werkzeuge: Signalstärke, Komparator, Overlay, Takt
				if (!"perf".equals(System.getProperty("cubiqclient.autotest.only")) && redstoneTest.step(mc, modules, new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				})) return;
				// Leistung: Testszene, FPS ohne/mit FPS-Boost, Dynamische FPS, Menü, Rückgängig
				if (!"redstone".equals(System.getProperty("cubiqclient.autotest.only")) && perfTest.step(mc, modules, new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				})) return;
				if ("perf".equals(System.getProperty("cubiqclient.autotest.only"))) {
					step = 20;
					break;
				}
				next(5);
				break;
			case 19:
				// Umhang-Physik-Einstellungen (Vorschau, Wind, Stufen) und Farben (Sättigung 0 %/200 %)
				if (capeColorTest.step(mc, modules, new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				})) return;
				next(5);
				break;
			case 24:
				// Kopf-Kosmetik v2: Screenshots cubiqclient-<mc>-cos2-*.png
				if (cosmetics2Test.step(mc, modules, new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				})) return;
				step = 20;
				wait = 5;
				break;
			case 28:
				// Winter-Kosmetik: Screenshots <mc>-winter-*.png
				if (petsTest.step(mc, modules, new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				})) return;
				step = 20;
				wait = 5;
				break;
			case 25:
			case 26:
			case 27: {
				// Creator-Programm bzw. Kamera-Fahrten: Screenshots cubiqclient-<mc>-creator-*.png / -cinematic-*.png
				CapeTest.Actions creatorActions = new CapeTest.Actions() {
					@Override
					public void shot(String name) {
						AutoTest.this.shot(mc, name);
					}

					@Override
					public void command(String command) {
						AutoTest.command(mc, command);
					}
				};
				if (step == 25 ? creatorTest.step(mc, modules, creatorActions)
						: step == 26 ? cinematicTest.step(mc, modules, creatorActions) : freecamTest.step(mc, modules, creatorActions)) return;
				step = 20;
				wait = 5;
				break;
			}
			case 20:
				CubiqClient.LOGGER.info("[Autotest] Hook-Aufrufe: {}", HookStats.summary());
				CubiqClient.LOGGER.info("[Autotest] Leistungs-Hooks: {}", dev.pwiepixel.cubiqclient.perf.LegacyPerf.get().stats());
				CubiqClient.LOGGER.info("[Autotest] fertig, verlasse Welt und beende das Spiel");
				CubiqClient.get().sprintToggle().set(false);
				if (before != null) modules.registry.apply(before);
				CubiqClient.get().saveConfig();
				WorldClient w = Mc.world();
				if (w != null) {
					w.sendQuittingDisconnectingPacket();
					mc.loadWorld((WorldClient) null);
				}
				mc.displayGuiScreen(new GuiMainMenu());
				next(20);
				break;
			default:
				if (step == 21) mc.shutdown();
				step = 22;
				break;
		}
	}

	/** Öffnet den erwarteten Bildschirm (null = Spiel ohne Menü). */
	private void expect(GuiScreen screen) {
		expectedInstance = screen;
		expected = screen == null ? null : screen.getClass();
		Minecraft.getMinecraft().displayGuiScreen(screen);
	}

	private final Cosmetics2Test cosmetics2Test = new Cosmetics2Test();
	private final PetsTest petsTest = new PetsTest();

	private boolean isExpected(GuiScreen current) {
		// Das Emote-Rad öffnet der Emote-Test selbst.
		if (current instanceof dev.pwiepixel.cubiqclient.screen.EmoteWheelScreen) return true;
		return expected == null ? current == null : expected.isInstance(current);
	}

	/**
	 * Startbildschirm: Ruhe-Screenshot, einen Knopf per Tastatur-Auswahl hervorheben (wie Hover), dann die
	 * Kosten der Hintergrund-Animation messen (mit und ohne Szene). true = noch nicht fertig.
	 */
	private boolean titleStep(Minecraft mc, CubiqTitleScreen screen) {
		dev.pwiepixel.cubiqclient.core.ui.title.TitleUi title = screen.titleUi();
		switch (titlePhase++) {
			case 0:
				if (!figureTurned) {
					// Figur fast von vorn für den Screenshot (ein paar Bilder vorher setzen).
					figureTurned = true;
					title.setFigureAngle(20f);
					titlePhase--;
					wait = 2;
					return true;
				}
				shot(mc, "title");
				title.focus(0);
				wait = 10;
				return true;
			case 1:
				shot(mc, "title-hover");
				title.focus(-1);
				wait = 100;
				return true;
			case 2:
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm: Szene " + Math.round(title.sceneMicros()) + " µs, Bildschirm "
						+ Math.round(title.frameMicros()) + " µs (CPU), Bildzeit " + String.format(java.util.Locale.ROOT, "%.2f", title.frameMillis())
						+ " ms, " + Minecraft.getDebugFPS() + " fps, sparsam=" + title.sparseScene());
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm: Figur+Drehscheibe " + Math.round(title.modelMicros()) + " µs, "
						+ title.modelFaces() + " Flächen, Figur=" + title.figureShown() + ", Leiste=" + title.sideMode());
				title.setSceneEnabled(false);
				wait = 100;
				return true;
			case 3:
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm ohne Schaltung: Bildschirm " + Math.round(title.frameMicros())
						+ " µs (CPU), Bildzeit " + String.format(java.util.Locale.ROOT, "%.2f", title.frameMillis()) + " ms, " + Minecraft.getDebugFPS() + " fps");
				title.setSceneEnabled(true);
				if (!"title".equals(System.getProperty("cubiqclient.autotest.only"))) return false;
				wait = 10;
				return true;
			case 4:
				// Nur -PcubiqAutotestOnly=title: große und kleine GUI, „Kommt bald“, dann beenden.
				titleScaleBefore = mc.gameSettings.guiScale;
				mc.gameSettings.guiScale = 1;
				mc.displayGuiScreen(screen);
				wait = 30;
				return true;
			case 5:
				shot(mc, "title-wide");
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm groß: Figur=" + title.figureShown() + ", Leiste=" + title.sideMode()
						+ ", " + title.modelFaces() + " Flächen");
				mc.gameSettings.guiScale = 3;
				mc.displayGuiScreen(screen);
				wait = 30;
				return true;
			case 6:
				shot(mc, "title-narrow");
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm schmal: Figur=" + title.figureShown() + ", Leiste=" + title.sideMode());
				mc.gameSettings.guiScale = titleScaleBefore;
				mc.displayGuiScreen(screen);
				wait = 30;
				return true;
			case 7: {
				int[] r = title.buttonRect("wardrobe");
				if (r != null) title.mouseClicked(r[0] + r[2] / 2.0, r[1] + r[3] / 2.0, 0);
				wait = 12;
				return true;
			}
			case 8:
				shot(mc, "title-soon");
				CubiqClient.LOGGER.info("[Autotest] Garderobe-Knopf: Hinweis \"" + title.toast() + "\"");
				CubiqClient.LOGGER.info("[Autotest] Startbildschirm-Test fertig");
				if (before != null) CubiqClient.get().modules().registry.apply(before);
				mc.shutdown();
				return true;
			default:
				return false;
		}
	}

	private void next(int ticks) {
		step++;
		wait = ticks;
	}

	/** Legt für den Test zwei Server in servers.dat an, falls die Liste leer ist (Schnellbeitritt-Leiste). */
	private static void seedServers(Minecraft mc) {
		ServerList list = new ServerList(mc);
		list.loadServerList();
		if (list.countServers() > 0) return;
		list.addServerData(new ServerData("Cubiq Testserver", "localhost:25565", false));
		list.addServerData(new ServerData("Hypixel", "mc.hypixel.net", false));
		list.saveServerList();
	}

	/** Führt einen Befehl als Server (volle Rechte) auf dem Server-Thread aus. */
	private static void command(Minecraft mc, final String command) {
		final IntegratedServer server = mc.getIntegratedServer();
		if (server == null) return;
		server.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				int result = server.getCommandManager().executeCommand(server, command);
				CubiqClient.LOGGER.info("[Autotest] /{} → {}", command, result);
			}
		});
	}

	private void startWorld(Minecraft mc) {
		if (mc.getSaveLoader().canLoadWorld(world)) {
			CubiqClient.LOGGER.info("[Autotest] öffne Testwelt '{}'", world);
			mc.launchIntegratedServer(world, world, null);
		} else {
			CubiqClient.LOGGER.info("[Autotest] erstelle Testwelt '{}'", world);
			mc.launchIntegratedServer(world, world, Mc.creativeWorld(System.nanoTime()));
		}
	}

	private void shot(Minecraft mc, String name) {
		String file = "cubiqclient-" + mcVersion + "-" + name + ".png";
		ScreenShotHelper.saveScreenshot(Mc.gameDir(), file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		CubiqClient.LOGGER.info("[Autotest] Screenshot {}", file);
	}
}
