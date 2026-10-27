package dev.pwiepixel.cubiqclient.mixin;

import dev.pwiepixel.cubiqclient.online.OnlineHooks;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cubiq-Umhang statt Mojang-/OptiFine-Umhang – nur wenn der Lookup für diesen Spieler einen Cubiq-Umhang
 * liefert (und seine Textur geladen ist). Die Elytra bleibt dabei immer die Vanilla-Elytra: Vanilla nähme sonst den
 * Umhang als Elytra-Textur (Rückfall, wenn keine eigene Elytra-Textur da ist) – deshalb bekommt der Spieler dann
 * ausdrücklich die Vanilla-Elytra-Textur ({@link OnlineHooks#VANILLA_ELYTRA}). Spieler ohne Cubiq-Umhang bleiben unberührt.
 * Bis 1.20.1 über die Textur-Getter, ab 1.20.2 über den PlayerSkin (ab 1.21.9 mit ClientAsset-Texturen).
 * require = 0: fehlt eine Stelle, bleibt einfach der Vanilla-Umhang.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class CapeTextureMixin {
	/**
	 * getSkin() läuft je Bild mehrmals pro Spieler: der ersetzte Skin wird gemerkt und nur neu gebaut, wenn sich
	 * Vanillas Skin oder das Cubiq-Umhangbild (Animation) geändert hat.
	 */
	@org.spongepowered.asm.mixin.Unique
	private Object cubiqclient$skinIn;
	@org.spongepowered.asm.mixin.Unique
	private Object cubiqclient$texIn;
	@org.spongepowered.asm.mixin.Unique
	private Object cubiqclient$skinOut;

	//? if >=1.21.11 {
	/*@Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true, require = 0)
	private void cubiqclient$cubiqCape(CallbackInfoReturnable<net.minecraft.world.entity.player.PlayerSkin> cir) {
		Object tex = OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this);
		net.minecraft.world.entity.player.PlayerSkin skin = cir.getReturnValue();
		if (tex == null || skin == null) return;
		if (skin != cubiqclient$skinIn || tex != cubiqclient$texIn) {
			net.minecraft.core.ClientAsset.ResourceTexture asset = new net.minecraft.core.ClientAsset.ResourceTexture(
					(net.minecraft.resources.Identifier) tex, (net.minecraft.resources.Identifier) tex);
			net.minecraft.core.ClientAsset.Texture elytra = skin.elytra() != null ? skin.elytra()
					: new net.minecraft.core.ClientAsset.ResourceTexture(OnlineHooks.VANILLA_ELYTRA, OnlineHooks.VANILLA_ELYTRA);
			cubiqclient$skinOut = new net.minecraft.world.entity.player.PlayerSkin(skin.body(), asset, elytra, skin.model(), skin.secure());
			cubiqclient$skinIn = skin;
			cubiqclient$texIn = tex;
		}
		cir.setReturnValue((net.minecraft.world.entity.player.PlayerSkin) cubiqclient$skinOut);
	}
	*///?} elif >=1.21.9 {
	/*@Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true, require = 0)
	private void cubiqclient$cubiqCape(CallbackInfoReturnable<net.minecraft.world.entity.player.PlayerSkin> cir) {
		Object tex = OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this);
		net.minecraft.world.entity.player.PlayerSkin skin = cir.getReturnValue();
		if (tex == null || skin == null) return;
		if (skin != cubiqclient$skinIn || tex != cubiqclient$texIn) {
			net.minecraft.core.ClientAsset.ResourceTexture asset = new net.minecraft.core.ClientAsset.ResourceTexture(
					(net.minecraft.resources.ResourceLocation) tex, (net.minecraft.resources.ResourceLocation) tex);
			net.minecraft.core.ClientAsset.Texture elytra = skin.elytra() != null ? skin.elytra()
					: new net.minecraft.core.ClientAsset.ResourceTexture(OnlineHooks.VANILLA_ELYTRA, OnlineHooks.VANILLA_ELYTRA);
			cubiqclient$skinOut = new net.minecraft.world.entity.player.PlayerSkin(skin.body(), asset, elytra, skin.model(), skin.secure());
			cubiqclient$skinIn = skin;
			cubiqclient$texIn = tex;
		}
		cir.setReturnValue((net.minecraft.world.entity.player.PlayerSkin) cubiqclient$skinOut);
	}
	*///?} elif >=1.20.2 {
	/*@Inject(method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;", at = @At("RETURN"), cancellable = true, require = 0)
	private void cubiqclient$cubiqCape(CallbackInfoReturnable<net.minecraft.client.resources.PlayerSkin> cir) {
		Object tex = OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this);
		net.minecraft.client.resources.PlayerSkin skin = cir.getReturnValue();
		if (tex == null || skin == null) return;
		if (skin != cubiqclient$skinIn || tex != cubiqclient$texIn) {
			net.minecraft.resources.ResourceLocation id = (net.minecraft.resources.ResourceLocation) tex;
			net.minecraft.resources.ResourceLocation elytra = skin.elytraTexture() != null ? skin.elytraTexture()
					: OnlineHooks.VANILLA_ELYTRA;
			cubiqclient$skinOut = new net.minecraft.client.resources.PlayerSkin(skin.texture(), skin.textureUrl(), id, elytra,
					skin.model(), skin.secure());
			cubiqclient$skinIn = skin;
			cubiqclient$texIn = tex;
		}
		cir.setReturnValue((net.minecraft.client.resources.PlayerSkin) cubiqclient$skinOut);
	}
	*///?} else {
	@Inject(method = "getCloakTextureLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"),
			cancellable = true, require = 0)
	private void cubiqclient$cubiqCape(CallbackInfoReturnable<net.minecraft.resources.ResourceLocation> cir) {
		Object tex = OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this);
		if (tex != null) cir.setReturnValue((net.minecraft.resources.ResourceLocation) tex);
	}

	@Inject(method = "getElytraTextureLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"),
			cancellable = true, require = 0)
	private void cubiqclient$vanillaElytra(CallbackInfoReturnable<net.minecraft.resources.ResourceLocation> cir) {
		// Ohne eigene Elytra-Textur nähme Vanilla den (Cubiq-)Umhang – stattdessen die Vanilla-Elytra.
		if (cir.getReturnValue() == null && OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this) != null) {
			cir.setReturnValue(OnlineHooks.VANILLA_ELYTRA);
		}
	}

	@Inject(method = "isCapeLoaded()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$cubiqCapeLoaded(CallbackInfoReturnable<Boolean> cir) {
		if (OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this) != null) cir.setReturnValue(true);
	}

	@Inject(method = "isElytraLoaded()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void cubiqclient$vanillaElytraLoaded(CallbackInfoReturnable<Boolean> cir) {
		if (OnlineHooks.capeTexture((AbstractClientPlayer) (Object) this) != null) cir.setReturnValue(true);
	}
	//?}
}
