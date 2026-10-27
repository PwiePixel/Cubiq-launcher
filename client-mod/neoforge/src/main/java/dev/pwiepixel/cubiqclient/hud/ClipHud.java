package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.clips.ClipPanel;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.TextWidth;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import dev.pwiepixel.cubiqclient.ui.GfxCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Clips & Aufnahme im HUD (roter Punkt + Zeit, Puffer-Symbol, "Clip gespeichert"). Gezeichnet wird
 * versionsunabhängig in {@code core.clips.ClipPanel} – hier nur die Anbindung.
 */
public final class ClipHud extends HudElement {
	private static final TextWidth MEASURE = new TextWidth() {
		@Override
		public int width(String text) {
			return Minecraft.getInstance().font.width(text);
		}
	};

	private final ClipPanel panel;

	public ClipHud(CubiqModules modules) {
		super(modules.clips);
		this.panel = new ClipPanel(modules);
	}

	@Override
	public boolean visible() {
		return panel.visible();
	}

	@Override
	public int width(Font font, boolean preview) {
		return panel.width(MEASURE, preview);
	}

	@Override
	public int height(Font font, boolean preview) {
		return panel.height(preview);
	}

	@Override
	public void draw(Gfx g, Font font, boolean preview) {
		panel.draw(GfxCanvas.of(g, font), MEASURE, preview);
	}
}
