// Pixel-Art ohne Abhängigkeiten: eine kleine Leinwand mit Formen und Sprites aus Textzeilen, Licht und Schatten an
// den Kanten, automatische Umrisse, Vergrößerung ohne Glätten (jeder Bildpunkt wird ein scharfes Quadrat) und
// Ausgabe als PNG oder als animiertes PNG (APNG – Browser, GitHub und der Launcher spielen es ab; Programme ohne
// APNG zeigen das erste Bild). Genutzt von scripts/release-art.mjs.

import { deflateSync } from 'node:zlib'

/** „#rrggbb“ → [r, g, b, a] */
export function hex(value, alpha = 255) {
  const n = Number.parseInt(String(value).replace('#', ''), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, alpha]
}

/** Farbe zwischen a und b (t = 0 … 1), Deckkraft von a. */
export function mix(a, b, t) {
  return [0, 1, 2].map((i) => Math.round(a[i] + (b[i] - a[i]) * t)).concat(a[3] ?? 255)
}

const WHITE = [255, 255, 255, 255]
const BLACK = [0, 0, 0, 255]

export class Canvas {
  constructor(w, h) {
    this.w = w
    this.h = h
    this.data = new Uint8ClampedArray(w * h * 4)
  }

  inside(x, y) {
    return x >= 0 && y >= 0 && x < this.w && y < this.h
  }

  /** Farbe an (x, y) oder `null` (leer oder außerhalb). */
  get(x, y) {
    if (!this.inside(x, y)) return null
    const i = (y * this.w + x) * 4
    return this.data[i + 3] ? [this.data[i], this.data[i + 1], this.data[i + 2], this.data[i + 3]] : null
  }

  /** Punkt setzen; halbdurchsichtige Farben werden über das Vorhandene gelegt. */
  set(x, y, color) {
    if (!color || !Number.isInteger(x) || !Number.isInteger(y) || !this.inside(x, y)) return
    const i = (y * this.w + x) * 4
    const a = (color[3] ?? 255) / 255
    const below = this.data[i + 3] / 255
    if (a >= 1 || below === 0) {
      this.data.set([color[0], color[1], color[2], Math.round(a * 255)], i)
      return
    }
    const out = a + below * (1 - a)
    for (let c = 0; c < 3; c++) this.data[i + c] = Math.round((color[c] * a + this.data[i + c] * below * (1 - a)) / out)
    this.data[i + 3] = Math.round(out * 255)
  }

  erase(x, y) {
    if (this.inside(x, y)) this.data.fill(0, (y * this.w + x) * 4, (y * this.w + x) * 4 + 4)
  }

  rect(x, y, w, h, color) {
    for (let j = y; j < y + h; j++) for (let i = x; i < x + w; i++) this.set(i, j, color)
    return this
  }

  /** Gefüllter Kreis: alle Punkte, deren Mitte höchstens `r` vom Mittelpunkt (cx, cy) entfernt liegt. */
  disc(cx, cy, r, color) {
    for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) {
      for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) if ((x - cx) ** 2 + (y - cy) ** 2 <= r * r) this.set(x, y, color)
    }
    return this
  }

  /** Linie von (x0, y0) nach (x1, y1) (Bresenham), `width` Punkte breit. */
  line(x0, y0, x1, y1, color, width = 1) {
    const dx = Math.abs(x1 - x0)
    const dy = -Math.abs(y1 - y0)
    const sx = x0 < x1 ? 1 : -1
    const sy = y0 < y1 ? 1 : -1
    let err = dx + dy
    for (let x = x0, y = y0; ; ) {
      if (width > 1) this.disc(x, y, (width - 1) / 2 + 0.25, color)
      else this.set(x, y, color)
      if (x === x1 && y === y1) break
      const e2 = 2 * err
      if (e2 >= dy) {
        err += dy
        x += sx
      }
      if (e2 <= dx) {
        err += dx
        y += sy
      }
    }
    return this
  }

  /** Vieleck aus Punkten [[x, y], …] füllen – zählt, wenn die Mitte des Bildpunkts innen liegt. */
  poly(points, color) {
    const xs = points.map((p) => p[0])
    const ys = points.map((p) => p[1])
    for (let y = Math.floor(Math.min(...ys)); y <= Math.ceil(Math.max(...ys)); y++) {
      for (let x = Math.floor(Math.min(...xs)); x <= Math.ceil(Math.max(...xs)); x++) {
        let inside = false
        for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
          const [xi, yi] = points[i]
          const [xj, yj] = points[j]
          if (yi > y + 0.5 !== yj > y + 0.5 && x + 0.5 < ((xj - xi) * (y + 0.5 - yi)) / (yj - yi) + xi) inside = !inside
        }
        if (inside) this.set(x, y, color)
      }
    }
    return this
  }

  /** Andere Leinwand an (dx, dy) darüberlegen; leere Punkte bleiben, wie sie sind. */
  draw(src, dx = 0, dy = 0) {
    for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) this.set(x + dx, y + dy, src.get(x, y))
    return this
  }

  /** Jeden gefüllten Punkt umfärben: fn(farbe, x, y) → neue Farbe oder `null` (löschen). */
  map(fn) {
    const copy = this.clone()
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        const c = copy.get(x, y)
        if (!c) continue
        const next = fn(c, x, y)
        this.erase(x, y)
        if (next) this.set(x, y, next)
      }
    }
    return this
  }

  /**
   * Licht von oben links: Punkte am oberen/linken Rand der Form werden heller, am unteren/rechten dunkler.
   * Gibt der Fläche Tiefe, ohne jede Schattierung einzeln zu zeichnen.
   */
  bevel(light = 0.28, dark = 0.3) {
    const copy = this.clone()
    return this.map((c, x, y) => {
      if (!copy.get(x, y - 1) || !copy.get(x - 1, y)) return mix(c, WHITE, light)
      if (!copy.get(x, y + 1) || !copy.get(x + 1, y)) return mix(c, BLACK, dark)
      return c
    })
  }

  /** Umriss: jeder leere Punkt direkt neben einem gefüllten (oben, unten, links, rechts) bekommt `color`. */
  outline(color) {
    const copy = this.clone()
    for (let y = 0; y < this.h; y++) {
      for (let x = 0; x < this.w; x++) {
        if (copy.get(x, y)) continue
        if (copy.get(x - 1, y) || copy.get(x + 1, y) || copy.get(x, y - 1) || copy.get(x, y + 1)) this.set(x, y, color)
      }
    }
    return this
  }

  clone() {
    const c = new Canvas(this.w, this.h)
    c.data.set(this.data)
    return c
  }
}

/**
 * Sprite aus Textzeilen: jedes Zeichen ein Bildpunkt, `.` und Leerzeichen bleiben leer, sonst die Farbe aus
 * `colors` (fehlt eine, ist das ein Fehler im Bild – lieber laut als ein Loch).
 */
export function sprite(rows, colors) {
  const c = new Canvas(Math.max(...rows.map((r) => r.length)), rows.length)
  rows.forEach((row, y) => {
    ;[...row].forEach((ch, x) => {
      if (ch === '.' || ch === ' ') return
      if (!colors[ch]) throw new Error(`Sprite: Farbe für „${ch}“ fehlt`)
      c.set(x, y, colors[ch])
    })
  })
  return c
}

/** Sprite waagerecht spiegeln. */
export function flip(src) {
  const c = new Canvas(src.w, src.h)
  for (let y = 0; y < src.h; y++) for (let x = 0; x < src.w; x++) c.set(src.w - 1 - x, y, src.get(x, y))
  return c
}

/** Jeder Bildpunkt wird ein Quadrat aus `factor` × `factor` Punkten – scharf, ohne Glätten. */
export function upscale(src, factor) {
  const w = src.w * factor
  const h = src.h * factor
  const data = new Uint8Array(w * h * 4)
  for (let y = 0; y < h; y++) {
    const row = Math.floor(y / factor) * src.w
    for (let x = 0; x < w; x++) {
      const s = (row + Math.floor(x / factor)) * 4
      const d = (y * w + x) * 4
      // Durchsichtige Punkte immer als 0,0,0,0 (kleinere Datei, eine Palettenfarbe).
      if (src.data[s + 3]) data.set(src.data.subarray(s, s + 4), d)
    }
  }
  return { w, h, data }
}

// ---------------------------------------------------------------- PNG / APNG

const SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
const CRC_TABLE = new Uint32Array(256).map((_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})

function crc32(buf) {
  let c = 0xffffffff
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 255] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data = Buffer.alloc(0)) {
  const head = Buffer.alloc(8)
  head.writeUInt32BE(data.length, 0)
  head.write(type, 4, 'ascii')
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])), 0)
  return Buffer.concat([head, data, crc])
}

function u32(...values) {
  const b = Buffer.alloc(values.length * 4)
  values.forEach((v, i) => b.writeUInt32BE(v >>> 0, i * 4))
  return b
}

const keyAt = (data, i) => (data[i + 3] ? ((data[i] << 24) | (data[i + 1] << 16) | (data[i + 2] << 8) | data[i + 3]) >>> 0 : 0)

/** Gemeinsame Palette aller Bilder (höchstens 256 Farben, durchsichtige zuerst), sonst `null` → RGBA. */
function paletteOf(images) {
  const seen = new Map()
  for (const img of images) {
    for (let i = 0; i < img.data.length; i += 4) {
      const key = keyAt(img.data, i)
      if (seen.has(key)) continue
      if (seen.size === 256) return null
      seen.set(key, key ? [img.data[i], img.data[i + 1], img.data[i + 2], img.data[i + 3]] : [0, 0, 0, 0])
    }
  }
  const colors = [...seen.entries()].sort((a, b) => a[1][3] - b[1][3])
  return { index: new Map(colors.map(([key], i) => [key, i])), colors: colors.map(([, c]) => c) }
}

/** Zeilen eines Ausschnitts als Palettenindizes (1 Byte je Punkt) oder RGBA (4 Byte). */
function rowsOf(img, palette, box) {
  const rows = []
  for (let y = box.y; y < box.y + box.h; y++) {
    const row = new Uint8Array(palette ? box.w : box.w * 4)
    for (let x = 0; x < box.w; x++) {
      const i = (y * img.w + box.x + x) * 4
      if (palette) row[x] = palette.index.get(keyAt(img.data, i))
      else if (img.data[i + 3]) row.set(img.data.subarray(i, i + 4), x * 4)
    }
    rows.push(row)
  }
  return rows
}

function paeth(a, b, c) {
  const p = a + b - c
  const pa = Math.abs(p - a)
  const pb = Math.abs(p - b)
  const pc = Math.abs(p - c)
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c
}

/** Zeilen filtern (je Zeile der Filter mit der kleinsten Summe – klassische Faustregel) und packen. */
function compress(rows, bpp) {
  const out = []
  let prev = new Uint8Array(rows[0]?.length ?? 0)
  for (const row of rows) {
    const candidates = [0, 1, 2, 4].map((type) => {
      const f = new Uint8Array(row.length)
      for (let i = 0; i < row.length; i++) {
        const left = i >= bpp ? row[i - bpp] : 0
        const up = prev[i]
        const corner = i >= bpp ? prev[i - bpp] : 0
        const predict = type === 1 ? left : type === 2 ? up : type === 4 ? paeth(left, up, corner) : 0
        f[i] = (row[i] - predict) & 255
      }
      let score = 0
      for (const v of f) score += v < 128 ? v : 256 - v
      return { type, f, score }
    })
    const best = candidates.reduce((a, b) => (b.score < a.score ? b : a))
    out.push(Buffer.from([best.type]), Buffer.from(best.f))
    prev = row
  }
  return deflateSync(Buffer.concat(out), { level: 9 })
}

function header(w, h, palette) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(w, 0)
  ihdr.writeUInt32BE(h, 4)
  ihdr[8] = 8 // Bittiefe
  ihdr[9] = palette ? 3 : 6 // Palette oder RGBA
  const parts = [chunk('IHDR', ihdr)]
  if (palette) {
    parts.push(chunk('PLTE', Buffer.from(palette.colors.flatMap((c) => c.slice(0, 3)))))
    const alphas = palette.colors.filter((c) => c[3] < 255).map((c) => c[3])
    if (alphas.length) parts.push(chunk('tRNS', Buffer.from(alphas)))
  }
  return parts
}

/** Ein Bild { w, h, data (RGBA) } als PNG – mit Palette, wenn es höchstens 256 Farben hat. */
export function encodePng(img) {
  const palette = paletteOf([img])
  const data = compress(rowsOf(img, palette, { x: 0, y: 0, w: img.w, h: img.h }), palette ? 1 : 4)
  return Buffer.concat([SIGNATURE, ...header(img.w, img.h, palette), chunk('IDAT', data), chunk('IEND')])
}

function sameImage(a, b) {
  return a.w === b.w && a.h === b.h && Buffer.compare(Buffer.from(a.data.buffer, a.data.byteOffset, a.data.byteLength), Buffer.from(b.data.buffer, b.data.byteOffset, b.data.byteLength)) === 0
}

/** Kleinster Ausschnitt, in dem sich b von a unterscheidet. */
function changedBox(a, b) {
  let x0 = a.w
  let y0 = a.h
  let x1 = -1
  let y1 = -1
  for (let y = 0; y < a.h; y++) {
    for (let x = 0; x < a.w; x++) {
      const i = (y * a.w + x) * 4
      if (keyAt(a.data, i) === keyAt(b.data, i)) continue
      if (x < x0) x0 = x
      if (x > x1) x1 = x
      if (y < y0) y0 = y
      if (y > y1) y1 = y
    }
  }
  return x1 < 0 ? { x: 0, y: 0, w: 1, h: 1 } : { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 }
}

/**
 * Animiertes PNG aus gleich großen Bildern mit je einer Dauer in Millisekunden; spielt endlos. Das erste Bild ist
 * zugleich das Standbild für Programme ohne APNG. Gleiche Bilder hintereinander werden zusammengefasst, jedes
 * weitere speichert nur den geänderten Ausschnitt.
 */
export function encodeApng(frames, delays) {
  const list = []
  frames.forEach((img, i) => {
    const last = list[list.length - 1]
    if (last && sameImage(last.img, img)) last.delay += delays[i]
    else list.push({ img, delay: delays[i] })
  })
  if (list.length === 1) return encodePng(list[0].img)
  const { w, h } = list[0].img
  const palette = paletteOf(list.map((f) => f.img))
  const parts = [SIGNATURE, ...header(w, h, palette)]
  // acTL (Anzahl Bilder, 0 = endlos) muss vor dem ersten IDAT stehen – üblich direkt hinter IHDR.
  parts.splice(2, 0, chunk('acTL', u32(list.length, 0)))
  let seq = 0
  list.forEach((frame, n) => {
    const box = n === 0 ? { x: 0, y: 0, w, h } : changedBox(list[n - 1].img, frame.img)
    const fctl = Buffer.alloc(26)
    fctl.writeUInt32BE(seq++, 0)
    fctl.writeUInt32BE(box.w, 4)
    fctl.writeUInt32BE(box.h, 8)
    fctl.writeUInt32BE(box.x, 12)
    fctl.writeUInt32BE(box.y, 16)
    fctl.writeUInt16BE(Math.min(65535, Math.round(frame.delay)), 20)
    fctl.writeUInt16BE(1000, 22)
    fctl[24] = 0 // APNG_DISPOSE_OP_NONE: das Bild bleibt stehen, das nächste ändert nur seinen Ausschnitt
    fctl[25] = 0 // APNG_BLEND_OP_SOURCE: Ausschnitt ersetzt (auch Durchsichtiges)
    parts.push(chunk('fcTL', fctl))
    const data = compress(rowsOf(frame.img, palette, box), palette ? 1 : 4)
    parts.push(n === 0 ? chunk('IDAT', data) : chunk('fdAT', Buffer.concat([u32(seq++), data])))
  })
  parts.push(chunk('IEND'))
  return Buffer.concat(parts)
}
