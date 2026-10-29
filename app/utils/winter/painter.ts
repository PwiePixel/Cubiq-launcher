import { pineTiers, pineTops, pineTrunk, voxelX, voxelY, type CloudBank, type Island, type Voxel, type WinterLayout } from './layout'
import { between, ease, glide, hash, over, round, threshold, wave, type Argb } from './math'
import type { WinterPalette } from './palette'
import type { WinterSnow } from './snow'

// Malt die Szene „Polarlicht“ als ARGB-Zahlen in Texeln – Zeile für Zeile wie `WinterPainter.java` im Cubiq
// Client. Was sich nicht bewegt (Himmel mit Sternen und der fernen Wolkenbank, die Inseln), entsteht einmal je
// Aufbau und Farbschema; je Bild kommen das Polarlicht, funkelnde Sterne, die treibende nahe Wolkenbank,
// schwebende Inseln und der Schnee dazu. Übergänge sind mit einem festen Raster gestuft, damit alles Pixel-Art
// bleibt. Was je Texel oft gebraucht wird (Farbe und Leuchtkraft je Höhe des Vorhangs, Licht je Stufe), ist beim
// Aufbau vorgerechnet.

/** Zustand eines Bildes. */
export interface WinterFrame {
  /** Takt (80 ms) – Schnee und Funkeln. */
  tick: number
  /** Sekunden – Polarlicht, Wolken, Schweben. */
  seconds: number
  /** Stärke des Polarlichts 0…1. */
  level: number
  /** Inseln schweben, Wolken treiben (aus = Standbild). */
  moving: boolean
  snow: WinterSnow | null
}

/** Fertig gemalte Insel (0 = durchsichtig). */
interface Sprite {
  x: number
  y: number
  w: number
  h: number
  px: Uint32Array
  island: Island
}

/** Gemalte Wolkenbank: Farbe und Deckkraft je Texel (Spalten wiederholen sich nach w), ab Zeile `top`. */
interface Bank {
  color: Uint32Array
  alpha: Uint8Array
  drift: number
  top: number
}

/** Helligkeitsstufen des Polarlichts. */
const STEPS = 10
/** Höhenstufen des Vorhangs von der unteren Kante (0) bis ganz oben – je Stufe eine Farbe und Leuchtkraft. */
const BANDS = 20

export class WinterPainter {
  /** Himmel mit Sternen und der fernen Wolkenbank. */
  readonly backdrop: Uint32Array
  /** Erste Zeile der fernen Wolkenbank – Polarlicht und funkelnde Sterne bleiben darüber. */
  private readonly horizon: number
  private readonly sprites: Sprite[]
  private readonly near: Bank
  private readonly alphaOf: number[] = []
  /** Farbe des Vorhangs je Höhenstufe. */
  private readonly tone = new Uint32Array(BANDS)
  /** Leuchtkraft je Höhenstufe, schon mal STEPS. */
  private readonly glow = new Float64Array(BANDS)
  /** Licht je Höhen- und Helligkeitsstufe, schon mit der Deckkraft verrechnet: [Höhe][Stufe][Kanal]. */
  private readonly lit = new Int32Array(BANDS * (STEPS + 1) * 3)

  constructor(
    readonly L: WinterLayout,
    readonly P: WinterPalette,
  ) {
    this.backdrop = paintSky(L, P)
    const far = paintBank(L, L.cloudFar, P.cloudFarRim, P.cloudFar, 120, 8, 200)
    this.clouds(this.backdrop, far, 0)
    this.horizon = far.top
    this.sprites = L.islands.map((island) => paintIsland(island, P))
    this.near = paintBank(L, L.cloudNear, P.cloudNearRim, P.cloudNear, 210, 5, 255)
    for (let q = 0; q <= STEPS; q++) this.alphaOf.push(round((q / STEPS) * P.auroraAlpha))
    const a = P.aurora
    for (let b = 0; b < BANDS; b++) {
      // Mitte der Stufe: 0 an der unteren Kante, 1 ganz oben.
      const f = (b + 0.5) / BANDS
      let tone: Argb
      let glow: number
      if (b === 0) {
        // Die untere Kante ist der hellste Teil des Vorhangs – auch dort, wo ein Strahl schwach ist.
        tone = a[0]!
        glow = 1.4
      } else if (b === 1) {
        tone = a[1]!
        glow = 1.25
      } else {
        // Nach oben verglimmt der Vorhang (g·√g) und wird kühler: Smaragd → Türkis → Eisblau → Violett.
        const g = 1 - f
        glow = 0.9 * g * Math.sqrt(g)
        tone = f < 0.3 ? a[2]! : f < 0.55 ? between(a[2]!, a[3]!, (f - 0.3) / 0.25) : f < 0.8 ? between(a[3]!, a[4]!, (f - 0.55) / 0.25) : between(a[4]!, a[5]!, (f - 0.8) / 0.2)
      }
      this.tone[b] = tone
      this.glow[b] = glow * STEPS
      for (let q = 0; q <= STEPS; q++) {
        for (let c = 0; c < 3; c++) this.lit[(b * (STEPS + 1) + q) * 3 + c] = ((((tone >>> (16 - 8 * c)) & 255) * this.alphaOf[q]! + 127) / 255) | 0
      }
    }
  }

  /** Setzt ein ganzes Bild in `out` (Länge w·h) zusammen. */
  compose(out: Uint32Array, f: WinterFrame) {
    const { L } = this
    out.set(this.backdrop)
    this.twinkle(out, f.tick)
    if (f.level > 0.02) this.aurora(out, f.seconds, f.level)
    for (const s of this.sprites) if (s.island.depth === 0) blit(out, L.w, L.h, s, 0)
    if (f.snow) this.flakes(out, f.snow, f.tick, 0)
    for (const s of this.sprites) {
      if (s.island.depth > 0) blit(out, L.w, L.h, s, f.moving ? round(wave(f.seconds / 22 + s.island.phase)) : 0)
    }
    this.clouds(out, this.near, f.moving ? f.seconds : 0)
    if (f.snow) {
      this.flakes(out, f.snow, f.tick, 1)
      this.flakes(out, f.snow, f.tick, 2)
    }
  }

  /** Funkelnde Sterne: jeder vierte bis fünfte wechselt alle gut 0,6 s zwischen schwach und hell. */
  private twinkle(out: Uint32Array, tick: number) {
    const tones = this.P.stars
    if (!tones) return
    const stars = this.L.stars
    for (let n = 0; n < stars.length; n++) {
      const s = stars[n]!
      if (!s.twinkle || s.y >= this.horizon) continue
      const r = hash(n, Math.floor((tick + n * 3) / 8), 0x7a1)
      if (r < 0.3) out[s.y * this.L.w + s.x] = tones[0]
      else if (r > 0.82) out[s.y * this.L.w + s.x] = tones[2]
    }
  }

  /**
   * Polarlicht als Vorhang: lange Bänder, die sich in Wellen legen und dabei verdrehen. Aus jedem Band hängt nach oben
   * ein Vorhang aus Lichtstrahlen – unten eine helle Kante, darüber verglimmt er über Smaragd, Türkis und Eisblau bis in
   * ein zartes Violett. Jede Spalte ist ein eigener Strahl: zwei langsam wandernde Rauschen machen ihn heller oder
   * dunkler und unterschiedlich lang; wo sich das Band dreht, wird der Vorhang kürzer und etwas heller.
   */
  private aurora(out: Uint32Array, t: number, level: number) {
    const { L, P } = this
    const { w, h } = L
    const glows = P.auroraGlows
    const lit = this.lit
    const glowOf = this.glow
    const tones = this.tone
    const alphas = this.alphaOf
    // Hintere (höhere) Bänder zuerst.
    for (let k = L.ribbons.length - 1; k >= 0; k--) {
      const r = L.ribbons[k]!
      const fade = (r.to - r.from) * 0.18
      // Feine Strahlen wandern je Band in die andere Richtung.
      const drift = k % 2 === 0 ? 0.7 : -0.7
      for (let x = Math.max(0, Math.ceil(r.from)); x < Math.min(w, r.to); x++) {
        const taper = ease(r.from, r.from + fade, x) * ease(r.to, r.to - fade, x)
        if (taper <= 0) continue
        const u = x / w
        const center = h * (r.base + r.amp * wave(u / r.len + t * r.speed + r.phase) + 0.35 * r.amp * wave(u / (0.41 * r.len) - 1.6 * t * r.speed + 2 * r.phase))
        const turn = wave(u / r.twist + 1.8 * t * r.speed + 3 * r.phase)
        const face = 0.35 + 0.65 * turn * turn
        const coarse = glide(x / 6.5 - t * 0.25, 0x7b + k)
        const rays = 0.22 + 0.78 * (0.55 * glide(x / 1.6 + t * drift, 0x5a + k) + 0.45 * coarse)
        const bottom = center + h * r.thick * 0.9
        const tall = h * r.thick * (3.4 + 4.2 * coarse) * (0.6 + 0.4 * face)
        const strength = 1.3 * level * taper * r.power * rays * (1.15 - 0.15 * face)
        // Höhenstufe je Texel aus dem Abstand d zur unteren Kante: der Saum ist der erste Texel, die Kante der zweite,
        // darüber verteilt sich der Vorhang auf die übrigen Stufen. Alle Werte sind hier positiv – `| 0` rundet wie
        // `floor` ab (im Mod `(int)`), die Rasterschwellen der Spalte liegen schon bereit: das hält die Schleife schnell.
        const per = BANDS / tall
        const yb = Math.min(this.horizon - 1, Math.floor(bottom))
        const t0 = threshold(x, 0)
        const t1 = threshold(x, 1)
        const t2 = threshold(x, 2)
        const t3 = threshold(x, 3)
        for (let y = Math.max(0, Math.floor(bottom - tall)); y <= yb; y++) {
          const d = bottom - (y + 0.5)
          if (d < 0) continue
          let b = d < 1 ? 0 : d < 2 ? 1 : (d * per) | 0
          if (b < 2 && d >= 2) b = 2
          if (b >= BANDS) continue
          const m = y & 3
          let q = (glowOf[b]! * strength + (m === 0 ? t0 : m === 1 ? t1 : m === 2 ? t2 : t3)) | 0
          if (q <= 0) continue
          if (q > STEPS) q = STEPS
          const i = y * w + x
          if (glows) {
            // Wie `light()`, nur mit vorgerechneten Stufen (gleiche Zahlen, schneller).
            const v = out[i]!
            const o = (b * (STEPS + 1) + q) * 3
            const cr = (v >>> 16) & 255
            const cg = (v >>> 8) & 255
            const cb = v & 255
            const sr = lit[o]!
            const sg = lit[o + 1]!
            const sb = lit[o + 2]!
            out[i] = (0xff000000 | ((cr + sr - (((cr * sr + 127) / 255) | 0)) << 16) | ((cg + sg - (((cg * sg + 127) / 255) | 0)) << 8) | (cb + sb - (((cb * sb + 127) / 255) | 0))) >>> 0
          } else {
            out[i] = over(out[i]!, tones[b]!, alphas[q]!)
          }
        }
      }
    }
  }

  /** Wolkenbank darüberlegen; sie treibt mit `drift` Texeln je Sekunde nach rechts. */
  private clouds(out: Uint32Array, bank: Bank, seconds: number) {
    const { w, h } = this.L
    const shift = Math.floor(seconds * bank.drift) % w
    for (let y = bank.top; y < h; y++) {
      const row = y * w
      for (let x = 0; x < w; x++) {
        let src = x - shift
        if (src < 0) src += w
        const a = bank.alpha[row + src]!
        if (a === 255) out[row + x] = bank.color[row + src]!
        else if (a !== 0) out[row + x] = over(out[row + x]!, bank.color[row + src]!, a)
      }
    }
  }

  /** Flocken einer Tiefe: fern 1 Texel blass, mittel 1 Texel hell, nah 2 × 2. */
  private flakes(out: Uint32Array, snow: WinterSnow, tick: number, depth: number) {
    const { w, h } = this.L
    const color = depth === 0 ? this.P.flakeFar : this.P.flake
    const size = depth === 2 ? 2 : 1
    for (let i = 0; i < snow.count; i++) {
      if (snow.depthOf(i) !== depth) continue
      const x = snow.x(i, tick)
      const y = snow.y(i, tick)
      for (let dy = 0; dy < size; dy++) {
        for (let dx = 0; dx < size; dx++) {
          if (x + dx >= 0 && x + dx < w && y + dy >= 0 && y + dy < h) out[(y + dy) * w + x + dx] = color
        }
      }
    }
  }
}

// --- Himmel ---------------------------------------------------------------------------------------------------

/** Himmel in Stufen von oben nach unten – jede Stufe flächig, nur der Übergang zur nächsten gerastert. */
export function paintSky(L: WinterLayout, P: WinterPalette): Uint32Array {
  const out = new Uint32Array(L.w * L.h)
  const stops = P.sky.length - 1
  for (let y = 0; y < L.h; y++) {
    const pos = (y / Math.max(1, L.h - 1)) * stops
    const i = Math.min(stops - 1, Math.floor(pos))
    const mixed = Math.min(1, Math.max(0, (pos - i - 0.3) / 0.4))
    for (let x = 0; x < L.w; x++) out[y * L.w + x] = mixed > threshold(x, y) ? P.sky[i + 1]! : P.sky[i]!
  }
  if (P.stars) for (const s of L.stars) out[s.y * L.w + s.x] = P.stars[s.tone]!
  return out
}

// --- Wolkenmeer -----------------------------------------------------------------------------------------------

/**
 * Wolkenbank unter ihrer Oberkante: ein heller Rand (vom Polarlicht beschienen, oben leicht ausgefranst), dann ein
 * gerasterter Übergang in die Wolke, die nach unten dichter wird.
 */
function paintBank(L: WinterLayout, bank: CloudBank, rim: Argb, body: Argb, alpha0: number, step: number, alphaMax: number): Bank {
  const color = new Uint32Array(L.w * L.h)
  const alpha = new Uint8Array(L.w * L.h)
  const soft = between(rim, body, 0.5)
  let first = L.h
  for (let x = 0; x < L.w; x++) {
    const top = bank.top[x]!
    first = Math.min(first, Math.max(0, top))
    for (let y = Math.max(0, top); y < L.h; y++) {
      const d = y - top
      const th = threshold(x, y)
      if (d === 0 && th > 0.75) continue
      const i = y * L.w + x
      color[i] = d === 0 ? rim : d === 1 ? (th > 0.5 ? rim : soft) : d < 4 && th * 3 > d - 1 ? soft : body
      alpha[i] = Math.min(alphaMax, alpha0 + d * step)
    }
  }
  return { color, alpha, drift: bank.drift, top: first }
}

// --- Inseln ---------------------------------------------------------------------------------------------------

interface Faces {
  top: Argb
  rim: Argb
  left: Argb
  right: Argb
  lipLeft: Argb
  lipRight: Argb
  speck: Argb
  edgeLeft: Argb
  edgeRight: Argb
}

/**
 * Isometrischer Würfel mit oberer Spitze (X, Y): 2a breit, Oberseite a hoch, Seiten a hoch. Gerechnet wird an
 * Pixelmitten in doppelten bzw. vierfachen Koordinaten – keine Pixelmitte liegt je genau auf einer Kante, jede
 * Fläche fällt damit eindeutig und überall gleich. Gemalt werden nur freie Flächen; liegt die Oberseite frei, hängt
 * Schnee mit ein paar Tropfen über die Seiten. `rims`: Bit 1 = hintere linke, Bit 2 = hintere rechte Kante frei.
 */
function cube(px: Uint32Array, sw: number, sh: number, X: number, Y: number, a: number, top: boolean, left: boolean, right: boolean, rims: number, c: Faces, salt: number) {
  for (let yy = 0; yy < 2 * a; yy++) {
    const y = Y + yy
    if (y < 0 || y >= sh) continue
    const V = 4 * yy + 2
    for (let xx = -a; xx < a; xx++) {
      const x = X + xx
      if (x < 0 || x >= sw) continue
      const U = 2 * xx + 1
      const au = U < 0 ? -U : U
      if (V < au) continue
      let color: Argb
      if (V < 4 * a - au) {
        if (!top) continue
        color = V - au < 4 && (rims & (U < 0 ? 1 : 2)) !== 0 ? c.rim : c.top
      } else if (V < 8 * a - au) {
        const isLeft = U < 0
        if (isLeft ? !left : !right) continue
        const row = (V - (4 * a - au)) >> 2
        const n = hash(x, salt, 0x11)
        const lip = top ? 1 + (n < 0.35 ? 1 : 0) + (n < 0.12 ? 1 : 0) : 0
        if (row < lip) color = isLeft ? c.lipLeft : c.lipRight
        else if (row >= a - 1) color = isLeft ? c.edgeLeft : c.edgeRight
        else if (hash(x, y, salt) < 0.06) color = c.speck
        else color = isLeft ? c.left : c.right
      } else continue
      px[y * sw + x] = color
    }
  }
}

function paintIsland(isl: Island, P: WinterPalette): Sprite {
  const haze = isl.depth === 0 ? 0.45 : isl.depth === 1 ? 0.12 : 0
  const tint = (c: Argb) => (haze > 0 ? between(c, P.haze, haze) : c)
  const faces = (left: Argb, right: Argb, deep: number): Faces => ({
    top: tint(P.snowTop),
    rim: tint(P.snowLit),
    left: tint(between(left, P.rockDeep, deep)),
    right: tint(between(right, P.rockDeep, deep)),
    lipLeft: tint(P.snowLeft),
    lipRight: tint(P.snowRight),
    speck: tint(P.rockDeep),
    edgeLeft: tint(between(left, P.rockDeep, 0.5)),
    edgeRight: tint(between(right, P.rockDeep, 0.5)),
  })
  // Tiefere Lagen dunkler – die Insel bekommt Volumen.
  const layers = [0, 1, 2, 3].map((k) => faces(P.rockLeft, P.rockRight, Math.min(0.5, k * 0.16)))
  const pine: Faces = { ...faces(P.pineLeft, P.pineRight, 0), speck: tint(P.pineRight), edgeLeft: tint(P.pineLeft), edgeRight: tint(P.pineRight) }
  const sw = isl.x1 - isl.x0
  const sh = isl.y1 - isl.y0
  const px = new Uint32Array(sw * sh)
  const a = isl.a
  const s = isl.seed & 0xffff
  const at = (i: number, j: number, k: number) => isl.voxels.some((v) => v.i === i && v.j === j && v.k === k)
  for (const v of isl.voxels) {
    const rims = (at(v.i - 1, v.j, v.k) ? 0 : 1) | (at(v.i, v.j - 1, v.k) ? 0 : 2)
    cube(px, sw, sh, voxelX(a, v) - isl.x0, voxelY(a, v) - isl.y0, a, !at(v.i, v.j, v.k - 1), !at(v.i, v.j + 1, v.k), !at(v.i + 1, v.j, v.k), rims, layers[Math.min(v.k, 3)]!, s + v.k * 31)
  }
  const tiers = pineTiers(a)
  const tops = pineTops(tiers)
  const trunk = tint(P.trunk)
  for (const p of isl.pines) paintPine(px, sw, sh, isl, p, tiers, tops, trunk, pine, s)
  // Eiszapfen unter den untersten Würfeln (nicht bei fernen Inseln).
  if (isl.depth > 0) {
    for (let x = 0; x < sw; x++) {
      let bottom = -1
      for (let y = sh - 1; y >= 0 && bottom < 0; y--) if (px[y * sw + x] !== 0) bottom = y
      if (bottom < 0 || hash(x, s, 0x1c) >= 0.16) continue
      const len = 2 + Math.floor(hash(x, s, 0x1d) * 3)
      for (let k = 1; k <= len && bottom + k < sh; k++) px[(bottom + k) * sw + x] = tint(k === len ? P.iceTip : P.ice)
    }
  }
  return { x: isl.ox + isl.x0, y: isl.oy + isl.y0, w: sw, h: sh, px, island: isl }
}

/** Tanne aus Würfeln: kurzer Stamm, darüber kleiner werdende Würfel-Stufen, jede mit Schnee obenauf. */
function paintPine(px: Uint32Array, sw: number, sh: number, isl: Island, p: Voxel, tiers: number[], tops: number[], trunk: Argb, c: Faces, s: number) {
  const a = isl.a
  const bx = voxelX(a, p) - isl.x0
  const by = voxelY(a, p) + a / 2 - isl.y0
  for (let y = by - pineTrunk(tiers); y < by; y++) {
    for (let x = bx - 1; x <= bx; x++) if (x >= 0 && x < sw && y >= 0 && y < sh) px[y * sw + x] = trunk
  }
  for (let n = 0; n < tiers.length; n++) cube(px, sw, sh, bx, by + tops[n]!, tiers[n]!, true, true, true, 3, c, s + 97 + n)
}

function blit(out: Uint32Array, w: number, h: number, s: Sprite, dy: number) {
  for (let y = 0; y < s.h; y++) {
    const ty = s.y + y + dy
    if (ty < 0 || ty >= h) continue
    for (let x = 0; x < s.w; x++) {
      const tx = s.x + x
      if (tx < 0 || tx >= w) continue
      const v = s.px[y * s.w + x]!
      if (v !== 0) out[ty * w + tx] = v
    }
  }
}
