package dev.pwiepixel.cubiqclient.core.map;

import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Farben der Karten im Cubiq-Stil (Minimap und Weltkarte): Leisten, Kästen und Himmelsrichtungen kommen aus dem Thema
 * – ruhige Flächen mit feiner Kante, in Dunkel, OLED und Hell gleich gut lesbar. Der Kartengrund selbst (unerkundet,
 * „wird geladen“, Platten hinter Gesichtern) bleibt in jedem Thema dunkel, damit die Blockfarben leuchten.
 */
final class MapStyle {
	/** Unerkundeter Kartengrund (Cubiq base-950). */
	static final int VOID = 0xFF0C100E;
	/** Platzhalter „wird geladen“: etwas heller als der Kartengrund. */
	static final int LOADING = 0xFF1B231E;
	/** Dunkle Platte hinter Gesichtern und Kreatur-Köpfen. */
	static final int PLATE = 0xE00C100E;

	private MapStyle() {
	}

	/** Leisten oben/unten der Weltkarte: Fensterfläche, die Karte schimmert kaum durch. */
	static int bar(Theme t) {
		return ColorMath.withAlpha(t.background, 0xEE);
	}

	/** Kleine Kästen über der Karte (Hinweise, Tooltips, Maßstab, Messwert). */
	static int panel(Theme t) {
		return ColorMath.withAlpha(t.surface, 0xF2);
	}

	/**
	 * Akzent für Zeichen auf der (immer dunklen, farbigen) Karte – eigener Pfeil, Blickkegel: im hellen Thema die hellere
	 * Stufe, denn {@link Theme#accentText} ist dort für helle Flächen dunkel.
	 */
	static int marker(Theme t) {
		return t.light ? t.accentHover : t.accentText;
	}

	/** Himmelsrichtung auf der Minimap: Fläche, Kante, Schrift – Norden in der Akzentfarbe hervorgehoben. */
	static int badgeFill(Theme t, boolean north) {
		return north ? t.accent : ColorMath.withAlpha(t.surfaceHigh, 0xF2);
	}

	static int badgeEdge(Theme t, boolean north) {
		return north ? t.faceOnEdge : t.border;
	}

	static int badgeText(Theme t, boolean north) {
		return north ? t.faceTextOn : t.text;
	}

	// --- Klassisches Aussehen (wie die bekannten Minimap-Mods; in jedem Thema gleich) ---

	/** Rahmen: dunkle Außenlinie, grauer Rand mit Licht von links oben, dunkle Linie zur Karte. */
	static final int CLASSIC_OUTLINE = 0xFF0E0E0E;
	static final int CLASSIC_RIM_LIGHT = 0xFFB9B9B9;
	static final int CLASSIC_RIM_DARK = 0xFF555555;
	static final int CLASSIC_INNER = 0xFF1E1E1E;
	/** Eigener Pfeil: kräftiges Rot. */
	static final int CLASSIC_ARROW = 0xFFE3342B;
	/** Norden in Rot, die anderen Richtungen weiß – auf einer dunklen, halb durchsichtigen Platte. */
	static final int CLASSIC_NORTH = 0xFFFF5B4F;
	static final int CLASSIC_PLATE = 0xA8000000;
	/** Blickkegel: weiß, halb durchsichtig (der Verlauf steckt im Symbol). */
	static final int CLASSIC_CONE = 0x66FFFFFF;
	/** Schatten/Umriss der Wegpunkt-Kästchen. */
	static final int CLASSIC_BOX_EDGE = 0xC0000000;
}
