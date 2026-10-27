package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.CubiqClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Kein Schadens-Wackeln": überspringt das Kippen der Kamera beim Schaden (GameRenderer#bobHurt).
 * Rein optisch – Schaden, Blickrichtung und Treffer bleiben unverändert.
 */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$noHurtCam(CallbackInfo ci) {
		CubiqClient client = CubiqClient.get();
		if (client != null && client.modules().noHurtCam.isEnabled()) ci.cancel();
	}
}
