import { describe, expect, it } from 'vitest'
import { parseChangelog } from '../app/utils/changelog'
import { BLOG_BASE_URL, blogPostUrl, contributorNames, postLang, postTitle, shotKey, stepShot } from '../app/utils/updatePost'

describe('Update-Beitrag im Launcher', () => {
  it('verlinkt die GitHub-Release-Seite der Version', () => {
    expect(BLOG_BASE_URL).toBe('https://github.com/PwiePixel/Cubiq-launcher/releases')
    expect(blogPostUrl('0.6.4', 'de')).toBe('https://github.com/PwiePixel/Cubiq-launcher/releases/tag/v0.6.4')
    expect(blogPostUrl('v0.6.4', 'en')).toBe('https://github.com/PwiePixel/Cubiq-launcher/releases/tag/v0.6.4')
    expect(blogPostUrl('1.0.0-beta.1', 'es')).toBe('https://github.com/PwiePixel/Cubiq-launcher/releases/tag/v1.0.0-beta.1')
    expect(blogPostUrl('0.6.4', 'fr')).toBe('https://github.com/PwiePixel/Cubiq-launcher/releases/tag/v0.6.4')
    // Nie etwas Fremdes in die Adresse.
    expect(blogPostUrl('../admin', 'de')).toBe(BLOG_BASE_URL)
    expect(blogPostUrl('0.6.4?x=1', 'de')).toBe(BLOG_BASE_URL)
  })

  it('wählt Changelog-Sprache und Update-Namen', () => {
    expect(postLang('de')).toBe('de')
    expect(postLang('en')).toBe('en')
    expect(postLang('pt-BR')).toBe('en')
    const [named, plain] = parseChangelog(
      '## 1.0.0 – 2026-01-01 – The Big Update | Das große Update\n### English\n- a\n### Deutsch\n- b\n## 0.9.0 – 2025-12-01\n### English\n- a',
    )
    expect(postTitle(named!, 'de')).toBe('Das große Update')
    expect(postTitle(named!, 'tr')).toBe('The Big Update')
    expect(postTitle(plain!, 'de')).toBeNull()
  })

  it('blättert in der Galerie im Kreis und reagiert nur auf passende Tasten', () => {
    expect(stepShot(0, 1, 3)).toBe(1)
    expect(stepShot(2, 1, 3)).toBe(0)
    expect(stepShot(0, -1, 3)).toBe(2)
    expect(stepShot(0, -7, 3)).toBe(2)
    expect(stepShot(5, 1, 0)).toBe(0)
    expect(shotKey('ArrowRight', 0, 4)).toBe(1)
    expect(shotKey('ArrowLeft', 0, 4)).toBe(3)
    expect(shotKey('Home', 3, 4)).toBe(0)
    expect(shotKey('End', 0, 4)).toBe(3)
    expect(shotKey('Enter', 0, 4)).toBeNull()
    // Ein einzelnes Bild: Pfeiltasten bleiben frei.
    expect(shotKey('ArrowRight', 0, 1)).toBeNull()
  })

  it('dankt den Mitwirkenden nur mit Namen – ohne „@“, in der Sprache des Lesers', () => {
    const [entry] = parseChangelog('## 1.0.0 – 2026-01-01\n<!-- contributors: @alice,bob,carl -->\n### English\n- a\n### Deutsch\n- b')
    expect(contributorNames(entry!, 'en')).toBe('alice, bob, and carl')
    expect(contributorNames(entry!, 'de')).toBe('alice, bob und carl')
    expect(contributorNames({ contributors: ['alice'] }, 'es')).toBe('alice')
    expect(contributorNames({ contributors: [] }, 'de')).toBeNull()
    // Ungültige Sprache: trotzdem eine lesbare Liste.
    expect(contributorNames({ contributors: ['a', 'b'] }, 'no_such-locale!')).toBe('a, b')
  })
})
