// Start-Animation: Beim Öffnen blendet das Cubiq-Logo kurz ein und gibt den Launcher frei.
// Die Einstellung wird lokal gespiegelt, damit die Animation sofort entscheiden kann – der Kern
// lädt die echten Einstellungen erst später.

import { ref } from 'vue'

/** Spiegel der Einstellungen „Start-Animation“ und „Animationen“ (localStorage). */
export const INTRO_PREFS_KEY = 'cubiq-intro'

export interface IntroPrefs {
  enabled: boolean
  /** `full`, `system` oder `reduced` (wie die Einstellung „Animationen“). */
  motion: string
}

const DEFAULT_PREFS: IntroPrefs = { enabled: true, motion: 'full' }

/** Gespeicherten Spiegel lesen; Unbekanntes oder Kaputtes → Vorgaben. */
export function parseIntroPrefs(raw: string | null): IntroPrefs {
  if (!raw) return { ...DEFAULT_PREFS }
  try {
    const value = JSON.parse(raw) as Partial<IntroPrefs> | null
    return {
      enabled: typeof value?.enabled === 'boolean' ? value.enabled : DEFAULT_PREFS.enabled,
      motion: typeof value?.motion === 'string' ? value.motion : DEFAULT_PREFS.motion,
    }
  } catch {
    return { ...DEFAULT_PREFS }
  }
}

/** Einstellungs-Spiegel lesen. */
export function readIntroPrefs(): IntroPrefs {
  try {
    return parseIntroPrefs(localStorage.getItem(INTRO_PREFS_KEY))
  } catch {
    return { ...DEFAULT_PREFS }
  }
}

/** Einstellungen spiegeln – aufgerufen, sobald sie geladen oder geändert sind. */
export function mirrorIntroPrefs(ui: { startIntro?: boolean; motion?: string } | undefined) {
  if (!ui) return
  try {
    const prefs: IntroPrefs = { enabled: ui.startIntro !== false, motion: ui.motion ?? 'full' }
    localStorage.setItem(INTRO_PREFS_KEY, JSON.stringify(prefs))
  } catch {
    // Kein Speicher – beim nächsten Start gelten die Vorgaben.
  }
}

/** Läuft die Animation beim Start? Aus bei ausgeschalteter Einstellung oder „Animationen: reduziert“. */
export function shouldPlayIntro(prefs: IntroPrefs, systemReduced = false): boolean {
  if (!prefs.enabled || prefs.motion === 'reduced') return false
  return !(prefs.motion === 'system' && systemReduced)
}

/** „Jetzt abspielen“ aus den Einstellungen: Zähler, den die Start-Animation beobachtet. */
export const introReplay = ref(0)

export function replayStartIntro() {
  introReplay.value += 1
}
