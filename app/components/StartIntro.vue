<script setup lang="ts">
// Start-Animation „Das Logo rastet zusammen“: Die drei Arme des Cubiq-Logos fliegen nacheinander ein und rasten
// ein (je ein Pling), der Würfel in der Mitte springt mit Blitz, Ring und Schein auf (C6 + Akkord), der Name gleitet
// hoch, ein Lichtstreif läuft über das Logo, dann gibt die Fläche den Launcher frei (zusammen 1,98 s).
// - Erstes Bild = Ladebildschirm (spa-loading-template.html): gleiche Bühne, gleiche Teile an gleicher Stelle.
// - Zeitplan aus utils/startIntroCues.json – dieselbe Datei steuert den Klang (scripts/start-sound.mjs).
// - Die Uhr startet mit dem ersten gemalten Bild (Animation `intro-stage`), nicht schon beim Einbinden.
// - Aussehen fest je Lauf aus <html> (Design/Event) – nie aus Farbschema oder Akzent, die erst später kommen.
// - Klick oder Taste überspringt (Alt/Meta-Kombinationen bleiben beim System); beim Ausblenden gehen Klicks durch.
// - Aus bei ausgeschalteter Einstellung, bei „Animationen: reduziert“, im REC-Overlay und in anderen Fenstern;
//   „Jetzt abspielen“ in den Einstellungen spielt sie erneut.
import { isTauri } from '@tauri-apps/api/core'
import { getCurrentWindow } from '@tauri-apps/api/window'
import cues from '~/utils/startIntroCues.json'
import { sharedMotion } from '~/utils/motion'
import { introPlan, introReplay, readIntroPrefs } from '~/utils/startIntro'
import { prepareStartSound, type StartSound } from '~/utils/startSound'
import { isMobile } from '~/utils/system'

type Look = 'cubiq' | 'winter' | 'halloween'

/** So lange wartet die Animation höchstens auf den Klang – unsichtbar, die Pose gleicht dem Ladebildschirm. */
const SOUND_WAIT_MS = 250
/** Überspringen: Ausblenden wie beim regulären Abgang. */
const LEAVE_MS = Math.round(cues.leave * 1000)
/** Spätester Einsatz des Klangs: danach fehlte schon das erste Pling – dann lieber gar kein Ton. */
const LATEST_SOUND_OFFSET = cues.arms[0] ?? 0

/** Zeitplan als CSS-Variablen (Sekunden). */
const sec = (v: number | undefined) => `${v ?? 0}s`
const timing: Record<string, string> = {
  '--flight': sec(cues.flight),
  '--arm1': sec(cues.arms[0]),
  '--arm2': sec(cues.arms[1]),
  '--arm3': sec(cues.arms[2]),
  '--cube': sec(cues.cube),
  '--name': sec(cues.name),
  '--sweep-from': sec(cues.sweep[0]),
  '--sweep-to': sec(cues.sweep[1]),
  '--sparkle-from': sec(cues.sparkle[0]),
  '--sparkle-to': sec(cues.sparkle[1]),
  '--hint': sec(cues.hint),
  '--hold': sec(cues.hold),
  '--leave': sec(cues.leave),
}

// Würfelmitte in der Logo-Box (Prozent) und Winkel der Lücken zwischen den Armen (Grad, y nach unten).
const CX = 45.31
const CY = 43.52
const GAPS = { bottom: 89.3, left: 209.4, right: 329.6 }
const ARMS = [
  { at: '--arm1', bisector: 269.5, gaps: [GAPS.left, GAPS.right] },
  { at: '--arm2', bisector: 29.5, gaps: [GAPS.right, GAPS.bottom] },
  { at: '--arm3', bisector: 149.4, gaps: [GAPS.bottom, GAPS.left] },
]
/** Drei Pixel-Funken je Arm: zwei schießen aus den Lücken neben ihm, einer von seiner Außenkante. */
const sparks = ARMS.flatMap((arm) =>
  [...arm.gaps.map((deg) => ({ deg, from: 21, dist: 20 })), { deg: arm.bisector, from: 48, dist: 12 }].map(({ deg, from, dist }) => {
    const rad = (deg * Math.PI) / 180
    return {
      left: `${(CX + from * Math.cos(rad)).toFixed(2)}%`,
      top: `${(CY + from * Math.sin(rad)).toFixed(2)}%`,
      '--sx': `${(dist * Math.cos(rad)).toFixed(1)}px`,
      '--sy': `${(dist * Math.sin(rad)).toFixed(1)}px`,
      '--at': `var(${arm.at})`,
    }
  }),
)
/** Funkelnde Pixel um das Logo – so viele wie hohe Funken im Klang (SPARKLES in scripts/start-sound.mjs). */
const twinkles = [
  [2, 20],
  [96, 30],
  [88, 88],
  [4, 76],
  [62, -6],
].map(([x, y], k) => ({ left: `${x}%`, top: `${y}%`, '--k': String(k) }))

const visible = ref(false)
const go = ref(false)
const leaving = ref(false)
const look = ref<Look>('cubiq')
/** Neuer Lauf = neues Element: alle Animationen beginnen von vorn. */
const run = ref(0)
const root = ref<HTMLElement | null>(null)

let sound: Promise<StartSound | null> | null = null
let playing: StartSound | null = null
let fade: Animation | null = null
let fadeTimer: ReturnType<typeof setTimeout> | undefined
/** Aktueller Lauf – ältere, noch wartende Schritte brechen damit ab. */
let token = 0

function currentLook(): Look {
  const data = document.documentElement.dataset
  if (data.event === 'halloween') return 'halloween'
  if (data.design === 'winter') return 'winter'
  return 'cubiq'
}

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))
const frame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

/** Die Hauptanimation (Uhr der ganzen Animation). */
function stageAnimation(): Animation | null {
  const el = root.value
  if (!el || typeof el.getAnimations !== 'function') return null
  return el.getAnimations().find((a) => (a as CSSAnimation).animationName === 'intro-stage') ?? null
}

/** Sekunden seit dem ersten Bild der Animation. */
function elapsed(anim: Animation | null): number {
  const now = Number(document.timeline?.currentTime ?? NaN)
  const start = Number(anim?.startTime ?? NaN)
  if (Number.isFinite(now) && Number.isFinite(start)) return Math.max(0, (now - start) / 1000)
  const current = Number(anim?.currentTime ?? 0)
  return Number.isFinite(current) ? Math.max(0, current / 1000) : 0
}

/** Klang verwerfen, der noch geladen wird (schließt ihn, sobald er da ist). */
function dropPendingSound() {
  const pending = sound
  sound = null
  void pending?.then((s) => s?.close())
}

/** Ladebalken im selben Takt wie der des Ladebildschirms (der läuft schon) – kein Sprung bei der Übergabe. */
function syncBar() {
  try {
    const loader = document.querySelector('.cubiq-splash-bar span')?.getAnimations?.()[0]
    const own = root.value?.querySelector('.si-bar span')?.getAnimations?.()[0]
    if (loader && own && loader.startTime !== null) own.startTime = loader.startTime
  } catch {
    // Ohne Web Animations läuft der Balken eben für sich.
  }
}

async function start(id: number) {
  await nextTick()
  if (id !== token) return
  syncBar()
  // Halten, bis der Klang bereit ist (oder kurz) – die Pose gleicht dem Ladebildschirm.
  if (sound) await Promise.race([sound, wait(SOUND_WAIT_MS)])
  if (id !== token) return
  go.value = true
  await nextTick()
  const anim = stageAnimation()
  if (anim) await anim.ready.catch(() => {})
  else {
    await frame()
    await frame()
  }
  if (id !== token || !sound) return
  const pending = sound
  sound = null
  void pending.then((s) => {
    if (!s) return
    const offset = elapsed(anim) + s.latency
    if (id !== token || leaving.value || offset > LATEST_SOUND_OFFSET) return s.close()
    playing = s
    s.start(offset)
  })
}

function show(withSound: boolean) {
  const id = ++token
  playing?.fadeOut(40)
  playing = null
  dropPendingSound()
  fade?.cancel()
  fade = null
  clearTimeout(fadeTimer)
  if (withSound) sound = prepareStartSound()
  look.value = currentLook()
  go.value = false
  leaving.value = false
  run.value++
  visible.value = true
  // Name schon laden, bevor er erscheint (sonst springt die Schrift).
  void document.fonts?.load('650 26px "Sora Variable"').catch(() => {})
  return id
}

function end() {
  token++
  visible.value = false
  go.value = false
  leaving.value = false
  fade = null
  clearTimeout(fadeTimer)
  dropPendingSound()
  // Ein laufender Klang klingt von selbst aus und schließt sich am Ende (`playing` bleibt für „Jetzt abspielen“).
}

function skip() {
  if (!visible.value || leaving.value) return
  token++
  leaving.value = true
  dropPendingSound()
  // Bleibt in `playing`: „Jetzt abspielen“ kürzt das Ausblenden dann auf 40 ms.
  playing?.fadeOut(LEAVE_MS)
  const el = root.value
  const from = el ? Number(getComputedStyle(el).opacity) : 1
  fade = el?.animate?.([{ opacity: Number.isFinite(from) ? from : 1 }, { opacity: 0 }], { duration: LEAVE_MS, easing: 'ease', fill: 'forwards' }) ?? null
  const done = () => {
    if (leaving.value) end()
  }
  if (fade) fade.finished.then(done, () => {})
  else fadeTimer = setTimeout(done, LEAVE_MS)
}

function onAnimationStart(e: AnimationEvent) {
  // Übergabe beginnt (Logo und Name gehen): Klicks gehen ab jetzt an den Launcher.
  if (e.animationName === 'intro-reveal') leaving.value = true
}

function onAnimationEnd(e: AnimationEvent) {
  if (e.target === root.value && e.animationName === 'intro-stage') end()
}

/**
 * Tasten überspringen und erreichen den Launcher darunter nicht, solange die Animation zu sehen ist – auch nicht
 * beim Ausblenden (sonst überspränge bei Strg+K die Strg-Taste und das K landete in der Suche). Alt/Meta-Kombinationen
 * (Alt+F4, Alt+Tab …) bleiben beim System.
 */
function onKey(e: KeyboardEvent) {
  if (!visible.value || e.altKey || e.metaKey) return
  e.preventDefault()
  e.stopImmediatePropagation()
  skip()
}

if (typeof window !== 'undefined') {
  const prefs = readIntroPrefs()
  const boot = introPlan({
    prefs,
    systemReduced: sharedMotion().systemReduced.value,
    pathname: location.pathname,
    windowLabel: isTauri() ? getCurrentWindow().label : null,
    hidden: document.visibilityState === 'hidden',
    mobile: isMobile,
  })
  // Gleich beim ersten Rendern zeigen – der Ladebildschirm darunter verschwindet erst danach.
  if (boot.play) show(boot.sound)
  window.addEventListener('keydown', onKey, { capture: true })
}

onMounted(() => {
  if (visible.value) void start(token)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey, { capture: true })
  token++
  clearTimeout(fadeTimer)
  dropPendingSound()
  playing?.close()
  playing = null
})

// „Jetzt abspielen“: alter Klang ist binnen 40 ms weg, dann von vorn (ein Klick – Ton geht auch im Browser).
watch(introReplay, () => {
  const prefs = readIntroPrefs()
  void start(show(prefs.sound && !isMobile))
})
</script>

<template>
  <div
    v-if="visible"
    :key="run"
    ref="root"
    class="start-intro"
    :class="{ go, leaving }"
    :data-look="look"
    :style="timing"
    role="img"
    :aria-label="t('startIntro.label')"
    data-testid="start-intro"
    @click="skip"
    @animationstart="onAnimationStart"
    @animationend="onAnimationEnd"
  >
    <div class="si-glow" aria-hidden="true" />
    <div class="si-center" aria-hidden="true">
      <div class="si-mark">
        <span class="si-part" data-part="arm-top" />
        <span class="si-part" data-part="arm-right" />
        <span class="si-part" data-part="arm-left" />
        <span class="si-part" data-part="cube" />
        <span class="si-sweep"><i /></span>
        <svg class="si-ring" viewBox="-50 -50 100 100">
          <polygon points="0,-46 39.8,-23 39.8,23 0,46 -39.8,23 -39.8,-23" />
        </svg>
        <i v-for="(s, i) in sparks" :key="`s${i}`" class="si-spark" :style="s" />
        <i v-for="(s, i) in twinkles" :key="`t${i}`" class="si-twinkle" :style="s" />
      </div>
      <p class="si-name">Cubiq <span>Launcher</span></p>
    </div>
    <div class="si-bar" aria-hidden="true"><span /></div>
    <span class="si-skip" aria-hidden="true">{{ t('startIntro.skip') }}</span>
  </div>
</template>

<style scoped>
/* Bühne je Aussehen – dieselben Werte wie im Ladebildschirm (spa-loading-template.html, Test start-intro). */
.start-intro {
  --stage: radial-gradient(circle at 50% calc(50% - 30px), rgba(113, 163, 48, 0.16) 0%, rgba(113, 163, 48, 0) 38%), #0c100e;
  --ink: #f2f5f3;
  --ink-accent: #a9d46b;
  --track: #1d2520;
  --bar: linear-gradient(90deg, transparent, #71a330, #a9d46b, transparent);
  --glow: rgba(113, 163, 48, 0.3);
  --ring: #a9d46b;
  --spark: #e4f5c8;
  position: fixed;
  inset: 0;
  z-index: 200;
  overflow: hidden;
  background: var(--stage);
  cursor: pointer;
  user-select: none;
}
.start-intro[data-look='winter'] {
  --stage: radial-gradient(ellipse 70% 22% at 50% 14%, rgba(77, 255, 160, 0.1) 0%, rgba(38, 227, 198, 0.06) 55%, rgba(38, 227, 198, 0) 100%),
    radial-gradient(circle at 50% calc(50% - 30px), rgba(127, 211, 255, 0.12) 0%, rgba(127, 211, 255, 0) 38%), #070c18;
  --ink: #eef5fc;
  --ink-accent: #9fe7ff;
  --track: #142138;
  --bar: linear-gradient(90deg, transparent, #2b97d6, #e8fbff, transparent);
  --glow: rgba(127, 211, 255, 0.28);
  --ring: #9fe7ff;
  --spark: #e8fbff;
}
.start-intro[data-look='halloween'] {
  --stage: radial-gradient(circle at 50% calc(50% - 30px), rgba(142, 108, 201, 0.2) 0%, rgba(142, 108, 201, 0) 38%), #07060c;
  --ink: #ece9f5;
  --ink-accent: #b9a2ea;
  --track: #1c1828;
  --bar: linear-gradient(90deg, transparent, #8e6cc9, #cfd5e3, transparent);
  --glow: rgba(142, 108, 201, 0.3);
  --ring: #b9a2ea;
  --spark: #e3d8ff;
}
.start-intro.leaving {
  pointer-events: none;
}

/* Schein, der beim Würfel aufblüht. */
.si-glow {
  position: absolute;
  left: 50%;
  top: calc(50% - 30px);
  width: 46rem;
  height: 46rem;
  margin: -23rem 0 0 -23rem;
  border-radius: 50%;
  background: radial-gradient(closest-side, var(--glow), transparent 72%);
  opacity: 0;
}
.si-center {
  position: absolute;
  inset: 0;
  transform-origin: 50% calc(50% - 30px);
}

/* Logo: 128 px, Mitte 30 px über der Fenstermitte – wie im Ladebildschirm. */
.si-mark {
  position: absolute;
  left: 50%;
  top: calc(50% - 30px);
  width: 128px;
  height: 128px;
  margin: -64px 0 0 -64px;
}
/* Vier Teile aus derselben Grafik, je mit eigenem Umriss: wartende Pose = Ladebildschirm. */
.si-part {
  position: absolute;
  inset: 0;
  background: url('/logo.png') 0 0 / 100% 100% no-repeat;
  transform-origin: 45.31% 43.52%;
  transform: translate(var(--dx), var(--dy)) scale(0.92);
  opacity: 0.5;
}
.si-part[data-part='arm-top'] {
  --dx: -0.12%;
  --dy: -13.67%;
  clip-path: polygon(-10.84% 11.88%, 3.40% -5.45%, 22.71% -16.84%, 44.75% -20.94%, 66.86% -17.23%, 86.37% -6.17%, 100.90% 10.90%, 61.40% 34.08%, 57.19% 29.14%, 51.55% 25.94%, 45.15% 24.86%, 38.77% 26.05%, 33.18% 29.35%, 29.06% 34.36%);
}
.si-part[data-part='arm-right'] {
  --dx: 11.90%;
  --dy: 6.73%;
  clip-path: polygon(100.90% 10.90%, 108.70% 31.83%, 108.88% 54.15%, 101.44% 75.20%, 87.26% 92.45%, 68.04% 103.83%, 46.10% 107.96%, 45.54% 62.17%, 51.89% 60.97%, 57.45% 57.68%, 61.55% 52.69%, 63.71% 46.59%, 63.66% 40.13%, 61.40% 34.08%);
}
.si-part[data-part='arm-left'] {
  --dx: -11.77%;
  --dy: 6.96%;
  clip-path: polygon(46.10% 107.96%, 23.99% 104.34%, 4.46% 93.37%, -10.14% 76.37%, -18.03% 55.41%, -18.28% 33.01%, -10.84% 11.88%, 29.06% 34.36%, 26.91% 40.47%, 26.98% 46.96%, 29.27% 53.02%, 33.49% 57.94%, 39.14% 61.12%, 45.54% 62.17%);
}
.si-part[data-part='cube'] {
  clip-path: polygon(63.96% 43.52%, 63.33% 48.34%, 61.47% 52.84%, 58.50% 56.70%, 54.64% 59.67%, 50.14% 61.53%, 45.31% 62.17%, 40.48% 61.53%, 35.99% 59.67%, 32.12% 56.70%, 29.16% 52.84%, 27.30% 48.34%, 26.66% 43.52%, 27.30% 38.69%, 29.16% 34.19%, 32.12% 30.33%, 35.99% 27.36%, 40.48% 25.50%, 45.31% 24.86%, 50.14% 25.50%, 54.64% 27.36%, 58.50% 30.33%, 61.47% 34.19%, 63.33% 38.69%);
  transform: scale(0.8);
  opacity: 0.55;
}
/* Weißer Blitz über dem Würfel (Umriss des Würfels, Maske aus dem Logo). */
.si-part[data-part='cube']::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #fff;
  -webkit-mask: url('/logo.png') 0 0 / 100% 100% no-repeat;
  mask: url('/logo.png') 0 0 / 100% 100% no-repeat;
  opacity: 0;
}
/* Polarlicht: Schnee auf den oberen Kanten (2 px), wie im Ladebildschirm. */
.start-intro[data-look='winter'] .si-part {
  filter: drop-shadow(0 -2px 0 rgba(244, 250, 255, 0.95));
}

/* Lichtstreif über das Logo, nur wo das Logo ist. */
.si-sweep {
  position: absolute;
  inset: 0;
  overflow: hidden;
  -webkit-mask: url('/logo.png') 0 0 / 100% 100% no-repeat;
  mask: url('/logo.png') 0 0 / 100% 100% no-repeat;
}
.si-sweep i {
  position: absolute;
  top: -10%;
  bottom: -10%;
  left: 0;
  width: 45%;
  background: linear-gradient(100deg, transparent 15%, rgba(255, 255, 255, 0.55) 50%, transparent 85%);
  transform: translateX(-120%);
  opacity: 0;
}
/* Sechseck-Ring, der vom Würfel ausgeht. */
.si-ring {
  position: absolute;
  left: 45.31%;
  top: 43.52%;
  width: 100px;
  height: 100px;
  margin: -50px 0 0 -50px;
  overflow: visible;
  fill: none;
  stroke: var(--ring);
  stroke-width: 3;
  stroke-linejoin: round;
  opacity: 0;
}
.si-spark {
  position: absolute;
  width: 4px;
  height: 4px;
  margin: -2px 0 0 -2px;
  background: var(--spark);
  opacity: 0;
}
.si-twinkle {
  position: absolute;
  width: 9px;
  height: 9px;
  margin: -4.5px 0 0 -4.5px;
  background: var(--spark);
  clip-path: polygon(33.3% 0, 66.7% 0, 66.7% 33.3%, 100% 33.3%, 100% 66.7%, 66.7% 66.7%, 66.7% 100%, 33.3% 100%, 33.3% 66.7%, 0 66.7%, 0 33.3%, 33.3% 33.3%);
  opacity: 0;
}
/* Schrift fest (Sora wie die Marke) – nicht `.display`: Die folgt <html data-event> und könnte mitten im Lauf wechseln. */
.si-name {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(50% + 48px);
  margin: 0;
  font-family: 'Sora Variable', 'Inter Variable', 'Segoe UI Variable Display', 'Segoe UI', system-ui, sans-serif;
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1;
  text-align: center;
  color: var(--ink);
  opacity: 0;
}
.si-name span {
  color: var(--ink-accent);
}
/* Ladebalken wie im Ladebildschirm; blendet zu Beginn aus. */
.si-bar {
  position: absolute;
  left: 50%;
  top: calc(50% + 62px);
  width: 140px;
  height: 3px;
  margin-left: -70px;
  border-radius: 999px;
  background: var(--track);
  overflow: hidden;
}
.si-bar span {
  position: absolute;
  inset: 0;
  width: 40%;
  border-radius: 999px;
  background: var(--bar);
  animation: intro-load 1.2s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}
.si-skip {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 2rem;
  font-size: 0.75rem;
  text-align: center;
  color: var(--ink);
  opacity: 0;
}

/* --- Ablauf (ab `.go`, Zeitpunkte aus startIntroCues.json) ------------------------------------------------- */
/* Zeiten mit var()/calc() stehen als Einzelangaben: Der CSS-Minifier sortiert die Kurzschreibweise sonst um. */
/* Uhr und Abgang in einem: `intro-stage` blendet die Bühne aus (Deckkraft – läuft wie alle Teile im Compositor und
   startet mit ihnen im selben Bild; eine Uhr im Hauptthread liefe bei langsamem Aufbau davon). */
.go {
  animation: intro-stage linear both;
  animation-duration: calc(var(--hold) + var(--leave));
}
.go .si-bar {
  animation: intro-fade-out 0.2s ease-out both;
}
.go .si-part {
  animation: intro-arm linear both;
  animation-duration: calc(var(--flight) * 2);
}
.go .si-part[data-part='arm-top'] {
  animation-delay: calc(var(--arm1) - var(--flight));
}
.go .si-part[data-part='arm-right'] {
  animation-delay: calc(var(--arm2) - var(--flight));
}
.go .si-part[data-part='arm-left'] {
  animation-delay: calc(var(--arm3) - var(--flight));
}
.go .si-part[data-part='cube'] {
  animation: intro-cube 0.2s linear both;
  animation-delay: calc(var(--cube) - 0.08s);
}
.go .si-part[data-part='cube']::after {
  animation: intro-flash 0.3s linear both;
  animation-delay: calc(var(--cube) - 0.02s);
}
/* Ring, Funken und Funkeln: `forwards` – vor ihrem Einsatz gilt der unsichtbare Grundzustand, nicht das erste Bild. */
.go .si-ring {
  animation: intro-ring 0.46s cubic-bezier(0.2, 0.7, 0.3, 1) forwards;
  animation-delay: var(--cube);
}
.go .si-glow {
  animation: intro-glow 0.45s ease-out both;
  animation-delay: calc(var(--cube) - 0.04s);
}
.go .si-spark {
  animation: intro-spark 0.32s cubic-bezier(0.2, 0.8, 0.3, 1) forwards;
  animation-delay: var(--at);
}
.go .si-name {
  animation: intro-name 0.34s ease-out both;
  animation-delay: var(--name);
}
.go .si-sweep i {
  animation: intro-sweep cubic-bezier(0.4, 0, 0.2, 1) both;
  animation-duration: calc(var(--sweep-to) - var(--sweep-from));
  animation-delay: var(--sweep-from);
}
.go .si-twinkle {
  animation: intro-twinkle 0.3s ease-out forwards;
  animation-delay: calc(var(--sparkle-from) + (var(--sparkle-to) - var(--sparkle-from)) * var(--k) / 5);
}
.go .si-skip {
  animation: intro-hint 0.4s ease-out both;
  animation-delay: var(--hint);
}
/* Übergabe: Logo und Name werden etwas größer und sind nach 60 % der Zeit weg, die Bühne blendet ganz aus. */
.go .si-center {
  animation: intro-reveal ease-in both;
  animation-duration: calc(var(--leave) * 0.6);
  animation-delay: var(--hold);
}
/* „Animationen: reduziert“ (nur über „Jetzt abspielen“): Endbild zeigen, die Uhr läuft trotzdem. */
:root[data-reduced-motion] .start-intro.go {
  animation-duration: calc(var(--hold) + var(--leave)) !important;
}
</style>

<style>
/* Ohne Scope: Die Namen bleiben `intro-*` (Aufnahme-Werkzeuge und Ereignisse lesen sie). */
/* Ganze Animation: Bühne steht bis `hold`, blendet dann in `leave` aus – 80,81 % = 1,60 s / 1,98 s (Test start-intro). */
@keyframes intro-stage {
  0%,
  80.81% {
    opacity: 1;
    animation-timing-function: ease-in-out;
  }
  100% {
    opacity: 0;
  }
}
@keyframes intro-load {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(250%);
  }
}
@keyframes intro-fade-out {
  to {
    opacity: 0;
  }
}
/* Arm: beschleunigt einrasten (Mitte = Cue), kurz nachfedern, ruhen. */
@keyframes intro-arm {
  0% {
    transform: translate(var(--dx), var(--dy)) scale(0.92);
    opacity: 0.5;
    animation-timing-function: cubic-bezier(0.55, 0, 0.9, 0.45);
  }
  50% {
    transform: none;
    opacity: 1;
    animation-timing-function: cubic-bezier(0.2, 0.7, 0.4, 1);
  }
  68% {
    transform: translate(calc(var(--dx) * -0.14), calc(var(--dy) * -0.14)) scale(1.015);
    animation-timing-function: ease-in-out;
  }
  100% {
    transform: none;
    opacity: 1;
  }
}
@keyframes intro-cube {
  0% {
    transform: scale(0.8);
    opacity: 0.55;
    animation-timing-function: cubic-bezier(0.5, 0, 0.75, 0.5);
  }
  40% {
    transform: scale(1.08);
    opacity: 1;
    animation-timing-function: cubic-bezier(0.25, 0.6, 0.35, 1);
  }
  100% {
    transform: none;
    opacity: 1;
  }
}
@keyframes intro-flash {
  0% {
    opacity: 0;
  }
  8% {
    opacity: 0.85;
    animation-timing-function: ease-out;
  }
  100% {
    opacity: 0;
  }
}
@keyframes intro-ring {
  from {
    transform: scale(0.6);
    opacity: 0.9;
  }
  to {
    transform: scale(1.7);
    opacity: 0;
  }
}
@keyframes intro-glow {
  from {
    transform: scale(0.6);
    opacity: 0;
  }
  to {
    transform: none;
    opacity: 1;
  }
}
@keyframes intro-spark {
  from {
    transform: none;
    opacity: 1;
  }
  to {
    transform: translate(var(--sx), var(--sy));
    opacity: 0;
  }
}
@keyframes intro-name {
  from {
    transform: translateY(10px);
    opacity: 0;
  }
  to {
    transform: none;
    opacity: 1;
  }
}
@keyframes intro-sweep {
  0% {
    transform: translateX(-120%);
    opacity: 0;
  }
  15%,
  85% {
    opacity: 1;
  }
  100% {
    transform: translateX(240%);
    opacity: 0;
  }
}
@keyframes intro-twinkle {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  20% {
    transform: none;
    opacity: 1;
  }
  100% {
    transform: scale(0.4);
    opacity: 0;
  }
}
@keyframes intro-hint {
  from {
    opacity: 0;
  }
  to {
    opacity: 0.45;
  }
}
@keyframes intro-reveal {
  from {
    transform: none;
    opacity: 1;
  }
  to {
    transform: scale(1.06);
    opacity: 0;
  }
}
</style>
