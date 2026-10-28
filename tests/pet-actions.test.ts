// Aktionen der Begleiter am Boden (Streicheln/Füttern): eigene Liste `actions` neben `animations`, Format bleibt 2.
// Vertrag wie auf der Website und im Cubiq Client: nie ein Fehler, je Problem genau eine Warnung (Eintrag oder Liste
// fällt weg), gültige Aktionen ändern das Prüfergebnis nicht; Wiedergabe exklusiv ab dem Auslösen mit 250 ms
// Überblenden, jede Seite auf ihrer eigenen Zeit. Vorlage: das öffentliche fox_kit.json der Website (2026-10-28)
// mit fx-Knochen und einer `actions`-Liste von Hand – die echten Aktionen erzeugt der Generator der Website.
import fs from 'node:fs'
import path from 'node:path'
import * as THREE from 'three'
import { describe, expect, it } from 'vitest'
import {
  ACTION_DRIVERS,
  DRIVERS,
  LIMITS,
  PET_STATES,
  actionLength,
  actionOf,
  blendPoses,
  boneLocal,
  boneMatrices,
  createStateMixer,
  petActionAvailable,
  sampleAction,
  samplePoses,
  stateAnimations,
  transformPoint,
  validateModel,
  type ActionDriver,
  type BonePose,
  type CosmeticModel,
  type Vec3,
} from '../app/utils/cosmetic-v2/format'
import { createCosmetic, type CosmeticInstance } from '../app/utils/cosmetic-v2/view'

type Json = Record<string, unknown>
type Model = CosmeticModel & Json
const IMAGES = { texture: { width: 128, height: 320 } }

const fox = (): Model => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'cosmetic-v2', 'fox_kit_actions.json'), 'utf8'))
const withoutActions = (m: Model): Model => {
  const { actions: _, ...rest } = m
  return rest as Model
}
const entries = (m: Model) => m.actions as unknown as Json[]
const tracksOf = (a: Json) => a.tracks as { bone: string; channel: string; interpolation?: string; keys: Json[] }[]

const REST: BonePose = { position: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] }
/** Größter Unterschied zweier Posen (fehlende Knochen = Ruhe). */
function maxDiff(a: Map<string, BonePose>, b: Map<string, BonePose>): number {
  let d = 0
  for (const id of new Set([...a.keys(), ...b.keys()])) {
    const pa = a.get(id) ?? REST
    const pb = b.get(id) ?? REST
    for (const ch of ['position', 'rotation', 'scale'] as const) for (let j = 0; j < 3; j++) d = Math.max(d, Math.abs(pa[ch][j]! - pb[ch][j]!))
  }
  return d
}

/** Wanduhr + Versatz mitten im Sitzen des Fuchses (idle 16 s, sitzt etwa von 3,5 bis 13 s). */
const T0 = 16_000 * 100_000 + 8000

describe('Aktionen – Prüfung', () => {
  it('gültige Aktionen ändern das Ergebnis nicht: keine Warnung, kein Fehler', () => {
    const m = fox()
    const res = validateModel(m, IMAGES)
    expect(res).toEqual({ ok: true, errors: [], warnings: [] })
    expect(res).toEqual(validateModel(withoutActions(m), IMAGES))
    expect(ACTION_DRIVERS).toEqual(['pet', 'eat'])
    expect(actionOf(m, 'pet')).toBe(entries(m)[0])
    expect(actionOf(m, 'eat')).toBe(entries(m)[1])
    // Leere oder fehlende Liste am Begleiter: nichts zu melden.
    for (const actions of [[], undefined]) expect(validateModel({ ...m, actions }, IMAGES).warnings).toEqual([])
    expect(validateModel(withoutActions(m), IMAGES).warnings).toEqual([])
  })

  it('`null` ist keine Liste, und `actions` außerhalb von slot pet meldet sich immer (auch leer) – je eine Warnung', () => {
    const m = fox()
    const cases: [string, Json][] = [
      ['null', { ...m, actions: null }],
      ['Hut, leere Liste', { ...m, slot: 'hat', attach: 'head', actions: [] }],
      ['Begleiter am Kopf, leere Liste', { ...m, slot: 'companion', attach: 'head', actions: [] }],
      ['Begleiter am Kopf, null', { ...m, slot: 'companion', attach: 'head', actions: null }],
      ['Flügel, gültige Liste', { ...m, slot: 'wings', attach: 'body' }],
    ]
    for (const [name, model] of cases) {
      const res = validateModel(model, IMAGES)
      expect([name, res.ok, res.errors, res.warnings.length], name).toEqual([name, true, [], 1])
      expect(res.warnings[0], name).toMatch(/^actions: /)
      expect([actionOf(model, 'pet'), actionOf(model, 'eat')], name).toEqual([undefined, undefined])
    }
    // Ohne den Schlüssel bleibt jeder slot still.
    const hat = withoutActions({ ...m, slot: 'hat', attach: 'head' } as Model)
    expect(validateModel(hat, IMAGES).warnings).toEqual([])
  })

  // Jeder Fall betrifft die Aktion `pet`: Modell bleibt gültig, genau eine Warnung, `pet` fällt weg, `eat` bleibt.
  const broken: [string, (a: Json, m: Model) => void][] = [
    ['unbekannter Treiber', (a) => (a.driver = 'dance')],
    ['loop: true', (a) => (a.loop = true)],
    ['offsetMs', (a) => (a.offsetMs = 0)],
    ['unbekannter Knochen', (a) => (tracksOf(a)[0]!.bone = 'nope')],
    ['Schlüssel nicht aufsteigend', (a) => void tracksOf(a)[0]!.keys.reverse()],
    ['lengthMs 100', (a) => (a.lengthMs = 100)],
    ['lengthMs 10001', (a) => (a.lengthMs = 10001)],
    ['lengthMs nicht ganzzahlig', (a) => (a.lengthMs = 2200.5)],
    ['ungültige id', (a) => (a.id = 'Pet')],
    // id und Knochen werden nicht umgewandelt: true, ["pet"] oder ["head"] sind kein Text.
    ['id true', (a) => (a.id = true)],
    ['id ["pet"]', (a) => (a.id = ['pet'])],
    ['id 5', (a) => (a.id = 5)],
    ['Knochen ["head"]', (a) => (tracksOf(a)[0]!.bone = ['head'] as never)],
    ['Knochen fehlt', (a) => delete (tracksOf(a)[0] as Json).bone],
    ['keine Spuren', (a) => (a.tracks = [])],
    ['33 Spuren', (a) => (a.tracks = Array.from({ length: 33 }, () => tracksOf(a)[0]))],
    ['65 Schlüssel', (a) => (tracksOf(a)[0]!.keys = Array.from({ length: 65 }, (_, i) => ({ t: i, v: [0, 0, 0] })))],
    ['t über lengthMs', (a) => (tracksOf(a)[0]!.keys.at(-1)!.t = 2300)],
    ['v kein Vektor', (a) => (tracksOf(a)[0]!.keys[1]!.v = [1, 2])],
    ['unbekannter Kanal', (a) => (tracksOf(a)[0]!.channel = 'color')],
    ['unbekannte interpolation', (a) => (tracksOf(a)[0]!.interpolation = 'bounce')],
    ['Skalierung 0', (a) => (tracksOf(a).find((t) => t.channel === 'scale')!.keys[1]!.v = [1, 0, 1])],
    ['mehrere Probleme zugleich', (a) => Object.assign(a, { loop: true, offsetMs: 5, lengthMs: 1, id: '' })],
    ['Eintrag ist kein Objekt', (_a, m) => (entries(m)[0] = 'pet' as never)],
  ]
  it.each(broken)('%s → gültig, eine Warnung, die Aktion fällt weg', (_name, edit) => {
    const m = fox()
    edit(entries(m)[0]!, m)
    const res = validateModel(m, IMAGES)
    expect(res.ok).toBe(true)
    expect(res.errors).toEqual([])
    expect(res.warnings).toHaveLength(1)
    expect(res.warnings[0]).toMatch(/^actions\[0\]/)
    expect(actionOf(m, 'pet')).toBeUndefined()
    expect(actionOf(m, 'eat')).toBe(entries(m)[1])
  })

  it('ganze Liste fällt mit einer Warnung weg: keine Liste, nicht slot pet, mehr als 16 Einträge', () => {
    const cases: ((m: Model) => void)[] = [
      (m) => (m.actions = {} as never),
      (m) => (m.actions = 'pet' as never),
      (m) => Object.assign(m, { slot: 'hat', attach: 'head' }),
      (m) => (m.actions = Array.from({ length: 17 }, (_, i) => entries(m)[i % 2]) as never),
    ]
    for (const edit of cases) {
      const m = fox()
      edit(m)
      const res = validateModel(m, IMAGES)
      expect([res.ok, res.errors, res.warnings.length]).toEqual([true, [], 1])
      expect(res.warnings[0]).toMatch(/^actions: /)
      expect([actionOf(m, 'pet'), actionOf(m, 'eat')]).toEqual([undefined, undefined])
    }
  })

  it('je Treiber gilt der erste gültige Eintrag; Treiber späterer Versionen werden übergangen', () => {
    const twice = fox()
    entries(twice).push({ ...entries(twice)[0]!, lengthMs: 3000 })
    const res = validateModel(twice, IMAGES)
    expect([res.ok, res.warnings.length]).toEqual([true, 1])
    expect(res.warnings[0]).toContain('doppelt')
    expect(actionLength(twice, 'pet')).toBe(2200)

    const firstBroken = fox()
    entries(firstBroken).push({ ...entries(firstBroken)[0]!, lengthMs: 3000 })
    entries(firstBroken)[0]!.loop = true
    expect(validateModel(firstBroken, IMAGES).warnings).toHaveLength(1)
    expect(actionLength(firstBroken, 'pet')).toBe(3000)

    const later = fox()
    entries(later).push({ id: 'wave', driver: 'wave', lengthMs: 1000, loop: false, tracks: [{ bone: 'head', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }] }] })
    expect(validateModel(later, IMAGES).warnings).toHaveLength(1)
    expect(ACTION_DRIVERS.filter((d) => petActionAvailable(later, d))).toEqual(['pet', 'eat'])
  })

  it('pet/eat in `animations` bleiben ein Fehler – dort gelten weiter die zehn Treiber', () => {
    for (const driver of ACTION_DRIVERS) {
      const m = fox()
      m.animations!.push({ ...(entries(m)[0] as never as object), id: 'extra', driver } as never)
      const res = validateModel(m, IMAGES)
      expect(res.ok).toBe(false)
      expect(res.errors.join('\n')).toContain('animations[10]: driver eins von')
    }
    expect(DRIVERS).toHaveLength(10)
    for (const d of ACTION_DRIVERS) expect([(DRIVERS as readonly string[]).includes(d), (PET_STATES as readonly string[]).includes(d)]).toEqual([false, false])
    expect(LIMITS.animations).toBe(12)
  })

  it('Spuren nennen ihren Knochen als Text – auch in `animations` wird ["head"] nicht umgewandelt', () => {
    const m = fox()
    const tr = m.animations![0]!.tracks[0]! as unknown as Json
    const bone = tr.bone as string
    tr.bone = [bone]
    const res = validateModel(m, IMAGES)
    expect(res.ok).toBe(false)
    expect(res.errors).toEqual([`animations[0].tracks[0]: unbekannter Knochen ["${bone}"]`])
    tr.bone = bone
    expect(validateModel(m, IMAGES)).toEqual({ ok: true, errors: [], warnings: [] })
  })

  it('Zustands-Animationen übergehen `actions`', () => {
    const m = fox()
    expect(samplePoses(m, 500, ['pet', 'eat']).size).toBe(0)
    for (const t of [0, 5000, T0]) expect(stateAnimations(m, t, 'idle')).toEqual(stateAnimations(withoutActions(m), t, 'idle'))
  })
})

describe('Aktionen – Länge, Knöpfe, Pose', () => {
  it('Länge und Knöpfe nur mit gültiger Aktion im Modell', () => {
    const m = fox()
    expect([actionLength(m, 'pet'), actionLength(m, 'eat')]).toEqual([2200, 2600])
    expect(ACTION_DRIVERS.filter((d) => petActionAvailable(m, d))).toEqual(['pet', 'eat'])
    const plain = withoutActions(m)
    expect(ACTION_DRIVERS.filter((d) => petActionAvailable(plain, d))).toEqual([])
    expect(actionLength(plain, 'eat')).toBe(0)
    const petOnly = { ...m, actions: [entries(m)[0]] }
    expect(ACTION_DRIVERS.filter((d) => petActionAvailable(petOnly, d))).toEqual(['pet'])
    for (const junk of [null, undefined, 'fox', 42, [], {}, { actions: 'x' }, { slot: 'pet', actions: [{}] }, { slot: 'pet', bones: 3, actions: [entries(m)[0]] }]) {
      expect(petActionAvailable(junk, 'pet')).toBe(false)
    }
  })

  it('eigene Zeit, auf 0…lengthMs geklemmt, exklusiv; Anfang und Ende in Ruhe', () => {
    const m = fox()
    const pose = sampleAction(m, 'pet', 600)!
    expect([...pose.keys()].sort()).toEqual(['body', 'ear_l', 'ear_r', 'eye_l', 'eye_r', 'fx_hearts', 'head', 'tail'])
    expect(pose.get('head')!.rotation).toEqual([-10, 0, -10])
    expect(sampleAction(m, 'pet', -300)).toEqual(sampleAction(m, 'pet', 0))
    expect(sampleAction(m, 'pet', 99_999)).toEqual(sampleAction(m, 'pet', 2200))
    expect(sampleAction(m, 'pet', Number.NaN)).toEqual(sampleAction(m, 'pet', 0))
    expect(sampleAction(withoutActions(m), 'pet', 600)).toBeNull()
    for (const d of ACTION_DRIVERS) {
      for (const t of [0, actionLength(m, d)]) {
        for (const p of sampleAction(m, d, t)!.values()) {
          for (let j = 0; j < 3; j++) {
            expect(Math.abs(p.rotation[j]!)).toBeLessThanOrEqual(1)
            expect(Math.abs(p.position[j]!)).toBeLessThanOrEqual(0.05)
            expect(Math.abs(p.scale[j]! - 1)).toBeLessThanOrEqual(0.01)
          }
        }
      }
    }
  })

  it('Vorlage hält den Vertrag: steht am Boden, fx-Würfel bleiben für ältere Clients im Kopf', () => {
    const m = fox()
    const corners = (c: { from: Vec3; to: Vec3 }): Vec3[] =>
      [0, 1, 2, 3, 4, 5, 6, 7].map((i) => [(i & 1 ? c.to : c.from)[0], (i & 2 ? c.to : c.from)[1], (i & 4 ? c.to : c.from)[2]])
    const fx = m.cubes.filter((c) => c.bone.startsWith('fx_'))
    expect(fx.length).toBeGreaterThan(0)
    // Tiefster Punkt ohne fx bleibt während der Aktionen auf dem Boden.
    for (const d of ACTION_DRIVERS) {
      for (let t = 0; t <= actionLength(m, d); t += 25) {
        const mats = boneMatrices(m, sampleAction(m, d, t))
        const low = Math.min(...m.cubes.filter((c) => !fx.includes(c)).flatMap((c) => corners(c).map((p) => transformPoint(mats.get(c.bone)!, p)[1])))
        expect(Math.abs(low)).toBeLessThanOrEqual(0.05)
      }
    }
    // fx-Knochen: ohne Ruhe-Drehung, Kind des Kopfes, nie in `animations`, keine Höfe; Würfel mit Rand im Kopf-Würfel.
    const head = m.cubes.find((c) => c.id === 'head')!
    for (const b of m.bones.filter((b) => b.id.startsWith('fx_'))) expect([b.parent, b.rotation]).toEqual(['head', undefined])
    expect(m.animations!.flatMap((a) => a.tracks).filter((t) => t.bone.startsWith('fx_'))).toEqual([])
    expect(m.halos ?? []).toEqual([])
    for (const c of fx) for (const p of corners(c)) for (let k = 0; k < 3; k++) expect(Math.min(p[k]! - head.from[k]!, head.to[k]! - p[k]!)).toBeGreaterThanOrEqual(0.125)
  })
})

describe('Aktionen – Mischer (250 ms, eigene Zeitbasis)', () => {
  const play = (driver: ActionDriver, localMs: number) => ({ driver, localMs })

  it('Einblenden: erstes Bild = Bild davor (eingefroren), nach 250 ms genau die Aktion zur eigenen Zeit', () => {
    const m = fox()
    const mix = createStateMixer()
    let before = new Map<string, BonePose>()
    for (let t = T0 - 480; t <= T0; t += 16) before = mix.pose(m, t, 'idle')
    // Der Fuchs sitzt: Rumpf deutlich geneigt – ein Sprung in die Aktion wäre sichtbar.
    expect(Math.abs(before.get('body')!.rotation[0])).toBeGreaterThan(20)
    const first = mix.pose(m, T0 + 16, 'idle', play('pet', 0))
    expect([mix.action, mix.state]).toEqual(['pet', 'idle'])
    expect(maxDiff(first, before)).toBeLessThan(1e-9)
    // Die Ausgangsseite steht still (eigene Zeit eingefroren), die Zielseite läuft auf der Zeit der Aktion.
    const mid = mix.pose(m, T0 + 16 + 125, 'idle', play('pet', 125))
    expect(maxDiff(mid, blendPoses(before, sampleAction(m, 'pet', 125)!, 0.5))).toBeLessThan(1e-9)
    const done = mix.pose(m, T0 + 16 + 250, 'idle', play('pet', 250))
    expect(maxDiff(done, sampleAction(m, 'pet', 250)!)).toBeLessThan(1e-9)
  })

  it('weich über die ganze Überblendung (kein Sprung von Bild zu Bild)', () => {
    const m = fox()
    const mix = createStateMixer()
    let prev = mix.pose(m, T0, 'idle')
    let worst = 0
    for (let local = 0; local <= 600; local += 16) {
      const cur = mix.pose(m, T0 + 16 + local, 'idle', play('pet', local))
      worst = Math.max(worst, maxDiff(cur, prev))
      prev = cur
    }
    // Sitzen → Aktion: bis zu 78° Unterschied; ein Sprung wäre ein ganzer Schritt, weich sind es ≤ 1,5 · 16/250.
    expect(worst).toBeLessThan(10)
  })

  it('erneuter Klick: dieselbe Aktion beginnt weich von vorn', () => {
    const m = fox()
    const mix = createStateMixer()
    mix.pose(m, T0, 'idle')
    let last = new Map<string, BonePose>()
    for (let local = 0; local <= 1000; local += 20) last = mix.pose(m, T0 + 20 + local, 'idle', play('pet', local))
    const t = T0 + 20 + 1000 + 16
    expect(maxDiff(mix.pose(m, t, 'idle', play('pet', 0)), last)).toBeLessThan(1e-9)
    const half = mix.pose(m, t + 125, 'idle', play('pet', 125))
    expect(maxDiff(half, blendPoses(last, sampleAction(m, 'pet', 125)!, 0.5))).toBeLessThan(1e-9)
    expect(maxDiff(mix.pose(m, t + 250, 'idle', play('pet', 250)), sampleAction(m, 'pet', 250)!)).toBeLessThan(1e-9)
  })

  it('Ausblenden: vom gehaltenen Endbild weich in den nächsten Zustand zur Wanduhr', () => {
    const m = fox()
    const mix = createStateMixer()
    mix.pose(m, T0, 'walk')
    let end = new Map<string, BonePose>()
    // Über das Ende hinaus: die Aktion hält ihr letztes Bild, bis der Zustand übernimmt.
    for (let local = 0; local <= 2800; local += 20) end = mix.pose(m, T0 + 20 + local, 'walk', play('eat', local))
    expect(maxDiff(end, sampleAction(m, 'eat', 2600)!)).toBeLessThan(1e-9)
    const t = T0 + 20 + 2800 + 16
    expect(maxDiff(mix.pose(m, t, 'walk', null), end)).toBeLessThan(1e-9)
    expect(mix.action).toBeNull()
    expect(maxDiff(mix.pose(m, t + 125, 'walk'), blendPoses(end, stateAnimations(m, t + 125, 'walk'), 0.5))).toBeLessThan(1e-9)
    expect(maxDiff(mix.pose(m, t + 250, 'walk'), stateAnimations(m, t + 250, 'walk'))).toBeLessThan(1e-9)
  })

  it('Wechsel des Zustands unter einer laufenden Aktion ändert nichts; Wechsel der Aktion blendet über', () => {
    const m = fox()
    const mix = createStateMixer()
    mix.pose(m, T0, 'walk')
    mix.pose(m, T0 + 16, 'walk', play('pet', 0))
    expect(maxDiff(mix.pose(m, T0 + 1000, 'walk', play('pet', 984)), sampleAction(m, 'pet', 984)!)).toBeLessThan(1e-9)
    expect(maxDiff(mix.pose(m, T0 + 1016, 'idle', play('pet', 1000)), sampleAction(m, 'pet', 1000)!)).toBeLessThan(1e-9)
    expect(mix.state).toBe('idle')
    const before = sampleAction(m, 'pet', 1000)!
    expect(maxDiff(mix.pose(m, T0 + 1032, 'idle', play('eat', 0)), before)).toBeLessThan(1e-9)
    expect(mix.action).toBe('eat')
    expect(maxDiff(mix.pose(m, T0 + 1032 + 250, 'idle', play('eat', 250)), sampleAction(m, 'eat', 250)!)).toBeLessThan(1e-9)
  })

  it('ohne die Aktion im Modell läuft der Zustand ungestört weiter', () => {
    const plain = withoutActions(fox())
    const a = createStateMixer()
    const b = createStateMixer()
    for (let t = T0; t < T0 + 1000; t += 16) expect(a.pose(plain, t, 'idle', play('pet', t - T0))).toEqual(b.pose(plain, t, 'idle'))
    expect(a.action).toBeNull()
  })
})

describe('Aktionen – Renderer (three.js in node)', () => {
  const canvas = (width: number, height: number) => ({ width, height }) as unknown as HTMLCanvasElement
  const matrices = (inst: CosmeticInstance, m: CosmeticModel) => new Map(m.bones.map((b) => [b.id, inst.root.getObjectByName(b.id)!.matrix.toArray()]))
  const matDiff = (a: Map<string, number[]>, b: Map<string, number[]>) => Math.max(...[...a].map(([id, x]) => Math.max(...x.map((v, i) => Math.abs(v - b.get(id)![i]!)))))

  it('blendet ohne Sprung ein und aus; ein Modell ohne die Aktion übergeht sie', () => {
    const m = fox()
    const inst = createCosmetic(m, { texture: canvas(128, 320) })
    for (let t = T0 - 320; t <= T0; t += 16) inst.update(t, { state: 'idle' })
    const before = matrices(inst, m)
    inst.update(T0 + 16, { state: 'idle', action: { driver: 'pet', localMs: 0 } })
    expect(matDiff(matrices(inst, m), before)).toBeLessThan(1e-6)
    inst.update(T0 + 16 + 400, { state: 'idle', action: { driver: 'pet', localMs: 400 } })
    const head = m.bones.find((b) => b.id === 'head')!
    const want = boneLocal(head, sampleAction(m, 'pet', 400)!.get('head'))
    inst.root.getObjectByName('head')!.matrix.toArray().forEach((v, i) => expect(v).toBeCloseTo(want[i]!, 6))
    // Ende: Aktion weg → erstes Bild gleich, danach wieder der Zustand.
    inst.update(T0 + 3000, { state: 'idle', action: { driver: 'pet', localMs: 2984 } })
    const held = matrices(inst, m)
    inst.update(T0 + 3016, { state: 'idle' })
    expect(matDiff(matrices(inst, m), held)).toBeLessThan(1e-6)
    inst.dispose()

    const plainModel = withoutActions(fox())
    const withAction = createCosmetic(plainModel, { texture: canvas(128, 320) })
    const without = createCosmetic(plainModel, { texture: canvas(128, 320) })
    for (let t = T0; t < T0 + 800; t += 16) {
      withAction.update(t, { state: 'idle', action: { driver: 'eat', localMs: t - T0 } })
      without.update(t, { state: 'idle' })
      expect(matrices(withAction, plainModel)).toEqual(matrices(without, plainModel))
    }
  })

  it('Leucht-Bilder und Höfe bleiben auf der Wanduhr', () => {
    const glowPet: CosmeticModel = {
      format: 2,
      id: 'glow_pet',
      name: 'Leuchttier',
      slot: 'pet',
      attach: 'ground',
      texture: { file: 'texture.png', width: 16, height: 8, scale: 1, frames: 4, frameTimeMs: 100 },
      bones: [
        { id: 'root', pivot: [0, 0, 0] },
        { id: 'body', parent: 'root', pivot: [0, 4, 0] },
      ],
      cubes: [{ bone: 'body', from: [-3, 0, -3], to: [3, 8, 3], faces: { south: { uv: [0, 0, 6, 8] } } }],
      animations: [{ id: 'idle', driver: 'idle', lengthMs: 1000, tracks: [{ bone: 'body', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 500, v: [0, 20, 0] }, { t: 1000, v: [0, 0, 0] }] }] }],
      actions: [{ id: 'pet', driver: 'pet', lengthMs: 600, loop: false, tracks: [{ bone: 'body', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 300, v: [30, 0, 0] }, { t: 600, v: [0, 0, 0] }] }] }],
      halos: [{ bone: 'body', pos: [0, 9, 0], size: 4, color: '#ffcc66', pulse: { periodMs: 1000, min: 0.2, max: 1 } }],
    }
    expect(validateModel(glowPet, { texture: { width: 16, height: 32 } })).toEqual({ ok: true, errors: [], warnings: [] })
    const origDoc = globalThis.document
    // Höfe brauchen eine kleine Leinwand für ihre Verlaufs-Textur – hier eine Attrappe.
    globalThis.document = {
      createElement: () => ({ width: 0, height: 0, getContext: () => ({ createImageData: (w: number, h: number) => ({ data: new Uint8ClampedArray(w * h * 4) }), putImageData() {} }) }),
    } as unknown as Document
    try {
      const a = createCosmetic(glowPet, { texture: canvas(16, 32) })
      const b = createCosmetic(glowPet, { texture: canvas(16, 32) })
      const sprite = (i: CosmeticInstance) => {
        let s: THREE.Sprite | null = null
        i.root.traverse((o) => (s ??= (o as THREE.Sprite).isSprite ? (o as THREE.Sprite) : null))
        return s!
      }
      const frameOffset = (i: CosmeticInstance) => {
        const mesh = i.root.getObjectByName('body')!.children.find((o) => (o as THREE.Mesh).isMesh) as THREE.Mesh
        return (mesh.material as THREE.MeshBasicMaterial[])[0]!.map!.offset.y
      }
      let posesDiffer = false
      for (let t = T0; t < T0 + 900; t += 37) {
        a.update(t, { state: 'idle', action: { driver: 'pet', localMs: t - T0 } })
        b.update(t, { state: 'idle' })
        expect(sprite(a).material.opacity).toBe(sprite(b).material.opacity)
        expect(frameOffset(a)).toBe(frameOffset(b))
        posesDiffer ||= matDiff(matrices(a, glowPet), matrices(b, glowPet)) > 1e-3
      }
      expect(posesDiffer).toBe(true)
      a.dispose()
      b.dispose()
    } finally {
      globalThis.document = origDoc
    }
  })
})

describe('Aktionen – Namen im Launcher', () => {
  it('Streicheln/Füttern wie auf der Website und im Spiel', () => {
    const load = (l: string) => JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'app', 'locales', `${l}.json`), 'utf8')).headCosmetics.pet
    expect(load('en').action).toEqual({ pet: 'Pet', eat: 'Feed' })
    expect(load('de').action).toEqual({ pet: 'Streicheln', eat: 'Füttern' })
    expect(load('es').action).toEqual({ pet: 'Acariciar', eat: 'Dar de comer' })
    for (const l of ['fr', 'nl', 'pl', 'pt-BR', 'tr']) expect(Object.keys(load(l).action)).toEqual([...ACTION_DRIVERS])
  })
})
