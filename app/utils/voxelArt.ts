// Update-Bild als drehbares 3D-Modell: Die Pixel-Art-Szene jedes Release (64 × 64 Bildpunkte, ×8 vergrößert und
// freigestellt, siehe scripts/release-art.mjs) wird erst im Launcher in Voxel zerlegt – aus `banner.png` selbst,
// ohne eigene Dateien je Version. So bekommt jedes vorhandene und jedes künftige Release sein Modell.
//
// Ablauf (rein rechnerisch, ohne DOM – die Darstellung macht components/UpdateModel3d.vue):
// 1. `sampleArtGrid`: je Bildpunkt-Block (8 × 8) die Mitte lesen. Bilder, die keine vergrößerte Pixel-Art sind
//    (Blöcke nicht einfarbig, kein freigestellter Hintergrund), liefern `null` – dann bleibt das flache Bild.
// 2. `buildVoxelModel`: Teile der Szene finden (zusammenhängende Flächen, der dunkle Umriss trennt sie) und jedem
//    eine Tiefe geben: Die Insel ist am tiefsten und läuft nach unten spitz zu (Tiefe je Zeile aus ihrer Breite),
//    Figuren und Gegenstände sind halb so tief, Funkeln ist dünn. Den Umriss am Rand lassen wir weg – die
//    Schattierung der Würfel zeichnet die Kanten selbst. Umriss zwischen zwei Teilen (z. B. unter Cubis Füßen) wird
//    zur Fläche darunter, unter der Figur etwas dunkler: ein kleiner Kontaktschatten statt eines Lochs.

/** Bildpunkte je Seite einer Release-Szene. */
export const ART_CELLS = 64

/** RGBA-Bild (z. B. `ImageData`). */
export interface RgbaImage {
  width: number
  height: number
  data: ArrayLike<number>
}

/** Bildpunkte der Szene: Farbe `0xrrggbb` oder `-1` (durchsichtig), zeilenweise. */
export interface ArtGrid {
  size: number
  cells: Int32Array
}

export type VoxelKind = 'island' | 'figure' | 'spark'

/** Ein Quader des Modells: Spalte `x`, Zeile `y` (oben = 0) und Tiefe von `z0` bis `z1` (vorne = positiv). */
export interface VoxelBox {
  x: number
  y: number
  z0: number
  z1: number
  color: number
  kind: VoxelKind
  /** Teil der Szene, zu dem der Quader gehört (z. B. ein Funkeln – für eigene kleine Bewegungen). */
  group: number
}

export interface VoxelModel {
  boxes: VoxelBox[]
  minX: number
  maxX: number
  minY: number
  maxY: number
  /** Größte Tiefe (in Voxeln). */
  depth: number
}

/** Ab dieser Deckkraft (0–255) zählt ein Bildpunkt. */
const OPAQUE = 128
/** Höchstens so viele Blöcke dürfen nicht einfarbig sein (Rand von Kantenglättung o. Ä.). */
const MAX_MIXED_SHARE = 0.03
/** Insel: Tiefe = Breite der Zeile × Faktor, höchstens … */
const ISLAND_DEPTH_RATIO = 0.4
const ISLAND_DEPTH_MAX = 18
/** Figuren und Gegenstände: Tiefe = kürzere Seite × Faktor, höchstens … */
const FIGURE_DEPTH_RATIO = 0.6
const FIGURE_DEPTH_MAX = 10
/** Funkeln: kleine, freie Flächen (höchstens 12 Punkte, 5 × 5). */
const SPARK_AREA = 12
const SPARK_SPAN = 5
/** Kontaktschatten: Farbe × Faktor. */
const SHADE = 0.7

function rgbAt(image: RgbaImage, x: number, y: number): number {
  const i = (y * image.width + x) * 4
  const d = image.data
  if ((d[i + 3] ?? 0) < OPAQUE) return -1
  return ((d[i]! & 255) << 16) | ((d[i + 1]! & 255) << 8) | (d[i + 2]! & 255)
}

/**
 * Liest je Block der Größe `Bildbreite / size` die Mitte. `null`, wenn das Bild nicht quadratisch ist, nicht in
 * ganze Blöcke passt, keine einfarbigen Blöcke hat (keine vergrößerte Pixel-Art), zu wenig zeigt oder keinen
 * freigestellten Hintergrund hat.
 */
export function sampleArtGrid(image: RgbaImage, size = ART_CELLS): ArtGrid | null {
  const { width, height } = image
  if (width !== height || width < size || width % size !== 0 || image.data.length < width * height * 4) return null
  const scale = width / size
  const half = Math.floor(scale / 2)
  const cells = new Int32Array(size * size)
  let opaque = 0
  let mixed = 0
  for (let cy = 0; cy < size; cy++) {
    for (let cx = 0; cx < size; cx++) {
      const x0 = cx * scale
      const y0 = cy * scale
      const color = rgbAt(image, x0 + half, y0 + half)
      cells[cy * size + cx] = color
      if (color < 0) continue
      opaque++
      // Zwei weitere Punkte im Block müssen dieselbe Farbe haben (bei Vergrößerung ohne Glätten immer so).
      if (scale >= 4 && (rgbAt(image, x0 + 1, y0 + 1) !== color || rgbAt(image, x0 + scale - 2, y0 + scale - 2) !== color)) mixed++
    }
  }
  if (opaque < 24 || opaque > size * size * 0.9) return null
  if (mixed > opaque * MAX_MIXED_SHARE) return null
  return { size, cells }
}

function luminance(color: number): number {
  return (0.2126 * ((color >> 16) & 255) + 0.7152 * ((color >> 8) & 255) + 0.0722 * (color & 255)) / 255
}

/** Farbe × Faktor (je Kanal). */
export function shadeColor(color: number, factor: number): number {
  const f = (v: number) => Math.max(0, Math.min(255, Math.round(v * factor)))
  return (f((color >> 16) & 255) << 16) | (f((color >> 8) & 255) << 8) | f(color & 255)
}

const NEIGHBORS = [
  [0, -1],
  [-1, 0],
  [1, 0],
  [0, 1],
] as const

/**
 * Farbe des Umrisses: die häufigste Farbe am Rand der Flächen (neben Durchsichtigem), sofern sie dunkel ist und
 * einen guten Teil des Rands ausmacht. Ohne solchen Umriss `null`.
 */
export function detectOutline(grid: ArtGrid): number | null {
  const { size, cells } = grid
  const counts = new Map<number, number>()
  let edge = 0
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const c = cells[y * size + x]!
      if (c < 0) continue
      const open = NEIGHBORS.some(([dx, dy]) => {
        const nx = x + dx
        const ny = y + dy
        return nx < 0 || ny < 0 || nx >= size || ny >= size || cells[ny * size + nx]! < 0
      })
      if (!open) continue
      edge++
      counts.set(c, (counts.get(c) ?? 0) + 1)
    }
  }
  let best = -1
  let bestCount = 0
  for (const [color, n] of counts) {
    if (n > bestCount) {
      best = color
      bestCount = n
    }
  }
  if (best < 0 || bestCount < edge * 0.25 || luminance(best) > 0.2) return null
  return best
}

/** Gerade Zahl ≥ 2 (Tiefen gerade halten: Vorder- und Rückseiten liegen dann auf ganzen Voxeln). */
function even(value: number, round: (v: number) => number): number {
  return Math.max(2, 2 * round(value / 2))
}

interface Part {
  area: number
  minX: number
  maxX: number
  minY: number
  maxY: number
  kind: VoxelKind
  /** Tiefe für Figuren und Funkeln (die Insel rechnet je Zeile). */
  depth: number
}

/**
 * Baut das Modell. `null`, wenn nichts übrig bleibt. Tiefen: Insel bis 18 Voxel (nach unten schmaler), Figuren
 * und Gegenstände bis 10, Funkeln 1.
 */
export function buildVoxelModel(grid: ArtGrid): VoxelModel | null {
  const { size, cells } = grid
  const n = size * size
  const outline = detectOutline(grid)
  const isOutline = (i: number) => outline !== null && cells[i] === outline
  const solid = (i: number) => cells[i]! >= 0 && !isOutline(i)

  // 1. Teile: zusammenhängende Flächen ohne Umriss (4er-Nachbarschaft – der Umriss trennt sie).
  const part = new Int32Array(n).fill(-1)
  const parts: Part[] = []
  const queue = new Int32Array(n)
  for (let start = 0; start < n; start++) {
    if (part[start]! >= 0 || !solid(start)) continue
    const id = parts.length
    const p: Part = { area: 0, minX: size, maxX: -1, minY: size, maxY: -1, kind: 'figure', depth: 2 }
    parts.push(p)
    let head = 0
    let tail = 0
    queue[tail++] = start
    part[start] = id
    while (head < tail) {
      const i = queue[head++]!
      const x = i % size
      const y = (i - x) / size
      p.area++
      p.minX = Math.min(p.minX, x)
      p.maxX = Math.max(p.maxX, x)
      p.minY = Math.min(p.minY, y)
      p.maxY = Math.max(p.maxY, y)
      for (const [dx, dy] of NEIGHBORS) {
        const nx = x + dx
        const ny = y + dy
        if (nx < 0 || ny < 0 || nx >= size || ny >= size) continue
        const j = ny * size + nx
        if (part[j]! < 0 && solid(j)) {
          part[j] = id
          queue[tail++] = j
        }
      }
    }
  }
  if (!parts.length) return null

  // 2. Art und Tiefe je Teil: die größte Fläche ist die Insel.
  let island = -1
  parts.forEach((p, id) => {
    if (p.area >= 40 && (island < 0 || p.area > parts[island]!.area)) island = id
  })
  parts.forEach((p, id) => {
    const w = p.maxX - p.minX + 1
    const h = p.maxY - p.minY + 1
    if (id === island) p.kind = 'island'
    else if (p.area <= SPARK_AREA && Math.max(w, h) <= SPARK_SPAN) {
      p.kind = 'spark'
      p.depth = 1
    } else p.depth = Math.min(FIGURE_DEPTH_MAX, even(Math.min(w, h) * FIGURE_DEPTH_RATIO, Math.ceil))
  })

  // 3. Umriss zwischen Teilen: wird zur Fläche darunter (sonst zur häufigsten Nachbarfarbe); am Rand fällt er weg.
  const color = Int32Array.from(cells)
  /** Teile, die an einen übernommenen Umrisspunkt grenzen (für den Kontaktschatten). */
  const shadowBy = new Map<number, number[]>()
  if (outline !== null) {
    for (let i = 0; i < n; i++) {
      if (!isOutline(i)) continue
      const x = i % size
      const y = (i - x) / size
      const around = NEIGHBORS.map(([dx, dy]) => {
        const nx = x + dx
        const ny = y + dy
        return nx < 0 || ny < 0 || nx >= size || ny >= size ? -2 : ny * size + nx
      })
      if (around.some((j) => j === -2 || cells[j]! < 0)) {
        color[i] = -1
        continue
      }
      const below = around[3]!
      let source = solid(below) ? below : -1
      if (source < 0) {
        const votes = new Map<number, { count: number; at: number }>()
        for (const j of around) {
          if (!solid(j)) continue
          const v = votes.get(cells[j]!)
          if (v) v.count++
          else votes.set(cells[j]!, { count: 1, at: j })
        }
        let best = 0
        for (const v of votes.values()) {
          if (v.count > best) {
            best = v.count
            source = v.at
          }
        }
      }
      if (source < 0) {
        color[i] = -1
        continue
      }
      color[i] = cells[source]!
      part[i] = part[source]!
      const others = [...new Set(around.filter((j) => solid(j) && part[j] !== part[source]).map((j) => part[j]!))]
      if (others.length) shadowBy.set(i, others)
    }
  }

  // 4. Tiefe der Insel je Zeile: Breite des Abschnitts, in dem der Punkt liegt.
  const islandDepth = new Int32Array(n)
  if (island >= 0) {
    for (let y = 0; y < size; y++) {
      let x = 0
      while (x < size) {
        if (part[y * size + x] !== island || color[y * size + x]! < 0) {
          x++
          continue
        }
        let end = x
        while (end + 1 < size && part[y * size + end + 1] === island && color[y * size + end + 1]! >= 0) end++
        const depth = Math.min(ISLAND_DEPTH_MAX, even((end - x + 1) * ISLAND_DEPTH_RATIO, Math.round))
        for (let k = x; k <= end; k++) islandDepth[y * size + k] = depth
        x = end + 1
      }
    }
  }
  const depthOf = (i: number) => (part[i] === island ? islandDepth[i]! : parts[part[i]!]!.depth)

  // 5. Quader (Mitte der Tiefe bei z = 0).
  const boxes: VoxelBox[] = []
  let minX = size
  let maxX = -1
  let minY = size
  let maxY = -1
  let maxDepth = 0
  for (let i = 0; i < n; i++) {
    const c = color[i]!
    if (c < 0 || part[i]! < 0) continue
    const x = i % size
    const y = (i - x) / size
    const group = part[i]!
    const kind = parts[group]!.kind
    const depth = depthOf(i)
    const half = depth / 2
    const others = shadowBy.get(i)
    // Kontaktschatten nur so breit wie das, was darauf steht (nie breiter als der Punkt selbst).
    const shadow = others ? Math.min(depth, Math.max(...others.map((o) => parts[o]!.depth))) : 0
    if (!others || shadow >= depth) {
      boxes.push({ x, y, z0: -half, z1: half, color: others ? shadeColor(c, SHADE) : c, kind, group })
    } else {
      const s = shadow / 2
      boxes.push({ x, y, z0: -half, z1: -s, color: c, kind, group })
      boxes.push({ x, y, z0: -s, z1: s, color: shadeColor(c, SHADE), kind, group })
      boxes.push({ x, y, z0: s, z1: half, color: c, kind, group })
    }
    minX = Math.min(minX, x)
    maxX = Math.max(maxX, x)
    minY = Math.min(minY, y)
    maxY = Math.max(maxY, y)
    maxDepth = Math.max(maxDepth, depth)
  }
  if (!boxes.length) return null
  return { boxes, minX, maxX, minY, maxY, depth: maxDepth }
}

/** Bild → Modell in einem Schritt (`null` = flaches Bild zeigen). */
export function voxelModelFromImage(image: RgbaImage): VoxelModel | null {
  const grid = sampleArtGrid(image)
  return grid ? buildVoxelModel(grid) : null
}
