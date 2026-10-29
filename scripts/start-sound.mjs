// Start-Klang der Start-Animation: ein kurzes Glockenspiel, rein rechnerisch erzeugt – ohne Fremd-Samples, ohne
// Zufall (fester Startwert) und ohne Abhängigkeiten. Die Datei liegt fertig im Repo (`public/sounds/start.wav`):
// Im Launcher würde das Rechnen beim Start rund eine Viertelsekunde den Hauptthread blockieren.
//
// Zeitplan aus app/utils/startIntroCues.json – dieselbe Datei steuert die Animation (StartIntro.vue):
// - leiser, tiefer Auftakt ab 0 s, der bis zum Würfel anschwillt,
// - je Arm ein glasiges „Pling“ genau beim Einrasten (C5, E5, G5),
// - beim Würfel C6 und ein weicher C-Dur-Akkord,
// - während der Name erscheint ein paar sehr leise, hohe Funken,
// - ab der Übergabe an den Launcher klingt alles weich aus.
// Die Datei ist auf Spitze 1,0 normiert; der Launcher spielt sie mit 30 % (GainNode 0,3, siehe app/utils/startSound.ts).
//
//   node scripts/start-sound.mjs                    → public/sounds/start.wav
//   node scripts/start-sound.mjs --preview <ordner> → zusätzlich <ordner>/start-sound-30.wav (so laut wie im Launcher)

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

/** Zeitpunkte der Animation in Sekunden (Arme landen, Würfel springt auf, Funkeln, Übergabe). */
export const CUES = JSON.parse(readFileSync(join(ROOT, 'app', 'utils', 'startIntroCues.json'), 'utf8'))
export const SAMPLE_RATE = 44100
/** Lautstärke im Launcher (START_SOUND_VOLUME in app/utils/startSound.ts) – für die Hörprobe. */
export const PREVIEW_VOLUME = 0.3
/** Funken während des Namens: so viele wie funkelnde Pixel in StartIntro.vue, gleichmäßig verteilt. */
export const SPARKLES = 5

/** Kleiner, fester Zufall (mulberry32) – gleiche Ausgabe bei jedem Lauf. */
function rng(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/**
 * Glasiger „Pling“: Grundton und Obertöne mit eigener Abklingzeit (die hohen verklingen schneller), dazu ein sehr
 * kurzer, leicht unharmonischer Anschlag für den Moment des Einrastens. Weicher Einsatz (3 ms, Kosinus) gegen Knacken.
 */
function pling(buf, sr, t0, f, gain, tau = 0.42, strike = 0.15) {
  const partials = [
    [1, 1, tau],
    [2, 0.3, tau * 0.55],
    [3, 0.12, tau * 0.35],
    [4.16, 0.05, tau * 0.22], // leicht unharmonisch → Glas/Glocke
    [5.43, strike, 0.012], // Anschlag: nur ein paar Millisekunden
  ]
  const start = Math.round(t0 * sr)
  const len = Math.min(buf.length - start, Math.floor(tau * 7 * sr))
  const attack = 0.003
  for (let i = 0; i < len; i++) {
    const t = i / sr
    const atk = t < attack ? 0.5 - 0.5 * Math.cos((Math.PI * t) / attack) : 1
    let s = 0
    for (const [m, a, d] of partials) {
      if (f * m >= sr / 2) continue
      s += a * Math.sin(2 * Math.PI * f * m * t) * Math.exp(-t / d)
    }
    buf[start + i] += gain * atk * s
  }
}

/** Weicher Akkord (Pad): langsamer Anstieg, langes Ausklingen, leichtes Schweben (je Ton zwei verstimmte Sinus). */
function bloom(buf, sr, t0, freqs, gain, attack = 0.07, tau = 0.55) {
  const start = Math.round(t0 * sr)
  for (let i = 0; start + i < buf.length; i++) {
    const t = i / sr
    const rise = t < attack ? 0.5 - 0.5 * Math.cos((Math.PI * t) / attack) : 1
    const env = rise * Math.exp(-Math.max(0, t - attack) / tau)
    if (env < 1e-5 && t > attack) break
    let s = 0
    for (const f of freqs) s += Math.sin(2 * Math.PI * f * t) + 0.6 * Math.sin(2 * Math.PI * f * 1.0035 * t + 0.7)
    buf[start + i] += (gain * env * s) / freqs.length
  }
}

/**
 * Leiser, tiefer Auftakt unter den einfliegenden Teilen: ein Sinus gleitet eine Oktave hinauf (C2 → C3, passend zum
 * Akkord), schwillt bis zum Würfel an und klingt danach schnell aus. Eine Spur Oberton, damit kleine Lautsprecher
 * den Anstieg ahnen lassen.
 */
function swell(buf, sr, t0, peakAt, gain) {
  const start = Math.round(t0 * sr)
  const rise = peakAt - t0
  const fall = 0.14
  const len = Math.min(buf.length - start, Math.floor((rise + fall * 8) * sr))
  let phase = 0
  for (let i = 0; i < len; i++) {
    const t = i / sr
    const p = Math.min(1, t / rise)
    const f = 65.41 * 2 ** p
    phase += (2 * Math.PI * f) / sr
    const env = t < rise ? Math.sin((Math.PI / 2) * p) ** 2 : Math.exp(-(t - rise) / fall)
    buf[start + i] += gain * env * (Math.sin(phase) + 0.12 * Math.sin(2 * phase))
  }
}

/** Funken: sehr leise, hohe Glöckchen (C-Dur-Pentatonik) im Takt der funkelnden Pixel – jeder Ton einmal. */
function sparkle(buf, sr, from, to, gain, seed) {
  const r = rng(seed)
  const scale = [2093, 2349.32, 2637.02, 3135.96, 3520] // C7 D7 E7 G7 A7
  for (let i = scale.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1))
    ;[scale[i], scale[j]] = [scale[j], scale[i]]
  }
  for (let k = 0; k < SPARKLES; k++) {
    // Pixel k leuchtet ab from + (to - from)·k/SPARKLES auf; der Ton setzt kurz danach ein (hellster Moment).
    const t0 = from + ((to - from) * k) / SPARKLES + 0.03
    pling(buf, sr, t0, scale[k % scale.length], gain * (0.75 + 0.25 * r()), 0.15, 0)
  }
}

/**
 * Schlichter Hall (Schroeder/Freeverb-Art): vier Kammfilter mit Dämpfung (Tiefpass in der Rückkopplung – der Nachhall
 * wird weicher und klingt nicht metallisch) und zwei Allpassfilter, Anteil `wet`.
 */
function reverb(dry, sr, wet = 0.2, damp = 0.3) {
  const out = new Float64Array(dry.length)
  const combs = [0.0297, 0.0371, 0.0411, 0.0437].map((s) => ({ d: Math.floor(s * sr), g: 0.8, buf: new Float64Array(Math.floor(s * sr)), i: 0, lp: 0 }))
  const aps = [0.005, 0.0017].map((s) => ({ d: Math.floor(s * sr), g: 0.7, buf: new Float64Array(Math.floor(s * sr)), i: 0 }))
  for (let n = 0; n < dry.length; n++) {
    let acc = 0
    for (const c of combs) {
      const y = c.buf[c.i]
      c.lp = y * (1 - damp) + c.lp * damp
      c.buf[c.i] = dry[n] + c.lp * c.g
      c.i = (c.i + 1) % c.d
      acc += y
    }
    let y = acc / combs.length
    for (const a of aps) {
      const b = a.buf[a.i]
      const v = y + b * a.g
      a.buf[a.i] = v
      a.i = (a.i + 1) % a.d
      y = b - a.g * v
    }
    out[n] = dry[n] + wet * y
  }
  return out
}

/** Der ganze Klang (Länge `soundSeconds`), auf Spitze 1,0 normiert, ab der Übergabe weich ausgeblendet. */
export function renderStartSound(sr = SAMPLE_RATE) {
  const n = Math.round(CUES.soundSeconds * sr)
  const dry = new Float64Array(n)
  const [arm1, arm2, arm3] = CUES.arms
  swell(dry, sr, 0, CUES.cube, 0.14)
  pling(dry, sr, arm1, 523.25, 0.42)
  pling(dry, sr, arm2, 659.26, 0.42)
  pling(dry, sr, arm3, 783.99, 0.45)
  pling(dry, sr, CUES.cube, 1046.5, 0.55, 0.6)
  bloom(dry, sr, CUES.cube - 0.04, [261.63, 392.0, 659.26], 0.42)
  sparkle(dry, sr, CUES.sparkle[0], CUES.sparkle[1], 0.06, 7)
  const wet = reverb(dry, sr)
  // Ausklingen: ab der Übergabe (hold) bis zum Ende als halbe Kosinuswelle – kein harter Schluss.
  const fadeFrom = Math.round(CUES.hold * sr)
  for (let i = fadeFrom; i < n; i++) wet[i] *= 0.5 + 0.5 * Math.cos((Math.PI * (i - fadeFrom)) / (n - 1 - fadeFrom))
  let peak = 0
  for (const v of wet) peak = Math.max(peak, Math.abs(v))
  const out = new Float32Array(n)
  for (let i = 0; i < n; i++) out[i] = wet[i] / peak
  return out
}

/** 16-Bit-PCM-WAV, mono. Werte außerhalb von ±1 werden begrenzt. */
export function encodeWav(samples, sr = SAMPLE_RATE) {
  const data = Buffer.alloc(samples.length * 2)
  for (let i = 0; i < samples.length; i++) data.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767))), i * 2)
  const h = Buffer.alloc(44)
  h.write('RIFF', 0)
  h.writeUInt32LE(36 + data.length, 4)
  h.write('WAVE', 8)
  h.write('fmt ', 12)
  h.writeUInt32LE(16, 16) // Größe des fmt-Blocks
  h.writeUInt16LE(1, 20) // PCM
  h.writeUInt16LE(1, 22) // mono
  h.writeUInt32LE(sr, 24)
  h.writeUInt32LE(sr * 2, 28) // Bytes je Sekunde
  h.writeUInt16LE(2, 32) // Bytes je Sample
  h.writeUInt16LE(16, 34) // Bit je Sample
  h.write('data', 36)
  h.writeUInt32LE(data.length, 40)
  return Buffer.concat([h, data])
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]
if (isMain) {
  const args = process.argv.slice(2)
  const preview = args.includes('--preview') ? args[args.indexOf('--preview') + 1] : null
  if (args.includes('--preview') && !preview) {
    console.error('Aufruf: start-sound.mjs [--preview <ordner>]')
    process.exit(2)
  }
  const sound = renderStartSound()
  const target = join(ROOT, 'public', 'sounds', 'start.wav')
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, encodeWav(sound))
  console.error(`Start-Klang (${CUES.soundSeconds} s) → ${target}`)
  if (preview) {
    mkdirSync(preview, { recursive: true })
    const quiet = sound.map((v) => v * PREVIEW_VOLUME)
    writeFileSync(join(preview, 'start-sound-30.wav'), encodeWav(quiet))
    console.error(`Hörprobe mit ${PREVIEW_VOLUME * 100} % → ${join(preview, 'start-sound-30.wav')}`)
  }
}
