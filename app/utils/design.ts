import { readonly, ref } from 'vue'
import type { Accent, Design } from '~/types'

// Das gesamte Aussehen des Launchers. „Cubiq“ ist der Standard (ruhige, dunkle Flächen mit dem Grün aus dem
// Logo), „Polarlicht“ (`winter`) zeigt einen Nachthimmel mit Polarlicht-Bändern, schwebenden Schneeinseln und
// eisigen Akzenten. Das Design steht als `data-design` auf <html> (beim Standard fehlt das Attribut): winter.css
// tauscht damit Farben und Details, DesignScene den Hintergrund. Farbschema und Akzent bleiben eigene
// Einstellungen. Der Cubiq Client bekommt das Design über `launcher-theme.json` und sieht im Spiel gleich aus.

export const designs = ['cubiq', 'winter'] as const satisfies readonly Design[]

/** Unbekannte oder fehlende Werte (ältere Einstellungen) gelten als Cubiq. */
export function normalizeDesign(value: unknown): Design {
  return designs.find((d) => d === value) ?? 'cubiq'
}

/** Merker im localStorage: Der Ladebildschirm läuft vor Vue und zeigt so beim nächsten Start gleich das Design. */
export const DESIGN_HINT = 'cubiq-design'

/** Was ein gewähltes Design anfasst – im Launcher <html> und localStorage, in Tests eigene Attrappen. */
export interface DesignTarget {
  root: { dataset: Record<string, string | undefined> } | null
  storage: { setItem(key: string, value: string): void; removeItem(key: string): void } | null
}

/** Schreibt das Design als Attribut und als Merker für den Ladebildschirm. */
export function writeDesign(design: Design, target: DesignTarget) {
  if (target.root) {
    if (design === 'cubiq') delete target.root.dataset.design
    else target.root.dataset.design = design
  }
  try {
    if (design === 'cubiq') target.storage?.removeItem(DESIGN_HINT)
    else target.storage?.setItem(DESIGN_HINT, design)
  } catch {
    // Kein Speicher (privater Modus): dann zeigt nur der Ladebildschirm den Standard.
  }
}

function hint(): Design {
  try {
    return normalizeDesign(globalThis.localStorage?.getItem(DESIGN_HINT))
  } catch {
    return 'cubiq'
  }
}

/** Angezeigtes Design – startet mit dem Merker, damit bis zu den Einstellungen nichts umspringt. */
const shown = ref<Design>(hint())

/** Reaktives Design für Komponenten (Hintergrund-Szene, Titelbilder). */
export function useDesign() {
  return readonly(shown)
}

/** Design anwenden (auch die Vorschau im Einstellungsfenster); liefert das bereinigte Design. */
export function applyDesign(value: unknown): Design {
  const design = normalizeDesign(value)
  shown.value = design
  writeDesign(design, {
    root: typeof document === 'undefined' ? null : document.documentElement,
    storage: typeof localStorage === 'undefined' ? null : localStorage,
  })
  return design
}

/** Akzentfarbe, die ein Design mitbringt: Cubiq-Grün bzw. Eisblau. */
export const designAccent: Readonly<Record<Design, Accent>> = { cubiq: 'cubiq', winter: 'ice' }

/**
 * Akzent nach einem Designwechsel: Trägt der Launcher noch die Farbe eines Designs, kommt die des neuen
 * (Cubiq-Grün ↔ Eisblau); eine selbst gewählte Farbe bleibt. Der Cubiq Client macht es bei einer Wahl im Spiel
 * genauso.
 */
export function accentAfterSwitch(next: Design, accent: Accent): Accent {
  return Object.values(designAccent).includes(accent) ? designAccent[next] : accent
}
