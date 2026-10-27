package dev.pwiepixel.cubiqclient.core.ui.winter;

import static dev.pwiepixel.cubiqclient.core.ui.winter.WinterMath.rgb;

/**
 * Farben der Szene „Polarlicht“ je Farbschema – dieselben Werte wie {@code app/utils/winter/palette.ts}: Nacht (Dunkel),
 * tiefe Nacht (OLED) und heller Morgen (Hell). Das Polarlicht trägt immer das Cubiq-Grün aus dem Logo und läuft über
 * Türkis in Eis-Cyan aus, unabhängig von der Akzentfarbe. Alle Farben deckend ARGB.
 */
public final class WinterPalette {
	public static final int DARK = 0;
	public static final int OLED = 1;
	public static final int LIGHT = 2;

	/** Polarlicht: heller Saum, Cubiq-Grün (#71a330), Türkis, Eis-Cyan. */
	public static final int[] AURORA = {rgb("#d6f5a3"), rgb("#71a330"), rgb("#2aa894"), rgb("#78d6f2")};

	public final int variant;
	/** Himmel von oben nach unten (Stufen, dazwischen gerastert). */
	public final int[] sky;
	/** Sterne: schwach, mittel, hell – am Morgen keine (null). */
	public final int[] stars;
	public final int[] aurora = AURORA;
	/** Höchste Deckkraft des Polarlichts (0…255). */
	public final int auroraAlpha;
	/** Nachts leuchtet das Polarlicht (hellt auf), am Morgen färbt es den Himmel nur zart. */
	public final boolean auroraGlows;
	public final int snowLit;
	public final int snowTop;
	public final int snowLeft;
	public final int snowRight;
	public final int rockLeft;
	public final int rockRight;
	public final int rockDeep;
	public final int pineLeft;
	public final int pineRight;
	public final int trunk;
	public final int ice;
	public final int iceTip;
	public final int cloudFarRim;
	public final int cloudFar;
	public final int cloudNearRim;
	public final int cloudNear;
	public final int flake;
	public final int flakeFar;
	/** Dunst, in dem ferne Inseln verblassen. */
	public final int haze;

	/** @param variant {@link #DARK}, {@link #OLED} oder {@link #LIGHT} */
	public WinterPalette(int variant) {
		this.variant = variant;
		this.auroraGlows = variant != LIGHT;
		String[] t;
		if (variant == LIGHT) {
			sky = colors("#8ebde2", "#a2c9e8", "#b7d6ee", "#cbe2f3", "#deedf8", "#eef6fc");
			stars = null;
			auroraAlpha = 96;
			t = new String[]{"#ffffff", "#f7fbff", "#dce8f4", "#c0d3e8", "#7489a8", "#5a7092", "#465b7e", "#3f8a68", "#2b684f", "#6e5246",
					"#e3f6ff", "#a3cbe9", "#ffffff", "#e4eef8", "#ffffff", "#f1f6fb", "#ffffff", "#e1ecf6", "#d5e7f5"};
		} else if (variant == OLED) {
			sky = colors("#000000", "#010309", "#030813", "#070f20", "#0c1830", "#132240");
			stars = colors("#33425c", "#7d90b0", "#f2f8ff");
			auroraAlpha = 255;
			t = new String[]{"#f1f7ff", "#cbdaee", "#9db2cf", "#7189ab", "#1b2a46", "#111c33", "#0a1222", "#26604d", "#173f34", "#2c2124",
					"#9fd6f5", "#4f7fae", "#33547f", "#0d1b34", "#5f84b6", "#14284a", "#eef6ff", "#566c90", "#0a1426"};
		} else {
			sky = colors("#03060e", "#060c1a", "#0a1428", "#0f1d36", "#152846", "#1c3352");
			stars = colors("#3b4c6b", "#8a9dbd", "#f2f8ff");
			auroraAlpha = 242;
			t = new String[]{"#ffffff", "#e4eefa", "#b9cce4", "#8ba2c2", "#2b3f62", "#1c2b48", "#111b30", "#2f7358", "#1d4f3f", "#3d2d30",
					"#b4e5ff", "#6898c9", "#4a6c9c", "#1b2f50", "#86a9d8", "#2a426b", "#f4f9ff", "#7a90b4", "#13223d"};
		}
		snowLit = rgb(t[0]);
		snowTop = rgb(t[1]);
		snowLeft = rgb(t[2]);
		snowRight = rgb(t[3]);
		rockLeft = rgb(t[4]);
		rockRight = rgb(t[5]);
		rockDeep = rgb(t[6]);
		pineLeft = rgb(t[7]);
		pineRight = rgb(t[8]);
		trunk = rgb(t[9]);
		ice = rgb(t[10]);
		iceTip = rgb(t[11]);
		cloudFarRim = rgb(t[12]);
		cloudFar = rgb(t[13]);
		cloudNearRim = rgb(t[14]);
		cloudNear = rgb(t[15]);
		flake = rgb(t[16]);
		flakeFar = rgb(t[17]);
		haze = rgb(t[18]);
	}

	private static int[] colors(String... values) {
		int[] out = new int[values.length];
		for (int i = 0; i < values.length; i++) out[i] = rgb(values[i]);
		return out;
	}

	/** Variante aus dem Namen des Farbschemas ("dark", "oled", "light"). */
	public static int variantOf(String themeName) {
		if ("light".equals(themeName)) return LIGHT;
		if ("oled".equals(themeName)) return OLED;
		return DARK;
	}

	/** Palette zum Farbschema. */
	public static WinterPalette of(String themeName) {
		return new WinterPalette(variantOf(themeName));
	}
}
