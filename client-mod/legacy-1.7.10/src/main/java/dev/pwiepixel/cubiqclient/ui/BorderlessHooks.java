package dev.pwiepixel.cubiqclient.ui;

import dev.pwiepixel.cubiqclient.CubiqClient;

/** Ob randloses Vollbild gerade gewollt ist. Vor dem Start des Clients gilt der Standard (an). */
public final class BorderlessHooks {
	private BorderlessHooks() {
	}

	public static boolean want() {
		try {
			CubiqClient client = CubiqClient.get();
			if (client == null || client.modules() == null) return true;
			return client.modules().borderlessFullscreen.isEnabled();
		} catch (Throwable ignored) {
			return true;
		}
	}
}
