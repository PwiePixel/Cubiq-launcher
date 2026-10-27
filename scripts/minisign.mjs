// Minisign-Signaturen der Tauri-CLI prüfen (Prehash „ED“ wie beim Auto-Updater). Öffentlicher Schlüssel und Signatur
// kommen so, wie Tauri sie schreibt: Base64 des Dateitexts („untrusted comment: …“ + Schlüssel-/Signaturzeile).

import { createHash, createPublicKey, verify } from 'node:crypto'

const ED25519_SPKI = Buffer.from('302a300506032b6570032100', 'hex')

/** Base64 einer Minisign-Datei (oder der Text selbst) → Zeilen. */
function minisignLines(value) {
  const text = String(value ?? '').trim()
  const decoded = /untrusted comment:/.test(text) ? text : Buffer.from(text, 'base64').toString('utf8')
  return decoded.split(/\r?\n/)
}

/** Schlüssel-ID wie minisign sie anzeigt (z. B. „A427CF588D4DF86A“) – aus öffentlichem Schlüssel oder Signatur. */
export function minisignKeyId(value) {
  const bytes = Buffer.from(minisignLines(value)[1] ?? '', 'base64')
  if (bytes.length < 10) return null
  return Buffer.from(bytes.subarray(2, 10)).reverse().toString('hex').toUpperCase()
}

/**
 * Prüft `signature` (Inhalt der .sig-Datei) über `data` mit `publicKey` (wie in tauri.conf.json).
 * Ergebnis: `null` = gültig, sonst der Grund („format“, „other-key“, „signature“, „file“).
 */
export function verifyMinisign(publicKey, data, signature, fileName) {
  try {
    const pubLines = minisignLines(publicKey)
    const sigLines = minisignLines(signature)
    const pub = Buffer.from(pubLines[1] ?? '', 'base64')
    const sig = Buffer.from(sigLines[1] ?? '', 'base64')
    const trusted = (sigLines[2] ?? '').replace(/^trusted comment: /, '')
    const globalSig = Buffer.from(sigLines[3] ?? '', 'base64')
    if (pub.length !== 42 || sig.length !== 74 || globalSig.length !== 64) return 'format'
    if (pub.subarray(0, 2).toString() !== 'Ed' || sig.subarray(0, 2).toString() !== 'ED') return 'format'
    if (!sig.subarray(2, 10).equals(pub.subarray(2, 10))) return 'other-key'
    const key = createPublicKey({ key: Buffer.concat([ED25519_SPKI, pub.subarray(10)]), format: 'der', type: 'spki' })
    const digest = createHash('blake2b512').update(data).digest()
    const ok = verify(null, digest, key, sig.subarray(10)) && verify(null, Buffer.concat([sig.subarray(10), Buffer.from(trusted)]), key, globalSig)
    if (!ok) return 'signature'
    if (fileName && !trusted.split('\t').includes(`file:${fileName}`)) return 'file'
    return null
  } catch {
    return 'format'
  }
}
