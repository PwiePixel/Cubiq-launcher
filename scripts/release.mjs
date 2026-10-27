// Release-Automatik: bereitet eine neue Version vor – Versionsnummer, Abschnitt in CHANGELOG.md (Englisch +
// Deutsch, sieht der Spieler im Launcher unter „Was ist neu“) und Titel des GitHub-Release. Läuft in
// .github/workflows/auto-release.yml bei jedem Push auf main; den Rest (Bauen, Release) macht release.yml.
//
//   node --experimental-strip-types scripts/release.mjs prepare [--bump auto|patch|minor|major] [--dry-run]
//
// - Noch nicht veröffentlichte Version in package.json (kein Tag v<version>): genau diese wird veröffentlicht.
// - Sonst: Sprung nach den Commits seit dem letzten Tag (siehe scripts/release-lib.mjs) oder wie angegeben.
// - Changelog: Punkte unter „## Unreleased“ haben Vorrang, sonst entstehen sie aus den Commit-Nachrichten.
//   Steht die Version schon im Changelog, bleibt ihr Text; nur das Datum wird auf heute gesetzt.
// - Nichts für Spieler seit dem letzten Release und kein fester Sprung: kein Release.
// Ausgabe (für $GITHUB_OUTPUT): version=…, tag=…, title=…, skipped=true|false

import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { changelogFor, parseChangelog, releaseTitle } from '../app/utils/changelog.ts'
import { bumpFor, changelogItem, parseGitLog, prepareChangelog, setReleaseDate, today, unreleasedReady } from './release-lib.mjs'
import { currentVersion, nextVersion, setVersion } from './version.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const changelogPath = join(root, 'CHANGELOG.md')

const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()

function lastTag() {
  try {
    return git('describe', '--tags', '--abbrev=0', '--match', 'v[0-9]*')
  } catch {
    return null
  }
}

const [command, ...rest] = process.argv.slice(2)
if (command !== 'prepare') {
  console.error('Aufruf: release.mjs prepare [--bump auto|patch|minor|major] [--dry-run]')
  process.exit(2)
}
const flag = (name) => {
  const i = rest.indexOf(name)
  return i >= 0 ? rest[i + 1] : undefined
}
const bump = flag('--bump') ?? 'auto'
const dryRun = rest.includes('--dry-run')
if (!['auto', 'patch', 'minor', 'major'].includes(bump)) {
  console.error(`Unbekannter Sprung: ${bump}`)
  process.exit(2)
}

const tag = lastTag()
const current = currentVersion()
const released = git('tag', '--list', `v${current}`) !== ''
const log = git('log', '--no-merges', '--name-only', '--format=%x1e%H%x1f%s%x1f%b%x1f', ...(tag ? [`${tag}..HEAD`] : ['HEAD']))
let items = parseGitLog(log).map(changelogItem).filter(Boolean)
const text = readFileSync(changelogPath, 'utf8')
const manual = unreleasedReady(text)

let version
if (!released) {
  version = current
} else {
  if (!items.length && !manual && bump === 'auto') {
    console.error(`Seit ${tag ?? 'dem Anfang'} nichts für Spieler – kein Release.`)
    console.log('skipped=true')
    process.exit(0)
  }
  version = nextVersion(current, bump === 'auto' ? bumpFor(items) : bump)
  if (!items.length && !manual) {
    items = [{ en: 'Small improvements and fixes', de: 'Kleine Verbesserungen und Fehlerbehebungen', kind: 'change', bump: null }]
  }
}

let next = setReleaseDate(text, version, today())
if (!changelogFor(parseChangelog(text), version)) {
  const prepared = prepareChangelog(text, version, today(), items)
  if (!prepared) {
    console.error(`Für ${version} gibt es weder Punkte unter „## Unreleased“ noch Commits – kein Release.`)
    console.log('skipped=true')
    process.exit(0)
  }
  next = prepared
}

const entry = changelogFor(parseChangelog(next), version)
const title = releaseTitle(entry, version)
if (dryRun) {
  console.error(`Version ${current} → ${version} (${title})\n\n${entry?.en ?? ''}\n\n${entry?.de ?? ''}`)
} else {
  if (next !== text) writeFileSync(changelogPath, next)
  if (version !== current) setVersion(version)
  console.error(`Version ${version} vorbereitet: ${title}`)
}
console.log(`version=${version}\ntag=v${version}\ntitle=${title}\nskipped=false`)
