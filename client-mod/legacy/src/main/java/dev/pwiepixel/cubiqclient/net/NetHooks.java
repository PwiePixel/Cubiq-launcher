package dev.pwiepixel.cubiqclient.net;

import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.net.NetBoost;
import dev.pwiepixel.cubiqclient.core.net.NetPlatform;
import dev.pwiepixel.cubiqclient.core.perf.LatencyPanels;
import dev.pwiepixel.cubiqclient.core.perf.PerfFeature;
import dev.pwiepixel.cubiqclient.core.perf.Performance;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.NettyCompressionEncoder;
import net.minecraft.network.NettyEncryptingDecoder;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
//? if >=1.9 {
/*import net.minecraft.network.play.server.SPacketKeepAlive;
import net.minecraft.network.play.server.SPacketTimeUpdate;
*///?} else {
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
//?}

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Netzwerk-Optimierung und Ping für Forge 1.8.9–1.12.2 (ohne Mixins): Die Cubiq-Handler kommen per Abfrage an die
 * Verbindung – schon während „Verbinde …“ (bevor die Verschlüsselung startet) und danach je Tick. Eigene Ping-Anfragen
 * gibt es im Protokoll dieser Versionen nicht: Ping = Wert der Spielerliste, dazu Keepalive-Jitter und TPS-Schätzung.
 */
public final class NetHooks implements NetPlatform {
	private static final NetHooks INSTANCE = new NetHooks();
	private static CubiqModules modules;
	private static Field connectingField;
	private static boolean connectingFieldSearched;

	private NetHooks() {
	}

	public static NetHooks get() {
		return INSTANCE;
	}

	public static void init(final CubiqModules m, final Consumer<String> log) {
		modules = m;
		NetBoost.init(INSTANCE, new NetBoost.Switch() {
			@Override
			public boolean on() {
				Performance p = Performance.current();
				return m.netOptimize.isEnabled() && (p == null || p.active(PerfFeature.NET_CODECS));
			}
		}, new NetBoost.Logger() {
			@Override
			public void info(String message) {
				if (log != null) log.accept(message);
			}

			@Override
			public void warn(String message, Throwable t) {
				if (log != null) log.accept(message + ": " + t);
			}
		});
		LatencyPanels.registerNetOnly(m.netOptimize);
		dev.pwiepixel.cubiqclient.core.connect.FastConnect.install(m, dev.pwiepixel.cubiqclient.core.i18n.I18n.configDir(), NetBoost.logger());
		// Server-Ressourcenpakete: server-resource-packs/<sha1>, höchstens 50 MB (wie Vanilla 1.8.9–1.12.2).
		java.util.Map<String, String> headers = new java.util.HashMap<String, String>();
		headers.put("User-Agent", "Minecraft Java/" + Mc.version());
		dev.pwiepixel.cubiqclient.core.connect.ServerPacks.setup(dev.pwiepixel.cubiqclient.core.i18n.I18n.configDir(),
				dev.pwiepixel.cubiqclient.core.connect.ServerPacks.Era.HASH, 52428800L, headers,
				dev.pwiepixel.cubiqclient.core.connect.FastConnect.fastSwitchSwitch(m), dev.pwiepixel.cubiqclient.core.connect.FastConnect.packsSwitch(m));
		dev.pwiepixel.cubiqclient.core.connect.FastConnect.startup();
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || modules == null) return;
		CubiqModules m = modules;
		NetHandlerPlayClient net = Mc.connection();
		Channel ch = net == null || net.getNetworkManager() == null ? null : net.getNetworkManager().channel();
		NetBoost.tick(ch, m.ping.isEnabled(), Math.round(m.pingInterval.get() * 1000), Math.round(m.pingSpikeThreshold.get()));
		// Schnell verbinden: Paket-Beobachter an die Verbindung, Serverwechsel fertig, sobald „Lade Gelände“ zu ist.
		LegacyPacks.attach(ch);
		dev.pwiepixel.cubiqclient.core.connect.FastSwitch.tick(Mc.world() != null && Mc.player() != null
				&& !(Mc.screen() instanceof net.minecraft.client.gui.GuiDownloadTerrain));
	}

	/** Zuletzt gesehener „Verbinde …“-Bildschirm (Schnell verbinden: Anzeige einmal je Bildschirm vorbereiten). */
	private static Object lastConnecting;
	private static Field selectorField;
	private static Field selectedField;
	private static boolean selectorSearched;

	/** Je Bild, solange „Verbinde …“ offen ist: Handler früh einhängen (vor der Verschlüsselung). */
	@SubscribeEvent
	public void onRenderTick(TickEvent.RenderTickEvent event) {
		if (event.phase != TickEvent.Phase.START || modules == null) return;
		Object screen = Minecraft.getMinecraft().currentScreen;
		if (screen instanceof net.minecraft.client.gui.GuiMultiplayer) fastSelection((net.minecraft.client.gui.GuiMultiplayer) screen);
		if (screen instanceof net.minecraft.client.gui.GuiMainMenu) dev.pwiepixel.cubiqclient.core.connect.ServerPacks.cancelPreloads();
		if (!(screen instanceof GuiConnecting)) {
			lastConnecting = null;
			return;
		}
		if (screen != lastConnecting) {
			lastConnecting = screen;
			net.minecraft.client.multiplayer.ServerData d = Minecraft.getMinecraft().getCurrentServerData();
			dev.pwiepixel.cubiqclient.core.connect.FastConnect.connectScreenOpened();
			dev.pwiepixel.cubiqclient.core.connect.FastConnect.STATUS.expect(d == null ? null : d.serverIP);
		}
		NetworkManager nm = connecting((GuiConnecting) screen);
		if (nm != null && nm.channel() != null) {
			NetBoost.attach(nm.channel());
			LegacyPacks.attach(nm.channel());
		}
	}

	/** „Verbinde …“ ohne Redstone-Stil: Zeile von „Schnell verbinden“ unter Vanillas Text, wenn es länger dauert. */
	@SubscribeEvent
	public void onScreenDrawn(net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Post event) {
		net.minecraft.client.gui.GuiScreen s = Mc.eventGui(event);
		if (!(s instanceof GuiConnecting)) return;
		if (dev.pwiepixel.cubiqclient.core.menus.MenuStyle.enabled(dev.pwiepixel.cubiqclient.core.menus.MenuStyle.Kind.LOADING)) return;
		String line = dev.pwiepixel.cubiqclient.core.connect.FastConnect.STATUS.line();
		if (line == null) return;
		net.minecraft.client.gui.FontRenderer font = Mc.font();
		font.drawStringWithShadow(line, s.width / 2 - font.getStringWidth(line) / 2, s.height / 2 - 50 + 14, 0xA0A0A0);
	}

	/**
	 * Mehrspieler-Liste: ausgewählten Server vorab auflösen, sein Ressourcenpaket vorladen und – Java 8 – Javas
	 * Adress-Speicher füllen, damit der Klick ohne DNS und gleich zur besten Adresse verbindet.
	 */
	private static void fastSelection(net.minecraft.client.gui.GuiMultiplayer s) {
		try {
			if (!selectorSearched) {
				selectorSearched = true;
				for (Field f : net.minecraft.client.gui.GuiMultiplayer.class.getDeclaredFields()) {
					if (f.getType() == net.minecraft.client.gui.ServerSelectionList.class) {
						f.setAccessible(true);
						selectorField = f;
						break;
					}
				}
				for (Field f : net.minecraft.client.gui.ServerSelectionList.class.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
						f.setAccessible(true);
						selectedField = f;
						break;
					}
				}
			}
			if (selectorField == null || selectedField == null) return;
			Object list = selectorField.get(s);
			if (list == null) return;
			int index = selectedField.getInt(list);
			net.minecraft.client.multiplayer.ServerList servers = s.getServerList();
			if (servers == null || index < 0 || index >= servers.countServers()) return;
			String ip = servers.getServerData(index).serverIP;
			dev.pwiepixel.cubiqclient.core.connect.FastConnect.prefetch(ip);
			dev.pwiepixel.cubiqclient.core.connect.FastConnect.primeJvm(ip);
			dev.pwiepixel.cubiqclient.core.connect.ServerPacks.preload(ip, true);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			// nur ein Vorgriff
		}
	}

	/** Verbindung des „Verbinde …“-Bildschirms (privates Feld, per Typ gesucht). */
	private static NetworkManager connecting(GuiConnecting screen) {
		try {
			if (!connectingFieldSearched) {
				connectingFieldSearched = true;
				for (Field f : GuiConnecting.class.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers()) && f.getType() == NetworkManager.class) {
						f.setAccessible(true);
						connectingField = f;
						break;
					}
				}
			}
			return connectingField == null ? null : (NetworkManager) connectingField.get(screen);
		} catch (Throwable t) {
			return null;
		}
	}

	// --- NetPlatform ---

	@Override
	public ChannelHandler upgradeDecompress(ChannelHandler vanilla) {
		if (vanilla.getClass() != NettyCompressionDecoder.class) return null;
		int threshold = NetBoost.intField(vanilla, -1);
		return threshold < 0 ? null : new CubiqCompressionDecoder(threshold);
	}

	@Override
	public ChannelHandler upgradeCompress(ChannelHandler vanilla) {
		if (vanilla.getClass() != NettyCompressionEncoder.class) return null;
		int threshold = NetBoost.intField(vanilla, -1);
		return threshold < 0 ? null : new CubiqCompressionEncoder(threshold);
	}

	@Override
	public boolean vanillaDecrypt(ChannelHandler handler) {
		return handler.getClass() == NettyEncryptingDecoder.class;
	}

	@Override
	public long pongTime(Object packet) {
		return NONE;
	}

	@Override
	public long gameTime(Object packet) {
		//? if >=1.9 {
		/*return packet instanceof SPacketTimeUpdate ? ((SPacketTimeUpdate) packet).getTotalWorldTime() : NONE;
		*///?} else
		return packet instanceof S03PacketTimeUpdate ? ((S03PacketTimeUpdate) packet).getTotalWorldTime() : NONE;
	}

	@Override
	public long keepAliveId(Object packet) {
		//? if >=1.9 {
		/*return packet instanceof SPacketKeepAlive ? ((SPacketKeepAlive) packet).getId() : NONE;
		*///?} else
		return packet instanceof S00PacketKeepAlive ? ((S00PacketKeepAlive) packet).func_149134_c() : NONE;
	}

	@Override
	public boolean activePing() {
		return false;
	}

	@Override
	public boolean sendPing(long millis) {
		// Gibt es in diesen Versionen nicht (siehe Klassenbeschreibung).
		return false;
	}

	@Override
	public long millis() {
		return System.nanoTime() / 1_000_000L;
	}

	@Override
	public int serverLatency() {
		EntityPlayerSP player = Mc.player();
		NetHandlerPlayClient net = Mc.connection();
		if (player == null || net == null) return -1;
		NetworkPlayerInfo info = net.getPlayerInfo(player.getUniqueID());
		return info == null ? -1 : info.getResponseTime();
	}

	@Override
	public boolean multiplayer() {
		Minecraft mc = Minecraft.getMinecraft();
		return Mc.connection() != null && Mc.player() != null && !mc.isSingleplayer();
	}
}
