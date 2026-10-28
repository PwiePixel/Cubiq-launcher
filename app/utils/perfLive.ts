import { z } from 'zod'

// Live-Leistungsanzeige eines laufenden Spiels (components/PerfLivePanel.vue): Der Kern liefert etwa einmal je
// Sekunde CPU-Zeit und Arbeitsspeicher des Spielprozesses, mit Cubiq Client auch Bildrate und Java-Speicher
// (Befehl `game_perf`, crates/core/src/game_perf.rs). Hier: Auslastung aus zwei Messungen, die Kurven der letzten
// Minute und Tipps, wenn es hakt – nur, was sich ein paar Sekunden hält, damit nichts flackert.

export const gamePerfSchema = z.object({
  atMs: z.number(),
  cpuMs: z.number().nullable(),
  ramBytes: z.number().nullable(),
  cores: z.number(),
  systemTotalMb: z.number().nullable(),
  systemFreeMb: z.number().nullable(),
  client: z.boolean(),
  fps: z.number().nullable(),
  fpsLimited: z.boolean(),
  heapUsedMb: z.number().nullable(),
  heapMaxMb: z.number().nullable(),
})
export type GamePerf = z.infer<typeof gamePerfSchema>

/** Ein Punkt der Kurven (etwa je Sekunde). */
export interface PerfPoint {
  /** Zeitpunkt (ms, monoton – vom Kern). */
  at: number
  /** CPU-Auslastung in % des ganzen PCs (0–100). */
  cpu: number | null
  /** Arbeitsspeicher des Spiels (MB). */
  ramMb: number | null
  fps: number | null
  /** Bildrate absichtlich gebremst (Fenster im Hintergrund) – zählt nicht als „niedrig“. */
  fpsLimited: boolean
  heapUsedMb: number | null
  heapMaxMb: number | null
  systemFreeMb: number | null
  systemTotalMb: number | null
}

/** So viele Punkte zeigen die Kurven (1 Minute). */
export const PERF_HISTORY = 60

/** CPU-Auslastung zwischen zwei Messungen in % des ganzen PCs; `null` ohne zwei brauchbare Messungen. */
export function perfCpuPercent(prev: GamePerf | null, next: GamePerf): number | null {
  if (!prev || prev.cpuMs === null || next.cpuMs === null) return null
  const wall = next.atMs - prev.atMs
  const used = next.cpuMs - prev.cpuMs
  // Zu kurzer Abstand (ungenau) oder CPU-Zeit rückwärts (anderer Prozess).
  if (wall < 200 || used < 0) return null
  return Math.min(100, Math.max(0, (used / wall / Math.max(1, next.cores)) * 100))
}

/** Messung → Punkt (mit der vorigen Messung für die Auslastung). */
export function perfPoint(prev: GamePerf | null, next: GamePerf): PerfPoint {
  return {
    at: next.atMs,
    cpu: perfCpuPercent(prev, next),
    ramMb: next.ramBytes === null ? null : next.ramBytes / (1024 * 1024),
    fps: next.fps,
    fpsLimited: next.fpsLimited,
    heapUsedMb: next.heapUsedMb,
    heapMaxMb: next.heapMaxMb,
    systemFreeMb: next.systemFreeMb,
    systemTotalMb: next.systemTotalMb,
  }
}

/** Punkt anhängen; ältere fallen vorne weg (die Liste wird in place gekürzt). */
export function pushPerfPoint(history: PerfPoint[], point: PerfPoint, max = PERF_HISTORY): PerfPoint[] {
  history.push(point)
  if (history.length > max) history.splice(0, history.length - max)
  return history
}

function median(values: number[]): number | null {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  const mid = sorted.length >> 1
  return sorted.length % 2 ? sorted[mid]! : (sorted[mid - 1]! + sorted[mid]!) / 2
}

/** Kennzahlen einer Kurve (ohne Lücken): aktueller Wert, Durchschnitt, Minimum, Maximum. */
export interface PerfStats {
  current: number | null
  avg: number | null
  min: number | null
  max: number | null
}

export function perfStats(values: readonly (number | null)[]): PerfStats {
  const known = values.filter((v): v is number => v !== null && Number.isFinite(v))
  const last = values.length ? values[values.length - 1] : null
  if (!known.length) return { current: last ?? null, avg: null, min: null, max: null }
  return {
    current: last ?? null,
    avg: known.reduce((a, b) => a + b, 0) / known.length,
    min: Math.min(...known),
    max: Math.max(...known),
  }
}

export type PerfTip = 'memory' | 'systemMemory' | 'stutter' | 'lowFps' | 'cpu'

/** Ab diesen Werten wird es eng bzw. zäh. */
const HEAP_FULL = 0.9
const SYSTEM_FREE_SHARE = 0.08
const SYSTEM_FREE_MB = 700
const LOW_FPS = 30
const STUTTER_DROP = 0.5
const CPU_BUSY = 90

/** Wie viele der letzten `window` Punkte erfüllen `test`? */
function count(points: readonly PerfPoint[], window: number, test: (p: PerfPoint) => boolean | null): number {
  return points.slice(-window).filter((p) => test(p) === true).length
}

/**
 * Tipps aus den letzten Sekunden – wichtigster zuerst, höchstens zwei:
 * - `memory`: Java-Speicher (nur mit Cubiq Client) in 8 der letzten 10 Messungen ≥ 90 % → mehr Speicher geben.
 * - `systemMemory`: Der PC hat in 8 der letzten 10 Messungen kaum noch frei (< 8 % und < 700 MB) → andere Programme
 *   schließen (mehr Speicher fürs Spiel würde es dann schlimmer machen).
 * - `stutter`: In den letzten 15 Messungen mindestens 3 Einbrüche unter die Hälfte des Medians.
 * - `lowFps`: Median der letzten 10 ungebremsten Bildraten unter 30.
 * - `cpu`: CPU in 8 der letzten 10 Messungen ≥ 90 % des ganzen PCs.
 */
export function perfTips(points: readonly PerfPoint[]): PerfTip[] {
  const tips: PerfTip[] = []
  if (points.length >= 8) {
    if (count(points, 10, (p) => p.heapUsedMb !== null && p.heapMaxMb !== null && p.heapMaxMb > 0 && p.heapUsedMb / p.heapMaxMb >= HEAP_FULL) >= 8) {
      tips.push('memory')
    }
    const tight = (p: PerfPoint) =>
      p.systemFreeMb !== null && p.systemTotalMb !== null && p.systemTotalMb > 0 && p.systemFreeMb / p.systemTotalMb < SYSTEM_FREE_SHARE && p.systemFreeMb < SYSTEM_FREE_MB
    if (count(points, 10, tight) >= 8) tips.push('systemMemory')
  }
  const fps = points.slice(-15).filter((p) => !p.fpsLimited && p.fps !== null).map((p) => p.fps!)
  const middle = median(fps)
  if (fps.length >= 10 && middle !== null && middle >= LOW_FPS && fps.filter((f) => f < middle * STUTTER_DROP).length >= 3) tips.push('stutter')
  const recent = points.slice(-10).filter((p) => !p.fpsLimited && p.fps !== null).map((p) => p.fps!)
  const recentMiddle = median(recent)
  if (recent.length >= 8 && recentMiddle !== null && recentMiddle < LOW_FPS) tips.push('lowFps')
  if (points.length >= 8 && count(points, 10, (p) => p.cpu !== null && p.cpu >= CPU_BUSY) >= 8) tips.push('cpu')
  return tips.slice(0, 2)
}

/**
 * Pfad einer kleinen Kurve (SVG, Breite × Höhe) für die Werte von links (alt) nach rechts (neu); Lücken (`null`)
 * unterbrechen die Linie. `max` = oberer Rand (mindestens der größte Wert). Leer = keine zwei Punkte.
 */
export function sparkPath(values: readonly (number | null)[], width: number, height: number, max: number, slots = PERF_HISTORY): string {
  const top = Math.max(max, ...values.filter((v): v is number => v !== null), 1e-9)
  const step = width / Math.max(1, slots - 1)
  const offset = slots - values.length
  let path = ''
  let open = false
  values.forEach((v, i) => {
    if (v === null || !Number.isFinite(v)) {
      open = false
      return
    }
    const x = (offset + i) * step
    // 1 px Rand oben und unten, damit die 2-px-Linie nicht angeschnitten wird.
    const y = height - 1 - (Math.max(0, v) / top) * (height - 2)
    path += `${open ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`
    open = true
  })
  return /L/.test(path) ? path : ''
}
