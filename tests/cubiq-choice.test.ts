import { describe, expect, it } from 'vitest'
import {
  cubiqAfterPreview,
  cubiqAsks,
  cubiqBulkAsking,
  cubiqBulkInit,
  cubiqBulkRequest,
  cubiqBulkSetAll,
  cubiqBulkToggle,
  cubiqBulkWith,
  cubiqPreselect,
  cubiqRequest,
  cubiqShowsChoice,
  cubiqSoftConflicts,
  cubiqStrongConflicts,
} from '../app/utils/cubiqChoice'
import { settingsSchema } from '../app/utils/schemas'
import type { CubiqOffer } from '../app/types'

function offer(patch: Partial<CubiqOffer> = {}): CubiqOffer {
  return {
    applies: true,
    supported: true,
    unsupported: null,
    recommended: true,
    conflicts: [],
    policy: 'ask',
    ...patch,
  }
}

const essential = offer({ recommended: false, conflicts: [{ name: 'Essential', kind: 'clientMod' }] })
const zoomOnly = offer({ conflicts: [{ name: 'Zoomify', kind: 'zoom' }] })
const noBuild = offer({ supported: false, recommended: false, unsupported: { kind: 'noBuild', loader: 'forge', gameVersion: '1.12.2' } })
const vanilla = offer({ applies: false, recommended: false })

describe('Einzel-Dialog (Modpack installieren, Pack-Datei)', () => {
  it('fragt nur bei Packs mit Modloader, Build und Einstellung „fragen“', () => {
    expect(cubiqAsks(offer())).toBe(true)
    expect(cubiqAsks(essential)).toBe(true)
    expect(cubiqAsks(noBuild)).toBe(false)
    expect(cubiqAsks(vanilla)).toBe(false)
    expect(cubiqAsks(offer({ policy: 'always' }))).toBe(false)
    expect(cubiqAsks(null)).toBe(false)
  })

  it('zeigt ohne Build ausgegraute Karten, bei Vanilla und „immer …“ gar keine', () => {
    expect(cubiqShowsChoice(noBuild)).toBe(true)
    expect(cubiqShowsChoice(vanilla)).toBe(false)
    expect(cubiqShowsChoice(offer({ policy: 'never' }))).toBe(false)
  })

  it('wählt „mit“ vor – außer bei sich beißenden Mods oder ohne Build', () => {
    expect(cubiqPreselect(offer())).toBe(true)
    expect(cubiqPreselect(zoomOnly)).toBe(true)
    expect(cubiqPreselect(essential)).toBe(false)
    expect(cubiqPreselect(noBuild)).toBe(false)
    expect(cubiqPreselect(null)).toBe(true)
  })

  it('trennt starke Überschneidungen von bloßen Hinweisen', () => {
    const both = offer({ recommended: false, conflicts: [{ name: "Xaero's Minimap", kind: 'minimap' }, { name: 'Zoomify', kind: 'zoom' }] })
    expect(cubiqStrongConflicts(both).map((c) => c.name)).toEqual(["Xaero's Minimap"])
    expect(cubiqSoftConflicts(both).map((c) => c.name)).toEqual(['Zoomify'])
    expect(cubiqStrongConflicts(null)).toEqual([])
  })

  it('schickt die Wahl nur, wenn gefragt wurde', () => {
    expect(cubiqRequest(offer(), false)).toBe(false)
    expect(cubiqRequest(essential, true)).toBe(true)
    expect(cubiqRequest(noBuild, true)).toBe(false)
    expect(cubiqRequest(vanilla, true)).toBeNull()
    expect(cubiqRequest(offer({ policy: 'always' }), false)).toBeNull()
    // Vorschau fehlgeschlagen: Die Karten waren wählbar, die Wahl zählt.
    expect(cubiqRequest(null, false)).toBe(false)
  })

  it('übernimmt die Vorauswahl der Vorschau, solange niemand gewählt hat', () => {
    expect(cubiqAfterPreview(essential, false, true)).toBe(false)
    expect(cubiqAfterPreview(essential, true, true)).toBe(true)
    expect(cubiqAfterPreview(offer(), true, false)).toBe(false)
    expect(cubiqAfterPreview(noBuild, true, true)).toBe(false)
    expect(cubiqAfterPreview(null, false, false)).toBe(true)
  })
})

describe('Sammel-Import: eine Frage für alle, Ausnahmen je Instanz', () => {
  const items = [
    { id: 'plain', cubiqClient: offer() },
    { id: 'essential', cubiqClient: essential },
    { id: 'old', cubiqClient: noBuild },
    { id: 'vanilla', cubiqClient: vanilla },
    { id: 'unknown', cubiqClient: null },
  ]

  it('fragt nur Instanzen mit Mods und passendem Build', () => {
    expect(cubiqBulkAsking(items).map((i) => i.id)).toEqual(['plain', 'essential'])
  })

  it('startet mit „mit“ und nimmt Instanzen mit sich beißenden Mods aus', () => {
    const state = cubiqBulkInit(items)
    expect(state.all).toBe(true)
    expect([...state.exceptions]).toEqual(['essential'])
    expect(cubiqBulkRequest(state, items[0]!)).toBe(true)
    expect(cubiqBulkRequest(state, items[1]!)).toBe(false)
    expect(cubiqBulkRequest(state, items[2]!)).toBe(false)
    expect(cubiqBulkRequest(state, items[3]!)).toBeNull()
    expect(cubiqBulkRequest(state, items[4]!)).toBeNull()
  })

  it('startet mit „ohne“, wenn keine gefragte Instanz dazu passt', () => {
    expect(cubiqBulkInit([{ id: 'essential', cubiqClient: essential }]).all).toBe(false)
    expect(cubiqBulkInit([]).all).toBe(true)
  })

  it('Häkchen kehrt die Wahl für eine Instanz um, neue Wahl für alle setzt zurück', () => {
    let state = cubiqBulkInit(items)
    state = cubiqBulkToggle(state, 'essential')
    expect(cubiqBulkWith(state, 'essential')).toBe(true)
    state = cubiqBulkToggle(state, 'plain')
    expect(cubiqBulkWith(state, 'plain')).toBe(false)

    state = cubiqBulkSetAll(items, false)
    expect(state.exceptions.size).toBe(0)
    expect(cubiqBulkRequest(state, items[0]!)).toBe(false)
    state = cubiqBulkToggle(state, 'plain')
    expect(cubiqBulkRequest(state, items[0]!)).toBe(true)

    state = cubiqBulkSetAll(items, true)
    expect([...state.exceptions]).toEqual(['essential'])
  })

  it('Einstellung „immer …“: keine Frage, der Kern entscheidet', () => {
    const always = [{ id: 'a', cubiqClient: offer({ policy: 'always' }) }]
    expect(cubiqBulkAsking(always)).toEqual([])
    expect(cubiqBulkRequest(cubiqBulkInit(always), always[0]!)).toBeNull()
  })
})

describe('Einstellung „Bei Modpacks: Cubiq Client“', () => {
  const field = settingsSchema.shape.modpackCubiqClient

  it('fehlt sie, gilt „immer fragen“; unbekannte Werte werden abgelehnt', () => {
    expect(field.parse(undefined)).toBe('ask')
    expect(field.parse('never')).toBe('never')
    expect(field.parse('always')).toBe('always')
    expect(field.safeParse('sometimes').success).toBe(false)
  })
})
