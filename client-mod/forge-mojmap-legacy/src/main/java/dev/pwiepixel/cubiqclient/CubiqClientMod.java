package dev.pwiepixel.cubiqclient;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge-Einstieg. Reine Client-Mod: auf einem dedizierten Server passiert nichts
 * (und es werden keine Client-Klassen geladen).
 */
@Mod(CubiqClient.MOD_ID)
public final class CubiqClientMod {
	public CubiqClientMod() {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			CubiqClient.init();
		}
	}
}
