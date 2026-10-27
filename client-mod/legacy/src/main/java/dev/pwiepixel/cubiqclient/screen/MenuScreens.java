package dev.pwiepixel.cubiqclient.screen;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.online.CubiqOnline;
import dev.pwiepixel.cubiqclient.core.ui.clips.ClipsHost;
import dev.pwiepixel.cubiqclient.core.ui.clips.ClipsUi;
import dev.pwiepixel.cubiqclient.core.social.SocialOverlay;
import dev.pwiepixel.cubiqclient.core.ui.UiScreen;
import dev.pwiepixel.cubiqclient.core.ui.social.QuickReplyUi;
import dev.pwiepixel.cubiqclient.core.ui.social.SocialHost;
import dev.pwiepixel.cubiqclient.core.ui.social.SocialUi;
import dev.pwiepixel.cubiqclient.core.ui.menus.ServerInfoHost;
import dev.pwiepixel.cubiqclient.core.ui.menus.ServerInfoUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Einstiege in die Cubiq-Bildschirme Sozial (Chat + Freunde), Clips &amp; Bilder und Server-Info unter Minecraft 1.8.9–1.12.2.
 * Inhalt und Bedienung stehen versionsunabhängig in {@code core.ui}.
 */
public final class MenuScreens {
	private MenuScreens() {
	}

	// --- Sozial (Chat + Freunde) ---

	public static boolean friendsAvailable() {
		return CubiqOnline.current() != null;
	}

	/** Sozial-Bildschirm (Reiter Chat / Freunde) – ersetzt den früheren Freunde-Bildschirm. */
	public static GuiScreen friends(final GuiScreen parent) {
		return social(parent);
	}

	public static GuiScreen social(final GuiScreen parent) {
		return new CubiqUiScreen(I18n.tr("social.title"), new SocialUi(host(parent), CubiqOnline.current()));
	}

	/** Bildschirm zur Schnelltaste (Antwort/Beitreten → kleine Einblendung, Anfragen → Sozial-Bildschirm). */
	public static GuiScreen socialAction(SocialOverlay.QuickAction action, GuiScreen parent) {
		UiScreen ui = SocialUi.forAction(host(parent), CubiqOnline.current(), action);
		String title = ui instanceof QuickReplyUi ? I18n.tr("social.quickReply.title")
				: ui instanceof dev.pwiepixel.cubiqclient.core.ui.hosting.HostingUi ? I18n.tr("hosting.title")
				: ui instanceof dev.pwiepixel.cubiqclient.core.ui.hosting.JoinUi ? I18n.tr("hosting.join.title") : I18n.tr("social.title");
		return new CubiqUiScreen(title, ui);
	}

	// --- Welt-Hosting ---

	/** Einstellungen bzw. Verwaltung der offenen Welt (Pausemenü). */
	public static GuiScreen hosting(GuiScreen parent) {
		return new CubiqUiScreen(I18n.tr("hosting.title"), new dev.pwiepixel.cubiqclient.core.ui.hosting.HostingUi(host(parent)));
	}

	/** „Mit Code beitreten“ + Welten der Freunde (Mehrspieler-Menü). */
	public static GuiScreen join(GuiScreen parent) {
		return new CubiqUiScreen(I18n.tr("hosting.join.title"), new dev.pwiepixel.cubiqclient.core.ui.hosting.JoinUi(host(parent)));
	}

	static SocialHost host(final GuiScreen parent) {
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		return new SocialHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public String userAgent() {
				return "Cubiq-Client";
			}

			@Override
			public Path gameDir() {
				return Mc.gameDir().toPath().toAbsolutePath();
			}

			@Override
			public String currentServer() {
				return Mc.mc().getIntegratedServer() != null ? null : Mc.serverAddress();
			}

			@Override
			public boolean inWorld() {
				return Mc.world() != null;
			}

			@Override
			public void joinServer(String address, String label) {
				Mc.leaveWorld();
				Mc.connect(address, label);
			}

			@Override
			public void copy(String text) {
				Mc.setClipboard(text);
			}

			@Override
			public String paste() {
				return Mc.clipboard();
			}
		};
	}

	// --- Clips & Bilder ---

	public static GuiScreen clips(final GuiScreen parent) {
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		final Path gameDir = Mc.gameDir().toPath().toAbsolutePath();
		ClipsHost host = new ClipsHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public Path gameDir() {
				return gameDir;
			}

			@Override
			public Path configDir() {
				return gameDir.resolve("config");
			}

			@Override
			public boolean copy(String text) {
				Mc.setClipboard(text);
				return true;
			}
		};
		return new CubiqUiScreen(I18n.tr("clips.title"), new ClipsUi(host));
	}

	// --- Server-Info ---

	public static boolean serverInfoAvailable() {
		return Mc.world() != null;
	}

	public static GuiScreen serverInfo(final GuiScreen parent) {
		final CubiqMenuHost back = new CubiqMenuHost(parent);
		ServerInfoHost host = new ServerInfoHost() {
			@Override
			public void playClick() {
				back.playClick();
			}

			@Override
			public void closeScreen() {
				back.closeScreen();
			}

			@Override
			public Info info() {
				return serverInfo();
			}

			@Override
			public boolean copy(String text) {
				Mc.setClipboard(text);
				return true;
			}

			@Override
			public String userAgent() {
				return "Cubiq-Client";
			}
		};
		return new CubiqUiScreen(I18n.tr("menus.serverInfo.title"), new ServerInfoUi(host, CubiqOnline.current()));
	}

	static ServerInfoHost.Info serverInfo() {
		Minecraft mc = Mc.mc();
		ServerInfoHost.Info info = new ServerInfoHost.Info();
		info.singleplayer = mc.getIntegratedServer() != null;
		if (info.singleplayer) {
			info.name = Mc.levelName();
			info.lan = mc.getIntegratedServer().getPublic();
		} else {
			ServerData data = mc.getCurrentServerData();
			if (data != null) {
				info.name = data.serverName;
				info.address = data.serverIP;
				info.version = data.gameVersion;
			}
		}
		NetHandlerPlayClient net = Mc.connection();
		EntityPlayerSP player = Mc.player();
		if (net != null) {
			info.players = net.getPlayerInfoMap().size();
			if (player != null) {
				NetworkPlayerInfo self = net.getPlayerInfo(player.getUniqueID());
				if (self != null) info.ping = self.getResponseTime();
			}
		}
		String dim = Mc.dimensionId();
		info.dimension = dim == null || dim.isEmpty() ? null : dim;
		if (player != null) {
			info.position = String.format(Locale.ROOT, "%.1f / %.1f / %.1f", player.posX, player.posY, player.posZ);
		}
		return info;
	}

	/** Garderobe (Skins, Outfits, Umhänge, Emotes) als Bildschirm oder null, wo es sie nicht gibt. */
	public static GuiScreen wardrobe(GuiScreen parent) {
		return WardrobeScreen.available() ? WardrobeScreen.create(parent) : null;
	}
}
