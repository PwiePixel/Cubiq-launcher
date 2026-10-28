package dev.pwiepixel.cubiqclient.qol;

import dev.pwiepixel.cubiqclient.CubiqClient;
import dev.pwiepixel.cubiqclient.compat.ChatLines;
import dev.pwiepixel.cubiqclient.compat.Mc;
import dev.pwiepixel.cubiqclient.core.chat.ChatCoords;
import dev.pwiepixel.cubiqclient.core.chat.ChatText;
import dev.pwiepixel.cubiqclient.core.chat.ChatWaypoints;
import dev.pwiepixel.cubiqclient.core.clips.ScreenshotShare;
import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.map.WorldMapUi;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.screenshot.Screenshots;
import dev.pwiepixel.cubiqclient.core.ui.WaypointSaveUi;
import dev.pwiepixel.cubiqclient.core.waypoint.WaypointShare;
import dev.pwiepixel.cubiqclient.screen.CubiqUiScreen;
import net.minecraft.network.chat.Component;
//? if >=1.16 {
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
//?}

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Anklickbares im Minecraft-Chat (nur lokal, sendet nichts): Koordinaten werden unterstrichen und öffnen per Klick
 * „Als Wegpunkt speichern“; geteilte Wegpunkte im Format der Minimap-Mods ({@link ChatWaypoints}) werden zu
 * „[Name] x y z · Dimension [+ Wegpunkt] [Karte]“ (übernehmen bzw. auf der Weltkarte zeigen); die Vanilla-Zeile
 * „Bildschirmfoto gespeichert …“ bekommt „[Als Link teilen]“. Erkannt wird der Klick am Einfüge-Text des Stils
 * ({@code "x y z"}, der geteilte Text, {@code cubiq-wpmap:<text>} bzw. {@code cubiq-share:<datei>}) – Umschalt-Klick
 * fügt wie in Vanilla nur diesen Text ins Eingabefeld ein. Ab 1.16; davor bleibt der Chat unverändert.
 * Dieselbe Datei in allen Mojmap-Bäumen.
 */
public final class ChatLinks {
	/** Einfüge-Text von „[Karte]“ an geteilten Wegpunkten: davor der Merker, dahinter der geteilte Text. */
	static final String SHOW_WAYPOINT = "cubiq-wpmap:";
	private static boolean uploading;

	/** Hängt diese Version Aktionen an die Vanilla-Chatzeile „Bildschirmfoto gespeichert …“? */
	//? if >=1.16 {
	public static final boolean DECORATES = true;
	//?} else
	/*public static final boolean DECORATES = false;*/

	private ChatLinks() {
	}

	private static CubiqModules modules() {
		CubiqClient c = CubiqClient.get();
		return c == null ? null : c.modules();
	}

	private static boolean coordsOn(CubiqModules m) {
		return m != null && m.chat.isEnabled() && m.chatCoordLinks.get();
	}

	/** Eingehende Nachricht schmücken (aus ChatFeatures#onMessage). Nie Ausnahmen nach außen. */
	public static Component decorate(Component message) {
		//? if >=1.16 {
		if (message == null) return null;
		try {
			CubiqModules m = modules();
			String plain = message.getString();
			// Ein geteilter Wegpunkt geht vor – sein Text enthält selbst Koordinaten.
			ChatWaypoints.Shared shared = coordsOn(m) ? ChatWaypoints.find(plain) : null;
			boolean coords = shared == null && coordsOn(m) && !ChatCoords.find(plain).isEmpty();
			String shot = screenshotName(message);
			if (shared == null && !coords && shot == null) return message;
			MutableComponent out = shared != null ? withWaypoint(message, shared)
					: coords ? withCoords(message) : message.copy();
			if (shot != null) {
				Screenshots svc = Screenshots.get();
				if (svc != null && svc.onVanillaLine(shot)) {
					// Screenshot-Werkzeuge: [Bearbeiten] [Link kopieren] [Bild kopieren]
					for (Screenshots.Part p : svc.actionParts(shot)) out.append(part(p));
				} else if (svc == null || !svc.enabled()) {
					Style st = Style.EMPTY.applyFormat(ChatFormatting.GOLD).withUnderlined(true)
							.withInsertion(ScreenshotShare.marker(shot)).withHoverEvent(hover(I18n.tr("chat.screenshot.shareHover")));
					out.append(QolText.literal(" ")).append(QolText.literal(I18n.tr("chat.screenshot.share")).setStyle(st));
				}
			}
			return out;
		} catch (RuntimeException e) {
			CubiqClient.LOGGER.error("Chat-Links fehlgeschlagen", e);
			return message;
		}
		//?} else
		/*return message;*/
	}

	//? if >=1.16 {
	// Koordinaten in Textstücken ohne eigene Klick-/Einfüge-Aktion unterstreichen und markieren.
	private static MutableComponent withCoords(Component message) {
		final MutableComponent out = QolText.literal("");
		final String hoverText = I18n.tr("chat.coords.hover");
		message.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
			if (text.isEmpty()) return Optional.empty();
			List<ChatCoords.Hit> hits = style.getClickEvent() == null && style.getInsertion() == null ? ChatCoords.find(text)
					: Collections.<ChatCoords.Hit>emptyList();
			if (hits.isEmpty()) {
				out.append(QolText.literal(text).setStyle(style));
				return Optional.empty();
			}
			int pos = 0;
			for (ChatCoords.Hit h : hits) {
				if (h.start > pos) out.append(QolText.literal(ChatText.activeCodes(text.substring(0, pos)) + text.substring(pos, h.start)).setStyle(style));
				Style mark = style.withUnderlined(true).withInsertion(h.insertion()).withHoverEvent(hover(hoverText));
				out.append(QolText.literal(ChatText.activeCodes(text.substring(0, h.start)) + text.substring(h.start, h.end)).setStyle(mark));
				pos = h.end;
			}
			if (pos < text.length()) out.append(QolText.literal(ChatText.activeCodes(text.substring(0, pos)) + text.substring(pos)).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// Geteilter Wegpunkt: sein Text wird zu „[Name] x y z · Dimension [+ Wegpunkt] [Karte]“, alles davor und danach
	// behält seinen Stil (Name des Absenders, Rang …).
	private static MutableComponent withWaypoint(Component message, final ChatWaypoints.Shared wp) {
		final MutableComponent out = QolText.literal("");
		final int[] pos = {0};
		final boolean[] placed = {false};
		message.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
			int from = pos[0], to = from + text.length();
			pos[0] = to;
			if (from < wp.start) {
				out.append(QolText.literal(text.substring(0, Math.min(to, wp.start) - from)).setStyle(style));
			}
			if (!placed[0] && to > wp.start) {
				out.append(waypointParts(wp));
				placed[0] = true;
			}
			if (to > wp.end) {
				int begin = Math.max(from, wp.end) - from;
				out.append(QolText.literal(ChatText.activeCodes(text.substring(0, begin)) + text.substring(begin)).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (!placed[0]) out.append(waypointParts(wp));
		return out;
	}

	// „[Name] x y z · Dimension [+ Wegpunkt] [Karte]“: Name in der Farbe des Wegpunkts, Aktionen gold bzw. hellblau.
	private static MutableComponent waypointParts(ChatWaypoints.Shared wp) {
		MutableComponent part = QolText.literal("");
		int color = wp.color == 0 ? 0x555555 : wp.color;
		Style name = Style.EMPTY.withColor(TextColor.fromRgb(color)).withInsertion(wp.raw)
				.withHoverEvent(hover(I18n.tr("chat.waypoint.copyHover")));
		part.append(QolText.literal("[" + wp.name + "]").setStyle(name));
		String where = " " + wp.coords();
		if (wp.dimension != null) where += " · " + WorldMapUi.dimensionName(wp.dimension);
		part.append(QolText.literal(where).setStyle(Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
		Style add = Style.EMPTY.applyFormat(ChatFormatting.GOLD).withUnderlined(true).withInsertion(wp.raw)
				.withHoverEvent(hover(I18n.tr("chat.waypoint.addHover")));
		part.append(QolText.literal(" ")).append(QolText.literal(I18n.tr("chat.waypoint.add")).setStyle(add));
		Style map = Style.EMPTY.applyFormat(ChatFormatting.AQUA).withUnderlined(true).withInsertion(SHOW_WAYPOINT + wp.raw)
				.withHoverEvent(hover(I18n.tr("chat.waypoint.mapHover")));
		part.append(QolText.literal(" ")).append(QolText.literal(I18n.tr("chat.waypoint.map")).setStyle(map));
		return part;
	}

	// Bildschirmfoto-Zeile: Stil mit „Datei öffnen“ auf eine Datei unter screenshots/ → relativer Name, sonst null.
	private static String screenshotName(Component message) {
		final String[] found = {null};
		message.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
			String p = openFile(style.getClickEvent());
			if (p != null && found[0] == null) found[0] = p;
			return Optional.empty();
		}, Style.EMPTY);
		if (found[0] == null) return null;
		try {
			return ScreenshotShare.relative(gameDir(), java.nio.file.Paths.get(found[0]));
		} catch (RuntimeException e) {
			return null;
		}
	}

	// Klick auf einen Stil im Chat (ohne Umschalt): unsere Marker behandeln. true = verbraucht. Stile mit eigener
	// Klick-Aktion (Links des Servers) bleiben unberührt.
	public static boolean onClick(Style style) {
		if (style == null || style.getClickEvent() != null) return false;
		String ins = style.getInsertion();
		if (ins == null) return false;
		try {
			boolean links = coordsOn(modules());
			int[] c = links ? ChatCoords.parseInsertion(ins) : null;
			if (c != null) {
				openSave(c[0], c[1], c[2]);
				return true;
			}
			ChatWaypoints.Shared wp = links ? ChatWaypoints.parse(ins) : null;
			if (wp != null) {
				openSaveShared(wp);
				return true;
			}
			ChatWaypoints.Shared show = links && ins.startsWith(SHOW_WAYPOINT)
					? ChatWaypoints.parse(ins.substring(SHOW_WAYPOINT.length())) : null;
			if (show != null) {
				showShared(show);
				return true;
			}
			String shot = ScreenshotShare.parseMarker(ins);
			if (shot != null) {
				share(shot);
				return true;
			}
			Screenshots svc = Screenshots.get();
			if (svc != null && svc.onMarker(ins)) return true;
		} catch (RuntimeException e) {
			CubiqClient.LOGGER.error("Chat-Klick fehlgeschlagen", e);
		}
		return false;
	}
	//?}

	//? if >=1.16 {
	// Ein Teil der Screenshot-Chatzeile als Komponente (Aktion = gold, Dateiname unterstrichen).
	private static MutableComponent part(Screenshots.Part p) {
		Style st = Style.EMPTY;
		if (p.kind == Screenshots.Part.Kind.ACTION) st = st.applyFormat(ChatFormatting.GOLD);
		else if (p.kind == Screenshots.Part.Kind.NAME) st = st.withUnderlined(true);
		if (p.insertion != null) st = st.withInsertion(p.insertion);
		if (p.hover != null) st = st.withHoverEvent(hover(p.hover));
		return QolText.literal(p.text).setStyle(st);
	}
	//?}

	/**
	 * Eigene Zeile „Bildschirmfoto gespeichert: &lt;name&gt; [Bearbeiten] …“ – wenn keine Vanilla-Zeile kam (z. B. weil
	 * Essential sie verschluckt). Ab 1.16; davor false.
	 */
	public static boolean addScreenshotLine(String relativeName) {
		//? if >=1.16 {
		Screenshots svc = Screenshots.get();
		if (svc == null) return false;
		MutableComponent line = QolText.literal("");
		for (Screenshots.Part p : svc.chatLine(relativeName)) line.append(part(p));
		ChatLines.addMessage(line);
		return true;
		//?} else
		/*return false;*/
	}

	// Klick-/Hover-Ereignisse: ab 1.21.5 eigene Datensätze je Art.
	//? if >=1.21.5 {
	/*private static String openFile(ClickEvent e) {
		return e instanceof ClickEvent.OpenFile ? ((ClickEvent.OpenFile) e).path() : null;
	}

	private static HoverEvent hover(String text) {
		return new HoverEvent.ShowText(QolText.literal(text));
	}
	*///?} elif >=1.16 {
	private static String openFile(ClickEvent e) {
		return e != null && e.getAction() == ClickEvent.Action.OPEN_FILE ? e.getValue() : null;
	}

	private static HoverEvent hover(String text) {
		return new HoverEvent(HoverEvent.Action.SHOW_TEXT, QolText.literal(text));
	}
	//?}

	private static Path gameDir() {
		return Mc.mc().gameDirectory.toPath().toAbsolutePath();
	}

	public static void notice(String text) {
		// Direkt statt Mc.actionBar: dessen Parameter unterscheidet sich zwischen den Bäumen (Text/Komponente).
		//? if >=26.2 {
		/*Mc.mc().gui.hud.setOverlayMessage(QolText.plain(text), false);
		*///?} else
		Mc.mc().gui.setOverlayMessage(QolText.plain(text), false);
	}

	/** Fenster „Als Wegpunkt speichern“ (ersetzt den Chat). */
	public static void openSave(int x, int y, int z) {
		Mc.setScreen(new CubiqUiScreen(I18n.tr("waypoint.save.title"),
				new WaypointSaveUi(saveHost(), x, y, z, I18n.tr("waypoint.save.defaultName"))));
	}

	/** Fenster „Geteilten Wegpunkt übernehmen“ mit Name, Farbe und Dimension aus dem Chat (ohne Höhe: die eigene). */
	public static void openSaveShared(ChatWaypoints.Shared wp) {
		int y = wp.hasY() ? wp.y : WaypointShare.currentY();
		Mc.setScreen(new CubiqUiScreen(I18n.tr("waypoint.save.sharedTitle"),
				new WaypointSaveUi(saveHost(), wp.x, y, wp.z, wp.name, wp.color, wp.dimension)));
	}

	/** „[Karte]“: Weltkarte auf den geteilten Wegpunkt (dort per Rechtsklick „Als Wegpunkt speichern“). */
	private static void showShared(ChatWaypoints.Shared wp) {
		int y = wp.hasY() ? wp.y : Integer.MIN_VALUE;
		WaypointShare.Result r = WaypointShare.showShared(wp.name, wp.x, y, wp.z, wp.dimension, wp.color);
		if (r == WaypointShare.Result.OTHER_DIMENSION) notice(I18n.tr(r.key(), WorldMapUi.dimensionName(wp.dimension)));
		else if (r != WaypointShare.Result.OK) notice(I18n.tr(r.key()));
	}

	private static WaypointSaveUi.Host saveHost() {
		return new WaypointSaveUi.Host() {
			@Override
			public void closeScreen() {
				Mc.setScreen(null);
			}

			@Override
			public void playClick() {
				new dev.pwiepixel.cubiqclient.screen.CubiqMenuHost(null).playClick();
			}

			@Override
			public void notice(String text) {
				ChatLinks.notice(text);
			}

			@Override
			public String paste() {
				return null;
			}
		};
	}

	/** „[Als Link teilen]“: hochladen, Link in die Zwischenablage, Hinweis + Chatzeile mit dem Link. */
	public static void share(String name) {
		if (uploading) return;
		Path file = ScreenshotShare.resolve(gameDir(), name);
		if (file == null) {
			notice(I18n.tr("social.error.image_unreadable"));
			return;
		}
		uploading = true;
		boolean started = ScreenshotShare.share(file, (value, error) -> {
			uploading = false;
			if (value == null) {
				notice(I18n.tr(error == null ? "social.error.generic" : error));
				return;
			}
			Mc.setClipboard(value.url);
			String text = ScreenshotShare.copiedText(value, true);
			notice(text);
			ChatLines.addMessage(QolText.plain("Cubiq: " + text + " – " + value.url));
		});
		if (!started) {
			uploading = false;
			notice(I18n.tr("clips.share.offline"));
		} else if (uploading) {
			notice(I18n.tr("chat.screenshot.uploading"));
		}
	}
}
