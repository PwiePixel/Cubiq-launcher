package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.notes.NotePanel;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.TextWidth;
import dev.pwiepixel.cubiqclient.ui.BrandCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Angeheftete Notiz im HUD (Notizen je Welt). Gezeichnet wird versionsunabhängig in
 * {@code core.notes.NotePanel} – hier nur die Anbindung.
 */
public final class NotesHud extends HudElement {
	private static final TextWidth MEASURE = new TextWidth() {
		@Override
		public int width(String text) {
			return Minecraft.getInstance().fontRenderer.getStringWidth(text);
		}
	};

	private final NotePanel panel;

	public NotesHud(CubiqModules modules) {
		super(modules.notes.pinnedNote);
		this.panel = new NotePanel(modules);
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
		return panel.height(MEASURE, preview);
	}

	@Override
	public void draw(FontRenderer font, boolean preview) {
		panel.draw(BrandCanvas.of(font), MEASURE, preview);
	}
}
