package dev.pwiepixel.cubiqclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pwiepixel.cubiqclient.compat.Keys;
import net.minecraft.client.KeyMapping;
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/** Tastenbelegungen – erscheinen in den Steuerungs-Optionen unter "Cubiq Client". */
public final class CubiqKeys {
	// Kategorie: bis 1.21.8 ein Übersetzungsschlüssel, ab 1.21.9 ein registrierter Datensatz
	// (Anzeigename dann über "key.category.cubiqclient.main").
	//? if >=1.21.9 {
	/*public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CubiqClient.id("main"));
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
	/** Öffnet den Sozial-Bildschirm (standardmäßig unbelegt). */
	public static KeyMapping social;
	/** Schnellantwort/Beitreten zum neuesten Sozial-Toast (Y – in keiner Vanilla-Version belegt). */
	public static KeyMapping quickReply;
	/** Öffnet die Weltkarte (M – in keiner Vanilla-Version belegt; bei Doppelbelegung einmalig freigegeben). */
	public static KeyMapping worldMap;
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

	static void register() {
		menu = register(new KeyMapping("key.cubiqclient.menu", KEYBOARD, Keys.KEY_RSHIFT, CATEGORY));
		// Zoom liegt auf V: C ist ab Minecraft 1.12 mit "Hotbar speichern" belegt.
		zoom = register(new KeyMapping("key.cubiqclient.zoom", KEYBOARD, Keys.code("key.keyboard.v"), CATEGORY));
		// Standardmäßig unbelegt – Fullbright lässt sich auch im Menü schalten.
		fullbright = register(new KeyMapping("key.cubiqclient.fullbright", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		// Standardmäßig unbelegt – Gamma/Nachlegen lassen sich auch im Cubiq-Menü schalten.
		gammaCycle = register(new KeyMapping("key.cubiqclient.gammaCycle", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		autoRefill = register(new KeyMapping("key.cubiqclient.autoRefill", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		freelook = register(new KeyMapping("key.cubiqclient.freelook", KEYBOARD, Keys.KEY_LALT, CATEGORY));
		// Standardmäßig unbelegt – Profile lassen sich auch im Menü wechseln.
		hudProfile = register(new KeyMapping("key.cubiqclient.hudProfile", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		emoteWheel = register(new KeyMapping("key.cubiqclient.emoteWheel", KEYBOARD, Keys.code("key.keyboard.r"), CATEGORY));
		redstoneOverlay = register(new KeyMapping("key.cubiqclient.redstoneOverlay", KEYBOARD, Keys.code("key.keyboard.f6"), CATEGORY));
		saveClip = register(new KeyMapping("key.cubiqclient.saveClip", KEYBOARD, Keys.code("key.keyboard.f9"), CATEGORY));
		toggleRecording = register(new KeyMapping("key.cubiqclient.toggleRecording", KEYBOARD, Keys.code("key.keyboard.f10"), CATEGORY));
		wardrobe = register(new KeyMapping("key.cubiqclient.wardrobe", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		worldMap = register(new KeyMapping("key.cubiqclient.worldMap", KEYBOARD, Keys.code("key.keyboard.m"), CATEGORY));
		social = register(new KeyMapping("key.cubiqclient.social", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		quickReply = register(new KeyMapping("key.cubiqclient.quickReply", KEYBOARD, Keys.code("key.keyboard.y"), CATEGORY));
		panorama = register(new KeyMapping("key.cubiqclient.panorama", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		notes = register(new KeyMapping("key.cubiqclient.notes", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		cinematicAdd = register(new KeyMapping("key.cubiqclient.cinematicAdd", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		cinematicPlay = register(new KeyMapping("key.cubiqclient.cinematicPlay", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		cinematicRemove = register(new KeyMapping("key.cubiqclient.cinematicRemove", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		cinematicClear = register(new KeyMapping("key.cubiqclient.cinematicClear", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		cinematicFreecam = register(new KeyMapping("key.cubiqclient.cinematicFreecam", KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/**
	 * Stellt den alten Zoom-Standard C auf V um – aber nur, wenn die Taste nie geändert wurde
	 * (sie liegt also noch auf C). Danach werden die Steuerungen gespeichert.
	 * @return true, wenn umgestellt wurde
	 */
	public static boolean migrateZoomKey() {
		if (zoom == null) return false;
		if (boundKey(zoom) != Keys.code("key.keyboard.c")) return false;
		zoom.setKey(InputConstants.getKey("key.keyboard.v"));
		KeyMapping.resetMapping();
		return true;
	}

	/**
	 * Verbindung einer Modul-Tasteneinstellung (Cubiq-Menü) mit einer Vanilla-Tastenbelegung:
	 * gelesen/geschrieben wird die Belegung selbst, gespeichert in Minecrafts options.txt.
	 */
	public static dev.pwiepixel.cubiqclient.core.module.KeySetting.Link link(final KeyMapping mapping) {
		return new dev.pwiepixel.cubiqclient.core.module.KeySetting.Link() {
			@Override
			public String get() {
				return mapping.saveString();
			}

			@Override
			public void set(String keyName) {
				mapping.setKey(InputConstants.getKey(keyName));
				KeyMapping.resetMapping();
				net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
				if (mc != null && mc.options != null) mc.options.save();
			}
		};
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
		//? if >=26.1 {
		/*return KeyMappingHelper.getBoundKeyOf(mapping).getValue();
		*///?} else
		return KeyBindingHelper.getBoundKeyOf(mapping).getValue();
	}

	private static KeyMapping register(KeyMapping mapping) {
		//? if >=26.1 {
		/*return KeyMappingHelper.registerKeyMapping(mapping);
		*///?} else
		return KeyBindingHelper.registerKeyBinding(mapping);
	}
}
