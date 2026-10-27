package dev.pwiepixel.cubiqclient.core.ui.account;

/**
 * Was der Kontobildschirm ({@link AccountsUi}) von Minecraft braucht. Die Konten selbst verwaltet
 * {@link dev.pwiepixel.cubiqclient.core.account.AccountManager}.
 */
public interface AccountsHost {
	void playClick();

	/** Zurück zum vorherigen Bildschirm. */
	void closeScreen();

	/** Für Gesichter aus dem Netz. */
	String userAgent();
}
