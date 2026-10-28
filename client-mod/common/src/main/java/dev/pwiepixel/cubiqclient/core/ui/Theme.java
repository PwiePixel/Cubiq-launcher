package dev.pwiepixel.cubiqclient.core.ui;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Farben des Cubiq-Menüs. Der Launcher schreibt vor dem Start seine Oberflächen-Einstellungen nach
 * {@code config/cubiqclient/launcher-theme.json}; fehlt die Datei oder ist sie ungültig, gilt das
 * dunkle Cubiq-Design mit Cubiq-Grün. Alle Werte werden geprüft und begrenzt.
 */
public final class Theme {
	/** Datei, die der Launcher schreibt (relativ zum Config-Ordner der Instanz). */
	public static final String FILE = "cubiqclient/launcher-theme.json";
	/** Größere Dateien werden gar nicht erst gelesen. */
	private static final long MAX_BYTES = 8 * 1024;

	/** Gson-DTO der Launcher-Datei. */
	static final class File {
		int version;
		String theme;
		String accent;
		String accentColor;
		/** "cubiq" oder "winter" (Polarlicht) wie im Launcher; "fox" wählt man im Spiel, alles andere = Cubiq. */
		String design;
		/** Aktives Cubiq-Event für dieses Konto ({@code "halloween"}) – fehlt sonst. */
		String event;
		/** Halloween: „Flackern &amp; Rauschen“ im Launcher (fehlt = Standard an). */
		Boolean halloweenFlicker;
		/**
		 * Zeigt der Launcher den Event-Look? false = vom Team gesperrt oder im Launcher ausgeschaltet (fehlt = an, ältere
		 * Launcher). {@link #event} bleibt davon unberührt (Funktionen).
		 */
		Boolean eventLook;
		/**
		 * Animationen im Launcher: {@code "reduced"} = Standbilder („Wie System“ löst der Launcher selbst auf);
		 * fehlt (ältere Launcher) oder unbekannt = {@code "full"}.
		 */
		String motion;
	}

	/** Werte von {@code motion} in der Launcher-Datei. */
	public static final String MOTION_FULL = "full";
	public static final String MOTION_REDUCED = "reduced";

	/**
	 * Steht „Animationen“ im Launcher auf „Reduziert“? Nur ein zusätzliches Signal neben der eigenen Einstellung
	 * „Animierter Hintergrund“ – die bleibt, wie der Spieler sie gesetzt hat (siehe
	 * {@link dev.pwiepixel.cubiqclient.core.menus.MenuStyle#animated(boolean)}).
	 */
	private static volatile boolean launcherReducedMotion;

	/** Design „Cubiq“ (Standard, wie der Launcher), „Polarlicht“ (wie der Launcher) und „Fuchsbau“. */
	public static final String CUBIQ = "cubiq";
	public static final String WINTER = "winter";
	public static final String FOX = "fox";

	/**
	 * Akzent-Skalen des Launchers ({@code app/assets/css/main.css}, {@code --color-primary-300/400/500/600}), erkannt am
	 * Grundton (500). Eigene Farben bekommen eine abgeleitete Skala.
	 */
	private static final int[][] SCALES = {
			{0xFFA9D46B, 0xFF88BB45, 0xFF71A330, 0xFF5C8726},
			{0xFFFFD48A, 0xFFFFB84D, 0xFFE0900C, 0xFFB06E00},
			{0xFF86EFAC, 0xFF4ADE80, 0xFF17A34A, 0xFF15803D},
			{0xFF9DBAFF, 0xFF6B95FF, 0xFF3563E9, 0xFF2447B8},
			{0xFFDCBCFF, 0xFFC38CFB, 0xFF9B4DDF, 0xFF7A2FC0},
			{0xFFB8E7FF, 0xFF7FD3FF, 0xFF2B97D6, 0xFF1C74AD},
			{0xFFFFC89A, 0xFFFF9A52, 0xFFE8702A, 0xFFB8521C}};
	/** Schrift auf der Akzentfarbe, wenn sie hell genug ist ({@code --color-on-primary}); sonst Weiß. */
	private static final int ON_LIGHT_ACCENT = 0xFF0B1405;
	/** Hinweise, Warnungen, „ausstehend“ ({@code --color-caution-300}, dunkel/hell). */
	private static final int CAUTION_DARK = 0xFFF8CD7A;
	private static final int CAUTION_LIGHT = 0xFF8A5A00;
	/** Fehler, Löschen, Sperren – rot, egal welche Akzentfarbe ({@code --color-danger-300}, dunkel/hell). */
	private static final int DANGER_DARK = 0xFFFDA4A4;
	private static final int DANGER_LIGHT = 0xFFB42318;

	private static Theme current = new Theme("dark", "cubiq", 0xFF71A330, CUBIQ);
	/** Halloween-Variante des aktuellen Themas (zwischengespeichert, solange {@link #current} gleich bleibt). */
	private static volatile Theme spooky;

	public final String themeName;
	public final String accentName;
	/** Design: {@link #CUBIQ}, {@link #WINTER} oder {@link #FOX}. */
	public final String designName;
	/**
	 * Polarlicht? Aufbau wie Cubiq, aber in den Farben von {@code app/assets/css/winter.css}: Nachtblau, Eis-Akzent ab Werk,
	 * dazu der Schneesaum und der Hauch Schnee auf dem Zeichen ({@link dev.pwiepixel.cubiqclient.core.ui.winter.Frost}).
	 */
	public final boolean winter;
	/** Fuchsbau? (Warme Abenddämmerung: Pflaume und Kastanie, Laternen mit Glut, glimmende Äste als Linien.) */
	public final boolean fox;
	/**
	 * Halloween 2026 „Mondnacht“ (Event aktiv, siehe {@code core.halloween.Halloween}): Nacht, Pflaume, Violett und
	 * Mondsilber – liegt über allen Designs, immer dunkel.
	 */
	public final boolean halloween;
	/** Thema, aus dem die Halloween-Variante entstand (sonst null). */
	private final Theme base;

	/** Akzentfarbe (Knöpfe, aktive Elemente). */
	public final int accent;
	/** Hellere Akzentvariante (Hover). */
	public final int accentHover;
	/** Akzent für Schrift, Symbole, Schreibmarken und feine Linien – gut lesbar auf den Flächen des Themas. */
	public final int accentText;
	/** Hintergrund des Menüs (Fensterfläche). */
	public final int background;
	/** Flächen/Kacheln. */
	public final int surface;
	public final int surfaceHover;
	/** Flächen einer Ebene höher (Kopfzeile, Knöpfe, Tastenkappen). */
	public final int surfaceHigh;
	public final int border;
	public final int text;
	public final int textDim;
	/** Hinweise, Warnungen und „ausstehend“ (warmes Bernstein). */
	public final int caution;
	/** Fehler, Löschen, Sperren (rot, unabhängig vom Akzent). */
	public final int danger;
	/** Abdunkelung hinter dem Menü. */
	public final int scrim;
	/** Heller Grundton? (Licht-Thema – Texte sind dann dunkel.) */
	public final boolean light;

	/** Tiefste Fläche (Mulden: Eingabefelder, Listen, Symbol-Nischen). */
	public final int deep;
	/** Hellere und dunklere Kante (Ränder kleiner Elemente, Vorschaubilder). */
	public final int bevelLight;
	public final int bevelDark;
	/** Linie ohne Hervorhebung: Trennlinien, Schiene von Schalter und Schieberegler. */
	public final int track;
	/** Schein um aktive Elemente (deckend – Deckkraft setzt der Aufrufer). */
	public final int glow;
	/**
	 * Fläche, die mit der Auswahl aufleuchtet ({@link Paint#accentFace}): aus (Fläche/Kante), an (Fläche/hellere
	 * Fläche/Kante), Schein. In Cubiq und Polarlicht wird sie zur Akzentfarbe, im Fuchsbau ist sie eine Laterne.
	 */
	public final int faceOff;
	public final int faceOffEdge;
	public final int faceOn;
	public final int faceHot;
	public final int faceOnEdge;
	public final int faceGlow;
	/** Schrift auf dieser Fläche: aus (hell) und an (lesbar auf der leuchtenden Fläche). */
	public final int faceText;
	public final int faceTextOn;

	private Theme(String themeName, String accentName, int accent, String design) {
		this.halloween = false;
		this.base = null;
		this.themeName = themeName;
		this.accentName = accentName;
		this.designName = normalizeDesign(design);
		this.winter = WINTER.equals(designName);
		this.fox = FOX.equals(designName);
		this.accent = accent;
		this.light = "light".equals(themeName);
		this.caution = light ? CAUTION_LIGHT : CAUTION_DARK;
		this.danger = light ? DANGER_LIGHT : DANGER_DARK;
		boolean oled = "oled".equals(themeName);
		if (winter) {
			// Polarlicht: wie Cubiq aufgebaut, die Grundskala in kaltem Nachtblau bzw. als heller Morgen (winter.css: base-900
			// Fenster, base-850 Kacheln, base-800 Knöpfe, base-700 Kanten, base-50 Schrift, base-400 gedämpft, Lichtkante
			// knapp über line-strong).
			int[] scale = scale(accent);
			this.accentHover = scale[1];
			this.accentText = light ? ColorMath.lerp(scale[3], 0xFF000000, 0.18f) : scale[0];
			this.glow = accent;
			this.faceOn = accent;
			this.faceHot = scale[1];
			this.faceOnEdge = scale[3];
			this.faceGlow = accent;
			this.faceTextOn = onAccent(accent);
			if (light) {
				background = 0xFFFFFFFF;
				surface = 0xFFF4F8FC;
				surfaceHover = 0xFFE3ECF5;
				surfaceHigh = 0xFFEAF2F9;
				border = 0xFFCFDBE8;
				text = 0xFF0C1422;
				textDim = 0xFF40536C;
				scrim = 0x500C1422;
				deep = 0xFFEAF2F9;
				bevelLight = 0xFFFFFFFF;
				bevelDark = 0xFFA6B8CC;
			} else {
				background = oled ? 0xFF04070E : 0xFF0A1222;
				surface = oled ? 0xFF080D18 : 0xFF0E182C;
				surfaceHover = oled ? 0xFF131C2F : 0xFF192841;
				surfaceHigh = oled ? 0xFF0E1524 : 0xFF142138;
				border = oled ? 0xFF18233A : 0xFF1E2E4A;
				text = 0xFFEEF5FC;
				textDim = 0xFFA2B3CD;
				scrim = oled ? 0xB0000000 : 0xA8070C18;
				deep = oled ? 0xFF000000 : 0xFF070C18;
				bevelLight = oled ? 0xFF263652 : 0xFF2E4568;
				bevelDark = oled ? 0xFF000000 : 0xFF070C18;
			}
			this.track = border;
			this.faceOff = surfaceHigh;
			this.faceOffEdge = border;
			this.faceText = text;
			return;
		}
		if (fox) {
			// Fuchsbau: warme Dämmerung. Die Laternen haben warmes Glutlicht – golden unabhängig vom Akzent; glimmende Äste
			// in Akzentfarbe als Linien.
			this.accentHover = ColorMath.lerp(accent, 0xFFFFFFFF, 0.22f);
			this.track = ColorMath.lerp(ColorMath.scaleRgb(accent, 0.36f), oled ? 0xFF0E080D : 0xFF241620, 0.32f);
			this.accentText = ColorMath.lerp(accent, 0xFFFFE1A6, 0.22f);
			this.glow = accent;
			this.faceOff = 0xFF3A2428;
			this.faceOffEdge = 0xFF1E1416;
			this.faceOn = 0xFFFFC362;
			this.faceHot = 0xFFFFF6D8;
			this.faceOnEdge = 0xFF9A4A1C;
			this.faceGlow = 0xFFFFB050;
			this.faceText = 0xFFF3E2D8;
			this.faceTextOn = 0xFF2E1404;
			if (light) {
				// Goldene Stunde.
				background = 0xFFF6EBE0;
				surface = 0xFFFFFAF5;
				surfaceHover = 0xFFF0E0D0;
				surfaceHigh = 0xFFFBF2E8;
				border = 0xFFE3CDB9;
				text = 0xFF1E130F;
				textDim = 0xFF7D5E50;
				scrim = 0x501E130F;
				deep = 0xFFEAD6C2;
				bevelLight = 0xFFFFFFFF;
				bevelDark = 0xFFC7A993;
			} else {
				background = oled ? 0xFF000000 : 0xFF1A1119;
				surface = oled ? 0xFF0E080D : 0xFF281A27;
				surfaceHover = oled ? 0xFF181017 : 0xFF332232;
				surfaceHigh = oled ? 0xFF120B11 : 0xFF2D1E2C;
				border = oled ? 0xFF261A24 : 0xFF462F44;
				text = 0xFFFFF3EA;
				textDim = oled ? 0xFFA48C9A : 0xFFB398A8;
				scrim = oled ? 0xB0000000 : 0xA80E080D;
				deep = oled ? 0xFF040204 : 0xFF100A10;
				bevelLight = oled ? 0xFF33232F : 0xFF563A53;
				bevelDark = oled ? 0xFF000000 : 0xFF0C070B;
			}
			return;
		}
		// Cubiq (wie der Launcher): ruhige, dunkle Flächen mit einem Hauch Grün in klaren Höhenstufen, die Akzentfarbe für Aktionen.
		int[] scale = scale(accent);
		this.accentHover = scale[1];
		this.accentText = light ? ColorMath.lerp(scale[3], 0xFF000000, 0.18f) : scale[0];
		this.glow = accent;
		this.faceOn = accent;
		this.faceHot = scale[1];
		this.faceOnEdge = scale[3];
		this.faceGlow = accent;
		this.faceTextOn = onAccent(accent);
		if (light) {
			// Launcher „Hell“: base-900 als Fensterfläche, base-850/800/700 darüber, Schrift fast schwarz mit einem Hauch Grün.
			background = 0xFFFFFFFF;
			surface = 0xFFF5F8F6;
			surfaceHover = 0xFFE5EBE6;
			surfaceHigh = 0xFFEEF2EF;
			border = 0xFFD2DBD4;
			text = 0xFF0F1411;
			textDim = 0xFF5D6B61;
			scrim = 0x500F1411;
			deep = 0xFFEEF2EF;
			bevelLight = 0xFFFFFFFF;
			bevelDark = 0xFFA8B5AB;
		} else {
			// Launcher „Dunkel“/„OLED“: base-900 Fenster, base-850 Kacheln, base-800 Knöpfe, base-700 Kanten.
			background = oled ? 0xFF050706 : 0xFF111613;
			surface = oled ? 0xFF0A0D0B : 0xFF161C18;
			surfaceHover = oled ? 0xFF181F1A : 0xFF242D27;
			surfaceHigh = oled ? 0xFF121714 : 0xFF1D2520;
			border = oled ? 0xFF1F2721 : 0xFF29332C;
			text = 0xFFEEF4EF;
			textDim = 0xFF869489;
			scrim = oled ? 0xB0000000 : 0xA80C100E;
			deep = oled ? 0xFF000000 : 0xFF0C100E;
			bevelLight = oled ? 0xFF354038 : 0xFF3D4A41;
			bevelDark = oled ? 0xFF000000 : 0xFF0C100E;
		}
		this.track = border;
		this.faceOff = surfaceHigh;
		this.faceOffEdge = border;
		this.faceText = text;
	}

	/**
	 * Halloween-Variante von {@code base} (Vertrag „Look“): Nacht #07060c, Nebelgrund #13101f, Pflaume #3a1f5c,
	 * Violett #8e6cc9/#b9a2ea, Mondsilber #cfd5e3/#f3f5fb. Leuchtende Flächen sind mondbeschienenes Pflaumenglas mit
	 * Silberkante, Linien sind ein dünner violett-silberner Faden. Kein Orange als Fläche.
	 */
	private Theme(Theme base) {
		this.base = base;
		this.halloween = true;
		this.themeName = base.themeName;
		this.accentName = base.accentName;
		this.designName = base.designName;
		this.winter = false;
		this.fox = false;
		this.light = false;
		this.accent = 0xFF8E6CC9;
		this.accentHover = 0xFFB9A2EA;
		this.track = 0xFF2E2648;
		this.accentText = 0xFFCFD5E3;
		this.glow = 0xFFB9A2EA;
		this.faceOff = 0xFF141020;
		this.faceOffEdge = 0xFF5B5280;
		this.faceOn = 0xFF4A2C78;
		this.faceHot = 0xFFCFD5E3;
		this.faceOnEdge = 0xFFF3F5FB;
		this.faceGlow = 0xFFB9A2EA;
		this.faceText = 0xFFE9E6F5;
		this.faceTextOn = 0xFFF3F5FB;
		boolean oled = "oled".equals(base.themeName);
		this.background = oled ? 0xFF000000 : 0xFF0A0812;
		this.surface = oled ? 0xFF0A0812 : 0xFF13101F;
		this.surfaceHover = oled ? 0xFF14101F : 0xFF1C1730;
		this.surfaceHigh = oled ? 0xFF100C19 : 0xFF181427;
		this.border = 0xFF3B3358;
		this.text = 0xFFEDEAF7;
		this.textDim = 0xFF9A94B4;
		this.caution = CAUTION_DARK;
		this.danger = DANGER_DARK;
		this.scrim = 0xB007060C;
		this.deep = oled ? 0xFF000000 : 0xFF07060C;
		this.bevelLight = 0xFF4A4068;
		this.bevelDark = 0xFF040308;
	}

	/** Skala {300, 400, 500, 600} zur Akzentfarbe – die des Launchers oder, bei eigenen Farben, abgeleitet. */
	static int[] scale(int accent) {
		for (int[] s : SCALES) {
			if (s[2] == accent) return s;
		}
		return new int[]{ColorMath.lerp(accent, 0xFFFFFFFF, 0.42f), ColorMath.lerp(accent, 0xFFFFFFFF, 0.2f), accent,
				ColorMath.scaleRgb(accent, 0.8f)};
	}

	/**
	 * Schrift auf der Akzentfarbe wie im Launcher: dunkel auf hellen Akzenten (Cubiq-Grün, Bernstein, Smaragd, Eis,
	 * Orange), weiß auf dunklen (Lapis, Amethyst) – entschieden nach dem besseren Kontrast.
	 */
	static int onAccent(int accent) {
		double l = luminance(accent);
		double dark = (l + 0.05) / (luminance(ON_LIGHT_ACCENT) + 0.05);
		double white = 1.05 / (l + 0.05);
		return dark >= white ? ON_LIGHT_ACCENT : 0xFFFFFFFF;
	}

	/** Relative Leuchtdichte (WCAG) einer Farbe. */
	private static double luminance(int argb) {
		return 0.2126 * channel((argb >> 16) & 0xFF) + 0.7152 * channel((argb >> 8) & 0xFF) + 0.0722 * channel(argb & 0xFF);
	}

	private static double channel(int v) {
		double c = v / 255.0;
		return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	/** Aktuelles Thema (nie null) – während Halloween (Look an) dessen Halloween-Variante. */
	public static Theme get() {
		Theme c = current;
		if (!dev.pwiepixel.cubiqclient.core.halloween.Halloween.lookActive()) return c;
		Theme h = spooky;
		if (h == null || h.base != c) {
			h = new Theme(c);
			spooky = h;
		}
		return h;
	}

	/** Das eingestellte Thema ohne Halloween-Variante. */
	public static Theme plain() {
		return current;
	}

	/** Setzt das Thema direkt (Tests, Fallback). */
	public static void set(Theme theme) {
		if (theme != null) current = theme;
	}

	/** Thema aus Namen bauen (Design Cubiq); unbekannte Namen → Standard. */
	public static Theme of(String themeName, String accentName, String accentHex) {
		return of(themeName, accentName, accentHex, CUBIQ);
	}

	/** Thema aus Namen bauen; unbekannte Namen → Standard, unbekanntes Design → Cubiq. */
	public static Theme of(String themeName, String accentName, String accentHex, String design) {
		String t = normalize(themeName);
		String a = canonicalAccent(normalize(accentName));
		int accent = accentColor(a, accentHex);
		if (!"dark".equals(t) && !"oled".equals(t) && !"light".equals(t)) t = "dark";
		return new Theme(t, a.isEmpty() ? "cubiq" : a, accent, normalizeDesign(design));
	}

	/** "winter" → Polarlicht, "fox" → Fuchsbau, alles andere (fehlt, ältere Werte, Tippfehler) → Cubiq. */
	public static String normalizeDesign(String design) {
		String d = normalize(design);
		if (WINTER.equals(d)) return WINTER;
		if (FOX.equals(d)) return FOX;
		return CUBIQ;
	}

	/** Akzent, den jedes Design ab Werk mitbringt. */
	public static String designAccent(String design) {
		String d = normalizeDesign(design);
		if (WINTER.equals(d)) return "ice";
		if (FOX.equals(d)) return "orange";
		return "cubiq";
	}

	/**
	 * Akzent beim Wechsel des Designs: ist noch der Akzent eines Designs gewählt (Cubiq-Grün, Eisblau, Fuchsorange), kommt
	 * der des neuen Designs – Polarlicht wird Eisblau, Fuchsbau Orange, zurück zu Cubiq wieder Cubiq-Grün. Jede andere
	 * (selbst gewählte) Farbe bleibt.
	 */
	public static String accentForDesign(String design, String accent) {
		String a = canonicalAccent(accent);
		if ("cubiq".equals(a) || "ice".equals(a) || "orange".equals(a)) return designAccent(design);
		return a;
	}

	/**
	 * Namen des Launchers für frühere Akzent-Namen: „redstone“ → Cubiq-Grün (der frühere Standard), „lamp“ →
	 * Bernstein, „fox“ → Orange. Der Launcher kennt nur cubiq, amber, emerald, lapis, amethyst, ice und orange.
	 */
	public static String canonicalAccent(String accent) {
		if ("redstone".equals(accent)) return "cubiq";
		if ("lamp".equals(accent)) return "amber";
		if ("fox".equals(accent)) return "orange";
		return accent;
	}

	/**
	 * Lädt {@code <configDir>/cubiqclient/launcher-theme.json} und setzt das Thema.
	 * Fehler werden verschluckt – dann bleibt das Standard-Thema.
	 * @return true, wenn eine gültige Datei gelesen wurde
	 */
	public static boolean loadFrom(Path configDir) {
		if (configDir == null) return false;
		Path file = configDir.resolve("cubiqclient").resolve("launcher-theme.json");
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return false;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				File parsed = new Gson().fromJson(reader, File.class);
				if (parsed == null) return false;
				set(of(parsed.theme, parsed.accent, parsed.accentColor, parsed.design));
				motionFromLauncher(parsed.motion);
				dev.pwiepixel.cubiqclient.core.halloween.Halloween.fromLauncher(parsed.event);
				dev.pwiepixel.cubiqclient.core.halloween.Halloween.flickerFromLauncher(parsed.halloweenFlicker);
				dev.pwiepixel.cubiqclient.core.halloween.Halloween.lookFromLauncher(parsed.eventLook);
				return true;
			}
		} catch (IOException | RuntimeException e) {
			// JsonParseException, IllegalStateException, ClassCastException … – das Thema bleibt Standard.
			return false;
		}
	}

	/**
	 * Feld {@code motion} aus {@code launcher-theme.json}: {@value #MOTION_REDUCED} → reduzierte Bewegung, alles andere
	 * (fehlt, ältere Launcher, unbekannt) → volle Bewegung.
	 */
	public static void motionFromLauncher(String motion) {
		launcherReducedMotion = MOTION_REDUCED.equals(normalize(motion));
	}

	/** Will der Launcher reduzierte Bewegung? (Ohne Launcher-Datei: nein.) */
	public static boolean launcherReducedMotion() {
		return launcherReducedMotion;
	}

	/** Akzentfarbe aus dem Namen des Launchers (auch frühere Namen), sonst aus dem Hex-Wert, sonst Cubiq-Grün. */
	static int accentColor(String name, String hex) {
		String n = canonicalAccent(name);
		if ("cubiq".equals(n)) return 0xFF71A330;
		if ("amber".equals(n)) return 0xFFE0900C;
		if ("emerald".equals(n)) return 0xFF17A34A;
		if ("lapis".equals(n)) return 0xFF3563E9;
		if ("amethyst".equals(n)) return 0xFF9B4DDF;
		if ("ice".equals(n)) return 0xFF2B97D6;
		if ("orange".equals(n)) return 0xFFE8702A;
		int parsed = parseHex(hex);
		return parsed != 0 ? parsed : 0xFF71A330;
	}

	/** "#RRGGBB" → deckendes ARGB; ungültig → 0. */
	static int parseHex(String hex) {
		if (hex == null) return 0;
		String t = hex.trim();
		if (t.startsWith("#")) t = t.substring(1);
		if (t.length() != 6) return 0;
		for (int i = 0; i < 6; i++) {
			if (Character.digit(t.charAt(i), 16) < 0) return 0;
		}
		return 0xFF000000 | Integer.parseInt(t, 16);
	}

	private static String normalize(String s) {
		if (s == null) return "";
		String t = s.trim().toLowerCase(Locale.ROOT);
		if (t.length() > 24) return "";
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if (!(c >= 'a' && c <= 'z')) return "";
		}
		return t;
	}
}
