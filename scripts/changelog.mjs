// Changelog für den Release-Build: gibt die Hinweise einer Version aus (Englisch + Deutsch)
// und bricht ab, wenn der Abschnitt fehlt, eine Sprache leer ist, das Update-Banner fehlt
// (Zeile „<!-- banner: accent=#rrggbb -->“, optional mit eigenem Motiv „motif=/news/<version>/banner.png“ – das Bild
// muss dann in public/ liegen, ohne Motiv zeigt der Launcher das Cubiq-Logo) oder angegebene Screenshots nicht passen
// (Kommentar „<!-- shots: … -->“, freiwillig, siehe scripts/news-shots.mjs).
// Den Abschnitt legt die Release-Automatik an (scripts/release.mjs, .github/workflows/auto-release.yml).
//
//   node --experimental-strip-types scripts/changelog.mjs notes 0.4.4   → Markdown für Release/Update
//   node --experimental-strip-types scripts/changelog.mjs check 0.4.4   → nur prüfen
//   node --experimental-strip-types scripts/changelog.mjs title 0.4.4   → Titel des GitHub-Release
//       („Cubiq Launcher 0.4.4“, mit Update-Namen aus der Überschrift „Cubiq Launcher 0.4.4 – The Clip Update“)
//   node --experimental-strip-types scripts/changelog.mjs set-contributors 0.4.4 alice,bob
//       → schreibt „<!-- contributors: alice,bob -->“ in den Abschnitt (ändert CHANGELOG.md; leere Liste = nichts
//         tun). Die Namen liefert scripts/contributors.mjs; der Release-Build ruft beides vor „notes“ auf.
//
// Der Parser ist derselbe wie im Launcher (app/utils/changelog.ts).

import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { changelogFor, parseChangelog, releaseNotes, releaseTitle, setContributors } from '../app/utils/changelog.ts'
import { checkShots } from './news-shots.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const changelogPath = join(root, 'CHANGELOG.md')
const [command, arg, extra] = process.argv.slice(2)

if (!['notes', 'check', 'title', 'set-contributors'].includes(command) || !arg) {
  console.error('Aufruf: changelog.mjs notes|check|title <version> | set-contributors <version> <a,b>')
  process.exit(2)
}

const version = arg.replace(/^v/, '')

if (command === 'set-contributors') {
  try {
    const text = readFileSync(changelogPath, 'utf8')
    const next = setContributors(text, version, (extra ?? '').split(','))
    if (next !== text) writeFileSync(changelogPath, next)
    const names = changelogFor(parseChangelog(next), version)?.contributors ?? []
    console.error(`CHANGELOG.md, Version ${version}: Mitwirkende ${names.length ? names.join(', ') : '(keine)'}`)
  } catch (e) {
    console.error(e instanceof Error ? e.message : String(e))
    process.exit(1)
  }
  process.exit(0)
}

const entry = changelogFor(parseChangelog(readFileSync(changelogPath, 'utf8')), version)
if (command === 'title') {
  console.log(releaseTitle(entry, version))
  process.exit(0)
}
if (!entry) {
  console.error(`CHANGELOG.md hat keinen Abschnitt „## ${version} – <Datum>“. Bitte vor dem Release eintragen.`)
  process.exit(1)
}
const missing = [!entry.en && 'English', !entry.de && 'Deutsch'].filter(Boolean)
if (missing.length) {
  console.error(`CHANGELOG.md, Version ${version}: Teil „${missing.join('“ und „')}“ ist leer.`)
  process.exit(1)
}
const banner = entry.banner
if (!banner) {
  console.error(
    [
      `CHANGELOG.md, Version ${version}: Update-Banner fehlt. Unter der Überschrift eintragen:`,
      '  <!-- banner: accent=#71a330 -->',
      `Eigenes Motiv (optional): „motif=/news/${version}/banner.png“ ergänzen und das Bild dort in public/ ablegen.`,
    ].join('\n'),
  )
  process.exit(1)
}
if (banner.motif && !existsSync(join(root, 'public', banner.motif))) {
  console.error(`CHANGELOG.md, Version ${version}: Motiv des Update-Banners fehlt: public${banner.motif}`)
  process.exit(1)
}
const shotProblems = checkShots(entry, join(root, 'public'))
if (shotProblems.length) {
  console.error(
    [
      `CHANGELOG.md, Version ${version}: Screenshots passen nicht.`,
      ...shotProblems.map((p) => `  - ${p}`),
      'Unter der Banner-Zeile eintragen (Dateien in public/news/<version>/, PNG oder WebP):',
      '  <!-- shots:',
      `  /news/${version}/<datei>.png | English caption | Deutsche Bildunterschrift`,
      '  -->',
    ].join('\n'),
  )
  process.exit(1)
}
if (command === 'notes') process.stdout.write(releaseNotes(entry))
