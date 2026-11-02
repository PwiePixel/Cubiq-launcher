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

/** Spieler-Text, der genau vom Sonderthema „cosmetics“ spricht – es wird trotzdem nie aus dem Text gewählt. */
const COSMETICS_TEXT =
  '- **Nine new free cosmetics.** A waddling penguin, a fox kit, an axolotl, a snowman, a firefly, aurora wings, a shooting star and Mini-Cubi.'
/** Dasselbe für das Sonderthema „petting“. */
const PETTING_TEXT =
  '- **Pet and feed your companions.** Right-click your own pet with an empty hand to pet it or with food to feed it – or use the Pet and Feed buttons in the previews.'

/** Farben eines Bilds als „#rrggbb“, wahlweise nur die Zeilen y0 … y1 - 1. */
function colorsOf(frame: Canvas, y0 = 0, y1 = 64) {
  const set = new Set<string>()
  for (let y = y0; y < y1; y++) {
    for (let x = 0; x < 64; x++) {
      const c = frame.get(x, y)
      if (c) set.add(`#${c.slice(0, 3).map((v) => v.toString(16).padStart(2, '0')).join('')}`)
    }
  }
  return set
}

/** Farbe eines Punkts als „#rrggbb“ (`null`, wenn leer oder außerhalb). */
function hexAt(frame: Canvas, x: number, y: number) {
  const c = frame.get(x, y)
  return c ? `#${c.slice(0, 3).map((v) => v.toString(16).padStart(2, '0')).join('')}` : null
}

/** Schwerpunkt aller Punkte einer Farbe. */
function centre(frame: Canvas, color: string) {
  let sx = 0
  let sy = 0
  let n = 0
  for (let y = 0; y < 64; y++) {
    for (let x = 0; x < 64; x++) {
      if (hexAt(frame, x, y) !== color) continue
      sx += x
      sy += y
      n++
    }
  }
  return [sx / n, sy / n] as const
}

/**
 * Cubis Augen in einem Bild der Szene „petting“: Sein Mund (nur Cubi hat diese Farbe) gibt die Lage des Gesichts, die
 * Augen sind je 2 × 3 Punkte wie im Sprite. Je Auge das Muster (W = Augenweiß, K = Pupille, · = Lid), wie viel Weiß zu
 * sehen ist und wohin die Pupille schaut: `x`/`y` je −1 (links/oben), 0 oder 1 (rechts/unten), bei geschlossenen Augen
 * 0. Dazu `mid`, die Mitte zwischen den Augen.
 */
function cubiEyes(frame: Canvas) {
  let mx = 64
  let my = 64
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (hexAt(frame, x, y) === '#22301a') [mx, my] = [Math.min(mx, x), Math.min(my, y)]
  const left = mx - 6
  const top = my - 8
  const eyes = [left + 3, left + 10].map((ex) => {
    const rows: string[] = []
    const pupils: number[][] = []
    for (let dy = 0; dy < 3; dy++) {
      let row = ''
      for (let dx = 0; dx < 2; dx++) {
        const c = hexAt(frame, ex + dx, top + 4 + dy)
        row += c === '#f6f9f2' ? 'W' : c === '#18210f' ? 'K' : '·'
        if (c === '#18210f') pupils.push([dx, dy])
      }
      rows.push(row)
    }
    const pattern = rows.join('/')
    const white = pattern.split('W').length - 1
    const mean = (k: number) => pupils.reduce((sum, p) => sum + p[k]!, 0) / pupils.length
    return { pattern, white, x: white ? Math.sign(mean(0) - 0.5) : 0, y: white ? Math.sign(mean(1) - 1) : 0 }
  })
  return { eyes, mid: [left + 7.5, top + 5] as const }
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
    // Jedes Thema hat 16 Bilder – ein Sonderthema auch eine eigene Zahl (`frames`).
    for (const topic of all) expect(releaseFrames('0.1.0', topic.id).frames).toHaveLength((topic as { frames?: number }).frames ?? FRAMES)
    // Sonderthemen gibt es nur auf Wunsch (`--topic`) – nie aus dem Text, auch wenn er genau davon spricht.
    expect(pickTopic('- **The launcher is green again.** Back to the green colours.').id).not.toBe('green')
    expect(releaseArt({ version: '0.2.51', topic: 'green' }).topic).toBe('green')
    expect(pickTopic(COSMETICS_TEXT).id).not.toBe('cosmetics')
    expect(pickTopic(PETTING_TEXT).id).not.toBe('petting')
  })

  it('Sonderthema „cosmetics“ (0.3.0): alle neun Teile, längere flüssige Schleife – nahtlos, scharf und im Bild', () => {
    // Nur mit `--topic`: das feste Thema schlägt den Text.
    const art = releaseArt({ version: '0.3.0', text: COSMETICS_TEXT, topic: 'cosmetics' })
    expect(art.topic).toBe('cosmetics')
    // 32 verschiedene Bilder à 80 ms (2,56 s), endlos; das erste ist das ruhige Bild.
    const list = chunks(art.apng)
    const actl = list.find((c) => c.type === 'acTL')!.data
    expect([actl.readUInt32BE(0), actl.readUInt32BE(4)]).toEqual([32, 0])
    const delays = list.filter((c) => c.type === 'fcTL').map((c) => c.data.readUInt16BE(20) / c.data.readUInt16BE(22))
    expect(delays).toEqual(Array.from({ length: 32 }, () => 0.08))
    expect(Buffer.from(decode(art.apng).rgba).equals(Buffer.from(decode(art.png).rgba))).toBe(true)
    expect([decode(art.png).width, decode(art.png).colorType, decode(art.apng).colorType]).toEqual([512, 3, 3])
    expect(art.png.length).toBeLessThan(20_000)
    expect(art.apng.length).toBeLessThan(150_000)

    const { frames } = releaseFrames('0.3.0', 'cosmetics')
    expect(frames).toHaveLength(32)
    // Dieselbe Version immer gleich (ohne Zufall).
    const again = releaseFrames('0.3.0', 'cosmetics').frames
    expect(frames.every((frame, f) => Buffer.from(frame.data).equals(Buffer.from(again[f]!.data)))).toBe(true)
    // Scharfe Pixel (ganz deckend oder ganz durchsichtig); oben, links und rechts bleibt in jedem Bild ein freier Rand
    // (unten darf die Inselspitze an den Rand reichen, wie bei den anderen Bildern).
    expect(frames.every((frame) => frame.data.every((v, i) => i % 4 !== 3 || v === 0 || v === 255))).toBe(true)
    const border = (frame: Canvas) => Array.from({ length: 64 }, (_, i) => frame.get(i, 0) ?? frame.get(0, i) ?? frame.get(63, i)).some(Boolean)
    expect(frames.some(border)).toBe(false)

    // Im ruhigen Bild sind alle neun zu sehen – je eine kennzeichnende Farbe: Schal des Pinguins, Fell des Füchschens,
    // Haut des Axolotls, Karottennase des Schneemännchens, Laterne des Glühwürmchens, Stern, Federn der Flügel; dazu
    // Cubi auf der Insel und Mini-Cubi hoch darüber.
    const still = frames[0]!
    const all = colorsOf(still)
    for (const color of ['#d63e4a', '#e2702c', '#f6b3c6', '#f08a2c', '#f2ff8a', '#ffd23f', '#2aa894', '#78d6f2', '#71a330']) expect(all).toContain(color)
    expect(colorsOf(still, 0, 20)).toContain('#b3dc72')
  })

  it('Sonderthema „petting“ (0.3.2): Cubi krault das Füchschen und füttert den Pinguin – nahtlos, scharf und im Bild', () => {
    // Nur mit `--topic`: das feste Thema schlägt den Text.
    const art = releaseArt({ version: '0.3.2', text: PETTING_TEXT, topic: 'petting' })
    expect(art.topic).toBe('petting')
    // Schleife wie bei 0.3.0: 32 verschiedene Bilder à 80 ms (2,56 s), endlos; das erste ist das ruhige Bild.
    const list = chunks(art.apng)
    const actl = list.find((c) => c.type === 'acTL')!.data
    expect([actl.readUInt32BE(0), actl.readUInt32BE(4)]).toEqual([32, 0])
    const delays = list.filter((c) => c.type === 'fcTL').map((c) => c.data.readUInt16BE(20) / c.data.readUInt16BE(22))
    expect(delays).toEqual(Array.from({ length: 32 }, () => 0.08))
    expect(Buffer.from(decode(art.apng).rgba).equals(Buffer.from(decode(art.png).rgba))).toBe(true)
    const png = decode(art.png)
    expect([png.width, png.height, png.colorType, decode(art.apng).colorType]).toEqual([512, 512, 3, 3])
    expect(art.png.length).toBeLessThan(20_000)
    expect(art.apng.length).toBeLessThan(150_000)

    const { frames } = releaseFrames('0.3.2', 'petting')
    expect(frames).toHaveLength(32)
    // Dieselbe Version immer gleich (ohne Zufall).
    const again = releaseFrames('0.3.2', 'petting').frames
    expect(frames.every((frame, f) => Buffer.from(frame.data).equals(Buffer.from(again[f]!.data)))).toBe(true)
    // Scharfe Pixel und oben, links und rechts in jedem Bild ein freier Rand – auch wenn die Herzchen hoch steigen.
    expect(frames.every((frame) => frame.data.every((v, i) => i % 4 !== 3 || v === 0 || v === 255))).toBe(true)
    const border = (frame: Canvas) => Array.from({ length: 64 }, (_, i) => frame.get(i, 0) ?? frame.get(0, i) ?? frame.get(63, i)).some(Boolean)
    expect(frames.some(border)).toBe(false)
    // Nahtlos: Jedes Bild unterscheidet sich vom nächsten (kein Stillstand), und vom letzten zurück zum ersten ändert
    // sich nicht mehr als bei einem gewöhnlichen Schritt – kein Sprung an der Naht.
    const changed = (a: Canvas, b: Canvas) => {
      let n = 0
      for (let i = 0; i < a.data.length; i += 4) if (a.data.subarray(i, i + 4).some((v, k) => v !== b.data[i + k])) n++
      return n
    }
    const steps = frames.map((frame, f) => changed(frame, frames[(f + 1) % frames.length]!))
    expect(Math.min(...steps)).toBeGreaterThan(0)
    const median = [...steps].sort((a, b) => a - b)[steps.length >> 1]!
    expect(steps[steps.length - 1]).toBeLessThanOrEqual(2 * median)

    // Im ruhigen Bild ist alles zu sehen – je eine kennzeichnende Farbe: Cubi, Fell des Füchschens, Schal des Pinguins,
    // Rücken des Fischs, Herzchen, Haut des Axolotls, Laterne des Glühwürmchens.
    const still = colorsOf(frames[0]!)
    for (const color of ['#71a330', '#e2702c', '#d63e4a', '#5b8fb6', '#f2648c', '#f6b3c6', '#f2ff8a']) expect(still).toContain(color)
    // Das Futter wird nicht aufgebraucht (der Fisch ist in jedem Bild), und die Herzchen steigen ohne Pause auf: vom
    // Füchschen unten bis hoch in den Himmel.
    expect(frames.every((frame) => colorsOf(frame).has('#5b8fb6'))).toBe(true)
    expect(frames.every((frame) => colorsOf(frame).has('#f2648c'))).toBe(true)
    const heartRows = frames.flatMap((frame) => Array.from({ length: 64 }, (_, y) => y).filter((y) => colorsOf(frame, y, y + 1).has('#f2648c')))
    expect(Math.min(...heartRows)).toBeLessThan(12)
    expect(Math.max(...heartRows)).toBeGreaterThan(28)
  })

  it('Sonderthema „petting“: Cubis Augen folgen dem Fisch und dem Füchschen, jeder Blickwechsel mit einem Lidschlag', () => {
    const { frames } = releaseFrames('0.3.2', 'petting')
    const n = frames.length
    const faces = frames.map(cubiEyes)
    // Beide Augen immer gleich (kein Auge breiter oder anders gerichtet als das andere).
    for (const { eyes } of faces) expect(eyes[1]!.pattern).toBe(eyes[0]!.pattern)
    const look = faces.map(({ eyes }) => eyes[0]!)

    // Beim Füttern (Bild 0 bis 17, alle drei Bissen) ganz offen nach links oben – dort hängt der Fisch über dem Pinguin.
    // Das ruhige Bild (Bild 0) gehört dazu.
    expect(look[0]!.pattern).toBe('KW/KW/WW')
    for (let f = 0; f <= 17; f++) {
      expect(look[f]).toMatchObject({ white: 4, x: -1, y: -1 })
      const [fishX, fishY] = centre(frames[f]!, '#5b8fb6')
      expect(fishX).toBeLessThan(faces[f]!.mid[0])
      expect(fishY).toBeLessThan(faces[f]!.mid[1])
    }
    // Beim Kraulen (Bild 20 bis 29) nach rechts unten – dort sitzt das Füchschen.
    for (let f = 20; f <= 29; f++) {
      expect(look[f]).toMatchObject({ pattern: 'WW/WK/WK', x: 1, y: 1 })
      const [foxX, foxY] = centre(frames[f]!, '#e2702c')
      expect(foxX).toBeGreaterThan(faces[f]!.mid[0])
      expect(foxY).toBeGreaterThan(faces[f]!.mid[1])
    }

    // Die Pupillen springen nie: Zwischen zwei Bildern, in denen sie zu sehen sind, bleibt die Richtung gleich – auch
    // über die Naht vom letzten zum ersten Bild. Die Richtung wechselt nur über ein Bild mit geschlossenen Augen (Strich
    // unten wie beim Blinzeln), danach gehen die Lider in zwei Schritten auf: halb, ganz.
    for (let f = 0; f < n; f++) {
      const [a, b] = [look[f]!, look[(f + 1) % n]!]
      if (a.white && b.white) expect([f, b.x]).toEqual([f, a.x])
    }
    const shut = look.flatMap((e, f) => (e.white ? [] : [f]))
    expect(shut).toEqual([18, 30])
    for (const f of shut) {
      expect(look[f]!.pattern).toBe('··/··/KK')
      const [before, after, open] = [look[(f + n - 1) % n]!, look[(f + 1) % n]!, look[(f + 2) % n]!]
      // Jeder Lidschlag ist ein Blickwechsel (Fisch ↔ Füchschen), die neue Richtung schon beim halb offenen Auge.
      expect(after.x).toBe(-before.x)
      expect(after.white).toBeGreaterThan(0)
      expect(after.white).toBeLessThan(4)
      expect(after.pattern.startsWith('··/')).toBe(true)
      expect(open).toMatchObject({ white: 4, x: after.x })
    }
  })

  it('Sonderthema „petting“: Krümel fliegen hinter dem Pinguin und Cubi vorbei – kein heller Fleck auf ihnen', () => {
    const { frames } = releaseFrames('0.3.2', 'petting')
    // Krümelfarben (Papierweiß, Fischsilber hell und mittel) direkt neben dem Frack des Pinguins oder Cubis Körper
    // hieße: ein Krümel liegt auf der Figur oder auf ihrem Umriss. Alle Figuren haben einen Umriss, also grenzt sonst
    // nichts in diesen Farben an sie.
    const crumb = new Set(['#f2f5f1', '#d6ecf6', '#94c4dd'])
    const figure = new Set(['#313d5e', '#232c45', '#46557c', '#b3dc72', '#96c955', '#86b844', '#71a330', '#55821f', '#456b19'])
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]] as const
    const spots: string[] = []
    frames.forEach((frame, f) => {
      for (let y = 0; y < 64; y++) {
        for (let x = 0; x < 64; x++) {
          if (!crumb.has(hexAt(frame, x, y) ?? '')) continue
          if (near.some(([dx, dy]) => figure.has(hexAt(frame, x + dx, y + dy) ?? ''))) spots.push(`Bild ${f}: ${x},${y}`)
        }
      }
    })
    expect(spots).toEqual([])
    // Trotzdem fliegen nach jedem Bissen (Bild 1, 8, 15) sichtbar Krümel: ab dem zweiten Bild danach immer welche frei
    // in der Luft (ein Punkt in Krümelfarbe ohne Nachbarn – kein Umriss, keine Figur).
    const flying = (frame: Canvas) => {
      let count = 0
      for (let y = 0; y < 64; y++) {
        for (let x = 0; x < 64; x++) {
          if (crumb.has(hexAt(frame, x, y) ?? '') && near.every(([dx, dy]) => !frame.get(x + dx, y + dy))) count++
        }
      }
      return count
    }
    for (const bite of [1, 8, 15]) for (let age = 2; age <= 6; age++) expect(flying(frames[bite + age]!)).toBeGreaterThan(0)
  })

  it('Sonderthema „petting“: keine weißen Flecken auf dem Kopf des Pinguins – die weiße Maske bleibt ein Stück', () => {
    const { frames } = releaseFrames('0.3.2', 'petting')
    // Dunkle Farben des Pinguins: Frack (Glanz, mittel, dunkel) und Auge. Hell ist alles, was heller ist als die hellste
    // davon. Ein heller Punkt, der auf allen vier Seiten an diese Farben grenzt, sähe aus wie ein Fleck auf dem Kopf
    // (beim glücklich geschlossenen Auge wie ein zweites Augenpaar).
    const dark = new Set(['#46557c', '#313d5e', '#232c45', '#18210f'])
    const luma = (color: string) => {
      const n = Number.parseInt(color.slice(1), 16)
      return 0.299 * (n >> 16) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255)
    }
    const darkest = Math.max(...[...dark].map(luma))
    const scarf = new Set(['#d63e4a', '#a72d38', '#f5e9d3'])
    const near = [[1, 0], [-1, 0], [0, 1], [0, -1]] as const
    const specks: string[] = []
    const split: string[] = []
    frames.forEach((frame, f) => {
      // Den Schal trägt nur der Pinguin: eine Zeile aus 7 Punkten direkt unter dem Kopf (9 × 6 Punkte, links bündig).
      const knots: (readonly [number, number])[] = []
      for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (scarf.has(hexAt(frame, x, y) ?? '')) knots.push([x, y])
      const sx = Math.min(...knots.map(([x]) => x))
      const top = Math.min(...knots.map(([, y]) => y)) - 6
      expect([knots.length, knots.every(([, y]) => y === top + 6)]).toEqual([7, true])
      // Kopf samt Umriss: Zeilen top - 1 … top + 5, Spalten sx - 1 … sx + 9 (Lage im Sprite: x - sx, y - top).
      const mask = new Set<string>()
      for (let y = top - 1; y < top + 6; y++) {
        for (let x = sx - 1; x <= sx + 9; x++) {
          const color = hexAt(frame, x, y)
          if (!color || luma(color) <= darkest) continue
          if (color === '#f6f5f0') mask.add(`${x},${y}`)
          if (near.every(([dx, dy]) => dark.has(hexAt(frame, x + dx, y + dy) ?? ''))) specks.push(`Bild ${f}: ${x - sx},${y - top} ${color}`)
        }
      }
      // Die weiße Gesichtsmaske (Bauchweiß im Kopf) hängt zusammen – über die vier Nachbarn erreichbar.
      expect(mask.size).toBeGreaterThanOrEqual(8)
      const seen = new Set([[...mask][0]!])
      for (const key of seen) {
        const [x, y] = key.split(',').map(Number) as [number, number]
        for (const [dx, dy] of near) if (mask.has(`${x + dx},${y + dy}`)) seen.add(`${x + dx},${y + dy}`)
      }
      if (seen.size < mask.size) split.push(`Bild ${f}: ${seen.size} von ${mask.size}`)
    })
    expect(specks).toEqual([])
    expect(split).toEqual([])
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

  it('Sonderthema „map“ (0.3.1): große Karte mit Route zum Wegpunkt – 16 feste Bilder, ganz im Bild, bewegt', () => {
    // Nur auf Wunsch (`--topic`): ein Text über Karten bekommt das allgemeine Welt-Motiv, nie die große Karte.
    const notes = [
      '- **The map update.** The minimap and the world map of the Cubiq Client get a calm new frame, a compass and a view cone.',
      '- **Chunk grid, scale bar and ruler.** Measure distances on the world map.',
      '- **Go to coordinates.** Type coordinates on the world map and a waypoint shows the way.',
    ].join('\n')
    expect(pickTopic(notes).id).toBe('worlds')
    expect(TOPICS.map((t) => t.id)).not.toContain('map')
    expect(SPECIAL_TOPICS.map((t) => t.id)).toContain('map')

    const { frames, topic } = releaseFrames('0.3.1', 'map')
    expect(topic.id).toBe('map')
    expect(frames).toHaveLength(FRAMES)
    // Fest: noch einmal gezeichnet, Punkt für Punkt dieselben Bilder.
    const again = releaseFrames('0.3.1', 'map').frames
    frames.forEach((frame: Canvas, i: number) => expect(Buffer.from(frame.data).equals(Buffer.from(again[i]!.data))).toBe(true))
    // Ganz im Bild: nichts reicht bis an den Rand, also wird auch nichts abgeschnitten. Bei anderen Versionen darf nur
    // die Insel (ihre Wurzeln) die untere Zeile erreichen.
    const atEdge = (frame: Canvas) => {
      const hits: string[] = []
      for (let i = 0; i < frame.w; i++) {
        for (const [x, y] of [[i, 0], [i, frame.h - 1], [0, i], [frame.w - 1, i]]) if (frame.get(x!, y!)) hits.push(`${x},${y}`)
      }
      return hits
    }
    for (const frame of frames) expect(atEdge(frame)).toEqual([])
    for (const version of ['0.1.0', '0.9.3', '1.0.0', '2.7.11']) {
      for (const frame of releaseFrames(version, 'map').frames) expect(atEdge(frame).filter((p) => !p.endsWith(',63'))).toEqual([])
    }
    // Bewegt: alle 16 Bilder sind verschieden, und auf der Karte wandern die Striche der Route (Cubiq-Grün rechts von
    // Cubi) – nach 2 Bildern verschoben, nach 8 wieder wie am Anfang: die Schleife ist nahtlos.
    expect(new Set(frames.map((frame: Canvas) => Buffer.from(frame.data).toString('base64'))).size).toBe(FRAMES)
    const route = (frame: Canvas) => {
      const dots: string[] = []
      for (let y = 0; y < frame.h; y++) {
        for (let x = 31; x < frame.w; x++) {
          const c = frame.get(x, y)
          if (c && c[0] === 0x71 && c[1] === 0xa3 && c[2] === 0x30) dots.push(`${x},${y}`)
        }
      }
      return dots.join(' ')
    }
    expect(route(frames[0])).not.toBe('')
    expect(route(frames[0])).not.toBe(route(frames[2]))
    expect(route(frames[0])).toBe(route(frames[8]))
    // Animiertes PNG mit allen 16 Bildern; das erste ist das ruhige Bild.
    const { png, apng } = releaseArt({ version: '0.3.1', topic: 'map' })
    expect(chunks(apng).find((c) => c.type === 'acTL')!.data.readUInt32BE(0)).toBe(FRAMES)
    expect(Buffer.from(decode(apng).rgba).equals(Buffer.from(decode(png).rgba))).toBe(true)
  })

  it('Sonderthema „waypoints“ (0.3.3): klassische Minikarte, ein Wegpunkt fliegt aus dem Chat hinein – fest, im Bild, nahtlos', () => {
    // Nur auf Wunsch (`--topic`): ein Text über die klassische Minikarte und geteilte Wegpunkte bekommt das allgemeine
    // Welt-Motiv, nie diese Szene.
    const notes = [
      '- **The classic minimap look.** A thin grey frame, the compass letters N, E, S and W, every waypoint as a small coloured square with its first letter and a red arrow for you.',
      '- **Add shared waypoints with one click.** When someone shares a waypoint in the chat, click "[+ Waypoint]" and it is on your own map.',
    ].join('\n')
    expect(pickTopic(notes).id).toBe('worlds')
    expect(TOPICS.map((t) => t.id)).not.toContain('waypoints')
    expect(SPECIAL_TOPICS.map((t) => t.id)).toContain('waypoints')

    // Schleife wie bei 0.3.0: 32 Bilder à 80 ms (2,56 s), endlos; das erste ist das ruhige Bild.
    const art = releaseArt({ version: '0.3.3', text: notes, topic: 'waypoints' })
    expect(art.topic).toBe('waypoints')
    const list = chunks(art.apng)
    const actl = list.find((c) => c.type === 'acTL')!.data
    expect([actl.readUInt32BE(0), actl.readUInt32BE(4)]).toEqual([32, 0])
    const delays = list.filter((c) => c.type === 'fcTL').map((c) => c.data.readUInt16BE(20) / c.data.readUInt16BE(22))
    expect(delays).toEqual(Array.from({ length: 32 }, () => 0.08))
    expect(Buffer.from(decode(art.apng).rgba).equals(Buffer.from(decode(art.png).rgba))).toBe(true)
    expect(art.png.length).toBeLessThan(20_000)
    expect(art.apng.length).toBeLessThan(150_000)

    const { frames, topic } = releaseFrames('0.3.3', 'waypoints')
    expect(topic.id).toBe('waypoints')
    expect(frames).toHaveLength(32)
    // Fest: noch einmal gezeichnet, Punkt für Punkt dieselben Bilder.
    const again = releaseFrames('0.3.3', 'waypoints').frames
    frames.forEach((frame: Canvas, i: number) => expect(Buffer.from(frame.data).equals(Buffer.from(again[i]!.data))).toBe(true))
    // Scharfe Pixel, und nichts reicht bis an den Rand – auch der Wegpunkt oben im Bogen nicht, also wird nichts
    // abgeschnitten. Bei anderen Versionen darf nur die Insel (ihre Wurzeln) die untere Zeile erreichen.
    expect(frames.every((frame) => frame.data.every((v, i) => i % 4 !== 3 || v === 0 || v === 255))).toBe(true)
    const atEdge = (frame: Canvas) => {
      const hits: string[] = []
      for (let i = 0; i < frame.w; i++) {
        for (const [x, y] of [[i, 0], [i, frame.h - 1], [0, i], [frame.w - 1, i]]) if (frame.get(x!, y!)) hits.push(`${x},${y}`)
      }
      return hits
    }
    for (const frame of frames) expect(atEdge(frame)).toEqual([])
    for (const version of ['0.1.0', '0.9.3', '1.0.0', '2.7.11']) {
      for (const frame of releaseFrames(version, 'waypoints').frames) expect(atEdge(frame).filter((p) => !p.endsWith(',63'))).toEqual([])
    }
    // Bewegt: alle 32 Bilder sind verschieden.
    expect(new Set(frames.map((frame: Canvas) => Buffer.from(frame.data).toString('base64'))).size).toBe(32)

    // Im ruhigen Bild ist alles zu sehen – je eine kennzeichnende Farbe: Cubi, der blaue Freund, die Sprechblase, der neue
    // Wegpunkt (violett), die Wegpunkte auf der Karte (orange, blau), der rote Pfeil, das Wasser und die dunkle Platte
    // des „N“.
    const still = colorsOf(frames[0]!)
    for (const color of ['#71a330', '#b4def7', '#f2f5f1', '#a77be0', '#ef8a32', '#5aa9e6', '#e0533d', '#72b8e8', '#353f4f']) expect(still).toContain(color)

    // Die Geschichte: Der neue Wegpunkt fliegt oben über Blase und Karte (über Zeile 11 ist sonst nichts Violettes) und
    // zieht Bild für Bild nach rechts zur Karte – auch über die Naht der Schleife hinweg, ohne Sprung. Nach der Landung
    // sitzt er in der Karte, und bevor die nächste Nachricht kommt, ist er wieder weg.
    const violet = (frame: Canvas, where: (x: number, y: number) => boolean) => {
      const xs: number[] = []
      for (let y = 0; y < frame.h; y++) {
        for (let x = 0; x < frame.w; x++) {
          const c = frame.get(x, y)
          if (c && c[0] === 0xa7 && c[1] === 0x7b && c[2] === 0xe0 && where(x, y)) xs.push(x)
        }
      }
      return xs
    }
    const flight = [29, 30, 31, 0, 1, 2].map((f) => violet(frames[f]!, (_, y) => y < 11))
    for (const xs of flight) expect(xs.length).toBeGreaterThan(0)
    const mid = flight.map((xs) => xs.reduce((sum, x) => sum + x, 0) / xs.length)
    mid.slice(1).forEach((x, i) => {
      expect(x).toBeGreaterThan(mid[i]!)
      expect(x - mid[i]!).toBeLessThan(4)
    })
    const inMap = (f: number) => violet(frames[f]!, (x, y) => x >= 33 && y >= 12)
    for (const f of [6, 10, 16]) expect(inMap(f).length).toBeGreaterThan(0)
    for (const f of [21, 24]) expect(inMap(f)).toEqual([])
    // Nach der Landung steigt ein grünes „+“ über die Karte.
    expect(colorsOf(frames[10]!, 0, 10)).toContain('#71a330')
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
