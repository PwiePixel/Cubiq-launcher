import { describe, expect, it } from 'vitest'
import { jobPercent, restoredName, restoreStamp, sortBackups, type BackupInfo } from '../app/utils/worldBackups'

describe('Welt-Backups', () => {
  it('rechnet den Gesamtfortschritt über alle Welten', () => {
    expect(jobPercent(0, 2, 50)).toBe(25)
    expect(jobPercent(1, 2, 0)).toBe(50)
    expect(jobPercent(1, 2, 100)).toBe(100)
    expect(jobPercent(0, 0, 50)).toBe(0)
    expect(jobPercent(0, 1, 250)).toBe(100)
  })

  it('benennt die Kopie ohne Zeichen, die als Ordnername nicht gehen', () => {
    const stamp = restoreStamp(new Date(2026, 9, 27, 14, 5).toISOString())
    expect(stamp).toBe('2026-10-27 14.05')
    expect(restoredName('Meine Welt', 'restored', stamp)).toBe('Meine Welt (restored 2026-10-27 14.05)')
    expect(restoredName('Burg: <Teil 2>?', 'wiederhergestellt', stamp)).toBe('Burg- -Teil 2-- (wiederhergestellt 2026-10-27 14.05)')
    expect(restoredName('x'.repeat(200), 'restored', stamp)).toHaveLength(80)
    expect(restoreStamp('kaputt')).toBe('')
  })

  it('sortiert neueste zuerst', () => {
    const b = (id: string, created: string): BackupInfo => ({ id, kind: 'auto', created, size: 1, name: null, version: null, files: null })
    const sorted = sortBackups([b('alt', '2026-01-01T10:00:00Z'), b('neu', '2026-10-01T10:00:00Z'), b('mitte', '2026-05-01T10:00:00Z')])
    expect(sorted.map((x) => x.id)).toEqual(['neu', 'mitte', 'alt'])
  })
})
