package dev.pwiepixel.cubiqclient.core.ui.title;

import dev.pwiepixel.cubiqclient.core.ui.Canvas;
import dev.pwiepixel.cubiqclient.core.ui.ColorMath;
import dev.pwiepixel.cubiqclient.core.ui.Theme;

/**
 * Drehscheibe unter der Spielerfigur. Im Cubiq-Design ein flacher Würfel als Sockel, der sich mit der Figur dreht:
 * Oberseite in der Flächenfarbe mit feiner Kante in der Akzentfarbe, die sichtbaren Seiten zum Licht hin heller,
 * darunter ein weicher Schein. Im Polarlicht ist der Würfel verschneit wie die schwebenden Inseln der Szene; Fuchsbau
 * (Insel) und Halloween (Grabhügel) haben eigene Sockel. Alles aus Rechtecken im Pixelraster ({@code px} GUI-Pixel je
 * Bildpunkt).
 *
 * <p>Drehung: langsam von selbst ({@link #AUTO_SPEED}); per Ziehen mit der Maus mit Schwung, der danach
 * wieder in die Eigendrehung übergeht. Gezeichnet wird in zwei Durchgängen – hinterer Teil vor der Figur,
 * vorderer Teil danach.
 */
public final class Turntable {
	/** Eigendrehung in Grad je Sekunde (eine Runde in 24 s). */
	public static final float AUTO_SPEED = 15f;
	/** Grad je GUI-Pixel beim Ziehen. */
	static final float DRAG_DEG_PER_PX = 1.1f;
	/** Plätze am Rand (Blumen) und Punkte auf dem Ring (Laub). */
	private static final int RING_SPOTS = 6;
	private static final int RING_DOTS = 28;

	private float angle = 20f;
	private float velocity = AUTO_SPEED;
	/** Schon ein Zeitschritt gelaufen? (Ohne Animation steht die Scheibe dann von Anfang an.) */
	private boolean ticked;
	private boolean dragging;
	private double lastX;
	private long lastDragNanos;

	/** Aktueller Drehwinkel in Grad (0 = Figur schaut zum Betrachter). */
	public float angle() {
		return angle;
	}

	/** Winkel setzen (Selbsttest: feste Ansicht für Screenshots). */
	public void setAngle(float degrees) {
		angle = wrap(degrees);
	}

	public boolean dragging() {
		return dragging;
	}

	/** Zeitschritt: Eigendrehung bzw. Nachlauf nach dem Ziehen. */
	public void tick(float dt, boolean animated) {
		if (!ticked) {
			ticked = true;
			// Ohne Animation von Anfang an still – die Eigendrehung läuft nicht erst aus.
			if (!animated) velocity = 0f;
		}
		if (dragging) return;
		float target = animated ? AUTO_SPEED : 0f;
		// Schwung klingt in ~0,6 s auf die Eigendrehung ab.
		float k = Math.min(1f, dt / 0.6f);
		velocity += (target - velocity) * k;
		angle = wrap(angle + velocity * dt);
	}

	/** Ziehen beginnt bei {@code x}. */
	public void grab(double x) {
		dragging = true;
		lastX = x;
		lastDragNanos = System.nanoTime();
		velocity = 0f;
	}

	/** Maus bewegt sich beim Ziehen. */
	public void drag(double x) {
		if (!dragging) return;
		long now = System.nanoTime();
		float dx = (float) (x - lastX);
		float dt = Math.max(0.004f, (now - lastDragNanos) / 1_000_000_000f);
		angle = wrap(angle + dx * DRAG_DEG_PER_PX);
		float v = dx * DRAG_DEG_PER_PX / dt;
		velocity = velocity * 0.5f + Math.max(-720f, Math.min(720f, v)) * 0.5f;
		lastX = x;
		lastDragNanos = now;
	}

	/** Loslassen: der Schwung bleibt und geht in die Eigendrehung über. */
	public void release() {
		if (!dragging) return;
		dragging = false;
		// Lange still gehalten → kein Schwung.
		if (System.nanoTime() - lastDragNanos > 120_000_000L) velocity = 0f;
	}

	static float wrap(float deg) {
		float a = deg % 360f;
		return a < 0 ? a + 360f : a;
	}

	// --- Zeichnen ---

	/**
	 * Hinterer Teil: Schein, Seiten und Oberseite des Sockels (bzw. Scholle, Insel, Grabhügel).
	 *
	 * @param cx     Mitte
	 * @param cy     Mitte der Oberseite (hier stehen die Füße)
	 * @param radius Radius in GUI-Pixeln
	 * @param squash Höhe/Breite der Ellipse (Blick von schräg oben)
	 * @param px     Pixelgröße
	 * @param time   Sekunden (Animation des Signals)
	 */
	public void drawBack(Canvas c, int cx, int cy, int radius, float squash, int px, float time) {
		Theme t = Theme.get();
		int ry = Math.max(px, Math.round(radius * squash));
		int thick = Math.max(2 * px, Math.round(radius * 0.22f / px) * px);
		if (t.halloween) {
			graveBack(c, cx, cy, radius, ry, thick, px, time);
			return;
		}
		if (t.winter) {
			snowBlock(c, t, cx, cy, radius, thick, squash, px);
			return;
		}
		if (t.fox) {
			islandBack(c, t, cx, cy, radius, ry, thick, squash, px, time);
			return;
		}
		pedestal(c, t, cx, cy, radius, ry, thick, squash, px);
	}

	/** Vorderer Teil (nach der Figur): was vor den Füßen liegt (Blumen, Nebel); die Würfel-Sockel brauchen nichts. */
	public void drawFront(Canvas c, int cx, int cy, int radius, float squash, int px, float time) {
		Theme t = Theme.get();
		if (t.halloween) {
			graveFront(c, cx, cy, radius, Math.max(px, Math.round(radius * squash)), px, time);
			return;
		}
		if (t.fox) {
			leafRing(c, t, cx, cy, radius, squash, px, time, false);
			flowers(c, t, cx, cy, radius, squash, px, time, false);
		}
	}

	/**
	 * Halloween: Grabhügel aus dunkler Erde mit Moos und Grasbüscheln, ein schiefer Grabstein dahinter, zwei Kerzen
	 * (Licht flackert), violetter Mondschein darunter. Die Lage der Büschel hängt am Winkel – die Drehung bleibt sichtbar.
	 */
	private void graveBack(Canvas c, int cx, int cy, int rx, int ry, int thick, int px, float time) {
		for (int i = 3; i >= 1; i--) {
			ellipse(c, cx, cy + thick, rx + i * 3 * px, ry + i * px, px, ColorMath.withAlpha(0xFFB9A2EA, 8 + (3 - i) * 6));
		}
		// Grabstein hinter der Figur (leicht schief, Kreuz eingeritzt)
		int sw = Math.max(4 * px, Math.round(rx * 0.42f / px) * px);
		int sh = Math.max(6 * px, Math.round(rx * 0.7f / px) * px);
		int sx = cx + rx / 3 - sw / 2;
		int sy = cy - ry / 2 - sh;
		c.fill(sx, sy + px, sx + sw, sy + sh, 0xFF1C1730);
		c.fill(sx + px, sy, sx + sw - px, sy + px, 0xFF1C1730);
		c.fill(sx + sw - px, sy + px, sx + sw, sy + sh, 0xFF2D2648);
		int kx = sx + sw / 2 - px / 2;
		c.fill(kx, sy + 2 * px, kx + px, sy + 2 * px + Math.max(3 * px, sh / 3), 0xFF3B3851);
		c.fill(kx - px, sy + 3 * px, kx + 2 * px, sy + 4 * px, 0xFF3B3851);
		// Erdband (Seite) und Oberfläche
		for (int y = 0; y < ry + thick; y += px) {
			int hw;
			if (y < thick) {
				hw = rx;
			} else {
				float fy = (y - thick + px / 2f) / (float) ry;
				float f = 1f - fy * fy;
				if (f <= 0f) continue;
				hw = Math.round(rx * (float) Math.sqrt(f) / px) * px;
			}
			if (hw <= 0) continue;
			c.fill(cx - hw, cy + y, cx + hw, cy + y + px, y < thick / 2 ? 0xFF1A1426 : 0xFF120E1C);
		}
		ellipse(c, cx, cy, rx, ry, px, 0xFF1E1832);
		ellipse(c, cx, cy, rx - px, Math.max(px, ry - px), px, 0xFF262040);
		ellipse(c, cx, cy, rx - 3 * px, Math.max(px, ry - 2 * px), px, 0xFF2B2348);
		// Moos/Grasbüschel auf der Oberfläche, wandern mit der Drehung
		float spin = -(float) Math.toRadians(angle);
		for (int i = 0; i < 14; i++) {
			double a = spin + i * (Math.PI * 2 / 14);
			int x = cx + (int) Math.round(Math.cos(a) * rx * 0.78 / px) * px;
			int y = cy + (int) Math.round(Math.sin(a) * ry * 0.78 / px) * px;
			int col = (i % 3 == 0) ? 0xFF2F4A35 : 0xFF3F5A3A;
			c.fill(x, y - px, x + px, y + px, col);
			if (i % 2 == 0) c.fill(x + px, y - 2 * px, x + 2 * px, y, col);
		}
		// Kerzen links und rechts
		float f1 = 0.6f + 0.4f * (float) (Math.sin(time * 9) * Math.sin(time * 5.3));
		float f2 = 0.6f + 0.4f * (float) (Math.sin(time * 8.1 + 2) * Math.sin(time * 4.7 + 1));
		dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.candle(c, cx - rx * 2 / 3, cy - ry / 3, 1f, f1);
		dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.candle(c, cx + rx * 2 / 3, cy - ry / 4, 1f, f2);
	}

	/** Halloween vorn: Nebel, der langsam über den vorderen Rand zieht. */
	private static void graveFront(Canvas c, int cx, int cy, int rx, int ry, int px, float time) {
		for (int band = 0; band < 3; band++) {
			int y = cy + ry / 2 + band * px * 2;
			for (int x = cx - rx - 6 * px; x < cx + rx + 6 * px; x += px) {
				double v = 0.5 + 0.5 * Math.sin((x * 0.11) + time * (0.8 + band * 0.3) + band * 1.7) * Math.sin(x * 0.05 - time * 0.4);
				int a = (int) Math.round(Math.max(0, v) * (26 - band * 6));
				if (a <= 2) continue;
				c.fill(x, y, x + px, y + px * 2, (a << 24) | 0x9690B6);
			}
		}
	}

	/** Gefüllte Ellipse aus waagerechten Streifen im Pixelraster. */
	static void ellipse(Canvas c, int cx, int cy, int rx, int ry, int px, int argb) {
		if (rx <= 0 || ry <= 0 || (argb >>> 24) == 0) return;
		for (int y = -ry; y < ry; y += px) {
			float fy = (y + px / 2f) / (float) ry;
			float f = 1f - fy * fy;
			if (f <= 0f) continue;
			int hw = Math.round(rx * (float) Math.sqrt(f) / px) * px;
			if (hw <= 0) continue;
			c.fill(cx - hw, cy + y, cx + hw, cy + y + px, argb);
		}
	}

	/**
	 * Cubiq: flacher Würfel als Sockel. Die Ecken liegen auf einem Kreis und drehen sich mit dem Winkel; von den Seiten
	 * sind nur die zum Betrachter gewandten zu sehen (zum Licht von links vorn hin heller).
	 */
	private void pedestal(Canvas c, Theme t, int cx, int cy, int radius, int ry, int thick, float squash, int px) {
		for (int i = 3; i >= 1; i--) {
			ellipse(c, cx, cy + thick, radius + i * 3 * px, ry + i * px, px, ColorMath.withAlpha(t.glow, 8 + (3 - i) * 7));
		}
		float spin = (float) Math.toRadians(angle);
		float r = radius * 0.92f;
		float[] xs = new float[4];
		float[] ys = new float[4];
		for (int k = 0; k < 4; k++) {
			double a = spin + Math.PI / 4 + k * Math.PI / 2;
			xs[k] = cx + (float) Math.cos(a) * r;
			ys[k] = cy + (float) Math.sin(a) * r * squash;
		}
		int lit = t.light ? 0xFFD2DBD4 : 0xFF29332C;
		int shade = t.light ? 0xFFA8B5AB : 0xFF0C100E;
		for (int k = 0; k < 4; k++) {
			int j = (k + 1) % 4;
			double normal = spin + Math.PI / 2 + k * Math.PI / 2;
			if (Math.sin(normal) <= 0) continue;
			float light = (float) (0.5 + 0.5 * Math.cos(normal - Math.PI * 0.75));
			polygon(c, new float[]{xs[k], xs[j], xs[j], xs[k]}, new float[]{ys[k], ys[j], ys[j] + thick, ys[k] + thick}, px,
					ColorMath.lerp(shade, lit, 0.2f + 0.7f * light));
		}
		// Oberseite in der Flächenfarbe, die Kanten als Linien in der Akzentfarbe – vorn kräftig, hinten gedämpft.
		polygon(c, xs, ys, px, t.surfaceHigh);
		for (int k = 0; k < 4; k++) {
			int j = (k + 1) % 4;
			boolean front = Math.sin(spin + Math.PI / 2 + k * Math.PI / 2) > 0;
			edge(c, xs[k], ys[k], xs[j], ys[j], px, front ? ColorMath.lerp(t.accent, t.surfaceHigh, 0.15f) : ColorMath.lerp(t.accent, t.surfaceHigh, 0.6f));
		}
	}

	/**
	 * Polarlicht: der Würfel-Sockel verschneit wie die schwebenden Inseln der Szene – Seiten aus nachtblauem Fels (zum Licht
	 * hin heller), oben eine Schneedecke, die als Saum über die sichtbaren Seiten hängt, darunter ein paar Eiszapfen und ein
	 * kühler Schein in Türkis.
	 */
	private void snowBlock(Canvas c, Theme t, int cx, int cy, int radius, int thick, float squash, int px) {
		dev.pwiepixel.cubiqclient.core.ui.winter.WinterPalette p = dev.pwiepixel.cubiqclient.core.ui.winter.WinterPalette.of(t.themeName);
		int ry = Math.max(px, Math.round(radius * squash));
		for (int i = 3; i >= 1; i--) {
			ellipse(c, cx, cy + thick, radius + i * 3 * px, ry + i * px, px, ColorMath.withAlpha(0xFF2AA894, 8 + (3 - i) * 7));
		}
		float spin = (float) Math.toRadians(angle);
		float r = radius * 0.92f;
		float[] xs = new float[4];
		float[] ys = new float[4];
		for (int k = 0; k < 4; k++) {
			double a = spin + Math.PI / 4 + k * Math.PI / 2;
			xs[k] = cx + (float) Math.cos(a) * r;
			ys[k] = cy + (float) Math.sin(a) * r * squash;
		}
		int lip = Math.max(px, Math.round(thick * 0.3f / px) * px);
		for (int k = 0; k < 4; k++) {
			int j = (k + 1) % 4;
			double normal = spin + Math.PI / 2 + k * Math.PI / 2;
			if (Math.sin(normal) <= 0) continue;
			float light = (float) (0.5 + 0.5 * Math.cos(normal - Math.PI * 0.75));
			polygon(c, new float[]{xs[k], xs[j], xs[j], xs[k]}, new float[]{ys[k], ys[j], ys[j] + thick, ys[k] + thick}, px,
					ColorMath.lerp(p.rockRight, p.rockLeft, light));
			// Schneesaum unter der Oberkante und drei kleine Eiszapfen an der Unterkante.
			polygon(c, new float[]{xs[k], xs[j], xs[j], xs[k]}, new float[]{ys[k], ys[j], ys[j] + lip, ys[k] + lip}, px,
					ColorMath.lerp(p.snowRight, p.snowLeft, light));
			for (int n = 1; n <= 3; n++) {
				float f = n / 4f;
				int ix = Math.round((xs[k] + (xs[j] - xs[k]) * f) / px) * px;
				int iy = Math.round((ys[k] + (ys[j] - ys[k]) * f + thick) / px) * px;
				c.fill(ix, iy, ix + px, iy + px * (n == 2 ? 3 : 2), p.ice);
			}
		}
		polygon(c, xs, ys, px, p.snowTop);
		for (int k = 0; k < 4; k++) {
			int j = (k + 1) % 4;
			boolean front = Math.sin(spin + Math.PI / 2 + k * Math.PI / 2) > 0;
			edge(c, xs[k], ys[k], xs[j], ys[j], px, front ? p.snowLit : p.snowLeft);
		}
	}

	/** Kante von (x0, y0) nach (x1, y1) im Pixelraster {@code px} (Dicke px): je Rasterzeile ein Stück. */
	static void edge(Canvas c, float x0, float y0, float x1, float y1, int px, int argb) {
		if (y1 < y0) {
			float tx = x0;
			x0 = x1;
			x1 = tx;
			float ty = y0;
			y0 = y1;
			y1 = ty;
		}
		int first = Math.round(y0 / px);
		int last = Math.round(y1 / px);
		for (int row = first; row <= last; row++) {
			float a = last == first ? 0f : ColorMath.clamp01((row - 0.5f - y0 / px) / ((y1 - y0) / px));
			float b = last == first ? 1f : ColorMath.clamp01((row + 0.5f - y0 / px) / ((y1 - y0) / px));
			float xa = x0 + (x1 - x0) * a;
			float xb = x0 + (x1 - x0) * b;
			int l = Math.round(Math.min(xa, xb) / px) * px;
			int r = Math.max(l + px, Math.round(Math.max(xa, xb) / px) * px);
			c.fill(l, row * px, r, row * px + px, argb);
		}
	}

	/** Konvexes Vieleck zeilenweise füllen; Zeilen und Ränder im Pixelraster {@code px}. */
	static void polygon(Canvas c, float[] xs, float[] ys, int px, int argb) {
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (float y : ys) {
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		int n = xs.length;
		for (int y = (int) Math.floor(minY / px) * px; y < maxY; y += px) {
			float sy = y + px / 2f;
			float left = Float.MAX_VALUE;
			float right = -Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float y1 = ys[i];
				float y2 = ys[j];
				if ((sy < y1) == (sy < y2)) continue;
				float x = xs[i] + (sy - y1) * (xs[j] - xs[i]) / (y2 - y1);
				left = Math.min(left, x);
				right = Math.max(right, x);
			}
			int x1 = Math.round(left / px) * px;
			int x2 = Math.round(right / px) * px;
			if (x2 > x1) c.fill(x1, y, x2, y + px, argb);
		}
	}

	/**
	 * Fuchsbau: eine Grasinsel – Grasblock-Oberseite, Erdband mit Wurzeln und Steinchen (wandern mit der Drehung), ein Ring
	 * aus Herbstlaub, kleine Blumen und Glühwürmchen; in der Mitte glimmt warme Glut, darunter Feuerschein.
	 */
	private void islandBack(Canvas c, Theme t, int cx, int cy, int radius, int ry, int thick, float squash, int px, float time) {
		for (int i = 3; i >= 1; i--) {
			ellipse(c, cx, cy + thick, radius + i * 3 * px, ry + i * px, px, ColorMath.withAlpha(t.faceGlow, 8 + (3 - i) * 7));
		}
		dirtBand(c, cx, cy, radius, ry, thick, px);
		// Oberfläche: Gras, Kante im Schatten, Licht der tiefen Sonne von links.
		ellipse(c, cx, cy, radius, ry, px, 0xFF3E6A2C);
		ellipse(c, cx, cy, radius - px, Math.max(px, ry - px), px, 0xFF5A8A3A);
		ellipse(c, cx - px, cy, radius - 3 * px, Math.max(px, ry - 2 * px), px, 0xFF6A9A42);
		leafRing(c, t, cx, cy, radius, squash, px, time, true);
		flowers(c, t, cx, cy, radius, squash, px, time, true);
		// Mitte: glimmende Glut im Gras, pulsiert leise.
		int pr = Math.max(2 * px, Math.round(radius * 0.36f / px) * px);
		int pry = Math.max(px, Math.round(pr * squash));
		float pulse = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(time * 2.1));
		ellipse(c, cx, cy, pr + px, pry + px, px, 0xFF4A2A1A);
		ellipse(c, cx, cy, pr, pry, px, ColorMath.lerp(t.faceOff, t.faceOn, 0.55f * pulse));
		ellipse(c, cx, cy, Math.max(px, pr - 2 * px), Math.max(px, pry - px), px, ColorMath.withAlpha(t.faceHot, Math.round(80 * pulse)));
	}

	/** Seitenband der Insel: oben eine Graskante, darunter Erde (nach unten dunkler), Wurzeln und Steinchen. */
	private void dirtBand(Canvas c, int cx, int cy, int rx, int ry, int thick, int px) {
		int top = 0xFF9A6A44;
		int deep = 0xFF4E3420;
		int edge = 0xFF3B2614;
		for (int y = 0; y < ry + thick; y += px) {
			int hw;
			if (y < thick) {
				hw = rx;
			} else {
				float fy = (y - thick + px / 2f) / (float) ry;
				float f = 1f - fy * fy;
				if (f <= 0f) continue;
				hw = Math.round(rx * (float) Math.sqrt(f) / px) * px;
			}
			if (hw <= 0) continue;
			float depth = y / (float) Math.max(px, ry + thick);
			c.fill(cx - hw, cy + y, cx + hw, cy + y + px, ColorMath.lerp(top, deep, depth));
			c.fill(cx - hw, cy + y, cx - hw + px, cy + y + px, edge);
			c.fill(cx + hw - px, cy + y, cx + hw, cy + y + px, edge);
		}
		// Graskante, die über die Erde hängt.
		int grassY = cy + Math.max(px, thick / 3);
		c.fill(cx - rx + px, grassY, cx + rx - px, grassY + px, 0xFF4C7A30);
		float phase = -(float) Math.toRadians(angle);
		int rows = Math.max(1, thick / px);
		for (int i = 0; i < 18; i++) {
			double a = phase + i * (Math.PI * 2 / 18);
			double s = Math.sin(a);
			if (s <= 0.05) continue;
			int x = cx + (int) Math.round(Math.cos(a) * (rx - px) / px) * px;
			int row = 1 + (i * 7) % rows;
			int y = cy + (int) Math.round(s * ry / px) * px + row * px;
			if (y + px > cy + ry + thick - px) y = cy + ry + thick - 2 * px;
			if ((i & 1) == 0) {
				// Steinchen.
				c.fill(x, y, x + px, y + px, 0xFF8A8078);
			} else {
				// Wurzel schräg nach unten.
				c.fill(x, y, x + px, y + px, 0xFF2C1C0E);
				c.fill(x + px, y + px, x + 2 * px, y + 2 * px, 0xFF2C1C0E);
			}
		}
	}

	/** Ring aus Herbstlaub: Blätter auf einer Ellipse, ein Glutschimmer läuft im Kreis. */
	private void leafRing(Canvas c, Theme t, int cx, int cy, int rx, float squash, int px, float time, boolean back) {
		float r = rx * 0.72f;
		float spin = -(float) Math.toRadians(angle);
		float head = (time * 0.6f) % 1f;
		int[] tones = {0xFFE07A3A, 0xFFF0A050, 0xFFB4502A, 0xFFE8B04A, 0xFFF3A6C8};
		for (int i = 0; i < RING_DOTS; i++) {
			double a = spin + i * (Math.PI * 2 / RING_DOTS);
			double s = Math.sin(a);
			if (back != (s < 0)) continue;
			if (i % 4 == 3) continue;
			int x = cx + (int) Math.round(Math.cos(a) * r / px) * px;
			int y = cy + (int) Math.round(s * r * squash / px) * px;
			float pos = i / (float) RING_DOTS;
			float behind = (head - pos + 1f) % 1f;
			float glint = Math.max(0f, 1f - behind * 6f);
			int color = ColorMath.lerp(tones[i % tones.length], 0xFFFFE1A6, glint * 0.8f);
			if (glint > 0.3f) c.fill(x - px, y - px, x + 3 * px, y + px, ColorMath.withAlpha(t.faceGlow, Math.round(50 * glint)));
			c.fill(x, y, x + px * 2, y + px, color);
		}
	}

	/** Kleine Blumen am Rand (rosa, gelb, weiß), über jeder zweiten schwebt ein Glühwürmchen, das aufleuchtet. */
	private void flowers(Canvas c, Theme t, int cx, int cy, int rx, float squash, int px, float time, boolean back) {
		float r = rx * 0.9f;
		float spin = -(float) Math.toRadians(angle);
		int[] heads = {0xFFFF8AC0, 0xFFFFD84A, 0xFFF4EFE8};
		for (int i = 0; i < RING_SPOTS; i++) {
			double a = spin + (i + 0.5) * (Math.PI * 2 / RING_SPOTS);
			double s = Math.sin(a);
			if (back != (s < 0)) continue;
			int x = cx + (int) Math.round(Math.cos(a) * r / px) * px;
			int y = cy + (int) Math.round(s * r * squash / px) * px - px;
			c.fill(x, y - 2 * px, x + px, y + px, 0xFF3E6A2C);
			c.fill(x + px, y - px, x + 2 * px, y, 0xFF5A8A3A);
			int head = heads[i % heads.length];
			c.fill(x - px, y - 3 * px, x + 2 * px, y - 2 * px, head);
			c.fill(x, y - 4 * px, x + px, y - 3 * px, ColorMath.lerp(head, 0xFFFFFFFF, 0.3f));
			if (i % 2 == 0) {
				float glow = Math.max(0f, (float) Math.sin(time * (1.1f + i * 0.2f) + i * 1.7f));
				if (glow > 0.2f) {
					int fy = y - 7 * px - Math.round((float) Math.sin(time * 0.9f + i) * px);
					c.fill(x - px, fy - px, x + 2 * px, fy + 2 * px, ColorMath.withAlpha(0xFFFFE27A, Math.round(50 * glow)));
					c.fill(x, fy, x + px, fy + px, ColorMath.withAlpha(0xFFFFF4A8, Math.round(255 * glow)));
				}
			}
		}
	}

}
