// Versionsnummer des Launchers an allen Stellen: package.json, src-tauri/tauri.conf.json,
// src-tauri/Cargo.toml ([workspace.package]) und die Workspace-Crates in src-tauri/Cargo.lock.
//
//   node scripts/version.mjs get                    → aktuelle Version
//   node scripts/version.mjs next <patch|minor|major> → nächste Version (nur ausgeben)
//   node scripts/version.mjs set <x.y.z>            → überall eintragen
//
// Die Release-Automatik (scripts/release.mjs) nutzt dieselben Funktionen.

import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const VERSION = /^(\d+)\.(\d+)\.(\d+)$/

const files = {
  pkg: join(root, 'package.json'),
  tauri: join(root, 'src-tauri', 'tauri.conf.json'),
  cargo: join(root, 'src-tauri', 'Cargo.toml'),
  lock: join(root, 'src-tauri', 'Cargo.lock'),
}

/** Aktuelle Version aus package.json. */
export function currentVersion() {
  return JSON.parse(readFileSync(files.pkg, 'utf8')).version
}

/**
 * Nächste Version nach Semver-Sprung.
 * @param {string} version
 * @param {'patch' | 'minor' | 'major'} bump
 */
export function nextVersion(version, bump) {
  const m = VERSION.exec(version)
  if (!m) throw new Error(`Keine gültige Version: ${version}`)
  const [major, minor, patch] = m.slice(1).map(Number)
  if (bump === 'major') return `${major + 1}.0.0`
  if (bump === 'minor') return `${major}.${minor + 1}.0`
  if (bump === 'patch') return `${major}.${minor}.${patch + 1}`
  throw new Error(`Unbekannter Sprung: ${bump}`)
}

/** Workspace-Crates (ohne `source`) in Cargo.lock auf die neue Version setzen. */
export function setLockVersions(lock, from, to) {
  return lock
    .split('[[package]]')
    .map((block) => (block.includes('source =') ? block : block.replace(`\nversion = "${from}"\n`, `\nversion = "${to}"\n`)))
    .join('[[package]]')
}

/** Version überall eintragen; gibt die vorherige Version zurück. */
export function setVersion(version) {
  if (!VERSION.test(version)) throw new Error(`Keine gültige Version: ${version}`)
  const before = currentVersion()
  for (const file of [files.pkg, files.tauri]) {
    const text = readFileSync(file, 'utf8')
    writeFileSync(file, text.replace(/("version":\s*")[^"]+(")/, `$1${version}$2`))
  }
  const cargo = readFileSync(files.cargo, 'utf8')
  writeFileSync(files.cargo, cargo.replace(/(\[workspace\.package\][^[]*?\nversion = ")[^"]+(")/, `$1${version}$2`))
  writeFileSync(files.lock, setLockVersions(readFileSync(files.lock, 'utf8'), before, version))
  return before
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [command, arg] = process.argv.slice(2)
  try {
    if (command === 'get') console.log(currentVersion())
    else if (command === 'next') console.log(nextVersion(currentVersion(), /** @type {any} */ (arg)))
    else if (command === 'set') {
      const before = setVersion(arg)
      console.error(`Version ${before} → ${arg}`)
    } else {
      console.error('Aufruf: version.mjs get | next <patch|minor|major> | set <x.y.z>')
      process.exit(2)
    }
  } catch (e) {
    console.error(e instanceof Error ? e.message : String(e))
    process.exit(1)
  }
}
