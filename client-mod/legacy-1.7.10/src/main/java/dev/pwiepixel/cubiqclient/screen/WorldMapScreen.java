package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.core.map.MapEngine;
import dev.pwiepixel.cubiqclient.core.map.WorldMapUi;
import net.minecraft.client.Minecraft;

/** Vollbild-Weltkarte ({@link WorldMapUi} aus {@code core.map}) für Forge 1.7.10; hier nur die Anbindung. */
public final class WorldMapScreen extends CubiqUiScreen {
	private WorldMapScreen(WorldMapUi ui) {
		super(ui);
	}

	/** Öffnet die Weltkarte (null, solange die Karte nicht läuft). */
	public static WorldMapScreen create() {
		MapEngine engine = MapEngine.get();
		if (engine == null) return null;
		WorldMapUi.Host host = new WorldMapUi.Host() {
			@Override
			public void closeScreen() {
				Minecraft.getMinecraft().displayGuiScreen(null);
			}

			@Override
			public void playClick() {
				new CubiqMenuHost(null).playClick();
			}

			@Override
			public boolean isMapKey(int rawKey) {
				return CubiqKeys.worldMap != null && rawKey != 0 && rawKey == CubiqKeys.worldMap.getKeyCode();
			}
		};
		return new WorldMapScreen(new WorldMapUi(engine, host));
	}

	@Override
	protected boolean vanillaBackground() {
		// Die Karte deckt alles ab.
		return false;
	}
}
