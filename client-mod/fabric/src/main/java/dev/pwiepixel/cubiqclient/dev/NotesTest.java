package dev.pwiepixel.cubiqclient.dev;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.HudModule;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.notes.Notes;
import dev.pwiepixel.cubiqclient.core.notes.NotesSelfTest;
import dev.pwiepixel.cubiqclient.core.ui.menu.ModMenu;
import dev.pwiepixel.cubiqclient.screen.CubiqMenuScreen;
import dev.pwiepixel.cubiqclient.screen.CubiqUiScreen;
import net.minecraft.client.Minecraft;

/**
 * Selbsttest „Notizen je Welt“ ({@code -PcubiqAutotestOnly=notes}): Beispiel-Notizen in der Testwelt, angeheftete Notiz
 * im HUD, Liste/Lesen/Bearbeiten/Suche/andere Welten im Cubiq-Menü, Koordinaten-Menü, vorübergehender Wegpunkt und
 * Weltkarte. Screenshots cubiqclient-&lt;mc&gt;-notes-*.png, Zusammenfassung im Log. Ohne Netz (Sync nur in Unit-Tests).
 */
public final class NotesTest {
	private int phase;
	private int wait;
	private final NotesSelfTest self = new NotesSelfTest();

	/** Ein Tick; true = noch nicht fertig. */
	public boolean step(Minecraft mc, CubiqModules modules, CapeTest.Actions actions) {
		if (wait > 0) {
			wait--;
			return true;
		}
		switch (phase++) {
			case 0: {
				if (mc.player == null) return false;
				actions.command("gamerule sendCommandFeedback false");
				actions.command("time set day");
				actions.command("weather clear");
				for (Module m : modules.registry.all()) {
					if (m instanceof HudModule) m.setEnabled(false);
				}
				modules.notes.notes.setEnabled(true);
				modules.waypoints.setEnabled(true);
				modules.worldMap.setEnabled(true);
				boolean ok = self.seed();
				CubiqClient.LOGGER.info("[Autotest] Notizen angelegt: {} – {}", ok, self.summary());
				if (!ok) return false;
				Mc.setScreen(null);
				wait = 30;
				return true;
			}
			case 1:
				actions.shot("cubiqclient-notes-hud");
				Notes.requestOpen();
				Mc.setScreen(new CubiqMenuScreen(null));
				wait = 20;
				return true;
			case 2:
				CubiqClient.LOGGER.info("[Autotest] Notiz-Seite: {}", menu() == null ? "FEHLT" : menu().notesPage().viewName());
				actions.shot("cubiqclient-notes-list");
				if (menu() != null) menu().notesPage().showNote(self.world().key(), self.main().id, false);
				wait = 10;
				return true;
			case 3:
				actions.shot("cubiqclient-notes-view");
				if (menu() != null) menu().notesPage().testPopup(self.target()[0], self.target()[1], self.target()[2], 200, 120);
				wait = 6;
				return true;
			case 4:
				actions.shot("cubiqclient-notes-coords");
				if (menu() != null) {
					menu().notesPage().back();
					menu().notesPage().showNote(self.world().key(), self.main().id, true);
				}
				wait = 10;
				return true;
			case 5:
				actions.shot("cubiqclient-notes-edit");
				if (menu() != null) menu().notesPage().testSearch("redstone");
				wait = 6;
				return true;
			case 6:
				actions.shot("cubiqclient-notes-search");
				if (menu() != null) menu().notesPage().testWorlds();
				wait = 6;
				return true;
			case 7: {
				actions.shot("cubiqclient-notes-worlds");
				int[] t = self.target();
				Notes.Outcome o = Notes.get().temporaryWaypoint(self.world(), self.main().displayTitle(), t[0], t[1], t[2]);
				CubiqClient.LOGGER.info("[Autotest] Vorübergehender Wegpunkt: {} {}", o.ok, o.message);
				Mc.setScreen(null);
				wait = 20;
				return true;
			}
			case 8: {
				actions.shot("cubiqclient-notes-waypoint");
				int[] t = self.target();
				Notes.Outcome o = Notes.get().showOnMap(self.world(), self.main().displayTitle(), t[0], t[2]);
				CubiqClient.LOGGER.info("[Autotest] Auf Weltkarte: {} {}", o.ok, o.message);
				wait = 30;
				return true;
			}
			case 9:
				actions.shot("cubiqclient-notes-map");
				Mc.setScreen(null);
				CubiqClient.LOGGER.info("[Autotest] Notizen: {}", self.summary());
				actions.command("tp @p " + self.target()[0] + " " + self.target()[1] + " " + self.target()[2]);
				wait = 20;
				return true;
			case 10:
				CubiqClient.LOGGER.info("[Autotest] Wegpunkt nach Erreichen: {}", Notes.get().temporary() == null ? "entfernt" : "NOCH DA");
				self.cleanup();
				CubiqClient.LOGGER.info("[Autotest] Notizen: fertig");
				return false;
			default:
				return false;
		}
	}

	private static ModMenu menu() {
		Object s = Mc.screen();
		if (!(s instanceof CubiqUiScreen)) return null;
		Object ui = ((CubiqUiScreen) s).ui();
		return ui instanceof ModMenu ? (ModMenu) ui : null;
	}
}
