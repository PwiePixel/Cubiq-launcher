package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.camera.FreelookState;
import dev.pwiepixel.cubiqclient.core.input.MouseScaler;
import dev.pwiepixel.cubiqclient.dev.HookStats;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MouseHelper;

/**
 * Ersetzt {@code Minecraft.mouseHelper}: teilt die Mausbewegung während des Zooms durch den
 * Zoom-Faktor (langsamere Maus) und lenkt sie bei Freelook auf die Kamera statt auf die Spielfigur.
 */
final class ZoomMouseHelper extends MouseHelper {
	private final CubiqClient client;
	private final MouseScaler scaler = new MouseScaler();

	ZoomMouseHelper(CubiqClient client) {
		this.client = client;
	}

	@Override
	public void mouseXYChange() {
		super.mouseXYChange();
		HookStats.mouse++;
		// Gleiche Umrechnung wie EntityRenderer#updateCameraAndRender → Entity#setAngles.
		GameSettings o = Mc.mc().gameSettings;
		float f = o.mouseSensitivity * 0.6F + 0.2F;
		float f1 = f * f * f * 8.0F;
		int invert = o.invertMouse ? -1 : 1;
		// Kamera-Fahrt: die Maus dreht weder Figur noch Kamera; freie Kamera: die Maus dreht nur die Kamera.
		if (dev.pwiepixel.cubiqclient.cinematic.LegacyCinematic.turn(deltaX * f1, -deltaY * f1 * invert)) {
			deltaX = 0;
			deltaY = 0;
			return;
		}
		FreelookState freelook = client.pvp().freelook();
		if (freelook.active()) {
			freelook.turn(deltaX * f1, -deltaY * f1 * invert);
			deltaX = 0;
			deltaY = 0;
			return;
		}
		scaler.scale(deltaX, deltaY, client.mouseDivisor());
		deltaX = scaler.x();
		deltaY = scaler.y();
	}
}
