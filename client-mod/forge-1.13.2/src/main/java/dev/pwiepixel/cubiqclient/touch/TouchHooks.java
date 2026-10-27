package dev.pwiepixel.cubiqclient.touch;

import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.core.touch.TouchKeyboard;
import dev.pwiepixel.cubiqclient.core.touch.TouchMode;
import dev.pwiepixel.cubiqclient.core.touch.TouchRuntime;
import dev.pwiepixel.cubiqclient.screen.HudEditorScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiEditSign;

/**
 * Touch-Modus (mobile Engine, {@code -Dcubiq.touch=true}) je Client-Tick unter Forge 1.13.2: feste Overlay-Tasten
 * (ohne Emote-Rad – das gibt es hier nicht) und Bildschirmtastatur. Ohne Touch-Modus passiert nichts.
 */
public final class TouchHooks {
	private static final TouchRuntime.Platform PLATFORM = new TouchRuntime.Platform() {
		@Override
		public boolean keyDown(String keyName) {
			return Keys.isDown(keyName);
		}

		@Override
		public boolean screenOpen() {
			return Minecraft.getInstance().currentScreen != null;
		}

		@Override
		public boolean inWorld() {
			return false;
		}

		@Override
		public void openMenu() {
			Minecraft.getInstance().displayGuiScreen(new CubiqMenuScreen(null));
		}

		@Override
		public void openEmoteWheel() {
			// Kein Emote-Rad unter 1.13.2.
		}

		@Override
		public void openHudEditor() {
			Minecraft.getInstance().displayGuiScreen(new HudEditorScreen(null));
		}

		@Override
		public String vanillaField() {
			return field(Minecraft.getInstance().currentScreen);
		}

		@Override
		public double guiScale() {
			return Minecraft.getInstance().mainWindow.getGuiScaleFactor();
		}
	};

	private TouchHooks() {
	}

	/** Aus dem Client-Tick. */
	public static void tick() {
		if (!TouchMode.enabled()) return;
		TouchRuntime.tick(PLATFORM);
	}

	/** Art des Vanilla-Textfelds (Chat, Schild, Amboss) oder null. */
	static String field(GuiScreen screen) {
		if (screen == null) return null;
		if (screen instanceof GuiChat) return TouchKeyboard.FIELD_CHAT;
		if (screen instanceof GuiEditSign) return TouchKeyboard.FIELD_SIGN;
		if (screen instanceof GuiRepair) return TouchKeyboard.FIELD_ANVIL;
		return null;
	}
}
