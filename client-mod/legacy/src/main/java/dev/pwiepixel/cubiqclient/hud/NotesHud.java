package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.notes.NotePanel;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.TextWidth;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import dev.pwiepixel.cubiqclient.ui.GfxCanvas;
import net.minecraft.client.gui.FontRenderer;

/**
 * Angeheftete Notiz im HUD (Notizen je Welt). Gezeichnet wird versionsunabhängig in
 * {@code core.notes.NotePanel} – hier nur die Anbindung.
 */
public final class NotesHud extends HudElement {
	private static final TextWidth MEASURE = new TextWidth() {
		@Override
		public int width(String text) {
			return Mc.font().getStringWidth(text);
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
	public void draw(Gfx g, FontRenderer font, boolean preview) {
		panel.draw(GfxCanvas.of(g, font), MEASURE, preview);
	}
}
