package dev.pwiepixel.cubiqclient.touch;

import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.touch.TouchKeyboard;
import dev.pwiepixel.cubiqclient.core.touch.TouchMode;
import dev.pwiepixel.cubiqclient.core.touch.TouchRuntime;
import dev.pwiepixel.cubiqclient.online.LegacyEmotes;
import dev.pwiepixel.cubiqclient.screen.EmoteWheelScreen;
import dev.pwiepixel.cubiqclient.screen.HudEditorScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;

/**
 * Touch-Modus (mobile Engine, {@code -Dcubiq.touch=true}) je Client-Tick unter Forge 1.8.9–1.12.2: feste
 * Overlay-Tasten und Bildschirmtastatur. Die Logik steht in {@link TouchRuntime}; ohne Touch-Modus passiert nichts.
 */
public final class TouchHooks {
	private static final TouchRuntime.Platform PLATFORM = new TouchRuntime.Platform() {
		@Override
		public boolean keyDown(String keyName) {
			return Keys.isDown(keyName);
		}

		@Override
		public boolean screenOpen() {
			return Mc.mc().currentScreen != null;
		}

		@Override
		public boolean inWorld() {
			return Mc.player() != null && LegacyEmotes.enabled();
		}

		@Override
		public void openMenu() {
			Mc.mc().displayGuiScreen(new CubiqMenuScreen(null));
		}

		@Override
		public void openEmoteWheel() {
			Mc.mc().displayGuiScreen(new EmoteWheelScreen());
		}

		@Override
		public void openHudEditor() {
			Mc.mc().displayGuiScreen(new HudEditorScreen(null));
		}

		@Override
		public String vanillaField() {
			return field(Mc.mc().currentScreen);
		}

		@Override
		public double guiScale() {
			return Mc.scaledResolution().getScaleFactor();
		}
	};

	private TouchHooks() {
	}

	/** Aus dem Client-Tick. */
	public static void tick() {
		if (!TouchMode.enabled()) return;
		TouchRuntime.tick(PLATFORM);
	}

	/** Art des Vanilla-Textfelds (Chat, Schild, Amboss) oder null. Bücher nicht: dieselbe Klasse dient auch zum Lesen. */
	static String field(GuiScreen screen) {
		if (screen == null) return null;
		if (screen instanceof GuiChat) return TouchKeyboard.FIELD_CHAT;
		if (screen instanceof GuiEditSign) return TouchKeyboard.FIELD_SIGN;
		if (screen instanceof GuiRepair) return TouchKeyboard.FIELD_ANVIL;
		return null;
	}
}
