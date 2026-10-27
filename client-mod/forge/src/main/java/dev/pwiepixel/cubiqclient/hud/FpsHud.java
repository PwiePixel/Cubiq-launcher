package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.module.HudModule;

/** Bilder pro Sekunde. */
public final class FpsHud extends TextHudElement {
	public FpsHud(HudModule module) {
		super(module);
	}

	@Override
	protected long valueKey(boolean preview) {
		return mc.getFps();
	}

	@Override
	protected String format(long key) {
		return key + " FPS";
	}
}
