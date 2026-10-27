import { rgb, type Argb } from './math'

// Farben der Szene „Polarlicht“ je Farbschema: Nacht (Dunkel), tiefe Nacht (OLED) und heller Morgen (Hell).
// Das Polarlicht trägt immer das Cubiq-Grün aus dem Logo und läuft über Türkis in Eis-Cyan aus – unabhängig von
// der Akzentfarbe. Der Cubiq Client hat dieselben Werte (`WinterPalette.java`).

export type WinterVariant = 'dark' | 'oled' | 'light'

export interface WinterPalette {
  variant: WinterVariant
  /** Himmel von oben nach unten (Stufen, dazwischen gerastert). */
  sky: Argb[]
  /** Sterne: schwach, mittel, hell (am Morgen keine). */
  stars: [Argb, Argb, Argb] | null
  /** Polarlicht: heller Saum, Cubiq-Grün, Türkis, Eis-Cyan. */
  aurora: [Argb, Argb, Argb, Argb]
  /** Höchste Deckkraft des Polarlichts (0…255). */
  auroraAlpha: number
  /** Nachts leuchtet das Polarlicht (hellt auf), am Morgen färbt es den Himmel nur zart. */
  auroraGlows: boolean
  snowLit: Argb
  snowTop: Argb
  snowLeft: Argb
  snowRight: Argb
  rockLeft: Argb
  rockRight: Argb
  rockDeep: Argb
  pineLeft: Argb
  pineRight: Argb
  trunk: Argb
  ice: Argb
  iceTip: Argb
  cloudFarRim: Argb
  cloudFar: Argb
  cloudNearRim: Argb
  cloudNear: Argb
  flake: Argb
  flakeFar: Argb
  /** Dunst, in dem ferne Inseln verblassen. */
  haze: Argb
}

/** Cubiq-Grün (#71a330) mit hellem Saum, dann Türkis und Eis-Cyan. */
export const AURORA_COLORS = ['#d6f5a3', '#71a330', '#2aa894', '#78d6f2'] as const

/** Einzelne Töne (Inseln, Wolken, Schnee) – alles außer Himmel, Sternen und Polarlicht. */
type Tones = Omit<WinterPalette, 'variant' | 'sky' | 'stars' | 'aurora' | 'auroraAlpha' | 'auroraGlows'>

function palette(variant: WinterVariant, tones: Record<keyof Tones, string>, sky: string[], stars: [string, string, string] | null, auroraAlpha: number): WinterPalette {
  const parsed = Object.fromEntries(Object.entries(tones).map(([key, value]) => [key, rgb(value)])) as Tones
  return {
    variant,
    sky: sky.map(rgb),
    stars: stars ? [rgb(stars[0]), rgb(stars[1]), rgb(stars[2])] : null,
    aurora: [rgb(AURORA_COLORS[0]), rgb(AURORA_COLORS[1]), rgb(AURORA_COLORS[2]), rgb(AURORA_COLORS[3])],
    auroraAlpha,
    auroraGlows: variant !== 'light',
    ...parsed,
  }
}

export function buildWinterPalette(variant: WinterVariant): WinterPalette {
  if (variant === 'light') {
    return palette(
      variant,
      {
        snowLit: '#ffffff',
        snowTop: '#f7fbff',
        snowLeft: '#dce8f4',
        snowRight: '#c0d3e8',
        rockLeft: '#7489a8',
        rockRight: '#5a7092',
        rockDeep: '#465b7e',
        pineLeft: '#3f8a68',
        pineRight: '#2b684f',
        trunk: '#6e5246',
        ice: '#e3f6ff',
        iceTip: '#a3cbe9',
        cloudFarRim: '#ffffff',
        cloudFar: '#e4eef8',
        cloudNearRim: '#ffffff',
        cloudNear: '#f1f6fb',
        flake: '#ffffff',
        flakeFar: '#e1ecf6',
        haze: '#d5e7f5',
      },
      ['#8ebde2', '#a2c9e8', '#b7d6ee', '#cbe2f3', '#deedf8', '#eef6fc'],
      null,
      96,
    )
  }
  if (variant === 'oled') {
    return palette(
      variant,
      {
        snowLit: '#f1f7ff',
        snowTop: '#cbdaee',
        snowLeft: '#9db2cf',
        snowRight: '#7189ab',
        rockLeft: '#1b2a46',
        rockRight: '#111c33',
        rockDeep: '#0a1222',
        pineLeft: '#26604d',
        pineRight: '#173f34',
        trunk: '#2c2124',
        ice: '#9fd6f5',
        iceTip: '#4f7fae',
        cloudFarRim: '#33547f',
        cloudFar: '#0d1b34',
        cloudNearRim: '#5f84b6',
        cloudNear: '#14284a',
        flake: '#eef6ff',
        flakeFar: '#566c90',
        haze: '#0a1426',
      },
      ['#000000', '#010309', '#030813', '#070f20', '#0c1830', '#132240'],
      ['#33425c', '#7d90b0', '#f2f8ff'],
      255,
    )
  }
  return palette(
    variant,
    {
      snowLit: '#ffffff',
      snowTop: '#e4eefa',
      snowLeft: '#b9cce4',
      snowRight: '#8ba2c2',
      rockLeft: '#2b3f62',
      rockRight: '#1c2b48',
      rockDeep: '#111b30',
      pineLeft: '#2f7358',
      pineRight: '#1d4f3f',
      trunk: '#3d2d30',
      ice: '#b4e5ff',
      iceTip: '#6898c9',
      cloudFarRim: '#4a6c9c',
      cloudFar: '#1b2f50',
      cloudNearRim: '#86a9d8',
      cloudNear: '#2a426b',
      flake: '#f4f9ff',
      flakeFar: '#7a90b4',
      haze: '#13223d',
    },
    ['#03060e', '#060c1a', '#0a1428', '#0f1d36', '#152846', '#1c3352'],
    ['#3b4c6b', '#8a9dbd', '#f2f8ff'],
    242,
  )
}

/** Variante nach dem Farbschema auf `<html>` (`data-theme`; „System“ ist dort schon aufgelöst). */
export function documentVariant(): WinterVariant {
  if (typeof document === 'undefined') return 'dark'
  const theme = document.documentElement.dataset.theme
  return theme === 'light' ? 'light' : theme === 'oled' ? 'oled' : 'dark'
}
