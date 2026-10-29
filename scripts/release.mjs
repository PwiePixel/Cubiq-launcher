// Release-Automatik: bereitet eine neue Version vor – Versionsnummer, Abschnitt in CHANGELOG.md (Englisch +
// Deutsch, sieht der Spieler im Launcher unter „Was ist neu“) und Titel des GitHub-Release. Läuft in
// .github/workflows/auto-release.yml bei jedem Push auf main; den Rest (Bauen, Release) macht release.yml.
//
//   node --experimental-strip-types scripts/release.mjs prepare [--bump auto|patch|minor|major] [--dry-run]
//
// - Noch nicht veröffentlichte Version in package.json (kein Tag v<version>): genau diese wird veröffentlicht.
// - Sonst: Sprung nach den Commits seit dem letzten Tag (siehe scripts/release-lib.mjs) oder wie angegeben.
// - Changelog: Spieler-Texte aus changes/*.md (und ältere Punkte unter „## Unreleased“) haben Vorrang, sonst
//   entstehen sie aus den Commit-Nachrichten. Die übernommenen Dateien in changes/ werden gelöscht.
//   Steht die Version schon im Changelog, bleibt ihr Text; nur das Datum wird auf heute gesetzt.
// - Screenshots, die ein Spieler-Text aus changes/ mitbringt (Kommentar „shots:“ mit Dateinamen), ziehen nach
//   public/news/<version>/ um (scripts/release-lib.mjs, relocateShots).
// - Bild: hat der Abschnitt noch kein Motiv, zeichnet scripts/release-art.mjs eins passend zum Text – ruhig
//   (public/news/<version>/banner.png) und bewegt (banner-anim.png) – und trägt beide in die Banner-Zeile ein.
// - Nichts für Spieler seit dem letzten Release und kein fester Sprung: kein Release.
// - Cubiq Client (In-Game-Mod): Haben sich seine Quellen geändert (oder hat sein Update-Kanal noch nichts Neueres als
//   das Launcher-Paket), bekommt er eine neue mod_version (scripts/client-mod-version.mjs) – der Release-Commit trägt
//   sie ein, .github/workflows/client-mod.yml baut alle Jars und veröffentlicht sie im Kanal.
// Ausgabe (für $GITHUB_OUTPUT): version=…, tag=…, title=…, client_mod=<Version oder leer>, skipped=true|false

import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { changelogFor, parseChangelog, releaseTitle } from '../app/utils/changelog.ts'
import { releaseArt } from './release-art.mjs'
import { bundledModVersion, channelModVersion, isClientModSource, nextClientModVersion, readModVersion, writeModVersion } from './client-mod-version.mjs'
import { bumpFor, changelogItem, parseChangeNote, parseGitLog, prepareChangelog, relocateShots, setBannerMotif, setReleaseDate, today, unreleasedReady } from './release-lib.mjs'
import { currentVersion, nextVersion, setVersion } from './version.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const changelogPath = join(root, 'CHANGELOG.md')
const changesDir = join(root, 'changes')

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
const range = tag ? [`${tag}..HEAD`] : ['HEAD']
const log = git('log', '--no-merges', '--name-only', '--format=%x1e%H%x1f%s%x1f%b%x1f', ...range)
const commits = parseGitLog(log)
let items = commits.map(changelogItem).filter(Boolean)

/**
 * Commits, deren Änderung einen Spieler-Text in changes/ mitgebracht hat: bei einem Merge alle Commits des
 * Pull Requests, wenn einer davon eine Datei in changes/ anlegt; sonst der Commit selbst (z. B. Squash-Merge).
 */
function coveredByNotes() {
  const covered = new Set()
  const isNote = (files) => files.split('\n').some((f) => /^changes\/[^/]+\.md$/.test(f.trim()) && !/readme\.md$/i.test(f))
  for (const line of git('log', '--first-parent', '--format=%H %P', ...range).split('\n').filter(Boolean)) {
    const [hash, first, second] = line.split(' ')
    if (second) {
      if (!isNote(git('diff', '--name-only', '--diff-filter=A', `${first}...${second}`, '--', 'changes'))) continue
      for (const h of git('log', '--no-merges', '--format=%H', `${first}..${second}`).split('\n').filter(Boolean)) covered.add(h)
    } else if (isNote(git('show', '--name-only', '--diff-filter=A', '--format=', hash, '--', 'changes'))) {
      covered.add(hash)
    }
  }
  return covered
}
const text = readFileSync(changelogPath, 'utf8')
// Spieler-Texte je Änderung (changes/<thema>.md, ohne README), in Dateinamen-Reihenfolge.
const noteFiles = existsSync(changesDir)
  ? readdirSync(changesDir)
      .filter((name) => name.endsWith('.md') && name.toLowerCase() !== 'readme.md')
      .sort()
  : []
const notes = noteFiles.map((name) => ({ name, note: parseChangeNote(readFileSync(join(changesDir, name), 'utf8')) })).filter((n) => n.note)
const manual = unreleasedReady(text) || notes.length > 0

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

/**
 * Neue Version des Cubiq Client für dieses Release oder `null`. Ein Fehler hier (z. B. mod_version nicht überall
 * gleich) hält das Launcher-Release nicht auf – der Client kommt dann eben nicht mit, und der Lauf sagt warum.
 */
async function clientModPlan() {
  try {
    const repo = readModVersion(root)
    const next = nextClientModVersion({
      repo,
      channel: await channelModVersion(process.env.GITHUB_REPOSITORY || undefined),
      bundled: bundledModVersion(root),
      changed: commits.some((c) => c.files.some(isClientModSource)),
    })
    return { repo, next }
  } catch (e) {
    const message = `Cubiq Client nicht dabei: ${e.message.split('\n')[0]}`
    if (process.env.GITHUB_ACTIONS) console.log(`::warning title=Cubiq Client::${message}`)
    console.error(message)
    return { repo: null, next: null }
  }
}
const clientMod = await clientModPlan()

let next = setReleaseDate(text, version, today())
let consumed = []
/** Screenshots der Spieler-Texte: changes/<datei> → public/news/<version>/<name> (relocateShots). */
let shotMoves = []
if (!changelogFor(parseChangelog(text), version)) {
  const newsDir = join(root, 'public', 'news', version)
  const taken = new Set(existsSync(newsDir) ? readdirSync(newsDir).map((name) => name.toLowerCase()) : [])
  const relocated = notes.map((n) => {
    const { preamble, moves } = relocateShots(n.note.preamble, version, taken)
    shotMoves.push(...moves)
    return { ...n.note, preamble }
  })
  const missing = shotMoves.filter((m) => !existsSync(join(changesDir, m.from)))
  if (missing.length) {
    console.error(`Screenshots fehlen in changes/: ${missing.map((m) => m.from).join(', ')}`)
    process.exit(1)
  }
  // Neuerungen und Fehlerbehebungen ohne eigenen Spieler-Text (z. B. ältere Pull Requests) kommen aus den Commits
  // dazu – nur mit „feat:“/„fix:“ oder eigenen Zeilen „EN: …“/„DE: …“, damit keine Aufräum-Commits darin landen.
  const covered = relocated.length ? coveredByNotes() : new Set()
  const meant = (c) => /^(?:feat|fix)(?:\([^)]*\))?!?:/i.test(c.subject) || /^(?:EN|DE):\s*\S/im.test(c.body)
  const extra = relocated.length ? commits.filter((c) => !covered.has(c.hash) && meant(c)).map(changelogItem) : []
  const prepared = prepareChangelog(text, version, today(), items, relocated, extra)
  if (!prepared) {
    console.error(`Für ${version} gibt es weder Punkte unter „## Unreleased“ noch Commits – kein Release.`)
    console.log('skipped=true')
    process.exit(0)
  }
  next = prepared
  consumed = notes.map((n) => n.name)
}

// Bild des Release (Motiv im Update-Banner, ruhig und bewegt), sofern der Abschnitt noch keins hat.
let entry = changelogFor(parseChangelog(next), version)
let art = null
if (entry && !entry.banner?.motif) {
  const motif = `/news/${version}/banner.png`
  const anim = `/news/${version}/banner-anim.png`
  const file = join(root, 'public', motif)
  const animFile = join(root, 'public', anim)
  // Liegt schon ein eigenes Bild bereit, bleibt es (die bewegte Fassung nur, wenn es sie auch gibt).
  art = existsSync(file) ? { png: null, apng: null, topic: 'vorhanden' } : releaseArt({ version, text: entry.en || entry.de })
  next = setBannerMotif(next, version, motif, art.apng || existsSync(animFile) ? anim : null)
  entry = changelogFor(parseChangelog(next), version)
  if (!dryRun && art.png) {
    mkdirSync(join(root, 'public', 'news', version), { recursive: true })
    writeFileSync(file, art.png)
    writeFileSync(animFile, art.apng)
  }
}

const title = releaseTitle(entry, version)
const clientNote = clientMod.next ? `, Cubiq Client ${clientMod.repo === clientMod.next ? '' : `${clientMod.repo} → `}${clientMod.next}` : ''
if (dryRun) {
  console.error(`Version ${current} → ${version} (${title})${art ? ` – Bild: ${art.topic}` : ''}${clientNote}\n\n${entry?.en ?? ''}\n\n${entry?.de ?? ''}`)
} else {
  if (next !== text) writeFileSync(changelogPath, next)
  if (version !== current) setVersion(version)
  if (clientMod.next && clientMod.next !== clientMod.repo) writeModVersion(root, clientMod.next)
  for (const name of consumed) rmSync(join(changesDir, name))
  if (shotMoves.length) mkdirSync(join(root, 'public', 'news', version), { recursive: true })
  for (const { from, to } of shotMoves) {
    copyFileSync(join(changesDir, from), join(root, 'public', 'news', version, to))
    // Dieselbe Datei kann in zwei Texten stehen – nur einmal löschen.
    if (existsSync(join(changesDir, from))) rmSync(join(changesDir, from))
  }
  console.error(`Version ${version} vorbereitet: ${title}${art ? ` (Bild: ${art.topic})` : ''}${clientNote}${consumed.length ? `, Texte aus changes/: ${consumed.join(', ')}` : ''}`)
}
console.log(`version=${version}\ntag=v${version}\ntitle=${title}\nclient_mod=${clientMod.next ?? ''}\nskipped=false`)
