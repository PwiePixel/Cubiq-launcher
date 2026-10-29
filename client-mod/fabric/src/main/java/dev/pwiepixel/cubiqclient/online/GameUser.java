package dev.pwiepixel.cubiqclient.online;

import dev.pwiepixel.cubiqclient.core.account.SessionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Die Sitzung, mit der Minecraft gestartet wurde (Name, UUID, Token) – Quelle für
 * {@link dev.pwiepixel.cubiqclient.core.account.CurrentSession}. Nur lesen: Der Cubiq Client tauscht sie nie aus.
 */
public final class GameUser {
	private GameUser() {
	}

	/** Aus jedem Thread; null = unbekannt. */
	public static SessionData current() {
		Minecraft mc = Minecraft.getInstance();
		User user = mc == null ? null : mc.getUser();
		if (user == null) return null;
		//? if >=1.20.2 {
		String uuid = user.getProfileId() == null ? null : user.getProfileId().toString();
		//?} else {
		/*String uuid = user.getUuid();
		*///?}
		return new SessionData(uuid, user.getName(), user.getAccessToken(), null);
	}
}
