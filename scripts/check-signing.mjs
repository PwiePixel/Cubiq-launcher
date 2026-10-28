// Prüft den Signaturschlüssel für Updates, bevor gebaut wird: Ein falsch eingetragenes Secret soll nicht den ganzen
// Release-Build kippen (dann gäbe es gar keinen Installer), und der Release-Bericht soll sagen, was nicht stimmt.
//
//   node scripts/check-signing.mjs [--export]
//
// Liest TAURI_SIGNING_PRIVATE_KEY (Inhalt der Datei cubiq-launcher.key) und TAURI_SIGNING_PRIVATE_KEY_PASSWORD,
// verzeiht typische Einfügefehler (Leerzeichen und Zeilenumbrüche, entschlüsselter Text statt Base64, Zeilenumbruch
// hinter dem Passwort), signiert eine Probedatei mit der Tauri-CLI und prüft die Signatur gegen den öffentlichen
// Schlüssel in src-tauri/tauri.conf.json – genau wie der Auto-Updater.
//
// Ausgabe (für $GITHUB_OUTPUT): status=ok|missing|invalid und reason=<Text>.
// --export (GitHub Actions): bei „ok“ stehen Schlüssel und Passwort danach bereinigt und maskiert in $GITHUB_ENV –
// die folgenden Schritte bauen damit signiert. Bei „missing“/„invalid“ bauen sie ohne Update-Signatur.

import { spawnSync } from 'node:child_process'
import { appendFileSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { minisignKeyId, verifyMinisign } from './minisign.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const cli = join(root, 'node_modules', '@tauri-apps', 'cli', 'tauri.js')

/**
 * Secret → Inhalt der .key-Datei (eine Base64-Zeile) oder `null`. Verzeiht Leerzeichen/Zeilenumbrüche und den
 * entschlüsselten Text („untrusted comment: …“ + Schlüsselzeile).
 */
export function normalizeKey(raw) {
  const text = String(raw ?? '').trim().replace(/^(["'])([\s\S]*)\1$/, '$2').trim()
  if (!text) return null
  if (/untrusted comment:/i.test(text)) {
    const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
    if (lines.length < 2) return null
    return Buffer.from(`${lines[0]}\n${lines[1]}\n`, 'utf8').toString('base64')
  }
  return text.replace(/\s+/g, '')
}

/** Passwörter zum Probieren: wie eingetragen, ohne Zeilenumbruch am Ende, ohne Rand. */
export function passwordCandidates(raw) {
  const value = String(raw ?? '')
  return [...new Set([value, value.replace(/\r?\n$/, ''), value.trim()])]
}

/** Fehlermeldung der Tauri-CLI → verständlicher Grund. */
export function explainSignerError(stderr, passwordSet) {
  const text = String(stderr ?? '')
  if (/Wrong password|os error 6|No such device/i.test(text) && !passwordSet) {
    return 'Der Schlüssel hat ein Passwort, aber das Secret TAURI_SIGNING_PRIVATE_KEY_PASSWORD fehlt oder ist leer.'
  }
  if (/Wrong password/i.test(text)) return 'Das Passwort (Secret TAURI_SIGNING_PRIVATE_KEY_PASSWORD) passt nicht zum Schlüssel.'
  if (/decode base64|Invalid input|Invalid symbol|invalid length|fill whole buffer/i.test(text)) {
    return 'Der Schlüssel im Secret TAURI_SIGNING_PRIVATE_KEY ist unvollständig oder beschädigt – den kompletten Inhalt der Datei cubiq-launcher.key (eine lange Zeile) eintragen.'
  }
  const first = text.split(/\r?\n/).map((line) => line.trim()).find((line) => line && !/^Make sure/i.test(line))
  return `Signieren mit der Tauri-CLI fehlgeschlagen: ${first ?? 'unbekannter Fehler'}`
}

function check(env) {
  const rawKey = env.TAURI_SIGNING_PRIVATE_KEY
  if (!rawKey || !rawKey.trim()) {
    return { status: 'missing', reason: 'Secret TAURI_SIGNING_PRIVATE_KEY ist nicht gesetzt.' }
  }
  const key = normalizeKey(rawKey)
  if (!key) return { status: 'invalid', reason: explainSignerError('Invalid input', true) }
  const conf = JSON.parse(readFileSync(join(root, 'src-tauri', 'tauri.conf.json'), 'utf8'))
  const pubkey = conf.plugins.updater.pubkey
  const dir = mkdtempSync(join(tmpdir(), 'cubiq-signing-'))
  try {
    const file = join(dir, 'signing-check.txt')
    writeFileSync(file, `Cubiq Launcher – Probe für die Update-Signatur ${new Date().toISOString()}\n`)
    const passwordSet = env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD !== undefined && env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD !== ''
    let lastError = ''
    for (const password of passwordCandidates(env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD)) {
      rmSync(`${file}.sig`, { force: true })
      const r = spawnSync(process.execPath, [cli, 'signer', 'sign', file], {
        env: { ...process.env, TAURI_SIGNING_PRIVATE_KEY: key, TAURI_SIGNING_PRIVATE_KEY_PASSWORD: password },
        stdio: ['ignore', 'pipe', 'pipe'],
        encoding: 'utf8',
      })
      if (r.status !== 0) {
        lastError = `${r.stderr ?? ''}\n${r.stdout ?? ''}`
        continue
      }
      const signature = readFileSync(`${file}.sig`, 'utf8')
      const problem = verifyMinisign(pubkey, readFileSync(file), signature, 'signing-check.txt')
      if (problem === 'other-key') {
        return {
          status: 'invalid',
          reason: `Der Schlüssel im Secret gehört nicht zum öffentlichen Schlüssel in src-tauri/tauri.conf.json (Schlüssel-ID im Secret: ${minisignKeyId(signature) ?? '?'}, erwartet: ${minisignKeyId(pubkey) ?? '?'}).`,
        }
      }
      if (problem) return { status: 'invalid', reason: `Die Probe-Signatur ist ungültig (${problem}).` }
      return { status: 'ok', reason: `Update-Signatur mit Schlüssel ${minisignKeyId(pubkey) ?? ''} geprüft.`.trim(), key, password }
    }
    return { status: 'invalid', reason: explainSignerError(lastError, passwordSet) }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

/** Wert für Workflow-Befehle („::add-mask::…“) maskieren. */
const command = (value) => String(value).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const result = check(process.env)
  const lines = `status=${result.status}\nreason=${result.reason.replace(/\r?\n/g, ' ')}\n`
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, lines)
  else process.stdout.write(lines)
  if (process.argv.includes('--export') && result.status === 'ok' && process.env.GITHUB_ENV) {
    // Bereinigte Werte sind nicht mehr wortgleich mit den Secrets – deshalb selbst maskieren.
    console.log(`::add-mask::${command(result.key)}`)
    if (result.password) console.log(`::add-mask::${command(result.password)}`)
    const delimiter = `CUBIQ_${Math.random().toString(36).slice(2)}`
    appendFileSync(process.env.GITHUB_ENV, `TAURI_SIGNING_PRIVATE_KEY=${result.key}\n`)
    appendFileSync(process.env.GITHUB_ENV, `TAURI_SIGNING_PRIVATE_KEY_PASSWORD<<${delimiter}\n${result.password}\n${delimiter}\n`)
  }
  if (result.status !== 'ok' && process.env.GITHUB_ACTIONS) {
    console.log(`::warning title=Update-Signatur::${command(result.reason)} Gebaut wird ohne Update-Signatur – diese Version kommt nicht per Auto-Update.`)
  }
  console.error(`Update-Signatur: ${result.status} – ${result.reason}`)
}
