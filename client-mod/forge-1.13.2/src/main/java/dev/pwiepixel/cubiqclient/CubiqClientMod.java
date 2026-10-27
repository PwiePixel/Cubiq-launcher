package dev.pwiepixel.cubiqclient;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-Einstieg (mods.toml: modId "cubiqclient"). Der Cubiq Client ist ein reiner Client-Mod:
 * auf einem dedizierten Server passiert nichts (und keine Client-Klasse wird geladen).
 */
@Mod(CubiqClient.MOD_ID)
public final class CubiqClientMod {
	public CubiqClientMod() {
		DistExecutor.runWhenOn(Dist.CLIENT, () -> CubiqClient::bootstrap);
	}
}
