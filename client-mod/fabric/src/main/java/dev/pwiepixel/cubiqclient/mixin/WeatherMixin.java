package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.core.perf.PerfFeature;
import dev.pwiepixel.cubiqclient.perf.PerfHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kein Regen und Schnee: die Niederschlags-Säulen werden nicht gezeichnet (das Wetter selbst,
 * Geräusche und Himmelsfarbe bleiben; Regen-Spritzer sind Partikel, siehe Modul Partikel).
 * 1.14 {@code GameRenderer#renderSnowAndRain}, 1.15–1.21.1 {@code LevelRenderer#renderSnowAndRain},
 * 1.21.2–26.2 der Wetter-Pass (fünf Signaturen),
 * ab 26.3 die beiden Zeichen-Methoden des WeatherEffectRenderer.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.WeatherEffectRenderer.class)
*///?} elif >=1.15 {
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.GameRenderer.class)
*///?}
public abstract class WeatherMixin {
	//? if >=26.3 {
	/*@Inject(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}

	@Inject(method = "renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeatherOit(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.21.11 {
	/*@Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.21.9 {
	/*@Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.21.6 {
	/*@Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/world/phys/Vec3;FLcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.21.4 {
	/*@Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/world/phys/Vec3;FLnet/minecraft/client/renderer/FogParameters;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.21.2 {
	/*@Inject(method = "addWeatherPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/world/phys/Vec3;FLnet/minecraft/client/renderer/FogParameters;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?} elif >=1.15 {
	@Inject(method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	//?} else {
	/*@Inject(method = "renderSnowAndRain(F)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$noWeather(CallbackInfo ci) {
		cubiqclient$skip(ci);
	}
	*///?}

	private static void cubiqclient$skip(CallbackInfo ci) {
		if (PerfHooks.hide(PerfFeature.WEATHER)) {
			PerfHooks.weatherSkipped++;
			ci.cancel();
		}
	}
}
