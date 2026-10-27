package dev.pwiepixel.cubiqclient.mixin;

//? if >=1.15 {
import com.mojang.blaze3d.systems.RenderSystem;
import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Stärke der Umrandungslinien (Block-Umrandung und Hitboxen). Minecraft setzt alle Linienstärken
 * über {@code RenderSystem#lineWidth}; die Vanilla-Grundstärke 2,5 wird hier auf den eingestellten
 * Wert skaliert. Die Methode gibt es erst ab 1.15 – in 1.14.4 bleibt die Stärke unverändert.
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
		return width * (float) modules.blockOutlineWidth.get() / BASE;
	}
}
//?} else {
/*/^* RenderSystem#lineWidth gibt es erst ab 1.15 (und Forge dort kein Mixin) – leer. ^/
public final class LineWidthMixin {
	private LineWidthMixin() {
	}
}
*///?}
