// Texte der GitHub-Releases aus CHANGELOG.md neu schreiben – nachdem Hinweise verbessert wurden, ein Bild dazukam
// oder sich das Format geändert hat (z. B. ohne Danksagung). Für jedes veröffentlichte Release v<x.y.z> (keine
// Entwürfe) mit Abschnitt im Changelog: Text wie beim Release (scripts/changelog.mjs notes), Bilder vom Stand `main`.
// Geändert wird nur, was sich unterscheidet. Läuft in .github/workflows/release-notes.yml.
//
//   node --experimental-strip-types scripts/release-notes-sync.mjs [--dry-run]

import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { changelogFor, parseChangelog, releaseNotes } from '../app/utils/changelog.ts'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const dryRun = process.argv.includes('--dry-run')
const gh = (args) => execFileSync('gh', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] })

const entries = parseChangelog(readFileSync(join(root, 'CHANGELOG.md'), 'utf8'))
const releases = JSON.parse(gh(['release', 'list', '--limit', '200', '--json', 'tagName,isDraft']))
const tmp = mkdtempSync(join(tmpdir(), 'cubiq-notes-'))
let changed = 0
try {
  for (const { tagName, isDraft } of releases) {
    if (isDraft || !/^v\d+\.\d+\.\d+$/.test(tagName)) continue
    const entry = changelogFor(entries, tagName)
    if (!entry?.en || !entry?.de) {
      console.error(`${tagName}: kein vollständiger Abschnitt in CHANGELOG.md – bleibt, wie er ist.`)
      continue
    }
    const notes = releaseNotes(entry, { ref: 'main' })
    const body = JSON.parse(gh(['release', 'view', tagName, '--json', 'body'])).body ?? ''
    if (body.replace(/\r\n/g, '\n').trim() === notes.trim()) continue
    changed++
    if (dryRun) {
      console.error(`${tagName}: würde neu geschrieben.`)
      continue
    }
    const file = join(tmp, `${tagName}.md`)
    writeFileSync(file, notes)
    gh(['release', 'edit', tagName, '--notes-file', file])
    console.error(`${tagName}: Text aus CHANGELOG.md übernommen.`)
  }
} finally {
  rmSync(tmp, { recursive: true, force: true })
}
console.error(changed ? `${changed} Release-Text(e) ${dryRun ? 'wären' : 'wurden'} aktualisiert.` : 'Alle Release-Texte sind aktuell.')
