import { describe, expect, it } from 'vitest'
import {
  PERF_HISTORY,
  gamePerfSchema,
  perfCpuPercent,
  perfPoint,
  perfStats,
  perfTips,
  pushPerfPoint,
  sparkPath,
  type GamePerf,
  type PerfPoint,
} from '../app/utils/perfLive'

// Live-Leistungsanzeige (PerfLivePanel.vue): Auslastung aus zwei Messungen des Kerns, Kurven und Tipps.

function sample(over: Partial<GamePerf> = {}): GamePerf {
  return {
    atMs: 10_000,
    cpuMs: 5000,
    ramBytes: 3 * 1024 ** 3,
    cores: 8,
    systemTotalMb: 16_000,
    systemFreeMb: 7000,
    client: true,
    fps: 120,
    fpsLimited: false,
    heapUsedMb: 1500,
    heapMaxMb: 4096,
    ...over,
  }
}

function point(over: Partial<PerfPoint> = {}): PerfPoint {
  return { at: 0, cpu: 20, ramMb: 3000, fps: 120, fpsLimited: false, heapUsedMb: 1500, heapMaxMb: 4096, systemFreeMb: 7000, systemTotalMb: 16_000, ...over }
}

const many = (n: number, over: (i: number) => Partial<PerfPoint>) => Array.from({ length: n }, (_, i) => point({ at: i * 1000, ...over(i) }))

describe('Live-Leistung: Messungen', () => {
  it('passt zur Antwort des Kerns (game_perf)', () => {
    expect(gamePerfSchema.parse(sample())).toEqual(sample())
    expect(gamePerfSchema.nullable().parse(null)).toBeNull()
    // Ohne Cubiq Client fehlen FPS und Java-Speicher.
    const plain = sample({ client: false, fps: null, heapUsedMb: null, heapMaxMb: null })
    expect(gamePerfSchema.parse(plain).fps).toBeNull()
    expect(() => gamePerfSchema.parse({ ...sample(), cores: 'acht' })).toThrow()
  })

  it('rechnet die CPU-Auslastung in % des ganzen PCs', () => {
    const a = sample({ atMs: 10_000, cpuMs: 5000, cores: 8 })
    // 1 s Wanduhr, 4 s CPU-Zeit auf 8 Threads = 50 %.
    expect(perfCpuPercent(a, sample({ atMs: 11_000, cpuMs: 9000, cores: 8 }))).toBeCloseTo(50)
    // Begrenzt auf 0–100.
    expect(perfCpuPercent(a, sample({ atMs: 11_000, cpuMs: 99_000 }))).toBe(100)
    // Ohne Vorgänger, zu kurz, rückwärts (anderer Prozess) oder ohne CPU-Zeit: keine Angabe.
    expect(perfCpuPercent(null, a)).toBeNull()
    expect(perfCpuPercent(a, sample({ atMs: 10_100, cpuMs: 5100 }))).toBeNull()
    expect(perfCpuPercent(a, sample({ atMs: 11_000, cpuMs: 100 }))).toBeNull()
    expect(perfCpuPercent(a, sample({ atMs: 11_000, cpuMs: null }))).toBeNull()
  })

  it('macht aus Messungen Punkte und hält eine Minute', () => {
    const p = perfPoint(sample({ atMs: 0, cpuMs: 0 }), sample({ atMs: 1000, cpuMs: 800, fpsLimited: true }))
    expect(p.ramMb).toBe(3072)
    expect(p.cpu).toBeCloseTo(10)
    expect(p.fpsLimited).toBe(true)
    expect(perfPoint(null, sample({ ramBytes: null })).ramMb).toBeNull()
    const list: PerfPoint[] = []
    for (let i = 0; i < 75; i++) pushPerfPoint(list, point({ at: i }))
    expect(list).toHaveLength(PERF_HISTORY)
    expect(list[0]!.at).toBe(15)
  })

  it('fasst Kurven zusammen (ohne Lücken)', () => {
    expect(perfStats([10, null, 30, 20])).toEqual({ current: 20, avg: 20, min: 10, max: 30 })
    expect(perfStats([10, null])).toEqual({ current: null, avg: 10, min: 10, max: 10 })
    expect(perfStats([])).toEqual({ current: null, avg: null, min: null, max: null })
  })

  it('zeichnet Kurven rechtsbündig mit Lücken', () => {
    expect(sparkPath([], 120, 34, 100)).toBe('')
    expect(sparkPath([50], 120, 34, 100)).toBe('')
    // Zwei Punkte ganz rechts (60 Plätze), halbe Höhe bei 50 von 100.
    const path = sparkPath([50, 100], 120, 34, 100, 60)
    expect(path.startsWith('M')).toBe(true)
    expect(path).toContain('L120.0 1.0')
    expect(path).toContain('118.0 17.0')
    // Lücke: neue Linie statt einer Verbindung.
    expect(sparkPath([10, 20, null, 30, 40], 120, 34, 50, 5).match(/M/g)).toHaveLength(2)
  })
})

describe('Live-Leistung: Tipps', () => {
  it('schweigt, solange alles gut läuft (und bei zu wenigen Werten)', () => {
    expect(perfTips(many(20, () => ({})))).toEqual([])
    expect(perfTips(many(3, () => ({ heapUsedMb: 4000, fps: 10, cpu: 99 })))).toEqual([])
  })

  it('rät zu mehr Speicher, wenn der Java-Speicher fast voll bleibt', () => {
    expect(perfTips(many(12, () => ({ heapUsedMb: 3900 })))).toEqual(['memory'])
    // Kurz voll und wieder leer (Garbage Collector) zählt nicht.
    expect(perfTips(many(12, (i) => ({ heapUsedMb: i % 2 ? 3900 : 1500 })))).toEqual([])
    // Ohne Cubiq Client gibt es keinen Java-Speicher – kein Tipp.
    expect(perfTips(many(12, () => ({ heapUsedMb: null, heapMaxMb: null })))).toEqual([])
  })

  it('rät zum Aufräumen, wenn der PC selbst kaum noch Speicher hat', () => {
    expect(perfTips(many(12, () => ({ systemFreeMb: 400, systemTotalMb: 8000 })))).toEqual(['systemMemory'])
    // Viel RAM eingebaut: 600 MB frei von 32 GB sind knapp, 2 GB frei nicht.
    expect(perfTips(many(12, () => ({ systemFreeMb: 2000, systemTotalMb: 32_000 })))).toEqual([])
  })

  it('erkennt niedrige Bildraten und Ruckler – gebremste Bilder zählen nicht', () => {
    expect(perfTips(many(12, () => ({ fps: 22 })))).toEqual(['lowFps'])
    expect(perfTips(many(12, () => ({ fps: 8, fpsLimited: true })))).toEqual([])
    const stutter = many(15, (i) => ({ fps: i % 5 === 2 ? 30 : 140 }))
    expect(perfTips(stutter)).toEqual(['stutter'])
    // Ohne Cubiq Client gibt es keine Bildrate – keine FPS-Tipps.
    expect(perfTips(many(15, () => ({ fps: null })))).toEqual([])
  })

  it('meldet einen ausgelasteten Prozessor und höchstens zwei Tipps, wichtigster zuerst', () => {
    expect(perfTips(many(12, () => ({ cpu: 96 })))).toEqual(['cpu'])
    expect(perfTips(many(15, () => ({ heapUsedMb: 3950, systemFreeMb: 300, systemTotalMb: 8000, fps: 15, cpu: 99 })))).toEqual(['memory', 'systemMemory'])
  })
})
