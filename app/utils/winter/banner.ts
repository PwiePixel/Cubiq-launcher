import { paintWinterStill } from './canvas'
import type { WinterVariant } from './palette'

// Titelbild einer Instanz ohne eigenes Banner im Design „Polarlicht“: ein Standbild der Szene aus dem Seed der
// Instanz – Inseln, Tannen und Polarlicht liegen bei jeder Instanz anders, bleiben für dieselbe aber gleich. Damit sich
// die Instanzen in der Bibliothek auf einen Blick unterscheiden, wählt der Seed außerdem einen von drei Vorhängen
// (Farben), die Lage der großen Insel und wie kräftig das Polarlicht leuchtet. Seitenverhältnis wie die übrigen
// Titelbilder (8 : 3).

export const WINTER_BANNER_COLS = 256
export const WINTER_BANNER_ROWS = 96

/** Vorhang des Titelbilds (0 … 2, siehe CURTAINS) aus dem Seed der Instanz. */
export function winterBannerCurtain(seed: number): number {
  return (seed >>> 13) % 3
}

/** Stärke des Polarlichts im Titelbild aus dem Seed der Instanz: 0,65 … 1. */
export function winterBannerLevel(seed: number): number {
  return 0.65 + ((seed >>> 17) % 36) / 100
}

/** Malt das Motiv in ein neues Canvas (Texel-Auflösung); `null` ohne 2D-Kontext. */
export function paintWinterBanner(seed: number, variant: WinterVariant): HTMLCanvasElement | null {
  return paintWinterStill(
    { cols: WINTER_BANNER_COLS, rows: WINTER_BANNER_ROWS, seed, heroX: 0.3 + ((seed >>> 5) % 50) / 100 },
    variant,
    { tick: seed % 211, seconds: (seed >>> 3) % 400, level: winterBannerLevel(seed) },
    0.6,
    winterBannerCurtain(seed),
  )
}
