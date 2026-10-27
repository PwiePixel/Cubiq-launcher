package dev.pwiepixel.cubiqclient.qol;

/** Vom Getrennt-Bildschirm umgesetzt ({@code DisconnectScreenMixin}): Auto-Reconnect-Knopf beschriften. */
public interface QolReconnectButton {
	/**
	 * @param label  Beschriftung oder null = Knopf ausblenden
	 * @param active anklickbar (nur während des Countdowns)
	 */
	void cubiqclient$update(String label, boolean active);
}
