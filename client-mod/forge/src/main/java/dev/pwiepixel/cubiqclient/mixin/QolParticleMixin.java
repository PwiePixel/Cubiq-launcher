package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.qol.QolHooks;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treffer-Feedback (nur Anzeige): mehr Kritisch-/Schärfe-Partikel (dieselben Emitter wie Vanilla, mehrfach) und
 * Totem-Pops (Vanilla startet dafür einen Totem-Emitter am Spieler – Ereignis 35).
 */
@Mixin(ParticleEngine.class)
public abstract class QolParticleMixin {
	@Unique
	private boolean cubiqclient$again;

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
			at = @At("HEAD"), require = 0)
	private void cubiqclient$more(Entity entity, ParticleOptions options, CallbackInfo ci) {
		if (cubiqclient$again || (options != ParticleTypes.CRIT && options != ParticleTypes.ENCHANTED_HIT)) return;
		int extra = QolHooks.extraParticles();
		if (extra <= 0) return;
		cubiqclient$again = true;
		try {
			for (int i = 0; i < extra; i++) ((ParticleEngine) (Object) this).createTrackingEmitter(entity, options);
		} finally {
			cubiqclient$again = false;
		}
	}

	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
			at = @At("HEAD"), require = 0)
	private void cubiqclient$totem(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
		if (options == ParticleTypes.TOTEM_OF_UNDYING) QolHooks.onTotem(entity);
	}
}
