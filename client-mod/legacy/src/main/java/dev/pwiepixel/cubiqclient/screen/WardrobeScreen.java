package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.account.AccountManager;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.online.OnlineFeatures;
import dev.pwiepixel.cubiqclient.core.ui.wardrobe.WardrobeHost;
import dev.pwiepixel.cubiqclient.core.ui.wardrobe.WardrobeUi;
import net.minecraft.client.gui.GuiScreen;

import java.nio.file.Path;

/**
 * Garderobe (Skins, Outfits, Umhänge, Emotes, Skin-Editor) – die Oberfläche steht komplett in
 * {@code core.ui.wardrobe}; hier nur die Anbindung an diese Version.
 */
public final class WardrobeScreen {
	/** Zuletzt geöffnete Garderobe (für den Selbsttest). */
	public static WardrobeUi last;

	private WardrobeScreen() {
	}

	/** Gibt es die Garderobe (Konfig-Ordner bekannt)? */
	public static boolean available() {
		return I18n.configDir() != null;
	}

	/** Garderobe; {@code parent} = wohin „Schließen“ führt (null = zurück ins Spiel). */
	public static GuiScreen create(final GuiScreen parent) {
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		WardrobeHost host = new WardrobeHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public Path configDir() {
				return I18n.configDir();
			}

			@Override
			public String userAgent() {
				AccountManager m = AccountManager.get();
				return m == null ? "Cubiq-Client" : m.userAgent();
			}

			@Override
			public OnlineFeatures<?> features() {
				return dev.pwiepixel.cubiqclient.online.LegacyOnline.features();
			}
		};
		WardrobeUi ui = new WardrobeUi(host);
		last = ui;
		return new CubiqUiScreen(I18n.tr("wardrobe.title"), ui);
	}
}
