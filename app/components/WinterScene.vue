<script setup lang="ts">
import { copyPixels, WINTER_MAX_COLS, WINTER_MAX_ROWS, winterTexel } from '~/utils/winter/canvas'
import { auroraLevel, buildWinterLayout, islandHalf, WINTER_SEED } from '~/utils/winter/layout'
import { WinterPainter } from '~/utils/winter/painter'
import { buildWinterPalette, documentVariant } from '~/utils/winter/palette'

// Hintergrund im Design „Polarlicht“: Nachthimmel mit Polarlicht, schwebende Inseln aus verschneiten Würfeln und
// ein Wolkenmeer in Pixel-Art – gemalt nach denselben Regeln wie im Cubiq Client (utils/winter). Ein Texel sind gut
// 3 Bildschirmpixel (höchstens 640 × 400 Texel); das Canvas hat Texel-Auflösung, CSS skaliert es pixelig. Der Schnee
// fällt als drei weiche CSS-Ebenen darüber (flüssig und auf der Grafikkarte statt im Pixel-Takt).
// Auf der Startseite sitzt der Schein am Spielen-Knopf (`anchor`), die große Insel schwebt rechts neben dem Titel
// (`clear`) – ist dort kein Platz (schmales Fenster, langer Name), bleibt sie weg;
// beim Spielstart wird das Polarlicht mit dem Fortschritt kräftiger, läuft das Spiel, leuchtet es voll. Hinter Seiten
// (`sky`) bleibt nur der ruhige Himmel. Neue Bilder entstehen im Takt von 80 ms (Seiten-Hintergründe jeden zweiten
// Takt) und nur, solange die Szene zu sehen ist; „Animationen reduzieren“ oder `still` zeigen ein Standbild.
const props = withDefaults(
  defineProps<{
    mode?: 'idle' | 'starting' | 'running'
    /** Gesamtfortschritt beim Start, 0–100. */
    progress?: number
    /** Element, an dem der Schein sitzt (der Spielen-Knopf). */
    anchor?: HTMLElement | null
    /** Text, neben dem die große Insel Platz lässt (der Titel) – sie schwebt im freien Raum rechts davon. */
    clear?: HTMLElement | null
    /** Ganze Fläche füllen (Seiten-Hintergrund) statt eines Kopfbereichs. */
    fill?: boolean
    /** Nur Himmel mit Sternen und Polarlicht – ohne Inseln und Wolkenmeer (ruhig hinter Seiten). */
    sky?: boolean
    seed?: number
    /** Standbild (Einstellung „Animierter Hintergrund“ aus). */
    still?: boolean
  }>(),
  { mode: 'idle', progress: 0, anchor: null, clear: null, fill: false, sky: false, seed: WINTER_SEED, still: false },
)

const host = useTemplateRef<HTMLDivElement>('host')
const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
/** Größe des Canvas in CSS-Pixeln (ganze Texel). */
const box = ref({ width: 0, height: 0 })
/** Mitte des Spielen-Knopfs in Prozent der Szene – dort sitzt der Schein. */
const glowAt = ref({ x: 72, y: 58 })
/** Szene außerhalb des Bildes: auch der Schnee hält an. */
const away = ref(false)
const { reduced } = useMotion()
const resting = computed(() => props.still || reduced.value)

/** Ein Takt: 80 ms. */
const TICK_MS = 80
/** Standbild: ein ruhiger Moment mit kräftigem Polarlicht. */
const REST = { tick: 40, seconds: 21 }
/** Große Insel auf der Startseite: rechts neben Titel und Knöpfen (Anteil der Breite). */
const HERO_X = 0.8

/**
 * Wo die große Insel neben dem Titel schweben kann: Mitte des freien Raums rechts vom Text (Anteil der Breite) oder
 * `null`, wenn sie dort nicht hinpasst. Gemessen wird der Text selbst (der Titel ist ein Block über die ganze Breite).
 */
function heroBeside(text: HTMLElement, rect: DOMRect, texel: number, cols: number, rows: number): number | null {
  const range = document.createRange()
  range.selectNodeContents(text)
  const box = text.getBoundingClientRect()
  const right = Math.min(range.getBoundingClientRect().right, box.right)
  // Platz ab dem Textende (mit Abstand); die Insel ist höchstens 7 Würfel = 14 halbe Würfel breit.
  const from = Math.ceil((right - rect.left) / texel) + 4
  const need = 14 * islandHalf(cols, rows) + 4
  return cols - from >= need ? (from + (cols - from) / 2) / cols : null
}

interface Stage {
  key: string
  painter: WinterPainter
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
  // Seiten-Hintergründe nur jeden zweiten Takt neu – der Takt zählt weiter in 80 ms, Sterne funkeln gleich schnell.
  const every = props.fill ? 2 : 1
  const tick = resting.value ? REST.tick : Math.floor(elapsed / (TICK_MS * every)) * every
  if (!force && tick === shownTick) return
  shownTick = tick
  const seconds = resting.value ? REST.seconds : elapsed / 1000
  try {
    // Ohne Pixel-Schnee: der fällt als CSS-Ebenen darüber.
    stage.painter.compose(stage.pixels, { tick, seconds, level: auroraFor(seconds), moving: !resting.value, snow: null })
    copyPixels(stage.pixels, stage.image.data)
    c.getContext('2d')?.putImageData(stage.image, 0, 0)
  } catch (e) {
    // Ein Fehler in der Szene darf die Seite nicht stören – einmal melden, dann still stehen.
    if (!reported) console.error('[WinterScene]', e)
    reported = true
    stage = null
  }
}

/** Szene für die aktuelle Größe und das Farbschema (neu) aufbauen; der Anker setzt nur den Schein. */
function rebuild() {
  const el = host.value
  const c = canvas.value
  if (!el || !c) return
  const rect = el.getBoundingClientRect()
  if (rect.width < 1 || rect.height < 1) return
  const texel = winterTexel(rect.width, rect.height, window.devicePixelRatio || 1)
  const cols = Math.min(WINTER_MAX_COLS, Math.ceil(rect.width / texel))
  const rows = Math.min(WINTER_MAX_ROWS, Math.ceil(rect.height / texel))
  box.value = { width: cols * texel, height: rows * texel }
  const a = props.anchor?.getBoundingClientRect()
  const anchored = !!a && a.width > 0
  if (a && anchored) {
    glowAt.value = {
      x: Math.min(100, Math.max(0, ((a.left + a.width / 2 - rect.left) / rect.width) * 100)),
      y: Math.min(100, Math.max(0, ((a.top + a.height / 2 - rect.top) / rect.height) * 100)),
    }
  }
  // Große Insel: ohne Anker wie im Spiel; mit Anker rechts neben dem Titel – oder gar nicht, wenn dort kein Platz ist.
  const beside = anchored && props.clear ? heroBeside(props.clear, rect, texel, cols, rows) : undefined
  const heroX = beside ?? (anchored ? HERO_X : undefined)
  const hero = beside !== null
  const variant = documentVariant()
  const key = [cols, rows, heroX === undefined ? '' : heroX.toFixed(3), hero, props.seed, variant, props.fill, props.sky].join(':')
  if (stage?.key !== key) {
    const layout = buildWinterLayout({ cols, rows, seed: props.seed, heroX, hero, sky: props.sky })
    c.width = layout.w
    c.height = layout.h
    stage = {
      key,
      painter: new WinterPainter(layout, buildWinterPalette(variant)),
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
  () => [props.anchor, props.seed, props.fill, props.sky] as const,
  ([anchor], [before]) => {
    if (before && before !== anchor) resizeObserver?.unobserve(before)
    if (anchor) resizeObserver?.observe(anchor)
    soon()
  },
)
// Neuer Titel (andere Instanz auf der Bühne) → Platz neben dem Text neu messen.
let textObserver: MutationObserver | undefined
watch(
  () => props.clear,
  (text) => {
    textObserver?.disconnect()
    if (text && typeof MutationObserver !== 'undefined') {
      textObserver = new MutationObserver(soon)
      textObserver.observe(text, { childList: true, characterData: true, subtree: true })
    }
    soon()
  },
  { immediate: true },
)

onMounted(() => {
  startedAt = performance.now()
  resizeObserver = new ResizeObserver(soon)
  if (host.value) resizeObserver.observe(host.value)
  if (props.anchor) resizeObserver.observe(props.anchor)
  viewObserver = new IntersectionObserver((entries) => {
    onScreen = entries[entries.length - 1]?.isIntersecting ?? true
    away.value = !onScreen
    wake()
  })
  if (host.value) viewObserver.observe(host.value)
  // Anderes Farbschema → andere Palette.
  themeObserver = new MutationObserver(soon)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  document.addEventListener('visibilitychange', onVisibility)
  canvas.value?.addEventListener('contextrestored', onVisibility)
  // Die Schrift des Titels lädt evtl. später – danach den Platz neben dem Text neu messen.
  void document.fonts?.ready.then(() => soon())
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
  textObserver?.disconnect()
  document.removeEventListener('visibilitychange', onVisibility)
  canvas.value?.removeEventListener('contextrestored', onVisibility)
})
</script>

<template>
  <div
    ref="host"
    class="winter-scene"
    :class="[`is-${mode}`, { 'is-fill': fill, 'is-still': resting, 'is-away': away }]"
    :style="{ '--glow-x': `${glowAt.x}%`, '--glow-y': `${glowAt.y}%`, '--charge': charge }"
    aria-hidden="true"
  >
    <canvas ref="canvas" class="pixels" :style="{ width: `${box.width}px`, height: `${box.height}px` }" />
    <!-- Schnee: drei Ebenen quadratischer Flocken (fern, mittel, nah), jede fällt in einer Schleife um genau eine Kachel. -->
    <div class="snowfall">
      <i class="flakes flakes-far" />
      <i class="flakes flakes-mid" />
      <i class="flakes flakes-near" />
    </div>
    <!-- Schleier der Seite für lesbaren Text – über den Pixeln und dem Schnee, unter dem Schein. -->
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
.snowfall {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
/*
 * Eine Ebene ist eine Kachel höher als die Szene und wandert in einer Schleife genau eine Kachel nach unten – nahtlos
 * und nur über `transform` (Grafikkarte, kein Neuzeichnen). Die Flocken sind eine Maske über der Schneefarbe
 * (`--winter-flake` in winter.css), folgen also dem Farbschema.
 */
.flakes {
  position: absolute;
  inset: calc(-1 * var(--tile)) 0 0 0;
  background-color: var(--winter-flake, #eef6ff);
  -webkit-mask: var(--flake-mask) 0 0 / var(--tile) var(--tile) repeat;
  mask: var(--flake-mask) 0 0 / var(--tile) var(--tile) repeat;
  will-change: transform;
}
/* Fern: kleine, blasse Flocken, langsam. */
.flakes-far {
  --tile: 200px;
  --flake-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200' shape-rendering='crispEdges'%3E%3Cpath d='M12 30h2v2h-2zM70 8h2v2h-2zM130 52h2v2h-2zM181 120h2v2h-2zM44 150h2v2h-2zM98 110h2v2h-2zM160 178h2v2h-2zM24 92h2v2h-2z'/%3E%3C/svg%3E");
  opacity: 0.5;
  animation: winter-snow-far 26s linear infinite;
}
.flakes-mid {
  --tile: 240px;
  --flake-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240' shape-rendering='crispEdges'%3E%3Cpath d='M20 40h3v3h-3zM150 16h3v3h-3zM88 130h3v3h-3zM204 170h3v3h-3zM52 210h3v3h-3z'/%3E%3C/svg%3E");
  opacity: 0.75;
  animation: winter-snow-mid 16s linear infinite;
}
/* Nah: wenige große Flocken, schnell. */
.flakes-near {
  --tile: 320px;
  --flake-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='320' shape-rendering='crispEdges'%3E%3Cpath d='M40 60h4v4h-4zM230 200h4v4h-4zM140 280h4v4h-4z'/%3E%3C/svg%3E");
  opacity: 0.9;
  animation: winter-snow-near 10s linear infinite;
}
@keyframes winter-snow-far {
  to {
    transform: translate3d(0, 200px, 0);
  }
}
@keyframes winter-snow-mid {
  to {
    transform: translate3d(0, 240px, 0);
  }
}
@keyframes winter-snow-near {
  to {
    transform: translate3d(0, 320px, 0);
  }
}
/* Standbild und „Animationen reduzieren“: Flocken stehen; außerhalb des Bildes hält der Schnee nur an. */
.is-still .flakes,
:root[data-reduced-motion] .flakes {
  animation: none;
}
.is-away .flakes {
  animation-play-state: paused;
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
