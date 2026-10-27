package dev.pwiepixel.cubiqclient.core.module;

/** Reiter im Cubiq-Menü. Jedes Modul gehört zu genau einer Kategorie. */
public enum Category {
	HUD("HUD", "hud"),
	PVP("PvP", "sword"),
	CHAT("Chat", "chat"),
	WORLD("World", "globe"),
	REDSTONE("Redstone", "torch"),
	PERFORMANCE("Performance", "bolt"),
	MISC("Other", "gear");

	private final String label;
	private final String icon;
	private final String key;

	Category(String label, String icon) {
		this.label = label;
		this.icon = icon;
		this.key = "category." + name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Anzeigename des Reiters in der aktiven Sprache ("category.<name>"). */
	public String label() {
		return dev.pwiepixel.cubiqclient.core.i18n.I18n.trOr(key, label);
	}

	/** Übersetzungsschlüssel des Reiters. */
	public String key() {
		return key;
	}

	/** Standard-Symbol für Module ohne eigenes Symbol (siehe {@code core.ui.Icons}). */
	public String icon() {
		return icon;
	}
}
