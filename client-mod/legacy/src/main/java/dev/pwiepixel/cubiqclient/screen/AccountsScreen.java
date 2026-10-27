package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.account.AccountManager;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.ui.account.AccountsHost;
import dev.pwiepixel.cubiqclient.core.ui.account.AccountsUi;
import net.minecraft.client.gui.GuiScreen;

/**
 * Der Kontobildschirm (Kontowechsel ohne Neustart). Inhalt und Bedienung stehen versionsunabhängig in
 * {@link AccountsUi}; hier hängt er nur an einem Minecraft-Bildschirm.
 */
public final class AccountsScreen {
	private AccountsScreen() {
	}

	/** Gibt es die Kontoverwaltung (beim Start angelegt)? */
	public static boolean available() {
		return AccountManager.get() != null;
	}

	/** Kontobildschirm; {@code parent} = wohin „Schließen“ führt. */
	public static GuiScreen create(final GuiScreen parent) {
		final AccountManager manager = AccountManager.get();
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		AccountsHost host = new AccountsHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public String userAgent() {
				return manager == null ? "Cubiq-Client" : manager.userAgent();
			}
		};
		AccountsUi ui = new AccountsUi(host, manager);
		return new CubiqUiScreen(I18n.tr("accounts.title"), ui);
	}
}
