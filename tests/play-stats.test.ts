import { describe, expect, it } from 'vitest'
import {
  addLocalDays,
  playStreaks,
  localDayKey,
  heatmapGrid,
  lastDayBars,
  lastWeekBars,
  niceHours,
  splitByDay,
  localStartOfWeek,
  summarizePlayStats,
  weekStartFor,
  type PlaySession,
  type PlayStatsData,
} from '../app/utils/playStats'

// Alle Zeiten in Ortszeit gebaut – die Tests laufen so in jeder Zeitzone gleich.
const at = (y: number, m: number, d: number, h = 0, min = 0) => new Date(y, m - 1, d, h, min)
const sec = (date: Date) => Math.floor(date.getTime() / 1000)
const session = (start: Date, minutes: number, extra: Partial<PlaySession> = {}): PlaySession => ({
  instanceId: 'survival',
  gameVersion: '1.21.1',
  loader: 'fabric',
  start: sec(start),
  end: sec(start) + minutes * 60,
  ...extra,
})
const data = (sessions: PlaySession[], extra: Partial<PlayStatsData> = {}): PlayStatsData => ({
  sessions,
  running: [],
  carried: [],
  names: {},
  trackingSince: null,
  now: 0,
  ...extra,
})

describe('Tage und Wochen', () => {
  it('teilt Sitzungen an Mitternacht', () => {
    const parts = splitByDay(sec(at(2026, 10, 26, 23, 0)), sec(at(2026, 10, 27, 1, 30)))
    expect(parts).toEqual([
      ['2026-10-26', 3600],
      ['2026-10-27', 5400],
    ])
    expect(splitByDay(100, 50)).toEqual([])
  })

  it('beginnt Wochen am ersten Wochentag der Sprache', () => {
    // Dienstag, 27.10.2026
    const tuesday = at(2026, 10, 27, 15)
    expect(localDayKey(localStartOfWeek(tuesday, 1))).toBe('2026-10-26')
    expect(localDayKey(localStartOfWeek(tuesday, 0))).toBe('2026-10-25')
    expect(weekStartFor('de')).toBe(1)
    expect(weekStartFor('en')).toBe(0)
    expect(weekStartFor('pt-BR')).toBe(0)
    expect(weekStartFor('fr')).toBe(1)
    expect(weekStartFor('???')).toBe(1)
  })

  it('zählt Serien bis heute oder bis gestern', () => {
    const today = at(2026, 10, 27)
    const days = new Map<string, number>()
    for (const d of [-1, -2, -3, -10, -11]) days.set(localDayKey(addLocalDays(today, d)), 1800)
    days.set(localDayKey(addLocalDays(today, -5)), 30) // unter einer Minute zählt nicht
    expect(playStreaks(days, today)).toEqual({ current: 3, longest: 3 })
    days.set(localDayKey(today), 600)
    expect(playStreaks(days, today)).toEqual({ current: 4, longest: 4 })
    days.delete(localDayKey(addLocalDays(today, -1)))
    expect(playStreaks(days, today).current).toBe(1)
  })
})

describe('Zusammenfassung', () => {
  const now = at(2026, 10, 28, 20) // Mittwoch
  const stats = data(
    [
      session(at(2026, 9, 30, 18), 120, { gameVersion: '1.20.1', loader: 'forge', servers: ['play.example.net'] }),
      session(at(2026, 10, 26, 23, 30), 90, { servers: ['play.example.net', 'mc.example.org'] }), // über Mitternacht
      session(at(2026, 10, 27, 12), 30, { instanceId: 'kreativ', gameVersion: '1.21.4', loader: 'vanilla', crashed: true }),
      session(at(2026, 10, 28, 9), 0.5), // 30 s: Zeit zählt, Sitzung nicht
    ],
    { carried: [{ instanceId: 'survival', seconds: 36_000 }], running: [session(at(2026, 10, 28, 19), 0)] },
  )
  const s = summarizePlayStats(stats, { now, weekStart: 1 })

  it('rechnet Summen, Woche, Monat und heute', () => {
    const minutes = 120 + 90 + 30 + 0.5 + 60 // die laufende Sitzung zählt bis jetzt (1 h)
    expect(s.totalSeconds).toBe(minutes * 60 + 36_000)
    expect(s.carriedSeconds).toBe(36_000)
    expect(s.todaySeconds).toBe(30 + 3600)
    // Woche ab Montag 26.10. 23:30 – also 90 min + 30 min + heute
    expect(s.weekSeconds).toBe((90 + 30) * 60 + 30 + 3600)
    expect(s.monthSeconds).toBe(s.weekSeconds)
    expect(s.live).toBe(true)
  })

  it('zählt Sitzungen, Abstürze, Rekord und Tage', () => {
    expect(s.sessionCount).toBe(4)
    expect(s.crashes).toBe(1)
    expect(s.longest).toEqual({ seconds: 7200, start: sec(at(2026, 9, 30, 18)), instanceId: 'survival' })
    expect(s.days.get('2026-10-26')).toBe(1800)
    expect(s.days.get('2026-10-27')).toBe(3600 + 1800)
    expect(s.daysPlayed).toBe(4)
    expect(s.currentStreak).toBe(3)
    expect(s.firstDay).toBe('2026-09-30')
  })

  it('sortiert Instanzen, Versionen und Server', () => {
    expect(s.instances.map((r) => r.key)).toEqual(['survival', 'kreativ'])
    expect(s.instances[0]!.seconds).toBe(36_000 + (120 + 90 + 0.5 + 60) * 60)
    expect(s.versions.map((r) => [r.key, r.seconds])).toEqual([
      ['1.21.1', (90 + 0.5 + 60) * 60],
      ['1.20.1', 7200],
      ['1.21.4', 1800],
    ])
    expect(s.loaders[0]!.key).toBe('fabric')
    // Zwei Server in einer Sitzung teilen sich deren Zeit.
    expect(s.servers.map((r) => [r.key, r.seconds, r.sessions])).toEqual([
      ['play.example.net', 7200 + 2700, 2],
      ['mc.example.org', 2700, 1],
    ])
    expect(s.versions.reduce((sum, r) => sum + r.share, 0)).toBeCloseTo(1)
  })

  it('filtert nach Instanz', () => {
    const only = summarizePlayStats(stats, { now, weekStart: 1, instanceId: 'kreativ' })
    expect(only.totalSeconds).toBe(1800)
    expect(only.carriedSeconds).toBe(0)
    expect(only.instances.map((r) => r.key)).toEqual(['kreativ'])
    expect(only.live).toBe(false)
  })

  it('ist leer ohne Sitzungen', () => {
    const empty = summarizePlayStats(data([]), { now })
    expect([empty.totalSeconds, empty.sessionCount, empty.currentStreak, empty.longestStreak]).toEqual([0, 0, 0, 0])
    expect(empty.longest).toBeNull()
    expect(empty.firstDay).toBeNull()
  })
})

describe('Diagramme', () => {
  const now = at(2026, 10, 28, 12)
  const days = new Map([
    ['2026-10-28', 600],
    ['2026-10-27', 3600],
    ['2026-10-19', 7200],
  ])

  it('liefert die letzten Tage und Wochen', () => {
    const d = lastDayBars(days, 30, now)
    expect(d).toHaveLength(30)
    expect(d.at(-1)).toMatchObject({ day: '2026-10-28', seconds: 600 })
    expect(d[0]!.day).toBe('2026-09-29')
    const w = lastWeekBars(days, 12, 1, now)
    expect(w).toHaveLength(12)
    expect(w.at(-1)).toMatchObject({ day: '2026-10-26', seconds: 4200 })
    expect(w.at(-2)!.seconds).toBe(7200)
  })

  it('baut den Kalender mit Stufen und ohne Zukunft', () => {
    const { columns, thresholds } = heatmapGrid(days, 26, 1, now)
    expect(columns).toHaveLength(26)
    expect(columns.every((c) => c.length === 7)).toBe(true)
    const last = columns.at(-1)!
    expect(last[0]!.day).toBe('2026-10-26')
    expect(last.slice(3).every((c) => c.future && c.level === 0)).toBe(true)
    expect(thresholds).toHaveLength(3)
    const cell = (day: string) => columns.flat().find((c) => c.day === day)!
    expect(cell('2026-10-19').level).toBe(4)
    expect(cell('2026-10-28').level).toBe(1)
    expect(cell('2026-10-20').level).toBe(0)
  })

  it('wählt runde Achsen', () => {
    expect(niceHours(0)).toBe(3600)
    expect(niceHours(1000)).toBe(1800)
    expect(niceHours(5 * 3600)).toBe(6 * 3600)
    expect(niceHours(30 * 24 * 3600)).toBe(30 * 24 * 3600)
  })
})
