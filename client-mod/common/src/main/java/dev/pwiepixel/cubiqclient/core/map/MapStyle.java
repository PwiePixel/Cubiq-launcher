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
}
