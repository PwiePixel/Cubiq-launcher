import { describe, expect, it } from 'vitest'
import { changelogFor, parseChangelog, releaseTitle } from '../app/utils/changelog'
import {
  bumpFor,
  changelogItem,
  forPlayers,
  generateSection,
  parseChangeNote,
  parseGitLog,
  prepareChangelog,
  relocateShots,
  setBannerMotif,
  setReleaseDate,
  tidy,
  unreleasedReady,
} from '../scripts/release-lib.mjs'
import { nextVersion, setLockVersions } from '../scripts/version.mjs'

const commit = (subject: string, body = '', files: string[] = ['app/pages/index.vue']) => ({ subject, body, files })

const intro = ['# Changelog', '', 'Intro.', '', '<!--', 'How to write: "## Unreleased" …', '-->', ''].join('\n')
const released = ['## 0.1.0 – 2026-10-27 – Hello, Cubiq | Hallo, Cubiq', '<!-- banner: accent=#71a330 -->', '', '### English', '- Old', '', '### Deutsch', '- Alt', ''].join('\n')

describe('Release-Automatik', () => {
  it('liest git log mit und ohne Dateiliste', () => {
    const raw = '\x1eaaa\x1ffeat: Neu\x1fEN: New\x1f\n\napp/a.vue\nREADME.md\n\x1ebbb\x1ffix: Weg\x1f\x1f\n'
    expect(parseGitLog(raw)).toEqual([
      { hash: 'aaa', subject: 'feat: Neu', body: 'EN: New', files: ['app/a.vue', 'README.md'] },
      { hash: 'bbb', subject: 'fix: Weg', body: '', files: [] },
    ])
    expect(parseGitLog('')).toEqual([])
  })

  it('erkennt Commits, die Spieler nicht bekommen', () => {
    expect(forPlayers(['README.md', 'docs/wiki/Home.md', '.github/workflows/ci.yml', 'tests/a.test.ts'])).toBe(false)
    expect(forPlayers(['scripts/release.mjs', 'CHANGELOG.md', 'relay/src/index.ts'])).toBe(false)
    expect(forPlayers(['README.md', 'src-tauri/src/main.rs'])).toBe(true)
    expect(forPlayers(['client-mod/common/src/A.java'])).toBe(true)
    expect(forPlayers(['public/news/0.2.0/a.png'])).toBe(true)
    // Unbekannt (keine Dateiliste) zählt.
    expect(forPlayers([])).toBe(true)
    expect(forPlayers(undefined)).toBe(true)
  })

  it('macht aus Commits Changelog-Punkte', () => {
    expect(changelogItem(commit('feat: skin preview on the start screen'))).toEqual({
      en: 'Skin preview on the start screen',
      de: 'Skin preview on the start screen',
      kind: 'new',
      bump: 'minor',
    })
    expect(changelogItem(commit('fix(java): crash when the Java folder is missing. (#12)'))).toEqual({
      en: 'Crash when the Java folder is missing',
      de: 'Crash when the Java folder is missing',
      kind: 'fix',
      bump: null,
    })
    // Eigene Texte je Sprache.
    expect(changelogItem(commit('feat: Log-Suche', 'Details\nEN: Log search finds folded stack traces\nDE: Die Log-Suche findet auch eingeklappte Stacktraces'))).toEqual({
      en: 'Log search finds folded stack traces',
      de: 'Die Log-Suche findet auch eingeklappte Stacktraces',
      kind: 'new',
      bump: 'minor',
    })
    // Ohne Präfix: Änderung, Patch; „[minor]“ erzwingt den Sprung und verschwindet aus dem Text.
    expect(changelogItem(commit('Better search results'))).toMatchObject({ en: 'Better search results', kind: 'change', bump: null })
    expect(changelogItem(commit('Better search results [minor]'))).toMatchObject({ en: 'Better search results', bump: 'minor' })
    // Major: „!“ nach dem Typ, BREAKING CHANGE oder [major].
    expect(changelogItem(commit('feat!: new data folder'))?.bump).toBe('major')
    expect(changelogItem(commit('fix: settings format', 'BREAKING CHANGE: old settings are reset'))?.bump).toBe('major')
    expect(changelogItem(commit('Rework [major]'))?.bump).toBe('major')
  })

  it('lässt aus, was nicht in den Changelog gehört', () => {
    for (const subject of [
      'ci: cache cargo',
      'chore(deps): update tauri',
      'docs: wiki',
      'test: more cases',
      'refactor: split store',
      'Release v0.2.0',
      'Merge pull request #1 from PwiePixel/feature',
      "Merge branch 'main' into feature",
      'Bump vitest from 5.0.0 to 5.0.1',
      'Tweak something [skip changelog]',
    ]) {
      expect(changelogItem(commit(subject)), subject).toBeNull()
    }
    expect(changelogItem(commit('Tweak', 'no players affected [skip changelog]'))).toBeNull()
    // Nur Doku/Tests geändert.
    expect(changelogItem(commit('Update README.md', '', ['README.md']))).toBeNull()
    expect(changelogItem(commit(''))).toBeNull()
  })

  it('räumt Sätze auf und wählt den Versionssprung', () => {
    expect(tidy('  feat:   neue   Funktion!! ')).toBe('Neue Funktion')
    expect(tidy('fix: x (#3)')).toBe('X')
    expect(tidy('')).toBe('')
    expect(bumpFor([])).toBe('patch')
    expect(bumpFor([{ bump: null }, { bump: 'minor' }])).toBe('minor')
    expect(bumpFor([{ bump: 'minor' }, { bump: 'major' }])).toBe('major')
    expect(nextVersion('0.1.9', 'patch')).toBe('0.1.10')
    expect(nextVersion('0.1.9', 'minor')).toBe('0.2.0')
    expect(nextVersion('0.9.3', 'major')).toBe('1.0.0')
    expect(() => nextVersion('1.0', 'patch')).toThrow()
    expect(() => nextVersion('1.0.0', 'huge' as 'patch')).toThrow()
  })

  it('schreibt einen Abschnitt aus den Commits: Neues zuerst und fett, ohne Doppelte', () => {
    const items = [
      { en: 'Faster start', de: 'Schnellerer Start', kind: 'change', bump: null },
      { en: 'Skin preview', de: 'Skin-Vorschau', kind: 'new', bump: 'minor' },
      { en: 'Faster start', de: 'Schnellerer Start', kind: 'fix', bump: null },
    ] as const
    expect(generateSection('0.2.0', '2026-11-02', [...items])).toBe(
      [
        '## 0.2.0 – 2026-11-02',
        '<!-- banner: accent=#71a330 -->',
        '',
        '### English',
        '- **Skin preview.**',
        '- Faster start.',
        '',
        '### Deutsch',
        '- **Skin-Vorschau.**',
        '- Schnellerer Start.',
        '',
      ].join('\n'),
    )
  })

  it('legt die neue Version aus den Commits an – vor der neuesten Version, lesbar für den Launcher', () => {
    const text = `${intro}\n${released}`
    const next = prepareChangelog(text, '0.2.0', '2026-11-02', [changelogItem(commit('feat: skin preview')), null])!
    expect(next.indexOf('## 0.2.0 – 2026-11-02')).toBeGreaterThan(next.indexOf('-->'))
    expect(next.indexOf('## 0.2.0')).toBeLessThan(next.indexOf('## 0.1.0'))
    const entry = changelogFor(parseChangelog(next), '0.2.0')!
    expect(entry.en).toBe('- **Skin preview.**')
    expect(entry.de).toBe('- **Skin preview.**')
    // Ohne eigenes Motiv – der Launcher zeigt dann das Logo; die Release-Prüfung verlangt nur das Banner.
    expect(entry.banner).toEqual({ accent: '#71a330', motif: null, anim: null })
    expect(changelogFor(parseChangelog(next), '0.1.0')!.en).toBe('- Old')
    // Nichts zu veröffentlichen.
    expect(prepareChangelog(text, '0.2.0', '2026-11-02', [])).toBeNull()
    expect(prepareChangelog(text, '0.2.0', '2026-11-02', [null])).toBeNull()
  })

  it('nimmt von Hand geschriebene Punkte unter „## Unreleased“ und ergänzt die fehlende Sprache', () => {
    const text = `${intro}\n## Unreleased\n<!-- shots:\n/news/0.2.0/a.png | A | A\n-->\n\n### English\n\n- **Clips.** Save the last 30 seconds.\n  Second line.\n\n### Deutsch\n\n${released}`
    expect(unreleasedReady(text)).toBe(true)
    const next = prepareChangelog(text, '0.2.0', '2026-11-02', [changelogItem(commit('feat: ignored'))])!
    expect(next).not.toMatch(/^## Unreleased/m)
    expect(next).not.toContain('Ignored')
    const entry = changelogFor(parseChangelog(next), '0.2.0')!
    expect(entry.en).toBe('- **Clips.** Save the last 30 seconds.\n  Second line.')
    expect(entry.de).toBe(entry.en)
    expect(entry.banner?.accent).toBe('#71a330')
    expect(entry.shots.map((s) => s.src)).toEqual(['/news/0.2.0/a.png'])
    // Vorhandenes Banner bleibt.
    const own = text.replace('## Unreleased\n', '## Unreleased\n<!-- banner: accent=#ff7ab8 -->\n')
    expect(changelogFor(parseChangelog(prepareChangelog(own, '0.2.0', '2026-11-02', [])!), '0.2.0')!.banner?.accent).toBe('#ff7ab8')
    // Leeres „## Unreleased“ zählt nicht – dann gelten die Commits.
    const empty = `${intro}\n## Unreleased\n\n### English\n\n### Deutsch\n\n${released}`
    expect(unreleasedReady(empty)).toBe(false)
    const fromCommits = prepareChangelog(empty, '0.2.0', '2026-11-02', [changelogItem(commit('fix: crash'))])!
    expect(changelogFor(parseChangelog(fromCommits), '0.2.0')!.en).toBe('- Crash.')
    expect(fromCommits).not.toMatch(/^## Unreleased/m)
  })

  it('nimmt Spieler-Texte aus changes/ – je Datei, fehlende Sprache ergänzt, zusammen mit „## Unreleased“', () => {
    const a = parseChangeNote('<!-- shots:\n/news/0.2.0/a.png | A\n-->\n\n### English\n\n- **Trim clips.** Cut recordings.\n\n### Deutsch\n\n- **Clips zuschneiden.** Aufnahmen kürzen.\n')!
    const b = parseChangeNote('### English\n- **Faster start.** The launcher opens quicker.\n')!
    expect(a.en).toEqual(['- **Trim clips.** Cut recordings.'])
    expect(b.de).toEqual(b.en)
    expect(parseChangeNote('Nur ein Satz ohne Punkte.')).toBeNull()
    expect(parseChangeNote('### English\n\n### Deutsch\n')).toBeNull()

    const text = `${intro}\n${released}`
    const next = prepareChangelog(text, '0.2.0', '2026-11-02', [changelogItem(commit('feat: ignored'))], [a, b])!
    const entry = changelogFor(parseChangelog(next), '0.2.0')!
    expect(entry.en).toBe('- **Trim clips.** Cut recordings.\n- **Faster start.** The launcher opens quicker.')
    expect(entry.de).toBe('- **Clips zuschneiden.** Aufnahmen kürzen.\n- **Faster start.** The launcher opens quicker.')
    expect(entry.shots.map((s) => s.src)).toEqual(['/news/0.2.0/a.png'])
    expect(entry.banner?.accent).toBe('#71a330')
    expect(next).not.toContain('Ignored')
    // Ältere Punkte unter „## Unreleased“ kommen zuerst dazu.
    const legacy = `${intro}\n## Unreleased\n\n### English\n\n- **Old note.** From a branch.\n\n${released}`
    const both = changelogFor(parseChangelog(prepareChangelog(legacy, '0.2.0', '2026-11-02', [], [b])!), '0.2.0')!
    expect(both.en).toBe('- **Old note.** From a branch.\n- **Faster start.** The launcher opens quicker.')
  })

  it('übernimmt den Namen des Updates aus einem Spieler-Text in die Überschrift (erster gewinnt)', () => {
    const a = parseChangeNote('<!-- title: Classic maps | Klassische Karten -->\n<!-- shots:\n/news/0.2.0/a.png | A\n-->\n\n### English\n- **Maps.** New look.\n\n### Deutsch\n- **Karten.** Neuer Look.\n')!
    const b = parseChangeNote('<!-- title: Second -->\n### English\n- **Faster start.** Opens quicker.\n')!
    const next = prepareChangelog(`${intro}\n${released}`, '0.2.0', '2026-11-02', [], [a, b])!
    expect(next).toContain('## 0.2.0 – 2026-11-02 – Classic maps | Klassische Karten\n')
    expect(next).not.toContain('title:')
    const entry = changelogFor(parseChangelog(next), '0.2.0')!
    expect(entry.title).toEqual({ en: 'Classic maps', de: 'Klassische Karten' })
    expect(releaseTitle(entry, '0.2.0')).toBe('Cubiq Launcher 0.2.0 – Classic maps')
    expect(entry.shots.map((s) => s.src)).toEqual(['/news/0.2.0/a.png'])
    // Ohne Namen bleibt die Überschrift schlicht.
    const plain = prepareChangelog(`${intro}\n${released}`, '0.2.0', '2026-11-02', [], [parseChangeNote('### English\n- **X.** Y.\n')!])!
    expect(plain).toMatch(/^## 0\.2\.0 – 2026-11-02$/m)
  })

  it('trägt das Motiv in die Banner-Zeile der Version ein', () => {
    const text = `${intro}\n${released}`
    const withMotif = setBannerMotif(text, '0.1.0', '/news/0.1.0/banner.png')
    expect(withMotif).toContain('## 0.1.0 – 2026-10-27 – Hello, Cubiq | Hallo, Cubiq\n<!-- banner: accent=#71a330 motif=/news/0.1.0/banner.png -->\n')
    expect(changelogFor(parseChangelog(withMotif), '0.1.0')!.banner).toEqual({ accent: '#71a330', motif: '/news/0.1.0/banner.png', anim: null })
    // Mit bewegter Fassung: beide Bilder in einer Zeile, ein zweiter Aufruf ersetzt beide.
    const withAnim = setBannerMotif(withMotif, '0.1.0', '/news/0.1.0/banner.png', '/news/0.1.0/banner-anim.png')
    expect(withAnim).toContain('<!-- banner: accent=#71a330 motif=/news/0.1.0/banner.png anim=/news/0.1.0/banner-anim.png -->')
    expect(changelogFor(parseChangelog(withAnim), '0.1.0')!.banner!.anim).toBe('/news/0.1.0/banner-anim.png')
    expect(setBannerMotif(withAnim, '0.1.0', '/news/0.1.0/x.png').match(/anim=/g)).toBeNull()
    // Ersetzt ein vorhandenes Motiv statt es doppelt einzutragen; andere Versionen bleiben.
    expect(setBannerMotif(withMotif, '0.1.0', '/news/0.1.0/other.png').match(/motif=/g)).toHaveLength(1)
    expect(setBannerMotif(text, '0.9.9', '/news/0.9.9/banner.png')).toBe(text)
    // Ohne Banner-Zeile: neue Zeile unter der Überschrift.
    expect(setBannerMotif('## 1.0.0 – 2026-01-01\n### English\n- x', '1.0.0', '/news/1.0.0/banner.png')).toBe(
      '## 1.0.0 – 2026-01-01\n<!-- banner: accent=#71a330 motif=/news/1.0.0/banner.png -->\n### English\n- x',
    )
  })

  it('hängt Änderungen ohne eigenen Spieler-Text hinter die Spieler-Texte', () => {
    const note = parseChangeNote('### English\n- **Faster start.** Opens quicker.\n\n### Deutsch\n- **Schneller Start.** Öffnet schneller.')!
    const extra = [changelogItem(commit('feat: Calm client', 'EN: Calmer menus in the game\nDE: Ruhigere Menüs im Spiel')), null]
    const entry = changelogFor(parseChangelog(prepareChangelog(`${intro}\n${released}`, '0.2.0', '2026-11-02', [], [note], extra)!), '0.2.0')!
    expect(entry.en).toBe('- **Faster start.** Opens quicker.\n- **Calmer menus in the game.**')
    expect(entry.de).toBe('- **Schneller Start.** Öffnet schneller.\n- **Ruhigere Menüs im Spiel.**')
  })

  it('zieht Screenshots der Spieler-Texte nach public/news/<version>/ um', () => {
    const note = parseChangeNote(
      ['<!-- shots:', 'home.png | The new home | Der neue Start', './menu.webp', '/news/0.1.0/old.png | Old', '-->', '', '### English', '- **New look.** Nicer.', '', '### Deutsch', '- **Neuer Look.** Schöner.'].join('\n'),
    )!
    const taken = new Set(['home.png'])
    const { preamble, moves } = relocateShots(note.preamble, '0.2.0', taken)
    expect(moves).toEqual([
      { from: 'home.png', to: 'home-2.png' },
      { from: 'menu.webp', to: 'menu.webp' },
    ])
    expect(preamble).toEqual(['<!-- shots:', '/news/0.2.0/home-2.png | The new home | Der neue Start', '/news/0.2.0/menu.webp', '/news/0.1.0/old.png | Old', '-->'])
    const next = prepareChangelog(`${intro}\n${released}`, '0.2.0', '2026-11-02', [], [{ ...note, preamble }])!
    const entry = changelogFor(parseChangelog(next), '0.2.0')!
    expect(entry.shots.map((shot) => shot.src)).toEqual(['/news/0.2.0/home-2.png', '/news/0.2.0/menu.webp', '/news/0.1.0/old.png'])
    expect(entry.shots[0]!.caption).toEqual({ en: 'The new home', de: 'Der neue Start' })
    // Ohne Kommentar bleibt alles, wie es ist.
    expect(relocateShots(['<!-- banner: accent=#71a330 -->'], '0.2.0')).toEqual({ preamble: ['<!-- banner: accent=#71a330 -->'], moves: [] })
  })

  it('behält Windows-Zeilenenden', () => {
    const text = `${intro}\n${released}`.replace(/\n/g, '\r\n')
    const next = prepareChangelog(text, '0.2.0', '2026-11-02', [changelogItem(commit('fix: crash'))])!
    expect(next.replace(/\r\n/g, '')).not.toContain('\n')
  })

  it('setzt das Datum beim Release und behält den Update-Namen', () => {
    const text = `${intro}\n${released}`
    expect(setReleaseDate(text, '0.1.0', '2026-11-05')).toContain('## 0.1.0 – 2026-11-05 – Hello, Cubiq | Hallo, Cubiq\n')
    expect(setReleaseDate('## 0.3.0\n- x', '0.3.0', '2026-11-05')).toBe('## 0.3.0 – 2026-11-05\n- x')
    expect(setReleaseDate('## 0.3.0 – The Update\n', '0.3.0', '2026-11-05')).toBe('## 0.3.0 – 2026-11-05 – The Update\n')
    // Andere Versionen bleiben, ohne Abschnitt ändert sich nichts.
    expect(setReleaseDate('## 0.3.0 – 2026-01-01\n## 0.30.0 – 2026-01-01', '0.3.0', '2026-11-05')).toBe('## 0.3.0 – 2026-11-05\n## 0.30.0 – 2026-01-01')
    expect(setReleaseDate(text, '0.9.9', '2026-11-05')).toBe(text)
  })

  it('setzt die Version der eigenen Crates in Cargo.lock, nicht die fremder Pakete', () => {
    const lock = [
      '[[package]]\nname = "cubiq-core"\nversion = "0.1.0"\ndependencies = []\n',
      '[[package]]\nname = "serde"\nversion = "0.1.0"\nsource = "registry+https://github.com/rust-lang/crates.io-index"\n',
    ].join('\n')
    const next = setLockVersions(lock, '0.1.0', '0.2.0')
    expect(next).toContain('name = "cubiq-core"\nversion = "0.2.0"')
    expect(next).toContain('name = "serde"\nversion = "0.1.0"')
  })

  it('benennt das GitHub-Release nach Version und Update-Namen', () => {
    const entries = parseChangelog(`${intro}\n${released}`)
    expect(releaseTitle(changelogFor(entries, '0.1.0'), 'v0.1.0')).toBe('Cubiq Launcher 0.1.0 – Hello, Cubiq')
    expect(releaseTitle(null, '0.2.0')).toBe('Cubiq Launcher 0.2.0')
  })
})
