// Welt-Backups: Typen der Kern-Antworten und kleine Helfer (Name einer wiederhergestellten Kopie,
// Texte für Ereignisse). Getestet in tests/world-backups.test.ts (ohne Nuxt).

export type BackupKind = 'auto' | 'manual' | 'safety' | 'legacy'

/** Ein Backup einer Welt. */
export interface BackupInfo {
  /** Kennung für Wiederherstellen/Löschen (Dateiname bzw. `legacy:<Datei>`). */
  id: string
  kind: BackupKind
  created: string
  /** Größe des Archivs in Bytes. */
  size: number
  /** Weltname zum Zeitpunkt des Backups. */
  name: string | null
  version: string | null
  files: number | null
}

export interface WorldBackupSummary {
  folder: string
  name: string | null
  /** Gibt es die Welt noch? */
  exists: boolean
  count: number
  latest: string | null
  bytes: number
}

export type BackupReason = 'launch' | 'exit'

export interface BackupJob {
  job: number
  instanceId: string
  reason: BackupReason
  world: string | null
  index: number
  count: number
  percent: number
}

export interface BackupOverview {
  /** Gilt für diese Instanz. */
  enabled: boolean
  global: boolean
  /** Eigene Einstellung der Instanz (`null` = wie in den Einstellungen). */
  instance: boolean | null
  keep: number
  /** Gemeinsamer Welten-Ordner mehrerer Instanzen. */
  shared: boolean
  worlds: WorldBackupSummary[]
  job: BackupJob | null
}

export type RestoreMode = 'copy' | 'replace'

export interface RestoreOutcome {
  folder: string
  safetyBackup: string | null
}

export interface BackupUsage {
  bytes: number
  count: number
  worlds: number
  orphanedBytes: number
}

export interface WorldBackupSettings {
  enabled: boolean
  /** Automatische Backups je Welt (1–50). */
  keep: number
  /** Obergrenze aller Backups in GB; 0 = keine. */
  maxTotalGb: number
}

export interface BackupError {
  code: string
  message: string
  params?: Record<string, string>
}

/** Ereignisse der automatischen Backups (Event `world-backup`). */
export type BackupEvent =
  | { type: 'started'; job: number; instanceId: string; reason: BackupReason; worlds: string[]; totalBytes: number }
  | { type: 'progress'; job: number; instanceId: string; world: string; index: number; count: number; percent: number }
  | {
      type: 'finished'
      job: number
      instanceId: string
      reason: BackupReason
      saved: string[]
      skipped: string[]
      failed: { name: string; error: BackupError }[]
      cancelled: boolean
    }

/** Gesamtfortschritt einer Sicherung mehrerer Welten (jede Welt gleich gewichtet). */
export function jobPercent(index: number, count: number, percent: number): number {
  if (count <= 0) return 0
  return Math.min(100, Math.floor(((index + Math.min(100, Math.max(0, percent)) / 100) / count) * 100))
}

/** Zeichen, die in Ordnernamen nicht erlaubt sind (wie der Kern prüft). */
const UNSAFE = /[\\/:*?"<>|\u0000-\u001f]/g

/** Name der Kopie: „<Welt> (wiederhergestellt <Datum>)“ – ohne Zeichen, die als Ordnername nicht gehen. */
export function restoredName(world: string, label: string, date: string): string {
  const name = `${world} (${label} ${date})`.replace(UNSAFE, '-').replace(/\s+/g, ' ')
  return name.slice(0, 80).trim().replace(/\.+$/, '')
}

/** Datum für den Namen der Kopie – ohne Doppelpunkt (unter Windows nicht erlaubt): `2026-10-27 14.05`. */
export function restoreStamp(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}.${p(d.getMinutes())}`
}

/** Backups sortiert: neueste zuerst. */
export function sortBackups(list: BackupInfo[]): BackupInfo[] {
  return [...list].sort((a, b) => Date.parse(b.created) - Date.parse(a.created))
}
