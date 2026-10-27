package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.HudModule;

/** Bilder pro Sekunde. */
public final class FpsHud extends TextHudElement {
	public FpsHud(HudModule module) {
		super(module);
	}

	@Override
	protected long valueKey(boolean preview) {
		return Mc.fps();
	}

	@Override
	protected String format(long key) {
		return key + " FPS";
	}
}
