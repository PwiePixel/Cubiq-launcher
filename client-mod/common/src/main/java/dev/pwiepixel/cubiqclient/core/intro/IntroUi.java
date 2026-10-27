package dev.pwiepixel.cubiqclient.core.intro;

import dev.pwiepixel.cubiqclient.core.i18n.I18n;
import dev.pwiepixel.cubiqclient.core.menus.MenuStyle;
import dev.pwiepixel.cubiqclient.core.module.KeySetting;
import dev.pwiepixel.cubiqclient.core.module.Module;
import dev.pwiepixel.cubiqclient.core.module.ModulePacks;
import dev.pwiepixel.cubiqclient.core.module.NewSince;
import dev.pwiepixel.cubiqclient.core.module.CubiqModules;
import dev.pwiepixel.cubiqclient.core.perf.GameOptions;
import dev.pwiepixel.cubiqclient.core.perf.Performance;
import dev.pwiepixel.cubiqclient.core.sync.ClientSync;
import dev.pwiepixel.cubiqclient.core.ui.Anim;
import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.CubiqMark;
import dev.pwiepixel.cubiqclient.core.ui.FadeCanvas;
import dev.pwiepixel.cubiqclient.core.ui.Paint;
import dev.pwiepixel.cubiqclient.core.ui.PixelFont;
import dev.pwiepixel.cubiqclient.core.ui.Theme;
import dev.pwiepixel.cubiqclient.core.ui.UiKey;
import dev.pwiepixel.cubiqclient.core.ui.UiScreen;
import dev.pwiepixel.cubiqclient.core.ui.menu.MenuHost;
import dev.pwiepixel.cubiqclient.core.ui.menu.NewBadge;
import dev.pwiepixel.cubiqclient.core.ui.menu.PacksPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Einführung beim ersten Start – im flachen Stil des Cubiq-Menüs, jederzeit überspringbar. Zuerst zeigt sie kurz
 * das Cubiq-Zeichen wie die Start-Animation des Launchers ({@code app/components/StartIntro.vue}: Zeichen und Name
 * blenden ein, ein grüner Schein zieht auf, rund 1,6 s; ein Klick oder eine Taste überspringt, ohne „Animierter
 * Hintergrund“ entfällt sie). Dann die Schritte:
 * <ol>
 *   <li>Sprache und Aussehen (Thema, Akzent) – vorbelegt aus Launcher/Cubiq-Konto;</li>
 *   <li>Leistung: Config-Modus „Schön“/„Max FPS“, Bildraten-Grenze, VSync, Dynamische FPS;</li>
 *   <li>Tastenbelegung der Cubiq-Tasten mit Konflikten zu Vanilla-/Mod-Tasten, direkt änderbar;</li>
 *   <li>Modul-Paket mit HUD-Vorlage.</li>
 * </ol>
 * Einmal je Cubiq-Konto (Stand im {@code client}-Sync), ohne Cubiq-Dienste einmal je Instanz; im Cubiq-Menü unter
 * „Modul-Pakete“ erneut startbar.
 */
public final class IntroUi extends UiScreen {
	static final int STEPS = 4;
	static final int LOOK = 0;
	static final int PERF = 1;
	static final int KEYS = 2;
	static final int PACKS = 3;

	private static final String[] STEP_IDS = {"look", "perf", "keys", "packs"};
	/** Sprachen mit Eigennamen (werden nicht übersetzt). */
	private static final String[][] LANGUAGES = {
			{"en", "English"}, {"de", "Deutsch"}, {"es", "Español"}, {"fr", "Français"},
			{"pl", "Polski"}, {"pt-BR", "Português (BR)"}, {"tr", "Türkçe"}, {"nl", "Nederlands"}};
	private static final String[] THEMES = LookPicker.THEMES;
	private static final String[] ACCENTS = LookPicker.ACCENTS;
	/** Cubiq-Tasten des Schritts „Tastenbelegung“ (Reihenfolge der Anzeige). */
	static final String[] CUBIQ_KEYS = {"key.cubiqclient.menu", "key.cubiqclient.zoom", "key.cubiqclient.freelook",
			"key.cubiqclient.emoteWheel", NewSince.KEY_WARDROBE, NewSince.KEY_SOCIAL, NewSince.KEY_QUICK_REPLY,
			"key.cubiqclient.saveClip", "key.cubiqclient.toggleRecording"};
	private static final int HEADER_H = 30;
	private static final int FOOTER_H = 28;
	private static final int PAD = 12;
	/** Start-Animation: Dauer bis zum Ausblenden und Dauer des Ausblendens in Sekunden (wie im Launcher). */
	static final float SPLASH_TIME = 1.6f;
	static final float SPLASH_LEAVE = 0.38f;

	private final MenuHost host;
	private final CubiqModules modules;
	private final PacksPage packs;
	private final List<KeyBind> allKeys;
	private final List<KeyBind> cubiqKeys = new ArrayList<KeyBind>();
	/** Ruhige Bewegung: eigener Schalter „Animierter Hintergrund“ aus oder „Reduziert“ im Launcher. */
	private final boolean still;
	private int step;
	private float stepAnim;
	private KeyBind capturing;
	private boolean finished;
	/** Sekunden seit Beginn der Start-Animation; ab {@link #SPLASH_TIME} + {@link #SPLASH_LEAVE} ist sie vorbei. */
	private float splashTime;
	/** Beginn des Ausblendens der Start-Animation (Sekunden) oder -1. */
	private float splashLeave = -1f;

	public IntroUi(final MenuHost host) {
		this.host = host;
		this.modules = host.modules();
		// Bewegung reduziert („Animierter Hintergrund“ aus oder „Reduziert“ im Launcher): keine Start-Animation, gleich
		// der erste Schritt – sofort ganz da, ohne Einblenden und Hineingleiten.
		this.still = !MenuStyle.animated(modules == null || modules.titleAnimated.get());
		if (still) {
			skipSplash();
			open = 1f;
			stepAnim = 1f;
		}
		I18n.refresh();
		this.packs = new PacksPage(modules, new ModulePacks.Support() {
			@Override
			public boolean supports(Module module) {
				return host.supports(module);
			}
		}, new Runnable() {
			@Override
			public void run() {
				host.playClick();
			}
		});
		List<KeyBind> keys;
		try {
			keys = host.keyBindings();
		} catch (RuntimeException e) {
			keys = new ArrayList<KeyBind>();
		}
		this.allKeys = keys;
		for (String id : CUBIQ_KEYS) {
			for (KeyBind b : allKeys) {
				if (b.id.equals(id)) {
					cubiqKeys.add(b);
					break;
				}
			}
		}
	}

	// --- Für den Selbsttest ---

	public int step() {
		return step;
	}

	/** Ohne Bewegung (keine Start-Animation, kein Einblenden, kein Gleiten, keine pulsierende Lampe)? */
	public boolean still() {
		return still;
	}

	/** Springt zu einem Schritt (Selbsttest: Screenshot je Schritt); eine laufende Start-Animation endet sofort. */
	public void goTo(int s) {
		skipSplash();
		step = Math.max(0, Math.min(STEPS - 1, s));
		capturing = null;
	}

	/** Beendet die Start-Animation sofort (Selbsttest, Bewegung reduziert). */
	public void skipSplash() {
		splashTime = SPLASH_TIME + SPLASH_LEAVE;
		splashLeave = SPLASH_TIME;
	}

	/** Läuft die Start-Animation noch (auch während sie ausblendet)? */
	public boolean splashing() {
		return splashTime < SPLASH_TIME + SPLASH_LEAVE;
	}

	/** Klick oder Taste während der Start-Animation: sanft ausblenden (wie im Launcher). */
	private void leaveSplash() {
		if (splashLeave < 0f) splashLeave = splashTime;
	}

	public PacksPage packs() {
		return packs;
	}

	/** Cubiq-Tasten im Schritt „Tastenbelegung“ (Selbsttest). */
	public List<KeyBind> cubiqKeys() {
		return cubiqKeys;
	}

	// --- Ablauf ---

	void next() {
		capturing = null;
		if (step < STEPS - 1) {
			step++;
			stepAnim = 0f;
		} else {
			finish();
		}
	}

	void back() {
		capturing = null;
		if (step > 0) {
			step--;
			stepAnim = 0f;
		}
	}

	/** Fertig: gewähltes Paket anwenden, Einführung als erledigt merken (auch fürs Cubiq-Konto). */
	public void finish() {
		if (finished) return;
		finished = true;
		ModulePacks.Pack pack = packs.selected();
		if (pack != null) packs.applySelected();
		modules.clientState.markIntro(ClientState.FINISHED, pack == null ? null : pack.id, System.currentTimeMillis());
		// Die Modul-Pakete hat man hier gerade gesehen – im Cubiq-Menü brauchen sie kein „NEU“ mehr.
		modules.clientState.news().markSeen(NewSince.MENU_PACKS);
		host.save();
		IntroGate.notice(I18n.tr("intro.doneNotice", host.menuKeyLabel()));
		requestClose();
	}

	/** Überspringen (jederzeit): bisher Gewähltes bleibt, die Einführung gilt als erledigt. */
	public void skip() {
		if (finished) return;
		finished = true;
		modules.clientState.markIntro(ClientState.SKIPPED, null, System.currentTimeMillis());
		host.save();
		IntroGate.notice(I18n.tr("intro.skippedNotice", host.menuKeyLabel()));
		requestClose();
	}

	@Override
	protected void onClosed() {
		modules.clientState.news().closed();
		host.save();
		host.closeScreen();
	}

	// --- Zeichnen ---

	@Override
	protected void draw(Canvas raw, int width, int height, int mouseX, int mouseY, float dt) {
		if (!splashing()) {
			steps(FadeCanvas.of(raw, alpha()), width, height, mouseX, mouseY, dt);
			return;
		}
		splashTime += dt;
		if (splashLeave < 0f && splashTime >= SPLASH_TIME) splashLeave = SPLASH_TIME;
		float leave = splashLeave < 0f ? 0f : ColorMath.clamp01((splashTime - splashLeave) / SPLASH_LEAVE);
		// Beim Ausblenden liegt der erste Schritt schon darunter (und ist bedienbar).
		if (leave > 0f) steps(FadeCanvas.of(raw, alpha()), width, height, mouseX, mouseY, dt);
		splash(raw, width, height, leave);
		if (leave <= 0f) {
			hits.add(0, 0, width, height, new Runnable() {
				@Override
				public void run() {
					leaveSplash();
				}
			});
		}
	}

	/**
	 * Start-Animation (wie {@code StartIntro.vue}): tiefster Grund, ein weicher Schein in der Akzentfarbe wächst, das
	 * Zeichen blendet ein und wächst leicht, darunter „Cubiq Client“, unten der Hinweis zum Überspringen.
	 */
	private void splash(Canvas raw, int width, int height, float leave) {
		Theme t = Theme.get();
		float base = alpha() * (1f - Anim.easeOut(leave));
		if (base <= 0.004f) return;
		Canvas c = FadeCanvas.of(raw, base);
		c.push();
		c.raise(400f);
		c.fill(0, 0, width, height, t.deep);
		int px = Math.min(width, height) >= 300 ? 2 : 1;
		int cells = Math.max(24, Math.min(80, Math.round(Math.min(width, height) * 0.3f / px)));
		int mark = cells * px;
		int cx = width / 2;
		int cy = height / 2 - 14;
		// Schein: wächst in 1,2 s (ab 0,1 s) von 60 % auf volle Größe.
		float g = Anim.easeOut(ColorMath.clamp01((splashTime - 0.1f) / 1.2f));
		if (g > 0.01f) {
			// Wie im Launcher: in der Mitte 30 % Akzentfarbe, bei 72 % des Radius ausgelaufen.
			int r = Math.round(Math.min(width, height) * 0.46f * 0.72f * (0.6f + 0.4f * g));
			Paint.radialGlow(FadeCanvas.of(raw, base * g), cx, cy, r, r, t.accent, 77, width, height);
		}
		// Zeichen: in 0,6 s von 86 % auf volle Größe, blendet ein; beim Ausblenden wächst es noch etwas.
		float m = Anim.easeOut(ColorMath.clamp01(splashTime / 0.6f));
		if (m > 0.01f) {
			float scale = 0.86f + 0.14f * m + 0.04f * Anim.easeOut(leave);
			Canvas mc = FadeCanvas.of(raw, base * m);
			mc.push();
			mc.translate(cx, cy);
			mc.scale(scale);
			CubiqMark.draw(mc, -mark / 2, -mark / 2, cells, px, 1f);
			mc.pop();
		}
		// Name: „Cubiq Client“ in doppelter Größe, „Client“ in der Akzentfarbe; ab 0,32 s, rutscht dabei leicht hoch.
		float n = Anim.easeOut(ColorMath.clamp01((splashTime - 0.32f) / 0.45f));
		if (n > 0.01f) {
			Canvas nc = FadeCanvas.of(raw, base * n);
			String cubiq = "Cubiq ";
			String client = "Client";
			int w = (nc.textWidth(cubiq) + nc.textWidth(client)) * 2;
			nc.push();
			nc.translate(cx - w / 2f, cy + mark / 2f + 12 + Math.round((1 - n) * 6));
			nc.scale(2f);
			nc.text(cubiq, 0, 0, t.text, false);
			nc.text(client, nc.textWidth(cubiq), 0, t.accentText, false);
			nc.pop();
		}
		// Hinweis unten: ab 0,7 s.
		float k = Anim.easeOut(ColorMath.clamp01((splashTime - 0.7f) / 0.4f));
		if (k > 0.01f) {
			Canvas kc = FadeCanvas.of(raw, base * k);
			String skip = I18n.tr("intro.splash.skip");
			kc.text(skip, cx - kc.textWidth(skip) / 2, height - 20, t.textDim, false);
		}
		c.pop();
	}

	/** Die Schritte im Fenster. */
	private void steps(Canvas c, int width, int height, int mouseX, int mouseY, float dt) {
		Theme t = Theme.get();
		c.fill(0, 0, width, height, t.scrim);
		stepAnim = still ? 1f : Anim.approach(stepAnim, 1f, dt, 0.06f);
		int pw = Math.min(width - 16, Math.max(Math.min(420, width - 16), Math.min(640, Math.round(width * 0.8f))));
		int ph = Math.min(height - 16, Math.max(Math.min(250, height - 16), Math.min(400, Math.round(height * 0.84f))));
		int px = (width - pw) / 2;
		int py = (height - ph) / 2 + (still ? 0 : Math.round((1 - Anim.easeOut(open)) * 14));
		c.push();
		c.raise(300f);
		Paint.window(c, px, py, pw, ph);
		header(c, px, py, pw);
		int bx = px + PAD;
		int by = py + HEADER_H + 8;
		int bw = pw - PAD * 2;
		int bh = ph - HEADER_H - FOOTER_H - 14;
		// Titel und Text des Schritts
		String title = I18n.tr("intro.step." + STEP_IDS[step] + ".title");
		c.text(c.clip(title, bw), bx, by, t.text, false);
		int ty = Paint.paragraph(c, I18n.tr("intro.step." + STEP_IDS[step] + ".text"), bx, by + 12, bw, 10, t.textDim) + 6;
		int body = by + Math.max(0, ty - by);
		int bodyH = by + bh - body;
		int slide = Math.round((1 - Anim.easeOut(stepAnim)) * 10);
		c.scissor(bx - 3, body - 2, bx + bw + 3, by + bh + 2);
		hits.clip(bx - 3, body - 2, bw + 6, by + bh + 4 - body);
		c.push();
		c.translate(slide, 0);
		switch (step) {
			case LOOK:
				look(c, bx, body, bw, bodyH, mouseX, mouseY);
				break;
			case PERF:
				perf(c, bx, body, bw, bodyH, mouseX, mouseY);
				break;
			case KEYS:
				keys(c, bx, body, bw, bodyH, mouseX, mouseY);
				break;
			default:
				packs.draw(c, hits, bx, body, bw, bodyH, mouseX, mouseY, false, null);
				break;
		}
		c.pop();
		c.noScissor();
		hits.noClip();
		footer(c, px, py + ph - FOOTER_H, pw, mouseX, mouseY);
		c.pop();
	}

	private void header(Canvas c, int px, int py, int pw) {
		Theme t = Theme.get();
		Paint.windowHeader(c, px, py, pw, HEADER_H, t.surfaceHigh, t.border);
		int lx = px + PAD;
		int ly = py + (HEADER_H - PixelFont.HEIGHT * 2) / 2;
		boolean seasonal = t.halloween || t.fox;
		int textX;
		if (seasonal) {
			List<int[]> rects = PixelFont.rects("CUBIQ");
			int light = ColorMath.lerp(t.accent, 0xFFFFFFFF, 0.3f);
			for (int[] r : rects) c.fill(lx + r[0] * 2, ly + r[1] * 2, lx + r[2] * 2, ly + r[3] * 2, r[1] == 0 ? light : t.accent);
			if (t.fox) dev.pwiepixel.cubiqclient.core.ui.fox.Ember.leafCaps(c, rects, lx, ly, 2);
			textX = lx + PixelFont.width("CUBIQ") * 2 + 6;
		} else if (t.winter) {
			textX = lx + dev.pwiepixel.cubiqclient.core.ui.winter.Frost.lockup(c, lx, ly, 2, 1f) + 6;
		} else {
			textX = lx + CubiqMark.lockup(c, lx, ly, 2, 1f) + 6;
		}
		c.text(I18n.tr("intro.title"), textX, py + (HEADER_H - 8) / 2, t.textDim, false);
		if (!seasonal) {
			stepper(c, t, px + pw - PAD, py + HEADER_H / 2);
			return;
		}
		// Schritte als Laternen, verbunden durch Linien: erledigte und der aktuelle leuchten.
		int lamp = 12;
		int spacing = 26;
		int total = (STEPS - 1) * spacing + lamp;
		int sx = px + pw - PAD - total;
		int sy = py + (HEADER_H - lamp) / 2;
		for (int i = 0; i < STEPS; i++) {
			int x = sx + i * spacing;
			if (i > 0) {
				boolean powered = i <= step;
				Paint.lineH(c, x - spacing + lamp, x, sy + lamp / 2 - 1, powered ? t.accentText : t.track, powered ? 0.6f : 0f);
			}
			float pulse = still ? 1f : 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 300.0);
			float lit = i < step ? 0.85f : (i == step ? pulse : 0f);
			Paint.accentFace(c, x, sy, lamp, lamp, lit, 0f);
		}
	}

	/**
	 * Schrittanzeige im Cubiq-Design (rechtsbündig bis {@code right}, mittig um {@code cy}): Punkte, verbunden durch feine
	 * Linien – erledigte und der aktuelle in der Akzentfarbe, der aktuelle mit Ring.
	 */
	private void stepper(Canvas c, Theme t, int right, int cy) {
		int dot = 8;
		int spacing = 24;
		int sx = right - (STEPS - 1) * spacing - dot;
		int y = cy - dot / 2;
		for (int i = 0; i < STEPS; i++) {
			int x = sx + i * spacing;
			if (i > 0) c.fill(x - spacing + dot + 2, cy, x - 2, cy + 1, i <= step ? t.accent : t.track);
			if (i == step) Paint.roundOutline(c, x - 2, y - 2, dot + 4, dot + 4, (dot + 4) / 2, ColorMath.withAlpha(t.accentText, 0xB0));
			Paint.roundRect(c, x, y, dot, dot, dot / 2, i <= step ? t.accent : t.track);
		}
	}

	private void footer(Canvas c, int px, int fy, int pw, int mx, int my) {
		Theme t = Theme.get();
		c.fill(px + 1, fy, px + pw - 1, fy + 1, t.border);
		int y = fy + 6;
		int h = 17;
		String skip = I18n.tr("intro.skip");
		int sw = c.textWidth(skip) + 20;
		int sx = px + PAD;
		Paint.button(c, sx, y, sw, h, skip, false, inside(mx, my, sx, y, sw, h));
		hits.add(sx, y, sw, h, new Runnable() {
			@Override
			public void run() {
				host.playClick();
				skip();
			}
		});
		String nextLabel = step == STEPS - 1 ? I18n.tr("intro.finish") : I18n.tr("intro.next");
		int nw = Math.max(70, c.textWidth(nextLabel) + 24);
		int nx = px + pw - PAD - nw;
		Paint.button(c, nx, y, nw, h, nextLabel, true, inside(mx, my, nx, y, nw, h));
		hits.add(nx, y, nw, h, new Runnable() {
			@Override
			public void run() {
				host.playClick();
				next();
			}
		});
		if (step > 0) {
			String backLabel = I18n.tr("intro.back");
			int bw = c.textWidth(backLabel) + 20;
			int bx = nx - 6 - bw;
			Paint.button(c, bx, y, bw, h, backLabel, false, inside(mx, my, bx, y, bw, h));
			hits.add(bx, y, bw, h, new Runnable() {
				@Override
				public void run() {
					host.playClick();
					back();
				}
			});
		}
		String counter = I18n.tr("intro.stepOf", step + 1, STEPS);
		int cw = c.textWidth(counter);
		int cx = sx + sw + 10;
		if (cx + cw < nx - 70) c.text(counter, cx, y + 5, t.textDim, false);
	}

	// --- Schritt 1: Sprache und Aussehen ---

	private void look(Canvas c, int x, int y, int w, int h, int mx, int my) {
		Theme t = Theme.get();
		c.text(I18n.tr("intro.language"), x, y, t.text, false);
		int cols = w >= 360 ? 4 : 2;
		int gap = 4;
		int bw = (w - gap * (cols - 1)) / cols;
		int bh = 16;
		int ry = y + 11;
		String current = I18n.code();
		for (int i = 0; i < LANGUAGES.length; i++) {
			final String code = LANGUAGES[i][0];
			String label = LANGUAGES[i][1] + (I18n.BETA.contains(code) ? " β" : "");
			int bx = x + (i % cols) * (bw + gap);
			int by = ry + (i / cols) * (bh + gap);
			choice(c, bx, by, bw, bh, label, code.equals(current), mx, my, new Runnable() {
				@Override
				public void run() {
					chooseLook(null, null, code);
				}
			});
		}
		ry += ((LANGUAGES.length + cols - 1) / cols) * (bh + gap) + 6;
		c.text(I18n.tr("intro.theme"), x, ry, t.text, false);
		ry += 11;
		int tw = Math.min(110, (w - gap * 2) / 3);
		// Die Akzente passen neben die Farbschemata, wenn deren Knöpfe etwas schmaler werden – dann hat die Design-Wahl
		// (drei Knöpfe) darunter eine eigene Zeile, ohne dass der Schritt höher wird.
		int fit = (w - gap * 3 - 10 - ACCENTS.length * (16 + 4)) / 3;
		if (fit < tw && fit >= 60) tw = fit;
		for (int i = 0; i < THEMES.length; i++) {
			final String theme = THEMES[i];
			choice(c, x + i * (tw + gap), ry, tw, bh, I18n.tr("intro.theme." + theme), theme.equals(t.themeName), mx, my, new Runnable() {
				@Override
				public void run() {
					chooseLook(theme, null, null);
				}
			});
		}
		int ax = x + THEMES.length * (tw + gap) + 10;
		int size = 16;
		if (ax + ACCENTS.length * (size + 4) > x + w) {
			ry += bh + gap + 4;
			ax = x;
		}
		for (int i = 0; i < ACCENTS.length; i++) {
			final String accent = ACCENTS[i];
			int sx = ax + i * (size + 4);
			boolean sel = accent.equals(t.accentName);
			boolean hover = inside(mx, my, sx, ry, size, size);
			int color = Theme.of("dark", accent, null).accent;
			LookPicker.swatch(c, sx, ry, size, color, sel, hover);
			hits.add(sx, ry, size, size, new Runnable() {
				@Override
				public void run() {
					host.playClick();
					chooseLook(null, accent, null);
				}
			});
		}
		// Design (Cubiq | Polarlicht | Fuchsbau) neben den Akzenten, sonst in einer eigenen Zeile.
		int dx = ax + ACCENTS.length * (size + 4) + 10;
		int dw = x + w - dx;
		if (dw < 270) {
			ry += bh + gap + 4;
			dx = x;
			dw = Math.min(w, LookPicker.DESIGNS_WIDTH);
		}
		LookPicker.drawDesigns(c, hits, modules, dx, ry - 1, Math.min(dw, LookPicker.DESIGNS_WIDTH), mx, my, new Runnable() {
			@Override
			public void run() {
				host.playClick();
			}
		});
		ry += bh + 10;
		if (ry + 20 < y + h) {
			ClientSync sync = ClientSync.get();
			String note = sync != null && sync.enabled() ? I18n.tr("intro.look.synced") : I18n.tr("intro.look.local");
			Paint.paragraph(c, note, x, ry, w, 10, t.textDim);
		}
	}

	private void chooseLook(String theme, String accent, String language) {
		LookPicker.choose(modules, theme, accent, language, null);
	}

	private void choice(Canvas c, int x, int y, int w, int h, String label, boolean selected, int mx, int my,
			final Runnable action) {
		Theme t = Theme.get();
		boolean hover = inside(mx, my, x, y, w, h);
		if (selected) {
			Paint.accentButton(c, x, y, w, h, c.clip(label, w - 8), 1f, 0f);
		} else {
			boolean seasonal = t.halloween || t.fox;
			if (seasonal) Paint.panel(c, x, y, w, h, hover ? t.surfaceHover : t.surfaceHigh, hover ? Paint.hoverEdge(t) : t.border);
			else Paint.secondaryFace(c, x, y, w, h, hover ? 1f : 0f);
			String s = c.clip(label, w - 6);
			c.text(s, x + (w - c.textWidth(s)) / 2, y + (h - 8) / 2, seasonal ? t.text : Paint.secondaryText(hover ? 1f : 0f), false);
		}
		hits.add(x, y, w, h, new Runnable() {
			@Override
			public void run() {
				host.playClick();
				action.run();
			}
		});
	}

	// --- Schritt 2: Leistung ---

	private void perf(Canvas c, int x, int y, int w, int h, int mx, int my) {
		Theme t = Theme.get();
		final int bottom = y + h;
		final FpsModeChooser chooser = FpsModeChooser.get();
		String mode = chooser.current(modules);
		int gap = 6;
		int cw = (w - gap) / 2;
		int ch = h >= 120 ? 40 : 30;
		if (chooser.available()) {
			modeCard(c, x, y, cw, ch, FpsModeChooser.PRETTY, "sparkle", FpsModeChooser.PRETTY.equals(mode), mx, my, chooser);
			modeCard(c, x + cw + gap, y, cw, ch, FpsModeChooser.MAX, "bolt", FpsModeChooser.MAX.equals(mode), mx, my, chooser);
			y += ch + 6;
		}
		Performance perf = Performance.current();
		final GameOptions game = perf == null ? null : perf.game();
		int rowH = 18;
		// Bildraten-Grenze: „Unbegrenzt“ mit einem Klick (wo die Version es kann), sonst ein Hinweis.
		final GameOptions.Opt maxFps = maxFpsOption();
		int limit = game != null && maxFps != null ? game.get(maxFps) : GameOptions.NONE;
		if (limit != GameOptions.NONE) {
			boolean unlimited = limit >= 260;
			String label = I18n.tr("intro.perf.frameLimit", unlimited ? I18n.tr("intro.perf.unlimited") : String.valueOf(limit));
			Paint.textClipped(c, label, x, y + 5, w - 110, t.text, false);
			if (!unlimited) {
				String fix = I18n.tr("intro.perf.setUnlimited");
				int fw = c.textWidth(fix) + 16;
				int fx = x + w - fw;
				Paint.button(c, fx, y, fw, 16, fix, true, inside(mx, my, fx, y, fw, 16));
				hits.add(fx, y, fw, 16, new Runnable() {
					@Override
					public void run() {
						host.playClick();
						game.set(maxFps, 260);
						game.save();
					}
				});
			} else {
				dev.pwiepixel.cubiqclient.core.ui.Icons.draw(c, "check", x + w - 10, y + 4, 1, t.accentText);
			}
			y += rowH;
		} else {
			// Hinweis: so viele Zeilen, wie Platz ist (VSync und Dynamische FPS brauchen noch 2 Zeilen).
			List<String> lines = Paint.wrap(c, I18n.tr("intro.perf.frameLimitHint"), w);
			int room = Math.max(1, (bottom - y - 2 * rowH - 2) / 10);
			for (int i = 0; i < lines.size() && i < room; i++) {
				String line = i == room - 1 && lines.size() > room ? c.clip(lines.get(i), w - 8) + "…" : lines.get(i);
				c.text(line, x, y + 1 + i * 10, t.textDim, false);
			}
			y += Math.min(lines.size(), room) * 10 + 4;
		}
		// VSync
		final int vsync = game != null ? game.get(GameOptions.Opt.VSYNC) : GameOptions.NONE;
		if (vsync != GameOptions.NONE && y + rowH <= bottom) {
			String label = I18n.tr("intro.perf.vsync");
			toggleRow(c, x, y, w, label, vsync == 1, mx, my, new Runnable() {
				@Override
				public void run() {
					game.set(GameOptions.Opt.VSYNC, vsync == 1 ? 0 : 1);
					game.save();
				}
			});
			if (vsync == 1) {
				// Hinweis in derselben Zeile, rechts neben „VSync“.
				int hx = x + c.textWidth(label) + 10;
				Paint.textClipped(c, I18n.tr("intro.perf.vsyncHint"), hx, y + 5, x + w - 34 - hx, t.caution, false);
			}
			y += rowH;
		}
		// Dynamische FPS
		final Module dyn = modules.dynamicFps;
		if (host.supports(dyn) && y + rowH <= bottom) {
			toggleRow(c, x, y, w, dyn.name(), dyn.isEnabled(), mx, my, new Runnable() {
				@Override
				public void run() {
					dyn.toggle();
				}
			});
			y += rowH;
			List<String> lines = Paint.wrap(c, dyn.description(), w - 8);
			for (int i = 0; i < lines.size() && y + 9 <= bottom; i++, y += 10) c.text(lines.get(i), x + 8, y - 2, t.textDim, false);
		}
	}

	/** „Max. Bildrate“ gibt es erst mit dem Leistungs-Update im Options-Katalog – per Name, ohne feste Abhängigkeit. */
	static GameOptions.Opt maxFpsOption() {
		try {
			return GameOptions.Opt.valueOf("MAX_FPS");
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void modeCard(Canvas c, int x, int y, int w, int h, final String mode, String icon, boolean selected, int mx, int my,
			final FpsModeChooser chooser) {
		Theme t = Theme.get();
		boolean hover = inside(mx, my, x, y, w, h);
		if (selected) Paint.glow(c, x, y, w, h, t.glow, 0.8f);
		Paint.panel(c, x, y, w, h, selected ? ColorMath.lerp(t.surfaceHover, t.accent, 0.12f) : (hover ? t.surfaceHover : t.surface),
				selected ? ColorMath.lerp(t.border, t.accent, 0.85f) : t.border);
		Paint.iconWell(c, x + 6, y + (h - 14) / 2, 1, icon, selected ? t.accentText : t.textDim, selected ? 1f : 0f);
		boolean low = h < 36;
		Paint.textClipped(c, I18n.tr("intro.perf.mode." + mode), x + 26, y + (low ? 5 : 7), w - 32, t.text, false);
		List<String> lines = Paint.wrap(c, I18n.tr("intro.perf.mode." + mode + ".desc"), w - 32);
		if (low) Paint.textClipped(c, I18n.tr("intro.perf.mode." + mode + ".desc"), x + 26, y + 16, w - 32, t.textDim, false);
		else for (int i = 0; i < lines.size() && i < 2; i++) c.text(lines.get(i), x + 26, y + 18 + i * 10, t.textDim, false);
		hits.add(x, y, w, h, new Runnable() {
			@Override
			public void run() {
				host.playClick();
				chooser.apply(modules, mode);
				if (!mode.equals(modules.clientState.fpsMode())) modules.clientState.setFpsMode(mode);
			}
		});
	}

	private void toggleRow(Canvas c, int x, int y, int w, String label, boolean on, int mx, int my, final Runnable toggle) {
		Theme t = Theme.get();
		Paint.textClipped(c, label, x, y + 6, w - 34, t.text, false);
		int tx = x + w - 24;
		boolean hover = inside(mx, my, tx - 4, y + 2, 30, 16);
		Paint.toggle(c, tx, y + 4, 24, 12, on ? 1f : 0f, hover);
		hits.add(tx - 4, y + 2, 30, 16, new Runnable() {
			@Override
			public void run() {
				host.playClick();
				toggle.run();
			}
		});
	}

	// --- Schritt 3: Tasten ---

	private void keys(Canvas c, int x, int y, int w, int h, int mx, int my) {
		Theme t = Theme.get();
		if (cubiqKeys.isEmpty()) {
			Paint.paragraph(c, I18n.tr("intro.keys.unavailable"), x, y, w, 10, t.textDim);
			return;
		}
		// Tastenkappen sind 15 hoch – in kleinen Fenstern bis 16 zusammenrücken, damit alle Tasten passen.
		int rowH = Math.max(16, Math.min(26, h / Math.max(1, cubiqKeys.size())));
		int capW = Math.min(90, Math.max(60, w / 5));
		for (int i = 0; i < cubiqKeys.size(); i++) {
			final KeyBind b = cubiqKeys.get(i);
			int ry = y + i * rowH;
			if (ry + 16 > y + h) break;
			List<KeyBind> conflicts = b.conflicts(allKeys);
			int labelW = w - capW - 30;
			// Neue Taste seit dem letzten Update: „NEU“-Schild, solange die Einführung offen ist.
			modules.clientState.news().shown(b.id);
			if (modules.clientState.news().badge(b.id)) {
				int badgeW = NewBadge.width(c);
				int nameW = Math.min(c.textWidth(b.label), Math.max(0, labelW - badgeW - 4));
				Paint.textClipped(c, b.label, x, ry + (rowH >= 22 ? 2 : 3), nameW, t.text, false);
				NewBadge.draw(c, x + nameW + 4, ry + (rowH >= 22 ? 1 : 2));
			} else {
				Paint.textClipped(c, b.label, x, ry + (rowH >= 22 ? 2 : 3), labelW, t.text, false);
			}
			String status;
			int statusColor;
			if (!b.bound()) {
				status = I18n.tr("intro.keys.unbound");
				statusColor = t.textDim;
			} else if (conflicts.isEmpty()) {
				status = I18n.tr("intro.keys.free");
				statusColor = t.textDim;
			} else {
				StringBuilder sb = new StringBuilder();
				for (int k = 0; k < conflicts.size() && k < 2; k++) {
					if (k > 0) sb.append(", ");
					sb.append(conflicts.get(k).label);
				}
				if (conflicts.size() > 2) sb.append(" …");
				status = I18n.tr("intro.keys.conflict", sb.toString());
				statusColor = t.caution;
			}
			if (rowH >= 22) {
				Paint.textClipped(c, status, x + 6, ry + 12, labelW - 6, statusColor, false);
			} else {
				// Eine Zeile: Name links, Zustand rechtsbündig davor (bei Platzmangel nur ein Lämpchen).
				int nameW = c.textWidth(b.label) + (modules.clientState.news().badge(b.id) ? NewBadge.width(c) + 4 : 0);
				int sw = c.textWidth(status);
				if (nameW + 12 + sw <= labelW) Paint.textRight(c, status, x + labelW, ry + 3, statusColor, false);
				else if (!conflicts.isEmpty()) Paint.roundRect(c, x + labelW - 6, ry + 4, 5, 5, 2, t.caution);
			}
			int kx = x + w - capW - 22;
			boolean cap = capturing == b;
			boolean hover = inside(mx, my, kx, ry, capW, 15);
			if (cap) {
				Paint.accentFace(c, kx, ry, capW, 15, 1f, 0f);
				Paint.textCentered(c, c.clip(I18n.tr("settings.pressKey"), capW - 4), kx + capW / 2, ry + 4, t.faceTextOn, false);
			} else {
				Paint.keycap(c, kx, ry, capW, b.bound() ? host.keyLabel(b.key()) : "—", hover);
				if (!conflicts.isEmpty()) Paint.glow(c, kx, ry, capW, 15, t.caution, 0.6f);
			}
			hits.add(kx, ry, capW, 15, new Runnable() {
				@Override
				public void run() {
					host.playClick();
					capturing = capturing == b ? null : b;
				}
			});
			int rx = x + w - 16;
			boolean rHover = inside(mx, my, rx, ry, 16, 16);
			Paint.iconButton(c, rx, ry, 16, "reset", rHover, false);
			hits.add(rx, ry, 16, 16, new Runnable() {
				@Override
				public void run() {
					host.playClick();
					b.set(b.defaultKey);
					capturing = null;
				}
			});
		}
	}

	// --- Eingaben ---

	@Override
	public boolean keyPressed(int rawKey, UiKey key, boolean shift) {
		if (capturing != null) {
			if (key == UiKey.ESCAPE) {
				capturing = null;
				return true;
			}
			if (key == UiKey.BACKSPACE || key == UiKey.DELETE) {
				capturing.set(KeySetting.NONE);
				capturing = null;
				return true;
			}
			String name = host.keyNameOf(rawKey);
			if (name != null) capturing.set(name);
			capturing = null;
			return true;
		}
		if (splashing() && splashLeave < 0f) {
			// Jede Taste überspringt die Start-Animation (Esc auch – die Einführung selbst bleibt offen).
			leaveSplash();
			return true;
		}
		if (key == UiKey.ESCAPE) {
			skip();
			return true;
		}
		if (key == UiKey.ENTER) {
			host.playClick();
			next();
			return true;
		}
		if (key == UiKey.LEFT && step > 0) {
			back();
			return true;
		}
		if (key == UiKey.RIGHT && step < STEPS - 1) {
			next();
			return true;
		}
		return false;
	}

	@Override
	public boolean pausesGame() {
		return true;
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
