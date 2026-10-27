package dev.pwiepixel.cubiqclient.core.link;

/**
 * Leistung für die Live-Anzeige im Cubiq Launcher: etwa einmal je Sekunde eine Zeile
 * {@code {"type":"perf","fps":…,"limited":…,"heapUsed":…,"heapMax":…}} (Java-Speicher in MB) – nur, wenn der Launcher
 * {@link #FEATURE} im {@code challenge} nennt. Reine Anzeige, nie ein Befehl.
 *
 * <p>Das Spiel legt die Bildrate im Client-Tick ab ({@link #offer}, billig: zwei Felder); gesendet wird im Netz-Thread
 * des Links ({@link CubiqLink}), nie im Spiel-Thread. Den Java-Speicher liest der Netz-Thread selbst.
 */
public final class PerfReport {
	/** Merkmal des Launchers: nimmt Leistungsmeldungen an. */
	public static final String FEATURE = "perf.report";
	/** Abstand der Meldungen (der Launcher verwirft schnellere). */
	static final long INTERVAL_MS = 950;
	/** Ältere Bildraten gehen nicht mehr mit (Spiel hängt oder misst gerade nicht). */
	static final long FPS_STALE_MS = 3000;
	private static final long MB = 1024L * 1024L;

	private static volatile double fps;
	private static volatile boolean limited;
	private static volatile long fpsAt;

	private long lastSent;

	/**
	 * Spiel-Thread (Client-Tick): aktuelle Bildrate ({@code 0} = gerade keine Messung) und ob „Dynamische FPS“ sie
	 * absichtlich bremst (Fenster im Hintergrund, AFK).
	 */
	public static void offer(long nowMillis, double currentFps, boolean frameLimited) {
		fps = currentFps > 0 && !Double.isNaN(currentFps) && !Double.isInfinite(currentFps) ? currentFps : 0;
		limited = frameLimited;
		fpsAt = nowMillis;
	}

	/** Nur für Tests. */
	static void reset() {
		fps = 0;
		limited = false;
		fpsAt = 0;
	}

	/**
	 * Netz-Thread: die fällige Zeile oder {@code null} (zu früh). {@code heapUsed}/{@code heapMax} in Bytes,
	 * {@code heapMax <= 0} oder {@link Long#MAX_VALUE} = unbekannt.
	 */
	String due(long nowMillis, long heapUsed, long heapMax) {
		if (lastSent != 0 && nowMillis - lastSent < INTERVAL_MS && nowMillis >= lastSent) return null;
		lastSent = nowMillis;
		boolean fresh = fpsAt != 0 && nowMillis - fpsAt <= FPS_STALE_MS && nowMillis >= fpsAt;
		long max = heapMax > 0 && heapMax != Long.MAX_VALUE ? heapMax / MB : -1;
		long used = heapUsed >= 0 ? heapUsed / MB : -1;
		return line(fresh ? fps : 0, fresh && limited, used, max);
	}

	/** Die Zeile selbst (ohne Zeilenumbruch); unbekannte Werte fehlen. */
	static String line(double currentFps, boolean frameLimited, long heapUsedMb, long heapMaxMb) {
		StringBuilder out = new StringBuilder("{\"type\":\"perf\"");
		if (currentFps > 0) out.append(",\"fps\":").append(Math.round(Math.min(currentFps, 10000)));
		out.append(",\"limited\":").append(frameLimited);
		if (heapUsedMb >= 0) out.append(",\"heapUsed\":").append(heapUsedMb);
		if (heapMaxMb > 0) out.append(",\"heapMax\":").append(heapMaxMb);
		return out.append('}').toString();
	}

	/** Java-Speicher dieses Spiels: belegt (Bytes). */
	static long heapUsed() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	/** Java-Speicher dieses Spiels: höchstens (Bytes, {@link Long#MAX_VALUE} = ohne Grenze). */
	static long heapMax() {
		return Runtime.getRuntime().maxMemory();
	}
}
