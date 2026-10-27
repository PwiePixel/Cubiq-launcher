package dev.pwiepixel.cubiqclient.mixin;

// Diese Klasse gibt es erst ab 1.15 (in 1.14 fehlen ScreenEffectRenderer/RenderSystem);
// in 1.14 ist der Mixin auch nicht in cubiqclient.mixins.json eingetragen.
//? if >=1.15 {
import com.mojang.blaze3d.systems.RenderSystem;
import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Stärke der Umrandungslinien (Block-Umrandung und Hitboxen). Minecraft zeichnet alle
 * Linien über RenderSystem#lineWidth – dort wird die Vanilla-Stärke (2,5) mit dem
 * eingestellten Wert skaliert. In 1.14 und ab 26.1 gibt es diese Methode nicht mehr
 * (dort bleibt die Stärke unverändert; Mixin ist für diese Versionen nicht eingetragen).
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
