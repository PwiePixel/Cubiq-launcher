package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.core.camera.FreelookState;
import dev.pwiepixel.cubiqclient.dev.HookStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9
//import net.minecraft.client.input.MouseButtonInfo;

/** CPS-Zählung, Zoom per Mausrad, langsamere Maus beim Zoomen und Freelook-Drehung. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;

	// Maustaste: onPress(window, button, action, mods) bis 1.21.8, onButton(window, info, action) ab 1.21.9.
	//? if >=1.21.9 {
	/*@Inject(method = "onButton", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		HookStats.press++;
		if (action == InputConstants.PRESS && window == cubiqclient$window()) {
			if (dev.pwiepixel.cubiqclient.screenshot.ScreenshotHooks.onMousePress(Keys.uiButton(info.button()))) {
				ci.cancel();
				return;
			}
			CubiqClient.get().onMouseClick(info.button());
		}
	}
	*///?} else {
	@Inject(method = "onPress", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$countClick(long window, int button, int action, int mods, CallbackInfo ci) {
		HookStats.press++;
		if (action == InputConstants.PRESS && window == cubiqclient$window()) {
			if (dev.pwiepixel.cubiqclient.screenshot.ScreenshotHooks.onMousePress(Keys.uiButton(button))) {
				ci.cancel();
				return;
			}
			CubiqClient.get().onMouseClick(button);
		}
	}
	//?}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 1)
	private void cubiqclient$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		HookStats.scroll++;
		if (window == cubiqclient$window() && yOffset != 0 && CubiqClient.get().onScroll(yOffset)) {
			ci.cancel();
		}
	}

	// turnPlayer() bis 1.20.4, turnPlayer(double movementTime) ab 1.20.5.
	@Inject(method = "turnPlayer", at = @At("HEAD"), require = 1)
	//? if >=1.20.5 {
	private void cubiqclient$slowMouseWhileZooming(double movementTime, CallbackInfo ci) {
	//?} else
	/*private void cubiqclient$slowMouseWhileZooming(CallbackInfo ci) {*/
		HookStats.turn++;
		double divisor = CubiqClient.get().mouseDivisor();
		if (divisor > 1.0) {
			accumulatedDX /= divisor;
			accumulatedDY /= divisor;
		}
	}

	/** Freelook: Mausbewegung dreht die Kamera statt der Spielfigur (ohne MixinExtras → @Redirect). */
	@Redirect(method = "turnPlayer",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), require = 1)
	private void cubiqclient$freelookTurn(LocalPlayer player, double yaw, double pitch) {
		// Kamera-Fahrt: die Figur bleibt stehen, die Maus wirkt nicht; freie Kamera: die Maus dreht die Kamera.
		if (dev.pwiepixel.cubiqclient.cinematic.CinematicHooks.turn(yaw, pitch)) return;
		FreelookState freelook = CubiqClient.get().pvp().freelook();
		if (freelook.active()) freelook.turn(yaw, pitch);
		else player.turn(yaw, pitch);
	}

	private long cubiqclient$window() {
		//? if >=1.21.9 {
		/*return minecraft.getWindow().handle();
		*///?} else
		return minecraft.getWindow().getWindow();
	}
}
