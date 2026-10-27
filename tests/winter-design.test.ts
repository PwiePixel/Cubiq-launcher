import { describe, expect, it } from 'vitest'
import { accentAfterSwitch, designAccent, DESIGN_HINT, designs, normalizeDesign, writeDesign } from '../app/utils/design'
import { uiSettingsSchema } from '../app/utils/schemas'
import { auroraLevel, buildWinterLayout, pineTiers, pineTops, voxelX, voxelY, WINTER_SEED } from '../app/utils/winter/layout'
import { between, checksum, glide, hash, light, over, sequence, wave } from '../app/utils/winter/math'
import { WinterPainter } from '../app/utils/winter/painter'
import { AURORA_COLORS, buildWinterPalette, type WinterVariant } from '../app/utils/winter/palette'
import { WinterSnow } from '../app/utils/winter/snow'

// Design „Polarlicht“ (winter): die Einstellung, die Szene und – vor allem – dass der Cubiq Client im Spiel
// dasselbe Bild malt. Die Zahlen unter „Gleich wie im Spiel“ prüft WinterTest.java im Mod mit denselben Werten.

describe('Einstellung „Design“', () => {
  function target() {
    const dataset: Record<string, string | undefined> = { accent: 'ice', theme: 'light' }
    const saved = new Map<string, string>()
    const storage = { setItem: (k: string, v: string) => void saved.set(k, v), removeItem: (k: string) => void saved.delete(k) }
    return { dataset, saved, target: { root: { dataset }, storage } }
  }

  it('setzt data-design nur für Polarlicht und merkt es für den Ladebildschirm', () => {
    const h = target()
    writeDesign('winter', h.target)
    expect(h.dataset.design).toBe('winter')
    expect(h.saved.get(DESIGN_HINT)).toBe('winter')
    // Farbschema und Akzent sind eigene Einstellungen.
    expect(h.dataset.accent).toBe('ice')
    expect(h.dataset.theme).toBe('light')
    writeDesign('cubiq', h.target)
    expect('design' in h.dataset).toBe(false)
    expect(h.saved.has(DESIGN_HINT)).toBe(false)
    // Ohne Speicher (privater Modus) bleibt es beim Attribut.
    const broken = { root: { dataset: {} as Record<string, string | undefined> }, storage: { setItem: () => { throw new Error('voll') }, removeItem: () => {} } }
    expect(() => writeDesign('winter', broken)).not.toThrow()
    expect(broken.root.dataset.design).toBe('winter')
  })

  it('unbekannte oder fehlende Werte werden Cubiq', () => {
    expect(designs).toEqual(['cubiq', 'winter'])
    for (const value of [undefined, null, 'neon', 'Winter', 'fox', 42]) expect(normalizeDesign(value)).toBe('cubiq')
    expect(designs.map(normalizeDesign)).toEqual([...designs])
  })

  it('Polarlicht bringt Eisblau mit, eine selbst gewählte Farbe bleibt', () => {
    expect(designAccent).toEqual({ cubiq: 'cubiq', winter: 'ice' })
    expect(accentAfterSwitch('winter', 'cubiq')).toBe('ice')
    expect(accentAfterSwitch('cubiq', 'ice')).toBe('cubiq')
    expect(accentAfterSwitch('winter', 'emerald')).toBe('emerald')
    expect(accentAfterSwitch('cubiq', 'lapis')).toBe('lapis')
  })

  it('ist in den UI-Einstellungen optional (ältere Kerne) und nachsichtig', () => {
    const base = {
      theme: 'dark',
      accent: 'ice',
      advancedRendering: true,
      animatedBackground: true,
      motion: 'full',
      worldsTab: true,
      screenshotsTab: true,
      historyTab: true,
      sidebarRecent: true,
      sidebarAccount: true,
      hideRightSidebar: false,
      compactLibrary: false,
      showPlayTime: true,
      language: 'de',
    }
    expect(uiSettingsSchema.parse(base).design).toBe('cubiq')
    expect(uiSettingsSchema.parse({ ...base, design: 'winter' }).design).toBe('winter')
    expect(uiSettingsSchema.parse({ ...base, design: 'neon' }).design).toBe('cubiq')
  })
})

describe('Szene „Polarlicht“', () => {
  it('entsteht aus Größe und Seed immer gleich und bleibt im Bild', () => {
    const a = buildWinterLayout({ cols: 427, rows: 240, seed: WINTER_SEED })
    expect(buildWinterLayout({ cols: 427, rows: 240, seed: WINTER_SEED })).toEqual(a)
    expect(buildWinterLayout({ cols: 427, rows: 240, seed: 7 }).islands).not.toEqual(a.islands)
    expect(a.ribbons).toHaveLength(3)
    for (const s of a.stars) expect(s.y).toBeLessThan(a.h * 0.74)
    // Fern hinten, genau eine Hauptinsel vorn – alle ganz im Bild, gerade Würfelgrößen.
    expect(a.islands.filter((i) => i.depth === 2)).toHaveLength(1)
    expect(a.islands.map((i) => i.depth)).toEqual([...a.islands.map((i) => i.depth)].sort())
    for (const isl of a.islands) {
      expect(isl.voxels.length).toBeGreaterThan(0)
      expect(isl.ox + isl.x0).toBeGreaterThanOrEqual(1)
      expect(isl.ox + isl.x1).toBeLessThanOrEqual(a.w - 1)
      expect(isl.oy + isl.y0).toBeGreaterThanOrEqual(1)
      expect(isl.a % 2).toBe(0)
    }
    // Das Wolkenmeer liegt unten, die ferne Bank über der nahen.
    for (let x = 0; x < a.w; x++) {
      expect(a.cloudNear.top[x]!).toBeGreaterThan(a.h * 0.8)
      expect(a.cloudFar.top[x]!).toBeGreaterThan(a.h * 0.7)
    }
    expect(a.cloudFar.drift).toBe(0)
    expect(a.cloudNear.drift).toBeGreaterThan(0)
  })

  it('Inseln: eine Lage verschneiter Würfel, darunter eine gestufte Spitze, Tannen obenauf', () => {
    const L = buildWinterLayout({ cols: 427, rows: 240, seed: WINTER_SEED })
    const hero = L.islands.find((i) => i.depth === 2)!
    const top = hero.voxels.filter((v) => v.k === 0).length
    const deepest = Math.max(...hero.voxels.map((v) => v.k))
    expect(top).toBeGreaterThanOrEqual(6)
    expect(deepest).toBeGreaterThanOrEqual(1)
    expect(hero.voxels.filter((v) => v.k === deepest).length).toBeLessThan(top)
    // Jede Säule hängt durchgehend an der oberen Lage.
    for (const v of hero.voxels) if (v.k > 0) expect(hero.voxels.some((u) => u.i === v.i && u.j === v.j && u.k === v.k - 1)).toBe(true)
    // Zeichenreihenfolge: hinten (kleines i + j − k) zuerst.
    const order = hero.voxels.map((v) => v.i + v.j - v.k)
    expect(order).toEqual([...order].sort((p, q) => p - q))
    expect(hero.pines.length).toBeGreaterThanOrEqual(2)
    for (const p of hero.pines) expect(hero.voxels.some((v) => v.i === p.i && v.j === p.j && v.k === 0)).toBe(true)
    expect([voxelX(8, { i: 1, j: 0, k: 1 }), voxelY(8, { i: 1, j: 0, k: 1 })]).toEqual([8, 12])
    expect(pineTiers(12)).toEqual([8, 6, 4])
    expect(pineTops([8, 6, 4])).toEqual([-18, -25, -29])
    expect(pineTiers(4)).toEqual([2])
  })

  it('Startseite: die große Insel schwebt genau über dem Spielen-Knopf', () => {
    const L = buildWinterLayout({ cols: 505, rows: 160, seed: WINTER_SEED, anchor: { left: 360, top: 128, right: 480 } })
    const hero = L.islands.find((i) => i.depth === 2)!
    expect(Math.abs(hero.ox + (hero.x0 + hero.x1) / 2 - 420)).toBeLessThanOrEqual(1)
    expect(hero.oy + hero.y1).toBe(125)
  })

  it('kommt mit kleinen Flächen zurecht (Leer-Zustände, Titelbilder)', () => {
    const tiny = buildWinterLayout({ cols: 3, rows: 2, seed: 1 })
    expect([tiny.w, tiny.h]).toEqual([16, 16])
    expect(tiny.islands).toEqual([])
    const strip = buildWinterLayout({ cols: 300, rows: 40, seed: 0x51 })
    expect(strip.ribbons).toHaveLength(2)
    expect(strip.islands.every((i) => i.depth === 0 && i.voxels.length > 0)).toBe(true)
    const painter = new WinterPainter(tiny, buildWinterPalette('dark'))
    const px = new Uint32Array(tiny.w * tiny.h)
    painter.compose(px, { tick: 3, seconds: 2, level: 1, moving: true, snow: new WinterSnow(tiny.w, tiny.h, 1) })
    for (const v of px) expect(v >>> 24).toBe(255)
  })

  it('Polarlicht atmet ruhig zwischen 0,55 und 0,95', () => {
    let lo = 1
    let hi = 0
    for (let t = 0; t < 300; t += 0.25) {
      lo = Math.min(lo, auroraLevel(t))
      hi = Math.max(hi, auroraLevel(t))
    }
    expect(lo).toBeCloseTo(0.55, 5)
    expect(hi).toBeCloseTo(0.95, 5)
  })

  it('Bild: deckend; das Polarlicht leuchtet nur am Himmel und hellt nachts nur auf', () => {
    const L = buildWinterLayout({ cols: 240, rows: 135, seed: WINTER_SEED })
    const painter = new WinterPainter(L, buildWinterPalette('dark'))
    const calm = new Uint32Array(L.w * L.h)
    const lit = new Uint32Array(L.w * L.h)
    painter.compose(calm, { tick: 0, seconds: 20, level: 0, moving: false, snow: null })
    painter.compose(lit, { tick: 0, seconds: 20, level: 1, moving: false, snow: null })
    let changed = 0
    for (let i = 0; i < calm.length; i++) {
      expect(calm[i]! >>> 24).toBe(255)
      if (calm[i] === lit[i]) continue
      changed++
      expect(Math.floor(i / L.w)).toBeLessThan(L.h * 0.6)
      for (const shift of [16, 8, 0]) expect((lit[i]! >>> shift) & 255).toBeGreaterThanOrEqual((calm[i]! >>> shift) & 255)
    }
    expect(changed).toBeGreaterThan(1500)
    // Das Polarlicht trägt das Cubiq-Grün aus dem Logo.
    expect(AURORA_COLORS[1]).toBe('#71a330')
  })

  it('Standbild: Inseln und Wolken stehen still, egal welche Sekunde', () => {
    const L = buildWinterLayout({ cols: 240, rows: 135, seed: WINTER_SEED })
    const painter = new WinterPainter(L, buildWinterPalette('oled'))
    const a = new Uint32Array(L.w * L.h)
    const b = new Uint32Array(L.w * L.h)
    painter.compose(a, { tick: 5, seconds: 3, level: 0, moving: false, snow: null })
    painter.compose(b, { tick: 5, seconds: 333, level: 0, moving: false, snow: null })
    expect(checksum(a)).toBe(checksum(b))
    painter.compose(b, { tick: 5, seconds: 333, level: 0, moving: true, snow: null })
    expect(checksum(a)).not.toBe(checksum(b))
  })

  it('Schnee: ohne Zustand, fällt nach unten, läuft am Rand um und bleibt im Rahmen', () => {
    const snow = new WinterSnow(190, 100, 5)
    expect(snow.count).toBe(45)
    const again = new WinterSnow(190, 100, 5)
    let fell = 0
    for (let i = 0; i < snow.count; i++) {
      expect([0, 1, 2]).toContain(snow.depthOf(i))
      expect([again.x(i, 77), again.y(i, 77)]).toEqual([snow.x(i, 77), snow.y(i, 77)])
      if (snow.y(i, 10) > snow.y(i, 0) || snow.y(i, 10) < snow.y(i, 0) - 50) fell++
      for (let t = 0; t < 4000; t += 37) {
        expect(snow.x(i, t)).toBeGreaterThanOrEqual(-4)
        expect(snow.x(i, t)).toBeLessThan(190 + 4)
        expect(snow.y(i, t)).toBeGreaterThanOrEqual(-4)
        expect(snow.y(i, t)).toBeLessThan(100 + 4)
      }
    }
    expect(fell).toBe(snow.count)
  })
})

describe('Gleich wie im Spiel (WinterTest.java prüft dieselben Zahlen)', () => {
  it('Rechenkern', () => {
    expect([hash(1, 2, 3), hash(-5, 7, 1440), hash(12345, 678, 0x2c1b)]).toEqual([0.10170144238509238, 0.7964508407749236, 0.7557331561110914])
    expect([glide(3.25, 7), glide(-1.5, 99)]).toEqual([0.2455291334335925, 0.5092066435609013])
    expect([wave(0), wave(0.25), wave(0.3), wave(-0.6)]).toEqual([0, 1, 0.96, 0.6399999999999999])
    const r = sequence(WINTER_SEED ^ 0x51ce)
    expect([r(), r(), r()]).toEqual([0.5623783876653761, 0.8647929569706321, 0.6829025782644749])
    expect(between(0xff000000, 0xffffffff, 0.5)).toBe(0xff808080)
    expect(over(0xff000000, 0xffffffff, 128)).toBe(0xff808080)
    expect(light(0xff404040, 0xff808080, 255)).toBe(0xffa0a0a0)
    expect(light(0xff102030, 0xff71a330, 200)).toBe(0xff63904f)
  })

  it('Aufbau und Bild', () => {
    for (const [size, want] of Object.entries(GOLDEN)) {
      const [w, h] = size.split('x').map(Number) as [number, number]
      const L = buildWinterLayout({ cols: w, rows: h, seed: WINTER_SEED })
      const snow = new WinterSnow(L.w, L.h, 7)
      expect(L.stars.length, size).toBe(want.stars)
      expect(L.islands.reduce((n, i) => n + i.voxels.length, 0), size).toBe(want.voxels)
      expect(L.cloudNear.top.reduce((n, v) => n + v, 0), size).toBe(want.cloudNear)
      expect(L.cloudFar.top.reduce((n, v) => n + v, 0), size).toBe(want.cloudFar)
      expect([snow.count, snow.x(0, 100), snow.y(0, 100), snow.depthOf(0)], size).toEqual(want.flakes)
      for (const variant of ['dark', 'oled', 'light'] as WinterVariant[]) {
        const painter = new WinterPainter(L, buildWinterPalette(variant))
        const px = new Uint32Array(L.w * L.h)
        painter.compose(px, { tick: 30, seconds: 21.5, level: 0.8, moving: true, snow })
        const moving = checksum(px)
        painter.compose(px, { tick: 40, seconds: 21, level: 0.8, moving: false, snow: null })
        expect([moving, checksum(px)], `${size} ${variant}`).toEqual(want.frames[variant])
      }
    }
  })
})

/** Prüfsummen (FNV-1a über die ARGB-Pixel) – dieselben Werte stehen in WinterTest.java. */
const GOLDEN: Record<string, { stars: number; voxels: number; cloudNear: number; cloudFar: number; flakes: number[]; frames: Record<WinterVariant, [number, number]> }> = {
  '240x135': {
    stars: 125,
    voxels: 39,
    cloudNear: 27497,
    cloudFar: 25259,
    flakes: [77, 100, 116, 1],
    frames: { dark: [2172306274, 947558308], oled: [2523732370, 3691853699], light: [716548140, 4218033526] },
  },
  '427x240': {
    stars: 394,
    voxels: 42,
    cloudNear: 87152,
    cloudFar: 80666,
    flakes: [244, 176, 185, 1],
    frames: { dark: [594324932, 1511407891], oled: [1687684407, 3493181329], light: [3951712061, 1430334280] },
  },
}
