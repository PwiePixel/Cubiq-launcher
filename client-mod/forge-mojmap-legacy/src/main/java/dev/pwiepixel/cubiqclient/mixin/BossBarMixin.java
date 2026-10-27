package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.qol.QolHuds;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bossleiste verschieben und skalieren (Position/Größe aus dem HUD-Editor); gezeichnet wird weiter von Vanilla. */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarMixin {
	@Unique
	private boolean cubiqclient$moved;

	//? if >=26.1 {
	/*@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), require = 1)
	private void cubiqclient$start(net.minecraft.client.gui.GuiGraphicsExtractor g, CallbackInfo ci) {
		cubiqclient$moved = QolHuds.bossBarBegin(Gfx.of(g));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("RETURN"), require = 1)
	private void cubiqclient$end(net.minecraft.client.gui.GuiGraphicsExtractor g, CallbackInfo ci) {
		if (cubiqclient$moved) QolHuds.end(Gfx.of(g));
		cubiqclient$moved = false;
	}
	*///?} elif >=1.20 {
	/*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), require = 1)
	private void cubiqclient$start(net.minecraft.client.gui.GuiGraphics g, CallbackInfo ci) {
		cubiqclient$moved = QolHuds.bossBarBegin(Gfx.of(g));
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"), require = 1)
	private void cubiqclient$end(net.minecraft.client.gui.GuiGraphics g, CallbackInfo ci) {
		if (cubiqclient$moved) QolHuds.end(Gfx.of(g));
		cubiqclient$moved = false;
	}
	*///?} elif >=1.16 {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("HEAD"), require = 1)
	private void cubiqclient$start(com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
		cubiqclient$moved = QolHuds.bossBarBegin(Gfx.of(pose));
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("RETURN"), require = 1)
	private void cubiqclient$end(com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
		if (cubiqclient$moved) QolHuds.end(Gfx.of(pose));
		cubiqclient$moved = false;
	}
	//?} else {
	/*@Inject(method = "render()V", at = @At("HEAD"), require = 1)
	private void cubiqclient$start(CallbackInfo ci) {
		cubiqclient$moved = QolHuds.bossBarBegin(Gfx.of());
	}

	@Inject(method = "render()V", at = @At("RETURN"), require = 1)
	private void cubiqclient$end(CallbackInfo ci) {
		if (cubiqclient$moved) QolHuds.end(Gfx.of());
		cubiqclient$moved = false;
	}
	*///?}
}
