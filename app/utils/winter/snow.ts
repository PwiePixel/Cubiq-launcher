import { hash, wave } from './math'

// Schnee der Szene „Polarlicht“: quadratische Pixel-Flocken in drei Tiefen – fern (1 Texel, blass), mittel
// (1 Texel, hell) und nah (2 × 2). Ohne Zustand: wo eine Flocke steht, folgt allein aus ihrer Nummer, dem Seed und
// dem Takt (80 ms). So fällt im Launcher und im Cubiq Client (`WinterSnow.java`) zum selben Takt derselbe Schnee,
// und ein Standbild braucht keine Vorlaufzeit.

/** Fallen, Wind und Pendeln je Tiefe (Texel je Takt bzw. Texel). */
const FALL = [0.16, 0.3, 0.5]
const WIND = [0.03, 0.05, 0.08]
const SWAY = [0.6, 1.1, 1.7]

export class WinterSnow {
  readonly count: number
  private readonly depth: Uint8Array
  private readonly x0: Float64Array
  private readonly y0: Float64Array
  private readonly fall: Float64Array
  private readonly sway: Float64Array
  private readonly period: Float64Array
  private readonly phase: Float64Array

  constructor(
    readonly w: number,
    readonly h: number,
    readonly seed: number,
    density = 1,
  ) {
    this.count = Math.max(12, Math.min(600, Math.floor(((w * h) / 420) * density)))
    const n = this.count
    this.depth = new Uint8Array(n)
    this.x0 = new Float64Array(n)
    this.y0 = new Float64Array(n)
    this.fall = new Float64Array(n)
    this.sway = new Float64Array(n)
    this.period = new Float64Array(n)
    this.phase = new Float64Array(n)
    for (let i = 0; i < n; i++) {
      const r = hash(i, 1, seed)
      const d = r < 0.5 ? 0 : r < 0.84 ? 1 : 2
      this.depth[i] = d
      this.x0[i] = hash(i, 2, seed) * (w + 8)
      this.y0[i] = hash(i, 3, seed) * (h + 8)
      this.fall[i] = FALL[d]! * (0.85 + 0.3 * hash(i, 4, seed))
      this.sway[i] = SWAY[d]! * (0.5 + hash(i, 5, seed))
      this.period[i] = 70 + 60 * hash(i, 6, seed)
      this.phase[i] = hash(i, 7, seed)
    }
  }

  /** Tiefe der Flocke `i`: 0 = fern, 1 = mittel, 2 = nah. */
  depthOf(i: number): number {
    return this.depth[i]!
  }

  /** Linke obere Ecke der Flocke `i` zum Takt `tick` (ganze Texel; die Flocken laufen am Rand um). */
  x(i: number, tick: number): number {
    const d = this.depth[i]!
    const span = this.w + 8
    const v = this.x0[i]! + WIND[d]! * tick + this.sway[i]! * wave(this.phase[i]! + tick / this.period[i]!)
    return Math.floor(v - Math.floor(v / span) * span) - 4
  }

  y(i: number, tick: number): number {
    const span = this.h + 8
    const v = this.y0[i]! + this.fall[i]! * tick
    return Math.floor(v - Math.floor(v / span) * span) - 4
  }
}
