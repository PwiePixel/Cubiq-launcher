// Spielzeit-Statistik: Rechnungen über die Sitzungen aus dem Kern (`play_stats`).
// Alles nach Ortszeit – Tage beginnen um Mitternacht des Rechners, Wochen am ersten Wochentag der
// eingestellten Sprache. Getestet in tests/play-stats.test.ts (ohne Nuxt).

/** Eine Sitzung; Zeiten in Unix-Sekunden. */
export interface PlaySession {
  instanceId: string
  gameVersion: string
  /** `vanilla`, `fabric`, …; leer = unbekannt. */
  loader: string
  start: number
  end: number
  servers?: string[]
  crashed?: boolean
  /** Ende geschätzt (der Launcher lief beim Spielende nicht). */
  estimated?: boolean
  /** Aus dem Verlauf der Instanz übernommen. */
  imported?: boolean
}

export interface PlayStatsData {
  sessions: PlaySession[]
  running: PlaySession[]
  /** Spielzeit ohne Datum (vor der Statistik). */
  carried: { instanceId: string; seconds: number }[]
  /** Letzter bekannter Name je Instanz (auch gelöschter). */
  names: Record<string, string>
  trackingSince: number | null
  now: number
}

/** Ab so viel Spielzeit zählt ein Tag als „gespielt“ (Serien, Kalender). */
export const MIN_DAY_SECONDS = 60
/** Kürzere Sitzungen (Absturz beim Laden) zählen nicht als Sitzung – ihre Zeit schon. */
export const MIN_SESSION_SECONDS = 60

/** Datum als `JJJJ-MM-TT` in Ortszeit. */
export function localDayKey(date: Date): string {
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${m}-${d}`
}

/** Mitternacht (Ortszeit) des Tages von `date`. */
export function localStartOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

/** `n` Tage weiter (über Sommer-/Winterzeit hinweg kalendarisch, nicht 24 h). */
export function addLocalDays(date: Date, n: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + n)
}

/** Erster Tag der Woche, in der `date` liegt (`weekStart`: 0 = Sonntag, 1 = Montag …). */
export function localStartOfWeek(date: Date, weekStart: number): Date {
  const day = localStartOfDay(date)
  const back = (day.getDay() - weekStart + 7) % 7
  return addLocalDays(day, -back)
}

/**
 * Erster Wochentag einer Sprache (0 = Sonntag, 1 = Montag, 6 = Samstag). Nimmt die Angabe aus
 * `Intl.Locale` (getWeekInfo/weekInfo), sonst: USA und Brasilien Sonntag, sonst Montag.
 */
export function weekStartFor(locale: string): number {
  try {
    const loc = new Intl.Locale(locale).maximize() as Intl.Locale & {
      getWeekInfo?: () => { firstDay: number }
      weekInfo?: { firstDay: number }
    }
    const first = loc.getWeekInfo?.().firstDay ?? loc.weekInfo?.firstDay
    if (typeof first === 'number' && first >= 1 && first <= 7) return first % 7
    const region = loc.region ?? ''
    return ['US', 'BR', 'CA', 'JP', 'MX', 'IL'].includes(region) ? 0 : 1
  } catch {
    return 1
  }
}

/** Sitzung an Mitternacht (Ortszeit) teilen: `[Tag, Sekunden]` je berührtem Tag. */
export function splitByDay(startSec: number, endSec: number): [string, number][] {
  const out: [string, number][] = []
  let from = startSec * 1000
  const to = Math.max(from, endSec * 1000)
  // Schutz gegen absurde Spannen (verstellte Uhr): höchstens ein Jahr.
  let guard = 0
  while (from < to && guard++ < 400) {
    const day = localStartOfDay(new Date(from))
    const next = addLocalDays(day, 1).getTime()
    const until = Math.min(next, to)
    out.push([localDayKey(day), Math.round((until - from) / 1000)])
    from = until
  }
  return out
}

export interface StatsOptions {
  /** Nur diese Instanz. */
  instanceId?: string | null
  /** „Jetzt“ (Tests); sonst die Uhr. */
  now?: Date
  /** Erster Wochentag (0 = Sonntag, 1 = Montag). */
  weekStart?: number
}

export interface RankedTime {
  key: string
  seconds: number
  sessions: number
  /** Anteil an der Summe dieser Liste (0–1). */
  share: number
}

export interface LongestSession {
  seconds: number
  start: number
  instanceId: string
}

export interface StatsSummary {
  /** Alles, auch Spielzeit ohne Datum. */
  totalSeconds: number
  /** Davon ohne Datum (vor der Statistik). */
  carriedSeconds: number
  todaySeconds: number
  weekSeconds: number
  monthSeconds: number
  /** Sitzungen ab [MIN_SESSION_SECONDS]. */
  sessionCount: number
  averageSeconds: number
  longest: LongestSession | null
  /** Tage in Folge bis heute (oder gestern, solange heute noch nicht gespielt wurde). */
  currentStreak: number
  longestStreak: number
  daysPlayed: number
  crashes: number
  /** Spielzeit je Tag (`JJJJ-MM-TT` → Sekunden). */
  days: Map<string, number>
  instances: RankedTime[]
  versions: RankedTime[]
  loaders: RankedTime[]
  servers: RankedTime[]
  /** Erster Tag mit Spielzeit. */
  firstDay: string | null
  /** Läuft gerade ein Spiel (zählt bis „jetzt“ mit)? */
  live: boolean
}

function rank(map: Map<string, { seconds: number; sessions: number }>): RankedTime[] {
  const total = [...map.values()].reduce((sum, v) => sum + v.seconds, 0) || 1
  return [...map]
    .map(([key, v]) => ({ key, seconds: v.seconds, sessions: v.sessions, share: v.seconds / total }))
    .filter((r) => r.seconds > 0)
    .sort((a, b) => b.seconds - a.seconds || a.key.localeCompare(b.key))
}

function bump(map: Map<string, { seconds: number; sessions: number }>, key: string, seconds: number, counted: boolean) {
  const entry = map.get(key) ?? { seconds: 0, sessions: 0 }
  entry.seconds += seconds
  if (counted) entry.sessions++
  map.set(key, entry)
}

/** Alle Kennzahlen für die Seite „Statistik“ bzw. die Übersicht einer Instanz. */
export function summarizePlayStats(data: PlayStatsData, options: StatsOptions = {}): StatsSummary {
  const now = options.now ?? new Date()
  const weekStart = options.weekStart ?? 1
  const only = options.instanceId ?? null
  const pick = (s: { instanceId: string }) => !only || s.instanceId === only
  const running = data.running.filter(pick).map((s) => ({ ...s, end: Math.max(s.start, Math.floor(now.getTime() / 1000)) }))
  const sessions = [...data.sessions.filter(pick), ...running]
  const carried = data.carried.filter(pick)

  const days = new Map<string, number>()
  const instances = new Map<string, { seconds: number; sessions: number }>()
  const versions = new Map<string, { seconds: number; sessions: number }>()
  const loaders = new Map<string, { seconds: number; sessions: number }>()
  const servers = new Map<string, { seconds: number; sessions: number }>()
  let longest: LongestSession | null = null
  let sessionCount = 0
  let datedSeconds = 0
  let crashes = 0

  for (const s of sessions) {
    const seconds = Math.max(0, s.end - s.start)
    if (!seconds) continue
    datedSeconds += seconds
    const counted = seconds >= MIN_SESSION_SECONDS
    if (counted) sessionCount++
    if (s.crashed) crashes++
    for (const [day, part] of splitByDay(s.start, s.end)) days.set(day, (days.get(day) ?? 0) + part)
    bump(instances, s.instanceId, seconds, counted)
    if (s.gameVersion) bump(versions, s.gameVersion, seconds, counted)
    if (s.loader) bump(loaders, s.loader, seconds, counted)
    const list = s.servers ?? []
    for (const server of list) bump(servers, server, seconds / list.length, counted)
    if (!longest || seconds > longest.seconds) longest = { seconds, start: s.start, instanceId: s.instanceId }
  }
  let carriedSeconds = 0
  for (const c of carried) {
    carriedSeconds += c.seconds
    bump(instances, c.instanceId, c.seconds, false)
  }

  const today = localStartOfDay(now)
  const sumSince = (from: Date) => {
    let total = 0
    for (let d = from; d.getTime() <= today.getTime(); d = addLocalDays(d, 1)) total += days.get(localDayKey(d)) ?? 0
    return total
  }
  const streaks = playStreaks(days, today)
  const playedDays = [...days].filter(([, s]) => s >= MIN_DAY_SECONDS).map(([d]) => d).sort()

  return {
    totalSeconds: datedSeconds + carriedSeconds,
    carriedSeconds,
    todaySeconds: days.get(localDayKey(today)) ?? 0,
    weekSeconds: sumSince(localStartOfWeek(today, weekStart)),
    monthSeconds: sumSince(new Date(today.getFullYear(), today.getMonth(), 1)),
    sessionCount,
    averageSeconds: sessionCount ? Math.round(sessions.reduce((sum, s) => sum + (s.end - s.start >= MIN_SESSION_SECONDS ? s.end - s.start : 0), 0) / sessionCount) : 0,
    longest,
    currentStreak: streaks.current,
    longestStreak: streaks.longest,
    daysPlayed: playedDays.length,
    crashes,
    days,
    instances: rank(instances),
    versions: rank(versions),
    loaders: rank(loaders),
    servers: rank(servers),
    firstDay: playedDays[0] ?? null,
    live: running.length > 0,
  }
}

/** Serien: Tage in Folge mit Spielzeit. Die aktuelle läuft bis heute – oder bis gestern, wenn heute noch nichts war. */
export function playStreaks(days: Map<string, number>, today: Date): { current: number; longest: number } {
  const played = (d: Date) => (days.get(localDayKey(d)) ?? 0) >= MIN_DAY_SECONDS
  let current = 0
  let cursor = played(today) ? today : addLocalDays(today, -1)
  while (played(cursor)) {
    current++
    cursor = addLocalDays(cursor, -1)
  }
  const sorted = [...days].filter(([, s]) => s >= MIN_DAY_SECONDS).map(([d]) => d).sort()
  let longest = 0
  let run = 0
  let prev: Date | null = null
  for (const key of sorted) {
    const [y, m, d] = key.split('-').map(Number) as [number, number, number]
    const date = new Date(y, m - 1, d)
    run = prev && localDayKey(addLocalDays(prev, 1)) === key ? run + 1 : 1
    longest = Math.max(longest, run)
    prev = date
  }
  return { current, longest: Math.max(longest, current) }
}

export interface DayBar {
  day: string
  date: Date
  seconds: number
}

/** Die letzten `n` Tage bis heute (älteste zuerst). */
export function lastDayBars(days: Map<string, number>, n: number, now: Date = new Date()): DayBar[] {
  const today = localStartOfDay(now)
  return Array.from({ length: n }, (_, i) => {
    const date = addLocalDays(today, i - n + 1)
    const day = localDayKey(date)
    return { day, date, seconds: days.get(day) ?? 0 }
  })
}

/** Die letzten `n` Wochen bis zur aktuellen (älteste zuerst); `date` = erster Tag der Woche. */
export function lastWeekBars(days: Map<string, number>, n: number, weekStart: number, now: Date = new Date()): DayBar[] {
  const current = localStartOfWeek(now, weekStart)
  return Array.from({ length: n }, (_, i) => {
    const date = addLocalDays(current, (i - n + 1) * 7)
    let seconds = 0
    for (let k = 0; k < 7; k++) seconds += days.get(localDayKey(addLocalDays(date, k))) ?? 0
    return { day: localDayKey(date), date, seconds }
  })
}

export interface HeatCell {
  day: string
  date: Date
  seconds: number
  /** 0 = nicht gespielt, 1–4 = Stufe (Quartile der gespielten Tage im Bild). */
  level: number
  /** Liegt nach heute (leer gezeichnet). */
  future: boolean
}

/**
 * Kalender der letzten `weeks` Wochen: Spalten = Wochen (älteste zuerst), Zeilen = Wochentage ab
 * `weekStart`. Stufen nach Quartilen der gespielten Tage im Bild – wie bei Beitragskalendern.
 */
export function heatmapGrid(days: Map<string, number>, weeks: number, weekStart: number, now: Date = new Date()): { columns: HeatCell[][]; thresholds: number[] } {
  const today = localStartOfDay(now)
  const first = addLocalDays(localStartOfWeek(today, weekStart), -(weeks - 1) * 7)
  const values: number[] = []
  for (let d = first; d.getTime() <= today.getTime(); d = addLocalDays(d, 1)) {
    const s = days.get(localDayKey(d)) ?? 0
    if (s >= MIN_DAY_SECONDS) values.push(s)
  }
  values.sort((a, b) => a - b)
  const q = (p: number) => values[Math.min(values.length - 1, Math.floor(p * (values.length - 1)))] ?? 0
  const thresholds = values.length ? [q(0.25), q(0.5), q(0.75)] : []
  const level = (s: number) => {
    if (s < MIN_DAY_SECONDS) return 0
    if (!thresholds.length) return 1
    return 1 + thresholds.filter((t) => s > t).length
  }
  const columns: HeatCell[][] = []
  for (let w = 0; w < weeks; w++) {
    const column: HeatCell[] = []
    for (let k = 0; k < 7; k++) {
      const date = addLocalDays(first, w * 7 + k)
      const day = localDayKey(date)
      const seconds = days.get(day) ?? 0
      const future = date.getTime() > today.getTime()
      column.push({ day, date, seconds: future ? 0 : seconds, level: future ? 0 : level(seconds), future })
    }
    columns.push(column)
  }
  return { columns, thresholds }
}

/** „Schöne“ Obergrenze für die y-Achse in Sekunden (volle Viertel-, halbe oder ganze Stunden). */
export function niceHours(maxSeconds: number): number {
  if (maxSeconds <= 0) return 3600
  const steps = [900, 1800, 3600, 7200, 3 * 3600, 4 * 3600, 6 * 3600, 8 * 3600, 12 * 3600, 16 * 3600, 24 * 3600, 48 * 3600, 72 * 3600, 7 * 24 * 3600]
  for (const s of steps) if (maxSeconds <= s) return s
  return Math.ceil(maxSeconds / (24 * 3600)) * 24 * 3600
}
