import { paintWinterStill } from './canvas'
import { WINTER_SEED } from './layout'
import type { WinterVariant } from './palette'

// Vorschau des Designs „Polarlicht“ in den Einstellungen: eine echte kleine Szene, gemalt nach denselben Regeln
// wie der Hintergrund – nachts oder am Morgen, je nach Farbschema.

export const PREVIEW_COLS = 240
export const PREVIEW_ROWS = 88

const urls = new Map<WinterVariant, string>()

/** PNG-Data-URL der Vorschau; `null` ohne Canvas (Tests, Server). */
export function winterPreviewUrl(variant: WinterVariant): string | null {
  if (typeof document === 'undefined') return null
  const known = urls.get(variant)
  if (known) return known
  try {
    const url = paintWinterStill({ cols: PREVIEW_COLS, rows: PREVIEW_ROWS, seed: WINTER_SEED }, variant, { tick: 40, seconds: 21, level: 0.9 })?.toDataURL('image/png')
    if (!url) return null
    urls.set(variant, url)
    return url
  } catch {
    return null
  }
}
