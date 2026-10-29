#!/usr/bin/env node
// Veröffentlicht den Cubiq Client im eigenen Update-Kanal (GitHub-Release `client-mod`).
// Läuft normalerweise automatisch: Bringt ein Launcher-Release einen neuen Cubiq Client (scripts/release.mjs
// erhöht dann mod_version), baut .github/workflows/client-mod.yml alle Jars und ruft dieses Skript auf.
//
//   node scripts/publish-client-mod.mjs --dry-run      nur lokal: Manifest + Signatur schreiben und prüfen
//   node scripts/publish-client-mod.mjs                 zusätzlich hochladen (gh release upload --clobber)
//   node scripts/publish-client-mod.mjs --bump minor   mod_version in allen gradle.properties erhöhen
//
// Ablauf von Hand: Jars mit `collectLauncherJars` nach client-mod/dist bauen (Projekte nacheinander),
// dann dieses Skript. Es liest dist/builds.json + dist/builds-*.json, schreibt
// client-mod.json { version, builds[] mit sha256/size }, signiert es mit dem Schlüssel
// des Tauri-Updaters (`tauri signer sign`) und prüft die Signatur selbst nach.
// Dieselbe Datei (ohne Signatur) landet mit den Jars in src-tauri/resources/client-mod,
// damit der nächste Launcher-Release den Stand mitbringt.
//
// Optionen:
//   --dist <ordner>          Jars + builds*.json (Standard: client-mod/dist)
//   --out <ordner>           Ausgabe für client-mod.json(.sig) (Standard: client-mod/dist/channel)
//   --no-bundle              src-tauri/resources/client-mod nicht anfassen (so im CI)
//   --no-sign                nur mit --dry-run: Manifest ohne Signatur (Prüfung im Pull Request, ohne Schlüssel)
//   --expect-version <v>     abbrechen, wenn mod_version nicht genau <v> ist (CI: Version aus dem Release)
//   --skip-if-published      nichts tun, wenn der Kanal genau diese Version schon hat (Wiederholung im CI)
//   --force                  auch veröffentlichen, wenn die Version nicht neuer ist als im Kanal
//   --repo <o/r>             Standard: PwiePixel/Cubiq-launcher
//
// Schlüssel: im CI TAURI_SIGNING_PRIVATE_KEY (+ _PASSWORD) aus den Secrets; lokal
// %USERPROFILE%\.tauri\cubiq-launcher.key (+ .password). Beides nur per Umgebungsvariable an den Signierer – nie ausgegeben.

import { spawnSync } from 'node:child_process'
import { createHash, createPublicKey, verify as verifySignature } from 'node:crypto'
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { bumpModVersion, CHANNEL_MANIFEST, CHANNEL_TAG, channelModVersion, isNewer, readModVersion, writeModVersion } from './client-mod-version.mjs'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const TAG = CHANNEL_TAG
const MANIFEST = CHANNEL_MANIFEST
const SIGNATURE = `${MANIFEST}.sig`
const LOADERS = new Set(['fabric', 'forge', 'neoforge'])
// Muss zu MAX_JAR_BYTES in src-tauri/crates/core/src/client_mod_update.rs passen.
const MAX_JAR_BYTES = 32 * 1024 * 1024
const FILE_RE = /^[A-Za-z0-9][A-Za-z0-9._+-]*\.jar$/
const BUNDLE_DIR = join(ROOT, 'src-tauri', 'resources', 'client-mod')

function fail(message) {
  console.error(`Fehler: ${message}`)
  process.exit(1)
}

function parseArgs(argv) {
  const args = {
    dryRun: false,
    bundle: true,
    sign: true,
    force: false,
    skipIfPublished: false,
    expectVersion: null,
    bump: null,
    dist: join(ROOT, 'client-mod', 'dist'),
    out: null,
    repo: 'PwiePixel/Cubiq-launcher',
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const value = () => argv[++i] ?? fail(`${a} braucht einen Wert`)
    if (a === '--dry-run') args.dryRun = true
    else if (a === '--no-bundle') args.bundle = false
    else if (a === '--no-sign') args.sign = false
    else if (a === '--force') args.force = true
    else if (a === '--skip-if-published') args.skipIfPublished = true
    else if (a === '--expect-version') args.expectVersion = value()
    else if (a === '--bump') args.bump = value()
    else if (a === '--dist') args.dist = resolve(value())
    else if (a === '--out') args.out = resolve(value())
    else if (a === '--repo') args.repo = value()
    else if (a === '--help' || a === '-h') {
      console.log(readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').filter((l) => l.startsWith('//')).map((l) => l.slice(3)).join('\n'))
      process.exit(0)
    } else fail(`Unbekannte Option ${a}`)
  }
  if (!args.sign && !args.dryRun) fail('--no-sign geht nur mit --dry-run (unsigniert lädt kein Launcher)')
  args.out ??= join(args.dist, 'channel')
  return args
}

// --- Version --------------------------------------------------------------------

function modVersion() {
  try {
    return readModVersion(ROOT)
  } catch (e) {
    return fail(e.message)
  }
}

function bump(kind) {
  const current = modVersion()
  if (!['major', 'minor', 'patch'].includes(kind)) fail('--bump braucht major, minor oder patch')
  const next = bumpModVersion(current, kind)
  const files = writeModVersion(ROOT, next)
  console.log(`mod_version ${current} → ${next} (${files} Dateien).`)
  console.log('Jetzt die Jars neu bauen (collectLauncherJars je Projekt), dann veröffentlichen.')
}

// --- Manifest --------------------------------------------------------------------

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex')
}

function readBuilds(dist) {
  if (!existsSync(dist)) fail(`${dist} fehlt – erst collectLauncherJars laufen lassen`)
  const lists = readdirSync(dist)
    .filter((f) => f === 'builds.json' || /^builds-.+\.json$/.test(f))
    // builds.json (Fabric) zuerst, dann die anderen alphabetisch – wie bisher im Launcher-Paket.
    .sort((a, b) => (a === 'builds.json' ? -1 : b === 'builds.json' ? 1 : a.localeCompare(b)))
  if (lists.length === 0) fail(`keine builds*.json in ${dist}`)
  const builds = []
  const seen = new Map()
  for (const list of lists) {
    const entries = JSON.parse(readFileSync(join(dist, list), 'utf8'))
    if (!Array.isArray(entries)) fail(`${list} ist keine Liste`)
    for (const e of entries) {
      if (!LOADERS.has(e.loader)) fail(`${list}: unbekannter Loader ${e.loader}`)
      if (typeof e.file !== 'string' || !FILE_RE.test(e.file)) fail(`${list}: unsicherer Dateiname ${e.file}`)
      if (!Array.isArray(e.minecraft) || e.minecraft.length === 0) fail(`${list}: ${e.file} ohne Minecraft-Version`)
      for (const mc of e.minecraft) {
        const key = `${e.loader} ${mc}`
        if (seen.has(key)) fail(`${key} doppelt (${seen.get(key)} und ${e.file})`)
        seen.set(key, e.file)
      }
      const file = join(dist, e.file)
      if (!existsSync(file)) fail(`${e.file} fehlt in ${dist}`)
      const size = statSync(file).size
      if (size === 0 || size > MAX_JAR_BYTES) fail(`${e.file}: Größe ${size} außerhalb der Grenzen`)
      const { loader, minecraft, file: name, requires = [], ...extra } = e
      builds.push({ loader, minecraft, file: name, requires, ...extra, sha256: sha256(file), size })
    }
  }
  return builds
}

// --- Signatur --------------------------------------------------------------------

function sign(file) {
  const cli = join(ROOT, 'node_modules', '@tauri-apps', 'cli', 'tauri.js')
  if (!existsSync(cli)) fail('Tauri-CLI fehlt – erst `pnpm install`')
  const candidates = []
  if (process.env.TAURI_SIGNING_PRIVATE_KEY) {
    // CI: Schlüssel aus den Secrets (von scripts/check-signing.mjs --export bereinigt).
    candidates.push({ ...process.env })
  } else {
    const keyPath = join(homedir(), '.tauri', 'cubiq-launcher.key')
    const passwordPath = `${keyPath}.password`
    if (!existsSync(keyPath)) fail(`Signier-Schlüssel fehlt: ${keyPath} (oder TAURI_SIGNING_PRIVATE_KEY setzen)`)
    const raw = existsSync(passwordPath) ? readFileSync(passwordPath, 'utf8') : ''
    // Zuerst ohne Zeilenende am Ende der Passwortdatei, notfalls genau wie gespeichert.
    for (const password of new Set([raw.replace(/\r?\n$/, ''), raw])) {
      const env = { ...process.env, TAURI_SIGNING_PRIVATE_KEY_PATH: keyPath, TAURI_SIGNING_PRIVATE_KEY_PASSWORD: password }
      delete env.TAURI_SIGNING_PRIVATE_KEY
      candidates.push(env)
    }
  }
  let last = null
  for (const env of candidates) {
    last = spawnSync(process.execPath, [cli, 'signer', 'sign', file], { env, stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' })
    if (last.status === 0 && existsSync(`${file}.sig`)) return
  }
  fail(`Signieren fehlgeschlagen (${last?.status}):\n${last?.stderr ?? ''}`)
}

/** Prüft die Signatur wie der Launcher (Minisign, Prehash, file:client-mod.json). */
function verifyLikeTheLauncher(data, sigFileContent) {
  const conf = JSON.parse(readFileSync(join(ROOT, 'src-tauri', 'tauri.conf.json'), 'utf8'))
  const pubLines = Buffer.from(conf.plugins.updater.pubkey, 'base64').toString('utf8').split(/\r?\n/)
  const pub = Buffer.from(pubLines[1], 'base64')
  const sigLines = Buffer.from(sigFileContent.trim(), 'base64').toString('utf8').split(/\r?\n/)
  const sig = Buffer.from(sigLines[1], 'base64')
  const trusted = sigLines[2]?.replace(/^trusted comment: /, '') ?? ''
  const globalSig = Buffer.from(sigLines[3] ?? '', 'base64')
  if (pub.length !== 42 || sig.length !== 74 || globalSig.length !== 64) fail('Signatur/Schlüssel im falschen Format')
  if (sig.subarray(0, 2).toString() !== 'ED') fail('Signatur ist keine Prehash-Signatur')
  if (!sig.subarray(2, 10).equals(pub.subarray(2, 10))) fail('Signatur stammt von einem anderen Schlüssel als in tauri.conf.json')
  const key = createPublicKey({ key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), pub.subarray(10)]), format: 'der', type: 'spki' })
  const digest = createHash('blake2b512').update(data).digest()
  const ok =
    verifySignature(null, digest, key, sig.subarray(10)) &&
    verifySignature(null, Buffer.concat([sig.subarray(10), Buffer.from(trusted)]), key, globalSig)
  if (!ok) fail('Signatur ungültig')
  if (!trusted.split('\t').includes(`file:${MANIFEST}`)) fail(`signierter Kommentar nennt nicht file:${MANIFEST}`)
}

// --- Launcher-Paket ------------------------------------------------------------

function bundle(dist, manifestText, builds) {
  mkdirSync(BUNDLE_DIR, { recursive: true })
  const wanted = new Set(builds.map((b) => b.file))
  for (const f of readdirSync(BUNDLE_DIR)) {
    if (f.endsWith('.jar') && !wanted.has(f)) rmSync(join(BUNDLE_DIR, f))
  }
  let copied = 0
  for (const b of builds) {
    const target = join(BUNDLE_DIR, b.file)
    if (!existsSync(target) || sha256(target) !== b.sha256) {
      copyFileSync(join(dist, b.file), target)
      copied++
    }
  }
  writeFileSync(join(BUNDLE_DIR, 'builds.json'), manifestText)
  console.log(`Launcher-Paket: ${BUNDLE_DIR} (${copied} Jars aktualisiert, builds.json im neuen Format)`)
}

// --- GitHub ------------------------------------------------------------------------

function gh(args, { allowFail = false } = {}) {
  const r = spawnSync('gh', args, { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' })
  if (r.error) fail(`gh nicht gefunden: ${r.error.message}`)
  if (r.status !== 0 && !allowFail) fail(`gh ${args.slice(0, 2).join(' ')} fehlgeschlagen:\n${r.stderr}`)
  return r
}

function upload(repo, dist, out, builds) {
  if (gh(['auth', 'status'], { allowFail: true }).status !== 0) fail('gh ist nicht angemeldet (`gh auth login`)')
  if (gh(['release', 'view', TAG, '-R', repo], { allowFail: true }).status !== 0) {
    gh([
      'release', 'create', TAG, '-R', repo, '--prerelease', '--latest=false',
      '--title', 'Cubiq Client – Update-Kanal',
      '--notes', 'Wird automatisch gepflegt: neue Versionen des Cubiq Clients (In-Game-Mod) für den Cubiq Launcher. ' +
        'client-mod.json ist mit dem Launcher-Schlüssel signiert, der Launcher lädt die Jars selbst – nichts davon von Hand installieren.',
    ])
    console.log(`Release "${TAG}" angelegt.`)
  }
  // Erst die Jars, dann Manifest + Signatur: wer zwischendurch lädt, bekommt
  // höchstens eine Prüfsummen-Abweichung und nimmt die mitgelieferte Version.
  const jars = builds.map((b) => join(dist, b.file))
  for (let i = 0; i < jars.length; i += 40) {
    gh(['release', 'upload', TAG, '-R', repo, '--clobber', ...jars.slice(i, i + 40)])
    console.log(`  ${Math.min(i + 40, jars.length)}/${jars.length} Jars hochgeladen`)
  }
  gh(['release', 'upload', TAG, '-R', repo, '--clobber', join(out, SIGNATURE), join(out, MANIFEST)])
  // Jars, die es nicht mehr gibt, aus dem Release nehmen.
  const assets = JSON.parse(gh(['release', 'view', TAG, '-R', repo, '--json', 'assets']).stdout).assets.map((a) => a.name)
  const keep = new Set([MANIFEST, SIGNATURE, ...builds.map((b) => b.file)])
  for (const name of assets.filter((n) => !keep.has(n))) {
    gh(['release', 'delete-asset', TAG, name, '-R', repo, '--yes'])
    console.log(`  alte Datei entfernt: ${name}`)
  }
}

// --- Ablauf ------------------------------------------------------------------------

const args = parseArgs(process.argv.slice(2))
if (args.bump) {
  bump(args.bump)
  process.exit(0)
}

const version = modVersion()
if (args.expectVersion && args.expectVersion !== version) {
  fail(`mod_version ist ${version}, erwartet war ${args.expectVersion} – falscher Stand ausgecheckt?`)
}
const builds = readBuilds(args.dist)
const manifestText = `${JSON.stringify({ version, builds }, null, 2)}\n`
console.log(`Cubiq Client ${version}: ${builds.length} Builds aus ${args.dist}`)

const current = await channelModVersion(args.repo)
if (current === undefined) console.log('Kanal: aktueller Stand nicht abrufbar (offline?)')
else console.log(`Kanal: ${current ?? 'noch leer'}`)
if (args.skipIfPublished && current === version && !args.force) {
  console.log(`Cubiq Client ${version} ist schon im Kanal – nichts zu tun.`)
  process.exit(0)
}
if (current && !isNewer(version, current) && !args.force) {
  const msg = `Version ${version} ist nicht neuer als ${current} im Kanal – Launcher würden sie ignorieren. Erst \`--bump patch\` und neu bauen (oder --force).`
  if (args.dryRun) console.warn(`Warnung: ${msg}`)
  else fail(msg)
}

mkdirSync(args.out, { recursive: true })
const manifestFile = join(args.out, MANIFEST)
writeFileSync(manifestFile, manifestText)
rmSync(`${manifestFile}.sig`, { force: true })
if (args.sign) {
  sign(manifestFile)
  verifyLikeTheLauncher(readFileSync(manifestFile), readFileSync(`${manifestFile}.sig`, 'utf8'))
  console.log(`Signiert und geprüft: ${manifestFile}(.sig)`)
} else {
  console.log(`Ohne Signatur geschrieben: ${manifestFile}`)
}

if (args.bundle) bundle(args.dist, manifestText, builds)

if (args.dryRun) {
  console.log('Dry-Run: nichts hochgeladen.')
} else {
  upload(args.repo, args.dist, args.out, builds)
  console.log(`Veröffentlicht: https://github.com/${args.repo}/releases/tag/${TAG}`)
}
console.log(`Fertig (${basename(args.out)}).`)
