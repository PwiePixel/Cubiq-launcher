package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.account.AccountManager;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.online.OnlineFeatures;
import dev.pwiepixel.cubiqclient.core.ui.wardrobe.WardrobeHost;
import dev.pwiepixel.cubiqclient.core.ui.wardrobe.WardrobeUi;
import dev.pwiepixel.cubiqclient.online.OnlineHooks;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;

/**
 * Garderobe (Skins, Outfits, Umhänge, Emotes, Skin-Editor) – die Oberfläche steht komplett in
 * {@code core.ui.wardrobe}; hier nur die Anbindung an diese Version. Gleiche Datei in allen Mojmap-Bäumen.
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
	public static Screen create(final Screen parent) {
		WardrobeHost host = new WardrobeHost() {
			@Override
			public void playClick() {
				new CubiqMenuHost(parent).playClick();
			}

			@Override
			public void closeScreen() {
				Mc.setScreen(parent);
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
				return OnlineHooks.features();
			}
		};
		WardrobeUi ui = new WardrobeUi(host);
		last = ui;
		return new CubiqUiScreen(I18n.tr("wardrobe.title"), ui);
	}

	/** Öffnet die Garderobe über dem aktuellen Bildschirm. */
	public static void open() {
		Mc.setScreen(create(Mc.screen()));
	}
}
