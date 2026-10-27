package dev.pwiepixel.cubiqclient.core.ui.social;

import dev.pwiepixel.cubiqclient.core.account.FaceCache;
import dev.pwiepixel.cubiqclient.core.online.FriendsView;
import dev.pwiepixel.cubiqclient.core.online.CubiqOnline;
import dev.pwiepixel.cubiqclient.core.social.Chat;
import dev.pwiepixel.cubiqclient.core.social.Social;

/** Was die Teilflächen des Sozial-Bildschirms (Liste, Unterhaltung) vom Bildschirm brauchen. */
interface SocialContext {
	Social social();

	CubiqOnline online();

	SocialHost host();

	FaceCache faces();

	Kit kit();

	/** Dialog öffnen. */
	void dialog(Dialog d);

	/** Kontextmenü öffnen. */
	void popup(PopupMenu m);

	/** Unterhaltung öffnen (null = keine). */
	void open(String conversationId);

	/** Freunde (null = noch nicht geladen). */
	FriendsView friends();

	/** Menü einer Unterhaltung (Liste: Rechtsklick; Kopfzeile: ≡). */
	void conversationMenu(Chat.Conversation c, int x, int y);

	/** „Meine Strafen“ öffnen (Moderation v2). */
	void showSanctions();

	/** Reiter „Welten“ zeigen (Stand eines Welt-Beitritts). */
	void showWorlds();

	/** Bildschirmgröße (für Menüs/Dialoge). */
	int screenWidth();

	int screenHeight();
}
