package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.HudModule;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.online.LegacyOnline;
import dev.pwiepixel.cubiqclient.online.PlayerPreview;
import dev.pwiepixel.cubiqclient.core.ui.menu.HudItem;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuAction;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Minecraft-Seite des Cubiq-Menüs unter 1.8.9–1.12.2: Bildschirme öffnen, Geräusche, Tastennamen.
 * Alles Weitere steht versionsunabhängig in {@code core.ui.menu}.
 */
public final class CubiqMenuHost implements MenuHost {
	private final GuiScreen parent;

	public CubiqMenuHost(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public CubiqModules modules() {
		return CubiqClient.get().modules();
	}

	@Override
	public void playClick() {
		Mc.clickSound();
	}

	@Override
	public void closeScreen() {
		Mc.setScreen(parent);
	}

	@Override
	public void openHudEditor() {
		Mc.setScreen(new HudEditorScreen(parent));
	}

	@Override
	public void openMenu() {
		Mc.setScreen(new CubiqMenuScreen(parent));
	}

	@Override
	public void openPacks() {
		Mc.setScreen(new PackScreen(new CubiqMenuScreen(parent)));
	}

	@Override
	public boolean hasPacks() {
		return true;
	}

	@Override
	public void openAccounts() {
		// closeScreen() eines Hosts zeigt dessen „parent“ – hier also den Kontobildschirm (zurück führt ins Menü).
		new CubiqMenuHost(AccountsScreen.create(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public boolean hasAccounts() {
		return AccountsScreen.available();
	}

	@Override
	public boolean hasIntro() {
		return true;
	}

	/** Einführung (Sprache, Leistung, Tasten, Modul-Pakete); „Schließen“ führt zum Bildschirm unter diesem Host. */
	@Override
	public void openIntro() {
		Mc.setScreen(new CubiqUiScreen(I18n.tr("intro.title"), new dev.pwiepixel.cubiqclient.core.intro.IntroUi(this)));
	}

	/** Alle Tastenbelegungen (Vanilla, Cubiq, andere Mods) – für Konflikte in der Einführung. */
	@Override
	public List<dev.pwiepixel.cubiqclient.core.intro.KeyBind> keyBindings() {
		List<dev.pwiepixel.cubiqclient.core.intro.KeyBind> out = new ArrayList<dev.pwiepixel.cubiqclient.core.intro.KeyBind>();
		net.minecraft.client.Minecraft mc = Mc.mc();
		if (mc == null || mc.gameSettings == null) return out;
		for (net.minecraft.client.settings.KeyBinding k : mc.gameSettings.keyBindings) {
			if (k == null) continue;
			out.add(new dev.pwiepixel.cubiqclient.core.intro.KeyBind(k.getKeyDescription(),
					net.minecraft.client.resources.I18n.format(k.getKeyDescription()), CubiqKeys.nameOf(k.getKeyCodeDefault()), CubiqKeys.link(k)));
		}
		return out;
	}

	@Override
	public void openWardrobe() {
		// closeScreen() eines Hosts zeigt dessen „parent“ – hier also die Garderobe (zurück führt ins Menü).
		new CubiqMenuHost(WardrobeScreen.create(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public boolean hasWardrobe() {
		return WardrobeScreen.available();
	}

	/** Was unter Legacy-Forge nicht umsetzbar ist (Treffer-Farbe, niedriges Feuer), bleibt aus dem Menü heraus. */
	@Override
	public void openFriends() {
		new CubiqMenuHost(MenuScreens.friends(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public boolean hasFriends() {
		return MenuScreens.friendsAvailable();
	}

	@Override
	public void openClips() {
		new CubiqMenuHost(MenuScreens.clips(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public String clipboard() {
		try {
			return Mc.clipboard();
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public boolean hasClips() {
		return true;
	}

	@Override
	public boolean supports(Module module) {
		if (module == modules().colors && !dev.pwiepixel.cubiqclient.render.ColorPass.supported()) return false;
		return CubiqClient.get().menuModules().contains(module);
	}

	@Override
	public List<MenuAction> actions(Module module) {
		List<MenuAction> actions = new ArrayList<MenuAction>();
		if (module == modules().crosshair) {
			actions.add(new MenuAction(I18n.tr("crosshairEditor.title"), "crosshair", new Runnable() {
				@Override
				public void run() {
					Mc.setScreen(new CrosshairEditorScreen(new CubiqMenuScreen(parent)));
				}
			}));
		}
		if (module == modules().waypoints) {
			actions.add(new MenuAction(I18n.tr("menu.action.waypoints"), "compass", new Runnable() {
				@Override
				public void run() {
					Mc.setScreen(new WaypointListScreen(new CubiqMenuScreen(parent)));
				}
			}));
		}
		if (module instanceof HudModule) {
			actions.add(new MenuAction(I18n.tr("menu.action.showInEditor"), "move", new Runnable() {
				@Override
				public void run() {
					openHudEditor();
				}
			}));
		}
		return actions;
	}

	@Override
	public void save() {
		CubiqClient.get().saveConfig();
	}

	@Override
	public boolean shiftDown() {
		return GuiScreen.isShiftKeyDown();
	}

	@Override
	public String keyLabel(String keyName) {
		return Keys.display(keyName);
	}

	@Override
	public String keyNameOf(int rawKey) {
		return Keys.name(rawKey);
	}

	@Override
	public String menuKeyLabel() {
		return GameSettings.getKeyDisplayString(CubiqKeys.menu.getKeyCode());
	}

	@Override
	public String profileKeyLabel() {
		int code = CubiqKeys.hudProfile.getKeyCode();
		return code == Keys.UNBOUND ? "" : GameSettings.getKeyDisplayString(code);
	}

	@Override
	public List<HudItem> hudItems() {
		return CubiqClient.get().hud().editorItems();
	}

	@Override
	public boolean inWorld() {
		return Mc.world() != null;
	}

	// --- Spieler-Vorschau (Umhang-Physik) ---

	@Override
	public int playerPreviewState() {
		if (!supports(modules().capePhysics)) return PREVIEW_UNSUPPORTED;
		return Mc.player() == null ? PREVIEW_NO_PLAYER : PREVIEW_OK;
	}

	@Override
	public boolean previewHasCape() {
		net.minecraft.client.entity.EntityPlayerSP p = Mc.player();
		return p != null && p.hasPlayerInfo() && p.isWearing(net.minecraft.entity.player.EnumPlayerModelParts.CAPE)
				&& p.getLocationCape() != null;
	}

	@Override
	public boolean previewWalking() {
		return LegacyOnline.features() != null && LegacyOnline.features().physics().previewWalking();
	}

	@Override
	public void drawPlayerPreview(Canvas c, int x, int y, int w, int h, float yawDegrees) {
		if (LegacyOnline.features() != null) LegacyOnline.features().physics().preview();
		PlayerPreview.draw(x, y, w, h, yawDegrees);
	}
}
