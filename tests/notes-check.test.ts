import { describe, expect, it } from 'vitest'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { checkNoteShots, checkNotes } from '../scripts/notes-check.mjs'
import { encodePng, upscale, Canvas } from '../scripts/pixel-art.mjs'

const commit = (subject: string, body = '', files: string[] = ['app/pages/index.vue']) => ({ subject, body, files })
const good = '### English\n\n- **Trim clips.** Cut your recordings.\n\n### Deutsch\n\n- **Clips zuschneiden.** Kürze deine Aufnahmen.\n'

describe('PR-Prüfung „Spieler-Text“', () => {
  it('verlangt einen Spieler-Text, wenn sich für Spieler etwas ändert', () => {
    const r = checkNotes([commit('feat: Trim clips')], [])
    expect(r.ok).toBe(false)
    expect(r.needed).toEqual(['feat: Trim clips'])
    expect(r.problems[0]).toContain('changes/<thema>.md')
    expect(checkNotes([commit('feat: Trim clips')], [{ name: 'clip-trim.md', text: good }]).ok).toBe(true)
  })

  it('braucht keinen Text für Umbauten, Tests, Doku und CI', () => {
    expect(checkNotes([commit('refactor: Split store'), commit('test: More cases'), commit('ci: Faster')], []).ok).toBe(true)
    expect(checkNotes([commit('Update docs', '', ['docs/x.md', 'README.md'])], []).ok).toBe(true)
    expect(checkNotes([commit('fix: Typo [skip changelog]')], []).ok).toBe(true)
    expect(checkNotes([], []).ok).toBe(true)
  })

  it('nimmt auch EN:/DE:-Zeilen im Commit', () => {
    expect(checkNotes([commit('fix: Crash', 'EN: **No more crash.** Opening settings works again.\nDE: **Kein Absturz mehr.** Die Einstellungen öffnen wieder.')], []).ok).toBe(true)
    // Nur eine Sprache reicht nicht.
    expect(checkNotes([commit('fix: Crash', 'EN: Only English')], []).ok).toBe(false)
  })

  it('prüft die Texte selbst: beide Sprachen, keine Namen oder Danksagungen', () => {
    const onlyEnglish = checkNotes([commit('feat: X')], [{ name: 'x.md', text: '### English\n- **X.** Y.\n' }])
    expect(onlyEnglish.ok).toBe(false)
    expect(onlyEnglish.problems.join('\n')).toContain('beide Sprachen')
    const thanks = checkNotes([commit('feat: X')], [{ name: 'x.md', text: `${good}\nThanks to @alice!\n` }])
    expect(thanks.ok).toBe(false)
    expect(thanks.problems.join('\n')).toContain('Danksagungen')
    expect(checkNotes([commit('feat: X')], [{ name: 'x.md', text: 'Just a sentence.' }]).ok).toBe(false)
  })

  it('prüft die Screenshots eines Spieler-Texts: Datei in changes/, echtes Bild, passende Größe', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cubiq-notes-'))
    try {
      writeFileSync(join(dir, 'home.png'), encodePng(upscale(new Canvas(160, 90).rect(0, 0, 160, 90, [20, 30, 40, 255]), 8)))
      writeFileSync(join(dir, 'tiny.png'), encodePng(upscale(new Canvas(10, 10).rect(0, 0, 10, 10, [20, 30, 40, 255]), 2)))
      writeFileSync(join(dir, 'fake.png'), 'not a picture')
      const note = (shots: string) => [{ name: 'look.md', text: `<!-- shots:\n${shots}\n-->\n\n${good}` }]
      expect(checkNoteShots(note('home.png | The new home | Der neue Start'), dir)).toEqual([])
      expect(checkNoteShots([{ name: 'plain.md', text: good }], dir)).toEqual([])
      const problems = checkNoteShots(note('missing.png\ntiny.png\nfake.png\n/news/0.1.0/x.png\nhome.png | <b>bold</b>'), dir)
      expect(problems.join('\n')).toMatch(/missing\.png fehlt/)
      expect(problems.join('\n')).toMatch(/tiny\.png: 20×20 px/)
      expect(problems.join('\n')).toMatch(/fake\.png: kein gültiges/)
      expect(problems.join('\n')).toMatch(/nur den Dateinamen/)
      expect(problems.join('\n')).toMatch(/Bildunterschrift zu „home\.png“/)
    } finally {
      rmSync(dir, { recursive: true, force: true })
    }
  })
})
