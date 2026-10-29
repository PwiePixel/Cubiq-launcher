// PNG lesen für Tests (Bilder des Release, Logo der Start-Animation): Blöcke zerlegen und Zeilenfilter zurückrechnen.
import { expect } from 'vitest'

export interface Chunk {
  type: string
  data: Buffer
}

export function chunks(png: Buffer): Chunk[] {
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
export function unfilter(raw: Buffer, width: number, height: number, bpp: number): Uint8Array {
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
