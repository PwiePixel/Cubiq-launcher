package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.4 {
/*import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?} else {
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
//?}

/**
 * Farbe der Block-Umrandung. Bis 1.21.3 bekommt die Zeichenmethode vier Farbanteile,
 * ab 1.21.4 eine einzelne ARGB-Zahl (ab 26.2 heißt die Methode submitHitOutline).
 * Die Stärke der Linien wird nicht hier, sondern in {@link LineWidthMixin} gesetzt.
 * (NeoForges RenderHighlightEvent kann die Umrandung nur abschalten – die Farbe nicht ändern.)
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
	//? if >=26.2 {
	/*@ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
	private int cubiqclient$outlineColor(int color) {
		return cubiqclient$color(color);
	}
	*///?} elif >=1.21.4 {
	/*@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
	private int cubiqclient$outlineColor(int color) {
		return cubiqclient$color(color);
	}
	*///?} else {
	@ModifyArgs(method = "renderHitOutline",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape("
					+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
					+ "Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"), require = 1)
	private void cubiqclient$outlineColor(Args args) {
		CubiqModules modules = cubiqclient$modules();
		if (modules == null || !modules.blockOutline.isEnabled()) return;
		int rgb = modules.blockOutlineColor.rgb();
		args.set(6, ((rgb >> 16) & 0xFF) / 255f);
		args.set(7, ((rgb >> 8) & 0xFF) / 255f);
		args.set(8, (rgb & 0xFF) / 255f);
		args.set(9, modules.blockOutlineOpacity.getInt() / 100f);
	}
	//?}

	//? if >=1.21.4 {
	/*/^* Farbe als ARGB ersetzen, wenn das Modul an ist. *^/
	private static int cubiqclient$color(int original) {
		CubiqModules modules = cubiqclient$modules();
		if (modules == null || !modules.blockOutline.isEnabled()) return original;
		int alpha = Math.round(modules.blockOutlineOpacity.getInt() * 2.55f);
		return (alpha << 24) | modules.blockOutlineColor.rgb();
	}
	*///?}

	private static CubiqModules cubiqclient$modules() {
		CubiqClient client = CubiqClient.get();
		return client == null ? null : client.modules();
	}
}
