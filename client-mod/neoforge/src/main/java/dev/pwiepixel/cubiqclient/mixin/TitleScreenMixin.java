package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.CubiqClient;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//? if >=26.2 {
/*import net.minecraft.client.gui.Gui;
*///?} else
import net.minecraft.client.Minecraft;

/**
 * Cubiq-Startbildschirm: ersetzt den Vanilla-Titelbildschirm, sobald er geöffnet werden soll
 * (Modul "Startbildschirm"). setScreen liegt bis 26.1.2 in Minecraft, ab 26.2 in Gui.
 */
//? if >=26.2 {
/*@Mixin(Gui.class)
*///?} else
@Mixin(Minecraft.class)
public abstract class TitleScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 1)
	private Screen cubiqclient$replaceTitleScreen(Screen screen) {
		CubiqClient client = CubiqClient.get();
		return client == null ? screen : client.replaceScreen(screen);
	}
}
