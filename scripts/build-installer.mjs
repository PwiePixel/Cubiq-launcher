#!/usr/bin/env node
// Baut den Installer wie das Release: unter Windows den NSIS-Installer (.exe), unter Linux/macOS die Pakete der
// Plattform. Mit Signaturschlüssel bekommen die Update-Dateien eine Signatur; ohne ihn klappt der Build trotzdem –
// der Installer funktioniert genauso, nur verteilt der Auto-Updater solche Builds nicht.
// Aufruf: pnpm app:build [weitere Argumente für „tauri build“, z. B. --debug]
//
// Signaturschlüssel (das Erste, was da ist):
//   - Umgebungsvariablen TAURI_SIGNING_PRIVATE_KEY und TAURI_SIGNING_PRIVATE_KEY_PASSWORD
//   - Datei ~/.tauri/cubiq-launcher.key, Passwort in ~/.tauri/cubiq-launcher.key.password
//     (Windows: %USERPROFILE%\.tauri\…)
// Unter Windows liegt der Installer danach auch als release/Cubiq-Launcher_<version>_x64-setup.exe bereit.
// CARGO_TARGET_DIR wird beachtet.
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const extra = process.argv.slice(2)
const version = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).version

const env = { ...process.env }
const keyFile = path.join(os.homedir(), '.tauri', 'cubiq-launcher.key')
if (!env.TAURI_SIGNING_PRIVATE_KEY && fs.existsSync(keyFile)) {
  env.TAURI_SIGNING_PRIVATE_KEY = fs.readFileSync(keyFile, 'utf8').trim()
  const passwordFile = `${keyFile}.password`
  if (!env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD && fs.existsSync(passwordFile)) {
    env.TAURI_SIGNING_PRIVATE_KEY_PASSWORD = fs.readFileSync(passwordFile, 'utf8').replace(/\r?\n$/, '')
  }
  console.log(`[build] Update-Signatur mit ${keyFile}`)
}
const signed = Boolean(env.TAURI_SIGNING_PRIVATE_KEY)
if (!signed) console.log('[build] Kein Signaturschlüssel gefunden – Installer ohne Update-Signatur (siehe README).')

const args = ['tauri', 'build', ...(signed ? [] : ['--config', 'src-tauri/tauri.unsigned.conf.json']), ...extra]
// Shell nur unter Windows (pnpm ist dort eine .cmd).
const r = spawnSync('pnpm', args, { cwd: root, stdio: 'inherit', shell: process.platform === 'win32', env })
if (r.status !== 0) process.exit(r.status ?? 1)

const targetDir = env.CARGO_TARGET_DIR ? path.resolve(env.CARGO_TARGET_DIR) : path.join(root, 'src-tauri', 'target')
const bundleDir = path.join(targetDir, extra.includes('--debug') ? 'debug' : 'release', 'bundle')
if (process.platform !== 'win32') {
  console.log(`\n[build] Fertig: ${bundleDir}`)
  process.exit(0)
}
// „Cubiq Launcher_0.1.0_x64-setup.exe“ – ältere Versionen aus früheren Builds liegen evtl. daneben.
const nsis = path.join(bundleDir, 'nsis')
const setup = fs.existsSync(nsis) ? fs.readdirSync(nsis).find((f) => f.includes(`_${version}_`) && f.endsWith('-setup.exe')) : undefined
if (!setup) {
  console.error(`[build] Kein Installer für ${version} in ${nsis} gefunden.`)
  process.exit(1)
}
const out = path.join(root, 'release')
fs.mkdirSync(out, { recursive: true })
const target = path.join(out, `Cubiq-Launcher_${version}_x64-setup.exe`)
fs.copyFileSync(path.join(nsis, setup), target)
console.log(`\n[build] Installer: ${target}`)
