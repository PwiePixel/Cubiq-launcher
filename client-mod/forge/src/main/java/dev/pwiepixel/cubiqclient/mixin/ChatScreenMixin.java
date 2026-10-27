package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.CubiqClient;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

/**
 * Strg+Klick auf eine Chat-Zeile kopiert sie in die Zwischenablage; Klick auf markierte Koordinaten bzw.
 * „[Als Link teilen]“ geht an {@link dev.pwiepixel.cubiqclient.qol.ChatLinks} (ab 1.16).
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	//? if >=1.21.11 {
	/*@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$copyLine(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		int b = event.button() == dev.pwiepixel.cubiqclient.compat.Keys.MOUSE_LEFT ? 0
				: event.button() == dev.pwiepixel.cubiqclient.compat.Keys.MOUSE_RIGHT ? 1 : -1;
		if (!dev.pwiepixel.cubiqclient.qol.QolHooks.copyAllowed(b, cubiqclient$control())) return;
		if (cubiqclient$copy(event.x(), event.y())) cir.setReturnValue(true);
	}

	@Inject(method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$chatLink(net.minecraft.network.chat.Style style, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!cubiqclient$shift() && dev.pwiepixel.cubiqclient.qol.ChatLinks.onClick(style)) cir.setReturnValue(true);
	}
	*///?} elif >=1.21.9 {
	/*@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$copyLine(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		int b = event.button() == dev.pwiepixel.cubiqclient.compat.Keys.MOUSE_LEFT ? 0
				: event.button() == dev.pwiepixel.cubiqclient.compat.Keys.MOUSE_RIGHT ? 1 : -1;
		if (dev.pwiepixel.cubiqclient.qol.QolHooks.copyAllowed(b, cubiqclient$control())
				&& cubiqclient$copy(event.x(), event.y())) {
			cir.setReturnValue(true);
			return;
		}
		if (b == 0 && !cubiqclient$shift() && dev.pwiepixel.cubiqclient.qol.ChatLinks.onClick(
				dev.pwiepixel.cubiqclient.compat.ChatLines.chat().getClickedComponentStyleAt(event.x(), event.y()))) {
			cir.setReturnValue(true);
		}
	}
	*///?} elif >=1.16 {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$copyLine(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (dev.pwiepixel.cubiqclient.qol.QolHooks.copyAllowed(button, cubiqclient$control())
				&& cubiqclient$copy(mouseX, mouseY)) {
			cir.setReturnValue(true);
			return;
		}
		if (button == 0 && !cubiqclient$shift() && dev.pwiepixel.cubiqclient.qol.ChatLinks.onClick(
				dev.pwiepixel.cubiqclient.compat.ChatLines.chat().getClickedComponentStyleAt(mouseX, mouseY))) {
			cir.setReturnValue(true);
		}
	}
	//?} else {
	/*@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$copyLine(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!dev.pwiepixel.cubiqclient.qol.QolHooks.copyAllowed(button, cubiqclient$control())) return;
		if (cubiqclient$copy(mouseX, mouseY)) cir.setReturnValue(true);
	}
	*///?}

	/** Strg+Klick: Zeile kopieren (true = verbraucht). */
	private static boolean cubiqclient$copy(double x, double y) {
		CubiqClient client = CubiqClient.get();
		return client != null && client.chat().onChatClick(x, y, true);
	}

	/** Strg (bzw. Cmd auf macOS) gedrückt? */
	private static boolean cubiqclient$control() {
		//? if >=1.21.9 {
		/*return net.minecraft.client.Minecraft.getInstance().hasControlDown();
		*///?} else
		return net.minecraft.client.gui.screens.Screen.hasControlDown();
	}

	/** Umschalt gedrückt (dann fügt Vanilla den Einfüge-Text ein)? */
	private static boolean cubiqclient$shift() {
		//? if >=1.21.9 {
		/*return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
		*///?} else
		return net.minecraft.client.gui.screens.Screen.hasShiftDown();
	}
}
