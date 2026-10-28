import { createHash } from 'node:crypto'
import { inflateSync } from 'node:zlib'
import { describe, expect, it } from 'vitest'
import { DEFAULT_TOPIC, FRAMES, SPECIAL_TOPICS, TOPICS, pickTopic, releaseArt, releaseFrames, seedFor } from '../scripts/release-art.mjs'
import { Canvas, encodeApng, encodePng, sprite, upscale } from '../scripts/pixel-art.mjs'
import { versionSeed } from '../app/utils/changelog'

interface Chunk {
  type: string
  data: Buffer
}

function chunks(png: Buffer): Chunk[] {
  expect(png.subarray(0, 8)).toEqual(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  const out: Chunk[] = []
  for (let at = 8; at < png.length; ) {
    const len = png.readUInt32BE(at)
    out.push({ type: png.toString('ascii', at + 4, at + 8), data: png.subarray(at + 8, at + 8 + len) })
    at += 12 + len
  }
  return out
}

/** Gefilterte Zeilen zurückrechnen (PNG-Filter 0–4). */
function unfilter(raw: Buffer, width: number, height: number, bpp: number): Uint8Array {
  const stride = width * bpp
  const out = new Uint8Array(stride * height)
  for (let y = 0; y < height; y++) {
    const type = raw[y * (stride + 1)]!
    for (let i = 0; i < stride; i++) {
      const x = raw[y * (stride + 1) + 1 + i]!
      const a = i >= bpp ? out[y * stride + i - bpp]! : 0
      const b = y ? out[(y - 1) * stride + i]! : 0
      const c = i >= bpp && y ? out[(y - 1) * stride + i - bpp]! : 0
      const p = a + b - c
      const paeth = Math.abs(p - a) <= Math.abs(p - b) && Math.abs(p - a) <= Math.abs(p - c) ? a : Math.abs(p - b) <= Math.abs(p - c) ? b : c
      const predict = [0, a, b, Math.floor((a + b) / 2), paeth][type]!
      out[y * stride + i] = (x + predict) & 255
    }
  }
  return out
}

/** Erstes (bzw. einziges) Bild eines PNG/APNG als RGBA. */
function decode(png: Buffer) {
  const list = chunks(png)
  const ihdr = list.find((c) => c.type === 'IHDR')!.data
  const width = ihdr.readUInt32BE(0)
  const height = ihdr.readUInt32BE(4)
  const colorType = ihdr[9]!
  const bpp = colorType === 3 ? 1 : 4
  const pixels = unfilter(inflateSync(Buffer.concat(list.filter((c) => c.type === 'IDAT').map((c) => c.data))), width, height, bpp)
  const plte = list.find((c) => c.type === 'PLTE')?.data
  const trns = list.find((c) => c.type === 'tRNS')?.data
  const rgba = new Uint8Array(width * height * 4)
  for (let i = 0; i < width * height; i++) {
    if (colorType === 3) {
      const k = pixels[i]!
      rgba.set([plte![k * 3]!, plte![k * 3 + 1]!, plte![k * 3 + 2]!, trns && k < trns.length ? trns[k]! : 255], i * 4)
    } else rgba.set(pixels.subarray(i * 4, i * 4 + 4), i * 4)
  }
  return { width, height, colorType, rgba, list }
}

describe('Bild je Release', () => {
  it('wählt das Thema nach dem Text – fette Überschriften zählen mehr', () => {
    expect(pickTopic('- **Cubiq Launcher is here.** The first version.').id).toBe('launch')
    expect(pickTopic('- **Reduced animations in the launcher also calm the Cubiq Client.**').id).toBe('motion')
    expect(pickTopic('- **Clearer message when a shared mod file is missing.** …').id).toBe('fix')
    expect(pickTopic('- **Trim your clips.** The clip gallery can cut recordings.').id).toBe('media')
    expect(pickTopic("- **The Cubiq Client gets the launcher's look.** Menus and title screen of the in-game mod.").id).toBe('client')
    expect(pickTopic('- **Modpacks from CurseForge.**').id).toBe('mods')
    expect(pickTopic('- **A winter look.** Snow falls on the title screen.').id).toBe('winter')
    expect(pickTopic('').id).toBe(DEFAULT_TOPIC.id)
    // Jedes Thema hat ein eigenes Motiv – und für jedes gibt es eine Zeichnung.
    const all = [...TOPICS, ...SPECIAL_TOPICS, DEFAULT_TOPIC]
    expect(new Set(all.map((t) => t.subject)).size).toBe(all.length)
    for (const topic of all) expect(releaseFrames('0.1.0', topic.id).frames).toHaveLength(FRAMES)
    // Sonderthemen gibt es nur auf Wunsch (`--topic`) – nie aus dem Text, auch wenn er genau davon spricht.
    expect(pickTopic('- **The launcher is green again.** Back to the green colours.').id).not.toBe('green')
    expect(releaseArt({ version: '0.2.51', topic: 'green' }).topic).toBe('green')
  })

  it('zeichnet ein freigestelltes 512er-Bild – je Version anders, dieselbe Version immer gleich', () => {
    const a = releaseArt({ version: '0.4.2', text: '- **Fix a crash.**' })
    const again = releaseArt({ version: '0.4.2', text: '- **Fix a crash.**' })
    const other = releaseArt({ version: '0.4.3', text: '- **Fix a crash.**' })
    expect(a.topic).toBe('fix')
    const sha = (b: Buffer) => createHash('sha256').update(b).digest('hex')
    expect(sha(a.png)).toBe(sha(again.png))
    expect(sha(a.apng)).toBe(sha(again.apng))
    expect(a.png.equals(other.png)).toBe(false)
    const { width, height, colorType, rgba } = decode(a.png)
    expect([width, height, colorType]).toEqual([512, 512, 3])
    // Ränder durchsichtig, in der Mitte die Szene – scharfe Pixel: nur ganz deckend oder ganz durchsichtig.
    const alpha = (x: number, y: number) => rgba[(y * 512 + x) * 4 + 3]!
    expect(alpha(0, 0)).toBe(0)
    expect(alpha(511, 511)).toBe(0)
    let opaque = 0
    for (let y = 0; y < 512; y += 4) for (let x = 0; x < 512; x += 4) if (alpha(x, y) === 255) opaque++
    expect(opaque).toBeGreaterThan(2500)
    expect(a.png.length).toBeLessThan(20_000)
    expect(a.apng.length).toBeLessThan(80_000)
    // Ein festes Thema schlägt den Text.
    expect(releaseArt({ version: '0.4.2', text: '- **Fix a crash.**', topic: 'launch' }).topic).toBe('launch')
  })

  it('bewegte Fassung: animiertes PNG, endlos, erstes Bild = ruhiges Bild', () => {
    const { png, apng } = releaseArt({ version: '0.2.0', topic: 'launch' })
    const list = chunks(apng)
    const types = list.map((c) => c.type)
    expect(types.indexOf('acTL')).toBeLessThan(types.indexOf('IDAT'))
    const actl = list.find((c) => c.type === 'acTL')!.data
    const frames = actl.readUInt32BE(0)
    expect(frames).toBeGreaterThan(4)
    expect(frames).toBeLessThanOrEqual(FRAMES)
    expect(actl.readUInt32BE(4)).toBe(0)
    const fctl = list.filter((c) => c.type === 'fcTL')
    expect(fctl).toHaveLength(frames)
    // Fortlaufende Nummern über fcTL und fdAT, das erste Bild deckt alles ab.
    const seqs = list.filter((c) => c.type === 'fcTL' || c.type === 'fdAT').map((c) => c.data.readUInt32BE(0))
    expect(seqs).toEqual(seqs.map((_, i) => i))
    expect([fctl[0]!.data.readUInt32BE(4), fctl[0]!.data.readUInt32BE(8), fctl[0]!.data.readUInt32BE(12), fctl[0]!.data.readUInt32BE(16)]).toEqual([512, 512, 0, 0])
    const total = fctl.reduce((sum, c) => sum + c.data.readUInt16BE(20) / c.data.readUInt16BE(22), 0)
    expect(total).toBeCloseTo(1.6, 5)
    expect(Buffer.from(decode(apng).rgba).equals(Buffer.from(decode(png).rgba))).toBe(true)
  })

  it('nutzt denselben Startwert wie die Szenen im Launcher', () => {
    for (const v of ['0.1.0', '0.2.0', '1.10.3']) expect(seedFor(v)).toBe(versionSeed(v))
  })
})

describe('Pixel-Art-Werkzeug', () => {
  it('Sprite, Umriss und scharfes Vergrößern', () => {
    const c = new Canvas(5, 5).draw(sprite(['a'], { a: [10, 200, 30, 255] }), 2, 2).outline([0, 0, 0, 255])
    expect(c.get(2, 2)).toEqual([10, 200, 30, 255])
    for (const [x, y] of [[1, 2], [3, 2], [2, 1], [2, 3]]) expect(c.get(x!, y!)).toEqual([0, 0, 0, 255])
    // Ecken bekommen keinen Umriss (nur oben, unten, links, rechts).
    expect(c.get(1, 1)).toBeNull()
    const big = upscale(c, 3)
    expect([big.w, big.h]).toEqual([15, 15])
    expect(Array.from(big.data.subarray((7 * 15 + 7) * 4, (7 * 15 + 7) * 4 + 4))).toEqual([10, 200, 30, 255])
    expect(Array.from(big.data.subarray((6 * 15 + 6) * 4, (6 * 15 + 6) * 4 + 4))).toEqual([10, 200, 30, 255])
  })

  it('PNG mit Palette und Durchsichtigkeit; gleiche Bilder werden in der Animation zusammengefasst', () => {
    const img = upscale(new Canvas(4, 4).rect(1, 1, 2, 2, [255, 0, 0, 255]), 2)
    const { width, colorType, rgba } = decode(encodePng(img))
    expect([width, colorType]).toEqual([8, 3])
    expect(Buffer.from(rgba).equals(Buffer.from(img.data))).toBe(true)
    // Nur ein verschiedenes Bild → ein normales PNG ohne acTL.
    expect(chunks(encodeApng([img, img], [100, 100])).some((c) => c.type === 'acTL')).toBe(false)
    const moved = upscale(new Canvas(4, 4).rect(0, 0, 2, 2, [255, 0, 0, 255]), 2)
    const anim = chunks(encodeApng([img, img, moved], [100, 100, 100]))
    expect(anim.find((c) => c.type === 'acTL')!.data.readUInt32BE(0)).toBe(2)
    const delays = anim.filter((c) => c.type === 'fcTL').map((c) => c.data.readUInt16BE(20))
    expect(delays).toEqual([200, 100])
  })
})
