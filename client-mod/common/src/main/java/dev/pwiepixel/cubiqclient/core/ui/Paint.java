package dev.pwiepixel.cubiqclient.core.ui;

/**
 * Zeichenhilfen im Cubiq-Stil (wie der Launcher): ruhige, flache Flächen mit abgerundeten Ecken und einer feinen
 * Kante, die Akzentfarbe für Aktionen und Auswahl, ein weicher Schein statt harter Kanten. Alles aus Rechtecken im
 * GUI-Raster (1 Bildpunkt = 1 GUI-Pixel) – es skaliert mit der GUI-Größe und sieht in jeder Minecraft-Version gleich
 * aus. Keine Texturen. Winternacht, Fuchsbau und das Halloween-Event tauschen einzelne Teile aus (Laternen, Linien).
 */
public final class Paint {
	/** Größte Rundung von Knöpfen, Feldern und Kacheln (GUI-Pixel). */
	public static final int RADIUS = 4;
	/** Rundung von Fenstern. */
	public static final int WINDOW_RADIUS = 6;

	private Paint() {
	}

	/** Waagerechte Linie (y einschließlich). */
	public static void hLine(Canvas c, int x1, int x2, int y, int argb) {
		c.fill(Math.min(x1, x2), y, Math.max(x1, x2) + 1, y + 1, argb);
	}

	/** Senkrechte Linie (y2 einschließlich). */
	public static void vLine(Canvas c, int x, int y1, int y2, int argb) {
		c.fill(x, Math.min(y1, y2), x + 1, Math.max(y1, y2) + 1, argb);
	}

	/** Rahmen, 1 px. */
	public static void outline(Canvas c, int x, int y, int w, int h, int argb) {
		c.fill(x, y, x + w, y + 1, argb);
		c.fill(x, y + h - 1, x + w, y + h, argb);
		c.fill(x, y + 1, x + 1, y + h - 1, argb);
		c.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	/**
	 * Einrückung je Zeile für eine abgerundete Ecke (Viertelkreis).
	 * {@code radius} wird auf die halbe Höhe begrenzt.
	 */
	public static int[] corner(int radius) {
		int r = Math.max(0, radius);
		int[] inset = new int[r];
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			double dx = r - Math.sqrt(Math.max(0, r * (double) r - dy * dy));
			inset[i] = (int) Math.round(dx);
		}
		return inset;
	}

	/** Rundung, die zu einem Element dieser Größe passt (kleine Elemente runden weniger, höchstens {@link #RADIUS}). */
	public static int radius(int w, int h) {
		return Math.max(0, Math.min(RADIUS, Math.min(w, h) / 3));
	}

	/** Abgerundete Fläche. Zeilen mit gleicher Einrückung werden zu einem Rechteck zusammengefasst. */
	public static void roundRect(Canvas c, int x, int y, int w, int h, int radius, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		int[] inset = corner(r);
		// Die Einrückung nimmt nach innen ab; Zeilen ohne Einrückung gehören zur Mitte.
		int rows = 0;
		while (rows < r && inset[rows] > 0) rows++;
		for (int i = 0; i < rows; ) {
			int j = i + 1;
			while (j < rows && inset[j] == inset[i]) j++;
			c.fill(x + inset[i], y + i, x + w - inset[i], y + j, argb);
			c.fill(x + inset[i], y + h - j, x + w - inset[i], y + h - i, argb);
			i = j;
		}
		if (h - 2 * rows > 0) c.fill(x, y + rows, x + w, y + h - rows, argb);
	}

	/** Abgerundeter Rahmen (1 px), lückenlos auch bei großen Rundungen. */
	public static void roundOutline(Canvas c, int x, int y, int w, int h, int radius, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		if (r == 0) {
			outline(c, x, y, w, h, argb);
			return;
		}
		int[] inset = corner(r);
		// Oberste und unterste Zeile durchgehend …
		c.fill(x + inset[0], y, x + w - inset[0], y + 1, argb);
		c.fill(x + inset[0], y + h - 1, x + w - inset[0], y + h, argb);
		// … in den Ecken je Zeile das Stück bis zur Einrückung der Zeile davor …
		for (int i = 1; i < r; i++) {
			int d = inset[i];
			int end = Math.max(d + 1, inset[i - 1]);
			c.fill(x + d, y + i, x + end, y + i + 1, argb);
			c.fill(x + w - end, y + i, x + w - d, y + i + 1, argb);
			c.fill(x + d, y + h - i - 1, x + end, y + h - i, argb);
			c.fill(x + w - end, y + h - i - 1, x + w - d, y + h - i, argb);
		}
		// … dann die geraden Seiten.
		if (h - 2 * r > 0) {
			c.fill(x, y + r, x + 1, y + h - r, argb);
			c.fill(x + w - 1, y + r, x + w, y + h - r, argb);
		}
	}

	/** Weicher Schatten unter einer abgerundeten Fläche (drei Ringe). */
	public static void shadow(Canvas c, int x, int y, int w, int h, int radius, float alpha) {
		for (int i = 3; i >= 1; i--) {
			int a = Math.round(26 * alpha) / i;
			if (a <= 0) continue;
			roundRect(c, x - i, y - i + 1, w + i * 2, h + i * 2, radius + i, (a << 24));
		}
	}

	// --- Flächen ---

	/** Kleine Fläche mit abgerundeten Ecken (Rundung passend zur Größe). */
	public static void block(Canvas c, int x, int y, int w, int h, int argb) {
		roundRect(c, x, y, w, h, radius(w, h), argb);
	}

	/** Rahmen (1 px) mit abgerundeten Ecken. */
	public static void frame(Canvas c, int x, int y, int w, int h, int argb) {
		roundOutline(c, x, y, w, h, radius(w, h), argb);
	}

	/** Fläche mit feiner Kante – Grundform von Kacheln, Knöpfen und Karten. */
	public static void panel(Canvas c, int x, int y, int w, int h, int fill, int edge) {
		Theme t = Theme.get();
		int r = radius(w, h);
		roundRect(c, x, y, w, h, r, edge);
		if (w < 3 || h < 3) return;
		roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		if (t.winter && w >= 10 && h >= 8) {
			// Winternacht: Reif in der Ecke oben links.
			int rime = ColorMath.withAlpha(0xFFFFFFFF, t.light ? 0xB0 : 0x38);
			c.fill(x + 2, y + 1, x + 5, y + 2, rime);
			c.fill(x + 1, y + 2, x + 2, y + 4, rime);
		} else if (t.fox && w >= 10 && h >= 8) {
			// Fuchsbau: die tiefe Sonne legt einen warmen Saum in die Ecke oben links.
			int sun = ColorMath.withAlpha(0xFFFFB878, t.light ? 0x90 : 0x30);
			c.fill(x + 2, y + 1, x + 5, y + 2, sun);
			c.fill(x + 1, y + 2, x + 2, y + 3, sun);
		}
	}

	/** Mulde (eingelassene Fläche) für Felder, Listen und Symbole: tiefster Grund mit Kante. */
	public static void well(Canvas c, int x, int y, int w, int h, int edge) {
		Theme t = Theme.get();
		int r = radius(w, h);
		roundRect(c, x, y, w, h, r, edge);
		if (w < 3 || h < 3) return;
		roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), t.deep);
	}

	/** Fenster: weicher Schatten, feine Kante, Fensterfläche. */
	public static void window(Canvas c, int x, int y, int w, int h) {
		Theme t = Theme.get();
		for (int i = 4; i >= 1; i--) {
			roundRect(c, x - i, y - i + 2, w + 2 * i, h + 2 * i, WINDOW_RADIUS + i, (24 / i) << 24);
		}
		roundRect(c, x, y, w, h, WINDOW_RADIUS, t.border);
		roundRect(c, x + 1, y + 1, w - 2, h - 2, WINDOW_RADIUS - 1, t.background);
	}

	/** Kopfband oben in einem {@link #window} (Höhe {@code h}): Fläche {@code fill} in der Rundung des Fensters, Linie darunter. */
	public static void windowHeader(Canvas c, int x, int y, int w, int h, int fill, int line) {
		if (w < 4 || h < 3) return;
		roundRect(c, x + 1, y + 1, w - 2, h - 1, WINDOW_RADIUS - 1, fill);
		c.fill(x + 1, y + Math.max(1, h - WINDOW_RADIUS), x + w - 1, y + h - 1, fill);
		c.fill(x + 1, y + h - 1, x + w - 1, y + h, line);
	}

	/** Symbol in einer kleinen Mulde (Kachel, Kopfzeile); {@code px} = Pixel je Symbolpunkt, {@code glow} = Auswahl. */
	public static void iconWell(Canvas c, int x, int y, int px, String icon, int argb, float glow) {
		Theme t = Theme.get();
		int size = px * 8 + 6;
		float g = ColorMath.clamp01(glow);
		panel(c, x, y, size, size, ColorMath.lerp(t.deep, t.accent, 0.16f * g), ColorMath.lerp(t.border, t.accent, 0.5f * g));
		Icons.draw(c, icon, x + 3, y + 3, px, argb);
	}

	/**
	 * Weicher Schein um ein Rechteck: drei abgerundete Ringe, die nach außen verblassen. Wird nach der Fläche
	 * gezeichnet, überdeckt sie also nicht.
	 */
	public static void glow(Canvas c, int x, int y, int w, int h, int argb, float strength) {
		float s = ColorMath.clamp01(strength);
		if (s < 0.02f) return;
		int r = radius(w, h);
		roundOutline(c, x - 1, y - 1, w + 2, h + 2, r + 1, ColorMath.withAlpha(argb, Math.round(72 * s)));
		roundOutline(c, x - 2, y - 2, w + 4, h + 4, r + 2, ColorMath.withAlpha(argb, Math.round(32 * s)));
		roundOutline(c, x - 3, y - 3, w + 6, h + 6, r + 3, ColorMath.withAlpha(argb, Math.round(12 * s)));
	}

	/**
	 * Weicher, runder Schein (wie {@code radial-gradient} im Launcher): ineinanderliegende Ellipsen mit sehr wenig
	 * Deckkraft, nach außen linear auslaufend – so viele, dass keine Ringe zu sehen sind. {@code alpha} = Deckkraft in der
	 * Mitte (0..255); Zeilen außerhalb von {@code width}×{@code height} werden übersprungen.
	 */
	public static void radialGlow(Canvas c, int cx, int cy, int rx, int ry, int argb, int alpha, int width, int height) {
		if (rx <= 0 || ry <= 0 || alpha <= 0) return;
		int rings = Math.max(4, Math.min(14, alpha / 4));
		int color = ColorMath.withAlpha(argb, Math.max(1, Math.round(alpha / (float) rings)));
		for (int k = rings; k >= 1; k--) {
			int rxk = rx * k / rings;
			int ryk = ry * k / rings;
			if (ryk <= 0 || rxk <= 0) continue;
			int step = Math.max(1, ryk / 24);
			for (int y = -ryk; y < ryk; y += step) {
				int y1 = cy + y;
				int y2 = Math.min(cy + ryk, y1 + step);
				if (y2 <= 0 || y1 >= height) continue;
				float fy = (y + step / 2f) / ryk;
				int hw = Math.round(rxk * (float) Math.sqrt(Math.max(0f, 1f - fy * fy)));
				int x1 = Math.max(0, cx - hw);
				int x2 = Math.min(width, cx + hw);
				if (x2 > x1) c.fill(x1, Math.max(0, y1), x2, Math.min(height, y2), color);
			}
		}
	}

	// --- Leuchtende Fläche ---

	/**
	 * Fläche, die mit der Auswahl aufleuchtet: {@code level} 0 = ruhige Fläche, 1 = Akzentfarbe mit Lichtkante und
	 * weichem Schein; {@code flash} hellt kurz auf (Drücken). In Winternacht und Fuchsbau eine Laterne.
	 */
	public static void accentFace(Canvas c, int x, int y, int w, int h, float level, float flash) {
		Theme t = Theme.get();
		if (t.halloween) {
			dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.lamp(c, x, y, w, h, level, flash);
			return;
		}
		if (t.winter) {
			dev.pwiepixel.cubiqclient.core.ui.winter.Frost.lantern(c, x, y, w, h, level, flash);
			return;
		}
		if (t.fox) {
			dev.pwiepixel.cubiqclient.core.ui.fox.Ember.lantern(c, x, y, w, h, level, flash);
			return;
		}
		float l = ColorMath.clamp01(level);
		if (l > 0.02f) glow(c, x, y, w, h, t.faceGlow, l * 0.55f);
		accentFill(c, x, y, w, h, ColorMath.lerp(t.faceOff, t.faceOn, l), ColorMath.lerp(t.faceOffEdge, t.faceOnEdge, l), l);
		float f = ColorMath.clamp01(flash);
		if (f > 0.02f && w > 2 && h > 2) {
			roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius(w, h) - 1), ColorMath.withAlpha(0xFFFFFFFF, Math.round(110 * f)));
		}
	}

	/** Fläche mit Kante und – je nach {@code light} – der hellen Kante oben wie bei den Knöpfen des Launchers. */
	private static void accentFill(Canvas c, int x, int y, int w, int h, int fill, int edge, float light) {
		int r = radius(w, h);
		roundRect(c, x, y, w, h, r, edge);
		if (w < 3 || h < 3) return;
		roundRect(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		if (light > 0.02f && w > 6 && h > 6) {
			c.fill(x + Math.max(2, r), y + 1, x + w - Math.max(2, r), y + 2, ColorMath.withAlpha(0xFFFFFFFF, Math.round(46 * light)));
		}
	}

	/** Schriftfarbe auf einer leuchtenden Fläche mit Stärke {@code level}. */
	public static int accentFaceText(float level) {
		Theme t = Theme.get();
		float l = ColorMath.clamp01(level);
		// Kurz vor der Mitte umschalten, damit die Schrift nie grau auf Grau steht.
		float k = l < 0.35f ? 0f : (l > 0.6f ? 1f : (l - 0.35f) / 0.25f);
		return ColorMath.lerp(t.faceText, t.faceTextOn, k);
	}

	/** Beschriftete leuchtende Fläche (Wahlknopf: gewählt = 1). */
	public static void accentButton(Canvas c, int x, int y, int w, int h, String label, float level, float flash) {
		accentFace(c, x, y, w, h, level, flash);
		String s = c.textWidth(label) <= w - 10 ? label : c.clip(label, w - 10);
		c.text(s, x + (w - c.textWidth(s)) / 2, y + (h - 8) / 2, accentFaceText(level), false);
	}

	// --- Linien ---

	/**
	 * Waagerechte Linie: ohne {@code glow} eine feine Trennlinie (1 px), mit {@code glow} eine kräftige Linie (2 px) mit
	 * weichem Schein – z. B. unter dem aktiven Reiter.
	 */
	public static void lineH(Canvas c, int x1, int x2, int y, int argb, float glow) {
		if (x2 <= x1) return;
		Theme t = Theme.get();
		if (t.halloween) {
			dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.lineH(c, x1, x2, y, argb, glow);
			return;
		}
		if (t.winter) {
			dev.pwiepixel.cubiqclient.core.ui.winter.Frost.lineH(c, x1, x2, y, argb, glow);
			return;
		}
		if (t.fox) {
			dev.pwiepixel.cubiqclient.core.ui.fox.Ember.lineH(c, x1, x2, y, argb, glow);
			return;
		}
		float g = ColorMath.clamp01(glow);
		if (g <= 0.02f) {
			c.fill(x1, y, x2, y + 1, argb);
			return;
		}
		c.fill(x1, y, x2, y + 2, argb);
		int soft = ColorMath.withAlpha(t.glow, Math.round(36 * g));
		c.fill(x1 + 1, y - 1, x2 - 1, y, soft);
		c.fill(x1 + 1, y + 2, x2 - 1, y + 3, soft);
	}

	/** Senkrechte Linie, wie {@link #lineH}. */
	public static void lineV(Canvas c, int x, int y1, int y2, int argb, float glow) {
		if (y2 <= y1) return;
		Theme t = Theme.get();
		if (t.halloween) {
			dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.lineV(c, x, y1, y2, argb, glow);
			return;
		}
		if (t.winter) {
			dev.pwiepixel.cubiqclient.core.ui.winter.Frost.lineV(c, x, y1, y2, argb, glow);
			return;
		}
		if (t.fox) {
			dev.pwiepixel.cubiqclient.core.ui.fox.Ember.lineV(c, x, y1, y2, argb, glow);
			return;
		}
		float g = ColorMath.clamp01(glow);
		if (g <= 0.02f) {
			c.fill(x, y1, x + 1, y2, argb);
			return;
		}
		c.fill(x, y1, x + 2, y2, argb);
		int soft = ColorMath.withAlpha(t.glow, Math.round(36 * g));
		c.fill(x - 1, y1 + 1, x, y2 - 1, soft);
		c.fill(x + 2, y1 + 1, x + 3, y2 - 1, soft);
	}

	/** Text, auf {@code maxWidth} gekürzt. */
	public static void textClipped(Canvas c, String text, int x, int y, int maxWidth, int argb, boolean shadow) {
		if ((argb >>> 24) < 8) return;
		String s = c.textWidth(text) <= maxWidth ? text : c.clip(text, Math.max(0, maxWidth - c.textWidth("…"))) + "…";
		c.text(s, x, y, argb, shadow);
	}

	/** Mittig ausgerichteter Text. */
	public static void textCentered(Canvas c, String text, int centerX, int y, int argb, boolean shadow) {
		if ((argb >>> 24) < 8) return;
		c.text(text, centerX - c.textWidth(text) / 2, y, argb, shadow);
	}

	/** Rechtsbündiger Text (x = rechte Kante). */
	public static void textRight(Canvas c, String text, int right, int y, int argb, boolean shadow) {
		if ((argb >>> 24) < 8) return;
		c.text(text, right - c.textWidth(text), y, argb, shadow);
	}

	/**
	 * Umbricht Text auf {@code maxWidth} (an Leerzeichen) und liefert die Zeilen.
	 * Zu lange Wörter mit Bindestrich ("Cubiq-Online-Funktionen") werden hinter einem
	 * Bindestrich getrennt; was dann noch übersteht, kürzt der Aufrufer.
	 * Eigene Umbruchlogik, weil die Vanilla-Funktion je Version anders heißt.
	 */
	public static java.util.List<String> wrap(Canvas c, String text, int maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<String>();
		if (text == null || text.isEmpty() || maxWidth <= 0) return lines;
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (c.textWidth(candidate) <= maxWidth) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			String rest = word;
			if (line.length() > 0) {
				// Passt der Anfang eines Bindestrich-Worts noch in die Zeile ("Kein Schadens-")?
				int cut = hyphenCut(c, line + " ", rest, maxWidth);
				if (cut > 0) {
					line.append(' ').append(rest, 0, cut);
					rest = rest.substring(cut);
				}
				lines.add(line.toString());
				line.setLength(0);
			}
			// Das Wort allein ist zu breit: an Bindestrichen trennen (Trennzeichen bleibt vorne).
			while (c.textWidth(rest) > maxWidth) {
				int cut = hyphenCut(c, "", rest, maxWidth);
				if (cut < 0) break;
				lines.add(rest.substring(0, cut));
				rest = rest.substring(cut);
			}
			line.append(rest);
		}
		if (line.length() > 0) lines.add(line.toString());
		return lines;
	}

	/** Längster Anfang von {@code word} bis einschließlich eines Bindestrichs, der hinter {@code prefix} passt; sonst -1. */
	private static int hyphenCut(Canvas c, String prefix, String word, int maxWidth) {
		for (int i = word.length() - 2; i > 0; i--) {
			if (word.charAt(i) == '-' && c.textWidth(prefix + word.substring(0, i + 1)) <= maxWidth) return i + 1;
		}
		return -1;
	}

	/** Fügt umbrochene Zeilen wieder zusammen (nach einem Trenn-Bindestrich ohne Leerzeichen). */
	public static String join(java.util.List<String> lines, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < lines.size(); i++) {
			String l = lines.get(i);
			if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '-') sb.append(' ');
			sb.append(l);
		}
		return sb.toString();
	}

	/** Mehrzeiliger Text; liefert das y unter der letzten Zeile. */
	public static int paragraph(Canvas c, String text, int x, int y, int maxWidth, int lineHeight, int argb) {
		for (String line : wrap(c, text, maxWidth)) {
			c.text(line, x, y, argb, false);
			y += lineHeight;
		}
		return y;
	}

	// --- Bedienelemente ---

	/**
	 * An/Aus-Schalter als Pille wie im Launcher: Schiene in Linienfarbe, an in der Akzentfarbe, heller Knopf. {@code
	 * progress} 0 = aus, 1 = an – dazwischen wandert der Knopf (weiche Animation).
	 */
	public static void toggle(Canvas c, int x, int y, int w, int h, float progress, boolean hover) {
		Theme t = Theme.get();
		if (t.halloween) {
			dev.pwiepixel.cubiqclient.core.ui.halloween.HalloweenLook.toggle(c, x, y, w, h, progress, hover);
			return;
		}
		float p = ColorMath.clamp01(progress);
		int off = hover ? ColorMath.lerp(t.track, t.textDim, 0.3f) : t.track;
		roundRect(c, x, y, w, h, h / 2, ColorMath.lerp(off, hover ? t.accentHover : t.accent, p));
		int k = Math.max(3, h - 4);
		int kx = x + 2 + Math.round((w - k - 4) * p);
		int ky = y + (h - k) / 2;
		roundRect(c, kx, ky + 1, k, k, k / 2, 0x30000000);
		roundRect(c, kx, ky, k, k, k / 2, t.light ? 0xFFFFFFFF : ColorMath.lerp(0xFFE3E6F7, 0xFFFFFFFF, p));
	}

	/** Schieberegler: Schiene, gefüllter Teil in der Akzentfarbe, runder Knopf; die Trefferfläche verwaltet der Aufrufer. */
	public static void slider(Canvas c, int x, int y, int w, int h, float fraction, boolean hover) {
		Theme t = Theme.get();
		float f = ColorMath.clamp01(fraction);
		int trackY = y + h / 2 - 2;
		roundRect(c, x, trackY, w, 4, 2, t.track);
		int filled = Math.round(w * f);
		if (filled > 0) roundRect(c, x, trackY, filled, 4, 2, hover ? t.accentHover : t.accent);
		int k = Math.min(h, 10);
		int kx = x + filled - k / 2;
		int ky = y + (h - k) / 2;
		if (hover) glow(c, kx, ky, k, k, t.glow, 0.7f);
		roundRect(c, kx, ky + 1, k, k, k / 2, 0x38000000);
		roundRect(c, kx, ky, k, k, k / 2, hover ? t.accentHover : t.accent);
		roundRect(c, kx + 2, ky + 2, k - 4, k - 4, (k - 4) / 2, t.light ? 0xFFFFFFFF : 0xFFF2F4FD);
	}

	/**
	 * Fortschritt wie im Launcher: schlanker, abgerundeter Balken in der Akzentfarbe mit heller Kante und weichem Schein.
	 * {@code fraction} &lt; 0 = unbestimmt: ein Lichtstreifen läuft immer wieder durch – ohne Animation liegt er ruhig
	 * und halb durchsichtig über der ganzen Breite. {@code time} in Sekunden.
	 */
	public static void progress(Canvas c, int x, int y, int w, int h, float fraction, float time, boolean animated) {
		Theme t = Theme.get();
		if (w <= 0 || h <= 0) return;
		int r = h / 2;
		roundRect(c, x, y, w, h, r, ColorMath.lerp(t.surfaceHigh, t.text, 0.08f));
		if (fraction < 0f) {
			if (!animated) {
				roundRect(c, x, y, w, h, r, ColorMath.withAlpha(t.accentHover, 0x80));
				return;
			}
			// Streifen (35 % der Breite) von links außerhalb bis rechts außerhalb, weich beschleunigt und gebremst.
			float p = Anim.easeInOut((time % 1.3f) / 1.3f);
			int sw = Math.max(h * 2, Math.round(w * 0.35f));
			int sx = x - sw + Math.round((w + sw) * p);
			for (int i = 0; i < 6; i++) {
				int a = x + Math.max(0, Math.min(w, sx - x + sw * i / 6));
				int b = x + Math.max(0, Math.min(w, sx - x + sw * (i + 1) / 6));
				float k = 1f - Math.abs(i - 2.5f) / 3f;
				if (b > a) c.fill(a, y, b, y + h, ColorMath.withAlpha(t.accentHover, Math.round(230 * k)));
			}
			return;
		}
		int fw = Math.round(w * ColorMath.clamp01(fraction));
		if (fw <= 0) return;
		glow(c, x, y, fw, h, t.glow, 0.4f);
		roundRect(c, x, y, fw, h, r, t.accent);
		if (fw > h && h >= 4) c.fill(x + r, y + 1, x + fw - r, y + 2, ColorMath.withAlpha(t.accentHover, 0xC0));
	}

	/**
	 * Knopf mit Beschriftung: {@code primary} = Akzentfarbe mit dunkler Schrift (wie „Spielen“ im Launcher), sonst eine
	 * ruhige Fläche, die beim Überfahren heller wird. In Winternacht/Fuchsbau ist der Hauptknopf eine Laterne.
	 */
	public static void button(Canvas c, int x, int y, int w, int h, String label, boolean primary, boolean hover) {
		Theme t = Theme.get();
		boolean seasonal = t.halloween || t.winter || t.fox;
		int ty = y + (h - 8) / 2 + (h >= 16 ? 0 : 1);
		String s = c.clip(label, w - 6);
		if (primary) {
			if (seasonal) accentFace(c, x, y, w, h, hover ? 1f : 0.82f, 0f);
			else primaryFace(c, x, y, w, h, hover ? 1f : 0f);
			c.text(s, x + (w - c.textWidth(s)) / 2, ty, accentFaceText(1f), false);
			return;
		}
		if (seasonal) {
			panel(c, x, y, w, h, hover ? t.surfaceHover : t.surfaceHigh, hover ? hoverEdge(t) : t.border);
			if (hover) lineH(c, x + 3, x + w - 3, y + h - 3, t.accentText, 0f);
			c.text(s, x + (w - c.textWidth(s)) / 2, ty, t.text, false);
			return;
		}
		secondaryFace(c, x, y, w, h, hover ? 1f : 0f);
		c.text(s, x + (w - c.textWidth(s)) / 2, ty, secondaryText(hover ? 1f : 0f), false);
	}

	/**
	 * Fläche eines Hauptknopfs (wie {@code .btn-primary} im Launcher): Akzentfarbe mit heller Kante oben; {@code hover}
	 * 0..1 hellt auf und lässt einen weichen Schein wachsen.
	 */
	public static void primaryFace(Canvas c, int x, int y, int w, int h, float hover) {
		Theme t = Theme.get();
		float v = ColorMath.clamp01(hover);
		if (v > 0.02f) glow(c, x, y, w, h, t.glow, 0.55f * v);
		accentFill(c, x, y, w, h, ColorMath.lerp(t.accent, t.accentHover, v), ColorMath.lerp(t.faceOnEdge, t.accent, v), 1f);
	}

	/** Fläche eines Nebenknopfs (wie {@code .btn-ghost} im Launcher); {@code hover} 0..1 hellt Fläche und Kante auf. */
	public static void secondaryFace(Canvas c, int x, int y, int w, int h, float hover) {
		Theme t = Theme.get();
		float v = ColorMath.clamp01(hover);
		panel(c, x, y, w, h, ColorMath.lerp(t.surfaceHigh, t.surfaceHover, v), ColorMath.lerp(t.border, hoverEdge(t), v));
	}

	/** Schrift auf einem Nebenknopf: etwas gedämpft, unter der Maus voll. */
	public static int secondaryText(float hover) {
		Theme t = Theme.get();
		return ColorMath.lerp(ColorMath.lerp(t.text, t.textDim, 0.3f), t.text, ColorMath.clamp01(hover));
	}

	/** Fokusrahmen für die Tastatur (wie {@code :focus-visible} im Launcher): feiner Ring mit etwas Abstand. */
	public static void focusRing(Canvas c, int x, int y, int w, int h) {
		Theme t = Theme.get();
		roundOutline(c, x - 2, y - 2, w + 4, h + 4, radius(w, h) + 2, t.accentText);
	}

	/** Kante einer Fläche unter der Maus. */
	public static int hoverEdge(Theme t) {
		return t.halloween || t.winter || t.fox ? ColorMath.lerp(t.border, t.textDim, 0.5f) : t.bevelLight;
	}

	/** Quadratischer Symbol-Knopf. */
	public static void iconButton(Canvas c, int x, int y, int size, String icon, boolean hover, boolean primary) {
		Theme t = Theme.get();
		boolean seasonal = t.halloween || t.winter || t.fox;
		int px = Math.max(1, (size - 4) / 8);
		int iconSize = px * 8;
		int ix = x + (size - iconSize) / 2;
		int iy = y + (size - iconSize) / 2;
		if (primary) {
			if (seasonal) accentFace(c, x, y, size, size, hover ? 1f : 0.82f, 0f);
			else primaryFace(c, x, y, size, size, hover ? 1f : 0f);
			Icons.draw(c, icon, ix, iy, px, t.faceTextOn);
			return;
		}
		if (seasonal) {
			panel(c, x, y, size, size, hover ? t.surfaceHover : t.surfaceHigh, hover ? hoverEdge(t) : t.border);
			if (hover) glow(c, x, y, size, size, t.glow, 0.45f);
		} else {
			secondaryFace(c, x, y, size, size, hover ? 1f : 0f);
		}
		Icons.draw(c, icon, ix, iy, px, hover ? t.accentText : t.text);
	}

	/** Tastenkappe mit Beschriftung (wie {@code <kbd>} im Launcher: Kante, Fläche, dunkle Unterkante); liefert die Breite. */
	public static int keycap(Canvas c, int x, int y, String label, int maxWidth) {
		Theme t = Theme.get();
		String s = c.textWidth(label) + 8 <= maxWidth ? label : c.clip(label, Math.max(0, maxWidth - 8));
		int w = Math.min(maxWidth, c.textWidth(s) + 8);
		key(c, x, y, w, 14, t.surfaceHigh, t.border);
		c.text(s, x + 4, y + 3, t.text, false);
		return w;
	}

	/** Tastenkappe fester Breite, Beschriftung mittig (Tastenbelegung in den Einstellungen). */
	public static void keycap(Canvas c, int x, int y, int w, String label, boolean hover) {
		Theme t = Theme.get();
		key(c, x, y, w, 15, hover ? t.surfaceHover : t.surfaceHigh, hover ? hoverEdge(t) : t.border);
		String s = c.clip(label, Math.max(0, w - 6));
		c.text(s, x + (w - c.textWidth(s)) / 2, y + 3, t.text, false);
	}

	private static void key(Canvas c, int x, int y, int w, int h, int face, int edge) {
		roundRect(c, x, y, w, h, 3, edge);
		if (w < 4) return;
		roundRect(c, x + 1, y + 1, w - 2, h - 2, 2, face);
		c.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, ColorMath.lerp(face, 0xFF000000, 0.35f));
	}

	/** Runder Statuspunkt (z. B. „an“); {@code size} in Pixeln – flach, in den Jahreszeit-Designs mit Schein. */
	public static void pip(Canvas c, int x, int y, int size, float lit) {
		Theme t = Theme.get();
		float l = ColorMath.clamp01(lit);
		if (l > 0.05f && (t.halloween || t.winter || t.fox)) glow(c, x, y, size, size, t.faceGlow, l * 0.45f);
		int off = ColorMath.lerp(t.border, t.textDim, 0.35f);
		roundRect(c, x, y, size, size, size / 2, ColorMath.lerp(off, t.halloween ? t.faceHot : t.faceOn, l));
	}
}
