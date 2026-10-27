package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.ui.clips.ClipsHost;
import dev.pwiepixel.cubiqclient.core.ui.clips.ClipsUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.nio.file.Path;

/**
 * Bildschirm „Clips &amp; Bilder“ in dieser Version (aus dem Cubiq-Menü). Freunde, Server-Info und der Menü-Stil
 * brauchen die Cubiq-Online-Funktionen bzw. Mixins und fehlen hier.
 */
public final class MenuScreens {
	private MenuScreens() {
	}

	public static GuiScreen clips(final GuiScreen parent) {
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		final Path gameDir = Minecraft.getMinecraft().mcDataDir.toPath().toAbsolutePath();
		ClipsHost host = new ClipsHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public Path gameDir() {
				return gameDir;
			}

			@Override
			public Path configDir() {
				return gameDir.resolve("config");
			}
		};
		return new CubiqUiScreen(new ClipsUi(host));
	}
}
