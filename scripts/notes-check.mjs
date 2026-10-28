// PR-Prüfung „Spieler-Text“: Ändert ein Pull Request etwas, das Spieler bekommen, bringt er einen Spieler-Text mit –
// eine Datei changes/<thema>.md mit „### English“ und „### Deutsch“ (siehe changes/README.md) oder Zeilen „EN: …“
// und „DE: …“ in jedem betroffenen Commit. So sind die Release-Hinweise immer für Spieler geschrieben statt aus
// Commit-Zeilen zusammengesetzt.
// Nicht nötig für Commits, die laut scripts/release-lib.mjs nicht in den Changelog gehören (refactor:, test:, ci:,
// docs:, chore:, Abhängigkeits-Updates, „[skip changelog]“, nur Doku/Tests/Workflows/Skripte).
//
//   node --experimental-strip-types scripts/notes-check.mjs <basis> <kopf>     (Commits/Dateien aus basis...kopf)

import { execFileSync } from 'node:child_process'
import { appendFileSync, existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parseShot, SHOTS_MAX } from '../app/utils/changelog.ts'
import { imageInfo, SHOT_MAX_BYTES, SHOT_MAX_SIZE, SHOT_MIN_SIZE } from './news-shots.mjs'
import { changelogItem, NOTE_SHOT, parseChangeNote, parseGitLog } from './release-lib.mjs'

/**
 * Ergebnis der Prüfung aus den Commits des PRs und den Texten der geänderten Dateien in changes/.
 * `prepared`: Der PR bringt schon den Abschnitt einer neuen Version in CHANGELOG.md mit (Release im Branch
 * vorbereitet, z. B. für eine feste Versionsnummer) – die Spieler-Texte stehen dann dort statt in changes/.
 * @param {{ subject: string, body: string, files: string[] }[]} commits
 * @param {{ name: string, text: string }[]} notes
 * @param {{ prepared?: boolean }} [options]
 * @returns {{ ok: boolean, problems: string[], needed: string[] }}
 */
export function checkNotes(commits, notes, { prepared = false } = {}) {
  const problems = []
  for (const { name, text } of notes) {
    const english = /^###\s+english\s*$/im.test(text)
    const german = /^###\s+deutsch\s*$/im.test(text)
    const note = parseChangeNote(text)
    if (!note) problems.push(`changes/${name}: keine Punkte („- …“) unter „### English“ / „### Deutsch“.`)
    else if (!english || !german) problems.push(`changes/${name}: bitte beide Sprachen – „### English“ und „### Deutsch“.`)
    if (/@[A-Za-z0-9-]+|thanks to|danke an/i.test(text)) problems.push(`changes/${name}: keine Namen, Erwähnungen oder Danksagungen – nur, was sich für Spieler ändert.`)
  }
  const needed = commits
    .filter((c) => changelogItem(c))
    .filter((c) => !(/^EN:\s*\S/im.test(c.body) && /^DE:\s*\S/im.test(c.body)))
    .map((c) => c.subject)
  const hasNote = prepared || notes.some(({ text }) => parseChangeNote(text))
  if (needed.length && !hasNote) {
    problems.push(
      [
        'Dieser Pull Request ändert etwas für Spieler, bringt aber keinen Spieler-Text mit:',
        ...needed.map((s) => `  - ${s}`),
        'Bitte eine Datei changes/<thema>.md anlegen (Vorlage und Regeln: changes/README.md), z. B.:',
        '  ### English',
        '  - **Short headline.** What changed for players and why it helps.',
        '  ### Deutsch',
        '  - **Kurze Überschrift.** Was sich für Spieler ändert und was es bringt.',
        'Ändert sich für Spieler nichts: Commit mit refactor:, test:, ci:, docs: oder chore: beginnen.',
      ].join('\n'),
    )
  }
  return { ok: problems.length === 0, problems, needed }
}

/**
 * Screenshots der Spieler-Texte (Kommentar „shots:“ über „### English“): nur Dateinamen von Bildern in changes/,
 * die es gibt, echtes PNG/WebP in der Größe der Update-Bilder, höchstens SHOTS_MAX je Text.
 * @param {{ name: string, text: string }[]} notes
 * @param {string} changesDir
 * @returns {string[]}
 */
export function checkNoteShots(notes, changesDir) {
  const problems = []
  for (const { name, text } of notes) {
    const entries = [...text.matchAll(/<!--\s*shots:([\s\S]*?)-->/gi)]
      .flatMap((m) => m[1].split(/\r?\n|;/))
      .map((line) => line.trim())
      .filter(Boolean)
    if (entries.length > SHOTS_MAX) problems.push(`changes/${name}: ${entries.length} Screenshots – höchstens ${SHOTS_MAX}.`)
    for (const entry of entries) {
      const [file = '', ...captions] = entry.split('|').map((part) => part.trim())
      const local = NOTE_SHOT.exec(file)?.[1]
      if (!local) {
        problems.push(`changes/${name}: „${file}“ – bitte nur den Dateinamen eines Screenshots in changes/ (PNG oder WebP), z. B. „neu.png | English | Deutsch“.`)
        continue
      }
      if (!parseShot([`/news/0.0.0/${local}`, ...captions].join(' | '))) {
        problems.push(`changes/${name}: Bildunterschrift zu „${local}“ ungültig – höchstens zwei (English | Deutsch), eine Zeile, ohne [ ] < >.`)
      }
      const path = join(changesDir, local)
      if (!existsSync(path)) {
        problems.push(`changes/${name}: Screenshot ${local} fehlt – bitte nach changes/${local} legen.`)
        continue
      }
      const bytes = readFileSync(path)
      if (bytes.length > SHOT_MAX_BYTES) problems.push(`changes/${local}: ${(bytes.length / 1048576).toFixed(1)} MB – höchstens ${SHOT_MAX_BYTES / 1048576} MB.`)
      const info = imageInfo(bytes.subarray(0, 64))
      if (!info) {
        problems.push(`changes/${local}: kein gültiges PNG/WebP.`)
        continue
      }
      if (info.format !== (local.toLowerCase().endsWith('.webp') ? 'webp' : 'png')) problems.push(`changes/${local}: Inhalt passt nicht zur Endung.`)
      const { width, height } = info
      if (width < SHOT_MIN_SIZE.width || height < SHOT_MIN_SIZE.height || width > SHOT_MAX_SIZE.width || height > SHOT_MAX_SIZE.height) {
        problems.push(`changes/${local}: ${width}×${height} px – erlaubt ${SHOT_MIN_SIZE.width}×${SHOT_MIN_SIZE.height} bis ${SHOT_MAX_SIZE.width}×${SHOT_MAX_SIZE.height}.`)
      }
    }
  }
  return problems
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]
if (isMain) {
  const root = join(dirname(fileURLToPath(import.meta.url)), '..')
  const [base, head] = process.argv.slice(2)
  if (!base || !head) {
    console.error('Aufruf: notes-check.mjs <basis> <kopf>')
    process.exit(2)
  }
  const git = (...args) => execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  const commits = parseGitLog(git('log', '--no-merges', '--name-only', '--format=%x1e%H%x1f%s%x1f%b%x1f', `${base}..${head}`))
  const names = git('diff', '--name-only', '--diff-filter=AM', `${base}...${head}`, '--', 'changes')
    .split('\n')
    .map((f) => f.trim())
    .filter((f) => /^changes\/[^/]+\.md$/.test(f) && !/readme\.md$/i.test(f))
  const notes = names.filter((f) => existsSync(join(root, f))).map((f) => ({ name: f.slice('changes/'.length), text: readFileSync(join(root, f), 'utf8') }))
  // Neuer Versions-Abschnitt („## x.y.z – …“) in CHANGELOG.md: das Release ist im Branch schon vorbereitet.
  const prepared = /^\+## \d+\.\d+\.\d+/m.test(git('diff', `${base}...${head}`, '--', 'CHANGELOG.md'))
  const checked = checkNotes(commits, notes, { prepared })
  const shotProblems = checkNoteShots(notes, join(root, 'changes'))
  const result = { ...checked, ok: checked.ok && !shotProblems.length, problems: [...checked.problems, ...shotProblems] }
  const summary = process.env.GITHUB_STEP_SUMMARY
  if (result.ok) {
    const what = notes.length
      ? `Spieler-Text: ${notes.map((n) => n.name).join(', ')}`
      : prepared
        ? 'Spieler-Text: vorbereiteter Versions-Abschnitt in CHANGELOG.md'
        : 'Nichts für Spieler geändert – kein Spieler-Text nötig.'
    console.log(what)
    if (summary) appendFileSync(summary, `### Spieler-Text\n\n✅ ${what}\n`)
    process.exit(0)
  }
  for (const p of result.problems) console.log(`::error title=Spieler-Text::${p.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')}`)
  if (summary) appendFileSync(summary, `### Spieler-Text fehlt oder passt nicht\n\n${result.problems.map((p) => `\`\`\`\n${p}\n\`\`\``).join('\n\n')}\n`)
  process.exit(1)
}
