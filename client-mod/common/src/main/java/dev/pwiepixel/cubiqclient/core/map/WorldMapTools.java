package dev.pwiepixel.cubiqclient.core.map;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import java.util.Locale;

/**
 * Werkzeuge der Weltkarte ohne Zeichnen: Länge des Maßstabs (runde Zahlen), Entfernungstext für Maßstab und Messen und
 * „Gehe zu“ – Koordinaten, die man in die Suche der Wegpunkt-Liste tippt.
 */
final class WorldMapTools {
	/** Größte Koordinate einer Minecraft-Welt (Weltgrenze). */
	static final int WORLD_LIMIT = 30_000_000;

	private WorldMapTools() {
	}

	/**
	 * Länge des Maßstabs in Blöcken: die größte runde Zahl (1, 2, 5, 10, 20, 50 …), die bei {@code pxPerBlock} GUI-Pixeln je
	 * Block in {@code maxPx} passt; 0, wenn nicht einmal ein Block passt.
	 */
	static int scaleBlocks(float pxPerBlock, float maxPx) {
		if (!(pxPerBlock > 0f) || !(maxPx > 0f)) return 0;
		int best = 0;
		for (long step = 1; step <= 10_000_000L; step *= 10) {
			for (int k : new int[]{1, 2, 5}) {
				long blocks = k * step;
				if (blocks * pxPerBlock <= maxPx) best = (int) blocks;
			}
		}
		return best;
	}

	/**
	 * Entfernung für Maßstab und Messen: „87 m“, ab einem Kilometer „1.2 km“ mit dem Dezimaltrenner der Sprache
	 * („1,2 km“); ganze Kilometer ohne Nachkomma.
	 */
	static String distanceLabel(double blocks) {
		long r = Math.round(blocks);
		if (r < 1000) return I18n.tr("map.distance", r);
		long tenths = Math.round(r / 100.0);
		String v = tenths % 10 == 0 ? Long.toString(tenths / 10) : String.format(I18n.locale(), "%.1f", tenths / 10.0);
		return I18n.tr("map.distanceKm", v);
	}

	/**
	 * Koordinaten aus der Suche: zwei Zahlen (X Z) oder drei (X Y Z), getrennt durch Leerzeichen, Komma, Semikolon oder
	 * Schrägstrich, auch mit Achsen („x 100 z -200“, „X:100 Z:-200“, „z=-200 x=100“). Kommazahlen werden abgerundet.
	 * Liefert {X, Z} oder null (z. B. für Wegpunkt-Namen, eine einzelne Zahl oder Werte jenseits der Weltgrenze).
	 */
	static int[] parseCoords(String query) {
		if (query == null) return null;
		String s = query.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty() || s.length() > 64) return null;
		// Achsen an ihre Zahl hängen („x: 100“ → „x100“), Trennzeichen zu Leerzeichen.
		s = s.replaceAll("([xyz])\\s*[:=]?\\s*", " $1").replaceAll("[,;/]", " ").trim();
		String[] tokens = s.split("\\s+");
		if (tokens.length < 2 || tokens.length > 3) return null;
		double[] values = new double[3];
		boolean[] given = new boolean[3];
		double[] plain = new double[3];
		int plainCount = 0;
		for (String token : tokens) {
			char axis = token.charAt(0);
			boolean named = axis == 'x' || axis == 'y' || axis == 'z';
			String number = named ? token.substring(1) : token;
			if (!number.matches("[+-]?\\d{1,9}(\\.\\d+)?")) return null;
			double v = Double.parseDouble(number);
			if (named) {
				int i = axis - 'x';
				if (given[i]) return null;
				given[i] = true;
				values[i] = v;
			} else {
				plain[plainCount++] = v;
			}
		}
		double x, z;
		if (given[0] || given[1] || given[2]) {
			// Mit Achsen: X und Z müssen dabei sein, ohne Achse nichts zusätzlich.
			if (!given[0] || !given[2] || plainCount > 0) return null;
			x = values[0];
			z = values[2];
		} else if (plainCount == 2) {
			x = plain[0];
			z = plain[1];
		} else {
			x = plain[0];
			z = plain[2];
		}
		if (Math.abs(x) > WORLD_LIMIT || Math.abs(z) > WORLD_LIMIT) return null;
		return new int[]{(int) Math.floor(x), (int) Math.floor(z)};
	}

	/** Waagerechte Entfernung zweier Punkte in Blöcken. */
	static double distance(double x0, double z0, double x1, double z1) {
		return Math.hypot(x1 - x0, z1 - z0);
	}
}
