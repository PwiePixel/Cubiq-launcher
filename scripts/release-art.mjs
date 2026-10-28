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
    // Karte (Papier, Wasser, Sand, Wiese, Route)
    parchment: '#f1e2b6',
    parchmentShade: '#dcc68f',
    waterHi: '#bfe6f8',
    water: '#72b8e8',
    waterDeep: '#4b92cf',
    sand: '#e9c66f',
    meadow: '#cfe08f',
    routeHi: '#a9d46b',
    // Streicheln und Füttern (Sonderthema „petting“): silbrig-blauer Fisch, Herzchen
    fishHi: '#d6ecf6',
    fish: '#94c4dd',
    fishDark: '#5b8fb6',
    fishDeep: '#41719b',
    heartHi: '#ffd0e2',
    heart: '#f2648c',
    heartDark: '#c43c66',
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
  // 0.3.1: Das Karten-Update – neben Cubi steht eine große aufgefaltete Karte, eine grüne Route führt zum Wegpunkt.
  { id: 'map', subject: 'map' },
  // 0.3.2: Begleiter streicheln und füttern – Cubi mitten auf der schwebenden Insel krault mit einem Ärmchen das
  // Füchschen (Herzchen steigen auf) und hält dem Pinguin mit dem anderen einen Fisch hin (Bissen, Krümel, zufriedenes
  // Kauen); darüber schwebt das Axolotl auf einer eigenen kleinen Insel, statt der Würfel. Schleife wie bei 0.3.0.
  { id: 'petting', subject: 'petting', hero: 'middle', wide: 6, cubes: 0, sparkles: 3, float: 1, frames: 32, frameMs: 80 },
  // 0.3.3: Die klassische Minikarte – über der Insel schwebt eine große quadratische Minikarte mit dünnem grauem
  // Rahmen, rotem „N“ auf dunkler Platte, rotem Spielerpfeil und bunten Wegpunkt-Kästchen mit Anfangsbuchstaben. Ein
  // Freund auf seinem Inselchen teilt im Chat einen Wegpunkt; ein Klick auf „+“, und eine Kopie fliegt im Bogen in die
  // Karte und landet mit Funkeln und einem grünen „+“ – Cubi schaut ihr nach und hüpft vor Freude. Funkeln an festen
  // Stellen statt der Würfel; die Insel ist etwas breiter, so bleibt ihre Unterseite beim Schweben ganz im Bild.
  // Schleife wie bei 0.3.0.
  { id: 'waypoints', subject: 'waypoints', wide: 2, cubes: 0, sparkles: 0, float: 1, frames: 32, frameMs: 80 },
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

// ---------------------------------------------------------------- Streicheln und Füttern (Sonderthema „petting“)
//
// Cubi kümmert sich um seine Begleiter (eigene Schleife wie bei „cosmetics“): Mit dem rechten Ärmchen krault er das
// Füchschen – es kneift glücklich die Augen zu, legt die Ohren an, geht der Hand nach und wedelt, Herzchen steigen auf.
// Mit dem linken hält er dem Pinguin einen Fisch hin: der schaut hoch, sperrt den Schnabel auf, schnappt dreimal zu
// (Krümel fliegen) und kaut zufrieden. Über Cubi schwebt das Axolotl auf seiner eigenen kleinen Insel. Alles hängt am
// Bild der Schleife, damit sie nahtlos bleibt.

/** Farben des Pinguins (wie im Kosmetik-Bild), hier von der Seite gezeichnet. */
const PENGUIN_SIDE = {
  K: C.penguin, k: C.penguinDark, h: C.penguinHi, W: C.belly, E: C.pupil, P: C.cheek, B: C.beak, b: C.beakDark, M: C.lip,
  R: C.scarf, r: C.scarfDark, S: C.scarfStripe,
}

const CARE = {
  // Fisch: dunkler Rücken, weißer Bauch, Gabelschwanz rechts – dort hält Cubi ihn
  fish: sprite(['...dd...', '.ddddd.D', 'hEfffdD.', '.wwwww.D'], {
    d: C.fishDark, D: C.fishDeep, h: C.fishHi, f: C.fish, E: C.pupil, w: C.paper,
  }),
  // Herzchen in vier Größen: ploppt als Punkt auf, wächst beim Steigen, schrumpft beim Verblassen wieder (dann blass)
  hearts: [
    sprite(['p'], { p: C.heart }),
    sprite(['p.p', 'ppp', '.p.'], { p: C.heart }),
    sprite(['hp.pp', 'ppppp', '.ppd.', '..d..'], { h: C.heartHi, p: C.heart, d: C.heartDark }),
    sprite(['.pp.pp.', 'phhpppp', 'phppppd', '.ppppd.', '..ppd..', '...d...'], { h: C.heartHi, p: C.heart, d: C.heartDark }),
  ],
  // Pinguin von der Seite (schaut nach rechts zu Cubi): Kopf je Haltung – glücklich (Auge zu), kauend (Unterschnabel
  // klappt auf), hoch (Schnabel zum Fisch), aufgesperrt; darunter Schal, Flosse und Bauch. Das geschlossene Auge ist
  // ein kurzer Strich in der weißen Maske (wie beim Füchschen), die Maske bleibt ein Stück: Ein weißer Punkt, ringsum
  // von Dunklem umschlossen, sähe aus wie ein Fleck auf dem Kopf oder ein zweites Augenpaar.
  penguinHead: Object.fromEntries(
    Object.entries({
      happy: ['..hKKk...', '.hKKKKk..', '.KKWWWk..', '.KKEEWk..', '.KPWWWBB.', '.KKWWWb..'],
      chew: ['..hKKk...', '.hKKKKk..', '.KKWWWk..', '.KKEEWk..', '.KPWWWBB.', '.KKWWWMb.'],
      up: ['....BB...', '..hKKB...', '.hKKEWk..', '.KKWWWk..', '.KPWWWk..', '.KKWWWk..'],
      open: ['.....B...', '...KKBMb.', '.hKKEWbb.', '.KKWWWk..', '.KPWWWk..', '.KKWWWk..'],
    }).map(([k, rows]) => [k, sprite(rows, PENGUIN_SIDE)]),
  ),
  penguinBody: sprite(['RRRSRRr..', 'hKhWWWWk.', 'hKhWWWWk.', 'KKKhWWWk.', '.kKKKKk..'], PENGUIN_SIDE),
  penguinFeet: sprite(['..BB.BB..'], PENGUIN_SIDE),
  // Cubis Augen beim Kümmern, je 2 × 3 wie im Sprite: zum Füchschen nach rechts unten (wie im Sprite), zum Fisch nach
  // links oben; zu (Strich unten wie beim Blinzeln) und halb offen (das Lid deckt noch die obere Reihe).
  eyes: Object.fromEntries(
    Object.entries({
      fox: ['WW', 'WK', 'WK'],
      fish: ['KW', 'KW', 'WW'],
      shut: ['GG', 'GG', 'KK'],
      foxHalf: ['GG', 'WK', 'WK'],
      fishHalf: ['GG', 'KW', 'WW'],
    }).map(([k, rows]) => [k, sprite(rows, { W: C.eye, K: C.pupil, G: C.cubi })]),
  ),
}

/**
 * Fütter-Ablauf des Pinguins, ein Zeichen je Bild: u = schaut hoch, o = sperrt den Schnabel auf und streckt sich,
 * B = schnappt zu (Krümel fliegen), c/C = kaut (Schnabel auf/zu, Auge glücklich zu), h/H = zufrieden, hüpft und schlägt
 * mit der Flosse. Drei Bissen, nach dem letzten wird länger gekaut.
 */
const FEED = 'oBccCCuoBccCCuoBccCCccCChhHHhhHu'
/** Bilder, in denen der Pinguin zuschnappt. */
const BITES = [...FEED].flatMap((s, f) => (s === 'B' ? [f] : []))
/** Nach dem dritten Bissen schaut Cubi zum Füchschen – von Bild 18 bis vor Bild 30 –, sonst zum Fisch. */
const LOOK_FOX = [18, 30]

/**
 * Cubis Augen im Bild f (von n) als Schlüssel in CARE.eyes: Beim Fressen schaut er hoch zum Fisch, den er dem Pinguin
 * hinhält, beim Kraulen zum Füchschen. Jeder Blickwechsel geschieht mit einem Lidschlag – zu, halb auf in die neue
 * Richtung, ganz auf –, so springen die Pupillen nie (zwei Lidschläge je Schleife, auch über die Naht hinweg).
 */
function careGaze(f, n) {
  const [from, to] = LOOK_FOX
  const fox = f >= from && f < to
  const since = (f - (fox ? from : to) + n) % n
  return since === 0 ? 'shut' : `${fox ? 'fox' : 'fish'}${since === 1 ? 'Half' : ''}`
}

/**
 * Ärmchen von der Schulter (sx, sy) zur Hand (hx, hy): zwei Punkte dick, die Hand ein runder Klecks (3 × `hand`) –
 * oben und links im Licht, unten im Schatten wie Cubis Körper.
 */
function cubiArm(c, sx, sy, hx, hy, hand = 3) {
  const arm = layer()
  const steps = Math.max(Math.abs(hx - sx), Math.abs(hy - sy), 1)
  for (let k = 0; k <= steps; k++) {
    const x = Math.round(sx + ((hx - sx) * k) / steps)
    const y = Math.round(sy + ((hy - sy) * k) / steps)
    arm.rect(x, y, 2, 2, C.cubi)
  }
  arm.rect(hx - 1, hy - 1, 3, hand, C.cubi)
  const shape = arm.clone()
  arm.map((col, x, y) => (!shape.get(x, y - 1) ? C.cubiTop : !shape.get(x, y + 1) ? C.cubiSide : !shape.get(x - 1, y) ? C.cubiHi : col))
  c.draw(arm)
}

/**
 * Cubi beim Kümmern: steht mitten auf der Insel, die Augen wie `eyes` (CARE.eyes, siehe careGaze); `lift` hebt den
 * Körper (die Füße bleiben am Boden).
 */
function caringCubi(f, x, { eyes, feed, pet, lift = 0 }) {
  const c = layer()
  const top = GROUND - 13 - lift
  // Ärmchen zuerst: sie kommen hinter dem Körper hervor
  cubiArm(c, x + 1, top + 2, feed[0], feed[1])
  cubiArm(c, x + 13, top + 6, pet[0], pet[1], 2)
  c.draw(CUBI.body, x, top)
  for (const ex of [x + 3, x + 10]) c.draw(CARE.eyes[eyes], ex, top + 4)
  c.draw(CUBI.feet, x, GROUND - 1)
  c.draw(CUBI.sprout[Math.floor(f / 8) % 2], x + 6, top - 3)
  return c.outline(C.outline)
}

/** Pinguin beim Füttern (Haltung aus FEED): streckt sich zum Fisch oder hüpft (`lift`), die Füße bleiben am Boden. */
function fedPenguin(f, x) {
  const s = FEED[f % FEED.length]
  const lift = s === 'o' || s === 'h' ? 1 : s === 'B' ? 2 : 0
  const top = GROUND - 12 - lift
  const head = { u: 'up', o: 'open', B: 'up', c: 'chew' }[s] ?? 'happy'
  const c = layer()
  c.draw(CARE.penguinHead[head], x, top)
  c.draw(CARE.penguinBody, x, top + 6)
  c.draw(CARE.penguinFeet, x, GROUND - 1)
  // Zufrieden: die Flosse schlägt nach hinten aus
  if (s === 'h') {
    for (const [dx, dy] of [[2, 7], [2, 8], [3, 9]]) c.set(x + dx, top + dy, C.penguin)
    for (const [dx, dy] of [[0, 7], [-1, 6], [-1, 7]]) c.set(x + dx, top + dy, C.penguinHi)
  }
  return c.outline(C.outline)
}

/**
 * Krümel eines Bissens bei (x, y), `age` Bilder nach dem Biss: im Moment des Zuschnappens ein kleiner Blitz, dann
 * fliegen sie im Bogen auseinander und fallen.
 */
function crumbs(c, age, x, y) {
  if (age < 0 || age > 8) return
  if (age === 0) for (const [dx, dy] of [[-2, 0], [2, 0], [0, -2], [-1, -1], [1, -1]]) c.set(x + dx, y + dy, C.white)
  const flying = [[-1.3, -1.6, C.fishHi], [1.2, -1.9, C.paper], [-0.5, -2.3, C.fish], [0.7, -1.1, C.fishHi], [-1.1, -0.7, C.paper]]
  for (const [vx, vy, col] of flying) {
    const px = Math.round(x + vx * age)
    const py = Math.round(y + vy * age + 0.35 * age * age)
    if (py < GROUND - 1) c.set(px, py, col)
  }
}

/** Füchschen beim Kraulen: Ohren angelegt, Augen glücklich zu, rosige Bäckchen, es wedelt. */
function pettedFox(f, x) {
  const top = GROUND - 10
  const tail = layer().draw(PET.foxTail[[1, 2, 1, 0][Math.floor(f / 2) % 4]], x + 5, top - 1).outline(C.outline)
  const body = layer().draw(PET.fox, x, top)
  // Ohren angelegt: die Spitzen kippen zur Seite
  body.erase(x + 1, top)
  body.erase(x + 5, top)
  body.set(x, top + 1, C.sock)
  body.set(x + 6, top + 1, C.sock)
  // Augen glücklich zu: zwei kurze Striche, darunter Bäckchen
  for (const ex of [x + 1, x + 4]) body.rect(ex, top + 3, 2, 1, C.pupil)
  body.set(x + 1, top + 4, C.cheek)
  body.set(x + 5, top + 4, C.cheek)
  return tail.draw(body.outline(C.outline))
}

/** Kleine schwebende Insel (fest, ohne Zufall) mit Oberkante bei y: Gras, Erde, nach unten spitz mit Stein. */
function islet(x, y, w) {
  const c = layer()
  for (let k = 0; k < w; k++) {
    const edge = k === 0 || k === w - 1
    c.set(x + k, y, edge ? C.grass : C.grassHi)
    c.set(x + k, y + 1, k === w - 1 ? C.grassDark : C.grass)
    c.set(x + k, y + 2, k % 3 === 1 ? C.grassDark : C.dirt)
  }
  for (let r = 0; r < 3; r++) {
    for (let k = 1 + r * 2; k < w - 1 - r * 2; k++) c.set(x + k, y + 3 + r, r === 2 ? C.stoneDark : (k + r) % 4 === 0 ? C.dirtDark : C.dirtDeep)
  }
  c.set(x + 2, y - 1, C.grass)
  c.set(x + w - 4, y - 1, C.grassHi)
  return c.outline(C.outline)
}

/**
 * Herzchen `age` Bilder nach dem Aufploppen bei (x, y): steigt um `rise` (erst schnell, dann sanft) und treibt um
 * `drift` zur Seite, wiegt sich dabei, wächst (bis `big`) und schrumpft zum Schluss blass wieder zum Punkt.
 */
function risingHeart(c, age, x, y, { life = 16, rise = 14, drift = 0, big = 2 } = {}) {
  if (age < 0 || age >= life) return
  const t = age / life
  const size = Math.min(big, t < 0.07 ? 0 : t < 0.15 ? 1 : t < 0.25 ? 2 : t < 0.66 ? 3 : t < 0.8 ? 2 : t < 0.92 ? 1 : 0)
  const ease = 1 - (1 - t) ** 2
  const hx = Math.round(x + drift * ease + Math.sin(t * 2 * Math.PI) * 1.2)
  const hy = Math.round(y - rise * ease)
  const s = t >= 0.8 ? CARE.hearts[size].clone().map(() => C.heartHi) : CARE.hearts[size]
  c.draw(s, hx - (s.w >> 1), hy - (s.h >> 1))
}

// ---------------------------------------------------------------- Wegpunkte aus dem Chat (Sonderthema „waypoints“)
//
// Die klassische Minikarte wie in den beliebten Minikarten-Mods: quadratisch, dünner grauer Rahmen, oben das rote „N“
// auf einer dunklen Platte, roter Spielerpfeil in der Mitte, Wegpunkte als bunte Kästchen mit weißem Anfangsbuchstaben.
// Ein Freund auf seinem Inselchen teilt im Chat einen Wegpunkt: Die Sprechblase geht auf, er tippt, die Nachricht zeigt
// den Wegpunkt und den Knopf „+“; der Mauszeiger klickt, eine Kopie springt heraus, fliegt im Bogen in Cubis Minikarte
// und landet mit Funkeln und einem „+“. Eigene Schleife wie bei „petting“; alles hängt am Bild der Schleife, damit sie
// nahtlos bleibt.

/** Minikarte: linke obere Ecke und Kantenlänge mit Rahmen (2 Punkte breit) – innen 24 × 24 Punkte Gelände. */
const MINIMAP = { x: 32, y: 11, size: 28 }

/**
 * Ablauf des Teilens (Bild der Schleife): Sprechblase geht auf (`open`), Tipp-Punkte (`type`), der Wegpunkt erscheint in
 * der Blase (`show`), ein Klick – eine Kopie springt heraus (`pop`) und landet in der Karte (`land`); danach geht die
 * Blase zu (`close`). Der neue Wegpunkt schrumpft erst wieder weg (`fade`), während der Freund schon die nächste
 * Nachricht tippt. Im ruhigen Bild (dem ersten) ist die Kopie gerade oben im Bogen.
 */
const SHARE = { open: 17, type: 19, show: 22, pop: 27, land: 5, close: 2, fade: 18 }

/** Gelände der Minikarte von oben (24 × 24): „.“ Wiese (gesprenkelt), Bäume, ein See mit Sandufer. */
const MINIMAP_LAND = sprite(
  [
    '................Tt......',
    '...............Tttt.....',
    '................tt......',
    '........................',
    '........................',
    '........................',
    '........................',
    '........................',
    '........................',
    'Tt....................Tt',
    'ttt..................Ttt',
    '.t....................tt',
    '........................',
    '........................',
    'sss.....................',
    'wwss....................',
    'hwwss...................',
    'wwwws...................',
    'wWwwss..................',
    'wWWwws..................',
    'wWWwws.......Tt.........',
    'wwWwss......Tttt........',
    'wwwws........tt.........',
    'swwss...................',
  ],
  { T: C.grassDark, t: C.cubiBottom, w: C.water, W: C.waterDeep, h: C.waterHi, s: C.sand },
)

/** Weiße Anfangsbuchstaben der Wegpunkte (3 × 3). */
const WAYPOINT_LETTERS = { H: ['W.W', 'WWW', 'W.W'], T: ['WWW', '.W.', '.W.'], V: ['W.W', 'W.W', '.W.'] }

/** Wegpunkt-Kästchen (5 × 5) in `color` mit weißem Buchstaben, unten und rechts eine Spur dunkler. */
function waypointTile(color, letter) {
  const c = new Canvas(5, 5)
  for (let y = 0; y < 5; y++) for (let x = 0; x < 5; x++) c.set(x, y, x === 4 || y === 4 ? mix(color, C.outline, 0.25) : color)
  return c.draw(sprite(WAYPOINT_LETTERS[letter], { W: C.white }), 1, 1)
}

const WAYPOINT = {
  // Schon auf der Karte: Zuhause (blau) und ein Turm (orange); neu aus dem Chat: ein Dorf (violett)
  home: waypointTile(C.blue, 'H'),
  tower: waypointTile(C.orange, 'T'),
  shared: waypointTile(C.violet, 'V'),
  // Roter Spielerpfeil, zeigt nach Norden
  arrow: sprite(['..a..', '.aAr.', '.aAr.', 'aAAAr', 'aA.Ar'], { a: mix(C.red, C.white, 0.35), A: C.red, r: C.redDark }),
  // Norden: rotes „N“ auf einer kleinen dunklen Platte, mitten auf dem oberen Rand (wie im Spiel)
  north: sprite(['N..N', 'NN.N', 'N.NN', 'N..N'], { N: mix(C.red, C.white, 0.15) }),
  // Knopf „+ Wegpunkt“ in der Chat-Nachricht
  add: sprite(['.g.', 'ggg', '.g.'], { g: C.cubi }),
  // „+“ beim Landen: ein runder grüner Knopf mit weißem Plus (anders als die Funken) – erscheint als Punkt, wächst und
  // schrumpft beim Verblassen wieder
  plus: [
    sprite(['g'], { g: C.grassHi }),
    sprite(['.g.', 'gWg', '.g.'], { g: C.cubi, W: C.white }),
    sprite(['.hgg.', 'hgWgg', 'gWWWd', 'ggWgd', '.gdd.'], { h: C.grassHi, g: C.cubi, d: C.cubiSide, W: C.white }),
  ],
  // Der Freund: ein kleiner blauer Würfel mit Cubis Gesicht (schaut nach rechts zur Blase), oben das Pflänzchen
  friend: sprite(['.TTTTTTTt.', 'TTTTTTTTtt', 'HGWWGGWWSS', 'HGWKGGWKSS', 'HGPGGGGPSS', 'HGGGMMGGSS', '.BBBBBBBB.'], {
    T: C.friendTop, t: C.friendTopShade, H: C.friendHi, G: C.friend, S: C.friendSide, B: C.friendBottom, W: C.eye, K: C.pupil, P: C.cheek, M: C.mouth,
  }),
  friendBlink: sprite(['.TTTTTTTt.', 'TTTTTTTTtt', 'HGGGGGGGSS', 'HGKKGGKKSS', 'HGPGGGGPSS', 'HGGGMMGGSS', '.BBBBBBBB.'], {
    T: C.friendTop, t: C.friendTopShade, H: C.friendHi, G: C.friend, S: C.friendSide, B: C.friendBottom, K: C.pupil, P: C.cheek, M: C.mouth,
  }),
  friendFeet: sprite(['.FF....FF.'], { F: C.friendBottom }),
}

/** Kleines Zufallsmuster ohne Zufall: für (u, v) immer derselbe Wert 0 … 1 (Sprenkel der Wiese). */
function grain(u, v) {
  let h = (Math.imul(u + 17, 374761393) + Math.imul(v + 5, 668265263)) >>> 0
  h = Math.imul(h ^ (h >>> 13), 1274126177) >>> 0
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296
}

/**
 * Die klassische Minikarte mit linker oberer Ecke (x, y): grauer Rahmen mit Licht oben links, dunkle Innenkante,
 * Gelände, oben mittig das rote „N“ auf seiner dunklen Platte, in der Mitte der rote Pfeil, dazu die festen Wegpunkte.
 * `f` lässt das Wasser glitzern.
 */
function classicMinimap(f, x, y) {
  const { size } = MINIMAP
  const c = layer()
  c.rect(x, y, size, size, C.metal)
  c.bevel(0.45, 0.28)
  c.rect(x + 1, y + 1, size - 2, size - 2, C.slateDark)
  const inner = size - 4
  for (let v = 0; v < inner; v++) {
    for (let u = 0; u < inner; u++) {
      let land = MINIMAP_LAND.get(u, v)
      // Auf dem See wandern schräge Lichtstreifen (alle 4 Bilder einen Punkt, nach 32 wieder am Anfang).
      if (land?.[0] === C.water[0] && (u + v + Math.floor(f / 4)) % 8 === 0) land = mix(C.water, C.waterHi, 0.6)
      const g = grain(u, v)
      c.set(x + 2 + u, y + 2 + v, land ?? (g < 0.16 ? mix(C.grass, C.grassHi, 0.55) : g > 0.86 ? mix(C.grass, C.grassDark, 0.6) : C.grass))
    }
  }
  // Platte 6 × 6, halb über dem Rand
  const plate = x + size / 2 - 3
  c.rect(plate, y - 3, 6, 6, C.slateDark)
  c.draw(WAYPOINT.north, plate + 1, y - 2)
  c.outline(C.outline)
  // Pfeil und Wegpunkte mit eigenem dunklem Umriss, damit sie sich vom Gelände abheben.
  const marks = layer()
  marks.draw(WAYPOINT.arrow, x + 2 + 10, y + 2 + 9)
  marks.draw(WAYPOINT.tower, x + 2 + 17, y + 2 + 4)
  marks.draw(WAYPOINT.home, x + 2 + 16, y + 2 + 16)
  return c.draw(marks.outline(C.outline))
}

/** Kachel `tile` auf (x, y) in der Größe `size` (5, 3 oder 1 Punkt – zum Erscheinen und Verschwinden), mit Umriss. */
function poppedTile(tile, x, y, size, flash = false) {
  const c = layer()
  if (size >= 5) c.draw(flash ? tile.clone().map(() => C.white) : tile, x, y)
  else if (size === 3) c.rect(x + 1, y + 1, 3, 3, flash ? C.white : tile.get(0, 0))
  else if (size === 1) c.set(x + 2, y + 2, C.white)
  return c.outline(C.outline)
}

/**
 * Sprechblase des Freunds (linke obere Ecke x, y; 13 × 9), Zipfel links unten zu ihm hin. `size`: 0 zu, 1 halb offen,
 * 2 offen; darin Tipp-Punkte (`dots`) oder – neben dem Wegpunkt – der grüne Knopf „+ Wegpunkt“ (`button`, beim Klick
 * leuchtet er hell auf: `pressed`).
 */
function chatBubble(x, y, { size, dots = 0, button = false, pressed = false }) {
  const c = layer()
  if (!size) return c
  const w = size === 2 ? 13 : 7
  const h = size === 2 ? 9 : 5
  const bx = x + (13 - w) / 2
  const by = y + (9 - h) / 2
  c.rect(bx + 1, by, w - 2, h, C.paper)
  c.rect(bx, by + 1, w, h - 2, C.paper)
  c.rect(bx + 1, by + h - 1, w - 2, 1, C.paperShade)
  c.rect(bx + w - 1, by + 1, 1, h - 2, C.paperShade)
  // Zipfel zum Freund (links unten)
  c.set(bx + 1, by + h, C.paper)
  c.set(bx + 2, by + h, C.paperShade)
  c.set(bx, by + h + 1, C.paper)
  if (size === 2) {
    for (let i = 0; i < dots; i++) c.rect(x + 3 + i * 3, y + 4, 2, 2, i === 1 ? C.cubi : C.slate)
    if (button) c.draw(pressed ? WAYPOINT.add.clone().map(() => C.cubiTop) : WAYPOINT.add, x + 8, y + 3)
  }
  return c.outline(C.outline)
}

/**
 * Funkeln einer Landung um das Kästchen mit Mitte (cx, cy), `age` Bilder danach: im ersten Bild kurze Strahlen nach
 * oben, unten und zur Seite, dann fliegen vier Funken schräg nach außen und verglimmen.
 */
function landingBurst(c, age, cx, cy) {
  if (age === 0) {
    for (const [dx, dy] of [[0, -1], [0, 1], [-1, 0], [1, 0]]) {
      c.set(cx + dx * 5, cy + dy * 5, C.white)
      c.set(cx + dx * 6, cy + dy * 6, C.sparkleMid)
    }
  }
  const size = [0, 2, 2, 1, 1][age] ?? 0
  if (!size) return
  const s = SPARKLES[size - 1]
  for (const [dx, dy] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) c.draw(s, cx + dx * (3 + age) - (s.w >> 1), cy + dy * (3 + age) - (s.h >> 1))
}

/** Mauszeiger (zeigt nach links oben): klickt den Wegpunkt in der Blase an. */
const POINTER = sprite(['W...', 'WW..', 'WWW.', 'WWWW', 'WW..', '..W.'], { W: C.white })

/** Cubi schaut hoch: zur Sprechblase (`look` < 0) oder zur Karte, freut sich mit geschlossenen Augen (`happy`). */
function watchingCubi(f, n, x, { look, happy, lift }) {
  const c = layer()
  const top = GROUND - 13 - lift
  const blink = happy || f === 11
  c.draw(blink ? CUBI.blink : CUBI.body, x, top)
  if (!blink) {
    for (const ex of [x + 3, x + 11]) {
      c.rect(ex, top + 4, 2, 3, C.eye)
      c.rect(look < 0 ? ex : ex + 1, top + 4, 1, 2, C.pupil)
    }
  }
  if (happy) {
    // Glückliche Augen: kleine Bögen statt Strichen
    for (const ex of [x + 3, x + 11]) {
      c.rect(ex, top + 4, 2, 3, C.cubi)
      c.rect(ex, top + 5, 2, 1, C.pupil)
      c.set(ex - 1, top + 6, C.pupil)
      c.set(ex + 2, top + 6, C.pupil)
    }
  }
  c.draw(CUBI.feet, x, GROUND - 1)
  c.draw(CUBI.sprout[Math.floor(f / 8) % 2], x + 6, top - 3)
  return c.outline(C.outline)
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

  /**
   * Streicheln und Füttern: Cubi mitten auf der Insel (`hero: 'middle'`) krault rechts das Füchschen und hält links dem
   * Pinguin einen Fisch hin; Herzchen steigen auf, Krümel fliegen. Darüber schwebt das Axolotl auf seiner eigenen
   * kleinen Insel, links fliegt das Glühwürmchen mit seiner Laterne.
   */
  petting(f, { left, hero, frames: n }) {
    const s = FEED[f % FEED.length]
    const px = hero - 15
    const fx = hero + 18
    // Fisch über dem Pinguin, Cubi hält ihn an der Schwanzflosse; beim Zuschnappen ruckt er nach unten.
    const fishX = px + 4
    const fishY = GROUND - 18 + (s === 'B' ? 1 : 0)
    // Kraulen: die Hand fährt zweimal je Schleife über den Kopf des Füchschens (sanft hin und her), Cubi geht mit.
    const stroke = Math.round(1.5 - 1.5 * Math.cos((f / n) * 4 * Math.PI))
    const c = layer()
    c.draw(fedPenguin(f, px))
    // Das Füchschen geht der Hand ein Stück nach.
    c.draw(pettedFox(f, fx + (stroke >= 2 ? 1 : 0)))
    c.draw(layer().draw(CARE.fish, fishX, fishY).outline(C.outline))
    // Cubi schaut dem Pinguin beim Fressen zu (hoch zum Fisch), nach dem dritten Bissen zum Füchschen – nur zwei
    // Blickwechsel je Schleife, jeder mit einem Lidschlag.
    const caring = caringCubi(f, hero, {
      eyes: careGaze(f, n),
      feed: [fishX + 9, fishY + 2],
      pet: [fx + 1 + stroke, GROUND - 10],
      lift: stroke === 3 ? 1 : 0,
    })
    // Herzchen: alle 8 Bilder eins, steigt hoch über die Insel und treibt mal zur Mitte, mal nach außen (im ruhigen
    // Bild ist gerade ein großes ganz zu sehen).
    const hearts = layer()
    for (let k = 0; k < 4; k++) {
      const drift = [-1, 7, -2, 5][k]
      risingHeart(hearts, (f - k * 8 - 4 + 2 * n) % n, fx + 3, GROUND - 13, { life: 24, rise: 24, drift, big: k % 2 ? 2 : 3 })
    }
    const front = hearts.outline(C.outline)
    // Krümel: Der Blitz beim Zuschnappen leuchtet vorn, die fliegenden Krümel ziehen hinter den Figuren vorbei – keiner
    // landet als heller Fleck auf dem dunklen Pinguin oder auf Cubi.
    const behind = layer()
    for (const b of BITES) {
      const age = (f - b + n) % n
      crumbs(age ? behind : front, age, fishX + 1, fishY + 3)
    }
    // Am Himmel: das Axolotl auf seiner Insel (schwebt versetzt zur großen) bekommt auch ein Herz ab, wenn Cubi zum
    // Füchschen schaut; links fliegt das Glühwürmchen seine Acht.
    const iy = 19 + Math.round(swing(f, n, 0.3))
    const sky = layer().draw(islet(hero, iy, 15)).draw(axolotlPet(f, n, hero + 2), 0, iy - GROUND)
    const love = layer()
    risingHeart(love, (f - LOOK_FOX[0] + n) % n, hero + 7, iy - 9, { life: 14, rise: 4 })
    sky.draw(love.outline(C.outline)).draw(fireflyFlight(f, n, left + 5, 6))
    return { layer: c, hero: caring, front, sky, ground: behind }
  },

  /**
   * Wegpunkte aus dem Chat: Über der Insel schwebt die klassische Minikarte, links oben steht der Freund auf seinem
   * Inselchen und teilt im Chat einen Wegpunkt – ein Klick auf „+“, und eine Kopie springt aus der Sprechblase, fliegt im
   * Bogen in die Karte und landet mit Funkeln und „+“. Cubi schaut ihr nach und hüpft vor Freude, wenn sie ankommt.
   */
  waypoints(f, { left, right, hero, frames: n }) {
    const age = (f - SHARE.pop + n) % n
    const since = (f - SHARE.land + n) % n
    // Karte und Inselchen schweben jedes für sich (nicht mit der großen Insel); die Landung drückt die Karte kurz nach unten.
    const my = MINIMAP.y + Math.round(swing(f, n, 0.6)) + (since < 2 ? 1 : 0)
    const iy = 21 + Math.round(swing(f, n, 0.15))
    const sky = layer().draw(classicMinimap(f, MINIMAP.x, my))

    // Der Freund auf seinem Inselchen: beim Schreiben wippt er, bei der Landung hüpft er mit.
    const ix = 2
    sky.draw(islet(ix, iy, 12))
    const writing = f >= SHARE.open && f < SHARE.pop
    const bob = (writing && f % 4 < 2) || since < 2 ? 1 : 0
    const friend = layer()
    friend.draw(f === 9 ? WAYPOINT.friendBlink : WAYPOINT.friend, ix + 1, iy - 8 - bob)
    friend.draw(WAYPOINT.friendFeet, ix + 1, iy - 1)
    friend.draw(CUBI.sprout[Math.floor((f + 4) / 8) % 2], ix + 3, iy - 11 - bob)
    sky.draw(friend.outline(C.outline))

    // Sprechblase rechts neben ihm: geht auf, Tipp-Punkte, dann die Nachricht – der Wegpunkt und daneben der Knopf
    // „+ Wegpunkt“. Der Wegpunkt bleibt in der Nachricht stehen (in die eigene Karte fliegt eine Kopie), bis die Blase
    // wieder zugeht.
    const bx = 15
    const by = iy - 10
    const open = f >= SHARE.open || f <= SHARE.close
    const size = !open ? 0 : f === SHARE.open || f === SHARE.close ? 1 : 2
    const dots = f >= SHARE.type && f < SHARE.show ? 1 + ((f - SHARE.type) % 3) : 0
    const message = f >= SHARE.show || f < SHARE.close
    sky.draw(chatBubble(bx, by, { size, dots, button: message, pressed: age === 0 }))
    if (message) sky.draw(poppedTile(WAYPOINT.shared, bx + 2, by + 2, f === SHARE.show ? 3 : 5, age === 0))

    // Flug: vom Absprung über der Blase in einem hohen Bogen (Scheitel über der Lücke zwischen Blase und Karte) zum Platz
    // oben links in der Karte. Dahinter eine Spur aus Punkten, genau dort, wo das Kästchen in den Bildern davor war – so
    // zeigt auch das ruhige Bild den Bogen; auf Blase und Karte bleibt sie weg.
    const land = [MINIMAP.x + 2 + 2, my + 2 + 4]
    const from = [bx + 2, by - 3]
    const ctrl = [land[0] - 8, from[1] - 13]
    const at = (t) => [0, 1].map((i) => Math.round((1 - t) ** 2 * from[i] + 2 * t * (1 - t) * ctrl[i] + t * t * land[i]))
    const fly = layer()
    if (age >= 1 && age < 10) {
      ;[C.sparkle, C.sparkleMid, C.sparkleEdge, C.goldDark].forEach((col, k) => {
        const t = (age - 2.5 - k * 0.5) / 9
        if (t < 0) return
        const [tx, ty] = at(t)
        if (!sky.get(tx + 2, ty + 2)) fly.set(tx + 2, ty + 2, col)
      })
      const [x, y] = at((age - 1) / 9)
      fly.draw(poppedTile(WAYPOINT.shared, x, y, 5))
    }
    // Gelandet: Blitz, Funkeln und ein „+“, das über den Rahmen aufsteigt; vor der nächsten Runde schrumpft der Wegpunkt
    // wieder weg.
    const stay = SHARE.fade - SHARE.land - since
    if (stay >= -1) sky.draw(poppedTile(WAYPOINT.shared, land[0], land[1], stay > 0 ? 5 : stay === 0 ? 3 : 1, since === 0))
    landingBurst(fly, since, land[0] + 2, land[1] + 2)
    // „+“ je Bild nach der Landung: [Größe, Höhe über der Mitte des Kästchens]; es steigt schräg nach links oben – weg
    // vom „N“.
    const rise = [null, [1, 3], [3, 7], [5, 10], [5, 12], [5, 13], [5, 14], [3, 15], [1, 16]][since]
    if (rise) {
      const plus = WAYPOINT.plus[rise[0] >> 1]
      const px = land[0] + 2 - Math.min(2, since >> 1)
      fly.draw(layer().draw(plus, px - (plus.w >> 1), land[1] + 2 - rise[1] - (plus.h >> 1)).outline(C.outline))
    }
    sky.draw(fly)

    // Mauszeiger: gleitet von unten heran und klickt „+ Wegpunkt“ – ein Klick, und der Wegpunkt fliegt in die eigene Karte.
    // Spitze je Bild, beginnend 5 Bilder vor dem Absprung; der Klick (eingedrückt) fällt genau auf den Absprung.
    const point = [[bx + 11, by + 14], [bx + 11, by + 10], [bx + 10, by + 7], [bx + 10, by + 6], [bx + 10, by + 6], [bx + 9, by + 5], [bx + 11, by + 8]][
      (age + 5) % n
    ]
    if (point) sky.draw(layer().draw(POINTER, point[0], point[1]).outline(C.outline))
    // Funkeln an festen Stellen: Der Himmel ist so voll, dass die gewohnten Funken keinen freien Platz finden (oben in den
    // Ecken, links unter dem Inselchen und rechts unter der großen Insel – dort ist bei jeder Inselbreite frei).
    for (const [x, y, phase] of [[6, 4, 12], [58, 5, 3], [4, 34, 5], [59, 54, 9]]) sparkle(sky, f, { x, y, phase })

    // Cubi schaut zum Freund, sobald die Blase aufgeht, dann dem Wegpunkt nach zur Karte; bei der Landung hüpft er. Auf
    // schmaleren Inseln rückt er nicht näher an die Karte.
    const happy = since < 3
    const lift = since < 2 ? 2 : since === 2 ? 1 : 0
    const watching = watchingCubi(f, n, Math.min(hero, MINIMAP.x - 21), { look: writing ? -1 : 1, happy, lift })
    // Schatten der Karte auf der Wiese (nur, wo Insel ist)
    const ground = layer()
    for (const [y, inset, col] of [[GROUND, 3, mix(C.grassHi, C.grassDark, 0.7)], [GROUND + 1, 6, mix(C.grass, C.grassDark, 0.6)]]) {
      for (let x = Math.max(left + 1, MINIMAP.x + inset); x < Math.min(right, MINIMAP.x + MINIMAP.size - inset); x++) ground.set(x, y, col)
    }
    return { layer: layer(), hero: watching, ground, sky }
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
