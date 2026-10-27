// Bausteine der Release-Automatik (scripts/release.mjs): Commits seit dem letzten Release lesen, daraus
// Changelog-Punkte (Englisch + Deutsch) und den Versionssprung ableiten und den Abschnitt in CHANGELOG.md
// anlegen. Reine Funktionen – die Tests (tests/release.test.ts) prüfen sie ohne Git.
//
// Commit-Nachrichten → Changelog:
//   - Die erste Zeile wird zum Punkt, für beide Sprachen gleich. Eigene Texte je Sprache: Zeilen „EN: …“ und
//     „DE: …“ im Commit-Text.
//   - „feat: …“ (oder „neu: …“) = neue Funktion → fett und Minor-Sprung; „fix: …“ = Fehlerbehebung.
//   - Nicht im Changelog: „ci:“, „chore:“, „docs:“, „test:“, „build:“, „style:“, „refactor:“, Merge-Commits,
//     „Release v…“, Abhängigkeits-Updates („Bump x from 1 to 2“), alles mit „[skip changelog]“ und Commits, die
//     nur Dateien ändern, die Spieler nicht bekommen (Doku, Tests, Workflows, Skripte – siehe forPlayers).
//   - „!“ nach dem Typ („feat!: …“) oder „BREAKING CHANGE“ im Text = Major-Sprung; „[minor]“/„[major]“ erzwingen ihn.

const SKIP_PREFIX = /^(ci|chore|docs?|tests?|build|style|refactor|wip)(\([^)]*\))?!?:/i
const TYPE_PREFIX = /^(feat|feature|neu|new|fix|fixed|perf)(\([^)]*\))?!?:\s*/i
const RELEASE_COMMIT = /^Release v\d+\.\d+\.\d+/i
const DEPENDENCY_BUMP = /^bump \S+ from \S+ to \S+/i
const SKIP_MARKER = /\[(skip changelog|no changelog|skip release)\]/i
/** Standard-Banner automatischer Releases: Cubiq-Grün, Motiv ist dann das Logo (siehe UpdateBanner.vue). */
const BANNER = '<!-- banner: accent=#71a330 -->'
/** Dateien, die nicht im Launcher landen: Doku, Tests, Workflows, Skripte, Server-Teile, Repo-Pflege. */
const NOT_FOR_PLAYERS =
  /^(\.github\/|\.vscode\/|\.claude\/|docs\/|tests?\/|scripts\/|packaging\/|relay\/|server-plugin\/|[^/]+\.md$|LICENSE|NOTICE|\.gitignore$|\.gitattributes$|\.editorconfig$)/

/**
 * Ausgabe von `git log --name-only --format=%x1e%H%x1f%s%x1f%b%x1f` in Commits zerlegen (ohne `--name-only`
 * bleibt `files` leer).
 * @param {string} raw
 * @returns {{ hash: string, subject: string, body: string, files: string[] }[]}
 */
export function parseGitLog(raw) {
  return raw
    .split('\x1e')
    .filter((record) => record.trim())
    .map((record) => {
      const [hash = '', subject = '', body = '', files = ''] = record.split('\x1f')
      return {
        hash: hash.trim(),
        subject: subject.trim(),
        body: body.trim(),
        files: files
          .split('\n')
          .map((file) => file.trim())
          .filter(Boolean),
      }
    })
}

/** Ändert der Commit etwas, das Spieler bekommen? Ohne Dateiliste (unbekannt): ja. */
export function forPlayers(files) {
  return !files?.length || files.some((file) => !NOT_FOR_PLAYERS.test(file))
}

/** Ein Satz für den Changelog: ohne Typ-Präfix, ohne „(#12)“, groß anfangend, ohne Punkt am Ende. */
export function tidy(text) {
  let t = String(text ?? '').replace(/\s+/g, ' ').trim()
  t = t.replace(TYPE_PREFIX, '').replace(/\s*\(#\d+\)$/, '').replace(/[.!]+$/, '').trim()
  return t ? t[0].toUpperCase() + t.slice(1) : ''
}

/**
 * Changelog-Punkt eines Commits, `null` = nicht für Spieler.
 * @param {{ subject: string, body: string, files?: string[] }} commit
 * @returns {{ en: string, de: string, kind: 'new' | 'fix' | 'change', bump: 'major' | 'minor' | null } | null}
 */
export function changelogItem(commit) {
  const subject = commit.subject ?? ''
  const body = commit.body ?? ''
  if (!subject || RELEASE_COMMIT.test(subject) || /^Merge (pull request|branch|remote-tracking)/i.test(subject)) return null
  if (SKIP_MARKER.test(`${subject}\n${body}`) || SKIP_PREFIX.test(subject) || DEPENDENCY_BUMP.test(subject)) return null
  if (!forPlayers(commit.files)) return null
  const en = /^EN:\s*(.+)$/im.exec(body)?.[1]
  const de = /^DE:\s*(.+)$/im.exec(body)?.[1]
  const base = tidy(subject.replace(/\[(minor|major)\]/gi, ''))
  if (!base && !en && !de) return null
  const kind = /^(feat|feature|neu|new)\b/i.test(subject) ? 'new' : /^fix/i.test(subject) ? 'fix' : 'change'
  const breaking = /^[a-z]+(\([^)]*\))?!:/i.test(subject) || /BREAKING CHANGE/.test(body) || /\[major\]/i.test(subject)
  const bump = breaking ? 'major' : kind === 'new' || /\[minor\]/i.test(subject) ? 'minor' : null
  return { en: tidy(en ?? base), de: tidy(de ?? base), kind, bump }
}

/** Versionssprung aus den Punkten: Major vor Minor vor Patch. */
export function bumpFor(items) {
  if (items.some((i) => i.bump === 'major')) return 'major'
  if (items.some((i) => i.bump === 'minor')) return 'minor'
  return 'patch'
}

/** Datum für die Überschrift, YYYY-MM-DD (UTC). */
export function today(now = new Date()) {
  return now.toISOString().slice(0, 10)
}

function bullets(items, lang) {
  const seen = new Set()
  const out = []
  for (const item of [...items.filter((i) => i.kind === 'new'), ...items.filter((i) => i.kind !== 'new')]) {
    const text = item[lang]
    if (!text || seen.has(text)) continue
    seen.add(text)
    out.push(item.kind === 'new' ? `- **${text}.**` : `- ${text}.`)
  }
  return out
}

/** Neuer Abschnitt einer Version aus den Punkten (Englisch + Deutsch, Standard-Banner). */
export function generateSection(version, date, items) {
  return [`## ${version} – ${date}`, BANNER, '', '### English', ...bullets(items, 'en'), '', '### Deutsch', ...bullets(items, 'de'), ''].join('\n')
}

/** Grenzen des Abschnitts „## Unreleased“ (Zeilenindizes, Ende exklusiv) oder `null`. */
function unreleasedRange(lines) {
  const start = lines.findIndex((line) => /^## +unreleased\s*$/i.test(line))
  if (start < 0) return null
  let end = lines.findIndex((line, i) => i > start && /^## /.test(line))
  if (end < 0) end = lines.length
  return { start, end }
}

/** Teile von „## Unreleased“: Kommentare davor, Englisch- und Deutsch-Block (rohe Zeilen, ohne Ränder). */
function unreleasedParts(lines, range) {
  const section = lines.slice(range.start + 1, range.end)
  const first = section.findIndex((line) => /^### /.test(line))
  const preamble = (first < 0 ? section : section.slice(0, first)).filter((line) => line.trim())
  const block = (name) => {
    const at = section.findIndex((line) => new RegExp(`^### +${name}\\s*$`, 'i').test(line))
    if (at < 0) return []
    const rest = section.slice(at + 1)
    const next = rest.findIndex((line) => /^### /.test(line))
    const body = next < 0 ? rest : rest.slice(0, next)
    while (body.length && !body[0].trim()) body.shift()
    while (body.length && !body[body.length - 1].trim()) body.pop()
    return body.some((line) => /^\s*[-*] +\S/.test(line)) ? body : []
  }
  return { preamble, en: block('english'), de: block('deutsch') }
}

/**
 * Datum in der Überschrift einer Version auf den Release-Tag setzen (Update-Name bleibt). Ohne Abschnitt für die
 * Version: Text unverändert.
 */
export function setReleaseDate(text, version, date) {
  const heading = new RegExp(`^## +\\[?v?${version.replace(/\./g, '\\.')}\\]?(?: +[–-] +\\d{4}-\\d{2}-\\d{2})?((?: +[–-] +.+?)?) *$`, 'm')
  return text.replace(heading, (_, title) => `## ${version} – ${date}${title}`)
}

/** Hat „## Unreleased“ von Hand geschriebene Punkte (mindestens in einer Sprache)? */
export function unreleasedReady(text) {
  const lines = text.split(/\r?\n/)
  const range = unreleasedRange(lines)
  if (!range) return false
  const parts = unreleasedParts(lines, range)
  return parts.en.length > 0 || parts.de.length > 0
}

/**
 * CHANGELOG.md für eine neue Version vorbereiten. Von Hand geschriebene Punkte unter „## Unreleased“ haben
 * Vorrang und werden zur Version (fehlt eine Sprache, gilt die andere); sonst entsteht der Abschnitt aus den
 * Commits. `null` = es gibt nichts zu veröffentlichen.
 * @param {string} text
 * @param {string} version
 * @param {string} date
 * @param {ReturnType<typeof changelogItem>[]} items
 */
export function prepareChangelog(text, version, date, items) {
  const nl = text.includes('\r\n') ? '\r\n' : '\n'
  const lines = text.split(/\r?\n/)
  const range = unreleasedRange(lines)
  let section = null
  if (range) {
    const parts = unreleasedParts(lines, range)
    if (parts.en.length || parts.de.length) {
      const en = parts.en.length ? parts.en : parts.de
      const de = parts.de.length ? parts.de : parts.en
      const preamble = parts.preamble.some((line) => /^<!--\s*banner:/i.test(line.trim())) ? parts.preamble : [BANNER, ...parts.preamble]
      section = [`## ${version} – ${date}`, ...preamble, '', '### English', ...en, '', '### Deutsch', ...de, '']
    }
  }
  if (!section) {
    const real = items.filter(Boolean)
    if (!real.length) return null
    section = generateSection(version, date, real).split('\n')
  }
  const rest = range ? [...lines.slice(0, range.start), ...lines.slice(range.end)] : lines
  let at = rest.findIndex((line) => /^## /.test(line))
  if (at < 0) at = rest.length
  const before = rest.slice(0, at)
  while (before.length && before[before.length - 1].trim() === '') before.pop()
  return [...before, '', ...section, ...rest.slice(at)].join(nl)
}
