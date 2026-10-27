package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import dev.pwiepixel.cubiqclient.core.ui.menu.HudEditor;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;

/**
 * „HUD bearbeiten“: Elemente verschieben (mit Einrasten und Hilfslinien), Größe, Hintergrund,
 * Schatten und Chroma je Modul, Profilwechsel. Die Logik steht in {@link HudEditor}.
 */
public final class HudEditorScreen extends CubiqUiScreen {
	private final HudEditor editor;

	public HudEditorScreen(GuiScreen parent) {
		this(new HudEditor(new CubiqMenuHost(parent)));
	}

	private HudEditorScreen(HudEditor editor) {
		super(I18n.tr("editor.title"), editor);
		this.editor = editor;
	}

	/** Öffnet den Editor mit ausgewähltem erstem Element (Selbsttest). */
	public HudEditorScreen selectFirst() {
		editor.selectFirst();
		return this;
	}

	/** Kein Vanilla-Hintergrund – das Spiel soll beim Einrichten sichtbar bleiben. */
	@Override
	protected boolean customBackground() {
		return true;
	}

	@Override
	protected void drawBackground(Gfx g, float partialTick) {
		// Der Editor zeichnet seinen Hintergrund selbst (abhängig davon, ob eine Welt läuft).
	}
}
