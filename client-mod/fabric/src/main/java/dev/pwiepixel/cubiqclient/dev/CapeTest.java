package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.online.OnlineHooks;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.16 {
import net.minecraft.client.CameraType;
//?}

/**
 * Selbsttest für Cubiq-Umhang, Umhang-Physik und Abzeichen (Teil des Autotests, gleicher Code für alle Loader):
 * Third-Person-Ansicht von hinten auf einer frei geräumten Bahn, warten bis der Cubiq-Umhang (lokale API-Attrappe,
 * {@code -PcubiqApi=http://127.0.0.1:8787}) geladen ist, dann Screenshots: Stehen, nächstes Animationsbild,
 * Laufen, Springen, Schleichen und die Tabliste mit Abzeichen.
 */
public final class CapeTest {
	/** Screenshot und Server-Befehl kommen aus dem Autotest des jeweiligen Loaders. */
	public interface Actions {
		void shot(String name);

		void command(String command);
	}

	private int phase;
	private int wait;
	private int tries;

	/** Ein Tick; true = noch nicht fertig. */
	public boolean step(Minecraft mc, CubiqModules modules, Actions actions) {
		if (mc.player == null) return false;
		if (phase > 1) {
			// Das Testfenster kann den Fokus bekommen – Mausbewegungen des Benutzers sollen die Kamera nicht drehen.
			mc.mouseHandler.releaseMouse();
			face(mc, 0f, 15f);
		}
		if (wait > 0) {
			wait--;
			return true;
		}
		switch (phase++) {
			case 0:
				modules.cubiqOnline.setEnabled(true);
				modules.cubiqCapes.set(true);
				modules.badgeTab.set(true);
				modules.badgeNametag.set(true);
				modules.capePhysics.setEnabled(true);
				modules.minimap.setEnabled(false);
				CubiqClient.get().sprintToggle().set(false);
				//? if >=1.17 {
				actions.command("item replace entity @p armor.chest with air");
				//?} else
				//actions.command("replaceitem entity @p armor.chest air");
				actions.command("time set day");
				actions.command("weather clear");
				// Freie, ebene Bahn nach Süden (Blickrichtung), damit Laufen/Springen frei sichtbar ist.
				actions.command("execute as @p at @s run fill ~-6 ~ ~-8 ~6 ~7 ~28 air");
				actions.command("execute as @p at @s run fill ~-6 ~-1 ~-8 ~6 ~-1 ~28 grass_block");
				actions.command("scoreboard objectives add cubiqtest dummy");
				actions.command("scoreboard objectives setdisplay list cubiqtest");
				actions.command("execute as @p at @s run tp @s ~ ~ ~ 0 5");
				camera(mc, true);
				hud(mc, false);
				modules.zoomFactor.set(2.0);
				CubiqClient.get().setForceZoom(true);
				wait = 5;
				return true;
			case 1: {
				// Auf Anmeldung + Textur warten (höchstens 20 s)
				Object tex = OnlineHooks.capeTexture(mc.player);
				if (tex == null && tries++ < 400) {
					phase = 1;
					return true;
				}
				CubiqClient.LOGGER.info("[Autotest] Cubiq-Umhang: {} (Status {}, Physik aktiv für {} Spieler)",
						tex != null ? tex : "NICHT geladen", OnlineHooks.features().online().status(),
						OnlineHooks.features().physics().active());
				wait = 40;
				return true;
			}
			case 2:
				actions.shot("cubiqclient-cape-stand");
				wait = 3;
				return true;
			case 3:
				actions.shot("cubiqclient-cape-frame");
				hold(mc.options.keyUp, true);
				mc.player.setSprinting(true);
				wait = 25;
				return true;
			case 4:
				actions.shot("cubiqclient-cape-walk");
				hold(mc.options.keyUp, false);
				hold(mc.options.keyJump, true);
				wait = 2;
				return true;
			case 5:
				hold(mc.options.keyJump, false);
				wait = 7;
				return true;
			case 6:
				actions.shot("cubiqclient-cape-jump");
				wait = 20;
				return true;
			case 7:
				hold(sneakKey(mc), true);
				wait = 25;
				return true;
			case 8:
				actions.shot("cubiqclient-cape-sneak");
				hold(sneakKey(mc), false);
				// Elytra mit Cubiq-Umhang: immer die Vanilla-Elytra (der Cubiq-Umhang wird nie als Elytra-Textur genutzt).
				//? if >=1.17 {
				actions.command("item replace entity @p armor.chest with elytra");
				//?} else
				//actions.command("replaceitem entity @p armor.chest elytra");
				wait = 20;
				return true;
			case 9:
				actions.shot("cubiqclient-cape-elytra");
				wait = 3;
				return true;
			case 10:
				actions.shot("cubiqclient-cape-elytra-frame");
				cameraFront(mc);
				wait = 5;
				return true;
			case 11:
				actions.shot("cubiqclient-cape-elytra-front");
				camera(mc, true);
				//? if >=1.17 {
				actions.command("item replace entity @p armor.chest with air");
				//?} else
				//actions.command("replaceitem entity @p armor.chest air");
				hud(mc, true);
				CubiqClient.get().setForceZoom(false);
				hold(mc.options.keyPlayerList, true);
				wait = 5;
				return true;
			case 12:
				actions.shot("cubiqclient-cape-tab");
				hold(mc.options.keyPlayerList, false);
				CubiqClient.LOGGER.info("[Autotest] Abzeichen Tabliste={} Name={} → \"{}\"",
						OnlineHooks.badge(mc.player.getUUID(), true), OnlineHooks.badge(mc.player.getUUID(), false),
						OnlineHooks.badged(OnlineHooks.text("Spieler")).getString());
				actions.command("scoreboard objectives remove cubiqtest");
				camera(mc, false);
				KeyMapping.releaseAll();
				return false;
			default:
				return false;
		}
	}

	/** Blickrichtung des Spielers setzen (Süden = 0). */
	private static void face(Minecraft mc, float yaw, float pitch) {
		//? if >=1.17 {
		mc.player.setYRot(yaw);
		mc.player.setXRot(pitch);
		//?} else {
		/*mc.player.yRot = yaw;
		mc.player.xRot = pitch;
		*///?}
		mc.player.yHeadRot = yaw;
	}

	private static void camera(Minecraft mc, boolean thirdPerson) {
		//? if >=1.16 {
		mc.options.setCameraType(thirdPerson ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
		//?} else
		/*mc.options.thirdPersonView = thirdPerson ? 1 : 0;*/
	}

	/** Third-Person von vorn (Innenseite der Flügel). */
	private static void cameraFront(Minecraft mc) {
		//? if >=1.16 {
		mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		//?} else
		/*mc.options.thirdPersonView = 2;*/
	}

	private static void hud(Minecraft mc, boolean visible) {
		//? if >=26.2 {
		/*if (mc.gui.hud.isHidden() == visible) mc.gui.hud.toggle();
		*///?} else
		mc.options.hideGui = !visible;
	}

	/** Taste "Schleichen" (bis 1.14 keySneak, danach keyShift). */
	private static KeyMapping sneakKey(Minecraft mc) {
		//? if >=1.15 {
		return mc.options.keyShift;
		//?} else
		/*return mc.options.keySneak;*/
	}

	private static void hold(KeyMapping key, boolean down) {
		//? if >=1.15 {
		key.setDown(down);
		//?} else
		/*((dev.pwiepixel.cubiqclient.mixin.KeyMappingAccessor) key).cubiqclient$setDown(down);*/
	}
}
