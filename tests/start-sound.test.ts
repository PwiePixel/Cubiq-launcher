import fs from 'node:fs'
import path from 'node:path'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { CUES, SAMPLE_RATE, encodeWav, renderStartSound } from '../scripts/start-sound.mjs'
import { START_SOUND_URL, START_SOUND_VOLUME, prepareStartSound, previewStartSound } from '../app/utils/startSound'

const wavFile = path.resolve(__dirname, '..', 'public', 'sounds', 'start.wav')

/** Rendern dauert auf langsamen Rechnern ein paar Sekunden. */
const RENDER_MS = 60_000

describe('Start-Klang (Generator)', () => {
  let sound: Float32Array
  beforeAll(() => {
    sound = renderStartSound()
  }, RENDER_MS)

  it('ist bei jedem Lauf gleich', () => {
    expect(renderStartSound()).toEqual(sound)
  }, RENDER_MS)

  it('hat die Länge aus dem Zeitplan, ist voll ausgesteuert und beginnt und endet still', () => {
    expect(sound.length).toBe(Math.round(CUES.soundSeconds * SAMPLE_RATE))
    let peak = 0
    for (const v of sound) {
      expect(Number.isFinite(v)).toBe(true)
      peak = Math.max(peak, Math.abs(v))
    }
    expect(peak).toBeGreaterThanOrEqual(0.98)
    expect(peak).toBeLessThanOrEqual(1)
    expect(sound[0]).toBe(0)
    expect(Math.abs(sound[sound.length - 1]!)).toBeLessThan(1e-3)
  })

  it('setzt genau mit den Teilen ein: an jedem Cue mindestens 3 dB mehr Anschlag als kurz davor', () => {
    // Hochpass über die erste Differenz: hebt die Anschläge hervor, nicht das Ausklingen davor.
    const energy = (from: number, to: number) => {
      let sum = 0
      for (let i = Math.max(1, from); i < to; i++) sum += (sound[i]! - sound[i - 1]!) ** 2
      return sum
    }
    const window = Math.round(0.03 * SAMPLE_RATE)
    for (const cue of [...CUES.arms, CUES.cube]) {
      const at = Math.round(cue * SAMPLE_RATE)
      const gain = 10 * Math.log10(energy(at, at + window) / energy(at - window, at))
      expect(gain, `Cue ${cue} s`).toBeGreaterThanOrEqual(3)
    }
  })

  it('die Datei im Repo ist dieselbe: WAV, PCM, mono, 16 Bit, 44,1 kHz, klein, ±1 Stufe zum frischen Rendern', () => {
    const file = fs.readFileSync(wavFile)
    expect(file.length).toBeLessThanOrEqual(220 * 1024)
    expect(file.toString('ascii', 0, 4)).toBe('RIFF')
    expect(file.readUInt32LE(4)).toBe(file.length - 8)
    expect(file.toString('ascii', 8, 12)).toBe('WAVE')
    expect(file.toString('ascii', 12, 16)).toBe('fmt ')
    expect(file.readUInt16LE(20)).toBe(1) // PCM
    expect(file.readUInt16LE(22)).toBe(1) // mono
    expect(file.readUInt32LE(24)).toBe(44100)
    expect(file.readUInt16LE(34)).toBe(16)
    expect(file.toString('ascii', 36, 40)).toBe('data')
    const fresh = encodeWav(sound)
    expect(file.length).toBe(fresh.length)
    let worst = 0
    for (let at = 44; at < file.length; at += 2) worst = Math.max(worst, Math.abs(file.readInt16LE(at) - fresh.readInt16LE(at)))
    expect(worst).toBeLessThanOrEqual(1)
  })
})

// --- Abspielen mit einem nachgebauten AudioContext ---------------------------------------------------------------

type Call = [string, ...unknown[]]

class FakeParam {
  value = 1
  calls: Call[] = []
  cancelScheduledValues(t: number) {
    this.calls.push(['cancel', t])
  }
  setValueAtTime(v: number, t: number) {
    this.calls.push(['set', v, t])
  }
  linearRampToValueAtTime(v: number, t: number) {
    this.calls.push(['ramp', v, t])
  }
}

class FakeNode {
  connected: unknown[] = []
  connect<T>(node: T): T {
    this.connected.push(node)
    return node
  }
}

class FakeSource extends FakeNode {
  buffer: unknown = null
  onended: (() => void) | null = null
  started: [number, number] | null = null
  stopped: number[] = []
  start(when: number, offset: number) {
    this.started = [when, offset]
  }
  stop(when = 0) {
    this.stopped.push(when)
  }
}

class FakeGain extends FakeNode {
  gain = new FakeParam()
}

interface FakeOptions {
  state?: AudioContextState
  resume?: () => Promise<void>
  decode?: 'ok' | 'error'
}

function fakeAudio(options: FakeOptions = {}) {
  const contexts: FakeContext[] = []
  class FakeContext {
    state: AudioContextState = options.state ?? 'running'
    currentTime = 10
    outputLatency = 0.02
    baseLatency = 0.01
    destination = new FakeNode()
    closed = 0
    sources: FakeSource[] = []
    gains: FakeGain[] = []
    constructor() {
      contexts.push(this)
    }
    resume() {
      return options.resume ? options.resume() : Promise.resolve()
    }
    close() {
      this.closed++
      this.state = 'closed'
      return Promise.resolve()
    }
    decodeAudioData(_data: ArrayBuffer, ok?: (b: unknown) => void, fail?: (e: unknown) => void) {
      if (options.decode === 'error') {
        const error = new Error('EncodingError')
        fail?.(error)
        return Promise.reject(error)
      }
      const buffer = { duration: 2.2 }
      ok?.(buffer)
      return Promise.resolve(buffer)
    }
    createBufferSource() {
      const s = new FakeSource()
      this.sources.push(s)
      return s
    }
    createGain() {
      const g = new FakeGain()
      this.gains.push(g)
      return g
    }
  }
  return { Ctor: FakeContext as unknown as new () => AudioContext, contexts }
}

const okFetch = vi.fn(async () => ({ ok: true, status: 200, arrayBuffer: async () => new ArrayBuffer(8) }) as unknown as Response)

describe('Start-Klang abspielen', () => {
  afterEach(() => {
    vi.useRealTimers()
    okFetch.mockClear()
  })

  it('Quelle → Gain (genau 0,3) → Ausgang, mit dem übergebenen Versatz; am Ende wird geschlossen', async () => {
    const { Ctor, contexts } = fakeAudio()
    const sound = await prepareStartSound({ AudioContext: Ctor, fetch: okFetch })
    expect(sound).not.toBeNull()
    expect(okFetch).toHaveBeenCalledWith(START_SOUND_URL)
    expect(sound!.latency).toBe(0.02)
    sound!.start(0.07)
    const ctx = contexts[0]! as unknown as { sources: FakeSource[]; gains: FakeGain[]; destination: FakeNode; closed: number }
    const [source] = ctx.sources
    const [gain] = ctx.gains
    expect(START_SOUND_VOLUME).toBe(0.3)
    expect(gain!.gain.value).toBe(0.3)
    expect(source!.connected).toEqual([gain])
    expect(gain!.connected).toEqual([ctx.destination])
    expect(source!.started).toEqual([0, 0.07])
    // Nur einmal starten.
    sound!.start(0)
    expect(ctx.sources).toHaveLength(1)
    expect(ctx.closed).toBe(0)
    source!.onended?.()
    expect(ctx.closed).toBe(1)
    sound!.close()
    expect(ctx.closed).toBe(1)
  })

  it('Ausblenden: Rampe auf 0 in 0,38 s, dann Stopp und Schluss', async () => {
    vi.useFakeTimers()
    const { Ctor, contexts } = fakeAudio()
    const sound = (await prepareStartSound({ AudioContext: Ctor, fetch: okFetch }))!
    sound.start(0)
    const ctx = contexts[0]! as unknown as { sources: FakeSource[]; gains: FakeGain[]; closed: number }
    sound.fadeOut(380)
    expect(ctx.gains[0]!.gain.calls).toEqual([
      ['cancel', 10],
      ['set', 0.3, 10],
      ['ramp', 0, 10.38],
    ])
    expect(ctx.sources[0]!.stopped).toEqual([10.38])
    expect(ctx.closed).toBe(0)
    // `ended` kommt nach dem Stopp …
    ctx.sources[0]!.onended?.()
    expect(ctx.closed).toBe(1)
    // … und falls nicht, schließt spätestens der Zeitgeber (nie doppelt).
    vi.advanceTimersByTime(1000)
    expect(ctx.closed).toBe(1)
  })

  it('Ausblenden ohne `ended`: Zeitgeber schließt kurz nach der Rampe', async () => {
    vi.useFakeTimers()
    const { Ctor, contexts } = fakeAudio()
    const sound = (await prepareStartSound({ AudioContext: Ctor, fetch: okFetch }))!
    sound.start(0)
    sound.fadeOut(380)
    const ctx = contexts[0]! as unknown as { closed: number }
    vi.advanceTimersByTime(470)
    expect(ctx.closed).toBe(0)
    vi.advanceTimersByTime(20)
    expect(ctx.closed).toBe(1)
  })

  it('Ausblenden vor dem Start schließt sofort', async () => {
    const { Ctor, contexts } = fakeAudio()
    const sound = (await prepareStartSound({ AudioContext: Ctor, fetch: okFetch }))!
    sound.fadeOut(380)
    expect((contexts[0]! as unknown as { closed: number }).closed).toBe(1)
    sound.start(0)
    expect((contexts[0]! as unknown as { sources: FakeSource[] }).sources).toHaveLength(0)
  })

  it('ein Kontext, der nicht anläuft, gibt nach 150 ms null und wird geschlossen (nie auf einen Klick warten)', async () => {
    vi.useFakeTimers()
    const { Ctor, contexts } = fakeAudio({ state: 'suspended', resume: () => new Promise(() => {}) })
    let result: unknown = 'offen'
    void prepareStartSound({ AudioContext: Ctor, fetch: okFetch }).then((s) => (result = s))
    await vi.advanceTimersByTimeAsync(149)
    expect(result).toBe('offen')
    await vi.advanceTimersByTimeAsync(1)
    expect(result).toBeNull()
    expect((contexts[0]! as unknown as { closed: number }).closed).toBe(1)
  })

  it('läuft der Kontext nach resume(), gibt es Ton', async () => {
    const holder: { ctx?: { state: AudioContextState } } = {}
    const { Ctor, contexts } = fakeAudio({
      state: 'suspended',
      resume: async () => {
        holder.ctx!.state = 'running'
      },
    })
    const Wrapped = function () {
      const ctx = new Ctor()
      holder.ctx = ctx as unknown as { state: AudioContextState }
      return ctx
    } as unknown as new () => AudioContext
    const sound = await prepareStartSound({ AudioContext: Wrapped, fetch: okFetch })
    expect(sound).not.toBeNull()
    expect((contexts[0]! as unknown as { closed: number }).closed).toBe(0)
  })

  it('ohne Audio, bei Fehlern im Konstruktor, beim Laden oder Dekodieren: still null, ohne Ausnahme', async () => {
    expect(await prepareStartSound({ AudioContext: null, fetch: okFetch })).toBeNull()
    const Throws = function () {
      throw new Error('NotAllowedError')
    } as unknown as new () => AudioContext
    expect(await prepareStartSound({ AudioContext: Throws, fetch: okFetch })).toBeNull()

    const missing = fakeAudio()
    const notFound = vi.fn(async () => ({ ok: false, status: 404, arrayBuffer: async () => new ArrayBuffer(0) }) as unknown as Response)
    expect(await prepareStartSound({ AudioContext: missing.Ctor, fetch: notFound })).toBeNull()
    expect((missing.contexts[0]! as unknown as { closed: number }).closed).toBe(1)

    const offline = fakeAudio()
    const rejects = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    expect(await prepareStartSound({ AudioContext: offline.Ctor, fetch: rejects as unknown as typeof fetch })).toBeNull()
    expect((offline.contexts[0]! as unknown as { closed: number }).closed).toBe(1)

    const broken = fakeAudio({ decode: 'error' })
    expect(await prepareStartSound({ AudioContext: broken.Ctor, fetch: okFetch })).toBeNull()
    expect((broken.contexts[0]! as unknown as { closed: number }).closed).toBe(1)
  })

  it('Hörprobe in den Einstellungen: spielt von vorn; schnell erneut → nur die letzte spielt', async () => {
    const { Ctor, contexts } = fakeAudio()
    await previewStartSound({ AudioContext: Ctor, fetch: okFetch })
    const first = contexts[0]! as unknown as { sources: FakeSource[]; closed: number }
    expect(first.sources[0]!.started).toEqual([0, 0])
    const a = previewStartSound({ AudioContext: Ctor, fetch: okFetch })
    const b = previewStartSound({ AudioContext: Ctor, fetch: okFetch })
    await Promise.all([a, b])
    // Die erste Hörprobe wird ausgeblendet (Stopp nach der kurzen Rampe), die zweite gar nicht erst gestartet.
    expect(first.sources[0]!.stopped).toHaveLength(1)
    const second = contexts[1]! as unknown as { sources: FakeSource[]; closed: number }
    const third = contexts[2]! as unknown as { sources: FakeSource[]; closed: number }
    expect(second.sources).toHaveLength(0)
    expect(second.closed).toBe(1)
    expect(third.sources[0]!.started).toEqual([0, 0])
  })
})
