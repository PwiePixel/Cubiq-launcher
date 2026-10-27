import { buildWinterLayout, type WinterLayoutOptions } from './layout'
import { WinterPainter, type WinterFrame } from './painter'
import { buildWinterPalette, type WinterVariant } from './palette'
import { WinterSnow } from './snow'

// Brücke vom ARGB-Bild des Malers zum Canvas: Hintergrund, Vorschau in den Einstellungen, Titelbilder.

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

/** Standbild der Szene in ein neues Canvas (Texel-Auflösung); `null` ohne 2D-Kontext. */
export function paintWinterStill(options: WinterLayoutOptions, variant: WinterVariant, frame: Omit<WinterFrame, 'snow' | 'moving'>, snowDensity = 0.8): HTMLCanvasElement | null {
  const layout = buildWinterLayout(options)
  const painter = new WinterPainter(layout, buildWinterPalette(variant))
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
