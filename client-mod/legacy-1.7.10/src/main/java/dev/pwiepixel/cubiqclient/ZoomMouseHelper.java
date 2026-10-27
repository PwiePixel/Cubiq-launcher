package dev.pwiepixel.cubiqclient;

import dev.pwiepixel.cubiqclient.core.input.MouseScaler;
import dev.pwiepixel.cubiqclient.dev.HookStats;
import net.minecraft.util.MouseHelper;

/**
 * Ersetzt {@code Minecraft.mouseHelper}: teilt die Mausbewegung während des Zooms durch den
 * Zoom-Faktor (langsamere Maus). Ohne Zoom bleiben die Werte unverändert.
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
		scaler.scale(deltaX, deltaY, client.mouseDivisor());
		deltaX = scaler.x();
		deltaY = scaler.y();
	}
}
