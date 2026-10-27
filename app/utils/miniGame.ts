// „Cubi Hop“ – das kleine Spiel, während Minecraft startet (components/LaunchMiniGame.vue). Hier nur die Regeln,
// ohne DOM: Cubi steht links auf einem Grasstreifen, Blöcke rollen von rechts heran, ein Druck auf Leertaste/Pfeil
// hoch/Klick lässt ihn hüpfen, ein zweiter in der Luft hüpft noch einmal (Doppelsprung). Jeder überwundene Block und
// jedes eingesammelte Funkeln zählt einen Punkt; mit den Punkten wird es schneller. Die Welt ist 192 × 64 Bildpunkte
// groß (Pixel-Art, das Canvas skaliert pixelig). Der Zustand wird an Ort und Stelle fortgeschrieben – im Takt
// entsteht nichts Neues außer gelegentlich einem Hindernis.

/** Größe der Spielwelt in Bildpunkten. */
export const HOP_W = 192
export const HOP_H = 64
/** Oberkante des Grases – darauf stehen Cubi und die Blöcke. */
export const HOP_GROUND = 52
/** Cubi: linke Kante, Breite und Höhe (Körper + Füße; das Pflänzchen zählt nicht zur Trefferfläche). */
export const CUBI_X = 18
export const CUBI_W = 16
export const CUBI_H = 13

/** Schwerkraft und Absprung (Bildpunkte/s² bzw. /s): gut 36 Bildpunkte hoch, 0,7 s in der Luft. */
const GRAVITY = 600
const JUMP = 210
/** Doppelsprung: etwas schwächer. */
const DOUBLE_JUMP = 175
/** Tempo: Start, Zuwachs je Punkt, Höchstwert (Bildpunkte/s). */
const SPEED_START = 70
const SPEED_STEP = 2.5
const SPEED_MAX = 150
/** Nach einem Treffer so lange keine neue Runde (kein versehentlicher Neustart). */
export const HOP_RESTART_MS = 450
/** Trefferfläche etwas kleiner als das Bild – knappe Sprünge sollen klappen. */
const INSET = 2

export type HopPhase = 'ready' | 'running' | 'paused' | 'over'
export type ObstacleKind = 'grass' | 'stone' | 'tall' | 'wide'

export interface HopObstacle {
  kind: ObstacleKind
  x: number
  w: number
  h: number
  /** Schon gezählt (Cubi ist vorbei)? */
  passed: boolean
}

export interface HopSparkle {
  x: number
  y: number
  taken: boolean
}

export interface HopState {
  phase: HopPhase
  /** Laufzeit der Runde in Sekunden (für Animationen). */
  time: number
  speed: number
  score: number
  best: number
  /** Diese Runde hat den Rekord geschlagen. */
  newBest: boolean
  /** Cubi: Höhe über dem Boden (≥ 0) und Steiggeschwindigkeit (nach oben positiv). */
  lift: number
  rise: number
  /** In der Luft schon doppelt gesprungen? */
  doubled: boolean
  obstacles: HopObstacle[]
  sparkles: HopSparkle[]
  /** Abstand bis zum nächsten Hindernis (Bildpunkte, die die Welt noch weiterrollt). */
  nextIn: number
  /** So weit ist die Welt in dieser Runde gerollt (Bildpunkte – für Boden und Hintergrund). */
  scroll: number
  /** Zufallszustand (mulberry32) – gleiche Runde bei gleichem Startwert. */
  seed: number
  /** Zeitpunkt des Treffers (Rundenzeit), sonst `null`. */
  overAt: number | null
  /** Seit dem Treffer vergangene Zeit (Sekunden) – Sperre gegen sofortigen Neustart. */
  sinceOver: number
  /** Vor der Pause: lief die Runde? */
  pausedFrom: HopPhase | null
}

export type HopEvent = 'start' | 'jump' | 'double' | 'score' | 'sparkle' | 'hit'

function random(state: HopState): number {
  state.seed = (state.seed + 0x6d2b79f5) >>> 0
  let t = state.seed
  t = Math.imul(t ^ (t >>> 15), t | 1)
  t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}

/** Neues Spiel im Zustand „bereit“ (Cubi steht, nichts rollt). */
export function createHop(seed: number, best = 0): HopState {
  return {
    phase: 'ready',
    time: 0,
    speed: SPEED_START,
    score: 0,
    best: Math.max(0, Math.floor(best) || 0),
    newBest: false,
    lift: 0,
    rise: 0,
    doubled: false,
    obstacles: [],
    sparkles: [],
    nextIn: HOP_W * 0.55,
    scroll: 0,
    seed: seed >>> 0,
    overAt: null,
    sinceOver: 0,
    pausedFrom: null,
  }
}

function reset(state: HopState) {
  state.phase = 'running'
  state.time = 0
  state.speed = SPEED_START
  state.score = 0
  state.newBest = false
  state.lift = 0
  state.rise = 0
  state.doubled = false
  state.obstacles.length = 0
  state.sparkles.length = 0
  state.nextIn = HOP_W * 0.55
  state.scroll = 0
  state.overAt = null
  state.sinceOver = 0
  state.pausedFrom = null
}

/**
 * Taste/Klick: startet die Runde, springt (am Boden) oder springt noch einmal (in der Luft, einmal je Sprung).
 * Nach einem Treffer startet sie eine neue Runde – frühestens nach {@link HOP_RESTART_MS}. Eine Pause beendet sie.
 */
export function hopInput(state: HopState): HopEvent | null {
  if (state.phase === 'paused') {
    resumeHop(state)
    return null
  }
  if (state.phase === 'ready' || (state.phase === 'over' && state.sinceOver * 1000 >= HOP_RESTART_MS)) {
    reset(state)
    state.rise = JUMP
    return 'start'
  }
  if (state.phase !== 'running') return null
  if (state.lift <= 0) {
    state.rise = JUMP
    state.doubled = false
    return 'jump'
  }
  if (!state.doubled) {
    state.rise = DOUBLE_JUMP
    state.doubled = true
    return 'double'
  }
  return null
}

/** Pause (Fenster im Hintergrund, Spiel aus dem Blick). */
export function pauseHop(state: HopState) {
  if (state.phase !== 'running') return
  state.pausedFrom = state.phase
  state.phase = 'paused'
}

export function resumeHop(state: HopState) {
  if (state.phase !== 'paused') return
  state.phase = state.pausedFrom ?? 'running'
  state.pausedFrom = null
}

/** Tempo für einen Punktestand. */
export function hopSpeed(score: number): number {
  return Math.min(SPEED_MAX, SPEED_START + score * SPEED_STEP)
}

function spawn(state: HopState) {
  const r = random(state)
  // Schwerere Hindernisse erst nach ein paar Punkten.
  let kind: ObstacleKind = r < 0.55 ? 'grass' : 'stone'
  if (state.score >= 6 && r > 0.8) kind = 'tall'
  else if (state.score >= 10 && r < 0.12) kind = 'wide'
  // Hoch = zwei Blöcke übereinander, breit = zwei nebeneinander.
  const size = { grass: [10, 10], stone: [10, 10], tall: [10, 20], wide: [20, 10] }[kind]
  state.obstacles.push({ kind, x: HOP_W + 2, w: size[0]!, h: size[1]!, passed: false })
  // Manchmal ein Funkeln über dem Block – in Sprunghöhe.
  if (random(state) < 0.35) state.sparkles.push({ x: HOP_W + 2 + size[0]! / 2 - 2, y: HOP_GROUND - size[1]! - 14 - Math.floor(random(state) * 8), taken: false })
  // Abstand: mindestens ~0,9 s Reaktionszeit, höchstens ~2 s.
  state.nextIn = state.speed * (0.9 + random(state) * 1.1) + size[0]!
}

function overlaps(ax: number, ay: number, aw: number, ah: number, bx: number, by: number, bw: number, bh: number) {
  return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
}

/** Trefferfläche von Cubi (oben links, Breite, Höhe). */
export function cubiBox(state: HopState): [number, number, number, number] {
  const top = HOP_GROUND - CUBI_H - state.lift
  return [CUBI_X + INSET, top + INSET, CUBI_W - INSET * 2, CUBI_H - INSET]
}

/**
 * Ein Schritt um `dt` Sekunden (wird in kleine Schritte geteilt, damit nichts durchrutscht). Liefert, was passiert
 * ist (Punkt, Funkeln, Treffer).
 */
export function stepHop(state: HopState, dt: number): HopEvent[] {
  const events: HopEvent[] = []
  if (state.phase === 'over') {
    state.sinceOver += Math.max(0, dt)
    return events
  }
  if (state.phase !== 'running' || !(dt > 0)) return events
  // Nach langem Hänger (Tab im Hintergrund) nicht alles nachholen.
  let left = Math.min(dt, 0.1)
  while (left > 0 && state.phase === 'running') {
    const step = Math.min(left, 1 / 120)
    left -= step
    advance(state, step, events)
  }
  return events
}

function advance(state: HopState, dt: number, events: HopEvent[]) {
  state.time += dt
  // Cubi
  if (state.lift > 0 || state.rise > 0) {
    state.lift += state.rise * dt
    state.rise -= GRAVITY * dt
    if (state.lift <= 0) {
      state.lift = 0
      state.rise = 0
      state.doubled = false
    }
  }
  // Welt rollt nach links
  const move = state.speed * dt
  state.scroll += move
  state.nextIn -= move
  if (state.nextIn <= 0) spawn(state)
  for (const o of state.obstacles) o.x -= move
  for (const s of state.sparkles) s.x -= move
  while (state.obstacles.length && state.obstacles[0]!.x + state.obstacles[0]!.w < -4) state.obstacles.shift()
  while (state.sparkles.length && state.sparkles[0]!.x < -8) state.sparkles.shift()

  const [cx, cy, cw, ch] = cubiBox(state)
  for (const s of state.sparkles) {
    if (s.taken || !overlaps(cx, cy, cw, ch, s.x, s.y, 5, 5)) continue
    s.taken = true
    addPoint(state, events, 'sparkle')
  }
  for (const o of state.obstacles) {
    if (overlaps(cx, cy, cw, ch, o.x, HOP_GROUND - o.h, o.w, o.h)) {
      state.phase = 'over'
      state.overAt = state.time
      state.sinceOver = 0
      events.push('hit')
      return
    }
    if (!o.passed && o.x + o.w < cx) {
      o.passed = true
      addPoint(state, events, 'score')
    }
  }
}

/**
 * Vorführung auf dem Startbildschirm des Spiels (und Probe, dass jede Runde zu schaffen ist): springt kurz bevor
 * der nächste Block Cubi erreicht. `true` = jetzt springen.
 */
export function hopAutopilot(state: HopState): boolean {
  if (state.phase !== 'running' || state.lift > 0) return false
  const [cx, , cw] = cubiBox(state)
  const next = state.obstacles.find((o) => !o.passed && o.x + o.w >= cx)
  if (!next) return false
  const gap = next.x - (cx + cw)
  return gap <= state.speed * 0.14 && gap > -next.w
}

function addPoint(state: HopState, events: HopEvent[], event: HopEvent) {
  state.score++
  state.speed = hopSpeed(state.score)
  if (state.score > state.best) {
    state.best = state.score
    state.newBest = true
  }
  events.push(event)
}

// --- Rekord je Spieler --------------------------------------------------------------------------------------------

/** Rekorde je Konto (Konto-ID → Punkte) im lokalen Speicher. */
export const HOP_BEST_KEY = 'cubiq.miniGame.best'
/** So viele Konten merkt sich der Speicher höchstens. */
const BEST_MAX_USERS = 20

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>

function readAll(storage: StorageLike | null): Record<string, number> {
  try {
    const raw = storage?.getItem(HOP_BEST_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : null
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    const out: Record<string, number> = {}
    for (const [user, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (typeof value === 'number' && Number.isFinite(value) && value > 0 && user.length <= 64) out[user] = Math.floor(value)
    }
    return out
  } catch {
    return {}
  }
}

/** Rekord dieses Spielers (`user` = Konto-ID, ohne Konto „local“); ohne Speicher 0. */
export function readHopBest(storage: StorageLike | null, user: string): number {
  return readAll(storage)[user] ?? 0
}

/** Neuen Rekord merken (nur, wenn er höher ist). Liefert, ob gespeichert wurde. */
export function writeHopBest(storage: StorageLike | null, user: string, score: number): boolean {
  if (!storage || !(score > 0) || user.length > 64) return false
  const all = readAll(storage)
  if ((all[user] ?? 0) >= score) return false
  delete all[user]
  all[user] = Math.floor(score)
  // Älteste Einträge zuerst weg (Reihenfolge der Schlüssel = zuletzt geschrieben hinten).
  const users = Object.keys(all)
  for (const old of users.slice(0, Math.max(0, users.length - BEST_MAX_USERS))) delete all[old]
  try {
    storage.setItem(HOP_BEST_KEY, JSON.stringify(all))
    return true
  } catch {
    return false
  }
}

// --- Spielfenster da? ---------------------------------------------------------------------------------------------

/**
 * Zeilen im Spiel-Log, die erscheinen, sobald das Minecraft-Fenster offen ist: LWJGL 3 („Backend library: LWJGL
 * version …“, ab 1.13), LWJGL 2 („LWJGL Version: …“, bis 1.12), das frühe Ladefenster von Forge/NeoForge und – als
 * spätestes Zeichen – der Start der Tonausgabe.
 */
const WINDOW_MARKERS = [/Backend library: LWJGL/i, /\bLWJGL Version: /i, /ImmediateWindowProvider|fmlearlywindow|EARLYDISPLAY/i, /Sound engine started/i]

/** Zeigt eine dieser Log-Zeilen, dass das Spielfenster da ist? */
export function gameWindowShown(lines: Iterable<{ message: string }>): boolean {
  for (const line of lines) {
    const text = line.message
    if (text && text.length < 4000 && WINDOW_MARKERS.some((re) => re.test(text))) return true
  }
  return false
}
