import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

import { applyPart, keepVersions, partName } from '../scripts/client-mod-part.mjs'

const BUILDS = [
  { loader: 'fabric', minecraft: ['1.20.1'], file: 'cubiqclient-fabric-1.20.1.jar', requires: ['fabric-api'] },
  { loader: 'fabric', minecraft: ['1.21.1'], file: 'cubiqclient-fabric-1.21.1.jar', requires: ['fabric-api'] },
  { loader: 'fabric', minecraft: ['1.21.11'], file: 'cubiqclient-fabric-1.21.11.jar', requires: ['fabric-api'] },
]

const dirs: string[] = []
afterEach(() => {
  for (const d of dirs.splice(0)) rmSync(d, { recursive: true, force: true })
})

function dist(list = 'builds.json') {
  const d = mkdtempSync(join(tmpdir(), 'cubiq-part-'))
  dirs.push(d)
  writeFileSync(join(d, list), JSON.stringify(BUILDS))
  for (const b of BUILDS) writeFileSync(join(d, b.file), 'jar')
  return d
}

describe('client-mod-part', () => {
  it('keeps only the requested versions', () => {
    expect(keepVersions(BUILDS, ['1.20.1', ' 1.21.11 ']).map((b) => b.file)).toEqual(['cubiqclient-fabric-1.20.1.jar', 'cubiqclient-fabric-1.21.11.jar'])
    expect(keepVersions(BUILDS, [])).toBe(BUILDS)
    expect(keepVersions(BUILDS, [''])).toBe(BUILDS)
    // Ein Build für mehrere Versionen behält nur die gewünschten.
    expect(keepVersions([{ loader: 'fabric', minecraft: ['1.20', '1.20.1'], file: 'a.jar' }], ['1.20.1'])[0].minecraft).toEqual(['1.20.1'])
  })

  it('drops the always-built checked-in version of other jobs and renames the list', () => {
    const d = dist()
    expect(applyPart(d, 'fabric-3', '1.21.11')).toBe(1)
    expect(existsSync(join(d, 'builds.json'))).toBe(false)
    expect(JSON.parse(readFileSync(join(d, 'builds-fabric-3.json'), 'utf8'))).toEqual([BUILDS[2]])
    expect(existsSync(join(d, 'cubiqclient-fabric-1.21.1.jar'))).toBe(false)
    expect(existsSync(join(d, 'cubiqclient-fabric-1.21.11.jar'))).toBe(true)
  })

  it('keeps everything without a version list', () => {
    const d = dist('builds-forge.json')
    expect(applyPart(d, 'forge', '')).toBe(3)
    expect(existsSync(join(d, 'builds-forge.json'))).toBe(true)
  })

  it('rejects unsafe part names and unclear inputs', () => {
    expect(() => partName('../x')).toThrow()
    expect(() => partName('')).toThrow()
    expect(partName('forge-1.13.2')).toBe('forge-1.13.2')
    const d = dist()
    writeFileSync(join(d, 'builds-other.json'), '[]')
    expect(() => applyPart(d, 'x', '')).toThrow(/genau eine/)
  })
})
