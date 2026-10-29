import { buildWinterLayout, type WinterLayoutOptions } from './layout'
import { round } from './math'
import { WinterPainter, type WinterFrame } from './painter'
import { buildWinterPalette, type WinterVariant } from './palette'
import { WinterSnow } from './snow'

// Brücke vom ARGB-Bild des Malers zum Canvas: Hintergrund, Vorschau in den Einstellungen, Titelbilder.

/** Größte Szene in Texeln – wie `WinterScene.java` im Cubiq Client (MAX_COLS/MAX_ROWS). */
export const WINTER_MAX_COLS = 640
export const WINTER_MAX_ROWS = 400

/**
 * Kantenlänge eines Texels in CSS-Pixeln für eine Fläche von `width` × `height` CSS-Pixeln: gut 3 Bildschirmpixel,
 * immer ganze Gerätepixel (sonst wären die Pixel unterschiedlich breit) und nie mehr als 640 × 400 Texel. Große oder
 * sehr scharfe Bildschirme bekommen so gröbere Texel statt einer teureren Szene.
 */
export function winterTexel(width: number, height: number, dpr: number): number {
  const scale = dpr > 0 ? dpr : 1
  const device = Math.max(2, round(3 * scale), Math.ceil((width * scale) / WINTER_MAX_COLS), Math.ceil((height * scale) / WINTER_MAX_ROWS))
  return device / scale
}

/** ARGB-Pixel als deckende RGBA-Bytes in ein `ImageData` schreiben. */
export function copyPixels(pixels: Uint32Array, data: Uint8ClampedArray) {
  for (let i = 0, o = 0; i < pixels.length; i++, o += 4) {
    const v = pixels[i]!
    data[o] = (v >>> 16) & 255
    data[o + 1] = (v >>> 8) & 255
    data[o + 2] = v & 255
    data[o + 3] = 255
  }
}

/** Standbild der Szene in ein neues Canvas (Texel-Auflösung); `null` ohne 2D-Kontext. `curtain`: Vorhang (siehe CURTAINS). */
export function paintWinterStill(
  options: WinterLayoutOptions,
  variant: WinterVariant,
  frame: Omit<WinterFrame, 'snow' | 'moving'>,
  snowDensity = 0.8,
  curtain = 0,
): HTMLCanvasElement | null {
  const layout = buildWinterLayout(options)
  const painter = new WinterPainter(layout, buildWinterPalette(variant, curtain))
  const pixels = new Uint32Array(layout.w * layout.h)
  painter.compose(pixels, { ...frame, moving: false, snow: new WinterSnow(layout.w, layout.h, options.seed ^ 0x5e0, snowDensity) })
  const canvas = document.createElement('canvas')
  canvas.width = layout.w
  canvas.height = layout.h
  const g = canvas.getContext('2d')
  if (!g) return null
  const image = g.createImageData(layout.w, layout.h)
  copyPixels(pixels, image.data)
  g.putImageData(image, 0, 0)
  return canvas
}
