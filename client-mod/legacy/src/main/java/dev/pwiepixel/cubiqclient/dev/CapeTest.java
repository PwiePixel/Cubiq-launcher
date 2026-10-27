package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.online.LegacyOnline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Selbsttest für Cubiq-Umhang, Umhang-Physik und Abzeichen (Forge 1.8.9–1.12.2): Third-Person-Ansicht von hinten
 * auf einer frei geräumten Bahn, warten bis der Cubiq-Umhang (lokale API-Attrappe, {@code -PcubiqApi=…}) geladen ist,
 * dann Screenshots: Stehen, nächstes Animationsbild, Laufen, Springen, Schleichen und die Tabliste mit Abzeichen.
 */
public final class CapeTest {
	/** Screenshot und Server-Befehl kommen aus dem Autotest. */
	public interface Actions {
		void shot(String name);

		void command(String command);
	}

	/** Elytra gibt es ab 1.9 (Screenshots mit Elytra nur dort). */
	//? if >=1.9 {
	/*private static final boolean ELYTRA = true;
	*///?} else
	private static final boolean ELYTRA = false;

	private int phase;
	private int wait;
	private int tries;

	/** Ein Tick; true = noch nicht fertig. */
	public boolean step(Minecraft mc, CubiqModules modules, Actions actions) {
		EntityPlayer player = Mc.player();
		if (player == null) return false;
		if (phase > 1) {
			// Das Testfenster kann den Fokus bekommen – Mausbewegungen des Benutzers sollen die Kamera nicht drehen.
			mc.setIngameNotInFocus();
			player.rotationYaw = 0f;
			player.rotationPitch = 15f;
			player.rotationYawHead = 0f;
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
				actions.command("replaceitem entity @p slot.armor.chest air");
				actions.command("time set 1000");
				actions.command("weather clear");
				// Freie, ebene Bahn nach Süden (Blickrichtung), damit Laufen/Springen frei sichtbar ist.
				actions.command("execute @p ~ ~ ~ fill ~-6 ~ ~-8 ~6 ~7 ~28 air");
				actions.command("execute @p ~ ~ ~ fill ~-6 ~-1 ~-8 ~6 ~-1 ~28 grass");
				actions.command("scoreboard objectives add cubiqtest dummy");
				actions.command("scoreboard objectives setdisplay list cubiqtest");
				mc.gameSettings.thirdPersonView = 1;
				mc.gameSettings.hideGUI = true;
				modules.zoomFactor.set(2.0);
				CubiqClient.get().setForceZoom(true);
				wait = 5;
				return true;
			case 1: {
				// Auf Anmeldung + Textur warten (höchstens 20 s)
				Object tex = LegacyOnline.features().capeTexture(player.getUniqueID());
				if (tex == null && tries++ < 400) {
					phase = 1;
					return true;
				}
				CubiqClient.LOGGER.info("[Autotest] Cubiq-Umhang: {} (Status {}, Physik aktiv für {} Spieler, Umhang-Textur {})",
						tex != null ? tex : "NICHT geladen", LegacyOnline.features().online().status(),
						LegacyOnline.features().physics().active(), Mc.player() == null ? null : cape(mc));
				wait = 40;
				return true;
			}
			case 2:
				actions.shot("cape-stand");
				wait = 3;
				return true;
			case 3:
				actions.shot("cape-frame");
				hold(mc.gameSettings.keyBindForward, true);
				player.setSprinting(true);
				wait = 25;
				return true;
			case 4:
				actions.shot("cape-walk");
				hold(mc.gameSettings.keyBindForward, false);
				hold(mc.gameSettings.keyBindJump, true);
				wait = 2;
				return true;
			case 5:
				hold(mc.gameSettings.keyBindJump, false);
				wait = 7;
				return true;
			case 6:
				actions.shot("cape-jump");
				wait = 20;
				return true;
			case 7:
				hold(mc.gameSettings.keyBindSneak, true);
				wait = 25;
				return true;
			case 8:
				actions.shot("cape-sneak");
				hold(mc.gameSettings.keyBindSneak, false);
				if (ELYTRA) {
					// Elytra mit Cubiq-Umhang: immer die Vanilla-Elytra (der Cubiq-Umhang wird nie als Elytra-Textur genutzt).
					actions.command("replaceitem entity @p slot.armor.chest minecraft:elytra");
					wait = 20;
				} else {
					phase = 11;
				}
				return true;
			case 9:
				actions.shot("cape-elytra");
				wait = 3;
				return true;
			case 10:
				actions.shot("cape-elytra-frame");
				mc.gameSettings.thirdPersonView = 2;
				wait = 5;
				return true;
			case 11:
				if (ELYTRA) {
					actions.shot("cape-elytra-front");
					mc.gameSettings.thirdPersonView = 1;
					actions.command("replaceitem entity @p slot.armor.chest air");
				}
				mc.gameSettings.hideGUI = false;
				CubiqClient.get().setForceZoom(false);
				hold(mc.gameSettings.keyBindPlayerList, true);
				wait = 5;
				return true;
			case 12: {
				actions.shot("cape-tab");
				hold(mc.gameSettings.keyBindPlayerList, false);
				NetworkPlayerInfo info = Mc.connection() == null ? null : Mc.connection().getPlayerInfo(player.getUniqueID());
				CubiqClient.LOGGER.info("[Autotest] Abzeichen Tabliste={} Name={} → Tabliste \"{}\", Namensschild \"{}\"",
						LegacyOnline.features().badge(player.getUniqueID(), true),
						LegacyOnline.features().badge(player.getUniqueID(), false),
						info == null || info.getDisplayName() == null ? null : info.getDisplayName().getFormattedText(),
						player.getDisplayName().getFormattedText());
				actions.command("scoreboard objectives remove cubiqtest");
				mc.gameSettings.thirdPersonView = 0;
				KeyBinding.unPressAllKeys();
				return false;
			}
			default:
				return false;
		}
	}

	private static Object cape(Minecraft mc) {
		return ((net.minecraft.client.entity.AbstractClientPlayer) Mc.player()).getLocationCape();
	}

	private static void hold(KeyBinding key, boolean down) {
		KeyBinding.setKeyBindState(key.getKeyCode(), down);
	}
}
