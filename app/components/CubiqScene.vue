<script setup lang="ts">
import { bannerSeed, cubePath, rng } from '~/utils/banner'

// Hintergrund im Cubiq-Stil: ruhiger Verlauf, ein feines isometrisches Würfel-Raster (wie die Kanten im
// Logo) und ein paar Würfel-Umrisse, die langsam schweben. Ein weicher Schein in der Akzentfarbe sitzt
// hinter dem Spielen-Knopf (`anchor`): beim Start wächst er mit dem Fortschritt, läuft das Spiel,
// leuchtet er hell. Nur CSS – kein Canvas, nichts, was im Hintergrund Rechenzeit braucht.
const props = withDefaults(
  defineProps<{
    mode?: 'idle' | 'starting' | 'running'
    /** Gesamtfortschritt beim Start, 0–100. */
    progress?: number
    /** Element, hinter dem der Schein sitzt (der Spielen-Knopf). */
    anchor?: HTMLElement | null
    /** Ganze Fläche füllen (Seiten-Hintergrund) statt eines Kopfbereichs. */
    fill?: boolean
    /** Andere Anordnung der Würfel (z. B. für kleine Leer-Zustände). */
    seed?: number
    /** Standbild (Einstellung „Animierter Hintergrund“ aus). */
    still?: boolean
  }>(),
  { mode: 'idle', progress: 0, anchor: null, fill: false, seed: 0x7e5, still: false },
)

const root = useTemplateRef<HTMLDivElement>('root')
const { reduced } = useMotion()

/** Mitte des Ankers relativ zur Szene (Prozent); ohne Anker rechts der Mitte. */
const focus = ref({ x: 72, y: 58 })

function measure() {
  const el = root.value
  const anchor = props.anchor
  if (!el || !anchor) return
  const box = el.getBoundingClientRect()
  const a = anchor.getBoundingClientRect()
  if (box.width < 1 || box.height < 1) return
  focus.value = {
    x: Math.min(100, Math.max(0, ((a.left + a.width / 2 - box.left) / box.width) * 100)),
    y: Math.min(100, Math.max(0, ((a.top + a.height / 2 - box.top) / box.height) * 100)),
  }
}

let resize: ResizeObserver | undefined
onMounted(() => {
  resize = new ResizeObserver(() => measure())
  if (root.value) resize.observe(root.value)
  if (props.anchor) resize.observe(props.anchor)
  measure()
})
watch(
  () => props.anchor,
  (anchor, before) => {
    if (before) resize?.unobserve(before)
    if (anchor) resize?.observe(anchor)
    measure()
  },
)
onBeforeUnmount(() => resize?.disconnect())

/** Würfel-Umrisse: stabile Anordnung je `seed` (Position in %, Größe in px, Verzögerung der Bewegung). */
const cubes = computed(() => {
  const random = rng(bannerSeed(`scene-${props.seed}`))
  const count = props.fill ? 5 : 4
  return Array.from({ length: count }, (_, i) => {
    const size = (props.fill ? 46 : 38) + random() * (props.fill ? 70 : 56)
    return {
      key: i,
      left: 8 + random() * 84,
      top: 10 + random() * 70,
      size,
      delay: -random() * 18,
      duration: 16 + random() * 10,
      opacity: 0.1 + random() * 0.14,
      path: cubePath(50, 50, 44),
    }
  })
})

const animated = computed(() => !props.still && !reduced.value)
const charge = computed(() => (props.mode === 'starting' ? Math.min(100, Math.max(0, props.progress)) / 100 : props.mode === 'running' ? 1 : 0))
</script>

<template>
  <div
    ref="root"
    class="scene"
    :class="[`scene-${mode}`, { 'scene-fill': fill, 'scene-animated': animated }]"
    :style="{ '--fx': `${focus.x}%`, '--fy': `${focus.y}%`, '--charge': charge }"
    aria-hidden="true"
  >
    <div class="scene-base" />
    <div class="scene-grid" />
    <svg v-for="c in cubes" :key="c.key" class="scene-cube" viewBox="0 0 100 100" :style="{ left: `${c.left}%`, top: `${c.top}%`, width: `${c.size}px`, height: `${c.size}px`, opacity: c.opacity, animationDelay: `${c.delay}s`, animationDuration: `${c.duration}s` }">
      <path :d="c.path" fill="none" stroke="currentColor" stroke-width="5" stroke-linejoin="round" stroke-linecap="round" />
    </svg>
    <!-- Schleier für lesbaren Text: liegt über dem Grund, aber unter dem Schein. -->
    <slot />
    <div class="scene-glow" />
  </div>
</template>

<style scoped>
.scene {
  position: absolute;
  z-index: 0;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  contain: strict;
  color: var(--color-primary-400);
}
.scene-base {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(90% 120% at 100% 0%, color-mix(in srgb, var(--color-primary-500) 12%, transparent), transparent 60%),
    linear-gradient(180deg, var(--color-base-900), var(--color-base-950));
}
/* Isometrisches Raster: zwei Linienscharen unter ±30°, links zum Text hin ausgeblendet. */
.scene-grid {
  position: absolute;
  inset: 0;
  opacity: 0.55;
  background-image:
    linear-gradient(30deg, color-mix(in srgb, var(--color-primary-500) 11%, transparent) 1px, transparent 1px),
    linear-gradient(150deg, color-mix(in srgb, var(--color-primary-500) 11%, transparent) 1px, transparent 1px);
  background-size: 52px 30px;
  -webkit-mask-image: radial-gradient(70% 90% at var(--fx) var(--fy), #000 10%, transparent 75%);
  mask-image: radial-gradient(70% 90% at var(--fx) var(--fy), #000 10%, transparent 75%);
}
.scene-fill .scene-grid {
  -webkit-mask-image: linear-gradient(180deg, #000, transparent 85%);
  mask-image: linear-gradient(180deg, #000, transparent 85%);
  opacity: 0.35;
}
.scene-cube {
  position: absolute;
  translate: -50% -50%;
}
.scene-animated .scene-cube {
  animation-name: cube-drift;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
}
@keyframes cube-drift {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-10px) rotate(4deg);
  }
}
/* Schein hinter dem Spielen-Knopf: wächst beim Start mit dem Fortschritt, hell, solange das Spiel läuft. */
.scene-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    calc(28rem + var(--charge) * 10rem) calc(16rem + var(--charge) * 6rem) at var(--fx) var(--fy),
    color-mix(in srgb, var(--color-primary-500) calc(18% + var(--charge) * 14%), transparent),
    transparent 70%
  );
  mix-blend-mode: screen;
  transition: background 0.4s ease;
}
.scene-fill .scene-glow {
  background: radial-gradient(60rem 30rem at 85% 0%, color-mix(in srgb, var(--color-primary-500) 9%, transparent), transparent 70%);
}
.scene-running .scene-glow {
  background: radial-gradient(40rem 22rem at var(--fx) var(--fy), color-mix(in srgb, var(--color-live-400) 30%, transparent), transparent 70%);
}
.scene-animated.scene-starting .scene-glow,
.scene-animated.scene-running .scene-glow {
  animation: glow-breathe 2.4s ease-in-out infinite;
}
@keyframes glow-breathe {
  50% {
    opacity: 0.72;
  }
}
:root[data-theme="light"] .scene-glow {
  mix-blend-mode: normal;
  opacity: 0.6;
}
</style>
