import { describe, expect, it } from 'vitest'
import {
  CUBI_X,
  HOP_BEST_KEY,
  HOP_GROUND,
  HOP_RESTART_MS,
  HOP_W,
  createHop,
  cubiBox,
  gameWindowShown,
  hopAutopilot,
  hopInput,
  hopSpeed,
  pauseHop,
  readHopBest,
  resumeHop,
  stepHop,
  writeHopBest,
  type HopEvent,
  type HopState,
} from '../app/utils/miniGame'
import { HOP_COLORS, HOP_OUTLINE, HOP_OUTLINED, HOP_SPRITES, rasterizeHop } from '../app/utils/miniGameArt'

// „Cubi Hop“, das kleine Spiel beim Spielstart (LaunchMiniGame.vue): Regeln, Rekord je Spieler, „Fenster da?“.

/** `seconds` lang in 1/60-s-Schritten laufen lassen; `autopilot` springt wie die Vorführung. */
function run(state: HopState, seconds: number, autopilot = false): HopEvent[] {
  const events: HopEvent[] = []
  for (let t = 0; t < seconds && state.phase === 'running'; t += 1 / 60) {
    if (autopilot && hopAutopilot(state)) hopInput(state)
    events.push(...stepHop(state, 1 / 60))
  }
  return events
}

function memoryStorage(initial: Record<string, string> = {}) {
  const data = new Map(Object.entries(initial))
  return {
    data,
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => void data.set(k, v),
  }
}

describe('Cubi Hop: Regeln', () => {
  it('beginnt still und startet erst auf eine Eingabe (mit Sprung)', () => {
    const s = createHop(7, 12)
    expect(s.phase).toBe('ready')
    expect(stepHop(s, 1)).toEqual([])
    expect(s.time).toBe(0)
    expect(s.best).toBe(12)
    expect(hopInput(s)).toBe('start')
    expect(s.phase).toBe('running')
    expect(s.rise).toBeGreaterThan(0)
  })

  it('springt gut 36 Bildpunkte hoch, landet nach ~0,7 s und kann einmal in der Luft nachspringen', () => {
    const s = createHop(1)
    hopInput(s)
    let peak = 0
    let airborne = 0
    for (let i = 0; i < 120 && (i === 0 || s.lift > 0); i++) {
      stepHop(s, 1 / 60)
      peak = Math.max(peak, s.lift)
      airborne++
    }
    expect(peak).toBeGreaterThan(34)
    expect(peak).toBeLessThan(40)
    expect(airborne / 60).toBeGreaterThan(0.6)
    expect(airborne / 60).toBeLessThan(0.8)
    expect(s.lift).toBe(0)
    // Doppelsprung: einmal je Sprung.
    expect(hopInput(s)).toBe('jump')
    stepHop(s, 0.1)
    expect(hopInput(s)).toBe('double')
    expect(hopInput(s)).toBeNull()
  })

  it('lässt Blöcke heranrollen und endet beim Zusammenstoß', () => {
    const s = createHop(3)
    hopInput(s)
    run(s, 0.8)
    // Kein Sprung mehr: Der erste Block trifft Cubi.
    const events = run(s, 10)
    expect(events).toContain('hit')
    expect(s.phase).toBe('over')
    expect(s.overAt).not.toBeNull()
    const o = s.obstacles.find((x) => x.x < CUBI_X + 16)!
    const [cx, cy, cw, ch] = cubiBox(s)
    expect(o.x < cx + cw && o.x + o.w > cx && HOP_GROUND - o.h < cy + ch).toBe(true)
    // Neustart erst nach der Sperre.
    expect(hopInput(s)).toBeNull()
    stepHop(s, HOP_RESTART_MS / 1000 + 0.01)
    expect(hopInput(s)).toBe('start')
    expect(s.score).toBe(0)
    expect(s.obstacles).toEqual([])
  })

  it('ist zu schaffen: die Vorführung kommt über 40 Blöcke, wird schneller und schlägt den Rekord', () => {
    const s = createHop(42, 5)
    hopInput(s)
    const events = run(s, 120, true)
    expect(events).not.toContain('hit')
    expect(s.phase).toBe('running')
    const scored = events.filter((e) => e === 'score').length
    expect(scored).toBeGreaterThan(40)
    expect(s.score).toBeGreaterThanOrEqual(scored)
    expect(s.speed).toBe(hopSpeed(s.score))
    expect(s.speed).toBeGreaterThan(100)
    expect(s.best).toBe(s.score)
    expect(s.newBest).toBe(true)
    // Später kommen auch hohe und breite Hindernisse.
    expect(s.obstacles.every((o) => o.x > -10 && o.x < HOP_W + 10)).toBe(true)
  })

  it('ist mit gleichem Startwert gleich und mit anderem anders', () => {
    const a = createHop(9)
    const b = createHop(9)
    const c = createHop(10)
    for (const s of [a, b, c]) {
      hopInput(s)
      run(s, 30, true)
    }
    expect(a).toEqual(b)
    expect(a.score === c.score && a.scroll === c.scroll && JSON.stringify(a.obstacles) === JSON.stringify(c.obstacles)).toBe(false)
  })

  it('hält in der Pause an und holt nach langem Hänger nicht alles nach', () => {
    const s = createHop(5)
    hopInput(s)
    run(s, 0.5)
    const time = s.time
    pauseHop(s)
    expect(s.phase).toBe('paused')
    expect(stepHop(s, 5)).toEqual([])
    expect(s.time).toBe(time)
    // Eine Eingabe in der Pause setzt nur fort (kein Sprung).
    const rise = s.rise
    expect(hopInput(s)).toBeNull()
    expect(s.phase).toBe('running')
    expect(s.rise).toBe(rise)
    pauseHop(s)
    resumeHop(s)
    expect(s.phase).toBe('running')
    stepHop(s, 30)
    expect(s.time - time).toBeLessThanOrEqual(0.1 + 1e-9)
  })

  it('wird mit Punkten schneller, aber nie schneller als der Höchstwert', () => {
    expect(hopSpeed(0)).toBe(70)
    expect(hopSpeed(4)).toBe(80)
    expect(hopSpeed(1000)).toBe(150)
  })
})

describe('Cubi Hop: Rekord je Spieler', () => {
  it('merkt sich den besten Wert je Konto', () => {
    const storage = memoryStorage()
    expect(readHopBest(storage, 'alex')).toBe(0)
    expect(writeHopBest(storage, 'alex', 7)).toBe(true)
    expect(writeHopBest(storage, 'alex', 5)).toBe(false)
    expect(writeHopBest(storage, 'steve', 3)).toBe(true)
    expect(readHopBest(storage, 'alex')).toBe(7)
    expect(readHopBest(storage, 'steve')).toBe(3)
    expect(JSON.parse(storage.data.get(HOP_BEST_KEY)!)).toEqual({ alex: 7, steve: 3 })
  })

  it('übersteht kaputte Daten, fehlenden Speicher und Fehler beim Schreiben', () => {
    expect(readHopBest(memoryStorage({ [HOP_BEST_KEY]: '{kaputt' }), 'a')).toBe(0)
    expect(readHopBest(memoryStorage({ [HOP_BEST_KEY]: '[1,2]' }), 'a')).toBe(0)
    expect(readHopBest(memoryStorage({ [HOP_BEST_KEY]: '{"a":"9","b":-3,"c":4.7}' }), 'a')).toBe(0)
    expect(readHopBest(memoryStorage({ [HOP_BEST_KEY]: '{"a":"9","b":-3,"c":4.7}' }), 'c')).toBe(4)
    expect(readHopBest(null, 'a')).toBe(0)
    expect(writeHopBest(null, 'a', 3)).toBe(false)
    const throwing = {
      getItem: () => {
        throw new Error('gesperrt')
      },
      setItem: () => {
        throw new Error('voll')
      },
    }
    expect(readHopBest(throwing, 'a')).toBe(0)
    expect(writeHopBest(throwing, 'a', 3)).toBe(false)
    expect(writeHopBest(memoryStorage(), 'a', 0)).toBe(false)
  })

  it('hält höchstens 20 Konten (die ältesten fallen weg)', () => {
    const storage = memoryStorage()
    for (let i = 0; i < 25; i++) writeHopBest(storage, `u${i}`, i + 1)
    const all = JSON.parse(storage.data.get(HOP_BEST_KEY)!)
    expect(Object.keys(all)).toHaveLength(20)
    expect(all.u0).toBeUndefined()
    expect(all.u24).toBe(25)
  })
})

describe('Cubi Hop: Spielfenster da?', () => {
  it('erkennt das Minecraft-Fenster an typischen Log-Zeilen', () => {
    expect(gameWindowShown([{ message: 'Setting user: Alex' }, { message: 'Backend library: LWJGL version 3.3.3+5' }])).toBe(true)
    expect(gameWindowShown([{ message: 'LWJGL Version: 2.9.4' }])).toBe(true)
    expect(gameWindowShown([{ message: 'Loading ImmediateWindowProvider fmlearlywindow' }])).toBe(true)
    expect(gameWindowShown([{ message: 'Sound engine started' }])).toBe(true)
    expect(gameWindowShown([{ message: 'Loading 87 mods' }, { message: 'Environment: Environment[...]' }])).toBe(false)
    expect(gameWindowShown([])).toBe(false)
  })
})

describe('Cubi Hop: Pixel-Art', () => {
  it('hat gleich breite Zeilen und nur bekannte Farben', () => {
    for (const [name, rows] of Object.entries(HOP_SPRITES)) {
      const width = rows[0]!.length
      for (const row of rows) expect(row.length, name).toBe(width)
      for (const ch of rows.join('')) if (ch !== '.') expect(HOP_COLORS[ch], `${name}: ${ch}`).toBeDefined()
    }
    // Cubi: 16 × 16 (Pflänzchen, Körper, Füße) wie im Release-Bild.
    expect(HOP_SPRITES.idle).toHaveLength(16)
    expect(HOP_SPRITES.ground[0]).toHaveLength(32)
  })

  it('zeichnet mit Umriss ringsum einen Bildpunkt größer', () => {
    const plain = rasterizeHop(HOP_SPRITES.grass)
    expect([plain.w, plain.h]).toEqual([10, 10])
    const outlined = rasterizeHop(HOP_SPRITES.grass, HOP_OUTLINE)
    expect([outlined.w, outlined.h]).toEqual([12, 12])
    // Ecke bleibt frei (4er-Nachbarn), Kante bekommt den Umriss.
    expect(outlined.data[3]).toBe(0)
    expect([...outlined.data.subarray(4, 8)]).toEqual([0x14, 0x1b, 0x18, 255])
    expect(HOP_OUTLINED.has('idle')).toBe(true)
    expect(HOP_OUTLINED.has('ground')).toBe(false)
    expect(() => rasterizeHop(['xZ'])).toThrow()
  })
})
