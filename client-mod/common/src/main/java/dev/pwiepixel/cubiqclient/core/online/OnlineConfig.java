package dev.pwiepixel.cubiqclient.core.online;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Wohin und ob der Cubiq Client mit der Cubiq API spricht.
 *
 * <p>Der Launcher schreibt vor jedem Start {@code config/cubiqclient/cubiq-api.json}
 * ({@code {"version":1,"enabled":true|false}}). {@code enabled:false} heißt: Der Spieler hat die
 * Cubiq-Online-Funktionen im Launcher abgeschaltet – der Mod macht dann keinen einzigen Aufruf.
 * Fehlt die Datei (älterer Launcher, anderer Launcher), ist die API eingeschaltet; im Cubiq-Menü
 * lässt sie sich zusätzlich abschalten.
 *
 * <p>Für Tests lassen sich die Adressen per System-Property umbiegen
 * ({@code -Dcubiqclient.api.url=http://127.0.0.1:8787}, {@code -Dcubiqclient.session.url=…}).
 * Unverschlüsseltes HTTP ist dabei nur für localhost erlaubt.
 */
public final class OnlineConfig {
	/** Adresse der Cubiq API (dort liegt auch die Website). */
	public static final String DEFAULT_API = "https://cubiq-launcher.invalid";
	/**
	 * Bisherige Adresse – bleibt parallel erreichbar. Umhang-URLs mit diesem Host (ältere Antworten,
	 * {@code .url}-Dateien im Umhang-Cache) gelten weiter als Cubiq-API-Adressen.
	 */
	public static final String LEGACY_API = "https://api.cubiq-launcher.invalid";
	/** Alle Adressen, unter denen die echte Cubiq API läuft. */
	private static final String[] KNOWN_APIS = {DEFAULT_API, LEGACY_API};
	public static final String DEFAULT_SESSION = "https://sessionserver.mojang.com";
	private static final long MAX_BYTES = 4096;

	private final boolean launcherEnabled;
	private final String apiBase;
	private final String sessionBase;

	public OnlineConfig(boolean launcherEnabled, String apiBase, String sessionBase) {
		this.launcherEnabled = launcherEnabled;
		this.apiBase = apiBase;
		this.sessionBase = sessionBase;
	}

	/** DTO der Launcher-Datei. */
	static final class File {
		Integer version;
		Boolean enabled;
	}

	/** Liest {@code <configDir>/cubiqclient/cubiq-api.json} und die Test-Properties. */
	public static OnlineConfig load(Path configDir) {
		return new OnlineConfig(readEnabled(configDir),
				baseUrl(System.getProperty("cubiqclient.api.url"), DEFAULT_API),
				baseUrl(System.getProperty("cubiqclient.session.url"), DEFAULT_SESSION));
	}

	/** false nur, wenn die Datei ausdrücklich {@code "enabled": false} enthält. */
	static boolean readEnabled(Path configDir) {
		if (configDir == null) return true;
		Path file = configDir.resolve("cubiqclient").resolve("cubiq-api.json");
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return true;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				File parsed = new Gson().fromJson(reader, File.class);
				return parsed == null || parsed.enabled == null || parsed.enabled;
			}
		} catch (IOException | RuntimeException e) {
			// Kaputte Datei: lieber an (wie ohne Datei) – abschalten geht weiter über das Cubiq-Menü.
			return true;
		}
	}

	/**
	 * Prüft eine Basis-URL: https beliebig, http nur für localhost/127.0.0.1; ohne Pfad, Query oder
	 * abschließenden Schrägstrich. Ungültig oder leer → Standard.
	 */
	static String baseUrl(String raw, String fallback) {
		if (raw == null || raw.trim().isEmpty()) return fallback;
		try {
			URI uri = new URI(raw.trim());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (host.isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null) return fallback;
			String path = uri.getRawPath() == null ? "" : uri.getRawPath();
			if (!path.isEmpty() && !path.equals("/")) return fallback;
			boolean local = host.equals("localhost") || host.equals("127.0.0.1");
			if (!scheme.equals("https") && !(scheme.equals("http") && local)) return fallback;
			return scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		} catch (Exception e) {
			return fallback;
		}
	}

	/** Hat der Launcher die Cubiq API erlaubt? */
	public boolean launcherEnabled() {
		return launcherEnabled;
	}

	/** z. B. {@code https://cubiq-launcher.invalid} (ohne Schrägstrich am Ende). */
	public String apiBase() {
		return apiBase;
	}

	/** z. B. {@code https://sessionserver.mojang.com}. */
	public String sessionBase() {
		return sessionBase;
	}

	/**
	 * Stammt eine Textur-URL aus der Lookup-Antwort wirklich von der Cubiq API? Nur solche URLs werden
	 * geladen – der Mod lädt nie Bilder von beliebigen Adressen. Erlaubt sind die eingestellte Adresse
	 * und die neue wie die alte Adresse der echten API.
	 */
	public boolean isApiUrl(String url) {
		if (url == null || url.length() >= 512) return false;
		if (url.startsWith(apiBase + "/")) return true;
		for (String known : KNOWN_APIS) {
			if (url.startsWith(known + "/")) return true;
		}
		return false;
	}
}
