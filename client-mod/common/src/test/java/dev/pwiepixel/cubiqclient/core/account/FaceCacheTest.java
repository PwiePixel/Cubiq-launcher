package dev.pwiepixel.cubiqclient.core.account;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FaceCacheTest {
	@Test
	void skinAdressenNurVonMojang() {
		assertEquals("https://textures.minecraft.net/texture/abc", FaceCache.safeSkinUrl("http://textures.minecraft.net/texture/abc"));
		assertNull(FaceCache.safeSkinUrl("https://evil.example/texture/abc"));
		assertNull(FaceCache.safeSkinUrl("https://textures.minecraft.net/texture/a?x=<script>"));
		assertNull(FaceCache.safeSkinUrl("javascript:alert(1)"));
	}

	@Test
	void gesichtOhneDeckendeHutEbene() {
		int[] px = new int[64 * 32];
		java.util.Arrays.fill(px, 0xFF000000);
		for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) px[y * 64 + x] = 0xFFC08060;
		int[] face = FaceCache.extract(new dev.pwiepixel.cubiqclient.core.cape.PngDecoder.Image(64, 32, px));
		assertEquals(0xFFC08060, face[0]);
		assertEquals(0, face[64], "schwarze, deckende Hut-Ebene eines alten Skins zählt als leer");
		assertNull(FaceCache.extract(new dev.pwiepixel.cubiqclient.core.cape.PngDecoder.Image(32, 32, new int[32 * 32])));
	}
}
