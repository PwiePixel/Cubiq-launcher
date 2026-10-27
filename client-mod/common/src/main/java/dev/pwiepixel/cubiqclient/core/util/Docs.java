package dev.pwiepixel.cubiqclient.core.util;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Links in die Dokumentation – das Wiki des GitHub-Repos: Seite {@code <bereich>/<seite>} heißt dort
 * {@code <bereich>-<seite>}, auf Deutsch und Spanisch mit Sprachvorsilbe ({@code de-client-overview}). In der
 * Sprache des Clients, sofern es die Docs darin gibt (en, de, es), sonst Englisch (wie {@code app/utils/docs.ts}).
 */
public final class Docs {
	/** Wurzel der Docs. */
	public static final String BASE = "https://github.com/PwiePixel/Cubiq-launcher/wiki";
	/** Sprachen, in denen es die Docs gibt. */
	public static final List<String> LANGUAGES = Collections.unmodifiableList(Arrays.asList("en", "de", "es"));

	/** Überblick über den Cubiq Client (Ziel des Eintrags „Hilfe“ im Cubiq-Menü). */
	public static final String CLIENT_OVERVIEW = "client/overview";

	private Docs() {
	}

	/** Docs-Sprache zu einer Client-Sprache ({@code "de"}, {@code "es"}, sonst {@code "en"} – auch für {@code "pt-BR"}). */
	public static String language(String code) {
		if (code == null) return "en";
		String base = code.toLowerCase(Locale.ROOT);
		int dash = base.indexOf('-');
		if (dash < 0) dash = base.indexOf('_');
		if (dash >= 0) base = base.substring(0, dash);
		return LANGUAGES.contains(base) ? base : "en";
	}

	/**
	 * Adresse einer Seite ({@code "client/overview"}); {@code null} oder eine ungültige Seite = Startseite der Sprache
	 * ({@code de-Home}, Englisch die Wiki-Wurzel). Erlaubt sind nur Kleinbuchstaben, Ziffern, Bindestriche und einzelne {@code /}.
	 */
	public static String url(String page, String languageCode) {
		String lang = language(languageCode);
		String prefix = "en".equals(lang) ? "" : lang + "-";
		if (!validPage(page)) return "en".equals(lang) ? BASE : BASE + "/" + prefix + "Home";
		return BASE + "/" + prefix + page.replace('/', '-');
	}

	/** {@link #url(String, String)} in der aktiven Client-Sprache. */
	public static String url(String page) {
		return url(page, I18n.code());
	}

	/** Seite im Standardbrowser öffnen; false = kein Browser gefunden. */
	public static boolean open(String page) {
		return Links.open(url(page));
	}

	static boolean validPage(String page) {
		if (page == null || page.isEmpty() || page.length() > 100) return false;
		if (page.charAt(0) == '/' || page.charAt(page.length() - 1) == '/') return false;
		char prev = '/';
		for (int i = 0; i < page.length(); i++) {
			char c = page.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '/';
			if (!ok || (c == '/' && prev == '/')) return false;
			prev = c;
		}
		return true;
	}
}
