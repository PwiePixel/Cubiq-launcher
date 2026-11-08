package dev.pwiepixel.cubiqclient.core.link;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Kryptografie der Cubiq-Link-Anmeldung (Protokoll 2) – muss Byte für Byte zum Launcher passen
 * ({@code src-tauri/crates/core/src/link}).
 *
 * <p>Beide Seiten beweisen, dass sie den Schlüssel K kennen (HMAC-SHA256 über beide Zufallswerte), ohne ihn je über
 * die Verbindung zu schicken – ein fremdes Programm auf dem Port bekommt nichts. Alles mit Java-8-Bordmitteln
 * ({@code javax.crypto.Mac}), Vergleiche in konstanter Laufzeit.
 */
public final class LinkCrypto {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private LinkCrypto() {
	}

	public static byte[] hmac(byte[] key, String message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 fehlt", e);
		}
	}

	/** Beweis des Launchers (Schritt 2). */
	public static String launcherProof(byte[] key, String sid, String gameNonce, String launcherNonce) {
		return hex(hmac(key, "cubiq-link/2/launcher\n" + sid + "\n" + gameNonce + "\n" + launcherNonce));
	}

	/** Beweis des Spiels (Schritt 3). */
	public static String gameProof(byte[] key, String sid, String gameNonce, String launcherNonce) {
		return hex(hmac(key, "cubiq-link/2/game\n" + sid + "\n" + gameNonce + "\n" + launcherNonce));
	}

	/** Vergleich in konstanter Laufzeit (Länge ist kein Geheimnis). */
	public static boolean equalsConstantTime(String a, String b) {
		if (a == null || b == null) return false;
		return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	public static String randomHex(int bytes) {
		byte[] b = new byte[bytes];
		RANDOM.nextBytes(b);
		return hex(b);
	}

	public static String hex(byte[] bytes) {
		char[] out = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
			out[i * 2 + 1] = HEX[bytes[i] & 0xF];
		}
		return new String(out);
	}

	/** Nur Kleinbuchstaben-Hex gerader Länge; sonst null. */
	public static byte[] unhex(String s) {
		if (s == null || (s.length() & 1) != 0) return null;
		byte[] out = new byte[s.length() / 2];
		for (int i = 0; i < out.length; i++) {
			int hi = digit(s.charAt(i * 2));
			int lo = digit(s.charAt(i * 2 + 1));
			if (hi < 0 || lo < 0) return null;
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}

	public static boolean isHex(String s, int length) {
		if (s == null || s.length() != length) return false;
		for (int i = 0; i < s.length(); i++) {
			if (digit(s.charAt(i)) < 0) return false;
		}
		return true;
	}

	private static int digit(char c) {
		if (c >= '0' && c <= '9') return c - '0';
		if (c >= 'a' && c <= 'f') return c - 'a' + 10;
		return -1;
	}
}
