import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { describe, expect, it } from 'vitest'
import {
  DRIVERS,
  FACES,
  FACE_NORMAL,
  JUMP_VY,
  SLOT_ATTACH,
  STATE_FADE_MS,
  WALK_HOLD_MS,
  WALK_SPEED,
  WEAR_STATES,
  boneMatrices,
  faceCorners,
  faceUvCorners,
  frameAt,
  haloFacing,
  haloFalloff,
  haloIntensity,
  modelTop,
  multiply,
  rotationZYX,
  samplePoses,
  transformPoint,
  validateModel,
  blendPoses,
  createStateMixer,
  resolveDriver,
  stateAnimations,
  wearState,
  wearStateAvailable,
  wingState,
  type BonePose,
  type CosmeticModel,
  type CosmeticSlot,
  type Driver,
  type Movement,
  type Vec3,
} from '../app/utils/cosmetic-v2/format'

const load = (file: string) => JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', 'cosmetic-v2', file), 'utf8')) as CosmeticModel
const fixture = () => load('cubiq_cap.json')
/** Die veröffentlichten Aurora-Flügel der Website (unverändert kopiert): idle, air, sneak. */
const auroraWings = () => load('aurora_wings.json')
/**
 * Die Zipfelmütze der Website (unverändert kopiert, eingefroren): Tests verlassen sich nur auf ihren Vertrag – Treiber
 * idle, walk, jump, air und sneak, gültig ohne Warnungen –, nicht auf Knochen oder Werte.
 */
const christmasHat = () => load('christmas_hat.json')

const close = (a: number[], b: number[], eps = 1e-9) => a.forEach((v, i) => expect(v).toBeCloseTo(b[i]!, -Math.log10(eps)))
const sub = (a: Vec3, b: Vec3): Vec3 => [a[0] - b[0], a[1] - b[1], a[2] - b[2]]
const cross = (a: Vec3, b: Vec3): Vec3 => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]

function tiny(): CosmeticModel {
  return {
    format: 2,
    id: 'krone',
    name: 'Krone',
    slot: 'hat',
    attach: 'head',
    texture: { file: 'texture.png', width: 16, height: 8, scale: 2 },
    glow: { file: 'glow.png', frames: 4, frameTimeMs: 100, blend: 'additive' },
    bones: [
      { id: 'root', pivot: [0, 8, 0] },
      { id: 'tip', parent: 'root', pivot: [0, 10, 0], rotation: [0, 0, 90] },
    ],
    cubes: [{ id: 'band', bone: 'root', from: [-4, 8, -4], to: [4, 10, 4], faces: { south: { uv: [0, 0, 8, 2] } } }],
    animations: [
      {
        id: 'idle',
        lengthMs: 1000,
        tracks: [
          { bone: 'root', channel: 'rotation', interpolation: 'linear', keys: [{ t: 0, v: [0, 0, 0] }, { t: 500, v: [10, 0, 0] }, { t: 1000, v: [0, 0, 0] }] },
          { bone: 'tip', channel: 'scale', interpolation: 'step', keys: [{ t: 0, v: [1, 1, 1] }, { t: 500, v: [2, 2, 2] }] },
        ],
      },
    ],
    halos: [{ bone: 'tip', pos: [0, 11, 0], size: 4, color: '#ff5c2b', intensity: 0.8, pulse: { periodMs: 1000, min: 0.25, max: 1 } }],
  }
}

describe('Kosmetik v2 – Mathematik', () => {
  it('dreht in der Reihenfolge ZYX (erst x, dann y, dann z)', () => {
    const composed = multiply(rotationZYX(0, 0, 30), multiply(rotationZYX(0, 20, 0), rotationZYX(10, 0, 0)))
    close(rotationZYX(10, 20, 30), composed)
    // Rx(+90) kippt +y nach +z.
    close(transformPoint(rotationZYX(90, 0, 0), [0, 1, 0]), [0, 0, 1])
  })

  it('Flächen sind von außen gegen den Uhrzeigersinn (Normale zeigt nach außen)', () => {
    for (const face of FACES) {
      const [ol, , ur, ul] = faceCorners([-1, -2, -3], [4, 5, 6], face)
      const n = cross(sub(ul, ol), sub(ur, ol))
      const len = Math.hypot(...n)
      close(n.map((v) => v / len), FACE_NORMAL[face])
    }
    // inflate vergrößert in alle Richtungen
    expect(faceCorners([0, 0, 0], [1, 1, 1], 'south', 0.5)[0]).toEqual([-0.5, 1.5, 1.5])
  })

  it('dreht UVs im Uhrzeigersinn', () => {
    const uv: [number, number, number, number] = [0, 0, 2, 1]
    expect(faceUvCorners(uv)).toEqual([[0, 0], [2, 0], [2, 1], [0, 1]])
    expect(faceUvCorners(uv, 90)).toEqual([[0, 1], [0, 0], [2, 0], [2, 1]])
    expect(faceUvCorners(uv, 270)).toEqual(faceUvCorners(uv, -90))
  })

  it('Knochen erben die Welt-Matrix der Eltern', () => {
    const m = tiny()
    const world = boneMatrices(m)
    // tip dreht um z (+90°) um seinen Pivot [0, 10, 0]: +x wird zu +y.
    close(transformPoint(world.get('tip')!, [1, 10, 0]), [0, 11, 0])
    // Animation: bei t = 250 ms dreht root um 5° um x (linear), tip ist noch nicht skaliert (step).
    const poses = samplePoses(m, 250)
    close(poses.get('root')!.rotation, [5, 0, 0])
    expect(poses.get('tip')!.scale).toEqual([1, 1, 1])
    expect(samplePoses(m, 750).get('tip')!.scale).toEqual([2, 2, 2])
    // Schleife: Wanduhr läuft über die Länge hinaus
    close(samplePoses(m, 10_250).get('root')!.rotation, [5, 0, 0])
    // andere Treiber (walk …) laufen nicht
    expect(samplePoses(m, 250, []).size).toBe(0)
  })

  it('smooth = u²(3 − 2u)', () => {
    const m = tiny()
    m.animations![0]!.tracks[0]!.interpolation = 'smooth'
    close(samplePoses(m, 125).get('root')!.rotation, [10 * 0.25 * 0.25 * (3 - 0.5), 0, 0])
  })

  it('Streifen-Bild, Hof-Helligkeit und Blickrichtung', () => {
    expect(frameAt(0, 4, 100)).toBe(0)
    expect(frameAt(250, 4, 100)).toBe(2)
    expect(frameAt(450, 4, 100)).toBe(0)
    expect(frameAt(450, 1, 100)).toBe(0)
    const halo = tiny().halos![0]!
    expect(haloIntensity(halo, 0)).toBeCloseTo(0.8 * 0.25)
    expect(haloIntensity(halo, 500)).toBeCloseTo(0.8)
    expect(haloIntensity({ intensity: 0.5 }, 123)).toBe(0.5)
    expect(haloFacing(0)).toBe(0)
    expect(haloFacing(-1)).toBe(0)
    expect(haloFacing(0.35)).toBe(1)
    expect(haloFalloff(0)).toBe(1)
    expect(haloFalloff(1)).toBe(0)
    expect(modelTop(tiny())).toBe(10)
  })
})

describe('Kosmetik v2 – Prüfung', () => {
  it('nimmt freigegebene Studio-Modelle an (inkl. Bildmaße)', () => {
    const m = fixture()
    const res = validateModel(m, { texture: { width: 256, height: 256 }, glow: { width: 256, height: 256 * 12 } })
    expect(res.errors).toEqual([])
    expect(res.ok).toBe(true)
  })

  it('findet kaputte Modelle', () => {
    const bad = (edit: (m: CosmeticModel & Record<string, unknown>) => void, images?: Parameters<typeof validateModel>[1]) => {
      const m = tiny() as CosmeticModel & Record<string, unknown>
      edit(m)
      return validateModel(m, images ?? null)
    }
    expect(validateModel(null).ok).toBe(false)
    expect(bad(() => {}).ok).toBe(true)
    expect(bad((m) => (m.format = 1 as 2)).ok).toBe(false)
    expect(bad((m) => (m.bones[1]!.parent = 'nope')).ok).toBe(false)
    expect(bad((m) => (m.cubes[0]!.bone = 'nope')).ok).toBe(false)
    expect(bad((m) => (m.cubes[0]!.to = [4, 10, 40])).ok).toBe(false)
    expect(bad((m) => (m.cubes[0]!.faces.south!.uv = [0, 0, 20, 2])).ok).toBe(false)
    expect(bad((m) => (m.cubes = Array.from({ length: 65 }, () => tiny().cubes[0]!))).errors.join()).toContain('höchstens 64')
    expect(bad((m) => (m.texture.scale = 3)).ok).toBe(false)
    expect(bad((m) => (m.glow!.frames = 17)).ok).toBe(false)
    expect(bad((m) => (m.animations![0]!.tracks[0]!.keys[1]!.t = 0)).ok).toBe(false)
    expect(bad((m) => (m.halos![0]!.color = 'red')).ok).toBe(false)
    // Bildmaße müssen zu den Angaben passen (32×16, Glow 4 Frames).
    expect(bad(() => {}, { texture: { width: 32, height: 16 }, glow: { width: 32, height: 64 } }).ok).toBe(true)
    expect(bad(() => {}, { texture: { width: 64, height: 16 } }).ok).toBe(false)
    expect(bad(() => {}, { texture: { width: 32, height: 16 }, glow: { width: 32, height: 16 } }).ok).toBe(false)
  })
})

// Gegenprobe mit der Studio-Referenz (nur, wenn das Studio auf diesem Rechner liegt):
// gleiche Posen und Matrizen für alle freigegebenen Teile über eine Animationsschleife.
const studio = 'E:/ai/cubiq-studio'
const hasStudio = fs.existsSync(path.join(studio, 'public', 'cosmetic-format.mjs'))
describe.skipIf(!hasStudio)('Kosmetik v2 – gleich wie die Studio-Referenz', () => {
  it('Posen, Knochen-Matrizen und Höfe stimmen überein', async () => {
    const ref = await import(pathToFileURL(path.join(studio, 'public', 'cosmetic-format.mjs')).href)
    const dir = path.join(studio, 'exports', 'cosmetic-v2')
    const models = fs.readdirSync(dir).filter((f) => f.endsWith('.json'))
    expect(models.length).toBeGreaterThanOrEqual(6)
    for (const file of models) {
      const m = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8')) as CosmeticModel
      expect(validateModel(m).errors, file).toEqual(ref.validateModel(m).errors)
      for (const t of [0, 137, 1234, 4321, 99_999, 1_759_150_000_123]) {
        const ours = boneMatrices(m, samplePoses(m, t))
        const theirs = ref.boneMatrices(m, ref.samplePoses(m, t)) as Map<string, number[]>
        for (const [id, mat] of theirs) close(ours.get(id)!, mat, 1e-9)
        for (const h of m.halos ?? []) expect(haloIntensity(h, t)).toBeCloseTo(ref.haloIntensity(h, t), 12)
        expect(frameAt(t, m.glow?.frames, m.glow?.frameTimeMs)).toBe(ref.frameAt(t, m.glow?.frames, m.glow?.frameTimeMs))
      }
    }
  })
})

describe('Kosmetik v2 – Flügel (attach body, Zustände)', () => {
  const wings = (): CosmeticModel => {
    const m = tiny()
    m.slot = 'wings'
    m.attach = 'body'
    m.animations = [
      { id: 'idle', driver: 'idle', lengthMs: 1000, tracks: [{ bone: 'root', channel: 'rotation', interpolation: 'linear', keys: [{ t: 0, v: [0, 0, 0] }, { t: 1000, v: [0, 0, 0] }] }] },
      { id: 'air', driver: 'air', lengthMs: 1000, tracks: [{ bone: 'root', channel: 'rotation', interpolation: 'step', keys: [{ t: 0, v: [0, 40, 0] }] }] },
    ]
    return m
  }
  const slotErrors = (m: unknown) => validateModel(m).errors.filter((e) => e.startsWith('slot') || e.startsWith('attach'))

  it('Flügel hängen am Rumpf, Hüte am Kopf', () => {
    expect(slotErrors(wings())).toEqual([])
    expect(slotErrors({ ...wings(), attach: 'head' })).toHaveLength(1)
    expect(slotErrors({ ...tiny(), attach: 'body' })).toHaveLength(1)
    expect(slotErrors({ ...tiny(), slot: 'back' })).toHaveLength(1)
  })

  it('Zustand: sneak vor air vor idle', () => {
    expect(wingState({ crouching: true, onGround: true })).toBe('sneak')
    expect(wingState({ crouching: true, flying: true, onGround: false })).toBe('air')
    expect(wingState({ onGround: false })).toBe('air')
    expect(wingState({ onGround: false, inLiquid: true })).toBe('idle')
    expect(wingState({ onGround: true })).toBe('idle')
    // Fliegen zählt als in der Luft, auch dicht über dem Boden (wie `WingState.classify` im Cubiq Client).
    expect(wingState({ onGround: true, flying: true })).toBe('air')
  })

  it('Rückfall auf idle und exklusive Zustands-Animationen', () => {
    const m = wings()
    expect(resolveDriver(m, 'air')).toBe('air')
    expect(resolveDriver(m, 'sneak')).toBe('idle')
    expect(stateAnimations(m, 0, 'air').get('root')!.rotation[1]).toBe(40)
    expect(stateAnimations(m, 0, 'idle').get('root')!.rotation[1]).toBe(0)
  })

  it('blendet 250 ms weich über', () => {
    const m = wings()
    const mix = createStateMixer()
    expect(mix.pose(m, 0, 'idle').get('root')!.rotation[1]).toBe(0)
    mix.pose(m, 1000, 'air')
    expect(mix.state).toBe('air')
    const half = mix.pose(m, 1125, 'air').get('root')!.rotation[1]
    expect(half).toBeCloseTo(20)
    expect(mix.pose(m, 1300, 'air').get('root')!.rotation[1]).toBe(40)
    // Fehlende Knochen zählen als Ruhe.
    const b = blendPoses(new Map(), new Map([['x', { position: [2, 0, 0], rotation: [0, 0, 0], scale: [3, 1, 1] }]]), 0.5)
    expect(b.get('x')).toEqual({ position: [1, 0, 0], rotation: [0, 0, 0], scale: [2, 1, 1] })
  })
})

/** Größter Unterschied zweier Posen (fehlende Knochen = Ruhe). */
function maxDiff(a: Map<string, BonePose>, b: Map<string, BonePose>): number {
  const rest: BonePose = { position: [0, 0, 0], rotation: [0, 0, 0], scale: [1, 1, 1] }
  let d = 0
  for (const id of new Set([...a.keys(), ...b.keys()])) {
    const pa = a.get(id) ?? rest
    const pb = b.get(id) ?? rest
    for (const ch of ['position', 'rotation', 'scale'] as const) for (let j = 0; j < 3; j++) d = Math.max(d, Math.abs(pa[ch][j]! - pb[ch][j]!))
  }
  return d
}

describe('Kosmetik v2 – Bewegung getragener Teile (Hut, Begleiter am Kopf, Flügel)', () => {
  /** Teil mit genau diesen Treibern; der i-te dreht `root` um (i + 1) · 10° um y – so ist zu sehen, welcher läuft. */
  const worn = (drivers: Driver[], slot: CosmeticSlot = 'hat'): CosmeticModel => ({
    ...tiny(),
    slot,
    attach: SLOT_ATTACH[slot],
    animations: drivers.map((driver, i) => ({
      id: `${driver}_${i}`,
      driver,
      lengthMs: 1000,
      tracks: [{ bone: 'root', channel: 'rotation', interpolation: 'step', keys: [{ t: 0, v: [0, (i + 1) * 10, 0] }] }],
    })),
  })

  it('Schwellen wie im Cubiq Client: gehen, schleichen, abspringen, fallen', () => {
    expect([WALK_SPEED, WALK_HOLD_MS, JUMP_VY, STATE_FADE_MS]).toEqual([0.04, 150, 0.05, 250])
    expect(WEAR_STATES).toEqual(['idle', 'walk', 'jump', 'air', 'sneak'])
    // Gehen (≈ 0,216 Blöcke je Tick) ist walk – erst schneller als 0,04, Stehen und leichtes Rutschen bleiben idle.
    expect(wearState({ onGround: true, speed: 0.216 })).toBe('walk')
    expect(wearState({ onGround: true, speed: 0.04 })).toBe('idle')
    expect(wearState({ onGround: true, speed: 0.041 })).toBe('walk')
    expect(wearState({ onGround: true })).toBe('idle')
    // Schleichen geht vor, auch in Bewegung (≈ 0,065) und beim Sprung aus dem Schleichen.
    expect(wearState({ onGround: true, crouching: true, speed: 0.065 })).toBe('sneak')
    expect(wearState({ onGround: false, crouching: true, vy: 0.42 })).toBe('sneak')
    // Absprung (0,42) ist jump, solange er schneller steigt als 0,05 – auch im Laufen; danach und beim Fallen air.
    expect(wearState({ onGround: false, vy: 0.42, speed: 0.216 })).toBe('jump')
    expect(wearState({ onGround: false, vy: 0.051 })).toBe('jump')
    expect(wearState({ onGround: false, vy: 0.05 })).toBe('air')
    expect(wearState({ onGround: false, vy: -0.3 })).toBe('air')
    expect(wearState({ onGround: false })).toBe('air')
  })

  it('Schwimmen, Reiten und Klettern bleiben idle, auch in Bewegung – Fliegen ist air, auch beim Steigen', () => {
    const still: Movement[] = [
      { onGround: true, inLiquid: true, speed: 0.1 },
      { onGround: false, inLiquid: true, speed: 0.1, vy: 0.1 },
      { onGround: true, riding: true, speed: 0.3 },
      { onGround: false, riding: true, speed: 0.3, vy: 0.2 },
      { onGround: false, climbing: true, vy: 0.1176 },
      { onGround: true, climbing: true, speed: 0.1 },
    ]
    for (const p of still) expect(wearState(p)).toBe('idle')
    expect(wearState({ flying: true, onGround: false, vy: 0.3 })).toBe('air')
    expect(wearState({ flying: true, onGround: true, speed: 0.3 })).toBe('air')
    expect(wearState({ flying: true, crouching: true, onGround: false, vy: -0.3 })).toBe('air')
  })

  it('Teile ohne walk und jump wechseln in denselben Momenten wie bisher', () => {
    // Flügel (idle/air/sneak), eine Mütze mit idle/air und ein Begleiter nur mit idle spielen bei jeder Bewegung den
    // Treiber, den ihnen schon das alte `wingState` gab: walk zeigen sie als idle, jump als air.
    const models = [auroraWings(), worn(['idle', 'air']), worn(['idle', 'idle'], 'companion')]
    const flags = ['crouching', 'flying', 'onGround', 'riding', 'inLiquid', 'climbing'] as const
    let seen = 0
    for (let bits = 0; bits < 1 << flags.length; bits++) {
      for (const speed of [0, 0.04, 0.065, 0.216, 0.6]) {
        for (const vy of [-0.5, 0, 0.05, 0.2, 0.42]) {
          const p: Movement = { speed, vy }
          flags.forEach((k, i) => (p[k] = (bits & (1 << i)) !== 0))
          for (const m of models) expect(resolveDriver(m, wearState(p))).toBe(resolveDriver(m, wingState(p)))
          seen++
        }
      }
    }
    expect(seen).toBe(64 * 25)
  })

  it('Rückfall je Fach: getragene Teile jump → air, sonst idle – Begleiter am Boden wie bisher', () => {
    // Mütze ohne jump: der Absprung zeigt air; sneak hat sie nicht → idle.
    const noJump = worn(['idle', 'walk', 'air'])
    expect(WEAR_STATES.map((s) => resolveDriver(noJump, s))).toEqual(['idle', 'walk', 'air', 'air', 'idle'])
    expect(stateAnimations(noJump, 0, 'jump').get('root')!.rotation[1]).toBe(30)
    // Ohne air auch kein Rückfall dorthin: idle.
    expect(resolveDriver(worn(['idle', 'walk']), 'jump')).toBe('idle')
    // Flügel: walk → idle, jump → air.
    expect(WEAR_STATES.map((s) => resolveDriver(auroraWings(), s))).toEqual(['idle', 'idle', 'air', 'air', 'sneak'])
    // Wer alles selbst animiert, spielt jeden Zustand selbst.
    expect(WEAR_STATES.map((s) => resolveDriver(christmasHat(), s))).toEqual([...WEAR_STATES])
    // Begleiter am Boden (Anhang E) unverändert: jump kennt er nicht → idle, sneak ohne eigene Animation → walk.
    const pet = worn(['idle', 'walk', 'air'], 'pet')
    expect(resolveDriver(pet, 'jump')).toBe('idle')
    expect(resolveDriver(pet, 'sneak')).toBe('walk')
  })

  it('Knöpfe der Vorschau: „steht“ immer, sonst nur, was das Modell selbst animiert', () => {
    const buttons = (m: unknown) => WEAR_STATES.filter((s) => wearStateAvailable(m, s))
    expect(buttons(christmasHat())).toEqual(['idle', 'walk', 'jump', 'air', 'sneak'])
    expect(buttons(auroraWings())).toEqual(['idle', 'air', 'sneak'])
    expect(buttons(worn(['idle', 'air']))).toEqual(['idle', 'air'])
    // jump nur mit eigener Animation – der Rückfall sähe genauso aus wie air.
    expect(buttons(worn(['idle', 'walk', 'air']))).toEqual(['idle', 'walk', 'air'])
    expect(buttons(worn(['idle'], 'companion'))).toEqual(['idle'])
    // Das Modell kommt ungeprüft vom Server: Kaputtes zeigt nur „steht“, ohne Fehler.
    for (const bad of [null, 'walk', {}, { animations: 'walk' }, { animations: [null, 3, { driver: 7 }, ['walk']] }]) expect(buttons(bad)).toEqual(['idle'])
  })

  it('Zipfelmütze: gültig ohne Warnungen, nur bekannte Treiber, fünf Bewegungen', () => {
    const m = christmasHat()
    expect(validateModel(m)).toEqual({ ok: true, errors: [], warnings: [] })
    expect([m.id, m.name, m.slot, m.attach]).toEqual(['christmas_hat', 'Zipfelmütze', 'hat', 'head'])
    // Nur Treiber, die es schon gibt (ältere Launcher und Clients lehnen sonst das ganze Modell ab), keine Aktionen.
    const drivers = (m.animations ?? []).map((a) => a.driver ?? 'idle')
    expect(drivers.filter((d) => !(DRIVERS as readonly string[]).includes(d))).toEqual([])
    expect([...new Set(drivers)].sort()).toEqual(['air', 'idle', 'jump', 'sneak', 'walk'])
    expect(m.actions).toBeUndefined()
  })

  it('blendet von walk nach air 250 ms weich über, dann läuft genau air', () => {
    const m = christmasHat()
    const mix = createStateMixer()
    const t0 = 1_759_150_000_000
    for (let t = t0; t <= t0 + 400; t += 16) expect(maxDiff(mix.pose(m, t, 'walk'), stateAnimations(m, t, 'walk'))).toBe(0)
    const last = stateAnimations(m, t0 + 400, 'walk')
    // Wechsel: Ausgang ist das zuletzt gezeigte walk-Bild, das Ziel läuft auf seiner Zeit weiter (Smoothstep, Mitte = ½).
    const t1 = t0 + 416
    expect(maxDiff(mix.pose(m, t1, 'air'), last)).toBe(0)
    expect(mix.state).toBe('air')
    expect(maxDiff(mix.pose(m, t1 + 125, 'air'), blendPoses(last, stateAnimations(m, t1 + 125, 'air'), 0.5))).toBeLessThan(1e-9)
    expect(maxDiff(mix.pose(m, t1 + 250, 'air'), stateAnimations(m, t1 + 250, 'air'))).toBe(0)
    // walk und air sehen wirklich verschieden aus.
    expect(maxDiff(last, stateAnimations(m, t1 + 250, 'air'))).toBeGreaterThan(1)
  })
})
