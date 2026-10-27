package dev.pwiepixel.cubiqclient.mixin;

//? if >=1.15 {
import com.mojang.blaze3d.vertex.PoseStack;
import dev.pwiepixel.cubiqclient.CubiqClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Niedriges Feuer: verschiebt die Flammen am Bildschirmrand nach unten, damit man beim Brennen
 * noch etwas sieht. {@code ScreenEffectRenderer#renderFire(Minecraft, PoseStack)} ist von
 * 1.15.2 bis 1.19.4 unverändert – der PoseStack trägt die Flammen.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class LowFireMixin {
	@Inject(method = "renderFire", at = @At("HEAD"), require = 1)
	private static void cubiqclient$lowerFire(Minecraft minecraft, PoseStack pose, CallbackInfo ci) {
		pose.pushPose();
		pose.translate(0, -cubiqclient$offset(), 0);
	}

	@Inject(method = "renderFire", at = @At("RETURN"), require = 1)
	private static void cubiqclient$restoreFire(Minecraft minecraft, PoseStack pose, CallbackInfo ci) {
		pose.popPose();
	}

	/** Absenkung in Blöcken (0 = Modul aus). */
	private static float cubiqclient$offset() {
		CubiqClient client = CubiqClient.get();
		if (client == null || !client.modules().lowFire.isEnabled()) return 0;
		return (float) client.modules().lowFireHeight.get();
	}
}
//?} else {
/*/^* Minecraft 1.14.4 hat noch keinen ScreenEffectRenderer (und Forge dort kein Mixin) – leer. ^/
public final class LowFireMixin {
	private LowFireMixin() {
	}
}
*///?}
