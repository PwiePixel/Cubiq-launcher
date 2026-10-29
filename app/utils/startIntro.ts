// Start-Animation: Beim Öffnen rasten die Teile des Cubiq-Logos zusammen (mit kurzem Klang) und geben den
// Launcher frei. Die Einstellungen werden lokal gespiegelt, damit Ladebildschirm und Animation sofort entscheiden
// können – der Kern lädt die echten Einstellungen erst später.

import { ref } from 'vue'

/** Spiegel der Einstellungen „Start-Animation“, „Start-Ton“ und „Animationen“ (localStorage). */
export const INTRO_PREFS_KEY = 'cubiq-intro'

export interface IntroPrefs {
  enabled: boolean
  /** `full`, `system` oder `reduced` (wie die Einstellung „Animationen“). */
  motion: string
  /** Start-Ton (fehlt in älteren Spiegeln → an, wie ab Werk). */
  sound: boolean
}

const DEFAULT_PREFS: IntroPrefs = { enabled: true, motion: 'full', sound: true }

/** Gespeicherten Spiegel lesen; Unbekanntes oder Kaputtes → Vorgaben. */
export function parseIntroPrefs(raw: string | null): IntroPrefs {
  if (!raw) return { ...DEFAULT_PREFS }
  try {
    const value = JSON.parse(raw) as Partial<IntroPrefs> | null
    return {
      enabled: typeof value?.enabled === 'boolean' ? value.enabled : DEFAULT_PREFS.enabled,
      motion: typeof value?.motion === 'string' ? value.motion : DEFAULT_PREFS.motion,
      sound: typeof value?.sound === 'boolean' ? value.sound : DEFAULT_PREFS.sound,
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
export function mirrorIntroPrefs(ui: { startIntro?: boolean; startIntroSound?: boolean; motion?: string } | undefined) {
  if (!ui) return
  try {
    const prefs: IntroPrefs = { enabled: ui.startIntro !== false, motion: ui.motion ?? 'full', sound: ui.startIntroSound !== false }
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

/** Das REC-Overlay (eigenes Fenster über dem Spiel) – dort nie Animation oder Ton. */
export const CLIP_OVERLAY_PATH = '/clip-overlay'

export interface IntroPlanInput {
  prefs: IntroPrefs
  /** Meldet das System „Bewegung reduzieren“? */
  systemReduced: boolean
  /** `location.pathname` beim Start. */
  pathname: string
  /** Tauri-Fenster (`main`, `clip-overlay` …); `null` ohne Tauri (Browser). */
  windowLabel: string | null
  /** Startet das Fenster unsichtbar (z. B. minimiert)? */
  hidden: boolean
  /** Android/iOS: dort nie Ton (Android spielt auch im Lautlos-Modus). */
  mobile: boolean
}

/** Was beim Start läuft: die Animation (`play`) und der Klang dazu (`sound`). */
export function introPlan(input: IntroPlanInput): { play: boolean; sound: boolean } {
  const overlay = input.pathname === CLIP_OVERLAY_PATH || input.pathname.startsWith(`${CLIP_OVERLAY_PATH}/`)
  const mainWindow = input.windowLabel === null || input.windowLabel === 'main'
  const play = shouldPlayIntro(input.prefs, input.systemReduced) && !overlay && mainWindow && !input.hidden
  return { play, sound: play && input.prefs.sound && !input.mobile }
}

/** „Jetzt abspielen“ aus den Einstellungen: Zähler, den die Start-Animation beobachtet. */
export const introReplay = ref(0)

export function replayStartIntro() {
  introReplay.value += 1
}
