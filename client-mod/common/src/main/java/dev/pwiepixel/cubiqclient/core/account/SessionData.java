package dev.pwiepixel.cubiqclient.core.account;

import dev.pwiepixel.cubiqclient.core.online.Uuids;

/**
 * Die Sitzung des laufenden Spiels ({@link CurrentSession}) – das Zugangs-Token lebt nur im Speicher und wird nie
 * ausgegeben.
 */
public final class SessionData {
	/** 32 Hex-Zeichen ohne Bindestriche. */
	public final String uuid;
	public final String name;
	public final String accessToken;

	public SessionData(String uuid, String name, String accessToken) {
		this.uuid = Uuids.normalize(uuid);
		this.name = name;
		this.accessToken = accessToken;
	}

	public boolean valid() {
		return uuid != null && uuid.matches("[0-9a-f]{32}") && name != null && name.matches("[A-Za-z0-9_]{1,16}")
				&& accessToken != null && !accessToken.isEmpty() && accessToken.length() < 16384;
	}

	@Override
	public String toString() {
		return "SessionData{" + name + "}";
	}
}
