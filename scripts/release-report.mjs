// Release-Bericht für .github/workflows/release.yml: sagt auf der Release-Seite und im Lauf, was gebaut wurde und –
// wenn der Windows-Installer fehlt – warum.
//
//   node scripts/release-report.mjs job <windows|linux>   → out/status-<plattform>.json (am Ende jedes Build-Jobs)
//   node scripts/release-report.mjs start                 → Release-Entwurf „wird gebaut“ (Job „prepare“)
//   node scripts/release-report.mjs finish                → Dateien hochladen, veröffentlichen oder Entwurf mit Grund
//
// Ohne Windows-Installer bleibt das Release ein Entwurf (nur für Maintainer sichtbar) mit dem Grund ganz oben – Spieler
// sehen nie ein Release ohne .exe. Umgebung: TAG, GITHUB_REPOSITORY, RUN_URL, GH_TOKEN und je Befehl die unten
// genannten Variablen.

import { spawnSync } from 'node:child_process'
import { appendFileSync, copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

/** Die letzten aussagekräftigen Fehlerzeilen aus einem Build-Log (höchstens `max`, gekürzt). */
export function errorLines(log, max = 6) {
  const lines = String(log ?? '')
    .split(/\r?\n/)
    // eslint-disable-next-line no-control-regex -- Farbcodes der Konsole entfernen
    .map((line) => line.replace(/\u001b\[[0-9;]*m/g, '').trimEnd())
    .filter((line) => /\berror\b|\bfailed\b|panicked|Error:/i.test(line) && !/^\s*warning\b|0 errors?\b|error_chain/i.test(line))
  return [...new Set(lines)].slice(-max).map((line) => (line.length > 300 ? `${line.slice(0, 297)}…` : line.trim()))
}

const STEP_STATE = { failure: 'ist fehlgeschlagen', cancelled: 'wurde abgebrochen', skipped: 'wurde übersprungen' }

/**
 * Warum fehlt der Windows-Installer? `null` = er ist da.
 * @param {{ exe: boolean, changelog: string, changelogReason?: string, job?: string,
 *   status?: { notes?: string, build?: string, collect?: string, error?: string[] } | null }} s
 */
export function windowsProblem(s) {
  if (s.exe) return null
  if (s.changelog && s.changelog !== 'ok') {
    return `CHANGELOG.md ist für diese Version nicht vollständig: ${s.changelogReason || 'Abschnitt, Englisch, Deutsch oder Banner fehlt'}.`
  }
  const st = s.status
  if (!st) {
    if (s.job && s.job !== 'success') return `Der Windows-Job ${STEP_STATE[s.job] ?? `endete mit „${s.job}“`}, bevor er einen Bericht schreiben konnte (z. B. Abhängigkeiten nicht installierbar) – siehe Log des Jobs „Windows“.`
    return 'Der Windows-Job hat keinen Installer geliefert – siehe Log des Jobs „Windows“.'
  }
  if (st.notes && st.notes !== 'success') return `Die Release-Hinweise aus CHANGELOG.md ${STEP_STATE[st.notes] ?? 'fehlen'}.`
  if (st.build === 'failure') {
    const lines = st.error?.length ? `\n\n\`\`\`\n${st.error.join('\n')}\n\`\`\`` : ''
    return `Der Windows-Build (tauri build) ist fehlgeschlagen.${lines}`
  }
  if (st.build && st.build !== 'success') return `Der Windows-Build ${STEP_STATE[st.build] ?? `endete mit „${st.build}“`}.`
  if (st.collect && st.collect !== 'success') return 'Der Build lief durch, aber der Installer lag nicht in src-tauri/target/release/bundle/nsis.'
  return 'Der Windows-Job hat keinen Installer geliefert – siehe Log des Jobs „Windows“.'
}

/** Text des Entwurfs, solange der Windows-Installer fehlt. */
export function draftBody({ problem, runUrl, tag, notes }) {
  return [
    '> [!WARNING]',
    '> **Kein Windows-Installer (.exe) in diesem Release** – deshalb ist es nur ein Entwurf, den Spieler nicht sehen.',
    '>',
    ...problem.split('\n').map((line) => (line ? `> ${line}` : '>')),
    '>',
    `> Log: ${runUrl}`,
    `> Nach dem Beheben: Actions → Release → Run workflow mit dem Tag \`${tag}\` – dann wird das Release normal veröffentlicht.`,
    '',
    notes ?? '',
  ].join('\n')
}

/** Text des Entwurfs, während gebaut wird. */
export function buildingBody({ runUrl }) {
  return ['> [!NOTE]', `> ⏳ Wird gerade gebaut – Installer und Pakete kommen in einigen Minuten dazu. Fortschritt: ${runUrl}`].join('\n')
}

/** Übersicht für die Zusammenfassung des Laufs. */
export function summaryMarkdown(r) {
  const row = (name, ok, text) => `| ${name} | ${ok === true ? '✅' : ok === false ? '❌' : '⚠️'} ${text} |`
  return [
    `### Release ${r.tag}`,
    '',
    '| | |',
    '|---|---|',
    row('Windows-Installer (.exe)', r.exe, r.exe ? r.exeName : (r.problem ?? '').split('\n')[0]),
    row('Portable ZIP', r.zip, r.zip ? 'dabei' : 'fehlt'),
    row('Linux (AppImage, .deb, .rpm)', r.linux, r.linux ? 'dabei' : r.linuxReason || 'fehlt – siehe Job „Linux“'),
    row('Update-Signatur', r.signing === 'ok' ? true : null, r.signingReason || r.signing),
    row('Auto-Update (latest.json)', r.updater ? true : null, r.updater ? 'Update-Kanal aktualisiert' : r.updaterReason),
    row('Release', r.published ? true : null, r.published ? `veröffentlicht: ${r.url}` : `Entwurf (nur für Maintainer sichtbar): ${r.url}`),
    '',
    ...(r.problem ? ['**Warum fehlt der Windows-Installer?**', '', r.problem, ''] : []),
  ].join('\n')
}

// ------------------------------------------------------------------------------------------------ Befehle

function gh(args, { allowFail = false } = {}) {
  const r = spawnSync('gh', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  if (r.status !== 0 && !allowFail) throw new Error(`gh ${args.slice(0, 2).join(' ')}: ${r.stderr || r.stdout}`)
  return r
}

function release(tag) {
  const r = gh(['release', 'view', tag, '--json', 'isDraft,assets,url'], { allowFail: true })
  return r.status === 0 ? JSON.parse(r.stdout) : null
}

const hasExe = (rel) => !!rel?.assets?.some((a) => /_x64-setup\.exe$/.test(a.name))

function node(script, args) {
  const r = spawnSync(process.execPath, ['--experimental-strip-types', join(root, 'scripts', script), ...args], { cwd: root, encoding: 'utf8' })
  if (r.status !== 0) throw new Error(`${script}: ${r.stderr}`)
  return r.stdout
}

function cmdJob(platform) {
  const out = join(root, 'out')
  mkdirSync(out, { recursive: true })
  const log = process.env.BUILD_LOG && existsSync(process.env.BUILD_LOG) ? readFileSync(process.env.BUILD_LOG, 'utf8') : ''
  const status = {
    platform,
    notes: process.env.STEP_NOTES || 'skipped',
    build: process.env.STEP_BUILD || 'skipped',
    collect: process.env.STEP_COLLECT || 'skipped',
    signing: process.env.SIGNING || 'missing',
    error: process.env.STEP_BUILD === 'failure' ? errorLines(log) : [],
  }
  writeFileSync(join(out, `status-${platform}.json`), JSON.stringify(status, null, 2))
  console.error(`Status ${platform}: ${JSON.stringify(status)}`)
}

function cmdStart() {
  const tag = process.env.TAG
  const version = tag.replace(/^v/, '')
  const existing = release(tag)
  if (existing && !existing.isDraft && hasExe(existing)) {
    console.error(`${tag}: schon mit Installer veröffentlicht – Dateien werden am Ende ersetzt.`)
    return
  }
  const title = node('changelog.mjs', ['title', version]).trim() || `Cubiq Launcher ${version}`
  const body = buildingBody({ runUrl: process.env.RUN_URL })
  if (existing) gh(['release', 'edit', tag, '--draft=true', '--title', title, '--notes', body])
  else gh(['release', 'create', tag, '--draft', '--verify-tag', '--title', title, '--notes', body])
  console.error(`${tag}: Entwurf „wird gebaut“ angelegt.`)
}

function copyMatching(from, to, test, rename = (name) => name) {
  if (!existsSync(from)) return []
  const copied = []
  for (const name of readdirSync(from)) {
    if (!test(name)) continue
    copyFileSync(join(from, name), join(to, rename(name)))
    copied.push(rename(name))
  }
  return copied
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, 'utf8'))
  } catch {
    return null
  }
}

function cmdFinish() {
  const tag = process.env.TAG
  const version = tag.replace(/^v/, '')
  const repo = process.env.GITHUB_REPOSITORY
  const runUrl = process.env.RUN_URL
  const art = join(root, 'artifacts')
  const out = join(root, 'out')
  mkdirSync(out, { recursive: true })

  copyMatching(join(art, 'windows'), out, (n) => n.startsWith('Cubiq-Launcher_'))
  const linuxFiles = copyMatching(join(art, 'linux'), out, (n) => n.startsWith('Cubiq-Launcher') || n === 'SHA256SUMS-linux.txt')
  copyMatching(join(art, 'linux', 'aur'), out, (n) => n === 'PKGBUILD' || n === '.SRCINFO', (n) => (n === 'PKGBUILD' ? 'cubiq-launcher-bin.PKGBUILD' : 'cubiq-launcher-bin.SRCINFO'))

  const exeName = `Cubiq-Launcher_${version}_x64-setup.exe`
  const exe = existsSync(join(out, exeName))
  const zip = existsSync(join(out, `Cubiq-Launcher_${version}_x64-portable.zip`))
  const linux = linuxFiles.some((n) => n.endsWith('.AppImage'))
  const winStatus = readJson(join(art, 'windows', 'status-windows.json'))
  const linuxStatus = readJson(join(art, 'linux', 'status-linux.json'))

  // Hinweise wie im Launcher (mit Dank an die Mitwirkenden), sofern CHANGELOG.md für die Version passt.
  let notes = ''
  if (process.env.CHANGELOG === 'ok') {
    if (process.env.CONTRIBUTORS) node('changelog.mjs', ['set-contributors', version, process.env.CONTRIBUTORS])
    notes = node('changelog.mjs', ['notes', version])
  }
  writeFileSync(join(root, 'release-notes.md'), notes)

  // Update-Kanal nur mit signierten Teilen für Windows UND Linux (sonst fänden manche Launcher kein Update).
  const parts = [join(art, 'windows', 'platform-windows.json'), join(art, 'linux', 'platform-linux.json')]
  let updater = false
  let updaterReason = ''
  if (exe && parts.every((p) => existsSync(p))) {
    const manifest = node('updater-manifest.mjs', ['merge', version, join(root, 'release-notes.md'), repo, tag, ...parts])
    writeFileSync(join(out, 'latest.json'), manifest)
    updater = true
  } else if (process.env.SIGNING !== 'ok') {
    updaterReason = `keine Update-Signatur (${process.env.SIGNING_REASON || process.env.SIGNING || 'Schlüssel fehlt'}) – installierte Launcher bekommen diese Version nicht automatisch`
  } else {
    updaterReason = `signierte Pakete fehlen (${[!existsSync(parts[0]) && 'Windows', !existsSync(parts[1]) && 'Linux'].filter(Boolean).join(' und ')}) – Update-Kanal bleibt auf der vorigen Version`
  }

  const problem = windowsProblem({
    exe,
    changelog: process.env.CHANGELOG,
    changelogReason: process.env.CHANGELOG_REASON,
    job: process.env.WINDOWS_RESULT,
    status: winStatus,
  })
  const title = (process.env.CHANGELOG === 'ok' && node('changelog.mjs', ['title', version]).trim()) || `Cubiq Launcher ${version}`
  const body = problem ? draftBody({ problem, runUrl, tag, notes }) : notes
  writeFileSync(join(root, 'release-body.md'), body)

  if (!release(tag)) gh(['release', 'create', tag, '--draft', '--verify-tag', '--title', title, '--notes-file', 'release-body.md'])
  const files = readdirSync(out).filter((n) => n !== 'latest.json' && !n.startsWith('status-')).map((n) => join('out', n))
  if (files.length) gh(['release', 'upload', tag, ...files, '--clobber'])
  if (problem) {
    gh(['release', 'edit', tag, '--draft=true', '--title', title, '--notes-file', 'release-body.md'])
  } else {
    gh(['release', 'edit', tag, '--draft=false', '--prerelease=false', '--latest', '--title', title, '--notes-file', 'release-body.md'])
    if (updater) {
      // Fester Kanal (Release „updater“), den der Auto-Updater abfragt.
      if (gh(['release', 'view', 'updater'], { allowFail: true }).status !== 0) {
        gh(['release', 'create', 'updater', '--prerelease', '--latest=false', '--title', 'Update-Kanal', '--notes', 'Wird automatisch gepflegt – enthält nur latest.json für den Auto-Updater.'])
      }
      gh(['release', 'upload', 'updater', join('out', 'latest.json'), '--clobber'])
    }
  }

  const url = release(tag)?.url ?? `https://github.com/${repo}/releases/tag/${tag}`
  const linuxReason = linux ? '' : linuxStatus?.build === 'failure' ? `Build fehlgeschlagen: ${linuxStatus.error?.at(-1) ?? 'siehe Job „Linux“'}` : `Job „Linux“: ${process.env.LINUX_RESULT || 'kein Ergebnis'}`
  const summary = summaryMarkdown({
    tag,
    exe,
    exeName,
    zip,
    linux,
    linuxReason,
    signing: process.env.SIGNING,
    signingReason: process.env.SIGNING_REASON,
    updater,
    updaterReason,
    published: !problem,
    url,
    problem,
  })
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summary}\n`)
  console.error(summary)
  if (problem) {
    const oneLine = problem.split('\n')[0].replace(/%/g, '%25')
    console.log(`::error title=Kein Windows-Installer in ${tag}::${oneLine} Das Release bleibt ein Entwurf: ${url}`)
    process.exitCode = 1
  } else if (!updater) {
    console.log(`::warning title=Kein Auto-Update für ${tag}::${updaterReason.replace(/%/g, '%25')}`)
  }
  console.error(`Dateien: ${files.map((f) => basename(f)).join(', ') || '(keine)'}`)
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [command, arg] = process.argv.slice(2)
  try {
    if (command === 'job' && (arg === 'windows' || arg === 'linux')) cmdJob(arg)
    else if (command === 'start') cmdStart()
    else if (command === 'finish') cmdFinish()
    else {
      console.error('Aufruf: release-report.mjs job <windows|linux> | start | finish')
      process.exit(2)
    }
  } catch (e) {
    console.log(`::error title=Release-Bericht::${String(e instanceof Error ? e.message : e).replace(/%/g, '%25').replace(/\r?\n/g, '%0A')}`)
    process.exit(1)
  }
}
