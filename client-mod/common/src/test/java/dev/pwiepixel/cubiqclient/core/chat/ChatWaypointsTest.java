package dev.pwiepixel.cubiqclient.core.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geteilte Wegpunkte im Chat (Format der Xaero-Minimap) erkennen und selbst schreiben. */
class ChatWaypointsTest {
	@Test
	void findsSharedWaypointInPlayerLine() {
		String line = "<Steve> xaero-waypoint:Base:B:100:64:-200:6:false:0:Internal-overworld-waypoints";
		ChatWaypoints.Shared s = ChatWaypoints.find(line);
		assertNotNull(s);
		assertEquals("Base", s.name);
		assertEquals("B", s.initials);
		assertEquals(100, s.x);
		assertEquals(64, s.y);
		assertEquals(-200, s.z);
		assertEquals(0xFFAA00, s.color);
		assertEquals("minecraft:overworld", s.dimension);
		assertEquals("<Steve> ".length(), s.start);
		assertEquals(line.length(), s.end);
		assertEquals(line.substring(s.start), s.raw);
		assertEquals("100 64 -200", s.coords());
	}

	@Test
	void dimensionsFromTheWaypointSet() {
		assertEquals("minecraft:the_nether", ChatWaypoints.find("xaero-waypoint:N:N:1:70:2:12:false:0:Internal-the-nether-waypoints").dimension);
		assertEquals("minecraft:the_end", ChatWaypoints.find("xaero-waypoint:E:E:1:70:2:13:false:0:Internal-the-end-waypoints").dimension);
		assertEquals("minecraft:the_nether", ChatWaypoints.find("xaero-waypoint:N:N:1:70:2:12:false:0:Internal-dim%-1-waypoints").dimension);
		assertEquals("legacy:dim7", ChatWaypoints.find("xaero-waypoint:T:T:1:70:2:12:false:0:Internal-dim%7-waypoints").dimension);
		assertEquals("twilightforest:twilight_forest",
				ChatWaypoints.find("xaero-waypoint:T:T:1:70:2:12:false:0:Internal-dim%twilightforest$twilight_forest-waypoints").dimension);
		// Kurzform ohne „-waypoints“ (z. B. von Server-Plugins).
		assertEquals("minecraft:overworld", ChatWaypoints.find("x%self:xaero-waypoint:1:1:9:-60:13:2:false:0:Internal-overworld").dimension);
		// Unbekannter Satz → Dimension offen (dann gilt die aktuelle).
		assertNull(ChatWaypoints.find("xaero-waypoint:A:A:1:70:2:3:false:0:gui.xaero_default").dimension);
	}

	@Test
	void numericNameAndInitials() {
		ChatWaypoints.Shared s = ChatWaypoints.find("x%self:xaero-waypoint:1:1:9:-60:13:2:false:0:Internal-overworld");
		assertNotNull(s);
		assertEquals("1", s.name);
		assertEquals(9, s.x);
		assertEquals(-60, s.y);
		assertEquals(13, s.z);
		assertEquals(0x00AA00, s.color);
	}

	@Test
	void namesWithColonsAndSpaces() {
		ChatWaypoints.Shared s = ChatWaypoints.find("[VIP] Alex: xaero-waypoint:Shop: Diamonds:S:-1500:80:320:9:false:0:Internal-overworld-waypoints");
		assertNotNull(s);
		assertEquals("Shop: Diamonds", s.name);
		assertEquals("S", s.initials);
		assertEquals(-1500, s.x);
		assertEquals(0x5555FF, s.color);
		// Maskierter Doppelpunkt.
		assertEquals("A:B", ChatWaypoints.find("xaero-waypoint:A§§B:A:1:2:3:0:false:0:Internal-overworld-waypoints").name);
	}

	@Test
	void withoutHeight() {
		ChatWaypoints.Shared s = ChatWaypoints.find("xaero-waypoint:Village:V:250:~:-80:10:false:0:Internal-overworld-waypoints");
		assertNotNull(s);
		assertFalse(s.hasY());
		assertEquals(ChatWaypoints.NO_Y, s.y);
		assertEquals("250 ~ -80", s.coords());
	}

	@Test
	void shortFormsAndTrailingText() {
		// Ältere Form ohne Drehen/Blickrichtung/Satz.
		ChatWaypoints.Shared old = ChatWaypoints.find("xaero-waypoint:Home:H:5:64:6:4");
		assertNotNull(old);
		assertEquals(0xAA0000, old.color);
		assertNull(old.dimension);
		// Ohne Farbe → Weiß.
		assertEquals(0xFFFFFF, ChatWaypoints.find("xaero-waypoint:Home:H:5:64:6").color);
		// Text nach dem Wegpunkt gehört nicht dazu.
		String line = "xaero-waypoint:Home:H:5:64:6:4:false:0:Internal-overworld-waypoints komm vorbei";
		ChatWaypoints.Shared s = ChatWaypoints.find(line);
		assertEquals(" komm vorbei", line.substring(s.end));
		assertEquals("xaero-waypoint:Home:H:5:64:6:4:false:0:Internal-overworld-waypoints", s.raw);
		// Ungültige Farbe bleibt Weiß, Name ohne Kürzel bekommt eins.
		ChatWaypoints.Shared odd = ChatWaypoints.find("xaero-waypoint:base::5:64:6:99");
		assertEquals(0xFFFFFF, odd.color);
		assertEquals("B", odd.initials);
	}

	@Test
	void rejectsNonsense() {
		assertNull(ChatWaypoints.find("hallo welt"));
		assertNull(ChatWaypoints.find("xaero-waypoint:"));
		assertNull(ChatWaypoints.find("xaero-waypoint:Home:H:abc:64:6"));
		assertNull(ChatWaypoints.find("xaero-waypoint:Home:H:5:64"));
		// Jenseits der Weltgrenze bzw. unmögliche Höhe.
		assertNull(ChatWaypoints.find("xaero-waypoint:Far:F:40000000:64:6:1"));
		assertNull(ChatWaypoints.find("xaero-waypoint:Deep:D:5:9000:6:1"));
		assertNull(ChatWaypoints.parse("Home:H:5:64:6"));
		assertNull(ChatWaypoints.find(null));
	}

	@Test
	void controlAndFormattingCharactersAreRemoved() {
		ChatWaypoints.Shared s = ChatWaypoints.find("xaero-waypoint:§cRed\u0007Base:R:5:64:6:12");
		assertEquals("cRedBase", s.name);
	}

	@Test
	void formatRoundTrips() {
		String text = ChatWaypoints.format("Nether Hub", 12, 70, -34, 0xFF5555, "minecraft:the_nether");
		assertEquals("xaero-waypoint:Nether Hub:N:12:70:-34:12:false:0:Internal-the-nether-waypoints", text);
		ChatWaypoints.Shared s = ChatWaypoints.parse(text);
		assertNotNull(s);
		assertEquals("Nether Hub", s.name);
		assertEquals(12, s.x);
		assertEquals(70, s.y);
		assertEquals(-34, s.z);
		assertEquals(0xFF5555, s.color);
		assertEquals("minecraft:the_nether", s.dimension);
		// Legacy-Schreibweise und Modded-Dimensionen.
		assertEquals("minecraft:the_end", ChatWaypoints.parse(ChatWaypoints.format("E", 1, 2, 3, 0, "dim1")).dimension);
		assertEquals("legacy:dim7", ChatWaypoints.parse(ChatWaypoints.format("E", 1, 2, 3, 0, "dim7")).dimension);
		assertEquals("aether:the_aether", ChatWaypoints.parse(ChatWaypoints.format("A", 1, 2, 3, 0, "aether:the_aether")).dimension);
		// Doppelpunkte im Namen werden zu Leerzeichen, ohne Höhe „~“.
		String noY = ChatWaypoints.format("a:b", 1, ChatWaypoints.NO_Y, 3, 0xFFFFFF, "");
		assertEquals("xaero-waypoint:a b:A:1:~:3:15:false:0:Internal-overworld-waypoints", noY);
		assertTrue(noY.length() <= ChatOut.MAX_LENGTH);
		assertEquals(noY, ChatOut.sanitize(noY));
	}

	@Test
	void nearestChatColor() {
		assertEquals(12, ChatWaypoints.colorIndex(0xE0281E));
		assertEquals(15, ChatWaypoints.colorIndex(0xFFFFFF));
		assertEquals(6, ChatWaypoints.colorIndex(0xFF7A1F));
		assertEquals(9, ChatWaypoints.colorIndex(0x3D7BFF));
		assertEquals(10, ChatWaypoints.colorIndex(0x3DDC84));
		assertEquals(14, ChatWaypoints.colorIndex(0xF2E14C));
	}
}
