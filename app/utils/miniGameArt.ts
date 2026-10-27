// Pixel-Art für „Cubi Hop“ (utils/miniGame.ts): Cubi mit denselben Zeilen und Farben wie im Release-Bild
// (scripts/release-art.mjs), Gras- und Steinblöcke, Funkeln, Boden und ferne Inseln im Navy des Launchers.
// Jedes Bild ist eine Liste von Textzeilen; ein Zeichen = ein Bildpunkt, `.` = durchsichtig.

/** Farben (`#rrggbb`) je Zeichen. */
export const HOP_COLORS: Record<string, string> = {
  // Cubi
  T: '#b3dc72',
  t: '#96c955',
  H: '#86b844',
  G: '#71a330',
  S: '#55821f',
  B: '#456b19',
  F: '#456b19',
  W: '#f6f9f2',
  K: '#18210f',
  P: '#f29a9a',
  M: '#22301a',
  L: '#bfe67f',
  l: '#7fb83f',
  s: '#4f7a1f',
  // Gras und Erde
  g: '#b4e064',
  r: '#8bc441',
  q: '#64982d',
  d: '#87593a',
  D: '#69432b',
  e: '#4c301e',
  // Stein
  o: '#a9b2bd',
  O: '#8d97a4',
  n: '#68717e',
  // Funkeln
  y: '#ffffff',
  u: '#fff1a8',
  a: '#f4c34f',
  // Ferne Inseln (dunkles Navy)
  v: '#1b2350',
  V: '#141a3d',
}

/** Umriss wie im Release-Bild. */
export const HOP_OUTLINE = '#141b18'

const FACE_TOP = ['.TTTTTTTTTTTTTt.', 'TTTTTTTTTTTTTTtt', 'HGGGGGGGGGGGGGSS', 'HGGGGGGGGGGGGGSS']
const FACE_BOTTOM = ['HGPPGGGGGGGPPGSS', 'HGGGGGMGGMGGGGSS', 'HGGGGGGMMGGGGGSS', 'HGGGGGGGGGGGGGSS', '.BBBBBBBBBBBBBB.']
const EYES_OPEN = ['HGGWWGGGGGWWGGSS', 'HGGWKGGGGGWKGGSS', 'HGGWKGGGGGWKGGSS']
const EYES_CLOSED = ['HGGGGGGGGGGGGGSS', 'HGGGGGGGGGGGGGSS', 'HGGKKGGGGGKKGGSS']
const EYES_HIT = ['HGKGKGGGGGKGKGSS', 'HGGKGGGGGGGKGGSS', 'HGKGKGGGGGKGKGSS']

/** Cubi (16 × 16): Pflänzchen, Körper und Füße in einem Bild. */
function cubi(eyes: string[], feet: string, sprout: string[] = ['......LL.ll.....', '.......Lsl......', '........s.......']): string[] {
  return [...sprout, ...FACE_TOP, ...eyes, ...FACE_BOTTOM, feet]
}

const FEET_A = '..FFF......FFF..'
const FEET_B = '...FFF....FFF...'
const FEET_AIR = '...FF......FF...'
const SPROUT_SWAY = ['.......LL.l.....', '........Lsl.....', '........s.......']

export const HOP_SPRITES = {
  idle: cubi(EYES_OPEN, FEET_A),
  blink: cubi(EYES_CLOSED, FEET_A),
  runA: cubi(EYES_OPEN, FEET_A),
  runB: cubi(EYES_OPEN, FEET_B, SPROUT_SWAY),
  air: cubi(EYES_OPEN, FEET_AIR, SPROUT_SWAY),
  hit: cubi(EYES_HIT, FEET_A),
  grass: ['gggggggggg', 'grggggrggg', 'rrrrqrrrrr', 'dDdddddDdd', 'dddDdddddd', 'dddddddDdd', 'dDdddddddd', 'ddddDddddd', 'ddddddddDd', 'eeeeeeeeee'],
  stone: ['oooooooooo', 'oOOOOoOOOO', 'OOnOOOOOOO', 'OOOOOOOnOO', 'OnOOOOOOOO', 'OOOOnOOOOO', 'OOOOOOOOnO', 'OOnOOOOOOO', 'OOOOOOnOOO', 'nnnnnnnnnn'],
  sparkle: ['..a..', '..u..', 'auyua', '..u..', '..a..'],
  sparkleSmall: ['.a.', 'aya', '.a.'],
  /** Boden: 32 Bildpunkte breit, wird gekachelt. */
  ground: [
    'gggggrgggggggggggrgggggggggggrgg',
    'rrgrrrrrrgrrrrqrrrrrrrgrrrrrrrrr',
    'rqrrrqrrrrqrrrrrqrrrrqrrrrrqrrrr',
    'dddDdddddddddDddddddddDdddddddDd',
    'ddddddDddddddddddDdddddddddddddd',
    'dDddddddddDdddddddddddDddddDdddd',
    'ddddddddddddddDddddDdddddddddddd',
    'ddDddddDdddddddddddddddddDdddddd',
    'dddddddddddDdddddDdddddddddddDdd',
    'ddddDddddddddddddddddDdddddddddd',
    'dddddddddDddddDddddddddddddDdddd',
    'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee',
  ],
  /** Ferne schwebende Insel (Hintergrund, ohne Umriss). */
  farIsland: ['..vvvvvvvvvvvv..', 'vvvvvvvvvvvvvvvv', '.VVVVVVVVVVVVVV.', '..VVVVVVVVVVV...', '....VVVVVVV.....', '......VVV.......'],
} satisfies Record<string, string[]>

export type HopSpriteName = keyof typeof HOP_SPRITES

/** Sprites mit dunklem Umriss (wie im Release-Bild); Boden, Funkeln und Hintergrund ohne. */
export const HOP_OUTLINED: ReadonlySet<HopSpriteName> = new Set<HopSpriteName>(['idle', 'blink', 'runA', 'runB', 'air', 'hit', 'grass', 'stone'])

export interface HopBitmap {
  w: number
  h: number
  /** RGBA, zeilenweise (direkt für `new ImageData`). */
  data: Uint8ClampedArray<ArrayBuffer>
}

function rgb(hex: string): [number, number, number] {
  const v = Number.parseInt(hex.slice(1), 16)
  return [(v >> 16) & 255, (v >> 8) & 255, v & 255]
}

/**
 * Zeilen → RGBA. Mit `outline` bekommt das Bild ringsum einen Rand von 1 Bildpunkt in dieser Farbe (4er-Nachbarn,
 * wie `Canvas.outline` im Release-Skript) und wird dafür je Seite 1 Bildpunkt größer. Unbekannte Zeichen sind ein
 * Fehler im Bild (lieber laut als ein Loch).
 */
export function rasterizeHop(rows: readonly string[], outline: string | null = null): HopBitmap {
  const pad = outline ? 1 : 0
  const w = Math.max(...rows.map((r) => r.length)) + pad * 2
  const h = rows.length + pad * 2
  const data = new Uint8ClampedArray(w * h * 4)
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      const ch = row[x]!
      if (ch === '.' || ch === ' ') continue
      const color = HOP_COLORS[ch]
      if (!color) throw new Error(`Unbekannte Farbe „${ch}“ im Bild`)
      data.set([...rgb(color), 255], ((y + pad) * w + x + pad) * 4)
    }
  })
  if (outline) {
    const [r, g, b] = rgb(outline)
    const solid = (x: number, y: number) => x >= 0 && y >= 0 && x < w && y < h && data[(y * w + x) * 4 + 3]! > 0
    const marks: number[] = []
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        if (solid(x, y)) continue
        if (solid(x - 1, y) || solid(x + 1, y) || solid(x, y - 1) || solid(x, y + 1)) marks.push((y * w + x) * 4)
      }
    }
    for (const i of marks) data.set([r, g, b, 255], i)
  }
  return { w, h, data }
}
