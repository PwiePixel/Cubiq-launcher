package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleHost;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleUi;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.OptionsScreen;

/**
 * Cubiq-Startbildschirm (ersetzt den Vanilla-Titelbildschirm, abschaltbar im Modul "Startbildschirm").
 * Aussehen und Bedienung stehen versionsunabhängig in {@link TitleUi}; hier nur die Ziele der Knöpfe.
 */
public final class CubiqTitleScreen extends CubiqUiScreen {
	/** Forges Mod-Liste (Paket je nach Version verschieden) – per Reflection, erster Treffer gewinnt. */
	private static final String[] MOD_LIST_SCREENS = {
			"net.minecraftforge.client.gui.ModListScreen",
			"net.minecraftforge.fml.client.gui.screen.ModListScreen",
			"net.minecraftforge.fml.client.gui.GuiModList",
	};

	private final TitleUi title;

	public CubiqTitleScreen() {
		this(new Host());
	}

	private CubiqTitleScreen(Host host) {
		super("Cubiq Client", new TitleUi(host));
		host.screen = this;
		this.title = (TitleUi) ui();
	}

	/** Die Oberfläche (Selbsttest: Auswahl, Messung). */
	public TitleUi title() {
		return title;
	}

	@Override
	protected boolean customBackground() {
		return true;
	}

	@Override
	protected void drawBackground(Gfx g, float partialTick) {
		// Den Hintergrund zeichnet TitleUi (Würfel-Szene).
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** Ziele der Knöpfe. */
	private static final class Host implements TitleHost {
		private final String versionLine = "Minecraft " + Mc.modVersion("minecraft") + " · Forge "
				+ Mc.modVersion("forge") + " · Cubiq Client " + Mc.modVersion(CubiqClient.MOD_ID);
		CubiqTitleScreen screen;

		@Override
		public void singleplayer() {
			Mc.setScreen(new SelectWorldScreen(screen));
		}

		@Override
		public void multiplayer() {
			Mc.setScreen(new JoinMultiplayerScreen(screen));
		}

		@Override
		public void options() {
			Mc.setScreen(new OptionsScreen(screen, Mc.mc().options));
		}

		@Override
		public void cubiqMenu() {
			Mc.setScreen(new CubiqMenuScreen(screen));
		}

		/** Einführung beim ersten Start (über dem Startbildschirm). */
		@Override
		public boolean openIntro() {
			new CubiqMenuHost(screen).openIntro();
			return true;
		}

		@Override
		public boolean openAccounts() {
			if (!AccountsScreen.available()) return false;
			Mc.setScreen(AccountsScreen.create(screen));
			return true;
		}

		@Override
		public boolean openWardrobe() {
			if (!WardrobeScreen.available()) return false;
			Mc.setScreen(WardrobeScreen.create(screen));
			return true;
		}

		/** Forge bringt immer eine Mod-Liste mit. */
		@Override
		public boolean openFriends() {
			if (!MenuScreens.friendsAvailable()) return false;
			Mc.setScreen(MenuScreens.friends(screen));
			return true;
		}

		@Override
		public boolean openClips() {
			Mc.setScreen(MenuScreens.clips(screen));
			return true;
		}

		@Override
		public boolean hasMods() {
			return true;
		}

		/** Forges Mod-Liste – per Reflection, weil die Klasse zwischen den Versionen umgezogen ist. */
		@Override
		public void mods() {
			for (String name : MOD_LIST_SCREENS) {
				try {
					Class<?> type = Class.forName(name);
					Mc.setScreen((Screen) type.getConstructor(Screen.class).newInstance(screen));
					return;
				} catch (ClassNotFoundException e) {
					// nächsten Kandidaten versuchen
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					CubiqClient.LOGGER.warn("Modliste konnte nicht geöffnet werden", e);
					return;
				}
			}
			CubiqClient.LOGGER.warn("Keine Forge-Modliste gefunden");
		}

		@Override
		public void quit() {
			Mc.mc().stop();
		}

		@Override
		public void classicTitle() {
			CubiqClient.get().openVanillaTitle();
		}

		@Override
		public String versionLine() {
			return versionLine;
		}

		@Override
		public void playClick() {
			screen.clickSound();
		}

		@Override
		public void narrate(String text) {
			try {
				Mc.narrate(text);
			} catch (RuntimeException | LinkageError e) {
				// Kein Erzähler auf diesem System – dann eben still.
			}
		}

		@Override
		public boolean animated() {
			return CubiqClient.get().modules().titleAnimated.get();
		}

		@Override
		public boolean simpleAnimation() {
			return false;
		}

		/** Eigener Skin/Umhang für die Figur auf der Drehscheibe (kommt aus den Cubiq-Online-Funktionen). */
		@Override
		public dev.pwiepixel.cubiqclient.core.skin.PlayerLook look() {
			dev.pwiepixel.cubiqclient.core.online.OnlineFeatures<Object> f = dev.pwiepixel.cubiqclient.online.OnlineHooks.features();
			return f == null ? null : f.look();
		}

		@Override
		public String playerName() {
			return Mc.mc().getUser().getName();
		}
	}
}
