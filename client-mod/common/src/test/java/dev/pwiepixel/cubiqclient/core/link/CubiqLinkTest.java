package dev.pwiepixel.cubiqclient.core.link;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
		byte[] seal = LinkCrypto.sealKey(k, nc, nl);
		assertEquals("f6cc2d52a5afa6082ede8265df4e8b57fb50b197173b07b3ea2c9282148a3be5", LinkCrypto.hex(seal));
		StringBuilder p = new StringBuilder("token-");
		for (int i = 0; i < 60; i++) p.append('x');
		String expected = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf.1005d887d9fe3b7465beb7a87447311928f9a8e6e985dfb7c23af80b80d240b687c8336cea"
				+ "59940c674dd2b2bc232c5700f5d0b25a444bb74a154c487a53a13aad0e.4118a14dc743789c9a7b4760eddd0043bcd99b03545ba69af92730526b3ca1f9";
		byte[] nonce = LinkCrypto.unhex("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
		assertEquals(expected, LinkCrypto.seal(seal, p.toString().getBytes(StandardCharsets.UTF_8), nonce));
		assertEquals(p.toString(), new String(LinkCrypto.unseal(seal, expected), StandardCharsets.UTF_8));
		// Manipuliert → nichts.
		assertNull(LinkCrypto.unseal(seal, expected.substring(0, 40) + "0" + expected.substring(41)));
		assertNull(LinkCrypto.unseal(seal, "kaputt"));
		assertNull(LinkCrypto.unseal(LinkCrypto.hmac(k, "anderer"), expected));
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
	void anmeldungKontenUndVersiegeltesToken(@TempDir Path dir) throws Exception {
		try (FakeLauncher launcher = new FakeLauncher()) {
			CubiqLink link = new CubiqLink(dir, launcher.env());
			final CountDownLatch connected = new CountDownLatch(1);
			link.addListener(new CubiqLink.Listener() {
				@Override
				public void onConnected(CubiqLink l, boolean accounts) {
					if (accounts) connected.countDown();
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
				link.request("accounts.session", Collections.singletonMap("account", FakeLauncher.STEVE), 10000, cb);
				Object r = results.poll(20, TimeUnit.SECONDS);
				assertTrue(r instanceof CubiqLink.Line, "Antwort: " + r);
				CubiqLink.Line res = (CubiqLink.Line) r;
				assertEquals("Steve", res.session.name);
				assertFalse(res.session.token.contains("mc-token"), "Token kommt versiegelt");
				assertEquals("mc-token-Steve", new String(link.unseal(res.session.token), StandardCharsets.UTF_8));

				link.request("accounts.add", null, 10000, cb);
				assertEquals("busy", results.poll(20, TimeUnit.SECONDS));
				link.request("accounts.frob", null, 10000, cb);
				assertEquals("error", results.poll(20, TimeUnit.SECONDS), "unbekannte Fehlertexte werden nicht durchgereicht");
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
		link.request("accounts.list", null, 1000, new CubiqLink.Callback() {
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
