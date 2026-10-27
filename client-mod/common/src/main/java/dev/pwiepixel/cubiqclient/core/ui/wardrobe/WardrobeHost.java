package dev.pwiepixel.cubiqclient.core.ui.wardrobe;

import dev.pwiepixel.cubiqclient.core.online.OnlineFeatures;
import dev.pwiepixel.cubiqclient.core.skin.PlayerLook;

import java.nio.file.Path;

/**
 * Was die Garderobe ({@link WardrobeUi}) von Minecraft braucht – je Version eine kleine Umsetzung in
 * {@code screen/WardrobeScreen}. Alles Weitere (Konto, Cubiq API, Mojang) holt sich die Garderobe selbst.
 */
public interface WardrobeHost {
	void playClick();

	/** Zurück zum vorherigen Bildschirm. */
	void closeScreen();

	/** {@code config}-Ordner der Instanz. */
	Path configDir();

	String userAgent();

	/** Cubiq-Online-Funktionen der Version (Token, eigener Skin, Cubiq-Umhang) oder null (1.7.10/1.13.2). */
	OnlineFeatures<?> features();

	/** Eigenes Aussehen (Render-Thread) oder null. */
	default PlayerLook look() {
		OnlineFeatures<?> f = features();
		return f == null ? null : f.look();
	}
}
