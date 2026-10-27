package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.CubiqKeys;
import dev.pwiepixel.cubiqclient.compat.Keys;
import dev.pwiepixel.cubiqclient.core.module.HudModule;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.ui.menu.HudItem;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuAction;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Minecraft-Seite des Cubiq-Menüs unter 1.7.10. Resourcepacks gibt es hier nicht;
 * Module, die diese Version nicht kennt, blendet {@link CubiqClient#supported} aus.
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
		Minecraft.getMinecraft().getSoundHandler()
				.playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
	}

	@Override
	public void closeScreen() {
		Minecraft.getMinecraft().displayGuiScreen(parent);
	}

	@Override
	public void openHudEditor() {
		Minecraft.getMinecraft().displayGuiScreen(new HudEditorScreen(parent));
	}

	@Override
	public void openMenu() {
		Minecraft.getMinecraft().displayGuiScreen(new CubiqMenuScreen(parent));
	}

	/** 1.7.10 hat keinen eigenen Resourcepack-Bildschirm im Cubiq-Menü. */
	@Override
	public void openPacks() {
	}

	@Override
	public boolean hasPacks() {
		return false;
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
	public void openWardrobe() {
		// closeScreen() eines Hosts zeigt dessen „parent“ – hier also die Garderobe (zurück führt ins Menü).
		new CubiqMenuHost(WardrobeScreen.create(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public boolean hasWardrobe() {
		return WardrobeScreen.available();
	}

	@Override
	public void openClips() {
		new CubiqMenuHost(MenuScreens.clips(new CubiqMenuScreen(parent))).closeScreen();
	}

	@Override
	public boolean hasClips() {
		return true;
	}

	@Override
	public boolean supports(Module module) {
		return CubiqClient.supported(module);
	}

	@Override
	public List<MenuAction> actions(Module module) {
		List<MenuAction> actions = new ArrayList<MenuAction>();
		if (module == modules().crosshair) {
			actions.add(new MenuAction(I18n.tr("crosshairEditor.title"), "crosshair", new Runnable() {
				@Override
				public void run() {
					Minecraft.getMinecraft().displayGuiScreen(new CrosshairEditorScreen(new CubiqMenuScreen(parent)));
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
		return Minecraft.getMinecraft().theWorld != null;
	}
}
