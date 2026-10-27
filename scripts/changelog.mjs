// Changelog für den Release-Build: gibt die Hinweise einer Version aus (Englisch + Deutsch)
// und bricht ab, wenn der Abschnitt fehlt, eine Sprache leer ist, das Update-Banner fehlt
// (Zeile „<!-- banner: accent=#rrggbb motif=/news/<version>/banner.png -->“ – das Motiv zeichnet die Release-Automatik,
// es muss dann in public/ liegen; ohne Motiv zeigt der Launcher das Cubiq-Logo) oder angegebene Screenshots nicht passen
// (Kommentar „<!-- shots: … -->“, freiwillig, siehe scripts/news-shots.mjs).
// Den Abschnitt legt die Release-Automatik an (scripts/release.mjs, .github/workflows/auto-release.yml).
//
//   node --experimental-strip-types scripts/changelog.mjs notes 0.4.4 [--ref main]   → Markdown für Release/Update
//       (Bilder aus dem Repo zum Tag der Version, mit --ref aus einem anderen Stand, z. B. main)
//   node --experimental-strip-types scripts/changelog.mjs check 0.4.4   → nur prüfen
//   node --experimental-strip-types scripts/changelog.mjs title 0.4.4   → Titel des GitHub-Release
//       („Cubiq Launcher 0.4.4“, mit Update-Namen aus der Überschrift „Cubiq Launcher 0.4.4 – The Clip Update“)
//
// Der Parser ist derselbe wie im Launcher (app/utils/changelog.ts).

import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { changelogFor, parseChangelog, releaseNotes, releaseTitle } from '../app/utils/changelog.ts'
import { checkShots } from './news-shots.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const changelogPath = join(root, 'CHANGELOG.md')
const [command, arg, ...options] = process.argv.slice(2)

if (!['notes', 'check', 'title'].includes(command) || !arg) {
  console.error('Aufruf: changelog.mjs notes|check|title <version> [--ref <stand>]')
  process.exit(2)
}

const version = arg.replace(/^v/, '')
const ref = options.includes('--ref') ? options[options.indexOf('--ref') + 1] : undefined

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
if (command === 'notes') process.stdout.write(releaseNotes(entry, { ref }))
