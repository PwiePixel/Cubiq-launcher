package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleHost;
import dev.pwiepixel.cubiqclient.core.ui.title.TitleUi;
import dev.pwiepixel.cubiqclient.ui.Gfx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsScreen;
//?} else
/*import net.minecraft.client.gui.screens.OptionsScreen;*/

/**
 * Cubiq-Startbildschirm (ersetzt den Vanilla-Titelbildschirm, abschaltbar im Modul "Startbildschirm").
 * Aussehen und Bedienung – Würfel-Szene, Knöpfe, Tastatur – stehen versionsunabhängig in
 * {@link TitleUi}; hier nur die Ziele der Knöpfe für diese Minecraft-Version.
 */
public final class CubiqTitleScreen extends CubiqUiScreen {
	private static final String MODMENU_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";

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

	/** Lage eines Knopfs {x, y, w, h} im letzten Bild oder null (Selbsttest: echte Klicks). */
	public int[] spot(String label) {
		return title.buttonRect(label);
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
		private final boolean modMenu = FabricLoader.getInstance().isModLoaded("modmenu");
		private final String versionLine;
		CubiqTitleScreen screen;

		Host() {
			String mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
			String cubiqVersion = FabricLoader.getInstance().getModContainer(CubiqClient.MOD_ID)
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
			versionLine = "Minecraft " + mcVersion + " · Cubiq Client " + cubiqVersion;
		}

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
			//? if >=26.1 && <26.3 {
			/*Mc.setScreen(new OptionsScreen(screen, Mc.mc().options, false));
			*///?} else
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
			return modMenu;
		}

		/** ModMenu ist optional – daher per Reflection, ohne Abhängigkeit. */
		@Override
		public void mods() {
			try {
				Class<?> type = Class.forName(MODMENU_SCREEN);
				Mc.setScreen((Screen) type.getConstructor(Screen.class).newInstance(screen));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				CubiqClient.LOGGER.warn("ModMenu konnte nicht geöffnet werden", e);
			}
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
