package dev.pwiepixel.cubiqclient.core.link;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Leistungsmeldungen an den Launcher (Live-Anzeige): Format, Takt und dass sie nur mit Merkmal gehen. */
class PerfReportTest {
	private static final long MB = 1024L * 1024L;

	@AfterEach
	void reset() {
		PerfReport.reset();
	}

	@Test
	void zeileEnthaeltNurBekannteWerte() {
		assertEquals("{\"type\":\"perf\",\"fps\":144,\"limited\":false,\"heapUsed\":1834,\"heapMax\":4096}",
				PerfReport.line(143.6, false, 1834, 4096));
		assertEquals("{\"type\":\"perf\",\"limited\":true,\"heapUsed\":12}", PerfReport.line(0, true, 12, -1));
		assertEquals("{\"type\":\"perf\",\"fps\":10000,\"limited\":false}", PerfReport.line(123456, false, -1, 0));
		JsonObject o = new JsonParser().parse(PerfReport.line(59.5, false, 1, 2)).getAsJsonObject();
		assertEquals("perf", o.get("type").getAsString());
		assertEquals(60, o.get("fps").getAsInt());
	}

	@Test
	void taktUndFrischeDerBildrate() {
		PerfReport report = new PerfReport();
		PerfReport.offer(1000, 120.2, false);
		String first = report.due(1500, 512 * MB, 4096 * MB);
		assertEquals("{\"type\":\"perf\",\"fps\":120,\"limited\":false,\"heapUsed\":512,\"heapMax\":4096}", first);
		// Zu früh: nichts.
		assertNull(report.due(2000, 512 * MB, 4096 * MB));
		// Bildrate zu alt (Spiel misst nicht mehr): nur Speicher.
		String stale = report.due(5000, 600 * MB, 4096 * MB);
		assertEquals("{\"type\":\"perf\",\"limited\":false,\"heapUsed\":600,\"heapMax\":4096}", stale);
		// Gebremst (Fenster im Hintergrund) und ohne Grenze des Java-Speichers.
		PerfReport.offer(6000, 0, true);
		assertEquals("{\"type\":\"perf\",\"limited\":true,\"heapUsed\":700}", report.due(6100, 700 * MB, Long.MAX_VALUE));
		// Unsinn wird zu „keine Messung“.
		PerfReport.offer(7000, Double.NaN, false);
		assertFalse(report.due(7100, 1, 1).contains("fps"));
		PerfReport.offer(8200, Double.POSITIVE_INFINITY, false);
		assertFalse(report.due(8300, 1, 1).contains("fps"));
		// Uhr springt zurück: nicht festhängen.
		assertNotNull(report.due(100, 1, 1));
	}

	@Test
	void echterSpeicherDesSpiels() {
		assertTrue(PerfReport.heapUsed() > 0);
		assertTrue(PerfReport.heapMax() >= PerfReport.heapUsed());
	}

	private static String nextPerf(FakeLauncher launcher, long timeoutMs) throws InterruptedException {
		long until = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < until) {
			String line = launcher.received.poll(200, TimeUnit.MILLISECONDS);
			if (line != null && line.contains("\"type\":\"perf\"")) return line;
		}
		return null;
	}

	@Test
	void gehtNurAnLauncherMitMerkmal(@TempDir Path dir) throws Exception {
		try (FakeLauncher launcher = new FakeLauncher()) {
			launcher.features = "[\"clips\",\"accounts\",\"" + PerfReport.FEATURE + "\"]";
			PerfReport.offer(System.currentTimeMillis(), 87.4, false);
			CubiqLink link = new CubiqLink(dir, launcher.env());
			link.start();
			try {
				String line = nextPerf(launcher, 20000);
				assertNotNull(line, "Leistungsmeldung kommt an");
				JsonObject o = new JsonParser().parse(line).getAsJsonObject();
				assertTrue(o.get("heapUsed").getAsLong() > 0);
				assertTrue(link.status().has(PerfReport.FEATURE));
				// Etwa einmal je Sekunde, nicht öfter.
				long before = System.currentTimeMillis();
				assertNotNull(nextPerf(launcher, 5000), "nächste Meldung");
				assertTrue(System.currentTimeMillis() - before >= PerfReport.INTERVAL_MS - 400);
			} finally {
				link.stop();
			}
		}
	}

	@Test
	void alterLauncherBekommtKeineMeldung(@TempDir Path dir) throws Exception {
		try (FakeLauncher launcher = new FakeLauncher()) {
			PerfReport.offer(System.currentTimeMillis(), 60, false);
			CubiqLink link = new CubiqLink(dir, launcher.env());
			link.start();
			try {
				long until = System.currentTimeMillis() + 20000;
				while (!link.status().connected && System.currentTimeMillis() < until) Thread.sleep(50);
				assertTrue(link.status().connected, "verbunden");
				assertNull(nextPerf(launcher, 3000), "ohne Merkmal keine Leistungsmeldung");
			} finally {
				link.stop();
			}
		}
	}
}
