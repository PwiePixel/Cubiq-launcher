package dev.pwiepixel.cubiqclient.core.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Die Sitzung, mit der das Spiel gestartet wurde – nur lesen. Der Cubiq Client meldet sich nie selbst bei Microsoft an,
 * speichert keine Konten und tauscht die Sitzung des Spiels nie aus: Wer mit einem anderen Konto spielen will, wählt es
 * vor dem Start im Launcher. Garderobe (Mojang-Skins und -Umhänge) und Fehlerberichte nehmen genau diese Sitzung; das
 * Zugangs-Token bleibt im Speicher und wird nie geloggt oder weitergegeben (außer an Mojang selbst).
 *
 * <p>Je Loader beim Start einmal {@link #init}: Der Baum liefert nur, wie man die Sitzung aus Minecraft liest. Dabei
 * werden die Kontodateien früherer Versionen mit Anmeldung im Spiel gelöscht, ohne sie zu lesen
 * ({@link #removeOldAccountFiles}).
 */
public final class CurrentSession {
	/** Im Spiel hinzugefügte Konten früherer Versionen ({@code config/cubiqclient/accounts.json}, Refresh-Tokens). */
	static final String OLD_ACCOUNTS = "accounts.json";
	/** Schlüssel zu diesen Tokens im Benutzerordner (nicht in der Instanz) – wurde nur dafür angelegt. */
	static final String OLD_KEY = "account-key.bin";

	private static volatile Supplier<SessionData> source;
	private static volatile String userAgent = "Cubiq-Client";

	private CurrentSession() {
	}

	/**
	 * Beim Start je Loader. {@code current} liest Name, UUID und Token aus Minecraft (aus jedem Thread, null =
	 * unbekannt); {@code configDir} = {@code config}-Ordner der Instanz.
	 */
	public static synchronized void init(Path configDir, String agent, Supplier<SessionData> current, Consumer<String> log) {
		source = current;
		if (agent != null && !agent.isEmpty()) userAgent = agent;
		removeOldAccountFiles(configDir, oldKeyDir(), log);
	}

	/** Sitzung des laufenden Spiels (mit Zugangs-Token – nur für Mojang-Dienste im Hintergrund, nie loggen) oder null. */
	public static SessionData get() {
		Supplier<SessionData> s = source;
		if (s == null) return null;
		try {
			return s.get();
		} catch (RuntimeException | LinkageError e) {
			return null;
		}
	}

	/** User-Agent für Netzabrufe der Oberfläche (Gesichter, Garderobe). */
	public static String userAgent() {
		return userAgent;
	}

	/** Nur für Tests. */
	static synchronized void reset() {
		source = null;
		userAgent = "Cubiq-Client";
	}

	/**
	 * Löscht die Kontodatei früherer Versionen in dieser Instanz und den Schlüssel dazu im Benutzerordner. Beides wird
	 * nie gelesen, nie hochgeladen und nie kopiert. Gelöscht werden nur Dateien (bei einer Verknüpfung nur sie selbst,
	 * nie ihr Ziel), der Schlüsselordner nur, wenn er danach leer ist. Fehler stören den Start nicht.
	 *
	 * @return Anzahl gelöschter Dateien
	 */
	static int removeOldAccountFiles(Path configDir, Path keyDir, Consumer<String> log) {
		int removed = 0;
		if (configDir != null) {
			Path dir = configDir.resolve("cubiqclient");
			removed += delete(dir.resolve(OLD_ACCOUNTS), log);
			removed += delete(dir.resolve(OLD_ACCOUNTS + ".tmp"), log);
		}
		if (keyDir != null) {
			int keys = delete(keyDir.resolve(OLD_KEY), log) + delete(keyDir.resolve(OLD_KEY + ".tmp"), log);
			if (keys > 0) {
				try {
					// Geht nur bei einem leeren Ordner – andere Dateien darin bleiben unberührt.
					if (Files.isDirectory(keyDir, LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(keyDir);
				} catch (IOException | RuntimeException ignored) {
					// Ordner bleibt
				}
			}
			removed += keys;
		}
		if (removed > 0 && log != null) log.accept("Gespeicherte Konten früherer Versionen gelöscht (" + removed + " Datei(en))");
		return removed;
	}

	private static int delete(Path file, Consumer<String> log) {
		try {
			if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)) return 0;
			return Files.deleteIfExists(file) ? 1 : 0;
		} catch (IOException | RuntimeException e) {
			if (log != null) log.accept("Alte Kontodatei konnte nicht gelöscht werden: " + e.getClass().getSimpleName());
			return 0;
		}
	}

	/** Wo frühere Versionen den Schlüssel ablegten: im Benutzerordner, nicht in der Instanz (null = unbekannt). */
	static Path oldKeyDir() {
		try {
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
				String appData = System.getenv("APPDATA");
				if (appData != null && !appData.isEmpty()) return Paths.get(appData, "cubiqclient");
			}
			String xdg = System.getenv("XDG_DATA_HOME");
			if (xdg != null && !xdg.isEmpty()) return Paths.get(xdg, "cubiqclient");
			return Paths.get(System.getProperty("user.home", "."), ".local", "share", "cubiqclient");
		} catch (RuntimeException e) {
			return null;
		}
	}
}
