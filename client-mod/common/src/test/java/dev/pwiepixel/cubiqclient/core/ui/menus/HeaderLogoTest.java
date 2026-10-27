package dev.pwiepixel.cubiqclient.core.ui.menus;

import dev.pwiepixel.cubiqclient.core.ui.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cubiq-Zeichen in der Kopfleiste der Vanilla-Menüs: nur, wenn dort kein Bedienelement liegt. */
class HeaderLogoTest {
	@AfterEach
	void reset() {
		Theme.set(Theme.of("dark", "cubiq", null));
	}

	@Test
	void freeHeaderShowsTheLogo() {
		assertTrue(MenuSkin.headerLogoFits(new ArrayList<int[]>(), 30));
		// Knöpfe rechts oben (Ping-Test, Anheften) und alles unter der Kopfleiste stören nicht.
		assertTrue(MenuSkin.headerLogoFits(Arrays.asList(new int[]{300, 4, 80, 20}, new int[]{10, 40, 100, 20}), 30));
	}

	@Test
	void tabsOrButtonsTopLeftHideTheLogo() {
		// „Neue Welt“ bei 427 GUI-Pixeln Breite: die Reiter beginnen bei x = 27 (wie im Autotest-Bild).
		assertFalse(MenuSkin.headerLogoFits(Collections.singletonList(new int[]{27, 0, 124, 24}), 24));
		// „Mit Code beitreten“ links oben in der Serverliste.
		assertFalse(MenuSkin.headerLogoFits(Collections.singletonList(new int[]{6, 6, 100, 20}), 32));
		// Ebenso in Winternacht (Schriftzug ohne Zeichen).
		Theme.set(Theme.of("dark", "ice", null, Theme.WINTER));
		assertFalse(MenuSkin.headerLogoFits(Collections.singletonList(new int[]{27, 0, 124, 24}), 24));
	}

	@Test
	void emptyOrBrokenRectsAreIgnored() {
		assertTrue(MenuSkin.headerLogoFits(Arrays.asList(null, new int[]{1, 2}, new int[]{10, 5, 0, 20}), 30));
	}
}
