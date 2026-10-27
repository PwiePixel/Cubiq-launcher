import { createHash } from 'node:crypto'
import { inflateSync } from 'node:zlib'
import { describe, expect, it } from 'vitest'
import { DEFAULT_TOPIC, TOPICS, pickTopic, releaseArt, seedFor } from '../scripts/release-art.mjs'
import { encodePng, renderVoxels, rgb } from '../scripts/voxel.mjs'
import { versionSeed } from '../app/utils/changelog'

/** PNG lesen: Größe, Farbtyp und die Pixel (nur Filter 0, wie encodePng schreibt). */
function readPng(png: Buffer) {
  expect(png.subarray(0, 8)).toEqual(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  const width = png.readUInt32BE(16)
  const height = png.readUInt32BE(20)
  const idat: Buffer[] = []
  for (let at = 8; at < png.length; ) {
    const len = png.readUInt32BE(at)
    const type = png.toString('ascii', at + 4, at + 8)
    if (type === 'IDAT') idat.push(png.subarray(at + 8, at + 8 + len))
    at += 12 + len
  }
  const raw = inflateSync(Buffer.concat(idat))
  return { width, height, colorType: png[25], raw }
}

describe('Bild je Release', () => {
  it('wählt das Thema nach dem Text – fette Überschriften zählen mehr', () => {
    expect(pickTopic('- **Cubiq Launcher is here.** The first version.').id).toBe('launch')
    expect(pickTopic('- **Reduced animations in the launcher also calm the Cubiq Client.**').id).toBe('motion')
    expect(pickTopic('- **Clearer message when a shared mod file is missing.** …').id).toBe('fix')
    expect(pickTopic('- **Trim your clips.** The clip gallery can cut recordings.').id).toBe('media')
    expect(pickTopic('- **The Cubiq Client gets the launcher\'s look.** Menus and title screen of the in-game mod.').id).toBe('client')
    expect(pickTopic('- **Modpacks from CurseForge.**').id).toBe('mods')
    expect(pickTopic('').id).toBe(DEFAULT_TOPIC.id)
    // Jedes Thema hat ein eigenes Motiv.
    expect(new Set(TOPICS.map((t) => t.subject)).size).toBe(TOPICS.length)
  })

  it('zeichnet ein freigestelltes 512er-PNG – je Version anders, dieselbe Version immer gleich', () => {
    const a = releaseArt({ version: '0.4.2', text: '- **Fix a crash.**' })
    const again = releaseArt({ version: '0.4.2', text: '- **Fix a crash.**' })
    const other = releaseArt({ version: '0.4.3', text: '- **Fix a crash.**' })
    expect(a.topic).toBe('fix')
    expect(createHash('sha256').update(a.png).digest('hex')).toBe(createHash('sha256').update(again.png).digest('hex'))
    expect(a.png.equals(other.png)).toBe(false)
    const { width, height, colorType, raw } = readPng(a.png)
    expect([width, height, colorType]).toEqual([512, 512, 6])
    // Ränder durchsichtig, in der Mitte das Motiv.
    const alpha = (x: number, y: number) => raw[y * (512 * 4 + 1) + 1 + x * 4 + 3]!
    expect(alpha(0, 0)).toBe(0)
    expect(alpha(511, 511)).toBe(0)
    let opaque = 0
    for (let y = 0; y < 512; y += 4) for (let x = 0; x < 512; x += 4) if (alpha(x, y) === 255) opaque++
    expect(opaque).toBeGreaterThan(1500)
    expect(a.png.length).toBeLessThan(200_000)
    // Ein festes Thema schlägt den Text.
    expect(releaseArt({ version: '0.4.2', text: '- **Fix a crash.**', topic: 'launch' }).topic).toBe('launch')
  })

  it('nutzt denselben Startwert wie die Szenen im Launcher', () => {
    for (const v of ['0.1.0', '0.2.0', '1.10.3']) expect(seedFor(v)).toBe(versionSeed(v))
  })

  it('Renderer: Würfel als Sechseck, deckend und kantengeglättet, gültiges PNG', () => {
    const rgba = renderVoxels([{ x: 0, y: 0, z: 0, color: rgb('#71a330') }], { size: 64, ss: 4, fill: 0.8 })
    const at = (x: number, y: number) => Array.from(rgba.subarray((y * 64 + x) * 4, (y * 64 + x) * 4 + 4))
    expect(at(32, 32)[3]).toBe(255)
    expect(at(1, 1)[3]).toBe(0)
    // Oberseite heller als die rechte Seite (Licht von oben links).
    expect(at(32, 16)[1]!).toBeGreaterThan(at(44, 40)[1]!)
    // Kantenglättung: am Rand gibt es halbdurchsichtige Pixel.
    expect(Array.from({ length: 64 * 64 }, (_, i) => rgba[i * 4 + 3]!).some((a) => a > 0 && a < 255)).toBe(true)
    const png = encodePng(rgba, 64, 64)
    expect(readPng(png).width).toBe(64)
  })
})
