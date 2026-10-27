<script setup lang="ts">
import { copyPixels } from '~/utils/winter/canvas'
import { auroraLevel, buildWinterLayout, WINTER_SEED } from '~/utils/winter/layout'
import { WinterPainter } from '~/utils/winter/painter'
import { buildWinterPalette, documentVariant } from '~/utils/winter/palette'
import { WinterSnow } from '~/utils/winter/snow'

// Hintergrund im Design „Polarlicht“: Nachthimmel mit Polarlicht-Bändern in Cubiq-Grün, schwebende Inseln aus
// verschneiten Würfeln, ein Wolkenmeer und Schnee in Pixel-Art – gemalt nach denselben Regeln wie im Cubiq Client
// (utils/winter). Ein Texel sind gut 3 Bildschirmpixel; das Canvas hat Texel-Auflösung, CSS skaliert es pixelig.
// Auf der Startseite schwebt die große Insel über dem Spielen-Knopf (`anchor`); beim Spielstart wird das
// Polarlicht mit dem Fortschritt kräftiger, läuft das Spiel, leuchtet es voll. Neue Bilder entstehen im Takt von
// 80 ms und nur, solange die Szene zu sehen ist; „Animationen reduzieren“ oder `still` zeigen ein Standbild.
const props = withDefaults(
  defineProps<{
    mode?: 'idle' | 'starting' | 'running'
    /** Gesamtfortschritt beim Start, 0–100. */
    progress?: number
    /** Element, über dem die große Insel schwebt (der Spielen-Knopf). */
    anchor?: HTMLElement | null
    /** Ganze Fläche füllen (Seiten-Hintergrund) statt eines Kopfbereichs. */
    fill?: boolean
    seed?: number
    /** Standbild (Einstellung „Animierter Hintergrund“ aus). */
    still?: boolean
  }>(),
  { mode: 'idle', progress: 0, anchor: null, fill: false, seed: WINTER_SEED, still: false },
)

const host = useTemplateRef<HTMLDivElement>('host')
const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
/** Größe des Canvas in CSS-Pixeln (ganze Texel). */
const box = ref({ width: 0, height: 0 })
/** Mitte des Spielen-Knopfs in Prozent der Szene – dort sitzt der Schein. */
const glowAt = ref({ x: 72, y: 58 })
const { reduced } = useMotion()
const resting = computed(() => props.still || reduced.value)

/** Ein Takt: 80 ms. */
const TICK_MS = 80
/** Standbild: ein ruhiger Moment mit kräftigem Polarlicht. */
const REST = { tick: 40, seconds: 21 }

interface Stage {
  key: string
  painter: WinterPainter
  snow: WinterSnow
  pixels: Uint32Array
  image: ImageData
}
let stage: Stage | null = null
let startedAt = performance.now()
let shownTick = -1
let frame = 0
let onScreen = true
let reported = false

const charge = computed(() => (props.mode === 'running' ? 1 : props.mode === 'starting' ? Math.min(100, Math.max(0, props.progress)) / 100 : 0))

/** Polarlicht: atmet ruhig, wird beim Start mit dem Fortschritt kräftiger und leuchtet voll, solange das Spiel läuft. */
function auroraFor(seconds: number) {
  if (props.mode === 'running') return 1
  const calm = resting.value ? 0.8 : auroraLevel(seconds)
  return props.mode === 'starting' ? Math.max(calm, 0.5 + 0.5 * charge.value) : calm
}

function paint(force: boolean) {
  const c = canvas.value
  if (!stage || !c) return
  const elapsed = performance.now() - startedAt
  const tick = resting.value ? REST.tick : Math.floor(elapsed / TICK_MS)
  if (!force && tick === shownTick) return
  shownTick = tick
  const seconds = resting.value ? REST.seconds : elapsed / 1000
  try {
    stage.painter.compose(stage.pixels, { tick, seconds, level: auroraFor(seconds), moving: !resting.value, snow: stage.snow })
    copyPixels(stage.pixels, stage.image.data)
    c.getContext('2d')?.putImageData(stage.image, 0, 0)
  } catch (e) {
    // Ein Fehler in der Szene darf die Seite nicht stören – einmal melden, dann still stehen.
    if (!reported) console.error('[WinterScene]', e)
    reported = true
    stage = null
  }
}

/** Szene für die aktuelle Größe, den Anker und das Farbschema (neu) aufbauen. */
function rebuild() {
  const el = host.value
  const c = canvas.value
  if (!el || !c) return
  const rect = el.getBoundingClientRect()
  if (rect.width < 1 || rect.height < 1) return
  const dpr = window.devicePixelRatio || 1
  // Ganze Gerätepixel je Texel, sonst würden die Pixel unterschiedlich breit.
  const texel = Math.max(2, Math.round(3 * dpr)) / dpr
  const cols = Math.ceil(rect.width / texel)
  const rows = Math.ceil(rect.height / texel)
  box.value = { width: cols * texel, height: rows * texel }
  let anchor: { left: number; top: number; right: number } | null = null
  const a = props.anchor?.getBoundingClientRect()
  if (a && a.width > 0) {
    anchor = { left: Math.floor((a.left - rect.left) / texel), top: Math.floor((a.top - rect.top) / texel), right: Math.floor((a.right - rect.left) / texel) }
    glowAt.value = {
      x: Math.min(100, Math.max(0, ((a.left + a.width / 2 - rect.left) / rect.width) * 100)),
      y: Math.min(100, Math.max(0, ((a.top + a.height / 2 - rect.top) / rect.height) * 100)),
    }
  }
  const variant = documentVariant()
  const key = [cols, rows, anchor?.left, anchor?.top, anchor?.right, props.seed, variant, props.fill].join(':')
  if (stage?.key !== key) {
    const layout = buildWinterLayout({ cols, rows, seed: props.seed, anchor })
    c.width = layout.w
    c.height = layout.h
    stage = {
      key,
      painter: new WinterPainter(layout, buildWinterPalette(variant)),
      snow: new WinterSnow(layout.w, layout.h, props.seed ^ 0x5e0, props.fill ? 1 : 0.8),
      pixels: new Uint32Array(layout.w * layout.h),
      image: new ImageData(layout.w, layout.h),
    }
    reported = false
  }
  paint(true)
  wake()
}

// --- Takt ------------------------------------------------------------------------------------------------------

function animating() {
  return !resting.value && onScreen && !document.hidden && stage !== null
}

function loop() {
  frame = 0
  if (!animating()) return
  paint(false)
  frame = requestAnimationFrame(loop)
}

function wake() {
  if (frame === 0 && animating()) frame = requestAnimationFrame(loop)
}

let resizeObserver: ResizeObserver | undefined
let viewObserver: IntersectionObserver | undefined
let themeObserver: MutationObserver | undefined
let pending: ReturnType<typeof setTimeout> | undefined

function soon() {
  clearTimeout(pending)
  pending = setTimeout(rebuild, stage ? 120 : 0)
}

function onVisibility() {
  if (!document.hidden) paint(true)
  wake()
}

watch(resting, () => {
  paint(true)
  wake()
})
watch(
  () => [props.mode, props.progress] as const,
  () => {
    if (resting.value) paint(true)
  },
)
watch(
  () => [props.anchor, props.seed, props.fill] as const,
  ([anchor], [before]) => {
    if (before && before !== anchor) resizeObserver?.unobserve(before)
    if (anchor) resizeObserver?.observe(anchor)
    soon()
  },
)

onMounted(() => {
  startedAt = performance.now()
  resizeObserver = new ResizeObserver(soon)
  if (host.value) resizeObserver.observe(host.value)
  if (props.anchor) resizeObserver.observe(props.anchor)
  viewObserver = new IntersectionObserver((entries) => {
    onScreen = entries[entries.length - 1]?.isIntersecting ?? true
    wake()
  })
  if (host.value) viewObserver.observe(host.value)
  // Anderes Farbschema → andere Palette.
  themeObserver = new MutationObserver(soon)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  document.addEventListener('visibilitychange', onVisibility)
  canvas.value?.addEventListener('contextrestored', onVisibility)
  rebuild()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  frame = 0
  stage = null
  clearTimeout(pending)
  resizeObserver?.disconnect()
  viewObserver?.disconnect()
  themeObserver?.disconnect()
  document.removeEventListener('visibilitychange', onVisibility)
  canvas.value?.removeEventListener('contextrestored', onVisibility)
})
</script>

<template>
  <div
    ref="host"
    class="winter-scene"
    :class="[`is-${mode}`, { 'is-fill': fill }]"
    :style="{ '--glow-x': `${glowAt.x}%`, '--glow-y': `${glowAt.y}%`, '--charge': charge }"
    aria-hidden="true"
  >
    <canvas ref="canvas" class="pixels" :style="{ width: `${box.width}px`, height: `${box.height}px` }" />
    <!-- Schleier der Seite für lesbaren Text – über den Pixeln, unter dem Schein. -->
    <slot />
    <div v-if="anchor" class="glow" />
  </div>
</template>

<style scoped>
.winter-scene {
  position: absolute;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  contain: strict;
  background: var(--color-base-950);
}
.pixels {
  position: absolute;
  top: 0;
  left: 0;
  max-width: none;
  image-rendering: pixelated;
}
/* Eisiger Schein hinter dem Spielen-Knopf: wächst beim Start und leuchtet, solange das Spiel läuft. */
.glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    calc(20rem + var(--charge) * 10rem) calc(11rem + var(--charge) * 5rem) at var(--glow-x) var(--glow-y),
    color-mix(in srgb, var(--color-primary-400) calc(14% + var(--charge) * 16%), transparent),
    transparent 70%
  );
  mix-blend-mode: screen;
  transition: background 0.4s ease;
}
.is-running .glow {
  background: radial-gradient(32rem 17rem at var(--glow-x) var(--glow-y), color-mix(in srgb, var(--color-live-400) 26%, transparent), transparent 70%);
}
:root[data-theme="light"] .glow {
  mix-blend-mode: normal;
  opacity: 0.5;
}
:root[data-reduced-motion] .glow {
  transition: none;
}
</style>
