import { describe, expect, it } from 'vitest'

import {
  builtInClientId,
  check,
  compareWithWebsite,
  DEVICE_CODE_URL,
  explainDeviceCode,
  isGuid,
  normalizeClientId,
  websiteClientId,
} from '../scripts/check-msa-app.mjs'

const APP = '0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0'
const OTHER = '11111111-2222-3333-4444-555555555555'
const SOURCE = 'pub const CUBIQ_APP_ID: &str = "5cc8876f-8fe1-4c27-bc28-9355a79db635";'
const SITE = 'https://cubiq.example'

/** Antworten wie gemessen: Microsoft (Device-Code) und die Weiterleitung der Website-Anmeldung. */
function fakeFetch({ microsoft, websiteId }: { microsoft: { status: number, body: unknown } | 'offline', websiteId?: string | null }) {
  const calls: { url: string, body: string }[] = []
  const impl = async (url: string, init: { body?: string, redirect?: string } = {}) => {
    calls.push({ url, body: String(init.body ?? '') })
    if (url === DEVICE_CODE_URL) {
      if (microsoft === 'offline') throw new TypeError('fetch failed')
      return new Response(JSON.stringify(microsoft.body), { status: microsoft.status })
    }
    if (url === `${SITE}/auth/microsoft/login`) {
      // Ohne „manual“ folgte fetch der Weiterleitung – dann gäbe es keinen Location-Header zum Vergleichen.
      if (init.redirect !== 'manual') throw new Error('Website-Anmeldung ohne redirect: manual abgefragt')
      if (websiteId === undefined) throw new TypeError('fetch failed')
      const location = websiteId ? `https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=${websiteId}&response_type=code` : `${SITE}/login?error=ms_off`
      return new Response(null, { status: 302, headers: { location } })
    }
    throw new Error(`unerwartete Adresse ${url}`)
  }
  return { impl, calls }
}

const OK = { status: 200, body: { user_code: 'ABCD1234', device_code: 'x', verification_uri: 'https://www.microsoft.com/link' } }
const NOT_FOUND = { status: 400, body: { error: 'unauthorized_client', error_codes: [700016] } }
const DISABLED = { status: 400, body: { error: 'unauthorized_client', error_codes: [7000112] } }
const NO_CODE_FLOW = { status: 401, body: { error: 'invalid_client', error_codes: [70002] } }
const WORK_ONLY = { status: 400, body: { error: 'unauthorized_client', error_codes: [9002332] } }

describe('check-msa-app', () => {
  it('trims the variable like the launcher does', () => {
    expect(normalizeClientId(`  ${APP}\n`)).toBe(APP)
    expect(normalizeClientId(undefined)).toBe('')
  })

  it('reads the built-in app from the Rust source', () => {
    expect(builtInClientId(SOURCE)).toBe('5cc8876f-8fe1-4c27-bc28-9355a79db635')
    expect(builtInClientId('')).toBe('')
  })

  it('explains the measured Microsoft answers', () => {
    expect(explainDeviceCode(OK.status, OK.body).level).toBe('ok')
    expect(explainDeviceCode(NOT_FOUND.status, NOT_FOUND.body)).toMatchObject({ level: 'error', reason: expect.stringContaining('Application (client) ID') })
    expect(explainDeviceCode(DISABLED.status, DISABLED.body)).toMatchObject({ level: 'error', reason: expect.stringContaining('abgeschaltet') })
    expect(explainDeviceCode(NO_CODE_FLOW.status, NO_CODE_FLOW.body)).toMatchObject({ level: 'warn', reason: expect.stringContaining('Allow public client flows') })
    expect(explainDeviceCode(WORK_ONLY.status, WORK_ONLY.body)).toMatchObject({ level: 'error', reason: expect.stringContaining('private Microsoft-Konten') })
    // Unbekannt oder Störung: warnen, nicht abbrechen.
    expect(explainDeviceCode(503, null).level).toBe('warn')
    expect(explainDeviceCode(400, { error: 'invalid_request', error_codes: [90014] })).toMatchObject({ level: 'warn', reason: expect.stringContaining('AADSTS90014') })
  })

  it('takes the app only from a Microsoft sign-in address', () => {
    expect(websiteClientId(`https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=${APP}`)).toBe(APP)
    expect(websiteClientId(`https://login.live.com/oauth20_authorize.srf?client_id=${APP}`)).toBe(APP)
    expect(websiteClientId(`https://evil.example/?client_id=${APP}`)).toBeNull()
    expect(websiteClientId(`https://login.microsoftonline.com.evil.example/?client_id=${APP}`)).toBeNull()
    expect(websiteClientId('/login?error=ms_off')).toBeNull()
    expect(websiteClientId(null)).toBeNull()
  })

  it('compares with the website app', () => {
    expect(compareWithWebsite(APP, APP.toUpperCase()).level).toBe('ok')
    expect(compareWithWebsite(APP, OTHER).level).toBe('error')
    expect(compareWithWebsite(APP, null).level).toBe('warn')
  })

  it('passes with the website app and never prints the ID', async () => {
    const { impl, calls } = fakeFetch({ microsoft: OK, websiteId: APP })
    const out = await check({ CUBIQ_MSA_CLIENT_ID: `${APP}\n`, CUBIQ_API_BASE: `${SITE}/` }, impl, SOURCE)
    expect(out.failed).toBe(false)
    expect(out.results.map((r) => r.level)).toEqual(['ok', 'ok'])
    expect(JSON.stringify(out)).not.toContain(APP)
    expect(new URLSearchParams(calls[0].body).get('client_id')).toBe(APP)
    expect(new URLSearchParams(calls[0].body).get('scope')).toBe('XboxLive.signin offline_access')
  })

  it('stops for an ID Microsoft does not know (Object ID, tenant ID, Secret ID)', async () => {
    const { impl } = fakeFetch({ microsoft: NOT_FOUND, websiteId: APP })
    const out = await check({ CUBIQ_MSA_CLIENT_ID: OTHER, CUBIQ_API_BASE: SITE }, impl, SOURCE)
    expect(out.failed).toBe(true)
  })

  it('stops for an existing app that is not the website app', async () => {
    const { impl } = fakeFetch({ microsoft: NO_CODE_FLOW, websiteId: APP })
    const out = await check({ CUBIQ_MSA_CLIENT_ID: OTHER, CUBIQ_API_BASE: SITE }, impl, SOURCE)
    expect(out.failed).toBe(true)
    expect(out.results.map((r) => r.level)).toEqual(['warn', 'error'])
  })

  it('stops when the variable is empty and the built-in app is disabled', async () => {
    const { impl, calls } = fakeFetch({ microsoft: DISABLED, websiteId: APP })
    const out = await check({ CUBIQ_MSA_CLIENT_ID: '', CUBIQ_API_BASE: SITE }, impl, SOURCE)
    expect(out.failed).toBe(true)
    expect(new URLSearchParams(calls[0].body).get('client_id')).toBe('5cc8876f-8fe1-4c27-bc28-9355a79db635')
  })

  it('stops for a value that is no ID and sends it nowhere (e.g. the client secret)', async () => {
    const { impl, calls } = fakeFetch({ microsoft: OK, websiteId: APP })
    const secretLike = 'abcQ~0123456789abcdefghijklmnopqrstuvwxyzAB'
    const out = await check({ CUBIQ_MSA_CLIENT_ID: secretLike, CUBIQ_API_BASE: SITE }, impl, SOURCE)
    expect(out.failed).toBe(true)
    expect(calls).toEqual([])
    expect(JSON.stringify(out)).not.toContain(secretLike)
    expect(isGuid(APP)).toBe(true)
    expect(isGuid(`${APP}0`)).toBe(false)
  })

  it("stops on Microsoft's verdict alone when the website cannot be checked", async () => {
    for (const microsoft of [NOT_FOUND, DISABLED, WORK_ONLY]) {
      const { impl } = fakeFetch({ microsoft })
      const out = await check({ CUBIQ_MSA_CLIENT_ID: APP, CUBIQ_API_BASE: SITE }, impl, SOURCE)
      expect(out.failed).toBe(true)
      expect(out.results.map((r) => r.level)).toEqual(['error', 'warn'])
    }
  })

  it('only warns when Microsoft or the website cannot be reached', async () => {
    const offline = fakeFetch({ microsoft: 'offline' })
    const out = await check({ CUBIQ_MSA_CLIENT_ID: APP, CUBIQ_API_BASE: SITE }, offline.impl, SOURCE)
    expect(out.failed).toBe(false)
    expect(out.results.map((r) => r.level)).toEqual(['warn', 'warn'])

    const noSite = fakeFetch({ microsoft: OK, websiteId: null })
    expect((await check({ CUBIQ_MSA_CLIENT_ID: APP, CUBIQ_API_BASE: SITE }, noSite.impl, SOURCE)).failed).toBe(false)
    expect((await check({ CUBIQ_MSA_CLIENT_ID: APP }, noSite.impl, SOURCE)).failed).toBe(false)
  })
})
