package dev.pwiepixel.cubiqclient.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.6 {
import dev.pwiepixel.cubiqclient.CubiqClient;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * Eigenes Fadenkreuz: blendet bis 1.21.5 das Vanilla-Fadenkreuz aus (das eigene zeichnet der HUD-Manager).
 * Ab 1.21.6 ersetzt Cubiq die Fabric-HUD-Ebene "crosshair" direkt – dann ist dieser Mixin leer.
 */
@Mixin(Gui.class)
public abstract class CrosshairMixin {
	//? if <1.21.6 {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$hideVanillaCrosshair(CallbackInfo ci) {
		if (CubiqClient.get().hud().crosshair().replacesVanilla()) ci.cancel();
	}
	//?}
}
