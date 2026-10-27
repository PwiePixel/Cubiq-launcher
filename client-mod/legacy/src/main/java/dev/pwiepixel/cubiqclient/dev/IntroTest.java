package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.config.CubiqConfig;
import dev.pwiepixel.cubiqclient.core.intro.IntroGate;
import dev.pwiepixel.cubiqclient.core.intro.IntroUi;
import dev.pwiepixel.cubiqclient.core.intro.KeyBind;
import dev.pwiepixel.cubiqclient.core.module.ModulePacks;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.menu.ModMenu;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqTitleScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqUiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Selbsttest Einführung ({@code -PcubiqAutotestOnly=intro}): setzt die Einführung zurück, öffnet den Startbildschirm
 * (die Einführung muss von selbst erscheinen), Screenshot je Schritt, wählt das Paket „PvP“, schließt ab; danach das
 * Cubiq-Menü mit „NEU“-Markierungen (Stand künstlich auf 0.3.0 gesetzt), eine Einstellungsseite mit neuer Zeile und die
 * Seite „Modul-Pakete“. Stellt am Ende die Config wieder her und beendet das Spiel.
 */
public final class IntroTest {
	private int phase;
	private int wait;
	private int waited;
	private CubiqConfig before;

	private final String mcVersion = Mc.version();

	public static void install() {
		MinecraftForge.EVENT_BUS.register(new IntroTest());
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.END) tick(Minecraft.getMinecraft());
	}

	private void shot(Minecraft mc, String name) {
		String file = name.replace("cubiqclient-", "cubiqclient-" + mcVersion + "-") + ".png";
		ScreenShotHelper.saveScreenshot(Mc.gameDir(), file, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
		CubiqClient.LOGGER.info("[Autotest] Screenshot {}", file);
	}

	private static IntroUi intro() {
		if (Mc.screen() instanceof CubiqUiScreen && ((CubiqUiScreen) Mc.screen()).ui() instanceof IntroUi) {
			return (IntroUi) ((CubiqUiScreen) Mc.screen()).ui();
		}
		return null;
	}

	private void tick(Minecraft mc) {
		if (wait > 0) {
			wait--;
			return;
		}
		CubiqModules modules = CubiqClient.get().modules();
		switch (phase) {
			case 0:
				if (!(mc.currentScreen instanceof CubiqTitleScreen) && !(mc.currentScreen instanceof GuiMainMenu)) return;
				mc.gameSettings.pauseOnLostFocus = false;
				before = modules.registry.capture();
				modules.titleScreen.setEnabled(true);
				modules.clientState.resetIntro();
				modules.clientState.news().set("0.3.0", null);
				IntroGate.reset();
				Mc.setScreen(new CubiqTitleScreen());
				phase++;
				wait = 5;
				return;
			case 1: {
				// Die Einführung erscheint von selbst (höchstens ~5 s Warten auf den ersten Sync).
				IntroUi ui = intro();
				if (ui == null) {
					if (waited++ < 200) return;
					CubiqClient.LOGGER.error("[Autotest] Einführung: erschien NICHT von selbst");
					phase = 20;
					return;
				}
				CubiqClient.LOGGER.info("[Autotest] Einführung: erschien nach {} Ticks, Cubiq-Tasten={}", waited, ui.cubiqKeys().size());
				java.util.List<KeyBind> all = new dev.pwiepixel.cubiqclient.screen.CubiqMenuHost(null).keyBindings();
				for (KeyBind b : ui.cubiqKeys()) {
					StringBuilder with = new StringBuilder();
					for (KeyBind o : b.conflicts(all)) with.append(o.id).append(' ');
					CubiqClient.LOGGER.info("[Autotest] Einführung: Taste {} = {} (Konflikte: {})", b.id, b.key(), with.toString().trim());
				}
				phase++;
				wait = 20;
				return;
			}
			case 2:
				shot(mc, "cubiqclient-intro-1-look");
				intro().goTo(1);
				phase++;
				wait = 15;
				return;
			case 3:
				shot(mc, "cubiqclient-intro-2-perf");
				intro().goTo(2);
				phase++;
				wait = 15;
				return;
			case 4:
				shot(mc, "cubiqclient-intro-3-keys");
				intro().goTo(3);
				intro().packs().select(ModulePacks.PVP);
				phase++;
				wait = 15;
				return;
			case 5:
				shot(mc, "cubiqclient-intro-4-packs");
				intro().finish();
				phase++;
				wait = 30;
				return;
			case 6:
				shot(mc, "cubiqclient-intro-done");
				CubiqClient.LOGGER.info("[Autotest] Einführung: erledigt={} wie={} paket={} reach={} keystrokes={} minimap={}",
						modules.clientState.introDone(), modules.clientState.introHow(), modules.clientState.introPack(),
						modules.reach.isEnabled(), modules.keystrokes.isEnabled(), modules.minimap.isEnabled());
				CubiqMenuScreen grid = new CubiqMenuScreen(new CubiqTitleScreen());
				((ModMenu) grid.ui()).showCategory(dev.pwiepixel.cubiqclient.core.module.Category.REDSTONE);
				Mc.setScreen(grid);
				phase++;
				wait = 20;
				return;
			case 7:
				shot(mc, "cubiqclient-menu-new");
				CubiqMenuScreen page = new CubiqMenuScreen(new CubiqTitleScreen());
				((ModMenu) page.ui()).select(modules.cubiqOnline).scrollSettings(400);
				Mc.setScreen(page);
				phase++;
				wait = 20;
				return;
			case 8:
				shot(mc, "cubiqclient-menu-new-settings");
				CubiqClient.LOGGER.info("[Autotest] NEU: cubiqOnline noch neu={} (Kachel), sync-Zeile gesehen={}",
						modules.clientState.news().hasNew(modules.cubiqOnline), modules.clientState.news().seen().contains("cubiqOnline.sync"));
				CubiqMenuScreen menu = new CubiqMenuScreen(new CubiqTitleScreen());
				((ModMenu) menu.ui()).showPacks().packsPage().select(ModulePacks.REDSTONE);
				Mc.setScreen(menu);
				phase++;
				wait = 20;
				return;
			case 9:
				shot(mc, "cubiqclient-menu-packs");
				phase = 20;
				return;
			case 20:
				CubiqClient.LOGGER.info("[Autotest] Einführung fertig");
				if (before != null) {
					modules.registry.apply(before);
					CubiqClient.get().saveConfig();
				}
				phase++;
				wait = 20;
				return;
			case 21:
				phase++;
				mc.shutdown();
				return;
			default:
		}
	}
}
