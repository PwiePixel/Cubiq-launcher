package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.menus.VanillaMenus;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RRLS (Remove Reloading Screen) zeichnet beim versteckten Neuladen eine eigene kleine Ladeleiste
 * ({@code rrls$miniRender}, von RRLS in LoadingOverlay eingefügt). Zeichnet in diesem Bild schon die Cubiq-Ladefläche
 * (siehe LoadingOverlayMixin), bleibt die RRLS-Leiste weg – sie läge sonst über der Cubiq-Fläche.
 * Ohne RRLS gibt es die Methode nicht: {@code require = 0}, Priorität über RRLS (dessen Mixin muss zuerst greifen).
 */
@Mixin(value = LoadingOverlay.class, priority = 2000)
public abstract class RrlsOverlayMixin {
	@Inject(method = "rrls$miniRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void cubiqclient$replaceMiniRender(CallbackInfo ci) {
		if (VanillaMenus.takeHiddenOverlayDrawn()) ci.cancel();
	}
}
