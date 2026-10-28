// Bild für jedes Release: eine kleine Pixel-Art-Szene (freigestellt, 512 × 512) – Cubi, der Würfel aus dem
// Cubiq-Logo, steht auf einer schwebenden Grasinsel neben einem Gegenstand, der zum Update passt: eine Rakete für
// einen Start, ein Schraubenschlüssel für Fehlerbehebungen, ein Bildschirm für den Cubiq Client, ein Schneemann im
// Winter … Dazu Funkeln und kleine schwebende Würfel. Der Launcher zeigt das Bild im Update-Banner („Was ist neu“,
// Neuigkeiten), die Website im Blog und GitHub im Release-Text.
//
// Zwei Dateien je Version: `banner.png` (ruhig) und `banner-anim.png` – dieselbe Szene als animiertes PNG
// (16 Bilder, 1,6 s, endlos): Cubi wippt und blinzelt, der Gegenstand bewegt sich, es funkelt. Bei „Weniger
// Animationen“ zeigen Launcher und Website das ruhige Bild; es ist zugleich das erste Bild der Animation.
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
    // Karte (Papier, Wasser, Sand, Wiese, Route)
    parchment: '#f1e2b6',
    parchmentShade: '#dcc68f',
    waterHi: '#bfe6f8',
    water: '#72b8e8',
    waterDeep: '#4b92cf',
    sand: '#e9c66f',
    meadow: '#cfe08f',
    routeHi: '#a9d46b',
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
/** Sonderthemen für ein bestimmtes Release: nur mit `--topic` gezeichnet, nie aus dem Text gewählt. */
export const SPECIAL_TOPICS = [
  // 0.2.51: Der Launcher wird wieder grün – Cubi streicht den großen Würfel vom Navy zurück in Cubiq-Grün.
  { id: 'green', subject: 'roller' },
  // 0.3.1: Das Karten-Update – neben Cubi steht eine große aufgefaltete Karte, eine grüne Route führt zum Wegpunkt.
  { id: 'map', subject: 'map' },
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

/** Schwebende Grasinsel (oder verschneit): oben Gras, darunter Erde, nach unten schmaler mit Steinen und Wurzeln. */
function island(rnd, { snowy = false } = {}) {
  const c = layer()
  const width = 46 + Math.floor(rnd() * 5)
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
    l += 1 + Math.floor(rnd() * 3)
    r -= 1 + Math.floor(rnd() * 3)
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
  // Flächen der Karte (24 × 24, „.“ = blankes Papier): Wiesen und ein See mit Sandufer und Glanz.
  mapLand: sprite(
    [
      '........................',
      '........................',
      '........................',
      '........................',
      '........................',
      '........................',
      '........................',
      '........................',
      '........................',
      '....................g...',
      '...................gggg.',
      '..................ggggg.',
      '...................gggg.',
      '....................gg..',
      '...............sss......',
      '............ssswwwss....',
      '..ggg......sshhwwwwss...',
      '.gggggg...sswwwWWwwws...',
      '.ggggggg..swwwWWWwwws...',
      '.gggggggg.sswwwwwwwss...',
      '..ggggggg...sswwwwss....',
      '...ggggg......ssss......',
      '....ggg.................',
      '........................',
    ],
    { g: C.meadow, w: C.water, W: C.waterDeep, h: C.waterHi, s: C.sand },
  ),
  // Kartenzeichen, aufrecht gestempelt (sie knicken nicht mit dem Papier)
  mapMountain: sprite(['...n...', '..nnN..', '..mmM..', '.mmmMM.', 'mmmmMMM'], { n: C.snow, N: C.snowShade, m: C.stone, M: C.stoneDark }),
  mapTree: sprite(['.lLL.', 'lLLLD', 'LLLDD', '.LDD.', '..s..'], { l: C.leaf, L: C.grassDark, D: C.cubiBottom, s: C.woodDark }),
  mapHome: sprite(['ttt', 'GGS', 'GGS'], { t: C.cubiTop, G: C.cubi, S: C.cubiSide }),
  mapScale: sprite(['S..S', 'SSss'], { S: C.slateDark, s: C.slateHi }),
  pin: sprite(['.rrr.', 'rhrrR', 'rrwrR', 'rrrrR', '.rrR.', '..R..'], { r: C.red, R: C.redDark, h: C.white, w: C.white }),
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

/**
 * Jeder Gegenstand: f → Ebenen. `layer` bekommt einen Umriss, `front` (Licht, Flammen, Funken) und `ground`
 * (Rauch, Schatten auf der Insel) nicht bzw. eigene. `x` = linker Rand des freien Platzes rechts von Cubi.
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
   * Große aufgefaltete Karte aus drei Feldern, im Zickzack gefaltet – so steht sie von selbst: die Falten vorn liegen
   * im Bild tiefer, das mittlere Feld liegt im Schatten, Flächen und Route knicken mit dem Papier. Eine gestrichelte
   * Route in Cubiq-Grün läuft von Cubis kleinem Würfel zum Wegpunkt (die Striche wandern), die rote Nadel darüber wippt
   * und funkelt. An der Ecke klebt eine runde Minikarte mit Norden und Blickkegel, der sich langsam hin und her dreht.
   */
  map(f) {
    const x0 = 31
    const w = 24
    const h = 24
    const fold = 8
    // Versatz je Spalte: Felder 1 und 3 laufen nach rechts nach vorn (im Bild tiefer), Feld 2 wieder nach hinten.
    const sink = (u) => {
      const t = ((u % fold) + 0.5) / fold
      return Math.round(2 * (Math.floor(u / fold) % 2 ? 1 - t : t))
    }
    const top = (u) => GROUND - h + sink(u)
    // Mittleres Feld im Schatten, an den Falten eine dunklere Linie.
    const tone = (col, u) => {
      const shaded = Math.floor(u / fold) === 1 ? mix(col, C.dirtDeep, 0.16) : col
      return u % fold === fold - 1 && u < w - 1 ? mix(shaded, C.dirtDeep, 0.2) : shaded
    }

    // Papier mit Flächen und Route in Kartenkoordinaten (u, v), danach Spalte für Spalte versetzt aufs Bild.
    const sheet = new Canvas(w, h).rect(0, 0, w, h, C.parchment).draw(SPRITES.mapLand)
    // Route vom kleinen Würfel links zum Ziel oben rechts, Punkt für Punkt (ohne doppelte Punkte an den Ecken).
    const stops = [[4, 13], [6, 14], [8, 14], [10, 13], [12, 11], [14, 10], [16, 9], [17, 7], [18, 5]]
    const route = []
    for (let i = 1; i < stops.length; i++) {
      const [au, av] = stops[i - 1]
      const [bu, bv] = stops[i]
      const n = Math.max(Math.abs(bu - au), Math.abs(bv - av))
      for (let k = 0; k <= n; k++) {
        const p = [Math.round(au + ((bu - au) * k) / n), Math.round(av + ((bv - av) * k) / n)]
        const last = route[route.length - 1]
        if (!last || last[0] !== p[0] || last[1] !== p[1]) route.push(p)
      }
    }
    // Blasse Spur, darauf Striche (3 an, 1 aus), die alle 2 Bilder einen Punkt Richtung Ziel wandern.
    const step = Math.floor(f / 2)
    route.forEach(([u, v], i) => sheet.set(u, v, (((i - step) % 4) + 4) % 4 < 3 ? C.cubi : C.routeHi))
    const [du, dv] = stops[stops.length - 1]
    // Die Nadel schwebt über dem Ziel; ihr Schatten wird schmaler, wenn sie oben ist.
    const bob = f % FRAMES >= 4 && f % FRAMES < 12 ? 1 : 0
    sheet.rect(du - 1 + bob, dv, 3 - 2 * bob, 1, C.parchmentShade)

    const c = layer()
    for (let u = 0; u < w; u++) for (let v = 0; v < h; v++) c.set(x0 + u, top(u) + v, tone(sheet.get(u, v), u))
    // Kartenzeichen aufrecht: Höhe nach ihrer Mitte, Schatten je Spalte.
    const stamp = (spr, u, v) => {
      const y = top(u + (spr.w >> 1)) + v
      for (let j = 0; j < spr.h; j++) {
        for (let i = 0; i < spr.w; i++) {
          const col = spr.get(i, j)
          if (col) c.set(x0 + u + i, y + j, tone(col, u + i))
        }
      }
    }
    stamp(SPRITES.mapMountain, 8, 3)
    for (const [u, v] of [[1, 16], [3, 18], [18, 9]]) stamp(SPRITES.mapTree, u, v)
    stamp(SPRITES.mapHome, 1, 12)
    stamp(SPRITES.mapScale, 19, 21)
    c.bevel(0.3, 0.18).outline(C.outline)

    // Runde Minikarte an der Ecke oben links: ruhiger Rahmen, Norden rot, Pfeil mit Blickkegel – er schaut zum Wegpunkt
    // und dreht sich dabei langsam hin und her.
    const badge = layer()
    const bx = 33
    const by = 23
    badge.disc(bx, by, 4.7, C.slate).bevel(0.3, 0.3)
    badge.disc(bx, by, 3.6, C.grass)
    // Innen unten rechts Wasser, darüber der hellere Kegel in Blickrichtung `a` (0 = Norden, im Uhrzeigersinn).
    const a = 1.2 + wave(f) * 0.3
    for (let y = by - 4; y <= by + 4; y++) {
      for (let x = bx - 4; x <= bx + 4; x++) {
        const d = Math.hypot(x - bx, y - by)
        if (d > 3.6) continue
        if (x - bx + (y - by) > 2) badge.set(x, y, C.water)
        if (d >= 0.9 && Math.abs(Math.atan2(x - bx, by - y) - a) < 0.5) badge.set(x, y, [255, 255, 255, 140])
      }
    }
    badge.set(bx, by, C.white)
    badge.set(Math.round(bx + Math.sin(a) * 1.6), Math.round(by - Math.cos(a) * 1.6), C.white)
    badge.set(bx, by - 5, C.red)
    badge.outline(C.outline)
    c.draw(badge)

    // Wegpunkt: rote Nadel über dem Ziel, wippt; daneben funkelt es.
    const front = layer()
    const px = x0 + du
    const py = top(du) + dv
    front.draw(layer().draw(SPRITES.pin, px - 2, py - 7 - bob).outline(C.outline))
    sparkle(front, f, { x: px + 5, y: py - 9, phase: 3 })
    return { layer: c, front }
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
  const rnd = random(seedFor(version))
  const snowy = topic.id === 'winter'
  const ground = island(rnd, { snowy })
  const prop = PROPS[topic.subject]
  const heroX = ground.left + 3 + Math.floor(rnd() * 2)

  // Platz für Funkeln und Würfel: was in einem der Bilder belegt ist, bleibt frei.
  const busy = []
  for (let f = 0; f < FRAMES; f += 3) {
    const p = prop(f)
    busy.push(cubi(f, heroX), p.layer, ...(p.front ? [p.front] : []), ...(p.ground ? [p.ground] : []))
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
    if (cubes.length < 2 && !blocked(x, y, 3) && y > 8 && !cubes.some((c) => Math.abs(c.x - x) < 14)) {
      cubes.push({ x: x - 2, y: y - 2, phase: rnd() })
      continue
    }
    if (sparkles.length >= 5 || blocked(x, y, 2)) continue
    if (sparkles.some((s) => Math.hypot(s.x - x, s.y - y) < 9) || cubes.some((c) => Math.hypot(c.x + 2 - x, c.y + 2 - y) < 7)) continue
    // Etwa die Hälfte leuchtet im ersten Bild (dem ruhigen Bild), der Rest später.
    sparkles.push({ x, y, phase: [2, 9, 4, 12, 3][sparkles.length] })
  }
  const flakes = snowy ? snowfall(rnd) : []
  const mini = MINI_CUBE(snowy)

  const frames = []
  for (let f = 0; f < FRAMES; f++) {
    const frame = layer()
    if (snowy) drawSnow(frame, f, flakes)
    for (const s of sparkles.slice(0, 2)) sparkle(frame, f, s)
    for (const c of cubes) frame.draw(layer().draw(mini, c.x, c.y + Math.round(wave(f, c.phase))).outline(C.outline))
    frame.draw(ground.canvas)
    const p = prop(f)
    if (p.ground) frame.draw(p.ground)
    frame.draw(p.layer)
    frame.draw(cubi(f, heroX))
    if (p.front) frame.draw(p.front)
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
  return { png: encodePng(big[0]), apng: encodeApng(big, big.map(() => FRAME_MS)), topic: topic.id }
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
