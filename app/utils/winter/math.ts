// Rechenkern der Szene „Polarlicht“. Der Cubiq Client rechnet in `WinterMath.java` Zeile für Zeile dasselbe:
// Ganzzahl-Hashes mit Überlauf wie Java-`int`, sonst nur Grundrechenarten, `floor` und `sqrt` (in IEEE 754 exakt
// festgelegt) – keine Winkel- oder Exponentialfunktionen, deren letzte Stelle je Laufzeit abweichen darf. Gleiche
// Größe, gleicher Seed und gleicher Takt ergeben so im Launcher und im Spiel Pixel für Pixel dasselbe Bild
// (tests/winter-design.test.ts und WinterTest.java prüfen dieselben Prüfsummen).

/** Farbe als vorzeichenlose ARGB-Zahl (0xAARRGGBB). */
export type Argb = number

/** Durchmischt 32 Bit (Konstanten aus „hash prospector“, gemeinfrei). */
function scramble(v: number): number {
  v ^= v >>> 16
  v = Math.imul(v, 0x7feb352d)
  v ^= v >>> 15
  v = Math.imul(v, 0x846ca68b)
  v ^= v >>> 16
  return v >>> 0
}

/** Gleichverteilter Wert 0 ≤ h < 1 für eine ganzzahlige Stelle (x, y) und einen Kanal `salt`. */
export function hash(x: number, y: number, salt: number): number {
  return scramble(Math.imul(x, 0x27d4eb2d) ^ Math.imul(y, 0x165667b1) ^ Math.imul(salt, 0x9e3779b9)) / 4294967296
}

/** Glattes Rauschen 0…1 entlang einer Achse: Zufallswerte an ganzen Stellen, dazwischen weich verbunden. */
export function glide(x: number, salt: number): number {
  const i = Math.floor(x)
  const f = x - i
  const a = hash(i, 0, salt)
  return a + (hash(i + 1, 0, salt) - a) * f * f * (3 - 2 * f)
}

/** Sinusähnliche Welle aus zwei Parabelbögen, Periode 1: 0 → 1 (¼) → 0 (½) → −1 (¾). */
export function wave(u: number): number {
  const f = u - Math.floor(u)
  return f < 0.5 ? 16 * f * (0.5 - f) : 16 * (f - 0.5) * (f - 1)
}

/** Weicher Übergang 0…1 zwischen `a` und `b` (auch fallend, wenn b < a). */
export function ease(a: number, b: number, v: number): number {
  const t = Math.min(1, Math.max(0, (v - a) / (b - a)))
  return t * t * (3 - 2 * t)
}

/** Kaufmännisch gerundet (x,5 nach oben) – wie `WinterMath.round` im Mod. */
export function round(v: number): number {
  return Math.floor(v + 0.5)
}

export function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v
}

/** Geordnetes Raster (4 × 4): Schwelle 0…1 je Texel, damit weiche Übergänge Pixel-Art bleiben. */
const ORDER = Float64Array.from([0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5], (v) => (v + 0.5) / 16)
export function threshold(x: number, y: number): number {
  return ORDER[((y & 3) << 2) | (x & 3)]!
}

/** Zufallsfolge 0 ≤ r < 1 aus einem Startwert (SplitMix-Schritt, danach durchmischt). */
export function sequence(seed: number): () => number {
  let s = seed | 0
  return () => {
    s = (s + 0x9e3779b9) | 0
    let z = Math.imul(s ^ (s >>> 16), 0x21f0aaad)
    z = Math.imul(z ^ (z >>> 15), 0x735a2d97)
    return ((z ^ (z >>> 15)) >>> 0) / 4294967296
  }
}

/** Deckende Farbe aus "#rrggbb". */
export function rgb(value: string): Argb {
  return (0xff000000 | Number.parseInt(value.slice(1), 16)) >>> 0
}

/** Zwischenfarbe zweier deckender Farben, `t` = 0 … 1. */
export function between(a: Argb, b: Argb, t: number): Argb {
  const r = round(((a >>> 16) & 255) + (((b >>> 16) & 255) - ((a >>> 16) & 255)) * t)
  const g = round(((a >>> 8) & 255) + (((b >>> 8) & 255) - ((a >>> 8) & 255)) * t)
  const bl = round((a & 255) + ((b & 255) - (a & 255)) * t)
  return (0xff000000 | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(bl, 0, 255)) >>> 0
}

/** `src` mit Deckkraft `alpha` (0…255) über `dst` legen. */
export function over(dst: Argb, src: Argb, alpha: number): Argb {
  const k = 255 - alpha
  const r = (((src >>> 16) & 255) * alpha + ((dst >>> 16) & 255) * k + 127) / 255 | 0
  const g = (((src >>> 8) & 255) * alpha + ((dst >>> 8) & 255) * k + 127) / 255 | 0
  const b = ((src & 255) * alpha + (dst & 255) * k + 127) / 255 | 0
  return (0xff000000 | (r << 16) | (g << 8) | b) >>> 0
}

/** Licht `src` mit Stärke `alpha` (0…255) auf `dst` – hellt nur auf (wie „Negativ multiplizieren“). */
export function light(dst: Argb, src: Argb, alpha: number): Argb {
  let out = 0xff000000
  for (let shift = 16; shift >= 0; shift -= 8) {
    const d = (dst >>> shift) & 255
    const s = (((src >>> shift) & 255) * alpha + 127) / 255 | 0
    out |= (d + s - ((d * s + 127) / 255 | 0)) << shift
  }
  return out >>> 0
}

/** Prüfsumme (FNV-1a über alle Bytes) eines ARGB-Bildes – für den Vergleich Launcher ↔ Spiel. */
export function checksum(pixels: ArrayLike<number>): number {
  let h = 0x811c9dc5
  for (let i = 0; i < pixels.length; i++) {
    const v = pixels[i]! >>> 0
    for (let shift = 0; shift < 32; shift += 8) h = Math.imul(h ^ ((v >>> shift) & 255), 0x01000193)
  }
  return h >>> 0
}
