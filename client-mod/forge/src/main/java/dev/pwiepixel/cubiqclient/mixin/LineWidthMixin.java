package dev.pwiepixel.cubiqclient.mixin;

// RenderSystem#lineWidth gibt es nur bis 1.21.10; ab 1.21.11 zeichnet Minecraft Linien über die
// Render-Pipeline und die Stärke lässt sich nicht mehr setzen. Für spätere Versionen ist dieser
// Mixin nicht in cubiqclient.mixins.json eingetragen (siehe build.gradle).
//? if <1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Stärke der Umrandungslinien (Block-Umrandung und Hitboxen). Minecraft zeichnet alle
 * Linien über RenderSystem#lineWidth – dort wird die Vanilla-Stärke (2,5) mit dem
 * eingestellten Wert skaliert.
 */
@Mixin(RenderSystem.class)
public abstract class LineWidthMixin {
	/** Vanilla-Grundstärke der Linien. */
	private static final float BASE = 2.5F;

	@ModifyVariable(method = "lineWidth", at = @At("HEAD"), argsOnly = true, require = 1)
	private static float cubiqclient$lineWidth(float width) {
		CubiqClient client = CubiqClient.get();
		if (client == null) return width;
		CubiqModules modules = client.modules();
		if (!modules.blockOutline.isEnabled()) return width;
		float factor = (float) modules.blockOutlineWidth.get() / BASE;
		return width * factor;
	}
}
//?}
