// Kleiner isometrischer Voxel-Renderer ohne Abhängigkeiten – für die Bilder der Releases (scripts/release-art.mjs).
// Szene = Liste von Würfeln (ganze Koordinaten, z nach oben) → RGBA-Bild → PNG.
//
// - Echte Isometrie (alle drei Achsen gleich lang, 120° zueinander): Würfel werden zu regelmäßigen Sechsecken.
//   Darum reicht es, nach x + y + z von hinten nach vorn zu zeichnen – Würfel mit gleicher Summe überdecken sich nie.
// - Sichtbar sind Oberseite (hell), +y-Seite (links, mittel) und +x-Seite (rechts, dunkel); verdeckte Seiten fallen weg.
// - Kantenglättung: in `ss`-facher Größe zeichnen und dann herunterrechnen.
// - Ausgabe ist bei gleicher Szene Byte für Byte gleich (kein Zufall, kein Datum).

import { deflateSync } from 'node:zlib'

const COS30 = Math.cos(Math.PI / 6)
/** Helligkeit der drei sichtbaren Seiten: Licht von oben links. */
const SHADE = { top: 1, left: 0.8, right: 0.62 }

/** `#rrggbb` → [r, g, b] */
export function rgb(hex) {
  const n = Number.parseInt(hex.replace('#', ''), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

/** Fester Pseudo-Zufall aus ganzen Zahlen (für Muster auf den Flächen). */
export function hash(...values) {
  let h = 0x811c9dc5
  for (const v of values) {
    h ^= v | 0
    h = Math.imul(h, 0x01000193)
    h ^= h >>> 15
    h = Math.imul(h, 0x2c1b3c6d)
    h ^= h >>> 12
  }
  return (h >>> 0) / 0x100000000
}

/** Weltpunkt → Bildpunkt (Einheit = Kantenlänge eines Würfels, y nach unten). */
export function project(x, y, z) {
  return [(x - y) * COS30, (x + y) * 0.5 - z]
}

const scaleColor = ([r, g, b], f) => [Math.min(255, r * f), Math.min(255, g * f), Math.min(255, b * f)]
const key = (x, y, z) => `${x},${y},${z}`

/**
 * Die sichtbaren Flächen einer Szene in Zeichenreihenfolge, als Vielecke in Welt-Bildkoordinaten.
 * Ein Würfel: `{ x, y, z, color: [r,g,b], pattern?: (face, u, v) => Faktor, cells?: Zahl }` – mit `pattern` wird jede
 * Seite in `cells × cells` Felder geteilt, deren Helligkeit `pattern` bestimmt (Gras, Erde, Stein …).
 * Sonderformen: `scale` (< 1, kleiner Würfel in der Mitte seiner Zelle, mit `sit` auf deren Boden), `faces: { top,
 * left, right }` (eigene Farben je Seite).
 */
export function sceneFaces(voxels) {
  const solid = new Set(voxels.filter((v) => !v.scale).map((v) => key(v.x, v.y, v.z)))
  const order = [...voxels].sort((a, b) => a.x + a.y + a.z - (b.x + b.y + b.z) || a.z - b.z)
  const faces = []
  for (const v of order) {
    const s = v.scale ?? 1
    const o = (1 - s) / 2
    // `sit`: kleiner Würfel steht auf dem Boden der Zelle (Blumen, Rauch), sonst schwebt er in ihrer Mitte.
    const [x0, y0, z0] = [v.x + o, v.y + o, v.z + (v.sit ? 0 : o)]
    const [x1, y1, z1] = [x0 + s, y0 + s, z0 + s]
    const sides = [
      { name: 'top', hidden: !v.scale && solid.has(key(v.x, v.y, v.z + 1)), corners: [[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]] },
      { name: 'left', hidden: !v.scale && solid.has(key(v.x, v.y + 1, v.z)), corners: [[x0, y1, z0], [x1, y1, z0], [x1, y1, z1], [x0, y1, z1]] },
      { name: 'right', hidden: !v.scale && solid.has(key(v.x + 1, v.y, v.z)), corners: [[x1, y0, z0], [x1, y1, z0], [x1, y1, z1], [x1, y0, z1]] },
    ]
    for (const side of sides) {
      if (side.hidden) continue
      const base = v.faces?.[side.name] ?? v.color
      const shaded = scaleColor(base, SHADE[side.name])
      const cells = v.pattern ? (v.cells ?? 4) : 1
      // Ecken: a = Ursprung, b = erste Richtung (u), d = zweite Richtung (v).
      const [a, b, , d] = side.corners
      for (let i = 0; i < cells; i++) {
        for (let j = 0; j < cells; j++) {
          const at = (fu, fv) => [
            a[0] + (b[0] - a[0]) * fu + (d[0] - a[0]) * fv,
            a[1] + (b[1] - a[1]) * fu + (d[1] - a[1]) * fv,
            a[2] + (b[2] - a[2]) * fu + (d[2] - a[2]) * fv,
          ]
          const [u0, u1, w0, w1] = [i / cells, (i + 1) / cells, j / cells, (j + 1) / cells]
          const factor = v.pattern ? v.pattern(side.name, i, j, cells, v) : 1
          const color = Array.isArray(factor) ? scaleColor(factor, SHADE[side.name]) : scaleColor(shaded, factor)
          faces.push({ points: [at(u0, w0), at(u1, w0), at(u1, w1), at(u0, w1)].map((p) => project(...p)), color })
        }
      }
    }
  }
  return faces
}

/** Konvexes Vieleck in einen RGBA-Puffer füllen (deckend, Mittelpunkt-Abtastung mit kleiner Zugabe gegen Haarrisse). */
function fillPolygon(buf, width, height, points, [r, g, b], alpha = 1) {
  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity
  for (const [x, y] of points) {
    minX = Math.min(minX, x)
    maxX = Math.max(maxX, x)
    minY = Math.min(minY, y)
    maxY = Math.max(maxY, y)
  }
  const n = points.length
  let area = 0
  for (let i = 0; i < n; i++) {
    const [ax, ay] = points[i]
    const [bx, by] = points[(i + 1) % n]
    area += ax * by - bx * ay
  }
  const sign = area >= 0 ? 1 : -1
  const edges = points.map(([ax, ay], i) => {
    const [bx, by] = points[(i + 1) % n]
    const len = Math.hypot(bx - ax, by - ay) || 1
    return { ax, ay, nx: (-(by - ay) / len) * sign, ny: ((bx - ax) / len) * sign }
  })
  const pad = 0.35
  for (let py = Math.max(0, Math.floor(minY - 1)); py <= Math.min(height - 1, Math.ceil(maxY + 1)); py++) {
    for (let px = Math.max(0, Math.floor(minX - 1)); px <= Math.min(width - 1, Math.ceil(maxX + 1)); px++) {
      const cx = px + 0.5
      const cy = py + 0.5
      let inside = true
      for (const e of edges) {
        if ((cx - e.ax) * e.nx + (cy - e.ay) * e.ny < -pad) {
          inside = false
          break
        }
      }
      if (!inside) continue
      const i = (py * width + px) * 4
      if (alpha >= 1) {
        buf[i] = r
        buf[i + 1] = g
        buf[i + 2] = b
        buf[i + 3] = 255
      } else {
        const a0 = buf[i + 3] / 255
        const out = alpha + a0 * (1 - alpha)
        buf[i] = (r * alpha + buf[i] * a0 * (1 - alpha)) / out
        buf[i + 1] = (g * alpha + buf[i + 1] * a0 * (1 - alpha)) / out
        buf[i + 2] = (b * alpha + buf[i + 2] * a0 * (1 - alpha)) / out
        buf[i + 3] = out * 255
      }
    }
  }
}

/**
 * Szene → RGBA (Uint8Array, `size × size`), zentriert und auf `fill` der Bildgröße eingepasst.
 * `overlay(draw, toPixel)` zeichnet danach Zusätzliches (z. B. Funkeln) in Weltkoordinaten: `draw(points, color, alpha)`.
 */
export function renderVoxels(voxels, { size = 512, ss = 4, fill = 0.9, overlay } = {}) {
  const faces = sceneFaces(voxels)
  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity
  for (const f of faces) {
    for (const [x, y] of f.points) {
      minX = Math.min(minX, x)
      maxX = Math.max(maxX, x)
      minY = Math.min(minY, y)
      maxY = Math.max(maxY, y)
    }
  }
  const big = size * ss
  const scale = (big * fill) / Math.max(maxX - minX, maxY - minY)
  const offX = big / 2 - ((minX + maxX) / 2) * scale
  const offY = big / 2 - ((minY + maxY) / 2) * scale
  const toPixel = ([x, y]) => [x * scale + offX, y * scale + offY]
  const buf = new Float32Array(big * big * 4)
  for (const f of faces) fillPolygon(buf, big, big, f.points.map(toPixel), f.color)
  overlay?.((points, color, alpha = 1) => fillPolygon(buf, big, big, points.map(toPixel), color, alpha), { project, scale })

  // Herunterrechnen: Farben nach Deckkraft gewichtet mitteln (keine dunklen Ränder).
  const out = new Uint8Array(size * size * 4)
  const n = ss * ss
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0
      let g = 0
      let b = 0
      let a = 0
      for (let dy = 0; dy < ss; dy++) {
        for (let dx = 0; dx < ss; dx++) {
          const i = ((y * ss + dy) * big + (x * ss + dx)) * 4
          const al = buf[i + 3] / 255
          r += buf[i] * al
          g += buf[i + 1] * al
          b += buf[i + 2] * al
          a += al
        }
      }
      const o = (y * size + x) * 4
      if (a > 0) {
        out[o] = Math.round(r / a)
        out[o + 1] = Math.round(g / a)
        out[o + 2] = Math.round(b / a)
        out[o + 3] = Math.round((a / n) * 255)
      }
    }
  }
  return out
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  return table
})()

function crc32(bytes) {
  let c = 0xffffffff
  for (const byte of bytes) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const head = Buffer.alloc(8)
  head.writeUInt32BE(data.length, 0)
  head.write(type, 4, 'ascii')
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])), 0)
  return Buffer.concat([head, data, crc])
}

/** RGBA → PNG (8 Bit je Kanal, ohne Filter, zlib Stufe 9). */
export function encodePng(rgba, width, height) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0)
  ihdr.writeUInt32BE(height, 4)
  ihdr[8] = 8
  ihdr[9] = 6
  const stride = width * 4
  const raw = Buffer.alloc((stride + 1) * height)
  for (let y = 0; y < height; y++) Buffer.from(rgba.buffer, rgba.byteOffset + y * stride, stride).copy(raw, y * (stride + 1) + 1)
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}
