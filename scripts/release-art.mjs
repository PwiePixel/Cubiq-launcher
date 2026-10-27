// Bild für jedes Release: ein kleines Voxel-Motiv (freigestellt, 512 × 512 PNG), das zum Inhalt des Updates passt –
// z. B. ein Schraubenschlüssel für Fehlerbehebungen, eine Rakete für einen Start, ein Bildschirm für den Cubiq Client.
// Der Launcher zeigt es im Update-Banner („Was ist neu“, Neuigkeiten), die Website im Blog und GitHub im Release-Text.
//
// - Thema: aus dem englischen Text des Abschnitts (fette Überschriften zählen dreifach), siehe TOPICS.
// - Jede Version sieht anders aus (Insel, Baum, schwebende Würfel, Funkeln hängen an der Versionsnummer), dieselbe
//   Version aber immer gleich – das Bild entsteht ohne Zufall und ohne Abhängigkeiten (scripts/voxel.mjs).
// - Die Release-Automatik (scripts/release.mjs) legt es für jede neue Version an und trägt es als Motiv ein.
//
//   node --experimental-strip-types scripts/release-art.mjs <version> [--topic <thema>] [--out <datei>]
//       → Bild aus dem Abschnitt der Version in CHANGELOG.md, Standard: public/news/<version>/banner.png
//   node --experimental-strip-types scripts/release-art.mjs --sheet <ordner>
//       → alle Themen als Vorschau (zum Ansehen beim Entwickeln)

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { encodePng, hash, project, renderVoxels, rgb } from './voxel.mjs'

/** Farben der Motive (Cubiq-Grün, warme Akzente, helle Metalle – alles gut sichtbar auf dem dunklen Banner). */
const PALETTE = {
  grass: '#79ad35',
  grassLight: '#93c64a',
  dirt: '#8d6141',
  dirtDark: '#71492f',
  stone: '#7b8592',
  stoneDark: '#5f6875',
  cubiq: '#71a330',
  cubiqLight: '#a9d46b',
  cubiqDark: '#4f7a1f',
  metal: '#cdd5de',
  metalDark: '#8e9aa8',
  slate: '#62738a',
  slateDark: '#46546a',
  paper: '#f2f5f1',
  paperDark: '#c9d3cc',
  wood: '#b98552',
  woodDark: '#87592f',
  gold: '#f4c34f',
  goldDark: '#c98f22',
  orange: '#ef8a32',
  flame: '#ffd45c',
  glass: '#7fd8ef',
  glassDark: '#3d9fc4',
  screen: '#20331a',
  leaves: '#5f9e32',
  leavesDark: '#477d22',
  moon: '#f3e7ad',
  moonDark: '#d4c27a',
  violet: '#a77be0',
  sky: '#5aa9e6',
}
const C = Object.fromEntries(Object.entries(PALETTE).map(([k, v]) => [k, rgb(v)]))

/** Themen in Vorrang-Reihenfolge (bei Gleichstand gewinnt das frühere). `weight` hebt seltene, aber eindeutige Themen. */
export const TOPICS = [
  { id: 'launch', subject: 'rocket', weight: 3, words: ['is here', 'hello', 'welcome', 'first version', 'first release', 'introducing', 'brand-new'] },
  { id: 'motion', subject: 'moon', weight: 2, words: ['animation', 'motion', 'calm', 'reduced', 'stand still', 'stands still'] },
  { id: 'speed', subject: 'bolt', weight: 2, words: ['faster', 'performance', 'fps', 'speed', 'lag', 'smoother', 'memory', 'quicker', 'load time'] },
  { id: 'fix', subject: 'wrench', weight: 1.5, words: ['fix', 'bug', 'crash', 'error', 'message', 'problem', 'broken', 'missing', 'no longer', 'wrong', 'instead of'] },
  { id: 'media', subject: 'camera', weight: 1.5, words: ['clip', 'screenshot', 'camera', 'record', 'video', 'picture', 'gallery'] },
  { id: 'security', subject: 'shield', weight: 1.5, words: ['privacy', 'private', 'secure', 'security', 'safe', 'sign-in', 'sign in', 'login', 'password'] },
  { id: 'social', subject: 'bubble', weight: 1, words: ['friend', 'chat', 'world hosting', 'multiplayer', 'party', 'invite', 'together'] },
  { id: 'worlds', subject: 'compass', weight: 1, words: ['world', 'minimap', 'map', 'waypoint', 'compass', 'biome'] },
  { id: 'mods', subject: 'chest', weight: 1, words: ['modpack', 'mods', 'modrinth', 'curseforge', 'resource pack', 'shader', 'preset', 'content'] },
  { id: 'client', subject: 'monitor', weight: 1, words: ['cubiq client', 'in-game', 'in game', 'title screen', 'hud', 'menus', 'menu'] },
  { id: 'look', subject: 'palette', weight: 1, words: ['look', 'design', 'color', 'colour', 'theme', 'green', 'style', 'appearance', 'icon'] },
  { id: 'settings', subject: 'gear', weight: 1, words: ['setting', 'option', 'toggle', 'preference', 'switch'] },
]
/** Ohne passendes Thema: ein Geschenk (neue Version). */
export const DEFAULT_TOPIC = { id: 'update', subject: 'gift' }

function count(haystack, needle) {
  let n = 0
  for (let i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length)) n++
  return n
}

/** Thema eines Changelog-Texts (Markdown); fette Überschriften („**…**“) zählen dreifach. */
export function pickTopic(text) {
  const lower = String(text ?? '').toLowerCase()
  const heads = [...lower.matchAll(/\*\*(.+?)\*\*/g)].map((m) => m[1]).join('\n')
  let best = DEFAULT_TOPIC
  let bestScore = 0
  for (const topic of TOPICS) {
    const score = topic.words.reduce((sum, w) => sum + count(lower, w) + 2 * count(heads, w), 0) * topic.weight
    if (score > bestScore) {
      best = topic
      bestScore = score
    }
  }
  return best
}

/** Startwert aus der Versionsnummer (gleich wie versionSeed im Launcher). */
export function seedFor(version) {
  let h = 0x7e5
  for (const ch of String(version)) h = (Math.imul(h, 31) + ch.charCodeAt(0)) >>> 0
  return h % 0xffffff
}

/** Kleiner fester Zufallsgenerator (mulberry32). */
function random(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

// ---------------------------------------------------------------- Flächenmuster

const jitter = (amount) => (face, i, j, n, v) => 1 + (hash(v.x, v.y, v.z, i, j, face.length) - 0.5) * amount

/** Grasblock: oben gesprenkeltes Grün, an den Seiten ein Grasrand über Erde. */
const grassPattern = (face, i, j, n, v) => {
  const r = hash(v.x, v.y, v.z, i, j, face.length)
  if (face === 'top') return r > 0.86 ? C.grassLight : [C.grass[0] * (0.93 + r * 0.12), C.grass[1] * (0.93 + r * 0.12), C.grass[2] * (0.93 + r * 0.12)]
  const edge = j === n - 1 || (j === n - 2 && r > 0.55)
  if (edge) return C.grass
  return r > 0.8 ? C.dirtDark : [C.dirt[0] * (0.94 + r * 0.1), C.dirt[1] * (0.94 + r * 0.1), C.dirt[2] * (0.94 + r * 0.1)]
}
const dirtPattern = (face, i, j, n, v) => (hash(v.x, v.y, v.z, i, j, face.length) > 0.78 ? C.dirtDark : jitter(0.12)(face, i, j, n, v))
const stonePattern = (face, i, j, n, v) => (hash(v.x, v.y, v.z, i, j, face.length) > 0.8 ? C.stoneDark : jitter(0.14)(face, i, j, n, v))
const leavesPattern = (face, i, j, n, v) => (hash(v.x, v.y, v.z, i, j, face.length) > 0.72 ? C.leavesDark : jitter(0.16)(face, i, j, n, v))

const cube = (x, y, z, color, extra = {}) => ({ x, y, z, color: Array.isArray(color) ? color : C[color], ...extra })

// ---------------------------------------------------------------- Formen

function segDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax
  const dy = by - ay
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}

function inPolygon(px, py, points) {
  let inside = false
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i]
    const [xj, yj] = points[j]
    if (yi > py !== yj > py && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

/**
 * Flaches Motiv (Pixel-Art) aufrecht in der x-z-Ebene, `thick` Würfel tief: `fn(x, y)` liefert für die Mitte jeder
 * Zelle (Spalte, Zeile von oben) eine Farbe oder nichts. Randzellen werden mit `edge` (Farbname → Farbname) dunkler.
 */
function sprite(w, h, fn, { thick = 2, z0 = 1, edge = {} } = {}) {
  const cells = new Map()
  for (let row = 0; row < h; row++) {
    for (let col = 0; col < w; col++) {
      const m = fn(col + 0.5, row + 0.5)
      if (m) cells.set(`${col},${row}`, m)
    }
  }
  const out = []
  for (const [k, m] of cells) {
    const [col, row] = k.split(',').map(Number)
    const border = ['-1,0', '1,0', '0,-1', '0,1'].some((d) => {
      const [dc, dr] = d.split(',').map(Number)
      return !cells.has(`${col + dc},${row + dr}`)
    })
    const name = border && edge[m] ? edge[m] : m
    for (let t = 0; t < thick; t++) out.push(cube(col - Math.floor(w / 2), t - Math.floor(thick / 2), z0 + (h - 1 - row), name))
  }
  return out
}

const SUBJECTS = {
  // Aufrechter Maulschlüssel: Kopf oben (nach oben offen), Griff unten mit orangem Griffstück.
  wrench: () =>
    sprite(
      9,
      14,
      (x, y) => {
        const head = Math.hypot(x - 4.5, y - 3.6) < 3.4 && !(Math.abs(x - 4.5) < 1.2 && y < 3.9)
        const handle = Math.abs(x - 4.5) < 1.35 && y > 5 && y < 13.6
        if (handle && y > 9.4) return 'orange'
        return head || handle ? 'metal' : null
      },
      { edge: { metal: 'metalDark', orange: 'goldDark' } },
    ),
  bolt: () =>
    sprite(
      10,
      13,
      (x, y) =>
        inPolygon(x, y, [
          [6.4, 0.2],
          [1.0, 7.4],
          [4.6, 7.4],
          [3.0, 12.8],
          [9.0, 5.0],
          [5.3, 5.0],
          [8.2, 0.2],
        ])
          ? 'gold'
          : null,
      { edge: { gold: 'goldDark' } },
    ),
  gear: () =>
    sprite(
      13,
      13,
      (x, y) => {
        const dx = x - 6.5
        const dy = y - 6.5
        const r = Math.hypot(dx, dy)
        const tooth = Math.cos(8 * Math.atan2(dy, dx)) > 0.15
        return r > 1.9 && r < (tooth ? 6.3 : 4.9) ? 'metal' : null
      },
      { edge: { metal: 'metalDark' } },
    ),
  // Malerpalette mit Daumenloch und Farbklecksen (Design, Farben, Aussehen).
  palette: () =>
    sprite(
      14,
      11,
      (x, y) => {
        const body = ((x - 7) / 6.6) ** 2 + ((y - 5.5) / 5.1) ** 2 < 1 && Math.hypot(x - 10.6, y - 8.6) > 1.6
        if (!body || Math.hypot(x - 4.2, y - 7.4) < 1.15) return null
        const blobs = [
          [4.0, 3.6, 'cubiq'],
          [7.2, 2.6, 'gold'],
          [10.4, 3.8, 'sky'],
          [11.2, 6.6, 'violet'],
          [7.4, 6.2, 'orange'],
        ]
        return blobs.find(([bx, by]) => Math.hypot(x - bx, y - by) < 1.25)?.[2] ?? 'wood'
      },
      { thick: 1, edge: { wood: 'woodDark' } },
    ),
  bubble: () =>
    sprite(
      13,
      11,
      (x, y) => {
        const qx = Math.max(Math.abs(x - 6.5) - 4.0, 0)
        const qy = Math.max(Math.abs(y - 4.3) - 1.8, 0)
        const body = Math.hypot(qx, qy) < 2.0
        const tail = inPolygon(x, y, [
          [2.4, 7.0],
          [2.4, 10.9],
          [5.8, 7.0],
        ])
        if (!body && !tail) return null
        // Zwei Zeilen „Text“ in der Blase.
        if (y > 2.6 && y < 3.6 && x > 3 && x < 10.2) return 'cubiq'
        if (y > 5.0 && y < 6.0 && x > 3 && x < 7.8) return 'cubiqLight'
        return 'paper'
      },
      { edge: { paper: 'paperDark' } },
    ),
  shield: () =>
    sprite(
      11,
      13,
      (x, y) => {
        const half = y < 7 ? 5.0 : 5.0 * (1 - Math.pow((y - 7) / 5.9, 1.5))
        if (Math.abs(x - 5.5) >= half || y < 0.6) return null
        const check = segDist(x, y, 2.8, 6.4, 4.8, 8.4) < 0.85 || segDist(x, y, 4.8, 8.4, 8.4, 3.6) < 0.85
        return check ? 'cubiqLight' : 'slate'
      },
      { edge: { slate: 'metal' } },
    ),
  compass: () =>
    sprite(
      13,
      13,
      (x, y) => {
        const dx = x - 6.5
        const dy = y - 6.5
        const r = Math.hypot(dx, dy)
        if (r > 6.2) return null
        if (r > 4.9) return 'metal'
        const along = dx * 0.7071 - dy * 0.7071
        const across = dx * 0.7071 + dy * 0.7071
        if (Math.abs(along) < 4.4 && Math.abs(across) < 1.35 * (1 - Math.abs(along) / 4.6)) return along > 0 ? 'cubiq' : 'slate'
        return 'paper'
      },
      { edge: { metal: 'metalDark' } },
    ),
  moon: () => [
    ...sprite(
      12,
      12,
      (x, y) => (Math.hypot(x - 6, y - 6) < 5.4 && Math.hypot(x - 8.6, y - 4.2) > 4.4 ? 'moon' : null),
      { edge: { moon: 'moonDark' } },
    ),
    cube(4, 0, 9, 'gold', { scale: 0.55 }),
    cube(5, 0, 6, 'paper', { scale: 0.4 }),
  ],
  rocket: () => {
    const out = []
    const lift = 4
    for (let z = 0; z <= 8; z++) {
      for (let x = -2; x <= 2; x++) {
        for (let y = -2; y <= 2; y++) {
          if (x * x + y * y > 4.6) continue
          const window = y === 2 && (x === 0 || x === -1) && (z === 5 || z === 6)
          out.push(cube(x, y, z + lift, window ? 'glass' : z === 2 ? 'cubiq' : 'paper'))
        }
      }
    }
    for (const [z, r2] of [
      [9, 2.3],
      [10, 1],
      [11, 0],
    ]) {
      for (let x = -1; x <= 1; x++) for (let y = -1; y <= 1; y++) if (x * x + y * y <= r2) out.push(cube(x, y, z + lift, 'cubiq'))
    }
    for (const [x, y] of [
      [3, 0],
      [-3, 0],
      [0, 3],
      [0, -3],
    ]) {
      for (let z = 0; z <= 2; z++) out.push(cube(x, y, z + lift, 'cubiqDark'))
      out.push(cube(x * 1.34, y * 1.34, lift, 'cubiqDark'))
    }
    for (let x = -1; x <= 1; x++) for (let y = -1; y <= 1; y++) if (x * x + y * y <= 1) out.push(cube(x, y, lift - 1, 'orange'))
    out.push(cube(0, 0, lift - 2, 'flame'), cube(0, 0, lift - 3, 'flame', { scale: 0.6 }))
    // Rauchwolken auf der Insel unter der Rakete.
    for (const [x, y, s] of [
      [-1, 1, 0.8],
      [1, -1, 0.7],
      [1, 1, 0.6],
      [-2, 0, 0.5],
    ]) {
      out.push(cube(x, y, 1, 'paper', { scale: s, sit: true }))
    }
    return out.map((v) => ({ ...v, x: Math.round(v.x), y: Math.round(v.y) }))
  },
  chest: () => {
    const out = []
    for (let x = 0; x < 6; x++) {
      for (let y = 0; y < 5; y++) {
        for (let z = 0; z < 5; z++) {
          const frame = (x === 0 || x === 5) + (y === 0 || y === 4) + (z === 0 || z === 4) >= 2
          const latch = y === 4 && (x === 2 || x === 3) && (z === 2 || z === 3)
          out.push(cube(x - 3, y - 2, z + 1, latch ? 'gold' : frame || z === 3 ? 'woodDark' : 'wood'))
        }
      }
    }
    return out
  },
  camera: () => {
    const out = []
    for (let x = 0; x < 7; x++) for (let y = 0; y < 4; y++) for (let z = 0; z < 5; z++) out.push(cube(x - 3, y - 2, z + 1, 'slate'))
    out.push(cube(-2, -1, 6, 'slateDark'), cube(-1, -1, 6, 'slateDark'), cube(2, -1, 6, 'orange'), cube(2, 1, 5, 'paper'))
    for (let x = 0; x < 7; x++) {
      for (let z = 0; z < 5; z++) {
        const r = Math.hypot(x - 3, z - 2)
        if (r <= 2.3) out.push(cube(x - 3, 2, z + 1, r < 1.3 ? 'glass' : 'metal'))
        if (r < 1.3) out.push(cube(x - 3, 3, z + 1, 'glassDark', { scale: 0.7 }))
      }
    }
    return out.filter((v, i, all) => all.findIndex((o) => o.x === v.x && o.y === v.y && o.z === v.z && !o.scale === !v.scale) === i)
  },
  monitor: () => {
    const out = []
    for (let x = -4; x <= 4; x++) {
      for (let z = 3; z <= 9; z++) {
        const frame = x === -4 || x === 4 || z === 3 || z === 9
        let color = 'slate'
        if (!frame) {
          if (z === 8) color = 'cubiq'
          else if ((z === 6 && x >= -2 && x <= 1) || (z === 4 && x >= 1)) color = 'cubiqLight'
          else color = 'screen'
        }
        out.push(cube(x, 0, z, color), cube(x, -1, z, 'slateDark'))
      }
    }
    out.push(cube(0, -1, 2, 'slateDark'), cube(0, -1, 1, 'slateDark'))
    for (let x = -2; x <= 2; x++) for (let y = -2; y <= 1; y++) out.push(cube(x, y, 1, 'slate'))
    return out.filter((v, i, all) => all.findIndex((o) => o.x === v.x && o.y === v.y && o.z === v.z) === i)
  },
  gift: () => {
    const out = []
    for (let x = -2; x <= 2; x++) for (let y = -2; y <= 2; y++) for (let z = 1; z <= 4; z++) out.push(cube(x, y, z, x === 0 || y === 0 ? 'gold' : 'cubiq'))
    out.push(cube(-1, 0, 5, 'gold'), cube(1, 0, 5, 'gold'), cube(-1, 0, 6, 'goldDark'), cube(1, 0, 6, 'goldDark'), cube(0, 0, 5, 'goldDark'))
    return out
  },
}

// ---------------------------------------------------------------- Insel und Schmuck

/** Schwebende Insel: oben Gras, darunter Erde und Stein, unten spitz zulaufend. */
function island(rnd, radius) {
  const out = []
  const tops = []
  for (let x = -4; x <= 4; x++) {
    for (let y = -4; y <= 4; y++) {
      const d = Math.hypot(x, y)
      if (d > radius + (rnd() - 0.5) * 0.9) continue
      const depth = 1 + Math.min(2, Math.max(0, Math.round((radius - d) * 0.8 + (rnd() - 0.4))))
      out.push(cube(x, y, 0, C.grass, { pattern: grassPattern }))
      tops.push([x, y])
      for (let z = -1; z >= -depth; z--) out.push(z >= -1 ? cube(x, y, z, C.dirt, { pattern: dirtPattern }) : cube(x, y, z, C.stone, { pattern: stonePattern }))
    }
  }
  return { voxels: out, tops }
}

/** Busch aus Blättern (ein Würfel mit Laub-Muster) und Blumen (kleine Würfel auf dem Gras). */
const bush = (x, y) => cube(x, y, 1, C.leaves, { pattern: leavesPattern })
const FLOWERS = ['gold', 'paper', 'violet', 'orange', 'sky']

/** Vierzackiger Stern (Funkeln) um einen Weltpunkt. */
function sparkle(draw, at, size, color, alpha) {
  const [cx, cy] = project(...at)
  const s = size
  draw(
    [
      [cx, cy - s],
      [cx + s * 0.22, cy - s * 0.22],
      [cx + s, cy],
      [cx + s * 0.22, cy + s * 0.22],
      [cx, cy + s],
      [cx - s * 0.22, cy + s * 0.22],
      [cx - s, cy],
      [cx - s * 0.22, cy - s * 0.22],
    ],
    color,
    alpha,
  )
}

/** Szene einer Version: Insel, Motiv, Schmuck. */
export function releaseScene(version, topic) {
  const rnd = random(seedFor(version))
  const subject = SUBJECTS[topic.subject] ?? SUBJECTS.gift
  const main = subject()
  const { voxels: ground, tops } = island(rnd, 3.1)
  const taken = new Set(main.filter((v) => !v.scale).map((v) => `${v.x},${v.y}`))
  const out = [...ground, ...main]

  // Ein Busch und ein paar Blumen auf freiem Gras – niedrig, damit sie das Motiv nicht verdecken.
  const free = tops.filter(([x, y]) => !taken.has(`${x},${y}`) && [-1, 0, 1].every((d) => !taken.has(`${x + d},${y}`) && !taken.has(`${x},${y + d}`)))
  const pick = () => free.splice(Math.floor(rnd() * free.length), 1)[0]
  const back = free.filter(([x, y]) => x + y <= -2)
  if (back.length && rnd() < 0.7) {
    const [bx, by] = back[Math.floor(rnd() * back.length)]
    free.splice(free.findIndex(([x, y]) => x === bx && y === by), 1)
    out.push(bush(bx, by))
  }
  for (let i = 2 + Math.floor(rnd() * 3); i > 0 && free.length; i--) {
    const [fx, fy] = pick()
    out.push(cube(fx, fy, 1, FLOWERS[Math.floor(rnd() * FLOWERS.length)], { scale: 0.3, sit: true }))
  }

  // Schmuck nie vor oder hinter dem Motiv: Fläche des Motivs im Bild (projizierte Ecken, etwas Rand).
  const box = { minX: Infinity, maxX: -Infinity, minY: Infinity, maxY: -Infinity }
  for (const v of main) {
    for (const [dx, dy, dz] of [[0, 0, 0], [1, 0, 0], [0, 1, 0], [1, 1, 0], [0, 0, 1], [1, 0, 1], [0, 1, 1], [1, 1, 1]]) {
      const [px, py] = project(v.x + dx, v.y + dy, v.z + dz)
      box.minX = Math.min(box.minX, px)
      box.maxX = Math.max(box.maxX, px)
      box.minY = Math.min(box.minY, py)
      box.maxY = Math.max(box.maxY, py)
    }
  }
  const clear = (x, y, z) => {
    const [px, py] = project(x + 0.5, y + 0.5, z + 0.5)
    return px < box.minX - 0.9 || px > box.maxX + 0.9 || py < box.minY - 0.9 || py > box.maxY + 0.9
  }

  // Schwebende Würfel rund um die Insel – je Version andere Plätze und Farben.
  const colors = ['cubiq', 'cubiqLight', 'slate', 'cubiqDark']
  const cubes = 3 + Math.floor(rnd() * 3)
  for (let i = 0; i < cubes; i++) {
    for (let attempt = 0; attempt < 8; attempt++) {
      const angle = (i / cubes) * Math.PI * 2 + rnd() * 0.9
      const dist = 4.7 + rnd() * 1.0
      const [x, y, z] = [Math.round(Math.cos(angle) * dist), Math.round(Math.sin(angle) * dist), 2 + Math.floor(rnd() * 7)]
      if (!clear(x, y, z)) continue
      out.push(cube(x, y, z, colors[Math.floor(rnd() * colors.length)], { scale: 0.45 + rnd() * 0.35 }))
      break
    }
  }

  const glints = []
  for (let i = 0, tries = 0; glints.length < 3 + (i % 2) && tries < 40; tries++, i++) {
    const angle = rnd() * Math.PI * 2
    const dist = 3.2 + rnd() * 2.6
    const at = [Math.cos(angle) * dist, Math.sin(angle) * dist, 5 + rnd() * 7]
    if (clear(at[0] - 0.5, at[1] - 0.5, at[2] - 0.5)) glints.push({ at, size: 0.45 + rnd() * 0.4, bright: rnd() > 0.4 })
  }
  const overlay = (draw) => {
    for (const g of glints) sparkle(draw, g.at, g.size, g.bright ? C.paper : C.cubiqLight, 0.92)
  }
  return { voxels: out, overlay }
}

/** Das Bild einer Version als PNG (512 × 512, freigestellt). `text` = englischer Changelog-Text (fürs Thema). */
export function releaseArt({ version, text = '', topic: topicId = null, size = 512 }) {
  const topic = (topicId && [...TOPICS, DEFAULT_TOPIC].find((t) => t.id === topicId)) || pickTopic(text)
  const { voxels, overlay } = releaseScene(version, topic)
  const rgba = renderVoxels(voxels, { size, ss: 4, fill: 0.96, overlay })
  return { png: encodePng(rgba, size, size), topic: topic.id }
}

// ---------------------------------------------------------------- Kommandozeile

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]
if (isMain) {
  const root = join(dirname(fileURLToPath(import.meta.url)), '..')
  const args = process.argv.slice(2)
  const opt = (name) => {
    const i = args.indexOf(name)
    return i >= 0 ? args[i + 1] : undefined
  }
  if (opt('--sheet')) {
    const dir = opt('--sheet')
    mkdirSync(dir, { recursive: true })
    for (const topic of [...TOPICS, DEFAULT_TOPIC]) {
      writeFileSync(join(dir, `${topic.id}.png`), releaseArt({ version: `0.9.${topic.id.length}`, topic: topic.id }).png)
    }
    console.error(`Vorschau aller Themen → ${dir}`)
  } else {
    const version = (args.find((a) => !a.startsWith('--') && a !== opt('--topic') && a !== opt('--out')) ?? '').replace(/^v/, '')
    if (!/^\d+\.\d+\.\d+/.test(version)) {
      console.error('Aufruf: release-art.mjs <version> [--topic <thema>] [--out <datei>] | --sheet <ordner>')
      process.exit(2)
    }
    const { changelogFor, parseChangelog } = await import('../app/utils/changelog.ts')
    const entry = changelogFor(parseChangelog(readFileSync(join(root, 'CHANGELOG.md'), 'utf8')), version)
    const out = opt('--out') ?? join(root, 'public', 'news', version, 'banner.png')
    const { png, topic } = releaseArt({ version, text: entry?.en || entry?.de || '', topic: opt('--topic') ?? null })
    if (!existsSync(dirname(out))) mkdirSync(dirname(out), { recursive: true })
    writeFileSync(out, png)
    console.error(`Bild für ${version} (Thema „${topic}“) → ${out}`)
  }
}
