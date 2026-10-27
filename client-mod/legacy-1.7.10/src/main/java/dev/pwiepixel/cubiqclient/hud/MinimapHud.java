package dev.pwiepixel.cubiqclient.hud;

import dev.pwiepixel.cubiqclient.core.map.MapEngine;
import dev.pwiepixel.cubiqclient.core.map.MinimapRenderer;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.map.MapBridge;
import dev.pwiepixel.cubiqclient.ui.Brand;
import dev.pwiepixel.cubiqclient.ui.BrandCanvas;
import net.minecraft.client.gui.FontRenderer;

/**
 * Minimap im HUD (Forge 1.7.10) – nur die Anbindung: Abtasten/Speichern/Zeichnen stecken in {@code core.map}.
 */
public final class MinimapHud extends HudElement {
	private final CubiqModules modules;
	private final MapBridge bridge;
	private final MinimapRenderer renderer = new MinimapRenderer();
	private boolean drawing;

	/** Scissor für die gedrehte eckige Karte (GL-Scissor in GUI-Koordinaten des Bildschirms). */
	private final MinimapRenderer.Clip clip = new MinimapRenderer.Clip() {
		@Override
		public boolean begin(int x1, int y1, int x2, int y2) {
			if (!drawing) return false;
			float s = originScale;
			Brand.scissor(Math.round(originX + x1 * s), Math.round(originY + y1 * s), Math.round(originX + x2 * s),
					Math.round(originY + y2 * s));
			return true;
		}

		@Override
		public void end() {
			Brand.noScissor();
		}
	};

	public MinimapHud(CubiqModules modules) {
		super(modules.minimap);
		this.modules = modules;
		this.bridge = new MapBridge(modules, dev.pwiepixel.cubiqclient.core.i18n.I18n.configDir().resolve("cubiqclient-waypoints.json"));
	}

	@Override
	public boolean visible() {
		MapEngine e = MapEngine.get();
		return mc.theWorld != null && mc.thePlayer != null && (e == null || e.fairPlay().minimapAllowed());
	}

	@Override
	public int width(FontRenderer font, boolean preview) {
		return renderer.width(modules);
	}

	@Override
	public int height(FontRenderer font, boolean preview) {
		return renderer.height(modules);
	}

	/** Einmal je Client-Tick: Todespunkt, Karte abtasten (Zeitbudget), Spieler/Kreaturen merken. */
	public void tick() {
		bridge.tickDeath();
		MapEngine e = MapEngine.get();
		if (e != null) e.tick(bridge);
	}

	@Override
	public void draw(FontRenderer font, boolean preview) {
		MapEngine e = MapEngine.get();
		if (e == null) return;
		double scale = new net.minecraft.client.gui.ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor()
				* module.scale.getFloat();
		drawing = !preview;
		try {
			renderer.draw(BrandCanvas.of(font), e, preview, scale, preview ? null : clip);
		} finally {
			drawing = false;
		}
	}
}
