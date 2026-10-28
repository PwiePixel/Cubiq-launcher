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
  petPoseTime,
  sampleAction,
  samplePoses,
  stateAnimations,
  transformPoint,
  validateModel,
  type ActionDriver,
  type ActionPlay,
  type AnimState,
  type BonePose,
  type CosmeticModel,
  type PetState,
  type Vec3,
} from '../app/utils/cosmetic-v2/format'
import { createCosmetic, type CosmeticInstance } from '../app/utils/cosmetic-v2/view'

type Json = Record<string, unknown>
type Model = CosmeticModel & Json
const IMAGES = { texture: { width: 128, height: 320 } }

const fox = (): Model => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'cosmetic-v2', 'fox_kit_actions.json'), 'utf8'))
/** Die veröffentlichten Aurora-Flügel der Website (unverändert kopiert): beim Schleichen klappen die Hände über 180°. */
const aurora = (): CosmeticModel => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'cosmetic-v2', 'aurora_wings.json'), 'utf8'))
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

/** Kleiner Würfel-Begleiter: rollt beim Rutschen zwei volle Runden je Sekunde (0…720°), Streicheln neigt ihn kurz. */
const roller = (): CosmeticModel => ({
  format: 2,
  id: 'roller',
  name: 'Roller',
  slot: 'pet',
  attach: 'ground',
  texture: { file: 'texture.png', width: 16, height: 16, scale: 1 },
  bones: [
    { id: 'root', pivot: [0, 0, 0] },
    { id: 'roll', parent: 'root', pivot: [0, 4, 0] },
  ],
  cubes: [{ bone: 'roll', from: [-4, 0, -4], to: [4, 8, 4], faces: { north: { uv: [0, 0, 8, 8] } } }],
  animations: [
    { id: 'idle', driver: 'idle', lengthMs: 1000, tracks: [{ bone: 'root', channel: 'position', keys: [{ t: 0, v: [0, 0, 0] }] }] },
    { id: 'slide', driver: 'slide', lengthMs: 1000, tracks: [{ bone: 'roll', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 1000, v: [720, 0, 0] }] }] },
  ],
  actions: [
    { id: 'pet', driver: 'pet', lengthMs: 600, loop: false, tracks: [{ bone: 'roll', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 300, v: [0, 0, 10] }, { t: 600, v: [0, 0, 0] }] }] },
  ],
})

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

/** Gewicht der Überblendung zum Anteil u (0…1): smoothstep. */
const smooth = (u: number) => u * u * (3 - 2 * u)
/**
 * Geradlinig mischen wie der veröffentlichte Launcher und der Cubiq Client: a + (b − a) · w je Wert, auch Drehungen
 * je Euler-Winkel (kein kürzester Weg); fehlende Knochen zählen als Ruhe. Bewusst ohne `blendPoses` gerechnet.
 */
function lerpPoses(a: Map<string, BonePose>, b: Map<string, BonePose>, w: number): Map<string, BonePose> {
  const lerp = (x: Vec3, y: Vec3) => x.map((v, j) => v + (y[j]! - v) * w) as Vec3
  const out = new Map<string, BonePose>()
  for (const id of new Set([...a.keys(), ...b.keys()])) {
    const pa = a.get(id) ?? REST
    const pb = b.get(id) ?? REST
    out.set(id, { position: lerp(pa.position, pb.position), rotation: lerp(pa.rotation, pb.rotation), scale: lerp(pa.scale, pb.scale) })
  }
  return out
}
/** Erwartung laut Vertrag: geradlinig überblenden; fx-Knochen sofort in der Zielpose (fehlt sie dort: Ruhe). */
function fade(snap: Map<string, BonePose>, target: Map<string, BonePose>, w: number): Map<string, BonePose> {
  const out = lerpPoses(snap, target, w)
  for (const id of [...out.keys()]) {
    if (!id.startsWith('fx_')) continue
    const p = target.get(id)
    if (p) out.set(id, p)
    else out.delete(id)
  }
  return out
}
/** Pose ohne bzw. nur mit den fx-Knochen (die springen beim Überblenden absichtlich auf die Zielpose). */
const withoutFx = (p: Map<string, BonePose>) => new Map([...p].filter(([id]) => !id.startsWith('fx_')))
const onlyFx = (p: Map<string, BonePose>) => new Map([...p].filter(([id]) => id.startsWith('fx_')))

/**
 * Wie weit ragt ein fx-Würfel aus seinem Versteck (dem Würfel des Eltern-Knochens, der ihn in Ruhe umschließt)?
 * > 0 = sichtbar. fx-Knochen hängen direkt am Versteck-Knochen, ihre Ecken also im Raum des Eltern-Knochens.
 */
function fxOutside(m: CosmeticModel, poses: Map<string, BonePose>): number {
  const corners = (c: { from: Vec3; to: Vec3 }): Vec3[] =>
    [0, 1, 2, 3, 4, 5, 6, 7].map((i) => [(i & 1 ? c.to : c.from)[0], (i & 2 ? c.to : c.from)[1], (i & 4 ? c.to : c.from)[2]])
  const inside = (p: Vec3, c: { from: Vec3; to: Vec3 }) => [0, 1, 2].every((k) => p[k]! >= c.from[k]! && p[k]! <= c.to[k]!)
  let worst = -Infinity
  for (const b of m.bones.filter((x) => x.id.startsWith('fx_'))) {
    const mine = m.cubes.filter((c) => c.bone === b.id)
    const hide = m.cubes.find((c) => c.bone === b.parent && mine.every((f) => corners(f).every((p) => inside(p, c))))!
    expect(hide, `${b.id} braucht ein Versteck`).toBeDefined()
    const local = boneLocal(b, poses.get(b.id))
    for (const c of mine) {
      for (const p of corners(c)) {
        const q = transformPoint(local, p)
        for (let k = 0; k < 3; k++) worst = Math.max(worst, hide.from[k]! - q[k]!, q[k]! - hide.to[k]!)
      }
    }
  }
  return worst
}

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
    expect(maxDiff(mid, fade(before, sampleAction(m, 'pet', 125)!, 0.5))).toBeLessThan(1e-9)
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

  it('erneuter Klick: dieselbe Aktion beginnt weich von vorn, fliegende Herzen verschwinden sofort', () => {
    const m = fox()
    const mix = createStateMixer()
    mix.pose(m, T0, 'idle')
    let last = new Map<string, BonePose>()
    for (let local = 0; local <= 1000; local += 20) last = mix.pose(m, T0 + 20 + local, 'idle', play('pet', local))
    // Nach 1 s sind die Herzen mitten im Flug, weit über dem Kopf.
    expect(fxOutside(m, last)).toBeGreaterThan(2)
    const t = T0 + 20 + 1000 + 16
    const first = mix.pose(m, t, 'idle', play('pet', 0))
    expect(maxDiff(withoutFx(first), withoutFx(last))).toBeLessThan(1e-9)
    // fx-Knochen nehmen sofort die Zielpose der neu gestarteten Aktion (Ruhe, im Kopf versteckt) statt zurückzugleiten.
    expect(maxDiff(onlyFx(first), onlyFx(sampleAction(m, 'pet', 0)!))).toBeLessThan(1e-9)
    expect(fxOutside(m, first)).toBeLessThan(0)
    const half = mix.pose(m, t + 125, 'idle', play('pet', 125))
    expect(maxDiff(half, fade(last, sampleAction(m, 'pet', 125)!, 0.5))).toBeLessThan(1e-9)
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
    expect(maxDiff(mix.pose(m, t + 125, 'walk'), fade(end, stateAnimations(m, t + 125, 'walk'), 0.5))).toBeLessThan(1e-9)
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
    const first = mix.pose(m, T0 + 1032, 'idle', play('eat', 0))
    expect(maxDiff(withoutFx(first), withoutFx(before))).toBeLessThan(1e-9)
    // Füttern bewegt die Herzen nicht: sie stehen sofort in Ruhe (versteckt), die Krümel wie beim Füttern zur Zeit 0.
    expect(first.has('fx_hearts')).toBe(false)
    expect(first.get('fx_crumbs')).toEqual(sampleAction(m, 'eat', 0)!.get('fx_crumbs'))
    expect(mix.action).toBe('eat')
    expect(maxDiff(mix.pose(m, T0 + 1032 + 250, 'idle', play('eat', 250)), sampleAction(m, 'eat', 250)!)).toBeLessThan(1e-9)
  })

  it('fx-Knochen werden nie überblendet: unterbrochene Herzen verschwinden ab dem ersten Bild im Kopf', () => {
    const m = fox()
    // Herzen mitten im Flug, dann: andere Aktion, dieselbe Aktion von vorn, ein Zustand (Aktion endet) – je 250 ms Überblenden.
    const cut: [string, (local: number) => [AnimState, ActionPlay | null]][] = [
      ['Füttern', (local) => ['idle', play('eat', local)]],
      ['Streicheln von vorn', (local) => ['idle', play('pet', local)]],
      ['Zustand läuft', () => ['walk', null]],
      ['Zustand steht', () => ['idle', null]],
    ]
    for (const [name, next] of cut) {
      const mix = createStateMixer()
      mix.pose(m, T0, 'idle')
      let last = new Map<string, BonePose>()
      for (let local = 0; local <= 1300; local += 20) last = mix.pose(m, T0 + 20 + local, 'idle', play('pet', local))
      expect(fxOutside(m, last), name).toBeGreaterThan(2)
      const t0 = T0 + 20 + 1300 + 16
      for (let dt = 0; dt <= 300; dt += 10) {
        const [state, action] = next(dt)
        const pose = mix.pose(m, t0 + dt, state, action)
        const target = action ? sampleAction(m, action.driver, action.localMs)! : stateAnimations(m, t0 + dt, state)
        // fx-Knochen in jedem Bild genau wie das Ziel; die übrigen Knochen blenden weich über.
        expect(maxDiff(onlyFx(pose), onlyFx(target)), `${name} @${dt}`).toBeLessThan(1e-9)
        if (dt <= 120) expect(fxOutside(m, pose), `${name} @${dt}`).toBeLessThan(0.5)
        if (name !== 'Streicheln von vorn') expect(fxOutside(m, pose), `${name} @${dt}`).toBeLessThanOrEqual(-0.125)
      }
    }
  })

  it('ohne die Aktion im Modell läuft der Zustand ungestört weiter', () => {
    const plain = withoutActions(fox())
    const a = createStateMixer()
    const b = createStateMixer()
    for (let t = T0; t < T0 + 1000; t += 16) expect(a.pose(plain, t, 'idle', play('pet', t - T0))).toEqual(b.pose(plain, t, 'idle'))
    expect(a.action).toBeNull()
  })
})

/**
 * Mischer des veröffentlichten Launchers (vor den Aktionen) als Referenz: nur Zustände, Schnappschuss beim Wechsel,
 * 250 ms smoothstep, alles geradlinig. Modelle ohne fx-Knochen müssen Bild für Bild genau so laufen.
 */
function releasedMixer() {
  let cur: AnimState | null = null
  let since = 0
  let snap: Map<string, BonePose> | null = null
  let last = new Map<string, BonePose>()
  return (model: CosmeticModel, timeMs: number, state: AnimState): Map<string, BonePose> => {
    if (cur === null) cur = state
    if (state !== cur) {
      snap = last
      since = timeMs
      cur = state
    }
    const target = stateAnimations(model, timeMs, state)
    let out = target
    if (snap) {
      const u = Math.max(0, Math.min(1, (timeMs - since) / 250))
      if (u >= 1) snap = null
      else out = lerpPoses(snap, target, smooth(u))
    }
    last = out
    return out
  }
}

describe('Überblenden geradlinig wie bisher (kein kürzester Drehweg)', () => {
  it('je Euler-Winkel a + (b − a) · w, auch über 180° – Position und Größe ebenso', () => {
    const pose = (rotation: Vec3, position: Vec3 = [0, 0, 0], scale: Vec3 = [1, 1, 1]) => new Map([['x', { position, rotation, scale }]])
    const rot = (a: Vec3, b: Vec3, w: number) => blendPoses(pose(a), pose(b), w).get('x')!.rotation
    expect(rot([170, -170, 350], [-170, 170, 10], 0.5)).toEqual([0, 0, 180])
    expect(rot([10, 0, -90], [350, 270, 90], 0.5)).toEqual([180, 135, 0])
    expect(rot([600, 0, 0], [0, 0, 0], 0.5)).toEqual([300, 0, 0])
    expect(rot([30, 0, 0], [80, 0, 0], 0.25)).toEqual([42.5, 0, 0])
    // Anfang und Ende genau a und b – keine Umrechnung auf volle Umdrehungen.
    for (const [a, b] of [[350, 10], [-170, 170], [600, 0], [0, 720], [0, 197]] as const) {
      expect(rot([a, 0, 0], [b, 0, 0], 0)[0]).toBe(a)
      expect(rot([a, 0, 0], [b, 0, 0], 1)[0]).toBe(b)
    }
    const b = blendPoses(pose([0, 0, 0], [0, 0, 0], [1, 1, 1]), pose([0, 0, 0], [4, -2, 0], [3, 1, 1]), 0.5).get('x')!
    expect([b.position, b.scale]).toEqual([[2, -1, 0], [2, 1, 1]])
  })

  it('Rollen → Streicheln: geradlinig zurück auf die Aktion (kurz schnell rückwärts, wie bisher), am Ende genau die Aktion', () => {
    const m = roller()
    expect(validateModel(m, { texture: { width: 16, height: 16 } })).toEqual({ ok: true, errors: [], warnings: [] })
    const mix = createStateMixer()
    // Wanduhr bei 5/6 der Rolle: Drehung ≈ 600°.
    const t0 = 1_700_000_000_000 + 833
    let snap = new Map<string, BonePose>()
    for (let t = t0 - 160; t <= t0; t += 16) snap = mix.pose(m, t, 'slide')
    const from = snap.get('roll')!.rotation[0]
    expect(from).toBeGreaterThan(590)
    let prev = from
    for (let dt = 16; dt <= 240; dt += 16) {
      const pose = mix.pose(m, t0 + dt, 'slide', { driver: 'pet', localMs: dt - 16 })
      // Genau die Formel: eingefrorenes Rollen-Bild → Aktion zu ihrer Zeit, Gewicht smoothstep ab dem ersten Bild.
      expect(maxDiff(pose, fade(snap, sampleAction(m, 'pet', dt - 16)!, smooth((dt - 16) / 250)))).toBeLessThan(1e-9)
      // Rückwärts bis 0°, nie darüber hinaus; höchstens 1,5 · 16/250 · 600° ≈ 58° je Bild (wie im veröffentlichten Launcher).
      const x = pose.get('roll')!.rotation[0]
      expect(x).toBeLessThanOrEqual(prev + 1e-9)
      expect(x).toBeGreaterThanOrEqual(-1e-9)
      expect(prev - x).toBeLessThan(60)
      prev = x
    }
    const end = mix.pose(m, t0 + 300, 'slide', { driver: 'pet', localMs: 284 })
    expect(end).toEqual(sampleAction(m, 'pet', 284))
    // Standardweg ohne Überblenden: unverändert die Zustands-Animation.
    const plain = createStateMixer()
    for (let t = t0; t < t0 + 1000; t += 37) expect(plain.pose(m, t, 'slide')).toEqual(stateAnimations(m, t, 'slide'))
  })

  it('Streicheln → Rollen: das Ziel dreht weiter, die Überblendung folgt ihm stetig (kein Umspringen hinter 180°)', () => {
    const m = roller()
    const mix = createStateMixer()
    const b0 = 1_700_000_000_000
    mix.pose(m, b0, 'slide')
    let held = new Map<string, BonePose>()
    for (let t = b0 + 16; t <= b0 + 1084; t += 16) held = mix.pose(m, t, 'slide', { driver: 'pet', localMs: t - b0 - 16 })
    expect(held).toEqual(sampleAction(m, 'pet', 600))
    // Aktion vorbei: vom gehaltenen Endbild (0°) ins Rollen, dessen Ziel in den 250 ms von 72° auf 252° weiterdreht.
    const t1 = b0 + 1100
    let prev = 0
    for (let dt = 0; dt <= 272; dt += 16) {
      const target = stateAnimations(m, t1 + dt, 'slide')
      const pose = mix.pose(m, t1 + dt, 'slide')
      expect(maxDiff(pose, fade(held, target, smooth(Math.min(1, dt / 250))))).toBeLessThan(1e-9)
      // Stetig vorwärts hinter dem Ziel her; ein kürzester Weg spränge hinter 180° um über 200° zurück.
      const x = pose.get('roll')!.rotation[0]
      expect(x).toBeGreaterThanOrEqual(prev - 1e-9)
      expect(x).toBeLessThanOrEqual(target.get('roll')!.rotation[0] + 1e-9)
      expect(x - prev).toBeLessThan(30)
      prev = x
    }
  })

  it('Aurora-Flügel idle → sneak: die Hände klappen auf dem geraden Weg ein, wie im veröffentlichten Launcher', () => {
    const m = aurora()
    expect(validateModel(m, { texture: { width: 192, height: 512 }, glow: { width: 192, height: 3072 } })).toEqual({ ok: true, errors: [], warnings: [] })
    expect(m.bones.filter((b) => b.id.startsWith('fx_'))).toEqual([])
    const z = (p: Map<string, BonePose>, id: string) => p.get(id)!.rotation[2]
    // Schleichen: die Hände klappen über 180° ein, links auf +197°, rechts auf −197° (kürzester Weg: −163° / +163°).
    const sneak = stateAnimations(m, 0, 'sneak')
    expect([z(sneak, 'hand_l'), z(sneak, 'hand_r')]).toEqual([197, -197])
    const mix = createStateMixer()
    const ref = releasedMixer()
    const t0 = 1_761_650_000_000
    let idle = new Map<string, BonePose>()
    for (let t = t0 - 320; t <= t0; t += 16) {
      idle = mix.pose(m, t, 'idle')
      expect(idle).toEqual(ref(m, t, 'idle'))
    }
    for (const id of ['hand_l', 'hand_r']) expect(Math.abs(z(idle, id))).toBeLessThan(10)
    // Ab dem Wechsel je 25 ms ein Zehntel der Überblendung: jedes Bild genau wie der veröffentlichte Mischer.
    for (let k = 0; k <= 10; k++) {
      const t = t0 + 16 + k * 25
      const pose = mix.pose(m, t, 'sneak')
      for (const id of ['hand_l', 'hand_r']) expect(z(pose, id), `${id} @${k}`).toBeCloseTo(z(idle, id) + (z(sneak, id) - z(idle, id)) * smooth(k / 10), 9)
      expect(pose, `@${k}`).toEqual(ref(m, t, 'sneak'))
      if (k === 5) {
        // Mitte (Gewicht ½): genau zwischen idle und sneak – links ≈ +97°, nicht ≈ −83° wie auf dem kürzesten Weg.
        expect(z(pose, 'hand_l')).toBeCloseTo((z(idle, 'hand_l') + 197) / 2, 9)
        expect(z(pose, 'hand_r')).toBeCloseTo((z(idle, 'hand_r') - 197) / 2, 9)
        expect(z(pose, 'hand_l')).toBeGreaterThan(90)
        expect(z(pose, 'hand_r')).toBeLessThan(-90)
      }
    }
  })

  it('Modelle ohne fx-Knochen blenden Bild für Bild genau wie der veröffentlichte Launcher', () => {
    // Wechsel auch mitten in einer Überblendung (je 100 ms nach dem vorigen), Rollen über 180° beim Würfel-Begleiter.
    const plan = (k: number): AnimState =>
      k < 1000 ? 'idle' : k < 1600 ? 'sneak' : k < 1700 ? 'air' : k < 2600 ? 'slide' : k < 2700 ? 'sneak' : k < 3500 ? 'air' : k < 4200 ? 'slide' : 'idle'
    for (const m of [aurora(), roller()]) {
      const mix = createStateMixer()
      const ref = releasedMixer()
      const t0 = 1_761_650_000_000
      for (let k = 0; k < 6000; k += 16) expect(mix.pose(m, t0 + k, plan(k)), `${m.id} @${k}`).toEqual(ref(m, t0 + k, plan(k)))
    }
  })
})

describe('grab: eigene Pose-Zeit nur mit grab-Animation', () => {
  const noGrab = (): Model => {
    const m = fox()
    m.animations = m.animations!.filter((a) => a.driver !== 'grab')
    return m
  }

  it('Pose-Zeit ab dem Klick nur, wenn das Modell grab wirklich hat', () => {
    const now = T0 + 5000
    const since = T0 + 4000
    expect(petPoseTime(fox(), 'grab', now, 4242, since)).toBe(1000)
    expect(petPoseTime(noGrab(), 'grab', now, 4242, since)).toBe(now + 4242)
    for (const state of ['idle', 'walk', 'handsup', 'flee'] as const) expect(petPoseTime(fox(), state, now, 4242, since)).toBe(now + 4242)
  })

  it('ein Begleiter ohne grab behält seine idle-Phase; der Fuchs spielt grab ab dem Klick', () => {
    const off = 4242
    const click = T0 + 3000
    for (const m of [fox(), noGrab()]) {
      const mix = createStateMixer()
      const frame = (now: number, state: PetState) => mix.pose(m, now + off, state, null, petPoseTime(m, state, now, off, click))
      for (let now = T0; now < click; now += 16) frame(now, 'idle')
      let pose = new Map<string, BonePose>()
      for (let now = click; now <= click + 400; now += 16) pose = frame(now, 'grab')
      const now = click + 400
      if (m.animations!.some((a) => a.driver === 'grab')) expect(maxDiff(pose, stateAnimations(m, 400, 'grab'))).toBeLessThan(1e-9)
      else {
        // Rückfall idle auf Wanduhr + Versatz – nicht neu ab Phase 0 (der Fuchs säße dort ganz anders).
        expect(maxDiff(pose, stateAnimations(m, now + off, 'idle'))).toBeLessThan(1e-9)
        expect(maxDiff(stateAnimations(m, now + off, 'idle'), stateAnimations(m, 400, 'idle'))).toBeGreaterThan(5)
      }
    }
  })

  it('erneuter Klick auf grab beginnt weich von vorn statt zu springen', () => {
    const m = fox()
    const off = 4242
    const mix = createStateMixer()
    let since = T0
    const frame = (now: number) => mix.pose(m, now + off, 'grab', null, petPoseTime(m, 'grab', now, off, since))
    mix.pose(m, T0 - 16 + off, 'idle', null, T0 - 16 + off)
    let last = new Map<string, BonePose>()
    for (let now = T0; now <= T0 + 1200; now += 16) last = frame(now)
    since = T0 + 1216
    expect(maxDiff(frame(T0 + 1216), last)).toBeLessThan(1e-9)
    expect(maxDiff(frame(T0 + 1216 + 125), fade(last, stateAnimations(m, 125, 'grab'), 0.5))).toBeLessThan(1e-9)
    expect(maxDiff(frame(T0 + 1216 + 250), stateAnimations(m, 250, 'grab'))).toBeLessThan(1e-9)
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

  /** Leuchtendes Tier mit Bild-Streifen (4 Bilder à 100 ms) und pulsierendem Hof; Streicheln und grab neigen den Rumpf. */
  const glowPet = (): CosmeticModel => ({
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
    animations: [
      { id: 'idle', driver: 'idle', lengthMs: 1000, tracks: [{ bone: 'body', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 500, v: [0, 20, 0] }, { t: 1000, v: [0, 0, 0] }] }] },
      { id: 'grab', driver: 'grab', lengthMs: 2400, loop: false, tracks: [{ bone: 'body', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 1200, v: [0, 0, 40] }, { t: 2400, v: [0, 0, 0] }] }] },
    ],
    actions: [{ id: 'pet', driver: 'pet', lengthMs: 600, loop: false, tracks: [{ bone: 'body', channel: 'rotation', keys: [{ t: 0, v: [0, 0, 0] }, { t: 300, v: [30, 0, 0] }, { t: 600, v: [0, 0, 0] }] }] }],
    halos: [{ bone: 'body', pos: [0, 9, 0], size: 4, color: '#ffcc66', pulse: { periodMs: 1000, min: 0.2, max: 1 } }],
  })
  const sprite = (i: CosmeticInstance) => {
    let s: THREE.Sprite | null = null
    i.root.traverse((o) => (s ??= (o as THREE.Sprite).isSprite ? (o as THREE.Sprite) : null))
    return s!
  }
  const frameOffset = (i: CosmeticInstance) => {
    const mesh = i.root.getObjectByName('body')!.children.find((o) => (o as THREE.Mesh).isMesh) as THREE.Mesh
    return (mesh.material as THREE.MeshBasicMaterial[])[0]!.map!.offset.y
  }
  /** Höfe brauchen eine kleine Leinwand für ihre Verlaufs-Textur – hier eine Attrappe. */
  function withDocument(run: () => void) {
    const origDoc = globalThis.document
    globalThis.document = {
      createElement: () => ({ width: 0, height: 0, getContext: () => ({ createImageData: (w: number, h: number) => ({ data: new Uint8ClampedArray(w * h * 4) }), putImageData() {} }) }),
    } as unknown as Document
    try {
      run()
    } finally {
      globalThis.document = origDoc
    }
  }

  it('Leucht-Bilder und Höfe bleiben auf der Wanduhr', () => {
    const m = glowPet()
    expect(validateModel(m, { texture: { width: 16, height: 32 } })).toEqual({ ok: true, errors: [], warnings: [] })
    withDocument(() => {
      const a = createCosmetic(m, { texture: canvas(16, 32) })
      const b = createCosmetic(m, { texture: canvas(16, 32) })
      let posesDiffer = false
      for (let t = T0; t < T0 + 900; t += 37) {
        a.update(t, { state: 'idle', action: { driver: 'pet', localMs: t - T0 } })
        b.update(t, { state: 'idle' })
        expect(sprite(a).material.opacity).toBe(sprite(b).material.opacity)
        expect(frameOffset(a)).toBe(frameOffset(b))
        posesDiffer ||= matDiff(matrices(a, m), matrices(b, m)) > 1e-3
      }
      expect(posesDiffer).toBe(true)
      a.dispose()
      b.dispose()
    })
  })

  it('grab: Pose ab dem Klick, Leucht-Bilder und Höfe weiter auf Wanduhr + Versatz', () => {
    const m = glowPet()
    const off = 4321
    const click = T0 + 1000
    withDocument(() => {
      const grabbing = createCosmetic(m, { texture: canvas(16, 32) })
      const idle = createCosmetic(m, { texture: canvas(16, 32) })
      let firstGrab = Infinity
      for (let now = T0; now <= click + 1500; now += 37) {
        const state: PetState = now < click ? 'idle' : 'grab'
        if (state === 'grab') firstGrab = Math.min(firstGrab, now)
        grabbing.update(now + off, { state, poseMs: petPoseTime(m, state, now, off, click) })
        idle.update(now + off, { state: 'idle' })
        // Leuchten/Hof wie beim Begleiter, der einfach weiter steht – kein Sprung beim Klick.
        expect(sprite(grabbing).material.opacity).toBe(sprite(idle).material.opacity)
        expect(frameOffset(grabbing)).toBe(frameOffset(idle))
        // Nach dem Überblenden (250 ms ab dem ersten grab-Bild) genau grab zur Zeit seit dem Klick.
        if (now >= firstGrab + 250) {
          const want = boneLocal(m.bones[1]!, stateAnimations(m, now - click, 'grab').get('body'))
          grabbing.root.getObjectByName('body')!.matrix.toArray().forEach((v, i) => expect(v).toBeCloseTo(want[i]!, 9))
        }
      }
      grabbing.dispose()
      idle.dispose()
    })
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

  it('Knöpfe auf der Bühne: Gruppenname ohne Doppelpunkt, Hinweis zum Spiel in allen acht Sprachen', () => {
    const load = (l: string) => JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'app', 'locales', `${l}.json`), 'utf8')).headCosmetics.pet
    expect(load('en').actions).toBe('Interact')
    expect(load('de').actions).toBe('Interagieren')
    for (const l of ['en', 'de', 'es', 'fr', 'nl', 'pl', 'pt-BR', 'tr']) {
      const pet = load(l)
      expect(pet.actions, l).not.toMatch(/:\s*$/)
      expect(Object.keys(pet.actionHint), l).toEqual([...ACTION_DRIVERS])
      for (const d of ACTION_DRIVERS) expect(pet.actionHint[d].length, `${l} ${d}`).toBeGreaterThan(pet.action[d].length)
    }
  })
})
