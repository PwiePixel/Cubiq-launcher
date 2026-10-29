package dev.pwiepixel.cubiqclient.core.account;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Die Sitzung, mit der das Spiel läuft – nur lesen. Garderobe (Mojang-Skins und -Umhänge) und Fehlerberichte nehmen
 * genau diese Sitzung; das Zugangs-Token bleibt im Speicher und wird nie geloggt oder weitergegeben (außer an Mojang
 * selbst).
 *
 * <p>Je Loader beim Start einmal {@link #init}: Der Baum liefert nur, wie man die Sitzung aus Minecraft liest.
 */
public final class CurrentSession {
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
}
