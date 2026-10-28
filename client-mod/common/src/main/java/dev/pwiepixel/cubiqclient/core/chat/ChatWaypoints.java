package dev.pwiepixel.cubiqclient.core.chat;

import dev.pwiepixel.cubiqclient.core.waypoint.WaypointShare;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Geteilte Wegpunkte im Minecraft-Chat im verbreiteten Format der Xaero-Minimap
 * („xaero-waypoint:Haus:H:100:64:-200:6:false:0:Internal-overworld-waypoints“): erkennen – der Chat zeigt dann Name,
 * Koordinaten und „[+ Wegpunkt]“ – und eigene Wegpunkte in diesem Format schreiben, damit auch Spieler mit dieser
 * Minimap sie übernehmen können. Nur Text: gesendet wird erst, wenn der Spieler die Chatzeile selbst abschickt.
 *
 * <p>Aufbau nach dem Merker: Name, Kürzel, X, Y („~“ = ohne Höhe), Z, Farbe (0–15 wie die Chat-Farben), danach
 * optional Drehen beim Teleport, Blickrichtung und der Satz der Wegpunkte („Internal-the-nether-waypoints“ – daraus
 * die Dimension). Doppelpunkte im Namen sind möglich: die Koordinaten werden als drei Zahlen ab dem dritten Feld
 * gesucht.
 */
public final class ChatWaypoints {
	/** Merker vor jedem geteilten Wegpunkt. */
	public static final String MARKER = "xaero-waypoint:";
	/** Höhe fehlt („~“). */
	public static final int NO_Y = Integer.MIN_VALUE;
	/** Farben der geteilten Wegpunkte: die 16 Chat-Farben von Minecraft (Index = Farbcode 0–f). */
	static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	/** Farbe ohne (gültige) Angabe: Weiß wie bei den Wegpunkten ohne Farbe. */
	static final int DEFAULT_COLOR = 0xFFFFFF;
	/** Längere Namen werden gekürzt (wie gespeicherte Wegpunkte). */
	static final int MAX_NAME = 32;
	/** Längere Zeilen werden nicht durchsucht. */
	static final int MAX_LENGTH = 512;

	private static final Pattern INT = Pattern.compile("[+-]?\\d{1,9}");
	private static final Pattern LEGACY_DIM = Pattern.compile("dim%(-?\\d{1,9})");
	private static final Pattern MODDED_DIM = Pattern.compile("dim%([a-z0-9_.-]{1,32})\\$([a-z0-9_./-]{1,64})");

	private ChatWaypoints() {
	}

	/** Ein geteilter Wegpunkt in einer Chatzeile. */
	public static final class Shared {
		/** Bereich im Text [start, end) – ab dem Merker. */
		public final int start;
		public final int end;
		public final String name;
		public final String initials;
		public final int x;
		/** {@link #NO_Y} = ohne Höhe. */
		public final int y;
		public final int z;
		/** Farbe 0xRRGGBB. */
		public final int color;
		/** Dimension als Namensraum-Kennung ({@code minecraft:the_nether}) oder null = unbekannt. */
		public final String dimension;
		/** Der geteilte Text selbst (ab dem Merker) – Einfügen per Umschalt-Klick, Klick-Marker. */
		public final String raw;

		Shared(int start, int end, String name, String initials, int x, int y, int z, int color, String dimension, String raw) {
			this.start = start;
			this.end = end;
			this.name = name;
			this.initials = initials;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.dimension = dimension;
			this.raw = raw;
		}

		public boolean hasY() {
			return y != NO_Y;
		}

		/** „100 64 -200“ bzw. „100 ~ -200“ ohne Höhe. */
		public String coords() {
			return x + " " + (hasY() ? Integer.toString(y) : "~") + " " + z;
		}
	}

	/** Erster geteilter Wegpunkt der Zeile oder null. */
	public static Shared find(String text) {
		if (text == null || text.length() > MAX_LENGTH) return null;
		for (int at = text.indexOf(MARKER); at >= 0; at = text.indexOf(MARKER, at + 1)) {
			Shared s = parseAt(text, at);
			if (s != null) return s;
		}
		return null;
	}

	/** Klick-Marker (der geteilte Text ab dem Merker) zurück zum Wegpunkt; null = kein geteilter Wegpunkt. */
	public static Shared parse(String raw) {
		return raw != null && raw.startsWith(MARKER) && raw.length() <= MAX_LENGTH ? parseAt(raw, 0) : null;
	}

	private static Shared parseAt(String text, int at) {
		String payload = text.substring(at + MARKER.length());
		List<String> tokens = new ArrayList<String>();
		int from = 0;
		for (int colon = payload.indexOf(':'); colon >= 0; colon = payload.indexOf(':', from)) {
			tokens.add(payload.substring(from, colon));
			from = colon + 1;
		}
		tokens.add(payload.substring(from));
		// X, Y, Z: drei Zahlen ab dem dritten Feld (davor Name und Kürzel; der Name darf Doppelpunkte enthalten).
		int i = -1;
		for (int k = 2; k + 2 < tokens.size(); k++) {
			if (isInt(tokens.get(k)) && (isInt(tokens.get(k + 1)) || "~".equals(tokens.get(k + 1))) && isInt(tokens.get(k + 2))) {
				i = k;
				break;
			}
		}
		if (i < 0) return null;
		long x = Long.parseLong(strip(tokens.get(i))), z = Long.parseLong(strip(tokens.get(i + 2)));
		String yText = tokens.get(i + 1);
		long y = "~".equals(yText) ? NO_Y : Long.parseLong(strip(yText));
		if (Math.abs(x) > ChatCoords.MAX_XZ || Math.abs(z) > ChatCoords.MAX_XZ) return null;
		if (y != NO_Y && (y < ChatCoords.MIN_Y || y > ChatCoords.MAX_Y)) return null;
		StringBuilder nameB = new StringBuilder();
		for (int k = 0; k < i - 1; k++) {
			if (k > 0) nameB.append(':');
			nameB.append(tokens.get(k));
		}
		String name = clean(nameB.toString().replace("§§", ":"), MAX_NAME);
		String initials = clean(tokens.get(i - 1), 2);
		if (name.isEmpty()) name = initials;
		if (initials.isEmpty()) initials = initial(name);
		// Länge bis einschließlich Z, dann optional Farbe, Drehen, Blickrichtung und der Satz (bis zum Leerzeichen).
		int used = 0;
		for (int k = 0; k <= i + 2; k++) used += tokens.get(k).length() + 1;
		used--;
		int color = DEFAULT_COLOR;
		String dimension = null;
		int field = i + 3;
		if (field < tokens.size() && isInt(firstWord(tokens.get(field)))) {
			String c = firstWord(tokens.get(field));
			int index = Integer.parseInt(strip(c));
			if (index >= 0 && index < COLORS.length) color = COLORS[index];
			used += 1 + c.length();
			boolean whole = c.length() == tokens.get(field).length();
			field++;
			if (whole && field < tokens.size() && isBool(firstWord(tokens.get(field)))) {
				String r = firstWord(tokens.get(field));
				used += 1 + r.length();
				whole = r.length() == tokens.get(field).length();
				field++;
				if (whole && field < tokens.size() && isInt(firstWord(tokens.get(field)))) {
					String yaw = firstWord(tokens.get(field));
					used += 1 + yaw.length();
					whole = yaw.length() == tokens.get(field).length();
					field++;
					if (whole && field < tokens.size()) {
						String set = firstWord(tokens.get(field));
						if (!set.isEmpty()) {
							dimension = dimensionOf(set);
							used += 1 + set.length();
						}
					}
				}
			}
		}
		int start = at, end = at + MARKER.length() + used;
		return new Shared(start, end, name, initials, (int) x, (int) y, (int) z, color, dimension, text.substring(start, end));
	}

	/**
	 * Eigener Wegpunkt als Chat-Text in diesem Format ({@code dimension} in der Schreibweise des Spiels, z. B.
	 * {@code minecraft:the_nether} oder Legacy {@code dim-1}; {@code y} = {@link #NO_Y} für ohne Höhe).
	 */
	public static String format(String name, int x, int y, int z, int color, String dimension) {
		String n = clean(name == null ? "" : name.replace(':', ' '), MAX_NAME);
		if (n.isEmpty()) n = "?";
		StringBuilder b = new StringBuilder(MARKER);
		b.append(n).append(':').append(initial(n)).append(':').append(x).append(':');
		b.append(y == NO_Y ? "~" : Integer.toString(y)).append(':').append(z).append(':').append(colorIndex(color));
		b.append(":false:0:").append(waypointSet(dimension));
		return b.toString();
	}

	/** Farbtöne der bunten Chat-Farben (Grad) mit ihrem dunklen und hellen Index (-1 = nur eine Fassung). */
	private static final int[][] HUES = {{0, 4, 12}, {40, 6, -1}, {60, 14, -1}, {120, 2, 10}, {180, 3, 11}, {240, 1, 9},
			{300, 5, 13}, {360, 4, 12}};

	/**
	 * Passende der 16 Chat-Farben zu einer Wegpunkt-Farbe (Index 0–15): Graues nach der Helligkeit, Buntes nach dem
	 * nächsten Farbton und dann dunkel oder hell – so wird Orange zu Gold und Hellblau zu Blau statt zu Grau.
	 */
	static int colorIndex(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float v = max, s = max <= 0f ? 0f : (max - min) / max;
		if (s < 0.25f || v < 0.2f) return v < 0.17f ? 0 : v < 0.5f ? 8 : v < 0.84f ? 7 : 15;
		float d = max - min, h;
		if (max == r) h = 60f * (((g - b) / d) % 6f);
		else if (max == g) h = 60f * ((b - r) / d + 2f);
		else h = 60f * ((r - g) / d + 4f);
		if (h < 0f) h += 360f;
		int[] best = HUES[0];
		for (int[] hue : HUES) {
			if (Math.abs(h - hue[0]) < Math.abs(h - best[0])) best = hue;
		}
		return best[2] < 0 || v < 0.78f ? best[1] : best[2];
	}

	/** Satz der Wegpunkte zur Dimension („Internal-the-nether-waypoints“); unbekannt = Oberwelt. */
	static String waypointSet(String dimension) {
		String d = WaypointShare.canonicalDimension(dimension);
		if (d == null || d.equals("minecraft:overworld")) return "Internal-overworld-waypoints";
		if (d.equals("minecraft:the_nether")) return "Internal-the-nether-waypoints";
		if (d.equals("minecraft:the_end")) return "Internal-the-end-waypoints";
		if (d.startsWith("legacy:dim")) return "Internal-dim%" + d.substring("legacy:dim".length()) + "-waypoints";
		int colon = d.indexOf(':');
		return "Internal-dim%" + d.substring(0, colon) + "$" + d.substring(colon + 1) + "-waypoints";
	}

	/** Dimension aus dem Satz der Wegpunkte; null = nicht zu erkennen. */
	static String dimensionOf(String set) {
		String s = set.toLowerCase(Locale.ROOT);
		Matcher modded = MODDED_DIM.matcher(s);
		if (modded.find()) {
			String path = modded.group(2);
			if (path.endsWith("-waypoints")) path = path.substring(0, path.length() - "-waypoints".length());
			return WaypointShare.canonicalDimension(modded.group(1) + ":" + path);
		}
		Matcher legacy = LEGACY_DIM.matcher(s);
		if (legacy.find()) return WaypointShare.canonicalDimension("dim" + legacy.group(1));
		if (s.contains("nether")) return "minecraft:the_nether";
		if (s.contains("the-end") || s.contains("the_end")) return "minecraft:the_end";
		if (s.contains("overworld")) return "minecraft:overworld";
		return null;
	}

	/** Anfangsbuchstabe (groß) eines Namens, "?" wenn es keinen gibt – wie auf den Karten. */
	public static String initial(String name) {
		if (name != null) {
			for (int i = 0; i < name.length(); ) {
				int cp = name.codePointAt(i);
				if (Character.isLetterOrDigit(cp)) return new String(Character.toChars(Character.toUpperCase(cp)));
				i += Character.charCount(cp);
			}
		}
		return "?";
	}

	private static boolean isInt(String s) {
		return INT.matcher(s).matches();
	}

	private static boolean isBool(String s) {
		return "true".equals(s) || "false".equals(s);
	}

	private static String strip(String s) {
		return s.startsWith("+") ? s.substring(1) : s;
	}

	/** Text bis zum ersten Leerraum (danach steht in der Chatzeile kein Teil des Wegpunkts mehr). */
	private static String firstWord(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) return s.substring(0, i);
		}
		return s;
	}

	/** Ohne Steuer- und Formatzeichen, getrimmt, höchstens {@code max} Zeichen. */
	private static String clean(String s, int max) {
		StringBuilder b = new StringBuilder(Math.min(s.length(), max));
		for (int i = 0; i < s.length() && b.length() < max; i++) {
			char c = s.charAt(i);
			if (c >= ' ' && c != 127 && c != '§') b.append(c);
		}
		return b.toString().trim();
	}
}
