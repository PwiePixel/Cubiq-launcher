package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.config.CubiqConfig;
import dev.pwiepixel.cubiqclient.core.hud.Crosshair;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.screen.CrosshairEditorScreen;
import dev.pwiepixel.cubiqclient.screen.HudEditorScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenDemo;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Entwickler-Selbsttest, nur aktiv mit {@code -Dcubiqclient.autotest=true}
 * ({@code ./gradlew runClient -PcubiqAutotest}): Menü im Hauptmenü, Testwelt laden, Rüstung/Effekte per Befehl,
 * Screenshots von HUD, Zoom, Nacht ohne/mit Fullbright, Menü, Fadenkreuz-Editor und HUD-Editor, dann beenden.
 * Screenshots landen in {@code run/forge-1.13.2/screenshots/cubiqclient-1.13.2-*.png}.
 */
public final class AutoTest {
	private static final String WORLD = "cubiq-autotest-1.13.2";
	private static final String PREFIX = "cubiqclient-1.13.2-";

	private int step;
	private int wait;
	private CubiqConfig before;
	/** Bildschirm, den der Test gerade erwartet (null = Spiel ohne Menü). */
	private GuiScreen expected;
	/** Zoom-Mausrad-Probe: Slot vor der Simulation (-1 = noch nicht) und Zoomstufe davor. */
	private int scrollProbe = -1;
	private double scrollLevel;

	private AutoTest() {
	}

	public static void installIfRequested() {
		if (!Boolean.getBoolean("cubiqclient.autotest")) return;
		CubiqClient.LOGGER.info("[Autotest] aktiv");
		MinecraftForge.EVENT_BUS.register(new AutoTest());
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) tick(Minecraft.getInstance());
	}

	private void tick(Minecraft mc) {
		// Das Spielfenster bekommt den Fokus – Tastendrücke landen im Spiel (Esc → Pausenmenü,
		// Rechte Umschalttaste → Cubiq-Menü …). Für saubere Screenshots fremde Menüs schließen.
		if (step >= 3 && step < 10 && mc.world != null && mc.currentScreen != expected
				&& (expected == null || mc.currentScreen == null || mc.currentScreen.getClass() != expected.getClass())) {
			CubiqClient.LOGGER.info("[Autotest] fremdes Menü geschlossen: {}", mc.currentScreen);
			mc.displayGuiScreen(expected);
			KeyBinding.unPressAllKeys();
			if (wait < 5) wait = 5;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		CubiqModules modules = CubiqClient.get().modules();
		switch (step) {
			case 0:
				if (!(mc.currentScreen instanceof GuiMainMenu)) return; // Ressourcen laden noch
				mc.gameSettings.pauseOnLostFocus = false;
				// Tutorial-Hinweise (Toasts) verdecken sonst die HUD-Module rechts.
				mc.getTutorial().setStep(TutorialSteps.NONE);
				before = modules.registry.capture();
				CubiqClient.LOGGER.info("[Autotest] Hauptmenü erreicht, öffne Cubiq-Menü");
				mc.displayGuiScreen(new CubiqMenuScreen(mc.currentScreen));
				next(20);
				break;
			case 1:
				shot(mc, "menu-title");
				startWorld(mc);
				next(0);
				break;
			case 2:
				if (mc.world == null || mc.player == null) return;
				// Ohne Account startet der Launcher im Demo-Modus – dessen Willkommensbildschirm zählt nicht als Ladebildschirm.
				if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiIngameMenu)
						&& !(mc.currentScreen instanceof GuiScreenDemo)) return; // Ladebildschirm
				CubiqClient.LOGGER.info("[Autotest] Welt geladen");
				mc.displayGuiScreen(null);
				KeyBinding.unPressAllKeys();
				mc.player.rotationPitch = 15.0F;
				mc.player.prevRotationPitch = 15.0F;
				mc.getToastGui().clear();
				command(mc, "replaceitem entity @p armor.head minecraft:iron_helmet");
				command(mc, "replaceitem entity @p armor.chest minecraft:diamond_chestplate");
				command(mc, "replaceitem entity @p armor.feet minecraft:golden_boots{Damage:40}");
				command(mc, "replaceitem entity @p weapon.mainhand minecraft:diamond_sword");
				command(mc, "effect give @p minecraft:speed 300 1 true");
				command(mc, "effect give @p minecraft:night_vision 120 0 true");
				command(mc, "time set day");
				for (Module m : new Module[]{modules.armor, modules.effects, modules.coords, modules.clock, modules.memory,
						modules.packs, modules.toggleSprint, modules.crosshair}) {
					m.setEnabled(true);
				}
				CubiqClient.get().pvp().sprint().set(true);
				modules.crosshairShape.set(Crosshair.Shape.CROSS_DOT);
				modules.crosshairColor.set(0xFFB84D);
				// Chat-Meldungen der Befehle verblassen lassen (10 s)
				next(220);
				break;
			case 3:
				shot(mc, "hud");
				CubiqClient.get().setForceZoom(true);
				next(30);
				break;
			case 4:
				if (scrollProbe < 0) {
					// Zoom-Mausrad simulieren: Vanilla verschiebt beim Scrollen nach oben den Hotbar-Slot um -1.
					scrollProbe = mc.player.inventory.currentItem;
					scrollLevel = CubiqClient.get().zoom().level();
					mc.player.inventory.currentItem = (scrollProbe + 8) % 9;
					wait = 3;
					return;
				}
				CubiqClient.LOGGER.info("[Autotest] Zoom-Mausrad: Stufe {} -> {}, Hotbar-Slot zurückgesetzt: {}",
						scrollLevel, CubiqClient.get().zoom().level(), mc.player.inventory.currentItem == scrollProbe);
				shot(mc, "zoom");
				CubiqClient.LOGGER.info("[Autotest] Zoom-Faktor beim Screenshot: {}", CubiqClient.get().zoom().factor());
				CubiqClient.get().setForceZoom(false);
				command(mc, "effect clear @p minecraft:night_vision");
				command(mc, "time set midnight");
				modules.fullbright.setEnabled(false);
				next(40);
				break;
			case 5:
				shot(mc, "night");
				modules.fullbright.setEnabled(true);
				next(20);
				break;
			case 6:
				shot(mc, "fullbright");
				CubiqClient.LOGGER.info("[Autotest] Gamma-Option nach Fullbright unverändert: {}", mc.gameSettings.gammaSetting);
				modules.fullbright.setEnabled(false);
				expected = new CubiqMenuScreen(null).select(modules.crosshair);
				mc.displayGuiScreen(expected);
				next(20);
				break;
			case 7:
				shot(mc, "menu");
				expected = new CrosshairEditorScreen(null);
				mc.displayGuiScreen(expected);
				next(20);
				break;
			case 8:
				shot(mc, "crosshair-editor");
				expected = new HudEditorScreen(null);
				mc.displayGuiScreen(expected);
				next(20);
				break;
			case 9:
				shot(mc, "hud-editor");
				expected = null;
				mc.displayGuiScreen(null);
				next(5);
				break;
			case 10:
				CubiqClient.LOGGER.info("[Autotest] Hook-Aufrufe: {}", HookStats.summary());
				CubiqClient.LOGGER.info("[Autotest] fertig, verlasse Welt und beende das Spiel");
				CubiqClient.get().pvp().sprint().set(false);
				if (before != null) modules.registry.apply(before);
				CubiqClient.get().saveConfig();
				if (mc.world != null) {
					mc.world.sendQuittingDisconnectingPacket();
					mc.loadWorld((WorldClient) null);
				}
				mc.displayGuiScreen(new GuiMainMenu());
				next(20);
				break;
			default:
				if (step == 11) mc.shutdown();
				step = 12;
				break;
		}
	}

	private void next(int ticks) {
		step++;
		wait = ticks;
	}

	/** Führt einen Befehl als Server (Berechtigungsstufe 4) auf dem Server-Thread aus. */
	private static void command(Minecraft mc, String command) {
		final MinecraftServer server = mc.getIntegratedServer();
		if (server == null) return;
		server.addScheduledTask(() -> server.getCommandManager().handleCommand(server.getCommandSource(), command));
	}

	private static void startWorld(Minecraft mc) {
		if (mc.getSaveLoader().canLoadWorld(WORLD)) {
			CubiqClient.LOGGER.info("[Autotest] öffne Testwelt '{}'", WORLD);
			mc.launchIntegratedServer(WORLD, WORLD, null);
		} else {
			CubiqClient.LOGGER.info("[Autotest] erstelle Testwelt '{}'", WORLD);
			WorldSettings settings = new WorldSettings(System.nanoTime(), GameType.CREATIVE, true, false, WorldType.DEFAULT);
			mc.launchIntegratedServer(WORLD, WORLD, settings);
		}
	}

	private static void shot(Minecraft mc, String name) {
		ScreenShotHelper.saveScreenshot(mc.gameDir, PREFIX + name + ".png",
				mc.mainWindow.getFramebufferWidth(), mc.mainWindow.getFramebufferHeight(), mc.getFramebuffer(),
				msg -> CubiqClient.LOGGER.info("[Autotest] {}", msg.getString()));
	}
}
