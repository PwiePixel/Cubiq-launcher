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
import dev.pwiepixel.cubiqclient.online.OnlineHooks;
import dev.pwiepixel.cubiqclient.online.PlayerPreview;
import dev.pwiepixel.cubiqclient.ui.GfxCanvas;
import dev.pwiepixel.cubiqclient.core.ui.menu.HudItem;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuAction;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Minecraft-Seite des Cubiq-Menüs: Bildschirme öffnen, Geräusche, Tastennamen.
 * Alles Weitere steht versionsunabhängig in {@code core.ui.menu}.
 */
public final class CubiqMenuHost implements MenuHost {
	private final Screen parent;

	public CubiqMenuHost(Screen parent) {
		this.parent = parent;
	}

	public Screen parent() {
		return parent;
	}

	@Override
	public CubiqModules modules() {
		return CubiqClient.get().modules();
	}

	@Override
	public void playClick() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
		Mc.setScreen(AccountsScreen.create(new CubiqMenuScreen(parent)));
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
		List<dev.pwiepixel.cubiqclient.core.intro.KeyBind> out = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.options == null) return out;
		for (net.minecraft.client.KeyMapping k : mc.options.keyMappings) {
			if (k == null) continue;
			final net.minecraft.client.KeyMapping km = k;
			out.add(new dev.pwiepixel.cubiqclient.core.intro.KeyBind(k.getName(),
					net.minecraft.client.resources.language.I18n.get(k.getName()), k.getDefaultKey().getName(), CubiqKeys.link(() -> km)));
		}
		return out;
	}

	@Override
	public void openWardrobe() {
		Mc.setScreen(WardrobeScreen.create(new CubiqMenuScreen(parent)));
	}

	@Override
	public boolean hasWardrobe() {
		return WardrobeScreen.available();
	}

	@Override
	public void openFriends() {
		Mc.setScreen(MenuScreens.friends(new CubiqMenuScreen(parent)));
	}

	@Override
	public boolean hasFriends() {
		return MenuScreens.friendsAvailable();
	}

	@Override
	public void openClips() {
		Mc.setScreen(MenuScreens.clips(new CubiqMenuScreen(parent)));
	}

	@Override
	public String clipboard() {
		try {
			return Mc.mc().keyboardHandler.getClipboard();
		} catch (RuntimeException e) {
			return null;
		}
	}

	@Override
	public boolean hasClips() {
		return true;
	}

	/** In diesen Versionen gibt es jedes Modul. */
	@Override
	public boolean supports(Module module) {
		// Farben brauchen eine OpenGL-Zeichenfläche (nicht mit dem Vulkan-Backend).
		if (!dev.pwiepixel.cubiqclient.qol.QolHooks.supported(module)) return false;
		if (!dev.pwiepixel.cubiqclient.comfort.ComfortHooks.supported(module)) return false;
		if (module == modules().colors) return dev.pwiepixel.cubiqclient.render.ColorPass.supported();
		return true;
	}

	@Override
	public List<MenuAction> actions(Module module) {
		List<MenuAction> actions = new ArrayList<>();
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
		return actions.isEmpty() ? Collections.<MenuAction>emptyList() : actions;
	}

	@Override
	public void save() {
		CubiqClient.get().saveConfig();
	}

	@Override
	public boolean shiftDown() {
		return Keys.isDown("key.keyboard.left.shift") || Keys.isDown("key.keyboard.right.shift");
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
		return Mc.keyName(CubiqKeys.menu);
	}

	@Override
	public String profileKeyLabel() {
		return CubiqKeys.boundKey(CubiqKeys.hudProfile) == Keys.UNBOUND ? "" : Mc.keyName(CubiqKeys.hudProfile);
	}

	@Override
	public List<HudItem> hudItems() {
		return CubiqClient.get().hud().editorItems();
	}

	@Override
	public boolean inWorld() {
		return Minecraft.getInstance().level != null;
	}

	// --- Spieler-Vorschau (Umhang-Physik) ---

	@Override
	public int playerPreviewState() {
		if (!PlayerPreview.supported() || !supports(modules().capePhysics)) return PREVIEW_UNSUPPORTED;
		return Minecraft.getInstance().player == null ? PREVIEW_NO_PLAYER : PREVIEW_OK;
	}

	@Override
	public boolean previewHasCape() {
		net.minecraft.client.player.AbstractClientPlayer player = Minecraft.getInstance().player;
		return player != null && OnlineHooks.hasCapeVisible(player);
	}

	@Override
	public boolean previewWalking() {
		return OnlineHooks.features() != null && OnlineHooks.features().physics().previewWalking();
	}

	@Override
	public void drawPlayerPreview(Canvas c, int x, int y, int w, int h, float yawDegrees) {
		if (OnlineHooks.features() != null) OnlineHooks.features().physics().preview();
		PlayerPreview.draw(GfxCanvas.current().gfx().raw(), x, y, w, h, yawDegrees);
	}
}
