// Start-Klang zur Start-Animation: eine fertige Datei (public/sounds/start.wav, erzeugt von scripts/start-sound.mjs),
// abgespielt über WebAudio mit fester Lautstärke 30 % (GainNode 0,3 auf die voll ausgesteuerte Datei).
// - WebAudio statt <audio>: iOS ignoriert `<audio>.volume`, dort käme sonst 100 %.
// - Holen per fetch (gleiche Herkunft) + decodeAudioData – data:- und blob:-Adressen verbieten die Sicherheitsregeln.
// - Ohne Klick: Das Webview erlaubt Ton von selbst (wry: autoplay). Wo nicht (Browser im Entwicklermodus), bleibt
//   der Kontext „suspended“ – dann gibt es still keinen Ton, nie nachträglich beim nächsten Klick.
// - Jeder Fehler (kein Audio, Datei fehlt, nicht lesbar) bleibt still; die Animation läuft unabhängig davon.
// - Nach dem Klang wird der Kontext geschlossen: kein Eintrag im Lautstärkemixer, kein offener Audio-Stream.
// Nur ausdrückliche Importe – Tests laden die Datei direkt.

export const START_SOUND_URL = '/sounds/start.wav'
/** Lautstärke des Start-Klangs: fest 30 % (linear, rund −10,5 dB). */
export const START_SOUND_VOLUME = 0.3
/** So lange darf `resume()` dauern, bevor es ohne Ton weitergeht. */
const RESUME_TIMEOUT_MS = 150

export interface StartSound {
  /** Spielt ab `offset` Sekunden der Datei (die Animation ist schon so weit). Nur einmal. */
  start(offset: number): void
  /** Blendet in `ms` aus, stoppt und schließt den Kontext. */
  fadeOut(ms: number): void
  /** Sofort beenden und den Kontext schließen (mehrfach aufrufbar). */
  close(): void
  /** Verzögerung bis zum Lautsprecher in Sekunden (`outputLatency`, sonst `baseLatency`). */
  readonly latency: number
}

/** Austauschbar für Tests: AudioContext-Konstruktor, fetch und Wartezeit für `resume()`. */
export interface StartSoundDeps {
  AudioContext?: (new () => AudioContext) | null
  fetch?: typeof fetch
  timeoutMs?: number
}

function audioContextCtor(deps: StartSoundDeps): (new () => AudioContext) | null {
  if (deps.AudioContext !== undefined) return deps.AudioContext
  const g = globalThis as { AudioContext?: new () => AudioContext; webkitAudioContext?: new () => AudioContext }
  return g.AudioContext ?? g.webkitAudioContext ?? null
}

/** Läuft der Kontext innerhalb von `ms`? Ein hängendes `resume()` (keine Erlaubnis) zählt als nein. */
function running(ctx: AudioContext, ms: number): Promise<boolean> {
  if (ctx.state === 'running') return Promise.resolve(true)
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(ctx.state === 'running'), ms)
    ctx.resume().then(
      () => {
        clearTimeout(timer)
        resolve(ctx.state === 'running')
      },
      () => {
        clearTimeout(timer)
        resolve(false)
      },
    )
  })
}

function closeQuietly(ctx: AudioContext) {
  try {
    ctx.close().catch(() => {})
  } catch {
    // Schon geschlossen.
  }
}

/** Dekodieren – auch mit älterem WebKit, das nur die Rückruf-Form kennt. */
function decode(ctx: AudioContext, data: ArrayBuffer): Promise<AudioBuffer> {
  return new Promise((resolve, reject) => {
    const result = ctx.decodeAudioData(data, resolve, reject) as Promise<AudioBuffer> | undefined
    // Der Fehler kommt schon über `reject`; das Promise (falls es eins gibt) nicht unbehandelt lassen.
    result?.catch?.(() => {})
  })
}

/**
 * Kontext anlegen, Datei holen und dekodieren. `null`, wenn es keinen Ton geben kann – dann ist alles schon
 * wieder geschlossen.
 */
export async function prepareStartSound(deps: StartSoundDeps = {}): Promise<StartSound | null> {
  const Ctor = audioContextCtor(deps)
  if (!Ctor) return null
  let ctx: AudioContext
  try {
    ctx = new Ctor()
  } catch {
    return null
  }
  try {
    // Datei schon holen, während der Kontext anläuft.
    const file = (deps.fetch ? deps.fetch(START_SOUND_URL) : fetch(START_SOUND_URL)).then((res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return res.arrayBuffer()
    })
    file.catch(() => {})
    if (!(await running(ctx, deps.timeoutMs ?? RESUME_TIMEOUT_MS))) {
      closeQuietly(ctx)
      return null
    }
    const buffer = await decode(ctx, await file)
    return player(ctx, buffer)
  } catch {
    closeQuietly(ctx)
    return null
  }
}

function player(ctx: AudioContext, buffer: AudioBuffer): StartSound {
  let source: AudioBufferSourceNode | null = null
  let gain: GainNode | null = null
  let closed = false
  let fallback: ReturnType<typeof setTimeout> | undefined

  function close() {
    if (closed) return
    closed = true
    clearTimeout(fallback)
    try {
      source?.stop()
    } catch {
      // Nie gestartet oder schon gestoppt.
    }
    closeQuietly(ctx)
  }

  return {
    latency: Math.max(0, ctx.outputLatency || ctx.baseLatency || 0),
    start(offset) {
      if (closed || source) return
      try {
        source = ctx.createBufferSource()
        source.buffer = buffer
        gain = ctx.createGain()
        gain.gain.value = START_SOUND_VOLUME
        source.connect(gain)
        gain.connect(ctx.destination)
        // Zu Ende gespielt → Kontext schließen.
        source.onended = close
        source.start(0, Math.min(Math.max(0, offset), buffer.duration))
      } catch {
        close()
      }
    },
    fadeOut(ms) {
      if (closed) return
      if (!source || !gain) return close()
      try {
        const now = ctx.currentTime
        const end = now + Math.max(0, ms) / 1000
        const level = gain.gain
        level.cancelScheduledValues(now)
        level.setValueAtTime(level.value, now)
        level.linearRampToValueAtTime(0, end)
        source.stop(end)
        // `ended` schließt; falls es ausbleibt, spätestens kurz danach.
        fallback = setTimeout(close, ms + 100)
      } catch {
        close()
      }
    },
    close,
  }
}

let preview: StartSound | null = null
let previewRun = 0

/** Hörprobe beim Einschalten in den Einstellungen (ein Klick – Ton ist dann überall erlaubt). */
export function previewStartSound(deps: StartSoundDeps = {}): Promise<void> {
  const run = ++previewRun
  preview?.fadeOut(40)
  preview = null
  return prepareStartSound(deps).then((sound) => {
    if (!sound) return
    // Schneller noch einmal geschaltet: nur die letzte Hörprobe spielt.
    if (run !== previewRun) return sound.close()
    preview = sound
    sound.start(0)
  })
}
