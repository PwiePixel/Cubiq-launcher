import { describe, expect, it } from 'vitest'

import { chunk, plan, PROJECTS, readVersionList } from '../scripts/client-mod-plan.mjs'

describe('client-mod-plan', () => {
  it('reads the version lists of the real settings files', () => {
    const matrix = plan(12)
    const parts = matrix.include.map((e: { part: string }) => e.part)
    expect(new Set(parts).size).toBe(parts.length)
    // Jede Version jeder Liste genau einmal in genau einem Job.
    const fabric = matrix.include.filter((e: { dir: string }) => e.dir === '.').flatMap((e: { versions: string }) => e.versions.split(','))
    expect(fabric).toContain('1.21.11')
    expect(fabric).toContain('26.3')
    expect(new Set(fabric).size).toBe(fabric.length)
    for (const e of matrix.include) {
      expect(e.versions.split(',').filter(Boolean).length).toBeLessThanOrEqual(12)
      expect(e.part).toMatch(/^[A-Za-z0-9][A-Za-z0-9.-]*$/)
    }
    // Projekte mit nur einer Version bauen ohne Liste.
    expect(matrix.include.find((e: { part: string }) => e.part === 'legacy-1.7.10')).toMatchObject({ versions: '', dir: 'legacy-1.7.10' })
    expect(PROJECTS.length).toBe(7)
  })

  it('splits evenly and keeps the order', () => {
    const v = Array.from({ length: 25 }, (_, i) => `1.${i}`)
    const parts = chunk(v, 12)
    expect(parts.map((p) => p.length)).toEqual([9, 9, 7])
    expect(parts.flat()).toEqual(v)
    expect(chunk(['1.7.10'], 12)).toEqual([['1.7.10']])
  })

  it('names the parts and fails on missing lists', () => {
    const settings = "def forgeVersions = ['1.20', '1.20.1',\n\t\t'1.21']"
    expect(readVersionList(settings, 'forgeVersions')).toEqual(['1.20', '1.20.1', '1.21'])
    expect(() => readVersionList('', 'forgeVersions')).toThrow(/nicht gefunden/)
    const read = (dir: string) => (dir === '.' ? "def fabricVersions = ['1.20.1', '1.21.1', '1.21.11']" : `def forgeVersions = ['1.20']\ndef neoforgeVersions = ['1.20.2']\ndef mojmapVersions = ['1.16.5']\ndef legacyVersions = ['1.8.9']`)
    const small = plan(2, read)
    expect(small.include.filter((e: { dir: string }) => e.dir === '.').map((e: { part: string, versions: string }) => [e.part, e.versions])).toEqual([
      ['fabric-1', '1.20.1,1.21.1'],
      ['fabric-2', '1.21.11'],
    ])
    expect(small.include.find((e: { part: string }) => e.part === 'forge')).toMatchObject({ name: 'Forge 1.20', versions: '1.20' })
  })
})
