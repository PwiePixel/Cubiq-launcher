package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.HudModule;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.panorama.Panorama;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

/**
 * Selbsttest „Komfort 2“ unter 1.8.9–1.12.2 ({@code -PcubiqAutotestOnly=comfort}): Tooltips (Karte, Essen, Schwert mit
 * Haltbarkeit + kompakten Verzauberungen), Server-Profil für Einzelspieler und Panorama mit eigener Kamera-Drehung.
 * Bilder: cubiqclient-&lt;mc&gt;-comfort-*.png. Die leere Karte wird nur in 1.8.9 benutzt (Aufruf je Version anders).
 */
public final class ComfortTest {
	private final String mcVersion = Mc.version();
	private final String world = "cubiq-comfort-" + Mc.version();
	private int phase = -1;
	private int wait;
	private int waited;
	/** Inventar-Feld unter der Maus (-1 = keins). */
	private int hover = -1;
	private dev.pwiepixel.cubiqclient.core.config.CubiqConfig before;

	public static void install() {
		MinecraftForge.EVENT_BUS.register(new ComfortTest());
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (hover >= 0 && mc.currentScreen instanceof GuiInventory) hover(mc, hover);
		if (wait > 0) {
			wait--;
			return;
		}
		try {
			tick(mc);
		} catch (RuntimeException e) {
			CubiqClient.LOGGER.error("[Autotest] Komfort 2: Fehler", e);
			phase = 999;
			mc.shutdown();
		}
	}

	private void tick(Minecraft mc) {
		GuiScreen screen = mc.currentScreen;
		CubiqModules modules = CubiqClient.get().modules();
		String me = mc.getSession().getUsername();
		switch (phase) {
			case -1:
				if (!(screen instanceof CubiqTitleScreen) && !(screen instanceof GuiMainMenu)) return;
				mc.gameSettings.pauseOnLostFocus = false;
				mc.getSaveLoader().deleteWorldDirectory(world);
				mc.launchIntegratedServer(world, world, Mc.creativeWorld(20260927L));
				phase++;
				return;
			case 0:
				if (Mc.world() == null || Mc.player() == null || mc.currentScreen != null) {
					if (waited++ > 900) throw new IllegalStateException("Welt lädt nicht");
					return;
				}
				before = modules.registry.capture();
				command(mc, "gamerule sendCommandFeedback false");
				command(mc, "gamerule logAdminCommands false");
				command(mc, "time set 1000");
				command(mc, "weather clear");
				command(mc, "gamemode 0 " + me);
				command(mc, "clear " + me);
				command(mc, "replaceitem entity " + me + " slot.hotbar.0 map");
				for (Module m : modules.registry.all()) {
					if (m instanceof HudModule) m.setEnabled(false);
				}
				modules.dynamicFps.setEnabled(false);
				modules.comfort.tooltips.setEnabled(true);
				modules.comfort.serverProfiles.setEnabled(true);
				modules.comfort.panorama.setEnabled(true);
				phase++;
				wait = 30;
				return;
			case 1:
				//? if <1.9 {
				// Leere Karte benutzen → gefüllte Karte (1.8.9: sendUseItem, später je Version anders).
				mc.playerController.sendUseItem(Mc.player(), Mc.world(), Mc.player().getHeldItem());
				//?}
				phase++;
				wait = 100;
				return;
			case 2:
				command(mc, "replaceitem entity " + me + " slot.hotbar.1 cooked_beef 16");
				command(mc, "replaceitem entity " + me + " slot.hotbar.2 diamond_sword 1 700 {ench:[{id:16s,lvl:5s},{id:34s,lvl:3s},{id:21s,lvl:3s},{id:20s,lvl:2s}]}");
				phase++;
				wait = 20;
				return;
			case 3:
				mc.displayGuiScreen(new GuiInventory(Mc.player()));
				hover = 36;
				phase++;
				wait = 20;
				return;
			case 4:
				shot(mc, "comfort-map");
				hover = 37;
				phase++;
				wait = 8;
				return;
			case 5:
				shot(mc, "comfort-food");
				hover = 38;
				phase++;
				wait = 8;
				return;
			case 6:
				shot(mc, "comfort-sword");
				hover = -1;
				mc.displayGuiScreen(null);
				//? if >=1.11 {
				/*mc.ingameGUI.getChatGUI().clearChatMessages(false);
				*///?} else
				mc.ingameGUI.getChatGUI().clearChatMessages();
				modules.fps.setEnabled(true);
				modules.coords.setEnabled(true);
				modules.clock.setEnabled(true);
				String error = modules.serverProfiles.rememberCurrent();
				CubiqClient.LOGGER.info("[Autotest] Server-Profil merken: {} → {}", error == null ? "ok" : error,
						modules.serverProfiles.active() == null ? "-" : modules.serverProfiles.active().name());
				modules.serverProfiles.update(null);
				CubiqClient.LOGGER.info("[Autotest] Standard nach Verlassen: fps={} coords={}", modules.fps.isEnabled(), modules.coords.isEnabled());
				phase++;
				wait = 6;
				return;
			case 7:
				CubiqClient.LOGGER.info("[Autotest] Profil nach Betreten: {} fps={} coords={}",
						modules.serverProfiles.active() == null ? "-" : modules.serverProfiles.active().name(), modules.fps.isEnabled(),
						modules.coords.isEnabled());
				shot(mc, "comfort-profile");
				command(mc, "tp " + me + " ~ ~ ~ 45 0");
				phase++;
				wait = 20;
				return;
			case 8:
				Panorama.get().request(true, true);
				phase++;
				waited = 0;
				return;
			case 9:
				if (Panorama.get().state() != Panorama.State.IDLE && waited++ < 1200) return;
				CubiqClient.LOGGER.info("[Autotest] Panorama: {} Ticks, Ordner {}", waited, Panorama.get().lastFolder());
				phase++;
				wait = 5;
				return;
			case 10:
				shot(mc, "comfort-panorama-toast");
				modules.serverProfiles.delete(0);
				if (before != null) {
					modules.registry.apply(before);
					CubiqClient.get().saveConfig();
				}
				CubiqClient.LOGGER.info("[Autotest] Komfort 2: fertig");
				phase = 999;
				mc.displayGuiScreen(new GuiMainMenu());
				wait = 10;
				return;
			case 999:
				mc.shutdown();
				return;
			default:
		}
	}

	/** Maus über das Inventar-Feld {@code index} stellen (LWJGL-Maus, Ursprung unten links). */
	private static void hover(Minecraft mc, int index) {
		GuiInventory gui = (GuiInventory) mc.currentScreen;
		net.minecraft.inventory.Slot slot = gui.inventorySlots.inventorySlots.get(index);
		int left = (gui.width - 176) / 2, top = (gui.height - 166) / 2;
		//? if >=1.10.2 {
		/*int gx = left + slot.xPos + 8, gy = top + slot.yPos + 8;
		*///?} else
		int gx = left + slot.xDisplayPosition + 8, gy = top + slot.yDisplayPosition + 8;
		ScaledResolution res = Mc.scaledResolution();
		int px = gx * mc.displayWidth / res.getScaledWidth();
		int py = mc.displayHeight - 1 - gy * mc.displayHeight / res.getScaledHeight();
		Mouse.setCursorPosition(px, py);
	}

	private static void command(Minecraft mc, final String command) {
		final IntegratedServer server = mc.getIntegratedServer();
		if (server == null) return;
		server.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				server.getCommandManager().executeCommand(server, command);
			}
		});
	}

	private void shot(Minecraft mc, String name) {
		String file = "cubiqclient-" + mcVersion + "-" + name + ".png";
		ScreenShotHelper.saveScreenshot(Mc.gameDir(), file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		CubiqClient.LOGGER.info("[Autotest] Screenshot {}", file);
	}
}
