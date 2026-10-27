package dev.pwiepixel.cubiqclient.core.ui.title;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.intro.IntroGate;
import dev.pwiepixel.cubiqclient.core.menus.MenuStyle;
import dev.pwiepixel.cubiqclient.core.module.NewSince;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.skin.PlayerLook;
import dev.pwiepixel.cubiqclient.core.skin.SkinModel;
import dev.pwiepixel.cubiqclient.core.skin.SkinModelSpec;
import dev.pwiepixel.cubiqclient.core.ui.Anim;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.CubiqMark;
import dev.pwiepixel.cubiqclient.core.ui.FadeCanvas;
import dev.pwiepixel.cubiqclient.core.ui.Hits;
import dev.pwiepixel.cubiqclient.core.ui.Icons;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.PixelFont;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import dev.pwiepixel.cubiqclient.core.ui.UiScreen;
import dev.pwiepixel.cubiqclient.core.ui.menu.NewBadge;
import dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook;
import dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenPalette;
import dev.pwiepixel.cubiqclient.core.ui.halloween.NightScene;
import dev.pwiepixel.cubiqclient.core.ui.halloween.Sprites;
import dev.pwiepixel.cubiqclient.core.ui.fox.Ember;
import dev.pwiepixel.cubiqclient.core.ui.fox.FoxScene;
import dev.pwiepixel.cubiqclient.core.ui.winter.Frost;
import dev.pwiepixel.cubiqclient.core.ui.winter.WinterScene;

import java.util.ArrayList;
import java.util.List;

/**
 * Cubiq-Startbildschirm, versionsunabhängig, im Stil des Launchers: ruhige Würfel-Szene als Hintergrund
 * ({@link CubeScene}), oben das Cubiq-Zeichen mit dem Schriftzug, darunter flache Knöpfe – „Einzelspieler“ in der
 * Akzentfarbe, die übrigen als ruhige Flächen, die beim Überfahren aufhellen. Per Tastatur (Tab/Pfeile, Enter) wählbar,
 * die Auswahl zeigt einen Fokusrahmen.
 *
 * <p>Links steht die eigene Spielerfigur auf einem sich langsam drehenden Würfel-Sockel (ziehen dreht ihn), darunter
 * Name und „Garderobe“; rechts eine Leiste mit Symbolen (Konten, Freunde, Clips/Bilder, Cubiq-Einstellungen). Bereiche,
 * die es noch nicht gibt, melden „Kommt bald“. In schmalen Fenstern wird die Leiste zu Symbolen (bzw. einer Zeile oben
 * rechts) und die Figur entfällt. Polarlicht zeigt dasselbe in Nachtblau vor der Polarlicht-Szene (Schnee auf dem Zeichen,
 * der Figur-Sockel als verschneiter Würfel); Fuchsbau und Halloween behalten ihre Szenen und Laternen.
 */
public final class TitleUi extends UiScreen {
	/** Gemeinsamer Zeitbezug – die Animation läuft beim Zurückkehren weiter statt neu zu starten. */
	private static final long EPOCH = System.nanoTime();
	private static final int BTN_W = 200;
	private static final int BTN_H = 20;
	private static final int GAP = 5;
	private static final int HALF = (BTN_W - 6) / 2;
	/** Ein- und Ausblenden der Hervorhebung beim Überfahren (Sekunden). */
	private static final float HOVER_IN = 0.09f;
	private static final float HOVER_OUT = 0.14f;
	/** Verzögerung zwischen Drücken und Aktion – so ist das Aufblitzen sichtbar. */
	private static final float PRESS_DELAY = 0.07f;
	/** Link unten rechts (Übersetzung beim Erstellen – der Startbildschirm wird je Öffnen neu gebaut). */
	private final String classic;

	/** Ein Knopf (Mitte oder Seitenleiste). */
	private static final class Button {
		/** Feste ID für Selbsttest/Verknüpfungen ("singleplayer", "cubiqMenu" …). */
		final String id;
		final String label;
		final Runnable action;
		/** Symbol (Seitenleiste) oder null. */
		String icon;
		/** „NEU“-Eintrag des Bereichs ({@link NewSince}) oder null. */
		String newId;
		int x;
		int y;
		int w;
		int h = BTN_H;
		/** Hervorhebung (0..1) – folgt Maus und Tastatur-Auswahl. */
		float hover;
		float flash;

		Button(String id, Runnable action) {
			this.id = id;
			this.label = I18n.tr("title." + id);
			this.action = action;
		}
	}

	/** Text wird zuletzt gezeichnet: Minecraft zeichnet bei jedem Text den Puffer – so nur einmal je Bild. */
	private static final class Label {
		final String text;
		final int x;
		final int y;
		final int color;
		final boolean shadow;

		Label(String text, int x, int y, int color, boolean shadow) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.color = color;
			this.shadow = shadow;
		}
	}

	/** Darstellung der Seitenleiste. */
	static final int SIDE_FULL = 0;
	static final int SIDE_ICONS = 1;
	static final int SIDE_ROW = 2;
	/** Kamera-Neigung der Figur und passende Stauchung des Sockels. */
	private static final float FIGURE_PITCH = 14f;
	private static final float TABLE_SQUASH = 0.26f;

	private final List<Label> labels = new ArrayList<Label>();
	private final TitleHost host;
	/** Seitenleiste rechts (Knöpfe mit Symbol). */
	private final List<Button> side = new ArrayList<Button>();
	private final Turntable turntable = new Turntable();
	private final SkinModel model = new SkinModel();
	private final SkinModelSpec spec = new SkinModelSpec();
	private final String accountLabel;
	private final String rotateHint;
	private int sideMode = SIDE_FULL;
	/** Garderobe-Knopf unter der Figur (w = 0: ausgeblendet). */
	private int accX;
	private int accY;
	private int accW;
	private float accHover;
	private float accFlash;
	/** Figur sichtbar? (Fläche für das Ziehen) */
	private boolean figureShown;
	private float headYaw;
	private float headPitch;
	private String toast;
	private float toastLeft;
	private boolean introChecked;
	private float toastTotal = 2.4f;
	private float modelMicros;
	private int modelFrames;
	private final CubeScene scene = new CubeScene();
	private final List<Button> buttons = new ArrayList<Button>();
	private final int[][] reserved = new int[4][4];
	/**
	 * Tastatur-Auswahl: 0.. Mitte, dann Seitenleiste, dann Garderobe-Knopf, zuletzt der Link ({@link #linkIndex()}),
	 * -1 = keine.
	 */
	private int focus = -1;
	private int pending = -1;
	private float pendingIn;
	private float linkLit;
	private boolean sceneEnabled = true;
	private float uiMicros;
	private float frameMs;
	private int frames;

	public TitleUi(TitleHost host) {
		this.host = host;
		I18n.refresh();
		this.classic = I18n.tr("title.classic");
		this.accountLabel = I18n.tr("title.wardrobe");
		this.rotateHint = I18n.tr("title.rotateHint");
		scene.setSimple(host.simpleAnimation());
		final TitleHost h = host;
		sideButton("accounts", "profile", NewSince.MENU_ACCOUNTS, new Area() {
			@Override
			public boolean open() {
				return h.openAccounts();
			}
		});
		sideButton("friends", "friends", NewSince.MENU_SOCIAL, new Area() {
			@Override
			public boolean open() {
				return h.openFriends();
			}
		});
		sideButton("clips", "image", NewSince.MENU_CLIPS, new Area() {
			@Override
			public boolean open() {
				return h.openClips();
			}
		});
		sideButton("cubiqSettings", "gear", null, new Area() {
			@Override
			public boolean open() {
				h.cubiqMenu();
				return true;
			}
		});
		buttons.add(new Button("singleplayer", new Runnable() {
			@Override
			public void run() {
				h.singleplayer();
			}
		}));
		buttons.add(new Button("multiplayer", new Runnable() {
			@Override
			public void run() {
				h.multiplayer();
			}
		}));
		buttons.add(new Button("options", new Runnable() {
			@Override
			public void run() {
				h.options();
			}
		}));
		buttons.add(new Button("cubiqMenu", new Runnable() {
			@Override
			public void run() {
				h.cubiqMenu();
			}
		}));
		if (host.hasMods()) {
			buttons.add(new Button("mods", new Runnable() {
				@Override
				public void run() {
					h.mods();
				}
			}));
		}
		buttons.add(new Button("quit", new Runnable() {
			@Override
			public void run() {
				h.quit();
			}
		}));
	}

	/** Ein Bereich der Seitenleiste; false = gibt es noch nicht (dann „Kommt bald“). */
	private interface Area {
		boolean open();
	}

	private void sideButton(String id, String icon, final String newId, final Area area) {
		final String[] label = new String[1];
		Button b = new Button(id, new Runnable() {
			@Override
			public void run() {
				if (area.open()) seen(newId);
				else soon(label[0]);
			}
		});
		label[0] = b.label;
		b.icon = icon;
		b.newId = newId;
		side.add(b);
	}

	/** Bereich neu seit dem letzten Update und noch nie geöffnet? */
	private static boolean isNew(String newId) {
		CubiqModules m = IntroGate.modules();
		return newId != null && m != null && m.clientState.news().isNew(newId);
	}

	/** Bereich geöffnet: „NEU“ fällt weg (auch in der Leiste des Cubiq-Menüs). */
	private static void seen(String newId) {
		CubiqModules m = IntroGate.modules();
		if (newId != null && m != null && m.clientState.news().markSeen(newId)) IntroGate.save(m);
	}

	private int seenOfferNotice = -1;

	/**
	 * Umhang-Angebote von Freunden: im Hintergrund-Takt abfragen, ein neues Angebot kurz melden. Ungesehene Angebote
	 * markieren Garderobe und Freunde mit einem Punkt ({@link #unseenOffers()}).
	 */
	private void offerCheck() {
		dev.pwiepixel.cubiqclient.core.online.CubiqOnline online = dev.pwiepixel.cubiqclient.core.online.CubiqOnline.current();
		if (online == null || !online.online()) return;
		dev.pwiepixel.cubiqclient.core.online.Friends f = online.friends();
		f.want(dev.pwiepixel.cubiqclient.core.online.Friends.Interest.BACKGROUND, false);
		dev.pwiepixel.cubiqclient.core.online.Friends.Snapshot fs = f.snapshot();
		if (seenOfferNotice == -1) seenOfferNotice = fs.offerNotice;
		if (fs.offerNotice != seenOfferNotice) {
			seenOfferNotice = fs.offerNotice;
			if (fs.offerNoticeArgs.length == 2) notice(I18n.tr("wardrobe.share.offerToast", fs.offerNoticeArgs));
		}
	}

	/** Gibt es Umhang-Angebote, die noch niemand angesehen hat? */
	private static boolean unseenOffers() {
		dev.pwiepixel.cubiqclient.core.online.CubiqOnline online = dev.pwiepixel.cubiqclient.core.online.CubiqOnline.current();
		return online != null && online.online() && online.friends().snapshot().unseenOffers > 0;
	}

	/** Einführung beim ersten Start (oder kurze Begrüßung, wenn das Cubiq-Konto sie schon erledigt hat). */
	private void introCheck() {
		String pending = dev.pwiepixel.cubiqclient.core.intro.IntroGate.takeNotice();
		if (pending != null) notice(pending);
		if (introChecked) return;
		dev.pwiepixel.cubiqclient.core.intro.IntroGate.Decision d =
				dev.pwiepixel.cubiqclient.core.intro.IntroGate.decide(System.currentTimeMillis());
		if (d == dev.pwiepixel.cubiqclient.core.intro.IntroGate.Decision.WAIT) return;
		introChecked = true;
		if (d == dev.pwiepixel.cubiqclient.core.intro.IntroGate.Decision.SHOW && host.openIntro()) return;
		if (dev.pwiepixel.cubiqclient.core.intro.IntroGate.takeWelcome()) notice(I18n.tr("intro.welcomeBack"));
	}

	/** Kurzer Hinweis über der Fußzeile (z. B. nach der Einführung). */
	public void notice(String text) {
		toast = text;
		toastLeft = 4f;
		toastTotal = 4f;
		host.narrate(text);
	}

	/** Kurzer Hinweis „Kommt bald: …“ über der Fußzeile. */
	void soon(String what) {
		toast = I18n.tr("title.soon", what);
		toastLeft = 2.4f;
		toastTotal = 2.4f;
		host.narrate(toast);
	}

	// --- Für den Selbsttest ---

	/** Wählt einen Knopf wie per Tastatur aus (ohne Vorlesen); -1 = keiner. */
	public void focus(int index) {
		focus = index < -1 || index > linkIndex() ? -1 : index;
	}

	/** Lage eines Knopfs {x, y, w, h} im letzten Bild oder null (Selbsttest: echte Klicks). */
	public int[] buttonRect(String label) {
		if (frames == 0) return null;
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			if (b.id.equals(label) || b.label.equals(label)) return new int[]{b.x, b.y, b.w, BTN_H};
		}
		for (Button b : side) {
			if (b.id.equals(label) || b.label.equals(label)) return new int[]{b.x, b.y, b.w, b.h};
		}
		if (("wardrobe".equals(label) || accountLabel.equals(label)) && accW > 0) return new int[]{accX, accY, accW, 16};
		return null;
	}

	/** Darstellung der Seitenleiste im letzten Bild ({@link #SIDE_FULL}, {@link #SIDE_ICONS}, {@link #SIDE_ROW}). */
	public int sideMode() {
		return sideMode;
	}

	/** Dreht die Figur auf einen festen Winkel (Selbsttest: Screenshot von vorn/hinten). */
	public void setFigureAngle(float degrees) {
		turntable.setAngle(degrees);
	}

	/** Wurde die Spielerfigur im letzten Bild gezeichnet? */
	public boolean figureShown() {
		return figureShown;
	}

	/** Mittlere CPU-Zeit von Figur und Sockel je Bild in Mikrosekunden. */
	public float modelMicros() {
		return modelMicros;
	}

	/** Gezeichnete Flächen der Figur im letzten Bild. */
	public int modelFaces() {
		return model.lastFaceCount();
	}

	/** Aktueller Hinweis (Selbsttest) oder null. */
	public String toast() {
		return toastLeft > 0 ? toast : null;
	}

	/** Hintergrund-Animation an/aus (Selbsttest: Kosten der Animation messen). */
	public void setSceneEnabled(boolean enabled) {
		sceneEnabled = enabled;
	}

	/** Mittlere CPU-Zeit des Hintergrunds je Bild in Mikrosekunden. */
	public float sceneMicros() {
		return scene.averageMicros();
	}

	/** Mittlere CPU-Zeit des ganzen Bildschirms je Bild in Mikrosekunden. */
	public float frameMicros() {
		return uiMicros;
	}

	/** Mittlerer Abstand zwischen zwei Bildern in Millisekunden (gesamte Bildzeit des Spiels). */
	public float frameMillis() {
		return frameMs;
	}

	public boolean sparseScene() {
		return scene.isSimple();
	}

	// --- Zeichnen ---

	@Override
	protected void draw(Canvas raw, int width, int height, int mouseX, int mouseY, float dt) {
		long start = System.nanoTime();
		introCheck();
		offerCheck();
		Theme t = Theme.get();
		boolean seasonal = t.halloween || t.fox;
		// Eigene Einstellung „Animierter Hintergrund“ – „Reduziert“ im Launcher beruhigt zusätzlich.
		boolean animated = MenuStyle.animated(host.animated());
		float time = animated ? (System.nanoTime() - EPOCH) / 1_000_000_000f : 1.35f;
		boolean compact = height < 300;
		lastHeight = height;

		// Maße: Schriftzug, Untertitel, vier Knopfzeilen – als Block senkrecht zentriert.
		int big = compact ? 5 : (height >= 440 ? 9 : 8);
		int small = compact ? 2 : 3;
		int logoH = PixelFont.HEIGHT * big;
		int subH = PixelFont.HEIGHT * small;
		int gapA = compact ? 6 : 10;
		int gapB = compact ? 12 : 20;
		int buttonsH = 4 * BTN_H + 3 * GAP;
		int blockH = logoH + gapA + subH + gapB + buttonsH;
		int top = Math.max(compact ? 8 : 14, (height - 14 - blockH) / 2);
		int colX = (width - BTN_W) / 2;
		int rowsY = top + logoH + gapA + subH + gapB;
		layout(colX, rowsY);

		int logoHalf = Math.max(BTN_W / 2 + 22, logoWidth(t, big) / 2 + 8);
		reserved[0][0] = Math.min(colX - 22, width / 2 - logoHalf);
		reserved[0][1] = top - 8;
		reserved[0][2] = Math.max(colX + BTN_W + 22, width / 2 + logoHalf);
		reserved[0][3] = rowsY + buttonsH + 6;
		reserved[1][0] = 0;
		reserved[1][1] = height - 14;
		reserved[1][2] = width;
		reserved[1][3] = height;
		Canvas bg = FadeCanvas.unwrap(raw);
		int[] panel = leftPanel(bg, width, height, colX);
		layoutSide(bg, width, height, colX);
		int[] sb = sideBounds();
		setReserved(2, panel);
		setReserved(3, sb);

		if (t.halloween) {
			// Halloween: die Mondnacht (Standbild ohne Animation), leichter Schleier zu den Rändern wie .veil.
			if (sceneEnabled) NightScene.shared().draw(bg, width, height, animated);
			else bg.fill(0, 0, width, height, HalloweenPalette.NIGHT);
		} else if (t.winter) {
			if (sceneEnabled) WinterScene.shared().draw(bg, width, height, animated);
			else bg.fill(0, 0, width, height, t.background);
		} else if (t.fox) {
			if (sceneEnabled) FoxScene.shared().draw(bg, width, height, animated);
			else bg.fill(0, 0, width, height, t.background);
		} else if (sceneEnabled) {
			scene.draw(bg, width, height, time, reserved);
		} else {
			bg.fill(0, 0, width, height, t.deep);
		}
		int plate = plateColor(t);
		readingPlate(bg, colX - 26, top - 6, BTN_W + 52, rowsY + buttonsH + 6 - (top - 6), plate);
		if (panel != null) readingPlate(bg, panel[0], panel[1], panel[2] - panel[0], panel[3] - panel[1], plate);
		if (sb != null && sideMode != SIDE_ROW) readingPlate(bg, sb[0], sb[1], sb[2] - sb[0], sb[3] - sb[1], plate);
		// Polarlicht: die Fußzeile steht auf dem Wolkenmeer – ein Schleier hält sie lesbar.
		if (t.winter) bg.fill(0, height - 14, width, height, t.light ? 0x70FFFFFF : 0x90070C18);
		// Fuchsbau: die Fußzeile steht auf den Grasblöcken – ein warmer Schleier hält sie lesbar.
		if (t.fox) bg.fill(0, height - 14, width, height, t.light ? 0x70FFF8F0 : 0x90170D14);
		if (t.halloween) bg.fill(0, height - 14, width, height, 0x9007060C);

		Canvas c = FadeCanvas.of(raw, alpha());
		update(dt, mouseX, mouseY);
		turntable.tick(dt, animated);
		if (t.halloween) spider(c, width, top, time, animated);
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			int dy = b.flash > 0.45f ? 1 : 0;
			String s = c.textWidth(b.label) <= b.w - 10 ? b.label : c.clip(b.label, b.w - 10);
			int color;
			if (seasonal) {
				Paint.accentFace(c, b.x, b.y + dy, b.w, BTN_H, b.hover, b.flash);
				color = Paint.accentFaceText(b.hover);
			} else {
				// „Einzelspieler“ ist der Hauptknopf (wie „Spielen“ im Launcher), die übrigen ruhige Flächen.
				boolean primary = i == 0;
				if (primary) Paint.primaryFace(c, b.x, b.y + dy, b.w, BTN_H, b.hover);
				else Paint.secondaryFace(c, b.x, b.y + dy, b.w, BTN_H, b.hover);
				press(c, b.x, b.y + dy, b.w, BTN_H, b.flash);
				if (focus == i) Paint.focusRing(c, b.x, b.y + dy, b.w, BTN_H);
				color = primary ? t.faceTextOn : Paint.secondaryText(b.hover);
			}
			labels.add(new Label(s, b.x + (b.w - c.textWidth(s)) / 2, b.y + dy + (BTN_H - 8) / 2, color, seasonal && b.hover < 0.35f));
			final int index = i;
			hits.add(b.x, b.y, b.w, BTN_H, new Runnable() {
				@Override
				public void run() {
					activate(index);
				}
			});
		}
		long modelStart = System.nanoTime();
		if (panel != null) drawLeft(c, t, panel, mouseX, mouseY, time, animated, dt);
		float micros = (System.nanoTime() - modelStart) / 1000f;
		modelFrames++;
		modelMicros = modelFrames == 1 ? micros : modelMicros + (micros - modelMicros) * 0.05f;
		drawSide(c, t, width, mouseX, mouseY);
		logo(c, t, width, top, big, small, logoH, gapA, time, animated);
		footer(c, t, width, height, mouseX, mouseY, dt);
		for (int i = 0; i < labels.size(); i++) {
			Label label = labels.get(i);
			c.text(label.text, label.x, label.y, label.color, label.shadow);
		}
		labels.clear();
		for (int i = 0; i < tooltips.size(); i++) {
			Label tip = tooltips.get(i);
			tooltip(c, t, tip.text, tip.x, tip.y, tip.color, tip.shadow ? 1 : 0, width);
		}
		tooltips.clear();
		drawToast(c, t, width, height, dt);

		if (pending >= 0) {
			pendingIn -= dt;
			if (pendingIn <= 0) {
				int run = pending;
				pending = -1;
				run(run);
			}
		}
		micros = (System.nanoTime() - start) / 1000f;
		frames++;
		uiMicros = frames == 1 ? micros : uiMicros + (micros - uiMicros) * 0.05f;
		frameMs = frames == 1 ? dt * 1000f : frameMs + (dt * 1000f - frameMs) * 0.05f;
	}

	/** Kurzes Aufhellen beim Drücken (vor der Aktion). */
	private static void press(Canvas c, int x, int y, int w, int h, float flash) {
		if (flash <= 0.02f) return;
		Paint.roundRect(c, x + 1, y + 1, w - 2, h - 2, Paint.radius(w, h) - 1, ColorMath.withAlpha(0xFFFFFFFF, Math.round(90 * flash)));
	}

	private void setReserved(int index, int[] r) {
		int[] out = reserved[index];
		if (r == null) {
			out[0] = 0;
			out[1] = 0;
			out[2] = 0;
			out[3] = 0;
			return;
		}
		out[0] = r[0];
		out[1] = r[1];
		out[2] = r[2];
		out[3] = r[3];
	}

	// --- Linke Seite: Figur auf dem Sockel, Name, Garderobe ---

	/** Maße der linken Seite in diesem Bild (Figur-Maßstab ×100, Fußpunkt, Mitte, Namenszeile). */
	private int panelScale100;
	private int panelFeetY;
	private int panelCx;
	private int panelNameY;

	/**
	 * Legt die linke Seite fest: {x1, y1, x2, y2} oder null (zu schmal). Setzt Figurgröße, Fußpunkt, Namens- und
	 * Knopfzeile.
	 */
	private int[] leftPanel(Canvas c, int width, int height, int colX) {
		int x1 = 6;
		int x2 = colX - 30;
		int lw = x2 - x1;
		accW = 0;
		figureShown = false;
		if (lw < 56) return null;
		int avail = height - 16 - 8;
		float s = Math.min(Math.min(lw / 26f, (avail - 36) / 41f), 5f);
		boolean figure = s >= 1.5f;
		int figureH = figure ? Math.round(38.5f * s) : 0;
		int total = figureH + 6 + 9 + 5 + 16;
		int y0 = Math.max(8, (height - 14 - total) / 2);
		panelCx = x1 + lw / 2;
		panelScale100 = figure ? Math.round(s * 100f) : 0;
		panelFeetY = y0 + Math.round(33f * s);
		int radius = Math.round(11f * s);
		int tableBottom = panelFeetY + Math.round(radius * TABLE_SQUASH) + Math.round(radius * 0.22f) + 2;
		panelNameY = figure ? tableBottom + 6 : y0;
		int bw = Math.min(lw, Math.max(64, c.textWidth(accountLabel) + 26));
		accW = bw;
		accX = panelCx - bw / 2;
		accY = panelNameY + 9 + 5;
		figureShown = figure;
		int half = Math.max(radius + 10, bw / 2 + 8);
		int left = Math.max(x1, panelCx - half);
		int right = Math.min(x2, panelCx + half);
		return new int[]{left, figure ? y0 - 6 : y0 - 4, right, accY + 16 + 6};
	}

	private void drawLeft(Canvas c, Theme t, int[] panel, int mx, int my, float time, boolean animated, float dt) {
		PlayerLook look = host.look();
		String name = look != null ? look.name : host.playerName();
		if (figureShown) {
			float s = panelScale100 / 100f;
			int radius = Math.round(11f * s);
			int px = Math.max(1, (int) (s / 2f));
			turntable.drawBack(c, panelCx, panelFeetY, radius, TABLE_SQUASH, px, time);
			if (look != null && look.skin != null && c.images()) {
				look.applyTo(spec);
				spec.yaw = turntable.angle();
				spec.pitch = FIGURE_PITCH;
				spec.idleTime = animated ? time : 0f;
				// Kopf folgt der Maus, solange die Figur ungefähr nach vorn schaut.
				float body = ((turntable.angle() + 180f) % 360f) - 180f;
				float wantYaw = 0f;
				float wantPitch = 0f;
				float headY = panelFeetY - 28f * s;
				if (Math.abs(body) < 75f && mx >= 0) {
					float toMouse = (float) Math.toDegrees(Math.atan2(mx - panelCx, 60f * s));
					wantYaw = Math.max(-30f, Math.min(30f, toMouse - body));
					wantPitch = Math.max(-15f, Math.min(15f, (float) Math.toDegrees(Math.atan2(my - headY, 90f * s))));
				}
				headYaw = Anim.approach(headYaw, wantYaw, dt, 0.12f);
				headPitch = Anim.approach(headPitch, wantPitch, dt, 0.12f);
				spec.headYaw = headYaw;
				spec.headPitch = headPitch;
				spec.tint = ColorMath.withAlpha(0xFFFFFFFF, Math.round(255 * alpha()));
				model.draw(FadeCanvas.unwrap(c), panelCx, panelFeetY, s, spec);
			} else {
				// Keine Texturen in dieser Version: Umriss der Figur aus Rechtecken.
				silhouette(c, panelCx, panelFeetY, s);
			}
			turntable.drawFront(c, panelCx, panelFeetY, radius, TABLE_SQUASH, px, time);
			int fh = Math.round(34f * s);
			int fw = Math.round(24f * s);
			hits.addDrag(panelCx - fw / 2, panelFeetY - fh, fw, fh + Math.round(radius * TABLE_SQUASH) + 4, new Hits.Drag() {
				@Override
				public void to(double x, double y) {
					if (!turntable.dragging()) turntable.grab(x);
					else turntable.drag(x);
				}
			});
			// Hinweis „Ziehen zum Drehen“ beim Überfahren – nur wenn er ganz hinpasst.
			int hintW = c.textWidth(rotateHint);
			if (!turntable.dragging() && hintW <= panel[2] - panel[0] + 16 && inside(mx, my, panelCx - fw / 2, panelFeetY - fh, fw, fh)) {
				labels.add(new Label(rotateHint, panelCx - hintW / 2, Math.max(1, panel[1] - 4), dimText(t), false));
			}
		}
		// Name und Garderobe-Knopf
		String n = c.clip(name == null ? "" : name, Math.max(10, accW + 20));
		int nw = c.textWidth(n);
		boolean seasonal = t.halloween || t.fox;
		labels.add(new Label(n, panelCx - nw / 2, panelNameY, t.text, seasonal));
		final int focusIndex = buttons.size() + side.size();
		boolean hover = inside(mx, my, accX, accY, accW, 16);
		boolean on = hover || focus == focusIndex || pending == focusIndex;
		int dy = accFlash > 0.45f ? 1 : 0;
		if (seasonal) {
			Paint.panel(c, accX, accY + dy, accW, 16, on ? t.surfaceHover : t.surfaceHigh, on ? Paint.hoverEdge(t) : t.border);
			if (on) Paint.lineH(c, accX + 3, accX + accW - 3, accY + dy + 13, t.accentText, 0f);
		} else {
			Paint.secondaryFace(c, accX, accY + dy, accW, 16, accHover);
			if (focus == focusIndex) Paint.focusRing(c, accX, accY + dy, accW, 16);
		}
		Icons.draw(c, "shirt", accX + 5, accY + dy + 4, 1, on ? t.accentText : t.textDim);
		if (isNew(NewSince.MENU_WARDROBE) || isNew(NewSince.WARDROBE_COSMETICS) || isNew(NewSince.WARDROBE_COMPANION)
				|| unseenOffers()) NewBadge.dot(c, accX + accW - 7, accY + dy + 2);
		String lbl = c.clip(accountLabel, accW - 20);
		labels.add(new Label(lbl, accX + 16 + (accW - 18 - c.textWidth(lbl)) / 2, accY + dy + 4, seasonal ? t.text : Paint.secondaryText(accHover), false));
		press(c, accX, accY, accW, 16, accFlash);
		hits.add(accX, accY, accW, 16, new Runnable() {
			@Override
			public void run() {
				activate(focusIndex);
			}
		});
	}

	/** Ersatz ohne Texturen: Kopf, Körper, Arme, Beine als Flächen. */
	private static void silhouette(Canvas c, int cx, int feet, float s) {
		int u = Math.max(1, Math.round(s));
		int body = 0xFF3A6EA5;
		int skin = 0xFFC69C6D;
		int legs = 0xFF2E3A8C;
		c.fill(cx - 4 * u, feet - 32 * u, cx + 4 * u, feet - 24 * u, skin);
		c.fill(cx - 4 * u, feet - 24 * u, cx + 4 * u, feet - 12 * u, body);
		c.fill(cx - 8 * u, feet - 24 * u, cx - 4 * u, feet - 12 * u, skin);
		c.fill(cx + 4 * u, feet - 24 * u, cx + 8 * u, feet - 12 * u, skin);
		c.fill(cx - 4 * u, feet - 12 * u, cx + 4 * u, feet, legs);
	}

	// --- Rechte Seitenleiste ---

	private int sideX;
	private int sideY;
	private int sideW;
	private int sideH;

	private void layoutSide(Canvas c, int width, int height, int colX) {
		int n = side.size();
		int maxLabel = 0;
		for (Button b : side) maxLabel = Math.max(maxLabel, c.textWidth(b.label));
		int fullW = maxLabel + 34;
		int room = width - (colX + BTN_W + 30) - 8;
		if (room >= fullW + 14) {
			sideMode = SIDE_FULL;
			int gap = 6;
			int h = 20;
			sideW = fullW;
			sideH = n * h + (n - 1) * gap;
			sideX = width - 14 - fullW;
			sideY = Math.max(18, (height - 14 - sideH) / 2);
			for (int i = 0; i < n; i++) {
				Button b = side.get(i);
				b.x = sideX;
				b.y = sideY + i * (h + gap);
				b.w = fullW;
				b.h = h;
			}
		} else if (room >= 36) {
			sideMode = SIDE_ICONS;
			int size = 22;
			int gap = 5;
			sideW = size;
			sideH = n * size + (n - 1) * gap;
			sideX = width - 14 - size;
			sideY = Math.max(18, (height - 14 - sideH) / 2);
			for (int i = 0; i < n; i++) {
				Button b = side.get(i);
				b.x = sideX;
				b.y = sideY + i * (size + gap);
				b.w = size;
				b.h = size;
			}
		} else {
			// Kein Platz neben der Mitte: kleine Symbole in einer Zeile oben rechts.
			sideMode = SIDE_ROW;
			int size = 16;
			int gap = 3;
			sideW = n * size + (n - 1) * gap;
			sideH = size;
			sideX = width - 4 - sideW;
			sideY = 4;
			for (int i = 0; i < n; i++) {
				Button b = side.get(i);
				b.x = sideX + i * (size + gap);
				b.y = sideY;
				b.w = size;
				b.h = size;
			}
		}
	}

	/** Umriss der Seitenleiste {x1, y1, x2, y2}. */
	private int[] sideBounds() {
		if (side.isEmpty()) return null;
		if (sideMode == SIDE_ROW) return new int[]{sideX - 3, sideY - 3, sideX + sideW + 3, sideY + sideH + 3};
		return new int[]{sideX - 8, sideY - 8, sideX + sideW + 8, sideY + sideH + 8};
	}

	private void drawSide(Canvas c, Theme t, int width, int mx, int my) {
		int base = buttons.size();
		boolean seasonal = t.halloween || t.fox;
		for (int i = 0; i < side.size(); i++) {
			Button b = side.get(i);
			int dy = b.flash > 0.45f ? 1 : 0;
			int iconColor;
			int textColor;
			if (seasonal) {
				Paint.accentFace(c, b.x, b.y + dy, b.w, b.h, b.hover, b.flash);
				iconColor = b.hover > 0.5f ? t.faceTextOn : t.faceText;
				textColor = Paint.accentFaceText(b.hover);
			} else {
				Paint.secondaryFace(c, b.x, b.y + dy, b.w, b.h, b.hover);
				press(c, b.x, b.y + dy, b.w, b.h, b.flash);
				if (focus == base + i) Paint.focusRing(c, b.x, b.y + dy, b.w, b.h);
				iconColor = ColorMath.lerp(t.textDim, t.accentText, b.hover);
				textColor = Paint.secondaryText(b.hover);
			}
			boolean isNew = isNew(b.newId)
					|| ((NewSince.MENU_SOCIAL.equals(b.newId) || NewSince.MENU_WARDROBE.equals(b.newId)) && unseenOffers())
					|| (NewSince.MENU_WARDROBE.equals(b.newId) && (isNew(NewSince.WARDROBE_COSMETICS) || isNew(NewSince.WARDROBE_COMPANION)));
			if (sideMode == SIDE_FULL) {
				Icons.draw(c, b.icon, b.x + 7, b.y + dy + (b.h - 8) / 2, 1, iconColor);
				int room = b.w - 24;
				if (isNew) {
					int badgeW = NewBadge.width(c);
					if (room - badgeW - 6 >= c.textWidth(b.label) + 2) {
						NewBadge.draw(c, b.x + b.w - badgeW - 4, b.y + dy + (b.h - NewBadge.HEIGHT) / 2);
						room -= badgeW + 6;
					} else {
						NewBadge.dot(c, b.x + b.w - 8, b.y + dy + 3);
					}
				}
				String s = c.clip(b.label, room - 2);
				labels.add(new Label(s, b.x + 20 + (room - c.textWidth(s)) / 2, b.y + dy + (b.h - 8) / 2, textColor,
						seasonal && b.hover < 0.35f));
			} else {
				if (isNew) NewBadge.dot(c, b.x + b.w - 7, b.y + dy + 2);
				int px = b.w >= 20 ? 2 : 1;
				int size = 8 * px;
				Icons.draw(c, b.icon, b.x + (b.w - size) / 2, b.y + dy + (b.h - size) / 2, px, iconColor);
				boolean hover = inside(mx, my, b.x, b.y, b.w, b.h);
				// Beschriftung als Hinweis (zuletzt gezeichnet, über allem): x/y = Symbol, color/shadow = Größe/Zeile
				if (hover || focus == base + i) tooltips.add(new Label(b.label, b.x, b.y, b.w, b.h > 0));
			}
			final int index = base + i;
			hits.add(b.x, b.y, b.w, b.h, new Runnable() {
				@Override
				public void run() {
					activate(index);
				}
			});
		}
	}

	/** Hinweise neben Symbolen (nach dem Text gezeichnet). */
	private final List<Label> tooltips = new ArrayList<Label>();

	/** Beschriftung neben einem Symbol: links davon (Leiste) bzw. darunter (Zeile oben). */
	private void tooltip(Canvas c, Theme t, String text, int x, int y, int size, int unused, int width) {
		int tw = c.textWidth(text) + 8;
		int tx;
		int ty;
		if (sideMode == SIDE_ROW) {
			tx = Math.max(2, Math.min(width - tw - 2, x + size / 2 - tw / 2));
			ty = y + size + 3;
		} else {
			tx = x - tw - 4;
			ty = y + (size - 12) / 2;
		}
		c.push();
		c.raise(150);
		Paint.panel(c, tx, ty, tw, 12, t.surfaceHigh, t.border);
		c.text(text, tx + 4, ty + 2, t.text, false);
		c.pop();
	}

	private void drawToast(Canvas c, Theme t, int width, int height, float dt) {
		if (toastLeft <= 0f || toast == null) return;
		toastLeft -= dt;
		float a = ColorMath.clamp01(Math.min(toastLeft / 0.4f, (toastTotal - toastLeft) / 0.12f + 0.2f));
		int tw = c.textWidth(toast) + 22;
		int x = (width - tw) / 2;
		int y = height - 34;
		Canvas f = FadeCanvas.of(c, a);
		f.push();
		f.raise(200);
		Paint.panel(f, x, y, tw, 16, t.surfaceHigh, t.border);
		Paint.pip(f, x + 5, y + 5, 6, 1f);
		f.text(toast, x + 15, y + 4, t.text, false);
		f.pop();
	}

	private void update(float dt, int mx, int my) {
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			boolean hover = inside(mx, my, b.x, b.y, b.w, BTN_H);
			b.hover = fade(b.hover, hover || focus == i || pending == i, dt);
			b.flash = Math.max(0f, b.flash - dt / 0.25f);
		}
		for (int i = 0; i < side.size(); i++) {
			Button b = side.get(i);
			int index = buttons.size() + i;
			boolean hover = inside(mx, my, b.x, b.y, b.w, b.h);
			b.hover = fade(b.hover, hover || focus == index || pending == index, dt);
			b.flash = Math.max(0f, b.flash - dt / 0.25f);
		}
		boolean acc = inside(mx, my, accX, accY, accW, 16) || focus == accountIndex() || pending == accountIndex();
		accHover = fade(accHover, acc, dt);
		accFlash = Math.max(0f, accFlash - dt / 0.25f);
	}

	/** Hervorhebung weich ein- und ausblenden. */
	private static float fade(float value, boolean on, float dt) {
		return on ? Math.min(1f, value + dt / HOVER_IN) : Math.max(0f, value - dt / HOVER_OUT);
	}

	private void layout(int colX, int rowsY) {
		int row = 0;
		int col = 0;
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			int y = rowsY + row * (BTN_H + GAP);
			boolean full = i < 2;
			if (full) {
				b.x = colX;
				b.w = BTN_W;
				row++;
				continue;
			}
			boolean last = i == buttons.size() - 1;
			if (col == 0 && last) {
				// Allein in der Zeile (ohne Mod-Liste): mittig.
				b.x = colX + (BTN_W - HALF) / 2;
				b.w = HALF;
				b.y = y;
				row++;
				continue;
			}
			b.x = col == 0 ? colX : colX + BTN_W - HALF;
			b.w = HALF;
			b.y = y;
			if (col == 1) row++;
			col = 1 - col;
		}
		for (int i = 0; i < Math.min(2, buttons.size()); i++) buttons.get(i).y = rowsY + i * (BTN_H + GAP);
	}

	/** Weich auslaufende Fläche hinter Schriftzug und Knöpfen – die Schrift bleibt auf der Szene lesbar. */
	private static void readingPlate(Canvas c, int x, int y, int w, int h, int color) {
		for (int i = 0; i < 5; i++) {
			int d = (4 - i) * 7;
			Paint.block(c, x - d, y - d, w + 2 * d, h + 2 * d, color);
		}
	}

	/** Farbe der Lese-Fläche: dunkler Schleier, im Polarlicht Nachtblau bzw. am hellen Morgen ein weißer Schleier. */
	private static int plateColor(Theme t) {
		if (t.halloween) return 0x2407060C;
		if (t.fox) return t.light ? 0x1CFFFFFF : 0x12120810;
		if (t.winter) return t.light ? 0x26FFFFFF : 0x22070C18;
		return t.light ? 0x22FFFFFF : 0x16070A1D;
	}

	/** Gedämpfte Schrift (Hinweise, Fußzeile) passend zum Design. */
	private static int dimText(Theme t) {
		if (t.halloween) return HalloweenPalette.MUTED_LIGHT;
		if (t.fox) return t.light ? 0xFF5A3E34 : 0xFFC8AAB4;
		if (t.winter) return t.light ? 0xFF25354C : 0xFFA2B3CD;
		return t.textDim;
	}

	/** Halloween: hängende Spinne bei ~12 % von links (wie im Entwurf), Faden schwankt langsam. */
	private static void spider(Canvas c, int width, int top, float time, boolean animated) {
		int len = Math.max(20, top + 18);
		HalloweenLook.spider(c, width * 22 / 100, 0, len, 2, animated ? time : 1f);
	}

	/**
	 * Halloween-Schriftzug: „Cubiq“ groß in Mondsilber mit Pflaumen-Schatten, „CLIENT“ in Silber mit kleiner Kürbislaterne,
	 * darunter der Untertitel in Violett und schräg der pulsierende Splash.
	 */
	private void halloweenLogo(Canvas c, int width, int top, int big, int small, int logoH, int gapA, float time, boolean animated) {
		float pulse = animated ? 0.7f + 0.3f * (float) (0.5 + 0.5 * Math.sin(time * Math.PI * 2 / 3.4)) : 0.85f;
		int logoW = PixelFont.width("CUBIQ") * big;
		int x = (width - logoW) / 2;
		HalloweenLook.silverWord(c, "CUBIQ", x, top, big, 1f, pulse);
		int subW = PixelFont.width("CLIENT") * small;
		int sx = (width - subW) / 2;
		int sy = top + logoH + gapA;
		for (int[] r : PixelFont.rects("CLIENT")) {
			c.fill(sx + r[0] * small, sy + r[1] * small + Math.max(1, small / 2), sx + r[2] * small, sy + r[3] * small + Math.max(1, small / 2), HalloweenPalette.PLUM);
		}
		for (int[] r : PixelFont.rects("CLIENT")) {
			c.fill(sx + r[0] * small, sy + r[1] * small, sx + r[2] * small, sy + r[3] * small, HalloweenPalette.SILVER);
		}
		// Kürbislaterne neben „CLIENT“ (Kürbislicht flackert)
		float fl = animated ? 0.75f + 0.25f * (float) (Math.sin(time * 9) * Math.sin(time * 3.7)) : 0.9f;
		int jx = sx + subW + small * 3;
		int jy = sy + (PixelFont.HEIGHT * small - Sprites.JACK.h) / 2;
		c.fill(jx - 1, jy, jx + Sprites.JACK.w + 1, jy + Sprites.JACK.h, ColorMath.withAlpha(HalloweenPalette.PUMPKIN_LIGHT, Math.round(26 * fl)));
		c.fill(jx + 1, jy - 2, jx + Sprites.JACK.w - 1, jy + Sprites.JACK.h + 2, ColorMath.withAlpha(HalloweenPalette.PUMPKIN_LIGHT, Math.round(18 * fl)));
		Sprites.draw(c, Sprites.JACK, jx, jy, 1, 1f);
		// Untertitel (Schrift des Spiels) zwischen Schriftzug und Knöpfen
		String subtitle = I18n.tr("halloween.title.subtitle");
		int ty = sy + PixelFont.HEIGHT * small + 3;
		if (ty + 8 < top + logoH + gapA + PixelFont.HEIGHT * small + (height() < 300 ? 12 : 20)) {
			c.text(subtitle, (width - c.textWidth(subtitle)) / 2, ty, HalloweenPalette.VIOLET_LIGHT, true);
		}
		// Splash: schräg rechts vom Schriftzug, pulsiert wie bei Minecraft
		String splash = I18n.tr("halloween.title.splash");
		float s = animated ? 1f + 0.06f * (float) Math.abs(Math.sin(time * Math.PI / 0.9)) : 1f;
		c.push();
		c.translate(x + logoW + big * 4 + c.textWidth(splash) / 3f, top + logoH * 0.9f);
		c.rotate((float) Math.toRadians(-18));
		c.scale(s);
		c.text(splash, -c.textWidth(splash) / 2, -4, HalloweenPalette.SPLASH, true);
		c.pop();
	}

	private int lastHeight;

	private int height() {
		return lastHeight;
	}

	/** Breite des Schriftzugs (Cubiq: mit dem Zeichen davor). */
	private static int logoWidth(Theme t, int big) {
		int word = PixelFont.width("CUBIQ") * big;
		return t.halloween || t.fox ? word : CubiqMark.textOffset(big) + word;
	}

	private void logo(Canvas c, Theme t, int width, int top, int big, int small, int logoH, int gapA, float time, boolean animated) {
		if (t.halloween) {
			halloweenLogo(c, width, top, big, small, logoH, gapA, time, animated);
			return;
		}
		if (t.fox) {
			seasonLogo(c, t, width, top, big, small, logoH, gapA, time, animated);
			return;
		}
		// Cubiq: das Zeichen vor dem Schriftzug (wie im Launcher), flach in der Akzentfarbe mit weichem Schatten;
		// „CLIENT“ ruhig darunter, unter dem Schriftzug mittig. Im Polarlicht liegt Schnee auf dem Zeichen.
		int x = (width - logoWidth(t, big)) / 2;
		int px = Math.max(1, big / 3);
		int cells = CubiqMark.markSize(big) / px;
		int markY = top + (logoH - cells * px) / 2;
		CubiqMark.draw(c, x + px, markY + px, cells, px, ColorMath.lerp(t.accent, 0xFF000000, 0.7f), 0.45f);
		if (t.winter) Frost.mark(c, x, markY, cells, px, 1f);
		else CubiqMark.draw(c, x, markY, cells, px, 1f);
		int wx = x + CubiqMark.textOffset(big);
		List<int[]> rects = PixelFont.rects("CUBIQ");
		int off = Math.max(1, big / 4);
		int shadow = ColorMath.withAlpha(ColorMath.lerp(t.accent, 0xFF000000, 0.75f), t.light ? 0x50 : 0xB0);
		for (int[] r : rects) c.fill(wx + r[0] * big + off, top + r[1] * big + off, wx + r[2] * big + off, top + r[3] * big + off, shadow);
		for (int[] r : rects) c.fill(wx + r[0] * big, top + r[1] * big, wx + r[2] * big, top + r[3] * big, t.accent);
		int wordW = PixelFont.width("CUBIQ") * big;
		int subW = PixelFont.width("CLIENT") * small;
		int sx = wx + (wordW - subW) / 2;
		int sy = top + logoH + gapA;
		int sub = t.winter ? (t.light ? 0xFF33455D : 0xFFBFCCE0) : (t.light ? 0xFF333A62 : 0xFFBCC2E0);
		for (int[] r : PixelFont.rects("CLIENT")) {
			c.fill(sx + r[0] * small, sy + r[1] * small, sx + r[2] * small, sy + r[3] * small, sub);
		}
	}

	/** Schriftzug im Fuchsbau: leuchtende Pixel, Blätter darauf, das Fuchsgesicht neben „CLIENT“. */
	private static void seasonLogo(Canvas c, Theme t, int width, int top, int big, int small, int logoH, int gapA, float time,
			boolean animated) {
		int logoW = PixelFont.width("CUBIQ") * big;
		int x = (width - logoW) / 2;
		int y = top;
		float pulse = animated ? 0.7f + 0.3f * (float) (0.5 + 0.5 * Math.sin(time * Math.PI * 2 / 3.4)) : 0.85f;
		List<int[]> rects = PixelFont.rects("CUBIQ");
		// Leuchten: jede Pixelzeile zweimal vergrößert und sehr durchsichtig.
		int glowNear = ColorMath.withAlpha(t.glow, Math.round(46 * pulse));
		int glowFar = ColorMath.withAlpha(t.glow, Math.round(12 * pulse));
		int g1 = Math.max(2, big / 2);
		int g2 = big;
		for (int[] r : rects) {
			c.fill(x + r[0] * big - g2, y + r[1] * big - g2 / 2, x + r[2] * big + g2, y + r[3] * big + g2 / 2, glowFar);
		}
		for (int[] r : rects) {
			c.fill(x + r[0] * big - g1, y + r[1] * big - g1, x + r[2] * big + g1, y + r[3] * big + g1, glowNear);
		}
		// Schlagschatten, dann die Pixel: oberste Zeile heller, unterste dunkler (Pixel-Kante).
		int shadow = ColorMath.lerp(ColorMath.scaleRgb(t.accent, 0.28f), 0xFF000000, 0.35f);
		int off = Math.max(2, big / 2);
		for (int[] r : rects) {
			c.fill(x + r[0] * big + off, y + r[1] * big + off, x + r[2] * big + off, y + r[3] * big + off, shadow);
		}
		int face = t.accent;
		int light = ColorMath.lerp(face, 0xFFFFFFFF, 0.3f);
		int dark = ColorMath.scaleRgb(face, 0.72f);
		for (int[] r : rects) {
			int color = r[1] == 0 ? light : (r[1] == PixelFont.HEIGHT - 1 ? dark : face);
			c.fill(x + r[0] * big, y + r[1] * big, x + r[2] * big, y + r[3] * big, color);
		}
		Ember.leafCaps(c, rects, x, y, big);
		// Glanzpunkt: ein heller Pixel oben links in jedem Buchstaben.
		int sheen = ColorMath.withAlpha(0xFFFFFFFF, Math.round(90 * pulse));
		c.fill(x, y, x + Math.max(1, big / 3), y + Math.max(1, big / 3), sheen);

		int subW = PixelFont.width("CLIENT") * small;
		int sx = (width - subW) / 2;
		int sy = top + logoH + gapA;
		int sub = t.light ? 0xFF3D2A24 : 0xFFF6DCC8;
		for (int[] r : PixelFont.rects("CLIENT")) {
			c.fill(sx + r[0] * small, sy + r[1] * small, sx + r[2] * small, sy + r[3] * small, sub);
		}
		// Das kleine Fuchsgesicht neben „CLIENT“ (wie neben dem Logo im Launcher), blinzelt ab und zu.
		int px = Math.max(1, small * 2 / 3);
		int fx = sx + subW + small * 3;
		int fy = sy + (PixelFont.HEIGHT * small - 7 * px) / 2;
		Ember.foxMarkGlow(c, fx, fy, px, ColorMath.lerp(t.accent, 0xFFFFFFFF, 0.2f), time, animated, pulse);
	}

	private void footer(Canvas c, Theme t, int width, int height, int mx, int my, float dt) {
		int cw = c.textWidth(classic);
		int lx = width - cw - 4;
		// Versionszeile weicht dem Link aus (schmale Fenster).
		String version = host.versionLine();
		int room = lx - 18;
		if (c.textWidth(version) > room) version = room > 20 ? c.clip(version, room - c.textWidth("…")) + "…" : "";
		if (!version.isEmpty()) labels.add(new Label(version, 4, height - 10, dimText(t), false));
		// Halloween: Kürbis „client-title“ gleich neben der Versionszeile (Kürbis-Suche). Hängt am Event (Funktion), nicht
		// am Look – auch mit „Event-Design“ aus bleibt er im normalen Titelbild auffindbar (draw prüft Halloween.active()).
		dev.pwiepixel.cubiqclient.core.halloween.Pumpkins.draw(c, hits, dev.pwiepixel.cubiqclient.core.halloween.Pumpkins.CLIENT_TITLE,
				4 + c.textWidth(version) + 10, height - 7, mx, my);
		boolean hover = inside(mx, my, lx - 10, height - 12, cw + 10, 12);
		boolean on = hover || focus == linkIndex();
		linkLit = on ? Math.min(1f, linkLit + dt / 0.08f) : Math.max(0f, linkLit - dt / 0.08f);
		if (t.halloween || t.fox) {
			Paint.pip(c, lx - 9, height - 9, 5, linkLit);
			int lit = t.halloween ? HalloweenPalette.SILVER_LIGHT : (t.light ? t.accentText : t.faceOn);
			labels.add(new Label(classic, lx, height - 10, ColorMath.lerp(dimText(t), lit, linkLit), false));
		} else {
			// Cubiq: ein schlichter Link – beim Überfahren in der Akzentfarbe und unterstrichen.
			labels.add(new Label(classic, lx, height - 10, ColorMath.lerp(dimText(t), t.accentText, linkLit), false));
			if (linkLit > 0.02f) c.fill(lx, height - 1, lx + cw, height, ColorMath.withAlpha(t.accentText, Math.round(200 * linkLit)));
		}
		hits.add(lx - 10, height - 12, cw + 14, 12, new Runnable() {
			@Override
			public void run() {
				activate(linkIndex());
			}
		});
	}

	// --- Bedienung ---

	/** Index des Links „Klassischer Titelbildschirm“ (immer der letzte). */
	private int linkIndex() {
		return buttons.size() + side.size() + 1;
	}

	private int accountIndex() {
		return buttons.size() + side.size();
	}

	private void activate(int index) {
		if (pending >= 0) return;
		host.playClick();
		if (index < buttons.size()) buttons.get(index).flash = 1f;
		else if (index < accountIndex()) side.get(index - buttons.size()).flash = 1f;
		else if (index == accountIndex()) accFlash = 1f;
		pending = index;
		pendingIn = PRESS_DELAY;
	}

	/** Führt die Aktion nach dem Aufblitzen aus. */
	private void run(int index) {
		if (index < buttons.size()) {
			buttons.get(index).action.run();
		} else if (index < accountIndex()) {
			side.get(index - buttons.size()).action.run();
		} else if (index == accountIndex()) {
			if (host.openWardrobe()) seen(NewSince.MENU_WARDROBE);
			else soon(accountLabel);
		} else {
			host.classicTitle();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean hit = super.mouseClicked(mouseX, mouseY, button);
		if (!hit) focus = -1;
		return hit;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		turntable.release();
		return super.mouseReleased(mouseX, mouseY, button);
	}

	/** Kein Textfeld – keine Texteingabe (IME bliebe sonst unnötig an). */
	@Override
	public boolean wantsTextInput() {
		return false;
	}

	@Override
	public boolean keyPressed(int rawKey, UiKey key, boolean shift) {
		int count = linkIndex() + 1;
		switch (key) {
			case TAB:
				moveFocus(shift ? -1 : 1, count);
				return true;
			case DOWN:
			case RIGHT:
				moveFocus(1, count);
				return true;
			case UP:
			case LEFT:
				moveFocus(-1, count);
				return true;
			case ENTER:
				if (focus < 0) return false;
				activate(focus);
				return true;
			default:
				return false;
		}
	}

	private void moveFocus(int step, int count) {
		if (focus < 0) focus = step > 0 ? 0 : count - 1;
		else focus = (focus + step + count) % count;
		// Ausgeblendeter Garderobe-Knopf (schmales Fenster) wird übersprungen.
		if (focus == accountIndex() && accW <= 0) focus = (focus + step + count) % count;
		host.narrate(I18n.tr("title.narrateButton", focusLabel(focus)));
	}

	private String focusLabel(int index) {
		if (index < buttons.size()) return buttons.get(index).label;
		if (index < accountIndex()) return side.get(index - buttons.size()).label;
		if (index == accountIndex()) return accountLabel;
		return classic;
	}

	/** Der Startbildschirm schließt nicht (Esc tut nichts). */
	@Override
	public void requestClose() {
	}

	@Override
	protected void onClosed() {
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
