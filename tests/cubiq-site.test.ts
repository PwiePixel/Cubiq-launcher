import { describe, expect, it } from 'vitest'

import { cubiqSite, isSite } from '../scripts/cubiq-site.mjs'

// Dieselben Fälle prüft der Kern (site_rule in src-tauri/crates/core/src/cubiq_api/tests.rs).
const GOOD = [
  'https://launcher.cubiq.studio',
  'https://Cubiq.Studio:8443',
  'https://xn--bcher-kva.de',
  'http://127.0.0.1:8787',
  'http://localhost:3000',
]
const BAD = [
  'launcher.cubiq.studio',
  'http://launcher.cubiq.studio',
  'http://127.0.0.1',
  'https://launcher.cubiq.studio/api',
  'https://cubiq.studio:443:',
  'https://cubiq.studio:8o80',
  'https://cubiq.studio:99999',
  'https://cubiq.studio:0',
  'https://:8080',
  'https://',
  'https://cubiq..studio',
  'https://cubiq.studio.',
  'https://user@cubiq.studio',
  'https://cubiq studio',
  'https://203.0.113.5',
  'https://1.2.3.999',
]

describe('CUBIQ_API_BASE', () => {
  it('lässt nur reine Adressen durch, die auch der Browser lesen kann', () => {
    for (const ok of GOOD) {
      expect(isSite(ok), ok).toBe(true)
      expect(() => new URL(ok).host, ok).not.toThrow()
    }
    for (const bad of BAD) expect(isSite(bad), bad).toBe(false)
  })

  it('nimmt den Schrägstrich am Ende weg und lässt leer leer', () => {
    expect(cubiqSite('https://launcher.cubiq.studio/')).toBe('https://launcher.cubiq.studio')
    expect(cubiqSite('  https://launcher.cubiq.studio//  ')).toBe('https://launcher.cubiq.studio')
    expect(cubiqSite(undefined)).toBe('')
    expect(cubiqSite('  ')).toBe('')
  })

  it('bricht bei einem Tippfehler ab, statt die Dienste leise abzuschalten', () => {
    for (const bad of BAD) expect(() => cubiqSite(bad), bad).toThrow(/CUBIQ_API_BASE/)
  })
})
