package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.map.MapEngine;
import dev.pwiepixel.cubiqclient.core.map.WorldMapUi;
import dev.pwiepixel.cubiqclient.ui.Gfx;

/**
 * Vollbild-Weltkarte ({@link WorldMapUi} aus {@code core.map}); hier nur die Anbindung. Zeichnet den ganzen
 * Bildschirm selbst (kein Vanilla-Weichzeichner). Dieselbe Datei in allen Mojmap-Bäumen.
 */
public final class WorldMapScreen extends CubiqUiScreen {
	private WorldMapScreen(WorldMapUi ui) {
		super(I18n.tr("map.title"), ui);
	}

	/** Öffnet die Weltkarte (null, solange die Karte nicht läuft). */
	public static WorldMapScreen create() {
		MapEngine engine = MapEngine.get();
		if (engine == null) return null;
		WorldMapUi.Host host = new WorldMapUi.Host() {
			@Override
			public void closeScreen() {
				Mc.setScreen(null);
			}

			@Override
			public void playClick() {
				new CubiqMenuHost(null).playClick();
			}

			@Override
			public boolean isMapKey(int rawKey) {
				return CubiqKeys.worldMap != null && rawKey == CubiqKeys.boundKey(CubiqKeys.worldMap);
			}
		};
		return new WorldMapScreen(new WorldMapUi(engine, host));
	}

	@Override
	protected boolean customBackground() {
		return true;
	}

	@Override
	protected void drawBackground(Gfx g, float partialTick) {
		// Die Karte deckt alles ab.
	}
}
