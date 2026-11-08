package dev.pwiepixel.cubiqclient.core.link;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CubiqLinkTest {
	/** Dieselben Werte stehen im Launcher-Test (src-tauri/crates/core/src/link) – beide Seiten müssen sie treffen. */
	@Test
	void kryptoStimmtMitDemLauncherUeberein() {
		byte[] k = FakeLauncher.KEY;
		String sid = "0123456789abcdef";
		String nc = "00112233445566778899aabbccddeeff";
		String nl = "ffeeddccbbaa99887766554433221100";
		assertEquals("991f1e4237c9d0bf36d007ea95bb61fc10b58df4b54d7ee99bf359e737d86d42", LinkCrypto.launcherProof(k, sid, nc, nl));
		assertEquals("74d8da2a2a4be6655c3839cc07ec0962a2195053b84c47f4e0b67912347e62f9", LinkCrypto.gameProof(k, sid, nc, nl));
	}

	@Test
	void umgebungsvariableWirdStrengGelesen() {
		String key = LinkCrypto.hex(FakeLauncher.KEY);
		assertNotNull(LinkTarget.parseEnv("2:51234:0123456789abcdef:" + key));
		assertNull(LinkTarget.parseEnv("1:51234:0123456789abcdef:" + key));
		assertNull(LinkTarget.parseEnv("2:70000:0123456789abcdef:" + key));
		assertNull(LinkTarget.parseEnv("2:51234:0123456789ABCDEF:" + key));
		assertNull(LinkTarget.parseEnv("2:51234:0123456789abcdef:" + key.substring(2)));
		assertNull(LinkTarget.parseEnv("2:51234:0123456789abcdef:" + key + ":x"));
		assertNull(LinkTarget.parseEnv(null));
		// Kein Geheimnis im Text der Klasse.
		assertFalse(LinkTarget.parseEnv("2:51234:0123456789abcdef:" + key).toString().contains(key));
	}

	private static void writeClips(Path configDir, String json) throws Exception {
		Path dir = configDir.resolve("cubiqclient");
		Files.createDirectories(dir);
		Files.write(dir.resolve("clips.json"), json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void protokoll2DateiOhneUmgebungVerbindetNicht(@TempDir Path dir) throws Exception {
		writeClips(dir, "{\"version\":2,\"enabled\":true,\"port\":51234,\"token\":\"" + LinkCrypto.hex(FakeLauncher.KEY) + "\"}");
		LinkTarget t = LinkTarget.resolve(null, dir, false);
		assertEquals(LinkTarget.Kind.DISABLED, t.kind, "Protokoll 2 ohne Schlüssel aus der Umgebung: kein Token aus der Datei");
		LinkTarget env = LinkTarget.parseEnv("2:40000:" + FakeLauncher.SID + ":" + LinkCrypto.hex(FakeLauncher.KEY));
		assertEquals(40000, LinkTarget.resolve(env, dir, false).port);
		assertEquals(51234, LinkTarget.resolve(env, dir, true).port, "nach Launcher-Neustart: Port aus der Datei");
		writeClips(dir, "{\"version\":2,\"enabled\":false,\"port\":51235}");
		LinkTarget off = LinkTarget.resolve(env, dir, true);
		assertEquals(51235, off.port);
		assertFalse(off.clipsEnabled);
	}

	@Test
	void anmeldungOhneKonten(@TempDir Path dir) throws Exception {
		try (FakeLauncher launcher = new FakeLauncher()) {
			// Ein älterer Launcher nennt noch „accounts“ – die Mod fragt trotzdem nie nach Konten oder Tokens.
			launcher.features = "[\"clips\",\"accounts\"]";
			CubiqLink link = new CubiqLink(dir, launcher.env());
			final CountDownLatch connected = new CountDownLatch(1);
			link.addListener(new CubiqLink.Listener() {
				@Override
				public void onConnected(CubiqLink l) {
					connected.countDown();
				}

				@Override
				public void onLine(CubiqLink l, CubiqLink.Line line) {
				}

				@Override
				public void onDisconnected(CubiqLink l) {
				}
			});
			link.start();
			try {
				assertTrue(connected.await(20, TimeUnit.SECONDS), "verbunden");
				assertTrue(launcher.authOk);
				JsonObject hello = new JsonParser().parse(launcher.received.poll(5, TimeUnit.SECONDS)).getAsJsonObject();
				assertEquals(2, hello.get("v").getAsInt());
				assertEquals(FakeLauncher.SID, hello.get("sid").getAsString());
				assertFalse(hello.toString().contains(LinkCrypto.hex(FakeLauncher.KEY)), "Schlüssel geht nie über die Leitung");
				JsonObject auth = new JsonParser().parse(launcher.received.poll(5, TimeUnit.SECONDS)).getAsJsonObject();
				assertEquals("auth", auth.get("type").getAsString());
				assertFalse(auth.toString().contains("accounts"), "die Mod nennt keine Konten");
				assertEquals(2, link.status().protocol);

				final BlockingQueue<Object> results = new LinkedBlockingQueue<Object>();
				CubiqLink.Callback cb = new CubiqLink.Callback() {
					@Override
					public void done(CubiqLink.Line response) {
						results.add(response);
					}

					@Override
					public void failed(String code) {
						results.add(code);
					}
				};
				link.request("clips.frob", null, 10000, cb);
				assertEquals("error", results.poll(20, TimeUnit.SECONDS), "unbekannte Fehlertexte werden nicht durchgereicht");
				// Von sich aus hat die Mod nichts weiter angefragt (keine Konten, keine Sitzung).
				String line;
				while ((line = launcher.received.poll(1, TimeUnit.SECONDS)) != null) {
					assertFalse(line.contains("accounts."), line);
				}
			} finally {
				link.stop();
			}
		}
	}

	@Test
	void falscherLauncherBekommtKeinenBeweis(@TempDir Path dir) throws Exception {
		try (FakeLauncher launcher = new FakeLauncher()) {
			launcher.forgeProof = true;
			CubiqLink link = new CubiqLink(dir, launcher.env());
			link.start();
			try {
				assertNotNull(launcher.received.poll(20, TimeUnit.SECONDS), "hello");
				// Kein auth – die Mod trennt, bevor sie irgendetwas beweist.
				assertNull(launcher.received.poll(3, TimeUnit.SECONDS));
				assertFalse(link.status().connected);
				assertFalse(launcher.authOk);
			} finally {
				link.stop();
			}
		}
	}

	@Test
	void anfrageOhneVerbindungScheitertSofort(@TempDir Path dir) {
		CubiqLink link = new CubiqLink(dir, null);
		final String[] code = new String[1];
		link.request("clips.enable", null, 1000, new CubiqLink.Callback() {
			@Override
			public void done(CubiqLink.Line response) {
			}

			@Override
			public void failed(String c) {
				code[0] = c;
			}
		});
		assertEquals("offline", code[0]);
		assertFalse(link.launchedByLauncher());
	}
}
