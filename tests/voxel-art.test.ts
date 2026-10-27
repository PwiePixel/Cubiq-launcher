import { describe, expect, it } from 'vitest'
import { DEFAULT_TOPIC, TOPICS, releaseFrames } from '../scripts/release-art.mjs'
import { upscale } from '../scripts/pixel-art.mjs'
import {
  ART_CELLS,
  buildVoxelModel,
  detectOutline,
  sampleArtGrid,
  shadeColor,
  voxelModelFromImage,
  type RgbaImage,
  type VoxelModel,
} from '../app/utils/voxelArt'

// Das Update-Bild als 3D-Modell (UpdateModel3d.vue): Die Zerlegung in Voxel ist reine Rechnung und wird hier mit
// echten Release-Szenen aus scripts/release-art.mjs geprüft – so wie sie als banner.png (512 × 512) ausgeliefert werden.

const OUTLINE = 0x141b18
const GRASS_TOP = 0xb4e064
const CUBI_BODY = 0x71a330

/** Ruhiges Bild einer Szene, wie banner.png (×8 vergrößert). */
function banner(version: string, topic: string): RgbaImage {
  const { frames } = releaseFrames(version, topic)
  const big = upscale(frames[0], 8)
  return { width: big.w, height: big.h, data: big.data }
}

function cellsOf(model: VoxelModel, x: number, y: number) {
  return model.boxes.filter((b) => b.x === x && b.y === y)
}

describe('Update-Bild → Raster', () => {
  it('liest je 8 × 8-Block die Mitte und erkennt Durchsichtiges', () => {
    const grid = sampleArtGrid(banner('0.2.0', 'motion'))!
    expect(grid.size).toBe(ART_CELLS)
    expect(grid.cells.length).toBe(64 * 64)
    // Oben links ist Himmel (freigestellt), unten in der Mitte die Insel.
    expect(grid.cells[0]).toBe(-1)
    expect([...grid.cells].filter((c) => c >= 0).length).toBeGreaterThan(600)
    expect(detectOutline(grid)).toBe(OUTLINE)
  })

  it('nimmt auch das ursprüngliche 64 × 64-Bild und andere ganze Vielfache', () => {
    const { frames } = releaseFrames('0.1.0', 'launch')
    const small = sampleArtGrid({ width: 64, height: 64, data: frames[0].data })!
    const big = sampleArtGrid(banner('0.1.0', 'launch'))!
    expect([...small.cells]).toEqual([...big.cells])
    const x2 = upscale(frames[0], 2)
    expect([...sampleArtGrid({ width: x2.w, height: x2.h, data: x2.data })!.cells]).toEqual([...big.cells])
  })

  it('lehnt Bilder ab, die keine freigestellte, vergrößerte Pixel-Art sind', () => {
    const image = banner('0.1.0', 'launch')
    expect(sampleArtGrid({ ...image, width: 500, height: 512 })).toBeNull()
    expect(sampleArtGrid({ width: 500, height: 500, data: new Uint8Array(500 * 500 * 4) })).toBeNull()
    // Leer bzw. ganz deckend (Foto, Logo mit Hintergrund).
    expect(sampleArtGrid({ width: 512, height: 512, data: new Uint8Array(512 * 512 * 4) })).toBeNull()
    expect(sampleArtGrid({ width: 512, height: 512, data: new Uint8Array(512 * 512 * 4).fill(255) })).toBeNull()
    // Geglättet vergrößert (jeder Block mit Verlauf): keine einfarbigen Blöcke.
    const smooth = new Uint8Array(512 * 512 * 4)
    for (let y = 0; y < 512; y++) {
      for (let x = 0; x < 512; x++) {
        const i = (y * 512 + x) * 4
        const inside = (x - 256) ** 2 + (y - 256) ** 2 < 200 ** 2
        smooth.set([x % 256, y % 256, (x + y) % 256, inside ? 255 : 0], i)
      }
    }
    expect(sampleArtGrid({ width: 512, height: 512, data: smooth })).toBeNull()
    // Zu kurze Daten.
    expect(sampleArtGrid({ width: 512, height: 512, data: new Uint8Array(10) })).toBeNull()
  })
})

describe('Raster → Voxel-Modell', () => {
  const model = voxelModelFromImage(banner('0.2.0', 'motion'))!

  it('lässt den Umriss am Rand weg und färbt Umriss zwischen Teilen um', () => {
    expect(model.boxes.length).toBeGreaterThan(600)
    expect(model.boxes.some((b) => b.color === OUTLINE)).toBe(false)
    // Kein Quader dort, wo das Bild durchsichtig ist.
    const grid = sampleArtGrid(banner('0.2.0', 'motion'))!
    for (const b of model.boxes) expect(grid.cells[b.y * 64 + b.x]).toBeGreaterThanOrEqual(0)
  })

  it('macht die Insel am tiefsten und unten schmaler', () => {
    const island = model.boxes.filter((b) => b.kind === 'island')
    expect(island.length).toBeGreaterThan(300)
    const depthAt = (y: number) => Math.max(...island.filter((b) => b.y === y).map((b) => b.z1 - b.z0))
    // Oberkante der Insel: die erste Zeile, die fast durchgehend Gras ist (darüber nur Halme und Blumen).
    const top = Math.min(...island.filter((b) => b.color === GRASS_TOP && island.filter((o) => o.y === b.y).length > 40).map((b) => b.y))
    expect(depthAt(top)).toBe(18)
    // Halme und Blumen obenauf sind dünn.
    expect(depthAt(top - 1)).toBeLessThanOrEqual(4)
    expect(model.depth).toBe(18)
    const bottom = Math.max(...island.map((b) => b.y))
    expect(depthAt(bottom)).toBeLessThan(depthAt(bottom - 4))
    expect(depthAt(bottom - 4)).toBeLessThan(depthAt(top))
    // Gerade Tiefen mittig um z = 0: Vorder- und Rückseiten liegen auf ganzen Voxeln.
    for (const b of island) expect(Number.isInteger(b.z0) && Number.isInteger(b.z1)).toBe(true)
    const whole = island.filter((b) => cellsOf(model, b.x, b.y).length === 1)
    for (const b of whole) expect(b.z0).toBe(-b.z1)
  })

  it('gibt Figuren halbe Tiefe und Funkeln eine dünne Scheibe', () => {
    const cubi = model.boxes.filter((b) => b.color === CUBI_BODY)
    expect(cubi.length).toBeGreaterThan(50)
    for (const b of cubi) {
      expect(b.kind).toBe('figure')
      expect(b.z1 - b.z0).toBe(10)
    }
    const sparks = model.boxes.filter((b) => b.kind === 'spark')
    expect(sparks.length).toBeGreaterThan(0)
    for (const b of sparks) expect(b.z1 - b.z0).toBe(1)
    // Schwebende Mini-Würfel (4 × 4) werden echte Würfel.
    const minis = new Map<number, VoxelModel['boxes']>()
    for (const b of model.boxes.filter((b) => b.kind === 'figure' && b.z1 - b.z0 === 4)) minis.set(b.group, [...(minis.get(b.group) ?? []), b])
    expect([...minis.values()].some((boxes) => boxes.length === 16)).toBe(true)
  })

  it('legt unter Cubis Füße einen Kontaktschatten statt eines Lochs', () => {
    // Unter dem Fuß: Insel-Zeile mit Rand in Grasfarbe und dunklerem Mittelteil, so breit wie Cubi.
    const feet = model.boxes.filter((b) => b.color === 0x456b19 && b.kind === 'figure')
    expect(feet.length).toBeGreaterThan(0)
    const foot = feet.reduce((a, b) => (b.y > a.y ? b : a))
    const under = cellsOf(model, foot.x, foot.y + 1)
    expect(under.length).toBe(3)
    expect(under.every((b) => b.kind === 'island')).toBe(true)
    const middle = under.find((b) => b.z0 < 0 && b.z1 > 0)!
    expect(middle.z1 - middle.z0).toBe(10)
    expect(middle.color).toBe(shadeColor(under[0]!.color, 0.7))
    expect(under[0]!.z0).toBe(-9)
    expect(under[2]!.z1).toBe(9)
  })

  it('funktioniert für jedes Thema und jede Version gleich (ohne Zufall)', () => {
    for (const topic of [...TOPICS, DEFAULT_TOPIC]) {
      for (const version of ['0.1.0', '0.4.13', '1.0.0']) {
        const m = voxelModelFromImage(banner(version, topic.id))
        expect(m, `${topic.id} ${version}`).not.toBeNull()
        expect(m!.boxes.some((b) => b.kind === 'island'), topic.id).toBe(true)
        expect(m!.boxes.filter((b) => b.kind === 'figure').length, topic.id).toBeGreaterThan(100)
        expect(m!.depth).toBeLessThanOrEqual(18)
        expect(m!.maxX - m!.minX).toBeGreaterThan(30)
        // Nicht mehr als ~1 Quader je Bildpunkt (+ Kontaktschatten).
        expect(m!.boxes.length).toBeLessThan(64 * 64)
      }
    }
    const a = voxelModelFromImage(banner('0.3.1', 'social'))
    const b = voxelModelFromImage(banner('0.3.1', 'social'))
    expect(a).toEqual(b)
  })

  it('kommt ohne Umriss und mit einzelnen Flächen aus', () => {
    // Ein einzelner grüner Block ohne Umriss: eine Figur, kein Absturz.
    const cells = new Int32Array(64 * 64).fill(-1)
    for (let y = 20; y < 30; y++) for (let x = 20; x < 30; x++) cells[y * 64 + x] = 0x71a330
    const grid = { size: 64, cells }
    expect(detectOutline(grid)).toBeNull()
    const m = buildVoxelModel(grid)!
    expect(m.boxes.length).toBe(100)
    expect(m.boxes.every((b) => b.kind === 'island')).toBe(true)
    expect(buildVoxelModel({ size: 64, cells: new Int32Array(64 * 64).fill(-1) })).toBeNull()
  })

  it('dunkelt Farben je Kanal ab', () => {
    expect(shadeColor(0xffffff, 0.5)).toBe(0x808080)
    expect(shadeColor(0x102030, 1)).toBe(0x102030)
    expect(shadeColor(0x102030, 0)).toBe(0)
  })
})
