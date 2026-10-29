import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
  bumpModVersion,
  bundledModVersion,
  isClientModSource,
  isNewer,
  modVersionFiles,
  nextClientModVersion,
  readModVersion,
  writeModVersion,
} from '../scripts/client-mod-version.mjs'

const root = join(__dirname, '..')
const temps: string[] = []

/** Repo-Attrappe mit client-mod/gradle.properties und Unterprojekten. */
function fakeRepo(versions: Record<string, string | null>) {
  const dir = mkdtempSync(join(tmpdir(), 'cubiq-mod-version-'))
  temps.push(dir)
  for (const [project, version] of Object.entries(versions)) {
    const folder = project === '.' ? join(dir, 'client-mod') : join(dir, 'client-mod', project)
    mkdirSync(folder, { recursive: true })
    const lines = ['org.gradle.jvmargs=-Xmx2G', ...(version === null ? [] : [`mod_version=${version}`]), 'maven_group=dev.example', '']
    writeFileSync(join(folder, 'gradle.properties'), lines.join('\n'))
  }
  return dir
}

afterEach(() => {
  for (const dir of temps.splice(0)) rmSync(dir, { recursive: true, force: true })
})

describe('Cubiq Client: Version', () => {
  it('erkennt, was den Client für Spieler ändert', () => {
    expect(isClientModSource('client-mod/common/src/main/java/dev/x/Map.java')).toBe(true)
    expect(isClientModSource('client-mod/fabric/src/main/resources/assets/cubiqclient/lang/de_de.json')).toBe(true)
    expect(isClientModSource('client-mod/forge/build.gradle')).toBe(true)
    expect(isClientModSource('data/circuits/adder.json')).toBe(true)
    expect(isClientModSource('client-mod/common/src/test/java/dev/x/MapTest.java')).toBe(false)
    expect(isClientModSource('client-mod/docs/maps.md')).toBe(false)
    expect(isClientModSource('client-mod/README.md')).toBe(false)
    expect(isClientModSource('app/pages/index.vue')).toBe(false)
    expect(isClientModSource('scripts/publish-client-mod.mjs')).toBe(false)
  })

  it('vergleicht und erhöht Versionen wie der Launcher', () => {
    expect(isNewer('0.24.0', '0.23.0')).toBe(true)
    expect(isNewer('0.10.0', '0.9.9')).toBe(true)
    expect(isNewer('1.0.0', '1.0.0-beta.1')).toBe(true)
    expect(isNewer('0.23.0', '0.23.0')).toBe(false)
    expect(isNewer('0.23.0', '0.24.0')).toBe(false)
    expect(bumpModVersion('0.23.0', 'minor')).toBe('0.24.0')
    expect(bumpModVersion('0.23.4', 'patch')).toBe('0.23.5')
    expect(bumpModVersion('0.23.4-beta.1', 'major')).toBe('1.0.0')
    expect(bumpModVersion('1.2', 'patch')).toBe('1.2.1')
    expect(() => bumpModVersion('1.2.3', 'auto')).toThrow()
  })

  it('bringt beim ersten Mal (Kanal leer) eine Version über dem Launcher-Paket – auch ohne Änderung', () => {
    expect(nextClientModVersion({ repo: '0.23.0', channel: null, bundled: '0.23.0', changed: false })).toBe('0.24.0')
    expect(nextClientModVersion({ repo: '0.23.0', channel: null, bundled: '0.23.0', changed: true })).toBe('0.24.0')
    // Ohne Launcher-Paket (Version "0"): die Version im Repository.
    expect(nextClientModVersion({ repo: '0.23.0', channel: null, bundled: '0', changed: false })).toBe('0.23.0')
  })

  it('geht nach Änderungen eine Minor-Version weiter, sonst bleibt der Kanal', () => {
    expect(nextClientModVersion({ repo: '0.24.0', channel: '0.24.0', bundled: '0.23.0', changed: true })).toBe('0.25.0')
    expect(nextClientModVersion({ repo: '0.24.0', channel: '0.24.0', bundled: '0.23.0', changed: false })).toBeNull()
    // Kanal hinter dem Launcher-Paket (Paket von Hand aktualisiert): erst über das Paket.
    expect(nextClientModVersion({ repo: '0.26.0', channel: '0.24.0', bundled: '0.26.0', changed: true })).toBe('0.27.0')
  })

  it('veröffentlicht eine schon erhöhte, noch nicht veröffentlichte Version genau so', () => {
    // Abgebrochenes Release: mod_version steht schon auf 0.25.0, der Kanal hat noch 0.24.0.
    expect(nextClientModVersion({ repo: '0.25.0', channel: '0.24.0', bundled: '0.23.0', changed: false })).toBe('0.25.0')
    expect(nextClientModVersion({ repo: '0.25.0', channel: '0.24.0', bundled: '0.23.0', changed: true })).toBe('0.25.0')
    // Kanal noch leer, erster Versuch gescheitert: 0.24.0 bleibt.
    expect(nextClientModVersion({ repo: '0.24.0', channel: null, bundled: '0.23.0', changed: false })).toBe('0.24.0')
  })

  it('springt ohne erreichbaren Kanal nur bei Änderungen – und immer über alles Bekannte', () => {
    expect(nextClientModVersion({ repo: '0.24.0', channel: undefined, bundled: '0.23.0', changed: false })).toBeNull()
    expect(nextClientModVersion({ repo: '0.24.0', channel: undefined, bundled: '0.23.0', changed: true })).toBe('0.25.0')
    expect(nextClientModVersion({ repo: '0.23.0', channel: undefined, bundled: '0.23.0', changed: true })).toBe('0.24.0')
  })

  it('liest und schreibt mod_version in allen Projekten', () => {
    const dir = fakeRepo({ '.': '0.23.0', forge: '0.23.0', 'legacy-1.7.10': '0.23.0', 'e4mc-bridge': null })
    mkdirSync(join(dir, 'client-mod', 'common'))
    expect(modVersionFiles(dir)).toHaveLength(3)
    expect(readModVersion(dir)).toBe('0.23.0')
    expect(writeModVersion(dir, '0.24.0')).toBe(3)
    expect(readModVersion(dir)).toBe('0.24.0')
    const forge = readFileSync(join(dir, 'client-mod', 'forge', 'gradle.properties'), 'utf8')
    expect(forge).toBe('org.gradle.jvmargs=-Xmx2G\nmod_version=0.24.0\nmaven_group=dev.example\n')
    // Projekte ohne mod_version bleiben unberührt.
    expect(readFileSync(join(dir, 'client-mod', 'e4mc-bridge', 'gradle.properties'), 'utf8')).not.toContain('mod_version')
    expect(() => writeModVersion(dir, '0.24.0; rm -rf /')).toThrow()
  })

  it('bricht ab, wenn mod_version nicht überall gleich ist', () => {
    const dir = fakeRepo({ '.': '0.23.0', forge: '0.22.0' })
    expect(() => readModVersion(dir)).toThrow(/nicht überall gleich/)
  })

  it('findet im Repository alle Projekte mit eigener mod_version und das Launcher-Paket', () => {
    const projects = modVersionFiles(root).map((file) => file.slice(root.length + 1).replace(/\\/g, '/'))
    for (const project of ['', 'forge/', 'neoforge/', 'forge-mojmap-legacy/', 'forge-1.13.2/', 'legacy/', 'legacy-1.7.10/']) {
      expect(projects).toContain(`client-mod/${project}gradle.properties`)
    }
    expect(readModVersion(root)).toMatch(/^\d+\.\d+\.\d+$/)
    expect(bundledModVersion(root)).toMatch(/^\d+\.\d+\.\d+$/)
  })
})
