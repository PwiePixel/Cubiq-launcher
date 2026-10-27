package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.core.clips.ClipStatus;
import dev.pwiepixel.cubiqclient.core.clips.Clips;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

/**
 * Selbsttest Clips & Aufnahme (Forge 1.8.9–1.12.2, allein mit {@code -PcubiqAutotestOnly=clips}): drückt die
 * Tastenbelegungen F9/F10 über Minecrafts eigene Tastenverarbeitung ({@link KeyBinding#onTick}) und macht
 * Screenshots des HUD-Elements.
 */
public final class ClipsTest {
	private int phase;
	private int wait;
	private int waited;

	private static void log(String what) {
		ClipStatus s = Clips.get().status();
		CubiqClient.LOGGER.info("[Autotest] Clips {}: verbunden={} verfügbar={} grund={} puffer={} aufnahme={} clip={}s", what,
				s.connected, s.available, s.reason, s.buffer, s.recording, s.clipSeconds);
	}

	/** Wie ein echter Tastendruck: die Belegung (F9/F10 oder vom Spieler geändert) zählt einen Klick. */
	private static void press(KeyBinding binding) {
		KeyBinding.onTick(binding.getKeyCode());
	}

	/** Ein Tick; true = noch nicht fertig. */
	public boolean step(Minecraft mc, CubiqModules modules, CapeTest.Actions actions) {
		if (dev.pwiepixel.cubiqclient.compat.Mc.player() == null) return false;
		if (wait > 0) {
			wait--;
			return true;
		}
		switch (phase++) {
			case 0:
				modules.clips.setEnabled(true);
				modules.clips.resetLayout();
				log("Start");
				wait = 20;
				return true;
			case 1:
				if (!Clips.get().status().buffer && waited++ < 60) {
					phase--;
					wait = 20;
					return true;
				}
				log("vor F9");
				actions.shot("clips-buffer");
				press(CubiqKeys.saveClip);
				wait = 50;
				return true;
			case 2:
				log("nach F9");
				actions.shot("clips-saved");
				press(CubiqKeys.toggleRecording);
				wait = 100;
				return true;
			case 3:
				log("Aufnahme");
				actions.shot("clips-recording");
				press(CubiqKeys.toggleRecording);
				wait = 50;
				return true;
			case 4:
				log("nach Stopp");
				actions.shot("clips-recording-saved");
				return false;
			default:
				return false;
		}
	}
}
