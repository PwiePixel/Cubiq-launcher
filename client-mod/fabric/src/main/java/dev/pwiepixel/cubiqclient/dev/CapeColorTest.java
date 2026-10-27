package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.cape.CapeSettings;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.online.OnlineHooks;
import dev.pwiepixel.cubiqclient.render.ColorPass;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import net.minecraft.client.Minecraft;
//? if >=1.16 {
import net.minecraft.client.CameraType;
//?}

/**
 * Selbsttest für die Umhang-Physik-Einstellungen und das Modul „Farben“ (Teil des Autotests,
 * {@code -PcubiqAutotestOnly=capecolor} springt direkt hierher): Einstellungsseite mit Live-Vorschau
 * (stehend und gehend), Umhang im Böen-Wind, Stufen-Stil, dann das Spielbild mit Sättigung 0 % und 200 %
 * (HUD bleibt unverändert) und die Farben-Seite im Menü.
 */
public final class CapeColorTest {
	private int phase;
	private int wait;
	private int tries;

	/** Ein Tick; true = noch nicht fertig. */
	public boolean step(Minecraft mc, CubiqModules modules, CapeTest.Actions actions) {
		if (mc.player == null) return false;
		if (wait > 0) {
			wait--;
			return true;
		}
		switch (phase++) {
			case 0:
				modules.cubiqOnline.setEnabled(true);
				modules.cubiqCapes.set(true);
				modules.capePhysics.reset();
				modules.capePhysics.setEnabled(true);
				modules.minimap.setEnabled(false);
				modules.colors.reset();
				//? if >=1.17 {
				actions.command("item replace entity @p armor.chest with air");
				//?} else {
				/*actions.command("replaceitem entity @p armor.chest air");
				*///?}
				actions.command("time set day");
				actions.command("weather clear");
				actions.command("execute as @p at @s run fill ~-6 ~ ~-8 ~6 ~7 ~28 air");
				actions.command("execute as @p at @s run fill ~-6 ~-1 ~-8 ~6 ~-1 ~28 grass_block");
				actions.command("execute as @p at @s run fill ~-6 ~ ~20 ~6 ~4 ~20 red_wool");
				actions.command("execute as @p at @s run fill ~-2 ~ ~19 ~2 ~3 ~19 lime_wool");
				actions.command("execute as @p at @s run fill ~-1 ~ ~18 ~1 ~2 ~18 light_blue_wool");
				actions.command("execute as @p at @s run tp @s ~ ~ ~ 0 5");
				wait = 5;
				return true;
			case 1: {
				// Auf den eigenen (Cubiq-)Umhang warten (höchstens 20 s, sonst ohne weiter)
				Object tex = OnlineHooks.capeTexture(mc.player);
				if (tex == null && tries++ < 400) {
					phase = 1;
					return true;
				}
				CubiqClient.LOGGER.info("[Autotest] Umhang-Vorschau: Umhang {} sichtbar={}", tex != null ? tex : "(Vanilla/keiner)",
						OnlineHooks.hasCapeVisible(mc.player));
				Mc.setScreen(new CubiqMenuScreen(null).select(modules.capePhysics));
				wait = 20;
				return true;
			}
			case 2:
				actions.shot("cubiqclient-capephysics-menu");
				wait = 55;
				return true;
			case 3:
				actions.shot("cubiqclient-capephysics-menu-walk");
				CubiqClient.LOGGER.info("[Autotest] Vorschau geht={} (Physik aktiv für {} Spieler)",
						OnlineHooks.features().physics().previewWalking(), OnlineHooks.features().physics().active());
				Mc.setScreen(null);
				modules.capeWindMode.set(CapeSettings.Wind.GUSTS);
				modules.capeWind.set(200);
				camera(mc, true);
				hud(mc, false);
				CubiqClient.get().setForceZoom(true);
				// Von der Seite (Freelook), damit das Anheben durch Wind sichtbar ist.
				CubiqClient.get().pvp().forceFreelook(100F);
				modules.zoomFactor.set(2.0);
				wait = 60;
				return true;
			case 4:
				mc.mouseHandler.releaseMouse();
				actions.shot("cubiqclient-cape-wind");
				wait = 30;
				return true;
			case 5:
				actions.shot("cubiqclient-cape-wind-2");
				modules.capeStyle.set(CapeSettings.Style.BLOCKY);
				modules.capeMovement.set(CapeSettings.Movement.DUNGEONS);
				wait = 40;
				return true;
			case 6:
				actions.shot("cubiqclient-cape-blocky");
				modules.capePhysics.reset();
				CubiqClient.get().setForceZoom(false);
				CubiqClient.get().pvp().forceFreelook(Float.NaN);
				wait = 5;
				return true;
			case 7:
				// Freelook stellt beim Beenden die alte Perspektive her – danach erst in die Ich-Perspektive.
				camera(mc, false);
				hud(mc, true);
				modules.colors.setEnabled(true);
				modules.colorSaturation.set(0);
				wait = 15;
				return true;
			case 8:
				actions.shot("cubiqclient-colors-0");
				modules.colorSaturation.set(200);
				wait = 10;
				return true;
			case 9:
				actions.shot("cubiqclient-colors-200");
				CubiqClient.LOGGER.info("[Autotest] Farben: Durchgang lief {}× (unterstützt={})", ColorPass.applied,
						ColorPass.supported());
				modules.colorSaturation.set(140);
				modules.colorContrast.set(115);
				modules.colorTemperature.set(40);
				Mc.setScreen(new CubiqMenuScreen(null).select(modules.colors));
				wait = 20;
				return true;
			case 10:
				actions.shot("cubiqclient-colors-menu");
				Mc.setScreen(null);
				modules.colors.reset();
				return false;
			default:
				return false;
		}
	}

	private static void camera(Minecraft mc, boolean thirdPerson) {
		//? if >=1.16 {
		mc.options.setCameraType(thirdPerson ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
		//?} else {
		/*mc.options.thirdPersonView = thirdPerson ? 1 : 0;
		*///?}
	}

	private static void hud(Minecraft mc, boolean visible) {
		//? if >=26.2 {
		/*if (mc.gui.hud.isHidden() == visible) mc.gui.hud.toggle();
		*///?} else {
		mc.options.hideGui = !visible;
		//?}
	}
}
