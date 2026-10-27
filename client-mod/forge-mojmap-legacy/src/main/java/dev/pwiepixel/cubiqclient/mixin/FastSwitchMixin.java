package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.core.connect.FastSwitch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schneller Serverwechsel: Beginn eines Wechsels erkennen – ab 1.20.2 die Konfigurationsphase (so wechseln Proxys),
 * sonst jede neue Welt vom Server (Beitritt, Wechsel per JoinGame). Das Ende erkennt der Tick (Ladebildschirm zu).
 */
@Mixin(ClientPacketListener.class)
public abstract class FastSwitchMixin {
	@Inject(method = "handleLogin", at = @At("HEAD"), require = 0)
	private void cubiqclient$switchLogin(net.minecraft.network.protocol.game.ClientboundLoginPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) FastSwitch.beginIfIdle();
	}

	//? if >=1.20.2 {
	/*@Inject(method = "handleConfigurationStart", at = @At("HEAD"), require = 0)
	private void cubiqclient$switchConfig(net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) FastSwitch.begin();
	}
	*///?}
}
