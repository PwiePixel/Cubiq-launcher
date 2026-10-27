package dev.pwiepixel.cubiqclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/** Tastenbelegungen – erscheinen in den Steuerungs-Optionen unter "Cubiq Client". */
public final class CubiqKeys {
	// Kategorie: bis 1.21.8 ein Übersetzungsschlüssel, ab 1.21.9 ein registrierter Datensatz
	// (Anzeigename dann über "key.category.cubiqclient.main").
	//? if >=1.21.9 {
	/*public static KeyMapping.Category CATEGORY;
	*///?} else
	public static final String CATEGORY = "key.categories.cubiqclient";

	public static KeyMapping menu;
	public static KeyMapping zoom;
	public static KeyMapping fullbright;
	/** Gamma durchschalten: aus → an → voll → aus (standardmäßig unbelegt). */
	public static KeyMapping gammaCycle;
	/** Automatisch nachlegen an/aus (standardmäßig unbelegt). */
	public static KeyMapping autoRefill;
	public static KeyMapping freelook;
	/** Wechselt das HUD-Profil (standardmäßig unbelegt). */
	public static KeyMapping hudProfile;
	/** Emote-Rad (halten, Standard G – in keiner Vanilla-Version belegt). */
	public static KeyMapping emoteWheel;
	/** Schaltet das Redstone-Signal-Overlay (F6 – in keiner Vanilla-Version ab 1.9 belegt). */
	public static KeyMapping redstoneOverlay;
	/** Clip der letzten Sekunden speichern (F9 – in keiner Vanilla-Version belegt). */
	public static KeyMapping saveClip;
	/** Aufnahme starten/stoppen (F10 – in keiner Vanilla-Version belegt). */
	public static KeyMapping toggleRecording;
	/** Öffnet die Garderobe (standardmäßig unbelegt). */
	public static KeyMapping wardrobe;
	/** Öffnet die Weltkarte (M – in keiner Vanilla-Version belegt; bei Doppelbelegung einmalig freigegeben). */
	public static KeyMapping worldMap;
	/** Öffnet den Sozial-Bildschirm (standardmäßig unbelegt). */
	public static KeyMapping social;
	/** Schnellantwort/Beitreten zum neuesten Sozial-Toast (Y – in keiner Vanilla-Version belegt). */
	public static KeyMapping quickReply;
	/** Nimmt ein 360°-Panorama auf (standardmäßig unbelegt; auch im Menü auslösbar). */
	public static KeyMapping panorama;
	/** Öffnet die Notizen der aktuellen Welt im Cubiq-Menü (standardmäßig unbelegt). */
	public static KeyMapping notes;
	/** Kamera-Fahrten: Keyframe setzen, abspielen/anhalten, letzten entfernen, löschen (alle standardmäßig unbelegt). */
	public static KeyMapping cinematicAdd;
	public static KeyMapping cinematicPlay;
	public static KeyMapping cinematicRemove;
	public static KeyMapping cinematicClear;
	/** Freie Kamera an/aus (standardmäßig unbelegt). */
	public static KeyMapping cinematicFreecam;

	private CubiqKeys() {
	}

	// Tastatur-Eingabetyp: KEYSYM bis 26.2, KEYBOARD ab 26.3.
	//? if >=26.3 {
	/*private static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;
	*///?} else
	private static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

	/** Legt die Tastenbelegungen an (Mod-Konstruktor); eingetragen werden sie über {@link #register}. */
	static void create() {
		//? if >=1.21.9
		/*CATEGORY = KeyMapping.Category.register(CubiqClient.id("main"));*/
		menu = new KeyMapping("key.cubiqclient.menu", KEYBOARD, InputConstants.KEY_RSHIFT, CATEGORY);
		// Zoom liegt auf V: C ist ab Minecraft 1.12 mit "Hotbar speichern" belegt.
		zoom = new KeyMapping("key.cubiqclient.zoom", KEYBOARD, dev.pwiepixel.cubiqclient.compat.Keys.code("key.keyboard.v"), CATEGORY);
		// Standardmäßig unbelegt – Fullbright lässt sich auch im Menü schalten.
		fullbright = new KeyMapping("key.cubiqclient.fullbright", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		gammaCycle = new KeyMapping("key.cubiqclient.gammaCycle", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		autoRefill = new KeyMapping("key.cubiqclient.autoRefill", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		freelook = new KeyMapping("key.cubiqclient.freelook", KEYBOARD, InputConstants.KEY_LALT, CATEGORY);
		// Standardmäßig unbelegt – Profile lassen sich auch im Menü wechseln.
		hudProfile = new KeyMapping("key.cubiqclient.hudProfile", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		emoteWheel = new KeyMapping("key.cubiqclient.emoteWheel", KEYBOARD, dev.pwiepixel.cubiqclient.compat.Keys.code("key.keyboard.r"), CATEGORY);
		redstoneOverlay = new KeyMapping("key.cubiqclient.redstoneOverlay", KEYBOARD, InputConstants.KEY_F6, CATEGORY);
		saveClip = new KeyMapping("key.cubiqclient.saveClip", KEYBOARD, InputConstants.KEY_F9, CATEGORY);
		toggleRecording = new KeyMapping("key.cubiqclient.toggleRecording", KEYBOARD, InputConstants.KEY_F10, CATEGORY);
		wardrobe = new KeyMapping("key.cubiqclient.wardrobe", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		worldMap = new KeyMapping("key.cubiqclient.worldMap", KEYBOARD, InputConstants.KEY_M, CATEGORY);
		social = new KeyMapping("key.cubiqclient.social", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		quickReply = new KeyMapping("key.cubiqclient.quickReply", KEYBOARD, InputConstants.KEY_Y, CATEGORY);
		panorama = new KeyMapping("key.cubiqclient.panorama", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		notes = new KeyMapping("key.cubiqclient.notes", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		cinematicAdd = new KeyMapping("key.cubiqclient.cinematicAdd", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		cinematicPlay = new KeyMapping("key.cubiqclient.cinematicPlay", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		cinematicRemove = new KeyMapping("key.cubiqclient.cinematicRemove", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		cinematicClear = new KeyMapping("key.cubiqclient.cinematicClear", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
		cinematicFreecam = new KeyMapping("key.cubiqclient.cinematicFreecam", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY);
	}

	/**
	 * Stellt den alten Zoom-Standard C auf V um – aber nur, wenn die Taste noch auf C liegt,
	 * also nie geändert wurde.
	 * @return true, wenn umgestellt wurde
	 */
	public static boolean migrateZoomKey() {
		if (zoom == null) return false;
		if (boundKey(zoom) != dev.pwiepixel.cubiqclient.compat.Keys.code("key.keyboard.c")) return false;
		zoom.setKey(InputConstants.getKey("key.keyboard.v"));
		KeyMapping.resetMapping();
		return true;
	}

	/** Forge-Event (Mod-Bus): Tasten in die Steuerungs-Optionen eintragen. */
	static void register(RegisterKeyMappingsEvent event) {
		if (menu == null) return;
		event.register(menu);
		event.register(zoom);
		event.register(fullbright);
		event.register(gammaCycle);
		event.register(autoRefill);
		event.register(freelook);
		event.register(hudProfile);
		event.register(emoteWheel);
		event.register(redstoneOverlay);
		event.register(saveClip);
		event.register(toggleRecording);
		event.register(wardrobe);
		event.register(worldMap);
		event.register(social);
		event.register(quickReply);
		event.register(panorama);
		event.register(notes);
		event.register(cinematicAdd);
		event.register(cinematicPlay);
		event.register(cinematicRemove);
		event.register(cinematicClear);
		event.register(cinematicFreecam);
	}

	/**
	 * Einmalig: liegt die Weltkarten-Taste noch auf M und nutzt eine andere Belegung (z. B. eine andere Karten-Mod)
	 * ebenfalls M, wird die Cubiq-Taste freigegeben (im Cubiq-Menü/in den Steuerungen neu belegbar).
	 * @return true, wenn freigegeben wurde
	 */
	public static boolean resolveWorldMapConflict() {
		if (worldMap == null) return false;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc == null || mc.options == null) return false;
		java.util.List<String> others = new java.util.ArrayList<>();
		for (KeyMapping k : mc.options.keyMappings) {
			if (k != worldMap) others.add(k.saveString());
		}
		if (!dev.pwiepixel.cubiqclient.core.config.KeyDefaults.conflicts(worldMap.saveString(), "key.keyboard.m", others)) return false;
		worldMap.setKey(InputConstants.UNKNOWN);
		KeyMapping.resetMapping();
		mc.options.save();
		return true;
	}

	/** Aktuell belegte Taste (Code) einer Tastenbelegung. */
	public static int boundKey(KeyMapping mapping) {
		return mapping.getKey().getValue();
	}

	/**
	 * Verbindung einer Modul-Tasteneinstellung (Cubiq-Menü) mit einer Vanilla-Tastenbelegung: gelesen und
	 * geschrieben wird die Belegung selbst (gespeichert in options.txt). Die Belegung wird erst beim
	 * Aufruf geholt, weil sie je nach Loader erst später angelegt wird.
	 */
	public static dev.pwiepixel.cubiqclient.core.module.KeySetting.Link link(final java.util.function.Supplier<KeyMapping> mapping) {
		return new dev.pwiepixel.cubiqclient.core.module.KeySetting.Link() {
			@Override
			public String get() {
				KeyMapping m = mapping.get();
				return m == null ? dev.pwiepixel.cubiqclient.core.module.KeySetting.NONE : m.saveString();
			}

			@Override
			public void set(String keyName) {
				KeyMapping m = mapping.get();
				if (m == null) return;
				m.setKey(InputConstants.getKey(keyName));
				KeyMapping.resetMapping();
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				if (mc != null && mc.options != null) mc.options.save();
			}
		};
	}
}
