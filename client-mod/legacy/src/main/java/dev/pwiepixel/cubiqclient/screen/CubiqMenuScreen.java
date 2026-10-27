package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.ui.menu.ModMenu;
import net.minecraft.client.gui.GuiScreen;

/**
 * Das Cubiq-Menü (Rechte Umschalttaste). Der Inhalt – Kacheln, Suche, Kategorien, Einstellungen,
 * Profile – steht versionsunabhängig in {@link ModMenu}.
 */
public final class CubiqMenuScreen extends CubiqUiScreen {
	private final ModMenu menu;

	public CubiqMenuScreen(GuiScreen parent) {
		this(new ModMenu(new CubiqMenuHost(parent)));
	}

	private CubiqMenuScreen(ModMenu menu) {
		super("Cubiq Client", menu);
		this.menu = menu;
	}

	/** Öffnet das Menü direkt bei einem Modul (z. B. aus dem Autotest). */
	public CubiqMenuScreen select(Module module) {
		menu.select(module);
		return this;
	}

	/** Öffnet das Menü direkt bei den HUD-Profilen. */
	public CubiqMenuScreen scrollSettings(int pixels) {
		menu.scrollSettings(pixels);
		return this;
	}

	public CubiqMenuScreen showCategory(dev.pwiepixel.cubiqclient.core.module.Category category) {
		menu.showCategory(category);
		return this;
	}

	public CubiqMenuScreen showProfiles() {
		menu.showProfiles();
		return this;
	}
}
