import { readonly, ref } from 'vue'
import type { Design } from '~/types'

// Design des Launchers. Bisher gibt es eines – schlichte, dunkle Flächen mit dem Grün aus dem
// Logo; Akzentfarbe und Farbschema sind eigene Einstellungen. Der Typ bleibt, damit später weitere
// Designs (und ihre Einstellung im Konto-Abgleich) ohne Umbau dazukommen können.

export const designs = ['cubiq'] as const satisfies readonly Design[]

/** Unbekannte oder fehlende Werte (ältere Einstellungen) → Cubiq. */
export function normalizeDesign(value: unknown): Design {
  return designs.includes(value as Design) ? (value as Design) : 'cubiq'
}

/** Gerade angezeigtes Design. */
const active = ref<Design>('cubiq')

/** Reaktives Design für Komponenten (Hintergrund-Szene, Banner). */
export function useDesign() {
  return readonly(active)
}

/** Design anwenden; liefert das bereinigte Design. */
export function applyDesign(value: unknown): Design {
  const design = normalizeDesign(value)
  active.value = design
  return design
}
