package dev.pwiepixel.cubiqclient.mixin;

// Begleiter streicheln/füttern per Rechtsklick (ab 1.15 wie die Begleiter selbst).
//? if >=1.15 {
import dev.pwiepixel.cubiqclient.online.PetHooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rechtsklick auf den eigenen Begleiter am Boden ({@link PetHooks#onUse}): am Anfang von {@code Minecraft#startUseItem}
 * – davor hat Minecraft nur den Treffer gerechnet ({@code hitResult}), danach kämen Benutzen, Interagieren und die
 * Pakete an den Server. Gilt der Klick dem Begleiter, wird der ganze Aufruf abgebrochen: nichts wird gegessen oder
 * gesetzt, nichts geht an den Server. Ohne {@code @Shadow} (nichts, was in einer Version fehlen könnte); mit
 * {@code require = 0} bleibt ohne das Ziel einfach Vanilla.
 */
@Mixin(Minecraft.class)
public abstract class PetUseMixin {
	@Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$petUse(CallbackInfo ci) {
		if (PetHooks.onUse((Minecraft) (Object) this)) ci.cancel();
	}
}
//?}
