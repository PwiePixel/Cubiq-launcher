import { paintWinterStill } from './canvas'
import type { WinterVariant } from './palette'

// Titelbild einer Instanz ohne eigenes Banner im Design „Polarlicht“: ein Standbild der Szene aus dem Seed der
// Instanz – Inseln, Tannen und Polarlicht liegen bei jeder Instanz anders, bleiben für dieselbe aber gleich.
// Seitenverhältnis wie die übrigen Titelbilder (8 : 3).

export const WINTER_BANNER_COLS = 256
export const WINTER_BANNER_ROWS = 96

/** Malt das Motiv in ein neues Canvas (Texel-Auflösung); `null` ohne 2D-Kontext. */
export function paintWinterBanner(seed: number, variant: WinterVariant): HTMLCanvasElement | null {
  return paintWinterStill(
    { cols: WINTER_BANNER_COLS, rows: WINTER_BANNER_ROWS, seed, heroX: 0.3 + ((seed >>> 5) % 50) / 100 },
    variant,
    { tick: seed % 211, seconds: (seed >>> 3) % 400, level: 0.85 },
    0.6,
  )
}
