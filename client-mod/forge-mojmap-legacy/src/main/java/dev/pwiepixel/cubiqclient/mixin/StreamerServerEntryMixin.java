package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.core.streamer.StreamerMode;
import dev.pwiepixel.cubiqclient.qol.QolHooks;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Streamer-Modus: Servernamen, die wie eine Adresse aussehen („play.example.net“), werden in der Serverliste nur für
 * das Zeichnen verborgen – der gespeicherte Name bleibt unverändert (vor dem Zeichnen getauscht, danach zurück).
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class StreamerServerEntryMixin {
	@Shadow
	@Final
	private ServerData serverData;

	@Unique
	private String cubiqclient$name;

	@Unique
	private void cubiqclient$hide() {
		cubiqclient$show();
		if (serverData != null && QolHooks.hideServerName(serverData.name)) {
			cubiqclient$name = serverData.name;
			serverData.name = StreamerMode.HIDDEN_ADDRESS;
		}
	}

	@Unique
	private void cubiqclient$show() {
		if (cubiqclient$name != null && serverData != null) serverData.name = cubiqclient$name;
		cubiqclient$name = null;
	}

	//? if >=26.1 {
	/*@Inject(method = "extractContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V", at = @At("HEAD"), require = 0)
	private void cubiqclient$before(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered,
			float partialTick, CallbackInfo ci) {
		cubiqclient$hide();
	}

	@Inject(method = "extractContent(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIZF)V", at = @At("RETURN"), require = 0)
	private void cubiqclient$after(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered,
			float partialTick, CallbackInfo ci) {
		cubiqclient$show();
	}
	*///?} elif >=1.21.9 {
	/*@Inject(method = "renderContent(Lnet/minecraft/client/gui/GuiGraphics;IIZF)V", at = @At("HEAD"), require = 0)
	private void cubiqclient$before(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, boolean hovered, float partialTick,
			CallbackInfo ci) {
		cubiqclient$hide();
	}

	@Inject(method = "renderContent(Lnet/minecraft/client/gui/GuiGraphics;IIZF)V", at = @At("RETURN"), require = 0)
	private void cubiqclient$after(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, boolean hovered, float partialTick,
			CallbackInfo ci) {
		cubiqclient$show();
	}
	*///?} elif >=1.20 {
	/*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", at = @At("HEAD"), require = 0)
	private void cubiqclient$before(net.minecraft.client.gui.GuiGraphics g, int index, int top, int left, int width, int height,
			int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		cubiqclient$hide();
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V", at = @At("RETURN"), require = 0)
	private void cubiqclient$after(net.minecraft.client.gui.GuiGraphics g, int index, int top, int left, int width, int height,
			int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		cubiqclient$show();
	}
	*///?} elif >=1.16 {
	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIIIIZF)V", at = @At("HEAD"), require = 0)
	private void cubiqclient$before(com.mojang.blaze3d.vertex.PoseStack pose, int index, int top, int left, int width, int height,
			int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		cubiqclient$hide();
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIIIIZF)V", at = @At("RETURN"), require = 0)
	private void cubiqclient$after(com.mojang.blaze3d.vertex.PoseStack pose, int index, int top, int left, int width, int height,
			int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
		cubiqclient$show();
	}
	//?} else {
	/*@Inject(method = "render(IIIIIIIZF)V", at = @At("HEAD"), require = 0)
	private void cubiqclient$before(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered,
			float partialTick, CallbackInfo ci) {
		cubiqclient$hide();
	}

	@Inject(method = "render(IIIIIIIZF)V", at = @At("RETURN"), require = 0)
	private void cubiqclient$after(int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered,
			float partialTick, CallbackInfo ci) {
		cubiqclient$show();
	}
	*///?}
}
