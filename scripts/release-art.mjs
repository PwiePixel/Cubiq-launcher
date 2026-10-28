// Bild für jedes Release: eine kleine Pixel-Art-Szene (freigestellt, 512 × 512) – Cubi, der Würfel aus dem
// Cubiq-Logo, steht auf einer schwebenden Grasinsel neben einem Gegenstand, der zum Update passt: eine Rakete für
// einen Start, ein Schraubenschlüssel für Fehlerbehebungen, ein Bildschirm für den Cubiq Client, ein Schneemann im
// Winter … Dazu Funkeln und kleine schwebende Würfel. Der Launcher zeigt das Bild im Update-Banner („Was ist neu“,
// Neuigkeiten), die Website im Blog und GitHub im Release-Text.
//
// Zwei Dateien je Version: `banner.png` (ruhig) und `banner-anim.png` – dieselbe Szene als animiertes PNG
// (16 Bilder, 1,6 s, endlos; ein Sonderthema kann eine eigene, längere Schleife haben): Cubi wippt und blinzelt, der
// Gegenstand bewegt sich, es funkelt. Bei „Weniger Animationen“ zeigen Launcher und Website das ruhige Bild; es ist
// zugleich das erste Bild der Animation.
//
// - Thema: aus dem englischen Text des Abschnitts (fette Überschriften zählen dreifach), siehe TOPICS.
// - Jede Version sieht etwas anders aus (Form der Insel, Blumen, Funkeln und schwebende Würfel hängen an der
//   Versionsnummer), dieselbe Version aber immer gleich – ohne Zufall und ohne Abhängigkeiten (scripts/pixel-art.mjs).
// - Die Release-Automatik (scripts/release.mjs) legt beide Bilder für jede neue Version an und trägt sie ein.
//
//   node --experimental-strip-types scripts/release-art.mjs <version> [--topic <thema>] [--out <ordner>]
//       → Bilder aus dem Abschnitt der Version in CHANGELOG.md, Standard: public/news/<version>/
//   node --experimental-strip-types scripts/release-art.mjs --sheet <ordner>
//       → alle Themen als Vorschau (zum Ansehen beim Entwickeln)

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Canvas, encodeApng, encodePng, flip, hex, mix, sprite, upscale } from './pixel-art.mjs'

/** Szene in Bildpunkten; jeder wird ein Quadrat aus SCALE × SCALE (64 × 8 = 512). */
const SIZE = 64
const SCALE = 8
/** Bilder der Animation und Dauer je Bild: zusammen eine Schleife von 1,6 s. */
export const FRAMES = 16
export const FRAME_MS = 100
/** Oberkante der Insel – darauf stehen Cubi und die Gegenstände. */
const GROUND = 45

const C = Object.fromEntries(
  Object.entries({
    outline: '#141b18',
    // Insel
    grassHi: '#b4e064',
    grass: '#8bc441',
    grassDark: '#64982d',
    dirtHi: '#a87752',
    dirt: '#87593a',
    dirtDark: '#69432b',
    dirtDeep: '#4c301e',
    stone: '#8d97a4',
    stoneDark: '#68717e',
    root: '#5a3b25',
    snow: '#f5f9fc',
    snowShade: '#cddbe8',
    // Cubi (Cubiq-Grün) und ein Freund (blau)
    cubiTop: '#b3dc72',
    cubiTopShade: '#96c955',
    cubiHi: '#86b844',
    cubi: '#71a330',
    cubiSide: '#55821f',
    cubiBottom: '#456b19',
    friendTop: '#b4def7',
    friendTopShade: '#93cdef',
    friendHi: '#78bdee',
    friend: '#5aa9e6',
    friendSide: '#3f86c0',
    friendBottom: '#336d9c',
    leafHi: '#bfe67f',
    leaf: '#7fb83f',
    stem: '#4f7a1f',
    eye: '#f6f9f2',
    pupil: '#18210f',
    cheek: '#f29a9a',
    mouth: '#22301a',
    // Gegenstände
    white: '#ffffff',
    paper: '#f2f5f1',
    paperShade: '#cdd6cf',
    metalHi: '#e8eef3',
    metal: '#c1cad4',
    metalDark: '#8995a3',
    metalDeep: '#5f6a78',
    slateHi: '#6a7b93',
    slate: '#4b596e',
    slateDark: '#353f4f',
    screen: '#11201a',
    screenHi: '#1c3628',
    goldHi: '#ffe48c',
    gold: '#f4c34f',
    goldDark: '#c98f22',
    woodHi: '#d6a26c',
    wood: '#b98552',
    woodDark: '#87592f',
    red: '#e0533d',
    redDark: '#a83a2a',
    orange: '#ef8a32',
    flameCore: '#fff3b0',
    flame: '#ffd45c',
    flameRed: '#e0602e',
    glassHi: '#d2f4fc',
    glass: '#7fd8ef',
    glassDark: '#3d9fc4',
    moonHi: '#fff5c8',
    moon: '#f3e2a0',
    moonShade: '#d4bf72',
    blue: '#5aa9e6',
    pink: '#f07a9a',
    violet: '#a77be0',
    smoke: '#e7ecf0',
    smokeShade: '#bcc5ce',
    sparkle: '#ffffff',
    sparkleMid: '#fff1a8',
    sparkleEdge: '#f4c34f',
    coal: '#22262e',
    carrot: '#f08a2c',
    hat: '#272c36',
    hatHi: '#3c4352',
    // Neue Kosmetik (Sonderthema „cosmetics“): Farben der Teile auf der Website. Schwarz ist etwas heller, sonst
    // verschwände es im Umriss.
    penguinHi: '#46557c',
    penguin: '#313d5e',
    penguinDark: '#232c45',
    belly: '#f6f5f0',
    beak: '#f39a2b',
    beakDark: '#c96619',
    scarf: '#d63e4a',
    scarfDark: '#a72d38',
    scarfStripe: '#f5e9d3',
    furHi: '#f39b4c',
    fur: '#e2702c',
    furDark: '#b9501d',
    cream: '#f8f0e5',
    sock: '#3c2a24',
    axolotlHi: '#ffd2de',
    axolotl: '#f6b3c6',
    axolotlDark: '#e091a8',
    gill: '#d9467b',
    gillHi: '#f0709e',
    puff: '#ff86b5',
    blush: '#f48aa8',
    lip: '#a64a66',
    beanie: '#2aa894',
    beanieDark: '#1f7f70',
    seam: '#d6f5a3',
    ice: '#78d6f2',
    iceDark: '#4fb3d6',
    twig: '#8a6143',
    bug: '#47455a',
    shell: '#e8603d',
    lampHi: '#ffffe0',
    lamp: '#f2ff8a',
    lampDark: '#c9e85a',
    wing: '#b9d8f2',
    // Polarlicht (Federflügel), wie das Launcher-Design „Polarlicht“: Saum, Cubiq-Grün, Türkis, Eis-Cyan
    auroraCore: '#effff0',
    auroraHem: '#d6f5a3',
    auroraGreenHi: '#a6db6a',
    auroraGreen: '#71a330',
    auroraTeal: '#2aa894',
    auroraTealHi: '#4ebfc5',
    auroraIce: '#78d6f2',
    auroraIceHi: '#b4ecfa',
    auroraDeep: '#16564f',
    puffHi: '#ffd0e2',
    starHi: '#fff3a8',
    star: '#ffd23f',
    starDark: '#e8a317',
    starFace: '#8a4a10',
  }).map(([k, v]) => [k, hex(v)]),
)

/** Themen in Vorrang-Reihenfolge (bei Gleichstand gewinnt das frühere). `weight` hebt seltene, aber eindeutige Themen. */
export const TOPICS = [
  { id: 'launch', subject: 'rocket', weight: 3, words: ['is here', 'hello', 'welcome', 'first version', 'first release', 'introducing', 'brand-new'] },
  { id: 'winter', subject: 'snowman', weight: 3, words: ['winter', 'snow', 'frost', 'christmas', 'aurora', 'northern lights', 'polar'] },
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
/**
 * Sonderthemen für ein bestimmtes Release: nur mit `--topic` gezeichnet, nie aus dem Text gewählt. Wahlweise mit
 * `hero: 'middle'` (Cubi mitten auf der Insel), `wide` (Insel breiter), `cubes`/`sparkles` (wie viele schwebende
 * Würfel und Funken), `float` (Insel schwebt auf und ab) und `frames`/`frameMs` (eigene Schleife).
 */
export const SPECIAL_TOPICS = [
  // 0.2.51: Der Launcher wird wieder grün – Cubi streicht den großen Würfel vom Navy zurück in Cubiq-Grün.
  { id: 'green', subject: 'roller' },
  // 0.3.0: Neun neue Kosmetik-Teile – Cubi mit Polarlichtflügeln mitten auf der breiteren, schwebenden Insel, um ihn
  // herum die neuen Begleiter; statt der schwebenden Würfel fliegt Mini-Cubi, statt der Funken leuchten eigene Sterne.
  // Längere, flüssigere Schleife.
  { id: 'cosmetics', subject: 'companions', hero: 'middle', wide: 8, cubes: 0, sparkles: 0, float: 1, frames: 32, frameMs: 80 },
]

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

/** Schwingung über die Schleife: −1 … 1, nach FRAMES Bildern wieder am Anfang (nahtlos). */
const wave = (f, phase = 0, periods = 1) => Math.sin(((f / FRAMES) * periods + phase) * 2 * Math.PI)
const layer = () => new Canvas(SIZE, SIZE)

// ---------------------------------------------------------------- Cubi

function cubiSprites(tone) {
  const k = (name) => C[`${tone}${name}`]
  const colors = {
    T: k('Top'), t: k('TopShade'), H: k('Hi'), G: C[tone], S: k('Side'), B: k('Bottom'),
    W: C.eye, K: C.pupil, P: C.cheek, M: C.mouth, F: k('Bottom'), L: C.leafHi, l: C.leaf, s: C.stem,
  }
  const face = (eyes) => [
    '.TTTTTTTTTTTTTt.',
    'TTTTTTTTTTTTTTtt',
    'HGGGGGGGGGGGGGSS',
    'HGGGGGGGGGGGGGSS',
    ...eyes,
    'HGPPGGGGGGGPPGSS',
    'HGGGGGMGGMGGGGSS',
    'HGGGGGGMMGGGGGSS',
    'HGGGGGGGGGGGGGSS',
    '.BBBBBBBBBBBBBB.',
  ]
  const open = ['HGGWWGGGGGWWGGSS', 'HGGWKGGGGGWKGGSS', 'HGGWKGGGGGWKGGSS']
  const closed = ['HGGGGGGGGGGGGGSS', 'HGGGGGGGGGGGGGSS', 'HGGKKGGGGGKKGGSS']
  return {
    body: sprite(face(open), colors),
    blink: sprite(face(closed), colors),
    feet: sprite(['..FFF......FFF..'], colors),
    sprout: [sprite(['LL.ll', '.Lsl.', '..s..'], colors), sprite(['.LL.l', '..Lsl', '..s..'], colors)],
  }
}
const CUBI = cubiSprites('cubi')
const FRIEND = cubiSprites('friend')

/**
 * Cubi an (x, Füße auf GROUND): wippt einmal je Schleife (der Körper hebt sich, die Füße bleiben – kleine Beine
 * werden sichtbar), blinzelt kurz und das Pflänzchen auf dem Kopf schwingt. `mirror` = schaut nach links.
 */
function cubi(f, x, { sprites = CUBI, mirror = false, phase = 0 } = {}) {
  const c = layer()
  const g = (f + phase) % FRAMES
  const lift = g >= 6 && g < 10 ? 1 : 0
  const body = g === 13 ? sprites.blink : sprites.body
  const top = GROUND - 13 - lift
  c.draw(mirror ? flip(body) : body, x, top)
  c.draw(sprites.feet, x, GROUND - 1)
  const sprout = sprites.sprout[g >= 4 && g < 12 ? 1 : 0]
  c.draw(mirror ? flip(sprout) : sprout, x + (mirror ? 5 : 6), top - 3)
  return c.outline(C.outline)
}

// ---------------------------------------------------------------- Insel

const FLOWERS = ['gold', 'pink', 'blue', 'paper', 'violet']

/**
 * Schwebende Grasinsel (oder verschneit): oben Gras, darunter Erde, nach unten schmaler mit Steinen und Wurzeln.
 * `wide` macht sie um so viele Punkte breiter (Platz für mehr Figuren); die Unterseite läuft dann schneller zu.
 */
function island(rnd, { snowy = false, wide = 0 } = {}) {
  const c = layer()
  const width = 46 + Math.floor(rnd() * 5) + wide
  const left = Math.floor((SIZE - width) / 2)
  const right = left + width - 1
  const top = snowy ? [C.snow, C.snow, C.snowShade] : [C.grassHi, C.grass, C.grass]
  for (let x = left; x <= right; x++) {
    const edge = x === left || x === right
    for (let y = GROUND; y <= GROUND + 6; y++) {
      if (edge && (y === GROUND || y === GROUND + 6)) continue
      const d = y - GROUND
      const r = rnd()
      let color
      if (d < 3) color = d === 0 && r < 0.25 && !snowy ? C.grass : top[d]
      else if (d === 3) color = r < 0.45 ? (snowy ? C.snowShade : C.grassDark) : C.dirt
      else color = r < 0.16 ? C.dirtDark : C.dirt
      if (d >= 3 && x <= left + 1) color = mix(color, C.dirtHi, 0.6)
      if (d >= 3 && x >= right - 1) color = mix(color, C.dirtDeep, 0.5)
      if (d < 3 && x === right) color = snowy ? C.snowShade : C.grassDark
      c.set(x, y, color)
    }
  }
  // Steinchen in der Erde
  for (let i = 0; i < 3; i++) {
    const x = left + 3 + Math.floor(rnd() * (width - 8))
    const y = GROUND + 4 + Math.floor(rnd() * 2)
    c.set(x, y, C.stone)
    c.set(x + 1, y, C.stoneDark)
  }
  // Unterseite: wird Zeile für Zeile schmaler, Erde geht in Stein über.
  let l = left + 1
  let r = right - 1
  const bottom = []
  for (let y = GROUND + 7; y < SIZE - 1 && r - l > 1; y++) {
    l += 1 + Math.floor(rnd() * 3) + (wide ? 1 : 0)
    r -= 1 + Math.floor(rnd() * 3) + (wide ? 1 : 0)
    if (r < l) break
    for (let x = l; x <= r; x++) {
      const deep = y - GROUND - 7
      const v = rnd()
      let color = deep < 2 ? (v < 0.3 ? C.dirtDark : C.dirtDeep) : v < 0.4 ? C.stoneDark : v < 0.55 ? C.stone : C.dirtDeep
      if (x === l) color = mix(color, C.dirtHi, 0.3)
      c.set(x, y, color)
    }
    bottom.push([l, r, y])
  }
  // Hängende Wurzeln
  for (let i = 0; i < 3 && bottom.length; i++) {
    const [bl, br, by] = bottom[Math.floor(rnd() * Math.min(3, bottom.length))]
    const x = bl + Math.floor(rnd() * (br - bl + 1))
    const len = 2 + Math.floor(rnd() * 3)
    for (let k = 1; k <= len; k++) if (!c.get(x, by + k)) c.set(x, by + k, C.root)
  }
  // Grashalme oder Schneehäufchen auf der Kante, Blumen an den Rändern
  for (let x = left + 1; x < right; x++) {
    if (rnd() < (snowy ? 0.12 : 0.2)) c.set(x, GROUND - 1, snowy ? C.snow : rnd() < 0.5 ? C.grassHi : C.grass)
  }
  if (!snowy) {
    for (const x of [left + 2 + Math.floor(rnd() * 3), right - 2 - Math.floor(rnd() * 3)]) {
      const petal = C[FLOWERS[Math.floor(rnd() * FLOWERS.length)]]
      c.set(x, GROUND - 1, C.grassDark)
      c.set(x, GROUND - 2, petal)
      if (rnd() < 0.6) c.set(x + 1, GROUND - 2, mix(petal, C.white, 0.35))
    }
  }
  return { canvas: c.outline(C.outline), left, right }
}

// ---------------------------------------------------------------- Funkeln, schwebende Würfel, Schnee

const SPARKLES = [
  sprite(['a'], { a: C.sparkleMid }),
  sprite(['.a.', 'aba', '.a.'], { a: C.sparkleEdge, b: C.sparkle }),
  sprite(['..a..', '..b..', 'abcba', '..b..', '..a..'], { a: C.sparkleEdge, b: C.sparkleMid, c: C.sparkle }),
]
/** Größe eines Funkelns über die Schleife: aufleuchten, kurz strahlen, verlöschen, Pause. */
const TWINKLE = [0, 1, 2, 3, 3, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0]

function sparkle(c, f, { x, y, phase }) {
  const size = TWINKLE[(f + phase) % FRAMES]
  if (!size) return
  const s = SPARKLES[size - 1]
  c.draw(s, x - (s.w >> 1), y - (s.h >> 1))
}

const MINI_CUBE = (snowy) =>
  sprite(['tttt', 'tttT', 'dDdD', 'Dddd'], { t: snowy ? C.snow : C.grassHi, T: snowy ? C.snowShade : C.grass, d: C.dirt, D: C.dirtDark })

/**
 * Schneeflocken (eckig, 1 oder 2 Punkte groß), die in Spalten fallen: jede Spalte wiederholt sich alle 16 Punkte, so
 * ist die Schleife nahtlos. Sie fallen hinter Insel und Figuren – nur der Himmel ist verschneit.
 */
function snowfall(rnd) {
  // Über die Breite verteilt (je Achtel eine Flocke), damit sich nichts zu einem Gestöber ballt.
  return Array.from({ length: 8 }, (_, i) => ({ x: Math.floor(((i + rnd()) * SIZE) / 8), y: Math.floor(rnd() * 16), big: rnd() < 0.4, drift: rnd() }))
}

function drawSnow(c, f, flakes) {
  for (const flake of flakes) {
    for (let k = 0; k < SIZE / 16 + 1; k++) {
      const y = flake.y + k * 16 + f - 16
      const x = flake.x + Math.round(wave(f, flake.drift))
      if (y < 0 || y >= GROUND) continue
      const color = flake.big ? C.snow : mix(C.snow, C.snowShade, 0.6)
      c.set(x, y, color)
      if (flake.big) {
        c.set(x + 1, y, C.snowShade)
        c.set(x, y + 1, C.snowShade)
        c.set(x + 1, y + 1, C.snowShade)
      }
    }
  }
}

// ---------------------------------------------------------------- Gegenstände

const SPRITES = {
  rocket: sprite(
    [
      '.....qN.....',
      '....qNNn....',
      '...qNNNNn...',
      '...qNNNNn...',
      '..WWWWWWWw..',
      '..WWWffWWw..',
      '..WWfBbfWw..',
      '..WWfbbfWw..',
      '..WWWffWWw..',
      '..WWWWWWWw..',
      '..qNNNNNNn..',
      '..WWWWWWWw..',
      '..WWWWWWWw..',
      '.qWWWWWWWwn.',
      'qNWWWWWWWwnn',
      'qNWWWWWWWwnn',
      'qN..dddd..nn',
      'q....dd....n',
    ],
    { q: C.leafHi, N: C.cubi, n: C.cubiSide, W: C.paper, w: C.paperShade, f: C.metalDark, B: C.glassHi, b: C.glass, d: C.metalDeep },
  ),
  flames: [
    ['.yYYy.', '.oYYo.', '..oo..', '..r...'],
    ['.yYYy.', '.oYYy.', '.oooo.', '..rr..', '...r..'],
    ['.yYYy.', '..YYo.', '..oo..', '...r..'],
    ['.YYYY.', '.oYYo.', '.ooo..', '..r...'],
  ].map((rows) => sprite(rows, { Y: C.flameCore, y: C.flame, o: C.orange, r: C.flameRed })),
  chestLid: sprite(
    ['.hhhhhhhhhhhhhh.', 'hWWWWWWWWWWWWWWw', 'mMMMMMMMMMMMMMMm', 'hWWWWWWWWWWWWWWw', 'wwwwwwwwwwwwwwww'],
    { h: C.woodHi, W: C.wood, w: C.woodDark, m: C.metalDeep, M: C.metalDark },
  ),
  chestBody: sprite(
    [
      'mMMMMMMggMMMMMMm',
      'hWWWWWWgGWWWWWWw',
      'hWWWWWWGGWWWWWWw',
      'hWWWWWWWWWWWWWWw',
      'mMMMMMMMMMMMMMMm',
      'hWWWWWWWWWWWWWWw',
      'hWWWWWWWWWWWWWWw',
      'wwwwwwwwwwwwwwww',
    ],
    { h: C.woodHi, W: C.wood, w: C.woodDark, m: C.metalDeep, M: C.metalDark, g: C.gold, G: C.goldDark },
  ),
  bow: sprite(['gg..gg', 'gGggGg', '.gGGg.'], { g: C.goldHi, G: C.goldDark }),
  z: sprite(['zzzz', '..z.', '.z..', 'zzzz'], { z: C.white }),
  heart: sprite(['pp.pp', 'ppppp', '.ppp.', '..p..'], { p: C.pink }),
}

/** Kreisring: Scheibe r minus Scheibe r - w. */
function ring(c, cx, cy, r, w, color) {
  const inner = r - w
  for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) {
    for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) {
      const d = (x - cx) ** 2 + (y - cy) ** 2
      if (d <= r * r && d > inner * inner) c.set(x, y, color)
    }
  }
}

/** Zahnrad mit `teeth` Zähnen, um `angle` (Bogenmaß) gedreht. */
function gear(c, cx, cy, r, teeth, angle, color) {
  for (let y = Math.floor(cy - r - 1); y <= Math.ceil(cy + r + 1); y++) {
    for (let x = Math.floor(cx - r - 1); x <= Math.ceil(cx + r + 1); x++) {
      const dx = x - cx
      const dy = y - cy
      const d = Math.hypot(dx, dy)
      const a = (((Math.atan2(dy, dx) - angle) % ((2 * Math.PI) / teeth)) + (2 * Math.PI) / teeth) % ((2 * Math.PI) / teeth)
      const tooth = a < Math.PI / teeth
      if (d <= r - 1.6 || (tooth && d <= r + 0.4)) c.set(x, y, color)
    }
  }
  for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++) {
    for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++) if ((x - cx) ** 2 + (y - cy) ** 2 <= (r * 0.32) ** 2) c.erase(x, y)
  }
}

// ---------------------------------------------------------------- Neue Kosmetik (Sonderthema „cosmetics“)
//
// Eigene, längere Schleife (`frames`, `frameMs` am Thema): Cubi hüpft im Takt mit Stauchen und Strecken, seine
// gefiederten Polarlichtflügel schlagen dazu, Mini-Cubi umkreist seinen Kopf, die Sternschnuppe zieht einmal über den
// Himmel, und jeder Begleiter auf der Insel bewegt sich auf seine Art. Alles rechnet mit dem Anteil der Schleife
// (f / n), damit sie nahtlos bleibt.

/** Schwingung über eine Schleife aus `n` Bildern: −1 … 1, `periods`-mal je Schleife (wie `wave`, mit eigener Bildzahl). */
const swing = (f, n, phase = 0, periods = 1) => Math.sin(((f / n) * periods + phase) * 2 * Math.PI)

/** Die neuen Begleiter im Kleinformat: Farben wie auf der Website, Formen wie auf den Karten (von vorn). */
const PET = {
  // Watschelpinguin: Frack, weißes Gesicht mit zwei Bögen um die Augen, Bäckchen, Schnabel, rot-weißer Schal
  penguin: sprite(
    ['...K...', '.hKKKk.', 'hWWKWWk', 'KWEWEWk', 'KPWBWPk', '.KWbWk.', 'RRSRRSr', 'hWWWWRk', 'hWWWWSk', 'KWWWWRk', '.kKKKk.'],
    {
      K: C.penguin, k: C.penguinDark, h: C.penguinHi, W: C.belly, E: C.pupil, P: C.cheek,
      B: C.beak, b: C.beakDark, R: C.scarf, r: C.scarfDark, S: C.scarfStripe,
    },
  ),
  penguinFoot: sprite(['OO'], { O: C.beak }),
  // Füchschen (sitzt): spitze Ohren mit dunklen Spitzen, helle Wangen und Schnauze, schwarze Nase und Söckchen
  fox: sprite(
    ['.D...D.', '.Oe.eO.', 'hOOOOOo', 'OKOOOKo', 'CCOOOCC', '.CCNCC.', '.OCCCO.', 'hOCCCOo', 'hOOCOOo', '.ss.ss.'],
    { D: C.sock, e: C.sock, O: C.fur, h: C.furHi, o: C.furDark, K: C.pupil, C: C.cream, N: C.pupil, s: C.sock },
  ),
  // Buschiger Schwanz (hinter dem Körper, rechts): drei Stellungen zum kräftigen Wedeln, die Spitze hell
  foxTail: [
    ['.TT..', '.TT..', '.OO..', '.OOo.', '..Oo.', '..Oo.', '.Oo..', 'Oo...'],
    ['.....', '...TT', '...TT', '..OOo', '..OOo', '..Oo.', '.Oo..', 'Oo...'],
    ['.....', '.....', '.....', '.....', '...TT', '..OOT', '.OOo.', 'Oo...'],
  ].map((rows) => sprite(rows, { T: C.cream, O: C.fur, o: C.furDark })),
  // Axolotl von vorn: breiter rosa Kopf mit Lächeln, Bäckchen und kleinen schwarzen Augen, vorn zwei Füßchen
  axolotl: sprite(['.aaaaa.', 'aAAAAAd', 'AEAAAEd', 'APMAMPd', '.ddMdd.', '.A...A.'], {
    a: C.axolotlHi, A: C.axolotl, d: C.axolotlDark, E: C.pupil, P: C.blush, M: C.lip,
  }),
  // Schneemännchen: Mütze in Türkis mit hellgrünem Streifen und Bommel, Karottennase, hellblauer Schal
  snowman: sprite(
    [
      '...o...', '..TTT..', '.lllll.', '.ccccc.', '.SKSKs.', '.SSNn..', '..SSs..', 'IIWIIWi',
      '.SSSSIs', '.SSKSWs', '..SKSs.', '.SSSSSs', 'SSSSSSs', 'SSSSSSs', '.sssss.',
    ],
    {
      o: C.snow, T: C.beanie, l: C.seam, c: C.beanieDark, S: C.snow, s: C.snowShade, K: C.coal,
      N: C.carrot, n: C.orange, I: C.ice, i: C.iceDark, W: C.white,
    },
  ),
  // Mini-Cubi: winziger Cubi, sein Pflänzchen ist ein Propeller (vier Stellungen = eine Umdrehung)
  miniCubi: sprite(['.TTTTTt.', 'HGWGGWGS', 'HGKGGKGS', 'HPGMMGPS', '.BBBBBB.'], {
    T: C.cubiTop, t: C.cubiTopShade, H: C.cubiHi, G: C.cubi, S: C.cubiSide, B: C.cubiBottom, W: C.eye, K: C.pupil, P: C.cheek, M: C.mouth,
  }),
  propeller: [
    ['LL.ll', '..s..'],
    ['.L.l.', '..s..'],
    ['ll.LL', '..s..'],
    ['.l.L.', '..s..'],
  ].map((rows) => sprite(rows, { L: C.leafHi, l: C.leaf, s: C.stem })),
  miniFeet: [sprite(['.F....F.'], { F: C.cubiBottom }), sprite(['..F..F..'], { F: C.cubiBottom })],
  // Sternschnuppe: vierzackiger Stern mit Gesicht
  star: sprite(['....a....', '....b....', '...bYb...', '..bYYYb..', 'abYEYEYba', '..bYmYb..', '...bYb...', '....b....', '....a....'], {
    a: C.starDark, b: C.star, Y: C.starHi, E: C.starFace, m: C.starFace,
  }),
  // Glühwürmchen (schaut nach links): dunkler Kopf mit großen Augen, oranger Halsschild, Fühler, Flügel, Laterne
  firefly: sprite(['.r.r....', '..b.b...', '.ccccww.', 'bWbWbbLL', '.bbbb.LL'], {
    r: C.shell, b: C.bug, c: C.shell, w: C.wing, W: C.white, L: C.lamp,
  }),
  // Cubis Pflänzchen beim Hüpfen: beim Ausholen platt gedrückt, im Flug nach oben geweht
  sprout: {
    flat: sprite(['.....', 'LLsll', '..s..'], { L: C.leafHi, l: C.leaf, s: C.stem }),
    up: sprite(['L...l', '.LsL.', '..s..'], { L: C.leafHi, l: C.leaf, s: C.stem }),
  },
}

// ---------------------------------------------------------------- Cubi hüpft (Stauchen und Strecken)

/** Ein Hüpfer in 8 Bildern [Breite, Höhe, Höhe über dem Boden]: Ruhe, ausholen, abspringen, steigen, oben, fallen, landen. */
const HOP = [
  [16, 13, 0],
  [18, 11, 0],
  [14, 15, 1],
  [14, 15, 4],
  [16, 13, 6],
  [16, 13, 5],
  [14, 15, 2],
  [18, 11, 0],
]

/** Cubis Körper im Bild f eines Hüpfers – die Mitte bleibt über hero + 7,5, landet auf jedem 8. Bild (dem Takt). */
function hopBody(f, hero) {
  const [w, h, lift] = HOP[f % HOP.length]
  const left = hero + (16 - w) / 2
  const bottom = GROUND - 1 - lift
  return { left, right: left + w - 1, top: bottom - h + 1, bottom, w, h, lift, phase: f % HOP.length }
}

/** Cubi im Hüpf-Bild: Deckel, Glanz links, Schatten rechts, Gesicht und Füße wie im Sprite, nur neu verteilt. */
function hoppingCubi(f, n, b) {
  const c = layer()
  for (let y = b.top; y <= b.bottom; y++) {
    for (let x = b.left; x <= b.right; x++) {
      const row = y - b.top
      if ((row === 0 || y === b.bottom) && (x === b.left || x === b.right)) continue
      let col = C.cubi
      if (row === 0) col = x === b.right - 1 ? C.cubiTopShade : C.cubiTop
      else if (row === 1) col = x >= b.right - 1 ? C.cubiTopShade : C.cubiTop
      else if (y === b.bottom) col = C.cubiBottom
      else if (x === b.left) col = C.cubiHi
      else if (x >= b.right - 1) col = C.cubiSide
      c.set(x, y, col)
    }
  }
  // Gesicht: Augen (blinzeln einmal je Schleife), Bäckchen, Lächeln – mittig, gestaucht etwas tiefer
  const mid = b.left + (b.w - 1) / 2
  const eye = b.top + Math.floor((b.h - 13) / 2 + 4.5)
  for (const ex of [mid - 4.5, mid + 2.5]) {
    if (f === n / 2) c.rect(ex, eye + 2, 2, 1, C.pupil)
    else c.rect(ex, eye, 2, 3, C.eye).rect(ex + 1, eye + 1, 1, 2, C.pupil)
  }
  c.rect(mid - 5.5, eye + 3, 2, 1, C.cheek).rect(mid + 3.5, eye + 3, 2, 1, C.cheek)
  c.set(mid - 1.5, eye + 4, C.mouth)
  c.set(mid + 1.5, eye + 4, C.mouth)
  c.rect(mid - 0.5, eye + 5, 2, 1, C.mouth)
  // Füße: am Boden in der untersten Reihe, in der Luft baumeln sie darunter
  const feet = b.lift > 1 ? b.bottom + 1 : GROUND - 1
  c.rect(mid - 5.5, feet, 3, 1, C.cubiBottom).rect(mid + 3.5, feet, 3, 1, C.cubiBottom)
  // Pflänzchen: beim Ausholen und Landen platt, beim Steigen nach oben geweht, sonst wie immer
  const sprout = b.w > 16 ? PET.sprout.flat : b.phase >= 2 && b.phase <= 4 ? PET.sprout.up : CUBI.sprout[b.phase === 5 || b.phase === 6 ? 1 : 0]
  c.draw(sprout, mid - 1.5, b.top - 3)
  return c.outline(C.outline)
}

// ---------------------------------------------------------------- Gefiederte Polarlichtflügel

/**
 * Linker Flügel in Punkten ab dem Schultergelenk (u nach außen, v nach unten); der rechte ist gespiegelt. Vom kurzen
 * Arm fächern drei Reihen Federn auf: lange Schwungfedern (zur Spitze lang und flach nach außen, zum Körper kurz und
 * nach unten), darüber kürzere Deckfedern und am Flügelbug kleine Federn. Je Feder: [Ansatz u, v, Richtung in Grad
 * unter der Waagerechten nach außen, Länge] – von unten nach oben, so liegt jede Feder auf der darunter.
 */
const WING = {
  arm: [[0, 0], [7, -6]],
  rows: [
    [
      [1, -0.9, 62, 4], [2.5, -2.1, 50, 5.5], [4, -3.4, 38, 7], [5.5, -4.7, 27, 9],
      [7, -6, 18, 11.5], [7, -6, 8, 14], [7, -6, -4, 15], [7, -6, -16, 14],
    ],
    [[1, -0.9, 58, 3.5], [2.5, -2.1, 42, 4.5], [4, -3.4, 28, 5.5], [5.5, -4.7, 14, 7], [7, -6, 0, 8.5], [7, -6, -12, 7]],
    [[1.2, -1, 62, 2.5], [3, -2.6, 42, 3], [5, -4.3, 22, 3.5], [7, -6, 2, 4.5]],
  ],
}
/** Farben je Reihe: Körper vom Ansatz zur Spitze, leuchtende Oberkante. */
const WING_COLORS = [
  { body: [C.auroraTeal, C.auroraTeal, C.auroraTeal, C.auroraTealHi, C.auroraIce], edge: C.auroraIce, tip: C.auroraIceHi },
  { body: [C.auroraGreen, C.auroraGreen, C.auroraGreen, C.auroraTeal], edge: C.auroraGreenHi, tip: C.auroraTealHi },
  { body: [C.auroraGreen, C.auroraGreenHi], edge: C.auroraHem, tip: C.auroraHem },
]
/** Schultergelenk im Bild des linken Flügels. */
const WING_AT = 32
/** Flügelschlag je Hüpf-Bild (Grad, + = hoch): oben beim Ausholen und Landen, ganz unten, wenn Cubi am höchsten ist. */
const WING_UP = [10, 22, 13, -2, -10, -5, 4, 16]

/**
 * Der linke Flügel als eigenes Bild (Schulter bei WING_AT): `up` = Schlag in Grad, `drag` = wie stark die Federn
 * nachhängen (Grad, beim Abschlag nach oben), `t` = Anteil der Schleife für die wandernden Lichtbänder.
 */
function featherWing(up, drag, t) {
  const c = new Canvas(SIZE, SIZE)
  const a = (up * Math.PI) / 180
  // Drehung um die Schulter, dann ins Bild: u nach links, v nach unten
  const place = (u, v) => [WING_AT - (u * Math.cos(a) + v * Math.sin(a)), WING_AT + (v * Math.cos(a) - u * Math.sin(a))]
  const deep = new Set()
  /**
   * Eine Feder: spitz zulaufend, Oberkante und Spitze leuchten. Unter ihrer Spitzenhälfte bleibt zur Feder darunter
   * ein dunkler Spalt – so setzen sich Federn und Reihen voneinander ab.
   */
  const feather = (u, v, dir, len, width, { body, edge, tip }) => {
    const [bx, by] = place(u, v)
    const d = ((dir - up + (drag * (u + len)) / 20) * Math.PI) / 180
    const dx = -Math.cos(d) * len
    const dy = Math.sin(d) * len
    const hit = []
    for (let y = Math.floor(Math.min(by, by + dy) - width); y <= Math.ceil(Math.max(by, by + dy) + width); y++) {
      for (let x = Math.floor(Math.min(bx, bx + dx) - width); x <= Math.ceil(Math.max(bx, bx + dx) + width); x++) {
        const s = Math.max(0, Math.min(1, ((x - bx) * dx + (y - by) * dy) / (len * len)))
        const ox = x - (bx + s * dx)
        const oy = y - (by + s * dy)
        const dist = Math.hypot(ox, oy)
        const half = (width / 2) * (1 - 0.5 * s) + 0.3
        // Oberseite: zeigt (für einen Flügel nach links) nach oben
        const upper = ox * -Math.sin(d) + oy * -Math.cos(d) > 0
        if (dist <= half) hit.push([x, y, s, upper && dist > half - 0.6])
        else if (s > 0.3 && !upper && dist <= half + 0.9 && c.get(x, y)) {
          c.set(x, y, C.auroraDeep)
          deep.add(`${x},${y}`)
        }
      }
    }
    for (const [x, y, s, rim] of hit) {
      c.set(x, y, s > 0.9 ? tip : rim ? edge : body[Math.min(body.length - 1, Math.floor(s * body.length))])
      deep.delete(`${x},${y}`)
    }
  }
  WING.rows.forEach((row, r) => {
    for (const [u, v, dir, len] of row) feather(u, v, dir, len, r === 2 ? 2.6 : 3.2, WING_COLORS[r])
  })
  // Leuchtender Flügelbug: der Arm von der Schulter zum Handgelenk
  const [[u0, v0], [u1, v1]] = WING.arm
  for (let k = 0; k <= 1; k += 0.05) {
    const [x, y] = place(u0 + (u1 - u0) * k, v0 + (v1 - v0) * k - 0.8)
    c.set(Math.round(x), Math.round(y), k > 0.7 ? C.auroraCore : C.auroraHem)
  }
  // Polarlicht-Bänder: wandern zweimal je Schleife von der Schulter nach außen – ein heller Kamm, davor grünes,
  // dahinter eisblaues Licht
  return c.map((col, x, y) => {
    if (deep.has(`${x},${y}`)) return col
    const p = 2 * Math.PI * ((WING_AT - x) / 11 + (y - WING_AT) / 24 - 2 * t - 0.25)
    if (Math.cos(p) > 0.88) return mix(col, C.auroraCore, 0.35)
    if (Math.cos(p) > 0.55) return mix(col, Math.sin(p) > 0 ? C.auroraGreenHi : C.auroraIceHi, 0.25)
    return col
  })
}

/** Beide Flügel hinter Cubi: an den oberen Ecken seines Körpers, schlagen im Takt der Hüpfer. */
function auroraWings(f, n, b) {
  const p = b.phase
  const up = WING_UP[p]
  const drag = ((WING_UP[(p + 1) % 8] - WING_UP[(p + 7) % 8]) / 2) * 0.9
  const left = featherWing(up, drag, f / n)
  const right = flip(featherWing(up, drag, f / n + 0.1))
  const c = layer()
  c.draw(left, b.left + 3 - WING_AT, b.top - WING_AT)
  c.draw(right, b.right - 3 - (SIZE - 1 - WING_AT), b.top - WING_AT)
  return c.outline(C.outline)
}

// ---------------------------------------------------------------- Begleiter auf der Insel

/** Watschelpinguin: wiegt sich bei jedem Schritt zur Seite, der andere Fuß hebt sich, die Flosse auf der Seite schwingt hoch. */
function penguinPet(f, n, x) {
  const step = Math.round(swing(f, n, 0, 4) * 1.4)
  const bx = x + step
  const top = GROUND - 12 - Math.abs(step)
  const c = layer().draw(PET.penguin, bx, top)
  c.draw(PET.penguinFoot, x + 1, GROUND - 1 - (step > 0 ? 1 : 0))
  c.draw(PET.penguinFoot, x + 4, GROUND - 1 - (step < 0 ? 1 : 0))
  if (step > 0) for (const [dx, dy] of [[-1, 7], [-1, 6], [-2, 5], [-2, 4], [-2, 3]]) c.set(bx + dx, top + dy, C.penguinHi)
  if (step < 0) for (const [dx, dy] of [[7, 7], [7, 6], [8, 5], [8, 4]]) c.set(bx + dx, top + dy, C.penguinDark)
  return c.outline(C.outline)
}

/** Füchschen: wedelt kräftig mit dem buschigen Schwanz, ab und zu zuckt ein Ohr. */
function foxPet(f, n, x) {
  const top = GROUND - 10
  const tail = layer().draw(PET.foxTail[[1, 2, 1, 0][Math.floor(f / 2) % 4]], x + 5, top - 1).outline(C.outline)
  const body = layer().draw(PET.fox, x, top)
  const twitch = f % (n / 2)
  if (twitch === 10 || twitch === 11) {
    body.erase(x + 5, top)
    body.set(x + 6, top + 1, C.sock)
  }
  return tail.draw(body.outline(C.outline))
}

/** Axolotl: wackelt mit dem Kopf, die Kiemen pulsieren und leuchten rosa auf. */
function axolotlPet(f, n, x) {
  const c = layer()
  const hop = swing(f, n, 0, 4) > 0.5 ? 1 : 0
  const wiggle = f % 8 === 3 || f % 8 === 4 ? 1 : 0
  const hx = x + 2 + wiggle
  const hy = GROUND - 6 - hop
  // Kiemen pulsieren viermal je Schleife: dunkel – rosa – hell leuchtend (dann mit längeren Spitzen)
  const glow = (1 + swing(f, n, 0.1, 4)) / 2
  const stalk = glow > 0.65 ? C.gillHi : C.gill
  const tip = glow > 0.75 ? C.puffHi : glow > 0.35 ? C.puff : C.gillHi
  for (const dir of [-1, 1]) {
    const cx = dir < 0 ? hx : hx + 6
    // drei Äste je Seite, von der oberen Ecke schräg nach oben und außen gefächert
    for (const [dx, dy] of [[0, 0], [1, -1], [1, 1], [1, 2]]) c.set(cx + dir * dx, hy + dy, stalk)
    for (const [dx, dy] of [[2, -2], [2, 0], [2, 2]]) c.set(cx + dir * dx, hy + dy, tip)
    if (glow > 0.75) for (const [dx, dy] of [[2, -3], [2, -1], [2, 3]]) c.set(cx + dir * dx, hy + dy, C.puff)
  }
  c.draw(PET.axolotl, hx, hy)
  return c.outline(C.outline)
}

/** Schneemännchen (leicht gedreht, ein Zweigarm zu sehen): wippt, der Bommel federt nach, der Arm winkt über dem Kopf. */
function snowmanPet(f, n, x) {
  const bobAt = (g) => (swing(g, n, 0.15, 2) > 0.3 ? 1 : 0)
  const top = GROUND - 15 - bobAt(f)
  const c = layer().draw(PET.snowman, x, top)
  // Bommel kommt einen Moment zu spät: beim Hochwippen sitzt er tiefer, beim Absinken schwebt er kurz
  c.erase(x + 3, top)
  c.set(x + 3, GROUND - 15 - bobAt(f - 1) + (bobAt(f) > bobAt(f - 1) ? 1 : 0), C.snow)
  // Winken: der Zweigarm schwingt um die Senkrechte, mit kleiner Gabel an der Hand
  const a = ((87.5 + 17.5 * swing(f, n, 0, 4)) * Math.PI) / 180
  const hx = x + 7 + Math.round(Math.cos(a) * 4)
  const hy = top + 8 - Math.round(Math.sin(a) * 4)
  c.line(x + 7, top + 8, hx, hy, C.twig)
  c.set(hx + (Math.cos(a) > 0.1 ? -1 : 1), hy, C.twig)
  return c.outline(C.outline)
}

// ---------------------------------------------------------------- Begleiter in der Luft

/** Mini-Cubi umkreist Cubis Kopf: oben vor ihm, unten dahinter; der Propeller dreht sich, die Füßchen baumeln. */
function miniCubiOrbit(f, n, hero) {
  const a = (f / n) * 2 * Math.PI + Math.PI / 2
  const x = Math.round(hero + 7.5 - 8 * Math.cos(a) - 4)
  const y = Math.round(16 - 5 * Math.sin(a) - 4)
  const c = layer()
  c.draw(PET.propeller[f % 4], x + 2, y)
  c.draw(PET.miniCubi, x, y + 2)
  c.draw(PET.miniFeet[Math.floor(f / 2) % 2], x, y + 7)
  c.outline(C.outline)
  return Math.sin(a) > -0.1 ? { front: c } : { back: c }
}

/** Sternschnuppe: zieht einmal je Schleife in einem Bogen von rechts nach links und lässt verglimmende Funken zurück. */
function shootingStar(f, n) {
  const c = layer()
  const span = (n * 3) / 4
  const start = n - n / 8 - 2
  const age = (f - start + n) % n
  const at = (k) => {
    const q = k / span
    return [Math.round(56 - 31 * q), Math.round(9 - 3 * Math.sin(Math.PI * q))]
  }
  // Funken der Spur: alle drei Bilder einer, er leuchtet auf und verglimmt
  for (let k = 0; k < span; k += 3) {
    const g = age - k
    const size = [0, 3, 3, 2, 2, 1, 1, 1][g] ?? 0
    if (g < 0 || !size) continue
    const [sx, sy] = at(k)
    const s = SPARKLES[size - 1]
    c.draw(s, sx - (s.w >> 1), sy - (s.h >> 1))
  }
  if (age >= span) return c
  // Schweif direkt hinter dem Stern
  for (let k = 1; k <= 4; k++) {
    const [sx, sy] = at(Math.max(0, age - k * 0.6))
    c.set(sx + 1, sy, k < 3 ? C.starHi : C.star)
  }
  const [x, y] = at(age)
  if (age < 2 || age > span - 2) {
    const s = SPARKLES[age < 1 || age > span - 1 ? 1 : 2]
    return c.draw(s, x - (s.w >> 1), y - (s.h >> 1))
  }
  const star = layer().draw(PET.star, x - 4, y - 4)
  if (f % 4 === 0) for (const [gx, gy] of [[x, y - 1], [x - 1, y - 2], [x + 1, y - 2]]) star.set(gx, gy, C.white)
  return c.draw(star.outline(C.outline))
}

/**
 * Funkelnde Sterne: Hier ist der Himmel so voll, dass die gewohnten Funken keinen Platz finden – darum feste Stellen,
 * die in keinem Bild belegt sind (an den Rändern und unter der Insel). [x, y, Phase, größte Stufe], x ab Cubi bzw. Insel.
 */
function nightSky(f, { left, right, hero }) {
  const c = layer()
  const stars = [
    [hero + 36, 14, 2, 3], [hero + 36, 4, 9, 3], [hero + 22, 11, 5, 2], [hero - 21, 2, 12, 2], [left + 2, 58, 3, 3], [right - 1, 59, 11, 3],
  ]
  for (const [x, y, phase, most] of stars) {
    const size = Math.min(most, TWINKLE[(f + phase) % FRAMES])
    if (!size) continue
    const s = SPARKLES[size - 1]
    c.draw(s, x - (s.w >> 1), y - (s.h >> 1))
  }
  return c
}

/** Glühwürmchen: fliegt eine kleine Acht, hinter ihm verglimmt eine Lichtspur, die Laterne pulsiert. */
function fireflyFlight(f, n, x0, y0) {
  const pos = (g) => {
    const a = (g / n) * 2 * Math.PI
    return [x0 + 5 * Math.sin(a), y0 + 2.5 * Math.sin(2 * a)]
  }
  const c = layer()
  const [x, y] = pos(f)
  const [px] = pos(f - 1)
  const right = x > px
  // Lichtspur: wo die Laterne eben noch war
  ;[C.lampHi, C.lamp, C.lampDark, C.lampDark].forEach((col, k) => {
    const [tx, ty] = pos(f - 1 - k)
    c.set(Math.round(tx) + (right ? 1 : 6), Math.round(ty) + 3, col)
  })
  const bx = Math.round(x)
  const by = Math.round(y)
  const bug = layer().draw(right ? flip(PET.firefly) : PET.firefly, bx, by)
  // Flügel flattern: jedes zweite Bild höher
  if (f % 2) {
    bug.erase(bx + (right ? 1 : 5), by + 2)
    bug.set(bx + (right ? 1 : 5), by + 1, C.wing)
  }
  c.draw(bug.outline(C.outline))
  // Laterne pulsiert: Licht wächst zu einem kleinen Stern und vergeht
  const pulse = [0, 1, 2, 3, 3, 2, 1, 0][f % 8]
  const lx = bx + (right ? 1 : 6)
  const ly = by + 3
  c.rect(lx, ly, 2, 2, pulse >= 2 ? C.lampHi : C.lamp)
  for (let k = 1; k <= pulse; k++) {
    const col = k === pulse ? C.lampDark : C.lamp
    for (const [gx, gy] of [[lx - k, ly], [lx + 1 + k, ly + 1], [lx, ly - k], [lx + 1, ly + 1 + k]]) c.set(gx, gy, col)
  }
  return c
}

/**
 * Jeder Gegenstand: f → Ebenen. `layer` bekommt einen Umriss, `front` (Licht, Flammen, Funken) und `ground`
 * (Rauch, Schatten auf der Insel) nicht bzw. eigene. `x` = linker Rand des freien Platzes rechts von Cubi.
 * Als zweites kommt die Lage von Insel und Cubi (`{ left, right, hero, frames }`); ein Gegenstand kann Cubi auch selbst
 * zeichnen (`hero`) und Ebenen am Himmel liefern (`sky`), die nicht mit der Insel schweben.
 */
const PROPS = {
  rocket(f) {
    const lift = Math.round(wave(f, 0, 2))
    const body = layer().draw(SPRITES.rocket, 38, 15 + lift).outline(C.outline)
    const front = layer().draw(SPRITES.flames[f % 4], 41, 33 + lift)
    const ground = layer()
    for (const [i, dir] of [[0, -1], [1, 1], [2, -1], [3, 1]]) {
      const t = ((f + i * 4) % FRAMES) / FRAMES
      const r = 1 + t * 2.2
      const x = 44 + dir * (2 + t * 9)
      const y = GROUND - 1 - t * 3
      ground.disc(x, y, r, C.smoke)
      ground.disc(x + 0.6, y + 0.8, r * 0.6, C.smokeShade)
    }
    return { layer: body, front, ground: ground.outline(C.outline) }
  },

  moon(f) {
    const bob = Math.round(wave(f))
    const c = layer()
    const cx = 44
    const cy = 22 + bob
    c.disc(cx, cy, 9, C.moon)
    for (let y = cy - 10; y <= cy + 10; y++) for (let x = cx - 10; x <= cx + 10; x++) if ((x - cx - 5) ** 2 + (y - cy + 4) ** 2 <= 8.2 ** 2) c.erase(x, y)
    c.bevel(0.35, 0.22)
    c.set(cx - 6, cy + 1, C.moonShade)
    c.set(cx - 4, cy + 5, C.moonShade)
    c.set(cx - 3, cy + 5, C.moonShade)
    // Schlafendes Gesicht
    c.set(cx - 5, cy - 2, C.mouth)
    c.set(cx - 4, cy - 1, C.mouth)
    c.set(cx - 3, cy - 2, C.mouth)
    c.set(cx - 2, cy + 3, C.cheek)
    c.outline(C.outline)
    const front = layer()
    for (let i = 0; i < 2; i++) {
      const t = (f + i * 8) % FRAMES
      if (t > 11) continue
      front.draw(SPRITES.z, cx + 4 + Math.round(t / 3), cy - 9 - Math.round(t * 0.75))
    }
    return { layer: c, front: front.outline(C.slateDark) }
  },

  bolt(f) {
    const bob = Math.round(wave(f))
    const flash = f % 8 === 3 || f % 8 === 4
    const c = layer().poly(
      [
        [46, 8 + bob],
        [36, 26 + bob],
        [43, 26 + bob],
        [39, 41 + bob],
        [52, 21 + bob],
        [45, 21 + bob],
        [51, 8 + bob],
      ],
      flash ? C.goldHi : C.gold,
    )
    c.bevel(0.4, 0.28).outline(C.outline)
    const front = layer()
    if (flash) {
      for (const [x, y] of [[33, 20], [55, 15], [53, 31], [35, 35]]) {
        front.set(x, y + bob, C.sparkle)
        front.set(x + 1, y + bob - 1, C.sparkleMid)
      }
    }
    return { layer: c, front }
  },

  wrench(f) {
    const bob = Math.round(wave(f))
    const c = layer()
    const ox = 36
    const oy = 12 + bob
    c.line(ox + 2, oy + 24, ox + 13, oy + 9, C.metal, 4)
    c.disc(ox + 15.5, oy + 6.5, 5.6, C.metal)
    // Maul: Öffnung nach oben rechts
    for (let y = oy; y <= oy + 9; y++) {
      for (let x = ox + 13; x <= ox + 22; x++) {
        const u = x - (ox + 15.5)
        const v = y - (oy + 6.5)
        if (Math.hypot(u, v) < 2.4 || (u - v > 1 && u + v > -3 && Math.hypot(u, v) < 7)) c.erase(x, y)
      }
    }
    c.bevel(0.42, 0.3)
    c.outline(C.outline)
    // Lichtkante wandert den Griff entlang
    const front = layer()
    const t = f % FRAMES
    if (t < 8) front.set(ox + 3 + t, oy + 22 - Math.round(t * 1.35), C.white)
    // Mutter am Boden
    const nut = layer().poly([[50, GROUND - 1], [49, GROUND - 3], [51, GROUND - 5], [54, GROUND - 5], [56, GROUND - 3], [55, GROUND - 1]], C.metalDark)
    nut.set(52, GROUND - 3, C.metalDeep)
    nut.set(53, GROUND - 3, C.metalDeep)
    nut.bevel(0.35, 0.25).outline(C.outline)
    return { layer: c, front, ground: nut }
  },

  camera(f) {
    const bob = Math.round(wave(f))
    const c = layer()
    const x = 34
    const y = 20 + bob
    c.rect(x + 3, y - 2, 5, 3, C.slate) // Sucher
    c.rect(x, y, 20, 13, C.slate)
    c.rect(x + 1, y, 18, 1, C.slateHi)
    c.rect(x, y + 1, 3, 12, C.slateDark)
    c.rect(x + 15, y - 1, 3, 1, C.red) // Auslöser
    c.rect(x + 14, y + 2, 4, 2, f % FRAMES === 10 || f % FRAMES === 11 ? C.white : C.metalHi) // Blitz
    c.disc(x + 10.5, y + 7, 4.8, C.metalDark)
    c.disc(x + 10.5, y + 7, 3.6, C.slateDark)
    c.disc(x + 10.5, y + 7, 2.6, C.glassDark)
    c.disc(x + 10.5, y + 7, 1.2, C.glass)
    c.set(x + 9, y + 5, C.glassHi)
    c.set(x + 10, y + 5, C.glassHi)
    c.outline(C.outline)
    const front = layer()
    if (f % FRAMES === 10 || f % FRAMES === 11) {
      const fx = x + 16
      const fy = y + 3
      const len = f % FRAMES === 10 ? 3 : 5
      for (const [dx, dy] of [[1, -1], [1, 0], [1, 1], [0, -1], [-1, -1]]) {
        for (let k = 2; k <= len; k++) front.set(fx + dx * k, fy + dy * k, k === len ? C.sparkleMid : C.white)
      }
    }
    return { layer: c, front }
  },

  shield(f) {
    const bob = Math.round(wave(f))
    const x = 36
    const y = 10 + bob
    const shape = [
      [x + 1, y],
      [x + 17, y],
      [x + 18, y + 1],
      [x + 18, y + 11],
      [x + 14, y + 18],
      [x + 9.5, y + 22],
      [x + 5, y + 18],
      [x + 1, y + 11],
      [x + 1, y + 1],
    ]
    const c = layer().poly(shape, C.metalHi)
    const inner = shape.map(([px, py]) => [x + 9.5 + (px - x - 9.5) * 0.78, y + 10 + (py - y - 10) * 0.8])
    c.poly(inner, C.cubi)
    for (let yy = y; yy <= y + 22; yy++) for (let xx = x; xx < x + 10; xx++) if (c.get(xx, yy)?.[1] === C.cubi[1]) c.set(xx, yy, C.cubiHi)
    // Haken in der Mitte
    c.line(x + 6, y + 10, x + 8, y + 13, C.white, 2)
    c.line(x + 8, y + 13, x + 13, y + 7, C.white, 2)
    c.bevel(0.2, 0.25).outline(C.outline)
    // Glanz wandert schräg über das Schild
    const t = f % FRAMES
    if (t < 9) {
      const shine = layer()
      for (let k = -12; k <= 12; k++) {
        const sx = x + t * 3 - 4 + k * 0.4
        const sy = y + 11 + k
        for (const dx of [0, 1]) {
          const px = Math.round(sx + dx)
          const py = Math.round(sy)
          const under = c.get(px, py)
          if (under && under[0] !== C.outline[0]) shine.set(px, py, [255, 255, 255, 120])
        }
      }
      return { layer: c, front: shine }
    }
    return { layer: c }
  },

  bubble(f) {
    // Zwei Würfel unterhalten sich: rechts ein Freund, darüber eine Sprechblase mit Tippen.
    const friend = cubi(f, 37, { sprites: FRIEND, mirror: true, phase: 5 })
    const c = layer()
    const x = 20
    const y = 9 + Math.round(wave(f) * 0.6)
    c.rect(x + 1, y, 20, 12, C.paper)
    c.rect(x, y + 1, 22, 10, C.paper)
    c.rect(x + 1, y + 11, 20, 1, C.paperShade)
    c.rect(x + 21, y + 1, 1, 10, C.paperShade)
    c.poly([[x + 4, y + 11], [x + 9, y + 11], [x + 3, y + 16]], C.paper)
    const typing = Math.floor((f % FRAMES) / 4)
    const dots = [3, 1, 2, 3][typing]
    for (let i = 0; i < dots; i++) c.rect(x + 5 + i * 5, y + 5, 3, 3, i === 1 ? C.cubi : C.slate)
    c.outline(C.outline)
    const front = layer()
    const t = (f + 6) % FRAMES
    if (t < 10) front.draw(SPRITES.heart, 48 + Math.round(wave(f, 0.25)), 16 - Math.round(t * 0.7))
    return { layer: c.draw(friend), front: front.outline(C.outline) }
  },

  compass(f) {
    const bob = Math.round(wave(f))
    const c = layer()
    const cx = 45
    const cy = 24 + bob
    c.disc(cx, cy, 10, C.gold)
    c.bevel(0.35, 0.3)
    c.disc(cx, cy, 7.5, C.paper)
    for (let y = cy - 8; y <= cy + 8; y++) {
      for (let x = cx - 8; x <= cx + 8; x++) {
        const d = Math.hypot(x - cx, y - cy)
        if (d <= 7.5 && d > 6.3 && x - cx + (y - cy) > 2) c.set(x, y, C.paperShade)
      }
    }
    c.rect(cx, cy - 7, 1, 2, C.slate)
    c.rect(cx, cy + 6, 1, 2, C.slate)
    c.rect(cx - 7, cy, 2, 1, C.slate)
    c.rect(cx + 6, cy, 2, 1, C.slate)
    const angle = wave(f) * 0.45 - 0.2
    const nx = Math.sin(angle)
    const ny = -Math.cos(angle)
    c.line(cx, cy, Math.round(cx + nx * 6), Math.round(cy + ny * 6), C.red, 2)
    c.line(cx, cy, Math.round(cx - nx * 5), Math.round(cy - ny * 5), C.metalDark, 2)
    c.set(cx, cy, C.slateDark)
    c.set(cx - 9, cy - 5, C.goldHi)
    c.rect(cx - 1, cy - 12, 3, 2, C.goldDark)
    c.outline(C.outline)
    return { layer: c }
  },

  chest(f) {
    const t = f % FRAMES
    const open = t < 6 ? 3 : 1
    const c = layer()
    const x = 36
    const base = GROUND - 8
    c.draw(SPRITES.chestBody, x, base)
    const glow = layer()
    for (let k = 1; k < 15; k++) {
      for (let j = 1; j <= open; j++) glow.set(x + k, base - j, j === open ? C.goldDark : k % 3 === 0 ? C.goldHi : C.gold)
    }
    c.draw(glow)
    c.draw(SPRITES.chestLid, x, base - 5 - open)
    c.outline(C.outline)
    const front = layer()
    if (open === 3) {
      for (const [dx, dy] of [[4, -11], [9, -13], [13, -10]]) sparkle(front, t, { x: x + dx, y: base + dy, phase: 2 })
    }
    return { layer: c, front }
  },

  monitor(f) {
    const c = layer()
    const x = 33
    const y = 17
    c.rect(x, y, 22, 17, C.slate)
    c.rect(x + 1, y, 20, 1, C.slateHi)
    c.rect(x + 2, y + 2, 18, 12, C.screen)
    c.rect(x + 9, y + 17, 4, 2, C.slateDark)
    c.rect(x + 5, y + 19, 12, 2, C.slate)
    c.rect(x + 5, y + 19, 12, 1, C.slateHi)
    // Bild: kleiner Würfel und Textzeilen
    const cube = sprite(['.tt.', 'tttt', 'gGGd', 'gGdd', '.Gd.'], { t: C.cubiTop, g: C.cubiHi, G: C.cubi, d: C.cubiSide })
    c.draw(cube, x + 4, y + 4)
    c.rect(x + 10, y + 5, 7, 1, C.cubi)
    c.rect(x + 10, y + 7, 5, 1, C.screenHi)
    c.rect(x + 10, y + 9, 6, 1, C.screenHi)
    if (f % 8 < 4) c.rect(x + 16, y + 9, 2, 1, C.cubiTop)
    const scan = y + 2 + (f % 12)
    if (scan < y + 14) for (let k = x + 2; k < x + 20; k++) if (c.get(k, scan)?.[0] === C.screen[0]) c.set(k, scan, C.screenHi)
    c.outline(C.outline)
    return { layer: c }
  },

  palette(f) {
    const bob = Math.round(wave(f))
    const c = layer()
    const cx = 45
    const cy = 25 + bob
    for (let y = cy - 8; y <= cy + 8; y++) {
      for (let x = cx - 11; x <= cx + 11; x++) if (((x - cx) / 10.5) ** 2 + ((y - cy) / 7.5) ** 2 <= 1) c.set(x, y, C.wood)
    }
    c.bevel(0.3, 0.3)
    for (let y = cy + 1; y <= cy + 5; y++) for (let x = cx + 3; x <= cx + 7; x++) if ((x - cx - 5) ** 2 + (y - cy - 3) ** 2 <= 2.6) c.erase(x, y)
    const blobs = [C.red, C.gold, C.blue, C.cubi, C.paper]
    const spots = [[-6, -3], [-2, -5], [3, -4], [-7, 2], [-2, 3]]
    spots.forEach(([dx, dy], i) => {
      c.rect(cx + dx, cy + dy, 2, 2, blobs[i])
      c.set(cx + dx, cy + dy, mix(blobs[i], C.white, 0.4))
    })
    c.outline(C.outline)
    // Pinsel tupft
    const dab = f % 8 < 4 ? 0 : 1
    const brush = layer()
    brush.line(cx + 9, cy - 16 - dab, cx + 3, cy - 7 - dab, C.woodDark, 2)
    brush.rect(cx + 2, cy - 7 - dab, 2, 2, C.metal)
    brush.set(cx + 1, cy - 5 - dab, blobs[Math.floor((f % FRAMES) / 4) % 4])
    brush.set(cx + 2, cy - 5 - dab, blobs[Math.floor((f % FRAMES) / 4) % 4])
    brush.outline(C.outline)
    return { layer: c.draw(brush) }
  },

  gear(f) {
    const c = layer()
    const turn = (f / FRAMES) * ((2 * Math.PI) / 8)
    gear(c, 42, 23, 9, 8, turn, C.metal)
    c.bevel(0.38, 0.3).outline(C.outline)
    const small = layer()
    gear(small, 52.5, 34, 5.5, 6, -(f / FRAMES) * ((2 * Math.PI) / 6) + 0.25, C.gold)
    small.bevel(0.35, 0.3).outline(C.outline)
    return { layer: c.draw(small) }
  },

  gift(f) {
    const t = f % FRAMES
    const hop = t === 6 || t === 7 ? 2 : t === 5 || t === 8 ? 1 : 0
    const c = layer()
    const x = 37
    const base = GROUND - 11
    c.rect(x, base, 16, 11, C.cubi)
    c.rect(x, base, 1, 11, C.cubiHi)
    c.rect(x + 14, base, 2, 11, C.cubiSide)
    c.rect(x + 7, base, 3, 11, C.gold)
    c.rect(x + 9, base, 1, 11, C.goldDark)
    const lid = layer()
    lid.rect(x - 1, base - 4 - hop, 18, 4, C.cubiTop)
    lid.rect(x - 1, base - 1 - hop, 18, 1, C.cubiTopShade)
    lid.rect(x + 7, base - 4 - hop, 3, 4, C.goldHi)
    lid.draw(SPRITES.bow, x + 5, base - 7 - hop)
    c.outline(C.outline)
    lid.outline(C.outline)
    const front = layer()
    if (hop) for (const [dx, dy, p] of [[-2, -6, 3], [18, -5, 2], [8, -12, 4]]) sparkle(front, t - 5, { x: x + dx, y: base + dy, phase: p })
    return { layer: c.draw(lid), front }
  },

  /**
   * Großer Würfel, halb schon wieder Cubiq-Grün, rechts noch im alten Navy. Eine Farbrolle fährt an der Grenze auf
   * und ab, frische Farbe glänzt hinter ihr, ein Tropfen fällt, daneben steht der Eimer mit grüner Farbe.
   */
  roller(f) {
    const navy = { top: hex('#3b4688'), front: hex('#283062'), dark: hex('#1a2046') }
    const c = layer()
    const x0 = 31
    const x1 = 52
    const top = GROUND - 24
    const seam = 44
    // Oberseite (4 Reihen) und Vorderseite; links von der Naht grün, rechts noch Navy.
    for (let y = top; y < GROUND; y++) {
      for (let x = x0; x <= x1; x++) {
        const green = x < seam
        const face = y < top + 4 ? (green ? (y === top + 3 ? C.cubiTopShade : C.cubiTop) : navy.top) : green ? C.cubi : navy.front
        c.set(x, y, face)
      }
    }
    // Kanten: links Licht, rechts Schatten, unten der dunkle Rand.
    for (let y = top + 4; y < GROUND; y++) {
      c.set(x0, y, C.cubiHi)
      c.set(x1, y, navy.dark)
      c.set(x1 - 1, y, navy.dark)
    }
    for (let x = x0; x <= x1; x++) c.set(x, GROUND - 1, x < seam ? C.cubiSide : navy.dark)
    c.outline(C.outline)

    // Rolle: fährt einmal je Schleife hinunter und wieder hinauf.
    const ry = top + 6 + Math.round((1 - Math.cos((f / FRAMES) * 2 * Math.PI)) * 6.5)
    // Frische Farbe glänzt links neben der Rolle (auf dem Würfel, ohne eigenen Umriss).
    for (let y = Math.max(top + 4, ry - 3); y <= Math.min(GROUND - 2, ry + 5); y++) {
      for (let x = seam - 5; x < seam - 1; x++) c.set(x, y, mix(C.cubi, C.grassHi, y >= ry - 1 && y <= ry + 3 ? 0.6 : 0.3))
    }
    const front = layer()
    front.rect(seam - 4, ry, 9, 4, C.cubiHi)
    front.rect(seam - 4, ry, 9, 1, C.grassHi)
    front.rect(seam - 4, ry + 3, 9, 1, C.cubiSide)
    front.rect(seam - 4, ry + 1, 1, 2, C.cubi)
    // Bügel und Stiel nach rechts oben
    front.line(seam + 5, ry + 1, seam + 6, ry + 1, C.metalDark)
    front.line(seam + 6, ry + 1, seam + 6, ry - 3, C.metalDark)
    front.line(seam + 6, ry - 3, seam + 13, ry - 10, C.woodDark, 2)
    front.outline(C.outline)
    // Ein Tropfen löst sich alle halbe Schleife und fällt.
    const t = f % 8
    if (t >= 2) {
      const dy = ry + 4 + (t - 2) * 2
      if (dy < GROUND - 2) {
        front.set(seam - 1, dy, C.cubi)
        front.set(seam - 1, dy + 1, C.cubiSide)
      }
    }

    // Farbeimer rechts neben dem Würfel
    const ground = layer()
    const bx = 55
    ground.rect(bx, GROUND - 7, 7, 7, C.metal)
    ground.rect(bx, GROUND - 7, 1, 7, C.metalHi)
    ground.rect(bx + 6, GROUND - 7, 1, 7, C.metalDark)
    ground.rect(bx, GROUND - 7, 7, 1, C.cubiHi)
    ground.rect(bx + 1, GROUND - 8, 5, 1, C.cubi)
    ground.set(bx + 2, GROUND - 6, C.cubi)
    ground.set(bx + 2, GROUND - 5, C.cubi)
    ground.line(bx, GROUND - 9, bx + 6, GROUND - 9, C.metalDark)
    ground.outline(C.outline)
    return { layer: c, front, ground }
  },

  /**
   * Die neun neuen Kosmetik-Teile: Cubi hüpft in der Mitte (`hero: 'middle'`) und schlägt mit den gefiederten
   * Polarlichtflügeln, links Pinguin und Füchschen, rechts Axolotl und Schneemännchen; Mini-Cubi umkreist seinen Kopf,
   * die Sternschnuppe zieht über den Himmel, ein Glühwürmchen leuchtet in der Luft.
   */
  companions(f, { left, right, hero, frames: n }) {
    const body = hopBody(f, hero)
    const c = auroraWings(f, n, body)
    c.draw(penguinPet(f, n, Math.max(left, 3)))
    c.draw(foxPet(f, n, hero - 12))
    c.draw(snowmanPet(f, n, Math.min(right - 6, SIZE - 11)))
    c.draw(axolotlPet(f, n, hero + 18))
    const mini = miniCubiOrbit(f, n, hero)
    const sky = nightSky(f, { left, right, hero }).draw(fireflyFlight(f, n, left + 5, 6)).draw(shootingStar(f, n))
    return { layer: c, hero: hoppingCubi(f, n, body), ground: mini.back, front: mini.front, sky }
  },

  snowman(f) {
    const c = layer()
    const x = 45
    c.disc(x, GROUND - 6, 6.2, C.snow)
    c.disc(x, GROUND - 15, 4.8, C.snow)
    c.disc(x, GROUND - 22, 3.8, C.snow)
    c.map((col, px, py) => (px - x + (py - (GROUND - 12)) * 0.15 > 2.4 ? C.snowShade : col))
    // Gesicht, Knöpfe, Nase
    c.set(x - 2, GROUND - 23, C.coal)
    c.set(x + 1, GROUND - 23, C.coal)
    c.set(x, GROUND - 21, C.carrot)
    c.set(x + 1, GROUND - 21, C.carrot)
    c.set(x + 2, GROUND - 21, C.orange)
    for (const y of [GROUND - 16, GROUND - 13, GROUND - 8]) c.set(x, y, C.coal)
    // Hut mit Cubiq-grünem Band
    c.rect(x - 4, GROUND - 26, 9, 1, C.hat)
    c.rect(x - 3, GROUND - 31, 7, 5, C.hat)
    c.rect(x - 3, GROUND - 31, 1, 5, C.hatHi)
    c.rect(x - 3, GROUND - 28, 7, 1, C.cubi)
    // Schal, das Ende flattert
    c.rect(x - 4, GROUND - 19, 9, 2, C.cubi)
    c.rect(x - 4, GROUND - 19, 9, 1, C.cubiHi)
    const flap = Math.round(wave(f, 0, 2))
    c.rect(x + 3, GROUND - 17, 2, 4 + flap, C.cubi)
    c.set(x + 3, GROUND - 17 + 3 + flap, C.cubiSide)
    // Arme aus Zweigen
    c.line(x - 5, GROUND - 15, x - 9, GROUND - 19, C.woodDark)
    c.line(x + 5, GROUND - 15, x + 9, GROUND - 18, C.woodDark)
    c.set(x - 10, GROUND - 20, C.woodDark)
    c.outline(C.outline)
    return { layer: c }
  },
}

// ---------------------------------------------------------------- Szene

/** Bereich, den Funkeln und Würfel frei lassen sollen: alle gefüllten Punkte plus Rand. */
function occupied(layers, margin = 2) {
  const mask = new Canvas(SIZE, SIZE)
  for (const c of layers) for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) if (c.get(x, y)) mask.set(x, y, C.white)
  return (x, y, r) => {
    for (let j = y - r - margin; j <= y + r + margin; j++) for (let i = x - r - margin; i <= x + r + margin; i++) if (mask.get(i, j) || !mask.inside(i, j)) return true
    return false
  }
}

/** Alle Bilder der Szene einer Version (je SIZE × SIZE), dazu das gewählte Thema. */
export function releaseFrames(version, topicId) {
  const topic = [...TOPICS, ...SPECIAL_TOPICS, DEFAULT_TOPIC].find((t) => t.id === topicId) ?? DEFAULT_TOPIC
  const count = topic.frames ?? FRAMES
  const rnd = random(seedFor(version))
  const snowy = topic.id === 'winter'
  const ground = island(rnd, { snowy, wide: topic.wide ?? 0 })
  // Cubi steht links neben dem Gegenstand – bei `hero: 'middle'` mitten auf der Insel, der Gegenstand um ihn herum.
  const heroX = topic.hero === 'middle' ? Math.floor((ground.left + ground.right + 1) / 2) - 8 : ground.left + 3 + Math.floor(rnd() * 2)
  const prop = (f) => PROPS[topic.subject](f, { left: ground.left, right: ground.right, hero: heroX, frames: count })

  // Platz für Funkeln und Würfel: was in einem der Bilder belegt ist, bleibt frei.
  const busy = []
  for (let f = 0; f < count; f += 3) {
    const p = prop(f)
    busy.push(p.hero ?? cubi(f, heroX), p.layer, ...[p.front, p.ground, p.sky].filter(Boolean))
  }
  const blocked = occupied([ground.canvas, ...busy])
  const spots = []
  for (let y = 3; y < GROUND - 2; y += 2) for (let x = 3; x < SIZE - 3; x += 2) spots.push([x, y])
  for (let i = spots.length - 1; i > 0; i--) {
    const j = Math.floor(rnd() * (i + 1))
    ;[spots[i], spots[j]] = [spots[j], spots[i]]
  }
  const sparkles = []
  const cubes = []
  for (const [x, y] of spots) {
    if (cubes.length < (topic.cubes ?? 2) && !blocked(x, y, 3) && y > 8 && !cubes.some((c) => Math.abs(c.x - x) < 14)) {
      cubes.push({ x: x - 2, y: y - 2, phase: rnd() })
      continue
    }
    if (sparkles.length >= (topic.sparkles ?? 5) || blocked(x, y, 2)) continue
    if (sparkles.some((s) => Math.hypot(s.x - x, s.y - y) < 9) || cubes.some((c) => Math.hypot(c.x + 2 - x, c.y + 2 - y) < 7)) continue
    // Etwa die Hälfte leuchtet im ersten Bild (dem ruhigen Bild), der Rest später.
    sparkles.push({ x, y, phase: [2, 9, 4, 12, 3][sparkles.length] })
  }
  const flakes = snowy ? snowfall(rnd) : []
  const mini = MINI_CUBE(snowy)

  const frames = []
  for (let f = 0; f < count; f++) {
    const frame = layer()
    if (snowy) drawSnow(frame, f, flakes)
    for (const s of sparkles.slice(0, 2)) sparkle(frame, f, s)
    for (const c of cubes) frame.draw(layer().draw(mini, c.x, c.y + Math.round(wave(f, c.phase))).outline(C.outline))
    // `float`: Die Insel schwebt auf und ab, alles darauf mit – nur der Himmel (`sky`) bleibt, wo er ist.
    const dy = topic.float ? -Math.round(Math.sin((f / count) * 2 * Math.PI) * topic.float) : 0
    frame.draw(ground.canvas, 0, dy)
    const p = prop(f)
    if (p.ground) frame.draw(p.ground, 0, dy)
    frame.draw(p.layer, 0, dy)
    // Ein Gegenstand kann Cubi selbst zeichnen (`hero`), etwa mit eigener Bewegung.
    frame.draw(p.hero ?? cubi(f, heroX), 0, dy)
    if (p.front) frame.draw(p.front, 0, dy)
    if (p.sky) frame.draw(p.sky)
    for (const s of sparkles.slice(2)) sparkle(frame, f, s)
    frames.push(frame)
  }
  return { frames, topic }
}

/**
 * Bilder eines Release: `png` (ruhig, das erste Bild) und `apng` (animiert), je 512 × 512 und freigestellt. Thema aus
 * `topic` (z. B. „launch“) oder dem Text.
 */
export function releaseArt({ version, text = '', topic: topicId = null }) {
  const { frames, topic } = releaseFrames(version, topicId ?? pickTopic(text).id)
  const big = frames.map((frame) => upscale(frame, SCALE))
  return { png: encodePng(big[0]), apng: encodeApng(big, big.map(() => topic.frameMs ?? FRAME_MS)), topic: topic.id }
}

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
    for (const topic of [...TOPICS, ...SPECIAL_TOPICS, DEFAULT_TOPIC]) {
      const art = releaseArt({ version: `0.9.${topic.id.length}`, topic: topic.id })
      writeFileSync(join(dir, `${topic.id}.png`), art.png)
      writeFileSync(join(dir, `${topic.id}-anim.png`), art.apng)
    }
    console.error(`Vorschau aller Themen → ${dir}`)
  } else {
    const version = (args.find((a) => !a.startsWith('--') && a !== opt('--topic') && a !== opt('--out')) ?? '').replace(/^v/, '')
    if (!/^\d+\.\d+\.\d+/.test(version)) {
      console.error('Aufruf: release-art.mjs <version> [--topic <thema>] [--out <ordner>] | --sheet <ordner>')
      process.exit(2)
    }
    const { changelogFor, parseChangelog } = await import('../app/utils/changelog.ts')
    const entry = changelogFor(parseChangelog(readFileSync(join(root, 'CHANGELOG.md'), 'utf8')), version)
    const out = opt('--out') ?? join(root, 'public', 'news', version)
    const { png, apng, topic } = releaseArt({ version, text: entry?.en || entry?.de || '', topic: opt('--topic') ?? null })
    if (!existsSync(out)) mkdirSync(out, { recursive: true })
    writeFileSync(join(out, 'banner.png'), png)
    writeFileSync(join(out, 'banner-anim.png'), apng)
    console.error(`Bilder für ${version} (Thema „${topic}“) → ${join(out, 'banner.png')}, banner-anim.png`)
  }
}
