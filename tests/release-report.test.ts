import { createHash, generateKeyPairSync, sign } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import { explainSignerError, normalizeKey, passwordCandidates } from '../scripts/check-signing.mjs'
import { minisignKeyId, verifyMinisign } from '../scripts/minisign.mjs'
import { buildingBody, draftBody, errorLines, summaryMarkdown, windowsProblem } from '../scripts/release-report.mjs'

/** Minisign-Schlüsselpaar und Signatur wie von der Tauri-CLI (Prehash „ED“, alles Base64 des Dateitexts). */
function minisignFixture(keyId: Buffer) {
  const { publicKey, privateKey } = generateKeyPairSync('ed25519')
  const raw = publicKey.export({ format: 'der', type: 'spki' }).subarray(-32)
  const pubText = `untrusted comment: minisign public key: ${Buffer.from(keyId).reverse().toString('hex').toUpperCase()}\n${Buffer.concat([Buffer.from('Ed'), keyId, raw]).toString('base64')}\n`
  const signFile = (data: Buffer, file: string) => {
    const digest = createHash('blake2b512').update(data).digest()
    const sig = sign(null, digest, privateKey)
    const trusted = `timestamp:1793103813\tfile:${file}`
    const global = sign(null, Buffer.concat([sig, Buffer.from(trusted)]), privateKey)
    const text = `untrusted comment: signature from tauri secret key\n${Buffer.concat([Buffer.from('ED'), keyId, sig]).toString('base64')}\ntrusted comment: ${trusted}\n${global.toString('base64')}\n`
    return Buffer.from(text).toString('base64')
  }
  return { pubkey: Buffer.from(pubText).toString('base64'), signFile }
}

describe('Update-Signatur prüfen', () => {
  it('verzeiht typische Einfügefehler beim Secret', () => {
    const key = Buffer.from('untrusted comment: rsign encrypted secret key\nRWRTY0Iy\n').toString('base64')
    expect(normalizeKey(key)).toBe(key)
    expect(normalizeKey(`  ${key}\n`)).toBe(key)
    expect(normalizeKey(`"${key}"`)).toBe(key)
    expect(normalizeKey(`${key.slice(0, 20)}\n${key.slice(20)}`)).toBe(key)
    // Entschlüsselter Text statt Base64 (z. B. mit cat statt base64 kopiert).
    expect(normalizeKey('untrusted comment: rsign encrypted secret key\r\n  RWRTY0Iy  \r\n')).toBe(key)
    expect(normalizeKey('')).toBeNull()
    expect(normalizeKey('   \n')).toBeNull()
    expect(normalizeKey(undefined)).toBeNull()
    expect(passwordCandidates('pw\n')).toEqual(['pw\n', 'pw'])
    expect(passwordCandidates(' pw ')).toEqual([' pw ', 'pw'])
    expect(passwordCandidates(undefined)).toEqual([''])
  })

  it('übersetzt Fehler der Tauri-CLI in verständliche Gründe', () => {
    expect(explainSignerError('incorrect updater private key password: Wrong password for that key', true)).toContain('passt nicht')
    expect(explainSignerError('incorrect updater private key password: Wrong password for that key', false)).toContain('fehlt oder ist leer')
    expect(explainSignerError('No such device or address (os error 6)', false)).toContain('fehlt oder ist leer')
    expect(explainSignerError('failed to decode base64 secret key: Invalid symbol 10, offset 348.', true)).toContain('unvollständig oder beschädigt')
    expect(explainSignerError('incorrect updater private key password: failed to fill whole buffer', true)).toContain('unvollständig oder beschädigt')
    expect(explainSignerError('\n       Error something odd\n', true)).toBe('Signieren mit der Tauri-CLI fehlgeschlagen: Error something odd')
  })

  it('prüft Minisign-Signaturen wie der Auto-Updater', () => {
    const id = Buffer.from('0102030405060708', 'hex')
    const { pubkey, signFile } = minisignFixture(id)
    const data = Buffer.from('Cubiq')
    const sig = signFile(data, 'probe.txt')
    expect(minisignKeyId(pubkey)).toBe('0807060504030201')
    expect(minisignKeyId(sig)).toBe('0807060504030201')
    expect(verifyMinisign(pubkey, data, sig, 'probe.txt')).toBeNull()
    expect(verifyMinisign(pubkey, Buffer.from('anders'), sig, 'probe.txt')).toBe('signature')
    expect(verifyMinisign(pubkey, data, sig, 'andere.txt')).toBe('file')
    const other = minisignFixture(Buffer.from('1111111111111111', 'hex'))
    expect(verifyMinisign(other.pubkey, data, sig, 'probe.txt')).toBe('other-key')
    expect(verifyMinisign(pubkey, data, 'kaputt', 'probe.txt')).toBe('format')
    // Der Schlüssel des Projekts steht im selben Format in tauri.conf.json.
    expect(minisignKeyId('dW50cnVzdGVkIGNvbW1lbnQ6IG1pbmlzaWduIHB1YmxpYyBrZXk6IEE0MjdDRjU4OEQ0REY4NkEKUldScStFMk5XTThucERNTjlrSlozN0plT3NNMjBLL0x1Q0VnY3hEVTNuSzk1NE9jNTlrMVk1aCsK')).toBe('A427CF588D4DF86A')
  })
})

describe('Release-Bericht', () => {
  it('nimmt die letzten Fehlerzeilen aus dem Build-Log', () => {
    const log = [
      '   Compiling cubiq-core v0.1.0',
      'warning: unused variable: `x`',
      '\u001b[1m\u001b[31merror[E0425]\u001b[0m: cannot find value `y` in this scope',
      'error: could not compile `cubiq-core` (lib) due to 1 previous error',
      '       Error failed to build app: failed to build app',
    ].join('\n')
    expect(errorLines(log)).toEqual([
      'error[E0425]: cannot find value `y` in this scope',
      'error: could not compile `cubiq-core` (lib) due to 1 previous error',
      'Error failed to build app: failed to build app',
    ])
    expect(errorLines('x'.repeat(10) + ' error ' + 'y'.repeat(400))[0]).toHaveLength(298)
    expect(errorLines('')).toEqual([])
  })

  it('sagt, warum der Windows-Installer fehlt', () => {
    expect(windowsProblem({ exe: true, changelog: 'ok' })).toBeNull()
    expect(windowsProblem({ exe: false, changelog: 'invalid', changelogReason: 'Teil „Deutsch“ ist leer' })).toContain('Teil „Deutsch“ ist leer')
    expect(windowsProblem({ exe: false, changelog: 'ok', job: 'failure', status: null })).toContain('bevor er einen Bericht schreiben konnte')
    const failed = windowsProblem({ exe: false, changelog: 'ok', status: { notes: 'success', build: 'failure', collect: 'skipped', error: ['error: could not compile'] } })!
    expect(failed).toContain('tauri build')
    expect(failed).toContain('error: could not compile')
    expect(windowsProblem({ exe: false, changelog: 'ok', status: { notes: 'success', build: 'cancelled' } })).toContain('abgebrochen')
    expect(windowsProblem({ exe: false, changelog: 'ok', status: { notes: 'success', build: 'success', collect: 'failure' } })).toContain('nsis')
  })

  it('schreibt Entwurf und Zusammenfassung', () => {
    const body = draftBody({ problem: 'Grund A\n\nGrund B', runUrl: 'https://github.com/o/r/actions/runs/1', tag: 'v0.2.0', notes: '# Notes' })
    expect(body).toContain('> [!WARNING]')
    expect(body).toContain('> Grund A\n>\n> Grund B')
    expect(body).toContain('`v0.2.0`')
    expect(body.endsWith('# Notes')).toBe(true)
    expect(buildingBody({ runUrl: 'https://x' })).toContain('⏳')
    const summary = summaryMarkdown({
      tag: 'v0.2.0',
      exe: false,
      exeName: 'Cubiq-Launcher_0.2.0_x64-setup.exe',
      zip: false,
      linux: true,
      signing: 'invalid',
      signingReason: 'Passwort passt nicht.',
      updater: false,
      updaterReason: 'keine Update-Signatur',
      published: false,
      url: 'https://github.com/o/r/releases/tag/untagged-1',
      problem: 'Der Windows-Build ist fehlgeschlagen.\n\n```\nerror\n```',
    })
    expect(summary).toContain('| Windows-Installer (.exe) | ❌ Der Windows-Build ist fehlgeschlagen. |')
    expect(summary).toContain('| Update-Signatur | ⚠️ Passwort passt nicht. |')
    expect(summary).toContain('Entwurf')
    expect(summary).toContain('**Warum fehlt der Windows-Installer?**')
  })
})
