package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.module.HudModule;
import net.minecraft.client.Minecraft;

/** Bilder pro Sekunde. */
public final class FpsHud extends TextHudElement {
	public FpsHud(HudModule module) {
		super(module);
	}

	@Override
	protected long valueKey(boolean preview) {
		return Minecraft.getDebugFPS();
	}

	@Override
	protected String format(long key) {
		return key + " FPS";
	}
}
