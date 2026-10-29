// Versionsnummer des Cubiq Client (In-Game-Mod): `mod_version` in client-mod/gradle.properties und in jedem Projekt
// mit eigenem Gradle-Build – überall gleich. Die Release-Automatik (scripts/release.mjs) entscheidet damit, ob ein
// Launcher-Release auch einen neuen Cubiq Client bringt (Commit mit der neuen mod_version, Bau und Veröffentlichung im
// Workflow .github/workflows/client-mod.yml); scripts/publish-client-mod.mjs liest und erhöht sie.

import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

export const MOD_VERSION_RE = /^\d{1,9}(\.\d{1,9}){0,3}(-[0-9A-Za-z]+(\.[0-9A-Za-z]+)*)?$/
/** Release des Update-Kanals, aus dem der Launcher den Cubiq Client lädt. */
export const CHANNEL_TAG = 'client-mod'
export const CHANNEL_MANIFEST = 'client-mod.json'

/**
 * Ändert diese Datei den Cubiq Client, den Spieler bekommen? Quellen, Build-Dateien und mitgelieferte Daten (data/,
 * z. B. die Schaltungs-Bibliothek) ja – Tests und Doku nicht.
 */
export function isClientModSource(file) {
  if (!/^(client-mod|data)\//.test(file)) return false
  return !/(^|\/)(src\/test|docs)\//.test(file) && !/\.md$/i.test(file)
}

/** Alle gradle.properties mit mod_version (Wurzel + Projekte mit eigenem Build). */
export function modVersionFiles(root) {
  const dir = join(root, 'client-mod')
  const files = [join(dir, 'gradle.properties')]
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const file = join(dir, entry.name, 'gradle.properties')
    if (entry.isDirectory() && existsSync(file) && /^mod_version=/m.test(readFileSync(file, 'utf8'))) files.push(file)
  }
  return files
}

/** mod_version – wirft, wenn sie nicht überall gleich oder ungültig ist. */
export function readModVersion(root) {
  const versions = new Map()
  for (const file of modVersionFiles(root)) {
    const match = readFileSync(file, 'utf8').match(/^mod_version=(.+)$/m)
    if (match) versions.set(file, match[1].trim())
  }
  const distinct = [...new Set(versions.values())]
  if (distinct.length !== 1) {
    throw new Error(`mod_version ist nicht überall gleich:\n${[...versions].map(([f, v]) => `  ${v}  ${f}`).join('\n')}`)
  }
  const version = distinct[0]
  if (!MOD_VERSION_RE.test(version) || version.length > 32) throw new Error(`Ungültige mod_version "${version}"`)
  return version
}

/** mod_version überall eintragen; gibt die Anzahl der Dateien zurück. */
export function writeModVersion(root, version) {
  if (!MOD_VERSION_RE.test(version) || version.length > 32) throw new Error(`Ungültige mod_version "${version}"`)
  const files = modVersionFiles(root)
  for (const file of files) {
    const text = readFileSync(file, 'utf8')
    writeFileSync(file, text.replace(/^mod_version=.*$/m, `mod_version=${version}`))
  }
  return files.length
}

function parseVersion(v) {
  const [core, pre] = v.split(/-(.*)/s)
  return { nums: core.split('.').map(Number), pre: pre || null }
}

/** a > b? (Vorabversionen wie 1.0.0-beta sind älter als 1.0.0) */
export function isNewer(a, b) {
  const x = parseVersion(a)
  const y = parseVersion(b)
  const len = Math.max(x.nums.length, y.nums.length)
  for (let i = 0; i < len; i++) {
    const d = (x.nums[i] ?? 0) - (y.nums[i] ?? 0)
    if (d !== 0) return d > 0
  }
  if (x.pre === null) return y.pre !== null
  return y.pre !== null && x.pre > y.pre
}

/** Nächste Version nach Sprung (Vorabkennung fällt weg). */
export function bumpModVersion(version, kind) {
  const [major, minor, patch] = [...parseVersion(version).nums, 0, 0, 0]
  const next = { major: `${major + 1}.0.0`, minor: `${major}.${minor + 1}.0`, patch: `${major}.${minor}.${patch + 1}` }[kind]
  if (!next) throw new Error('Sprung braucht major, minor oder patch')
  return next
}

/**
 * Neue Version des Cubiq Client für ein Launcher-Release oder `null` (kein neuer Client).
 * - `repo`: mod_version im Repository, `bundled`: Version im Launcher-Paket (src-tauri/resources/client-mod) –
 *   Launcher nehmen aus dem Kanal nur Neueres als das.
 * - `channel`: Version im Update-Kanal; `null` = Kanal leer, `undefined` = nicht abrufbar.
 * - `changed`: Quellen des Clients seit dem letzten Release geändert ({@link isClientModSource}).
 * Gibt es schon eine unveröffentlichte neuere mod_version (von Hand erhöht oder ein abgebrochenes Release), wird
 * genau sie veröffentlicht; sonst geht es um eine Minor-Version weiter – wie bei den bisherigen Client-Versionen.
 * Ohne Änderung und mit aktuellem Kanal: `null`. Ist der Kanal nicht abrufbar, nur bei Änderungen und dann immer mit
 * Sprung (eine Version doppelt zu veröffentlichen ginge schief).
 */
export function nextClientModVersion({ repo, channel, bundled, changed }) {
  if (channel === undefined) return changed ? bumpModVersion(isNewer(repo, bundled) ? repo : bundled, 'minor') : null
  const published = channel !== null && isNewer(channel, bundled) ? channel : bundled
  if (isNewer(repo, published)) return repo
  if (!changed && channel !== null) return null
  return bumpModVersion(published, 'minor')
}

/** Version im Launcher-Paket (src-tauri/resources/client-mod/builds.json), "0" ohne Paket. */
export function bundledModVersion(root) {
  const file = join(root, 'src-tauri', 'resources', 'client-mod', 'builds.json')
  if (!existsSync(file)) return '0'
  const data = JSON.parse(readFileSync(file, 'utf8'))
  return typeof data?.version === 'string' && MOD_VERSION_RE.test(data.version) ? data.version : '0'
}

/** Version im Update-Kanal: Version, `null` = noch leer (404), `undefined` = nicht abrufbar. */
export async function channelModVersion(repo = 'PwiePixel/Cubiq-launcher', timeoutMs = 10_000) {
  try {
    const res = await fetch(`https://github.com/${repo}/releases/download/${CHANNEL_TAG}/${CHANNEL_MANIFEST}`, {
      signal: AbortSignal.timeout(timeoutMs),
    })
    if (res.status === 404) return null
    if (!res.ok) return undefined
    const version = (await res.json()).version
    return typeof version === 'string' && MOD_VERSION_RE.test(version) ? version : undefined
  } catch {
    return undefined
  }
}
