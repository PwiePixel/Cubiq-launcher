// Fragt vor dem Bauen bei Microsoft nach, ob die Microsoft-App des Launchers wirklich existiert und private
// Microsoft-Konten annimmt (nur damit klappt die Xbox-Anmeldung), und vergleicht sie mit der App, mit der sich Spieler
// auf der Website anmelden. Das Format allein reicht nicht: Auf der Übersichtsseite der App im Azure-Portal stehen
// Application (client) ID, Object ID und Directory (tenant) ID nebeneinander, bei den Secrets zusätzlich eine
// Secret ID – alle im selben Format. Eine verwechselte ID ergäbe ein Release, in dem sich niemand anmelden kann.
//
//   node scripts/check-msa-app.mjs
//
// Liest CUBIQ_MSA_CLIENT_ID (Repo-Variable; leer = eingebaute App aus src-tauri/crates/core/src/auth/microsoft.rs)
// und CUBIQ_API_BASE (Adresse der Website). Die ID selbst wird nirgends ausgegeben.
// Exit 1 = Release abbrechen: Microsoft kennt die App nicht, die App ist abgeschaltet oder die Website benutzt eine
// andere App. Nur eine Warnung, wenn Microsoft oder die Website gerade nicht zu erreichen sind.

import { appendFileSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

/** Wie der Launcher: fester `consumers`-Tenant, gleicher Scope (src-tauri/crates/core/src/auth/microsoft.rs). */
export const DEVICE_CODE_URL = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode'
export const SCOPE = 'XboxLive.signin offline_access'

const RIGHT_ID = 'Richtig ist die „Application (client) ID“ (Azure-Portal → App registrations → deine App → Overview) – nicht Object ID, Directory (tenant) ID oder Secret ID.'

/** Wert aus der Repo-Variable ohne Rand (Leerzeichen, Zeilenumbruch beim Einfügen); leer = nicht gesetzt. */
export function normalizeClientId(raw) {
  return String(raw ?? '').trim()
}

/** GUID 8-4-4-4-12 aus Hex-Ziffern – dieselbe Regel wie `is_guid` in src-tauri/crates/core/build.rs. */
export function isGuid(value) {
  return /^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$/.test(String(value ?? ''))
}

/** Die eingebaute App (`CUBIQ_APP_ID`) aus dem Rust-Quelltext – die nimmt der Build, wenn die Variable leer ist. */
export function builtInClientId(source) {
  return /pub const CUBIQ_APP_ID: &str = "([^"]+)";/.exec(String(source ?? ''))?.[1] ?? ''
}

/**
 * Antwort des Device-Code-Endpunkts → `{ level: 'ok' | 'warn' | 'error', reason }`. Gemessene Antworten:
 * 200 mit `user_code` (App da, Anmeldung im Browser und mit Code), AADSTS70002 (App da, Code-Anmeldung aus),
 * AADSTS700016 (keine App mit dieser ID für private Konten), AADSTS7000112 (App abgeschaltet).
 */
export function explainDeviceCode(status, body) {
  const codes = Array.isArray(body?.error_codes) ? body.error_codes.map(Number) : []
  if (status === 200 && body?.user_code) {
    return { level: 'ok', reason: 'Microsoft kennt die App; sie nimmt private Microsoft-Konten an (Anmeldung im Browser und mit Code).' }
  }
  if (codes.includes(7000112)) {
    return { level: 'error', reason: 'Die App ist bei Microsoft abgeschaltet – damit kann sich niemand anmelden.' }
  }
  if (codes.includes(700016)) {
    return {
      level: 'error',
      reason: `Microsoft kennt keine App mit dieser ID, die private Microsoft-Konten annimmt. ${RIGHT_ID} Steht die richtige ID drin, fehlen in der App unter „Supported account types“ die persönlichen Microsoft-Konten.`,
    }
  }
  if (codes.includes(70002)) {
    return {
      level: 'warn',
      reason: 'Microsoft kennt die App, aber die Anmeldung mit Code ist aus (Azure-Portal → App → Authentication → „Allow public client flows“ → Yes). Die Anmeldung im Browser geht trotzdem.',
    }
  }
  const code = codes.length ? `AADSTS${codes[0]}` : body?.error || `HTTP ${status}`
  return { level: 'warn', reason: `Microsoft hat unerwartet geantwortet (${code}) – die App konnte nicht geprüft werden.` }
}

/** `client_id` aus der Weiterleitung der Website-Anmeldung (`/auth/microsoft/login` → Microsoft), sonst `null`. */
export function websiteClientId(location) {
  try {
    const url = new URL(String(location ?? ''))
    if (!/(^|\.)login\.(microsoftonline|live)\.com$/i.test(url.hostname)) return null
    return url.searchParams.get('client_id') || null
  } catch {
    return null
  }
}

/** Launcher-App gegen Website-App. Ohne Website-App (nicht erreichbar, Anmeldung dort aus) nur ein Hinweis. */
export function compareWithWebsite(clientId, siteClientId) {
  if (!siteClientId) return { level: 'warn', reason: 'Die Website hat keine Microsoft-App genannt – Vergleich übersprungen.' }
  if (siteClientId.toLowerCase() === clientId.toLowerCase()) {
    return { level: 'ok', reason: 'Dieselbe App wie bei der Anmeldung auf der Website (dort von Mojang freigegeben).' }
  }
  return {
    level: 'error',
    reason: `Der Launcher bekäme eine andere App als die Website. Beide benutzen die von Mojang freigegebene App (docs/microsoft-sign-in.md) – eine andere App lehnt Minecraft ab. ${RIGHT_ID}`,
  }
}

async function request(fetchImpl, url, init) {
  try {
    return await fetchImpl(url, { ...init, signal: AbortSignal.timeout(15_000) })
  } catch {
    return null
  }
}

/** Alle Prüfungen → `{ results: [{ check, level, reason }], failed }`. `fetchImpl` ist für Tests austauschbar. */
export async function check(env, fetchImpl = fetch, source = readFileSync(join(root, 'src-tauri', 'crates', 'core', 'src', 'auth', 'microsoft.rs'), 'utf8')) {
  const results = []
  const fromVariable = normalizeClientId(env.CUBIQ_MSA_CLIENT_ID)
  const clientId = fromVariable || builtInClientId(source)
  const which = fromVariable ? 'App aus CUBIQ_MSA_CLIENT_ID' : 'Eingebaute App (CUBIQ_MSA_CLIENT_ID ist leer)'
  if (!clientId) {
    results.push({ check: which, level: 'error', reason: 'Keine Microsoft-App gefunden.' })
    return { results, failed: true }
  }
  // Kein GUID (etwa das Client-Secret): gar nicht erst an Microsoft oder sonst wohin schicken.
  if (!isGuid(clientId)) {
    results.push({
      check: which,
      level: 'error',
      reason: `Das ist keine ID im Format xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx. ${RIGHT_ID} Nie das Client-Secret eintragen – es landete sonst im veröffentlichten Launcher.`,
    })
    return { results, failed: true }
  }

  const res = await request(fetchImpl, DEVICE_CODE_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: clientId, scope: SCOPE }).toString(),
  })
  if (!res) {
    results.push({ check: `${which} bei Microsoft`, level: 'warn', reason: 'Microsoft war nicht zu erreichen – die App konnte nicht geprüft werden.' })
  } else {
    let body = null
    try {
      body = await res.json()
    } catch {}
    results.push({ check: `${which} bei Microsoft`, ...explainDeviceCode(res.status, body) })
  }

  const site = String(env.CUBIQ_API_BASE ?? '').trim().replace(/\/+$/, '')
  if (!site) {
    results.push({ check: 'Vergleich mit der Website', level: 'warn', reason: 'CUBIQ_API_BASE ist nicht gesetzt – Vergleich übersprungen.' })
  } else {
    const page = await request(fetchImpl, `${site}/auth/microsoft/login`, { redirect: 'manual' })
    const siteId = page ? websiteClientId(page.headers.get('location')) : null
    results.push({ check: 'Vergleich mit der Website', ...compareWithWebsite(clientId, siteId) })
  }
  return { results, failed: results.some((r) => r.level === 'error') }
}

/** Wert für Workflow-Befehle („::error::…“) maskieren. */
const command = (value) => String(value).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const { results, failed } = await check(process.env)
  const icon = { ok: '✅', warn: '⚠️', error: '❌' }
  const summary = ['### Microsoft-Anmeldung', '', '| | |', '|---|---|', ...results.map((r) => `| ${r.check} | ${icon[r.level]} ${r.reason} |`), '']
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${summary.join('\n')}\n`)
  for (const r of results) {
    if (process.env.GITHUB_ACTIONS && r.level !== 'ok') {
      console.log(`::${r.level === 'error' ? 'error' : 'warning'} title=Microsoft-Anmeldung::${command(`${r.check}: ${r.reason}`)}`)
    }
    console.error(`${r.check}: ${r.level} – ${r.reason}`)
  }
  if (failed) {
    console.error('Release abgebrochen, bevor etwas gebaut oder veröffentlicht wurde. Variable korrigieren, dann den Lauf neu starten.')
    process.exit(1)
  }
}
