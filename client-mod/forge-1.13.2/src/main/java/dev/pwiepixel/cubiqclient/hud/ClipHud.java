package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.clips.ClipPanel;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.TextWidth;
import dev.pwiepixel.cubiqclient.ui.BrandCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Clips & Aufnahme im HUD (Forge 1.13.2). Gezeichnet wird versionsunabhängig in
 * {@code core.clips.ClipPanel} über {@link BrandCanvas}.
 */
public final class ClipHud extends HudElement {
	private static final TextWidth MEASURE = new TextWidth() {
		@Override
		public int width(String text) {
			return Minecraft.getInstance().fontRenderer.getStringWidth(text);
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
	public int width(FontRenderer font, boolean preview) {
		return panel.width(MEASURE, preview);
	}

	@Override
	public int height(FontRenderer font, boolean preview) {
		return panel.height(preview);
	}

	@Override
	public void draw(FontRenderer font, boolean preview) {
		panel.draw(BrandCanvas.of(font), MEASURE, preview);
	}
}
