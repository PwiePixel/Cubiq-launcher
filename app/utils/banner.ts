import { HALLOWEEN_EVENT } from './halloween'
import { paintMoonnightBanner } from './moonnight/banner'
import { paintWinterBanner } from './winter/banner'
import { documentVariant } from './winter/palette'

// Titelbild für Instanzen ohne eigenes Banner: ein ruhiges Motiv im Stil des Logos – ein
// isometrischer Würfel als Umriss und eine Spur aus Pixel-Rauten in der Akzentfarbe, aus der
// Instanz-ID erzeugt. So hat jede Instanz von Anfang an ein eigenes, stabiles Bild.
// Ergebnis ist eine kleine SVG-Data-URL; zu Halloween ein Standbild der Mondnacht, im Design „Polarlicht“
// eine kleine Polarlicht-Szene mit schwebenden Schnee-Inseln (Pixel-Art, siehe `isPixelBanner`).

export const BANNER_W = 512
export const BANNER_H = 192

export function bannerSeed(text: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

/** Kleiner, deterministischer Zufall (mulberry32). */
export function rng(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const SIN30 = 0.5
const COS30 = Math.sqrt(3) / 2

/** Umriss eines isometrischen Würfels (Mitte `x`,`y`, Kantenlänge `r`) als SVG-Pfad. */
export function cubePath(x: number, y: number, r: number): string {
  const dx = r * COS30
  const dy = r * SIN30
  const p = (px: number, py: number) => `${px.toFixed(1)} ${py.toFixed(1)}`
  const top = p(x, y - r)
  const ur = p(x + dx, y - dy)
  const lr = p(x + dx, y + dy)
  const bottom = p(x, y + r)
  const ll = p(x - dx, y + dy)
  const ul = p(x - dx, y - dy)
  const center = p(x, y)
  return `M${top}L${ur}L${lr}L${bottom}L${ll}L${ul}Z M${center}L${ul} M${center}L${ur} M${center}L${bottom}`
}

/** Eine Raute (Pixel der Logo-Spuren) mit Mitte `x`,`y` und halber Breite `s`. */
export function diamondPath(x: number, y: number, s: number): string {
  const h = s * 0.58
  return `M${x.toFixed(1)} ${(y - h).toFixed(1)}L${(x + s).toFixed(1)} ${y.toFixed(1)}L${x.toFixed(1)} ${(y + h).toFixed(1)}L${(x - s).toFixed(1)} ${y.toFixed(1)}Z`
}

/** SVG des Motivs (ohne Hintergrund – den liefert die Komponente in Theme-Farben). */
export function bannerSvg(seed: number, accent: string): string {
  const random = rng(seed)
  const cx = BANNER_W * (0.62 + random() * 0.26)
  const cy = BANNER_H * (0.38 + random() * 0.3)
  const r = 54 + random() * 34
  const parts: string[] = []
  parts.push(`<path d="${cubePath(cx, cy, r)}" fill="none" stroke="${accent}" stroke-opacity=".42" stroke-width="7" stroke-linejoin="round" stroke-linecap="round"/>`)
  parts.push(`<path d="${cubePath(cx, cy, r * 0.46)}" fill="${accent}" fill-opacity=".16"/>`)
  // Spur aus Rauten, schräg vom Würfel weg (wie die Pixel-Spuren im Logo).
  const dir = random() < 0.5 ? -1 : 1
  const steps = 5 + Math.floor(random() * 5)
  const s = 9 + random() * 5
  for (let i = 0; i < steps; i++) {
    const ox = cx + dir * (r * 1.05 + i * s * 1.9)
    const oy = cy - r * 0.55 + i * s * 1.1 * (random() < 0.5 ? 1 : 0.5)
    const opacity = (0.5 - i * 0.04).toFixed(2)
    parts.push(`<path d="${diamondPath(ox, oy, s)}" fill="${accent}" fill-opacity="${opacity}"/>`)
    if (random() < 0.45) parts.push(`<path d="${diamondPath(ox + s, oy + s * 0.58, s)}" fill="${accent}" fill-opacity="${(Number(opacity) * 0.6).toFixed(2)}"/>`)
  }
  // Ein zweiter, kleiner Würfel weiter hinten.
  const sx = BANNER_W * (0.12 + random() * 0.3)
  const sy = BANNER_H * (0.2 + random() * 0.5)
  parts.push(`<path d="${cubePath(sx, sy, 16 + random() * 14)}" fill="none" stroke="${accent}" stroke-opacity=".22" stroke-width="4" stroke-linejoin="round"/>`)
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${BANNER_W} ${BANNER_H}" width="${BANNER_W}" height="${BANNER_H}">${parts.join('')}</svg>`
}

const cache = new Map<string, string>()

/** Akzentfarbe des Dokuments (`--color-primary-500`), sonst Cubiq-Grün. */
function documentAccent(): string {
  if (typeof getComputedStyle !== 'function') return '#71a330'
  const value = getComputedStyle(document.documentElement).getPropertyValue('--color-primary-500').trim()
  return /^#[0-9a-f]{3,8}$/i.test(value) ? value : '#71a330'
}

/** Zeigt das erzeugte Motiv Pixel-Art (pixelig skalieren statt weichzeichnen)? */
export function isPixelBanner(url: string | null): boolean {
  return !!url && url.startsWith('data:image/png') && document.documentElement.dataset.design === 'winter'
}

/** Data-URL des Motivs; `null` ohne Dokument (Tests, Server). */
export function generatedBanner(id: string): string | null {
  if (typeof document === 'undefined') return null
  const root = document.documentElement
  const halloween = root.dataset.event === HALLOWEEN_EVENT
  const winter = !halloween && root.dataset.design === 'winter'
  const accent = halloween || winter ? '' : documentAccent()
  const look = halloween ? HALLOWEEN_EVENT : winter ? 'winter' : accent
  const key = `${id}|${look}|${root.dataset.theme ?? 'dark'}`
  const hit = cache.get(key)
  if (hit) return hit
  const seed = bannerSeed(id)
  let url: string | null = null
  try {
    if (halloween) url = paintMoonnightBanner(seed)?.toDataURL('image/png') ?? null
    else if (winter) url = paintWinterBanner(seed, documentVariant())?.toDataURL('image/png') ?? null
    else url = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(bannerSvg(seed, accent))}`
  } catch {
    url = null
  }
  if (url) cache.set(key, url)
  return url
}
