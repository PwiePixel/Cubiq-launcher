import { clamp, round, sequence, wave } from './math'

// Aufbau der Szene „Polarlicht“ – nur Zahlen, ohne Canvas (testbar). Der Cubiq Client baut mit `WinterLayout.java`
// aus denselben Zahlen dasselbe. Zur Szene gehören: Sterne, zwei bis drei Polarlicht-Bänder, schwebende Inseln aus
// isometrischen Würfeln – dem Würfel aus dem Cubiq-Zeichen – mit Schnee obenauf und Tannen aus kleinen Würfeln,
// darunter ein Wolkenmeer aus zwei Bänken. Maße in Texeln; alle Zufallszahlen kommen in fester Reihenfolge aus
// einer Folge, damit beide Seiten Schritt für Schritt gleich bauen.

/** Seed der Szene (Launcher-Hintergrund, Vorschau und Spiel). */
export const WINTER_SEED = 0x2c1b

export interface WinterStar {
  x: number
  y: number
  /** 0 = schwach, 1 = mittel, 2 = hell. */
  tone: number
  twinkle: boolean
}

/**
 * Ein Polarlicht-Band: ein langes Tuch, das sich in Wellen legt und dabei verdreht – wo es sich dreht, wird es
 * schmal und heller. Lagen und Längen als Anteile der Szene, Geschwindigkeiten in Wellen je Sekunde.
 */
export interface Ribbon {
  /** Anfang und Ende in Texeln (die Enden laufen weich aus). */
  from: number
  to: number
  /** Mittellinie als Anteil der Höhe. */
  base: number
  amp: number
  /** Wellenlänge als Anteil der Breite. */
  len: number
  speed: number
  /** Länge einer Drehung als Anteil der Breite. */
  twist: number
  /** Halbe Dicke (Anteil der Höhe), wo das Band flach liegt. */
  thick: number
  /** Schein über dem Band, in halben Dicken. */
  reach: number
  power: number
  phase: number
}

/** Ein Würfel einer Insel: `i`, `j` waagrecht, `k` = Lage (0 oben, darunter 1, 2 …). */
export interface Voxel {
  i: number
  j: number
  k: number
}

export interface Island {
  /** Obere Spitze des Würfels (0, 0, 0) in Texeln. */
  ox: number
  oy: number
  /** Halbe Würfelbreite (gerade): ein Würfel ist 2a breit und 2a hoch. */
  a: number
  /** Zeichenreihenfolge: hinten zuerst. */
  voxels: Voxel[]
  /** Tannen auf den oberen Würfeln (i, j, 0), hinten zuerst. */
  pines: Voxel[]
  /** 0 = fern (blass, ruhig), 1 = Nebeninsel, 2 = Hauptinsel. */
  depth: number
  /** Phase des Schwebens 0…1. */
  phase: number
  seed: number
  /** Umriss relativ zu (ox, oy), mit Tannen und Platz für Eiszapfen. */
  x0: number
  y0: number
  x1: number
  y1: number
}

/** Oberkante einer Wolkenbank je Spalte; sie wiederholt sich nach `w` Spalten und treibt langsam nach rechts. */
export interface CloudBank {
  top: Int16Array
  /** Texel je Sekunde. */
  drift: number
}

export interface WinterLayout {
  w: number
  h: number
  seed: number
  stars: WinterStar[]
  ribbons: Ribbon[]
  islands: Island[]
  cloudFar: CloudBank
  cloudNear: CloudBank
}

export interface WinterLayoutOptions {
  cols: number
  rows: number
  seed: number
  /** Spielen-Knopf in Texeln (linke, obere, rechte Kante): die Hauptinsel schwebt genau darüber. */
  anchor?: { left: number; top: number; right: number } | null
  /** Waagrechte Mitte der Hauptinsel ohne Anker, 0…1 (Standard 0,72 – links stehen Schrift und Knöpfe). */
  heroX?: number
}

/** Bildschirm-Lage der oberen Spitze eines Würfels relativ zur Insel (a ist gerade). */
export function voxelX(a: number, v: Voxel): number {
  return (v.i - v.j) * a
}
export function voxelY(a: number, v: Voxel): number {
  return (v.i + v.j) * (a / 2) + v.k * a
}

/** Halbe Breiten der Tannen-Stufen (unten zuerst, gerade) für Würfel der Größe `a`. */
export function pineTiers(a: number): number[] {
  const tiers: number[] = []
  for (let b = Math.max(2, 2 * round(a * 0.32)); b >= 2 && tiers.length < 3; b -= 2) tiers.push(b)
  return tiers
}

/** Sichtbarer Stamm unter der untersten Stufe. */
export function pineTrunk(tiers: number[]): number {
  return Math.max(1, tiers[0]! >> 2)
}

/** Obere Spitze jeder Stufe relativ zum Fußpunkt der Tanne (Mitte der Würfel-Oberseite). */
export function pineTops(tiers: number[]): number[] {
  const tops: number[] = []
  let y = -pineTrunk(tiers) - 2 * tiers[0]!
  for (let n = 0; n < tiers.length; n++) {
    // Jede Stufe sitzt knapp unter der Mitte der Oberseite der vorigen.
    if (n > 0) y += tiers[n - 1]! / 2 + 1 - 2 * tiers[n]!
    tops.push(y)
  }
  return tops
}

/** Polarlicht im Ruhezustand: atmet in gut einer Minute zwischen 0,55 und 0,95. */
export function auroraLevel(seconds: number): number {
  return 0.75 + 0.2 * wave(seconds / 64)
}

/**
 * Form einer Insel: obere Lage W × D Würfel (vorn fehlt manchmal eine Ecke); unter jeder Zelle hängt eine Säule,
 * umso länger, je näher sie der Mitte liegt – die Insel läuft nach unten zu einer gestuften Spitze aus.
 */
function islandShape(W: number, D: number, pines: number, rand: () => number) {
  const corner = W > 1 && D > 1 && rand() < 0.45 ? Math.floor(rand() * 3) : -1
  const cells: Voxel[] = []
  for (let j = 0; j < D; j++) {
    for (let i = 0; i < W; i++) {
      const extra = rand() < 0.3 ? 1 : 0
      if ((corner === 0 && i === W - 1 && j === D - 1) || (corner === 1 && i === 0 && j === D - 1) || (corner === 2 && i === W - 1 && j === 0)) continue
      const inner = (W - 1 - Math.abs(2 * i - (W - 1)) + (D - 1 - Math.abs(2 * j - (D - 1)))) >> 1
      for (let k = 0; k <= inner + extra; k++) cells.push({ i, j, k })
    }
  }
  // Hinten zuerst (kleines i + j − k); bei Gleichstand die tiefere Lage zuerst.
  cells.sort((p, q) => p.i + p.j - p.k - (q.i + q.j - q.k) || q.k - p.k || p.i - q.i)
  const tops = cells.filter((c) => c.k === 0)
  const trees: Voxel[] = []
  for (let n = 0; n < pines * 3 && trees.length < pines && tops.length > 0; n++) {
    const c = tops[Math.floor(rand() * tops.length)]!
    if (!trees.some((t) => t.i === c.i && t.j === c.j)) trees.push({ i: c.i, j: c.j, k: 0 })
  }
  trees.sort((p, q) => p.i + p.j - (q.i + q.j) || p.i - q.i)
  return { voxels: cells, pines: trees }
}

/** Umriss {x0, y0, x1, y1} relativ zur Insel. */
function outline(a: number, voxels: Voxel[], pines: Voxel[]) {
  let x0 = 0
  let y0 = 0
  let x1 = 0
  let y1 = 0
  for (let n = 0; n < voxels.length; n++) {
    const x = voxelX(a, voxels[n]!)
    const y = voxelY(a, voxels[n]!)
    if (n === 0 || x - a < x0) x0 = x - a
    if (n === 0 || x + a > x1) x1 = x + a
    if (n === 0 || y < y0) y0 = y
    if (n === 0 || y + 2 * a > y1) y1 = y + 2 * a
  }
  const tiers = pineTiers(a)
  const peak = pineTops(tiers)[tiers.length - 1]!
  for (const p of pines) y0 = Math.min(y0, voxelY(a, p) + a / 2 + peak)
  return { x0, y0, x1, y1: y1 + 4 }
}

function cloudBank(w: number, base: number, size: number, drift: number, rand: () => number): CloudBank {
  const bumps: number[] = []
  for (let cx = rand() * size; cx < w; ) {
    const r = size * (0.75 + 0.7 * rand())
    bumps.push(cx, r, r * (0.5 + 0.35 * rand()))
    cx += r * (0.85 + 0.6 * rand())
  }
  const top = new Int16Array(w)
  for (let x = 0; x < w; x++) {
    let y = base
    for (let b = 0; b < bumps.length; b += 3) {
      // Die Bank wiederholt sich nach w Spalten: auch die Nachbarn links und rechts zählen.
      for (let copy = -1; copy <= 1; copy++) {
        const dx = (x - bumps[b]! - copy * w) / bumps[b + 1]!
        if (dx <= -1 || dx >= 1) continue
        const lift = base - bumps[b + 2]! * Math.sqrt(1 - dx * dx)
        if (lift < y) y = lift
      }
    }
    top[x] = round(y)
  }
  return { top, drift }
}

export function buildWinterLayout(o: WinterLayoutOptions): WinterLayout {
  const w = Math.max(16, Math.floor(o.cols))
  const h = Math.max(16, Math.floor(o.rows))
  const seed = o.seed | 0
  const rand = sequence(seed ^ 0x51ce)

  const stars: WinterStar[] = []
  for (let n = round((w * h) / 260); n > 0; n--) {
    const x = Math.floor(rand() * w)
    const y = Math.floor(Math.min(rand(), rand()) * h * 0.74)
    const r = rand()
    stars.push({ x, y, tone: r < 0.62 ? 0 : r < 0.9 ? 1 : 2, twinkle: rand() < 0.2 })
  }

  const ribbons: Ribbon[] = []
  for (let k = 0, count = h >= 100 ? 3 : 2; k < count; k++) {
    // Das vordere Band ist das längste; die hinteren sind kürzer und liegen höher.
    const span = w * (0.95 - 0.2 * k + 0.25 * rand())
    const middle = w * (0.42 + 0.25 * rand())
    ribbons.push({
      from: middle - span / 2,
      to: middle + span / 2,
      base: 0.3 - 0.085 * k + 0.03 * rand(),
      amp: 0.022 + 0.02 * rand(),
      len: 0.38 + 0.3 * rand(),
      speed: (0.012 + 0.01 * rand()) * (k % 2 === 0 ? 1 : -1),
      twist: 0.14 + 0.12 * rand(),
      thick: 0.045 - 0.008 * k + 0.015 * rand(),
      reach: 1.8 + 0.9 * rand(),
      power: 1 - 0.22 * k,
      phase: rand(),
    })
  }

  // Wolkenmeer: die ferne Bank höher, mit kleineren Buckeln und still; die nahe treibt langsam.
  const cloudFar = cloudBank(w, h * 0.82, Math.max(3, h * 0.05), 0, rand)
  const cloudNear = cloudBank(w, h * 0.91, Math.max(4, h * 0.08), 0.5, rand)

  const islands: Island[] = []
  const half = clamp(round(Math.min(h / 30, w / 70)), 2, 7)
  const free = (x0: number, y0: number, x1: number, y1: number) =>
    islands.every((b) => x1 + 8 <= b.ox + b.x0 || x0 - 8 >= b.ox + b.x1 || y1 + 6 <= b.oy + b.y0 || y0 - 6 >= b.oy + b.y1)
  const make = (depth: number, a: number, W: number, D: number, pines: number) => {
    const shape = islandShape(W, D, pines, rand)
    return { a, depth, ...shape, phase: rand(), seed: Math.floor(rand() * 0x7fffffff), ...outline(a, shape.voxels, shape.pines) }
  }

  // Hauptinsel: über dem Spielen-Knopf, sonst rechts der Mitte.
  if (h >= 60 && w >= 120) {
    const W = rand() < 0.5 ? 4 : 3
    const isl = make(2, half * 2, W, 3, rand() < 0.5 ? 3 : 2)
    let ox: number
    let oy: number
    const anchor = o.anchor
    if (anchor && anchor.right > anchor.left) {
      ox = round((anchor.left + anchor.right) / 2) - round((isl.x0 + isl.x1) / 2)
      oy = anchor.top - 3 - isl.y1
    } else {
      ox = round(w * (o.heroX ?? 0.72)) - round((isl.x0 + isl.x1) / 2)
      oy = round(h * 0.42) - round((isl.y0 + isl.y1) / 2)
    }
    ox = clamp(ox, 2 - isl.x0, w - 2 - isl.x1)
    oy = Math.max(oy, 2 - isl.y0)
    islands.push({ ...isl, ox, oy })
  }
  // Nebeninseln, eher rechts.
  if (h >= 80 && w >= 100) {
    for (let n = 0, count = w >= 160 ? Math.max(1, round(w / 200)) : 1; n < count; n++) {
      const isl = make(1, Math.max(4, half * 2 - 4), rand() < 0.5 ? 3 : 2, rand() < 0.5 ? 3 : 2, rand() < 0.4 ? 2 : 1)
      for (let t = 0; t < 12; t++) {
        const ox = round(w * (0.32 + 0.63 * rand())) - round((isl.x0 + isl.x1) / 2)
        const oy = round(h * (0.18 + 0.37 * rand())) - round((isl.y0 + isl.y1) / 2)
        if (ox + isl.x0 < 2 || ox + isl.x1 > w - 2 || oy + isl.y0 < 2 || !free(ox + isl.x0, oy + isl.y0, ox + isl.x1, oy + isl.y1)) continue
        islands.push({ ...isl, ox, oy })
        break
      }
    }
  }
  // Ferne kleine Würfel überall am Himmel.
  if (h >= 30 && w >= 60) {
    for (let n = 0, count = Math.max(1, round(w / 120)); n < count; n++) {
      const a = Math.max(2, half * 2 - 8)
      const isl = make(0, a, rand() < 0.5 ? 2 : 1, 1, a >= 4 && rand() < 0.5 ? 1 : 0)
      for (let t = 0; t < 12; t++) {
        const ox = round(w * (0.04 + 0.92 * rand())) - round((isl.x0 + isl.x1) / 2)
        const oy = round(h * (0.1 + 0.4 * rand())) - round((isl.y0 + isl.y1) / 2)
        if (ox + isl.x0 < 1 || ox + isl.x1 > w - 1 || oy + isl.y0 < 1 || !free(ox + isl.x0, oy + isl.y0, ox + isl.x1, oy + isl.y1)) continue
        islands.push({ ...isl, ox, oy })
        break
      }
    }
  }
  // Fern hinten, Hauptinsel vorn (stabil sortiert).
  islands.sort((p, q) => p.depth - q.depth)
  return { w, h, seed, stars, ribbons, islands, cloudFar, cloudNear }
}
