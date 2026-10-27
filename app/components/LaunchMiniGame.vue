<script lang="ts">
import type { HopState } from '~/utils/miniGame'

/**
 * Runde je Instanz, über Seitenwechsel hinweg (Startseite → Instanzseite behält Punkte und Zustand). Endet, wenn die
 * Instanz wieder ruht (Start abgebrochen, Spiel beendet).
 */
interface LaunchSession {
  hop: HopState
  /** Vorführung (Cubi springt allein), bis der Spieler selbst spielt. */
  demo: HopState
  closed: boolean
  keep: boolean
  /** Seit wann das Spielfenster da ist (ms), sonst `null`. */
  windowAt: number | null
  /** Bis zu welcher Log-Zeile schon gesucht wurde. */
  logSeen: number
  runningSince: number | null
}
const sessions = new Map<string, LaunchSession>()
</script>

<script setup lang="ts">
import {
  CUBI_X,
  HOP_GROUND,
  HOP_H,
  HOP_W,
  createHop,
  gameWindowShown,
  hopAutopilot,
  hopInput,
  pauseHop,
  readHopBest,
  stepHop,
  writeHopBest,
} from '~/utils/miniGame'
import { HOP_OUTLINE, HOP_OUTLINED, HOP_SPRITES, rasterizeHop, type HopSpriteName } from '~/utils/miniGameArt'

// „Cubi Hop“ – kleines Spiel, während Minecraft startet: erscheint, sobald eine Instanz vorbereitet wird, und
// verschwindet, wenn das Spielfenster da ist (mit „Weiterspielen“ bleibt es). Ohne Instanz-ID (Startseite) begleitet
// es die Instanz, die gerade startet.
// - Leichtgewichtig: Canvas mit 192 × 64 Bildpunkten, ganzzahlig hochskaliert; gezeichnet wird nur, solange es zu
//   sehen ist und das Launcher-Fenster den Fokus hat – sonst Pause. Die Vorbereitung im Kern bremst das nicht.
// - Fokus: Tasten zählen nur, wenn das Spielfeld fokussiert ist (Klick darauf); nie wird Fokus gesetzt oder das
//   Fenster nach vorn geholt – das Minecraft-Fenster behält ihn.
// - Ohne „Animationen reduzieren“ springt Cubi zur Vorführung allein; mit nur auf ausdrücklichen Klick/Taste.
// - Rekord je Konto im lokalen Speicher (utils/miniGame.ts).
const props = withDefaults(defineProps<{ instanceId?: string | null }>(), { instanceId: null })

const games = useGamesStore()
const accounts = useAccountsStore()
const { reduced } = useMotion()

/** Nach „Minecraft ist da“ so lange, bis das Spiel von selbst schließt (Sekunden). */
const CLOSE_AFTER = 8
/** Ohne Fenster-Zeile im Log gilt das Fenster nach so langer Laufzeit als da. */
const WINDOW_FALLBACK_MS = 30_000

/** Die Instanz, deren Start wir begleiten. */
const target = computed<string | null>(() => {
  if (props.instanceId) return props.instanceId
  const list = Object.entries(games.states)
  return (
    list.find(([id, s]) => s.phase !== 'idle' && sessions.has(id) && !sessions.get(id)!.closed)?.[0] ??
    list.find(([, s]) => s.phase === 'preparing')?.[0] ??
    null
  )
})
const game = computed(() => (target.value ? games.state(target.value) : null))
const phase = computed(() => game.value?.phase ?? 'idle')
const userKey = computed(() => accounts.active?.id ?? 'local')

/** Für Vue sichtbare Spiegel des Zustands (der Zustand selbst ändert sich im Takt, ohne Reaktivität). */
const session = shallowRef<LaunchSession | null>(null)
const view = reactive({ phase: 'ready' as HopState['phase'], score: 0, best: 0, newBest: false, demo: true, closed: false, keep: false, windowReady: false })
const countdown = ref(CLOSE_AFTER)
const announce = ref('')

function sync() {
  const s = session.value
  if (!s) return
  view.phase = s.hop.phase
  view.score = s.hop.score
  view.best = s.hop.best
  view.newBest = s.hop.newBest
  view.closed = s.closed
  view.keep = s.keep
  view.windowReady = s.windowAt !== null
}

function storage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/**
 * Neue oder bestehende Runde für die Instanz. Neu nur beim Vorbereiten – wer die Seite erst öffnet, wenn das Spiel
 * schon läuft, bekommt kein Spiel mehr angeboten.
 */
function ensureSession(id: string, preparing: boolean) {
  let s = sessions.get(id)
  if (!s) {
    const seed = (Date.now() ^ (Math.random() * 0x7fffffff)) >>> 0
    s = {
      hop: createHop(seed, readHopBest(storage(), userKey.value)),
      demo: createHop(seed ^ 0x5eed),
      closed: !preparing,
      keep: false,
      windowAt: null,
      logSeen: games.state(id).logTotal,
      runningSince: null,
    }
    sessions.set(id, s)
  }
  session.value = s
  sync()
}

const visible = computed(() => !!session.value && !view.closed && platformCaps.value.gameLaunch && !mobileUi.value)

// --- Spielfenster da? ------------------------------------------------------------------------------------------------

let fallbackTimer: ReturnType<typeof setTimeout> | undefined

function checkWindow() {
  const s = session.value
  const g = game.value
  if (!s || !g || s.windowAt !== null || phase.value !== 'running') return
  const fresh = g.logTotal - s.logSeen
  if (fresh > 0) {
    const lines = g.logs.slice(-Math.min(fresh, 2000))
    s.logSeen = g.logTotal
    if (gameWindowShown(lines)) s.windowAt = Date.now()
  }
  if (s.windowAt === null && s.runningSince !== null && Date.now() - s.runningSince >= WINDOW_FALLBACK_MS) s.windowAt = Date.now()
  if (s.windowAt === null) {
    clearTimeout(fallbackTimer)
    fallbackTimer = setTimeout(checkWindow, 2000)
  }
  sync()
}
watch(() => game.value?.logTotal, checkWindow)

// Minecraft ist da: ohne „Weiterspielen“ nach kurzer Zeit schließen – nie mitten in einer laufenden Runde.
let countdownTimer: ReturnType<typeof setInterval> | undefined
watch(
  () => [view.windowReady, view.keep, view.closed, view.phase] as const,
  ([ready, keep, closed, hopPhase]) => {
    clearInterval(countdownTimer)
    countdownTimer = undefined
    if (!ready || keep || closed) return
    if (hopPhase === 'running') return
    countdown.value = CLOSE_AFTER
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) close()
    }, 1000)
  },
)

function close() {
  const s = session.value
  if (!s) return
  pauseHop(s.hop)
  s.closed = true
  remember()
  sync()
  stopLoop()
}

function keepPlaying() {
  const s = session.value
  if (!s) return
  s.keep = true
  sync()
}

/** Rekord sichern (nur echte Runden, nie die Vorführung). */
function remember() {
  const s = session.value
  if (s && s.hop.newBest) writeHopBest(storage(), userKey.value, s.hop.best)
}

// --- Zeichnen -----------------------------------------------------------------------------------------------------

const canvas = useTemplateRef<HTMLCanvasElement>('canvas')
const stage = useTemplateRef<HTMLDivElement>('stage')
/** Ganzzahlige Vergrößerung (scharfe Pixel). */
const scale = ref(3)
let ctx: CanvasRenderingContext2D | null = null
let sprites: Record<HopSpriteName, HTMLCanvasElement> | null = null
let sky: HTMLCanvasElement | null = null

/** Sterne: feste Plätze (wie ein kleiner Nachthimmel im Banner). */
const STARS = Array.from({ length: 16 }, (_, i) => {
  const r = (n: number) => ((Math.sin(i * 12.9898 + n * 78.233) * 43758.5453) % 1 + 1) % 1
  return { x: Math.floor(r(1) * HOP_W), y: 2 + Math.floor(r(2) * (HOP_GROUND - 26)), phase: r(3) * 6.28, color: r(4) > 0.7 ? '#fff1a8' : '#e9eef9', alpha: 0.35 + r(5) * 0.5 }
})

function bitmap(name: HopSpriteName): HTMLCanvasElement {
  const b = rasterizeHop(HOP_SPRITES[name], HOP_OUTLINED.has(name) ? HOP_OUTLINE : null)
  const c = document.createElement('canvas')
  c.width = b.w
  c.height = b.h
  c.getContext('2d')?.putImageData(new ImageData(b.data, b.w, b.h), 0, 0)
  return c
}

function setupCanvas() {
  const c = canvas.value
  if (!c || ctx) return
  ctx = c.getContext('2d', { alpha: false })
  if (!ctx) return
  ctx.imageSmoothingEnabled = false
  sprites = Object.fromEntries((Object.keys(HOP_SPRITES) as HopSpriteName[]).map((name) => [name, bitmap(name)])) as Record<HopSpriteName, HTMLCanvasElement>
  // Himmel: Navy-Verlauf wie im Update-Banner.
  sky = document.createElement('canvas')
  sky.width = HOP_W
  sky.height = HOP_H
  const g = sky.getContext('2d')
  if (g) {
    const grad = g.createLinearGradient(0, 0, 0, HOP_H)
    grad.addColorStop(0, '#0c1028')
    grad.addColorStop(1, '#070a1d')
    g.fillStyle = grad
    g.fillRect(0, 0, HOP_W, HOP_H)
    const glow = g.createRadialGradient(HOP_W * 0.78, HOP_H * 0.35, 2, HOP_W * 0.78, HOP_H * 0.35, HOP_W * 0.45)
    glow.addColorStop(0, 'rgba(113, 163, 48, 0.22)')
    glow.addColorStop(1, 'rgba(113, 163, 48, 0)')
    g.fillStyle = glow
    g.fillRect(0, 0, HOP_W, HOP_H)
  }
}

/** Welcher Zustand ist zu sehen: die Vorführung (bis der Spieler selbst spielt) oder die echte Runde. */
function shown(): HopState | null {
  const s = session.value
  if (!s) return null
  return view.demo && s.hop.phase === 'ready' && !reduced.value ? s.demo : s.hop
}

function draw(now: number) {
  const c = ctx
  const s = shown()
  if (!c || !sprites || !sky || !s) return
  const moving = !reduced.value
  c.drawImage(sky, 0, 0)
  for (const star of STARS) {
    const twinkle = moving ? 0.6 + 0.4 * Math.sin(now / 700 + star.phase) : 1
    c.globalAlpha = star.alpha * twinkle
    c.fillStyle = star.color
    c.fillRect(star.x, star.y, 1, 1)
  }
  c.globalAlpha = 1
  // Ferne Inseln ziehen langsam (Parallaxe).
  const far = sprites.farIsland
  const span = HOP_W + far.width + 40
  for (const [i, base, y] of [
    [0, 30, 14],
    [1, 130, 24],
  ] as const) {
    const x = Math.round(((((base - s.scroll * 0.18 - i * 7) % span) + span) % span) - far.width)
    c.drawImage(far, x, y)
  }
  // Boden (gekachelt, rollt mit der Welt).
  const tile = sprites.ground
  const offset = Math.floor(s.scroll) % tile.width
  for (let x = -offset; x < HOP_W; x += tile.width) c.drawImage(tile, x, HOP_GROUND)
  // Funkeln
  for (const sp of s.sparkles) {
    if (sp.taken) continue
    const img = moving && Math.floor(now / 160 + sp.x / 9) % 4 === 0 ? sprites.sparkleSmall : sprites.sparkle
    c.drawImage(img, Math.round(sp.x + (5 - img.width) / 2), Math.round(sp.y + (5 - img.height) / 2))
  }
  // Hindernisse
  for (const o of s.obstacles) {
    const x = Math.round(o.x) - 1
    if (o.kind === 'tall') {
      c.drawImage(sprites.stone, x, HOP_GROUND - 10 - 1)
      c.drawImage(sprites.grass, x, HOP_GROUND - 20 - 1)
    } else if (o.kind === 'wide') {
      c.drawImage(sprites.grass, x, HOP_GROUND - 10 - 1)
      c.drawImage(sprites.grass, x + 10, HOP_GROUND - 10 - 1)
    } else c.drawImage(o.kind === 'stone' ? sprites.stone : sprites.grass, x, HOP_GROUND - 10 - 1)
  }
  // Cubi
  let sprite: HopSpriteName = 'idle'
  let lift = s.lift
  if (s.phase === 'over') sprite = 'hit'
  else if (s.lift > 0) sprite = 'air'
  else if (s.phase === 'running') sprite = Math.floor(s.scroll / 7) % 2 ? 'runB' : 'runA'
  else if (moving) {
    // Bereit: blinzelt ab und zu und hüpft leicht – eine Einladung.
    if (now % 3200 < 140) sprite = 'blink'
    const t = (now % 2400) / 2400
    if (t < 0.18) lift = Math.round(Math.sin((t / 0.18) * Math.PI) * 3)
  }
  const img = sprites[sprite]
  c.drawImage(img, CUBI_X - 1, Math.round(HOP_GROUND - lift - (img.height - 1)))
}

// --- Takt -----------------------------------------------------------------------------------------------------------

let frame = 0
let last = 0
let lastDraw = 0
/** Zeitpunkt des letzten Treffers (für die Neustart-Sperre, auch wenn kein Takt läuft). */
let hitAt = 0
let onScreen = true
const focused = ref(typeof document !== 'undefined' ? document.hasFocus() : true)

/** Soll gerade gezeichnet werden? Nur sichtbar, im Blick und mit Fokus im Launcher. */
function active() {
  return visible.value && onScreen && !document.hidden && focused.value
}

function animating() {
  const s = session.value
  if (!s || !active()) return false
  if (s.hop.phase === 'running') return true
  // Vorführung bzw. Warten: nur ohne „Animationen reduzieren“.
  return !reduced.value && (s.hop.phase === 'ready' || s.hop.phase === 'over')
}

function loop(now: number) {
  frame = 0
  const s = session.value
  if (!s || !animating()) return
  const dt = last ? (now - last) / 1000 : 0
  last = now
  const playing = s.hop.phase === 'running'
  if (playing || s.hop.phase === 'over') {
    const events = stepHop(s.hop, dt)
    if (events.includes('hit')) onHit()
    if (events.length) sync()
  } else if (view.demo && !reduced.value) {
    // Vorführung: läuft endlos (nach einem Treffer neu).
    const d = s.demo
    if (d.phase === 'ready' || (d.phase === 'over' && d.sinceOver > 1.2)) hopInput(d)
    if (hopAutopilot(d)) hopInput(d)
    stepHop(d, dt)
  }
  // Spielen: jedes Bild; Vorführung und Warten: ~30 Bilder je Sekunde.
  if (playing || now - lastDraw >= 32) {
    lastDraw = now
    draw(now)
  }
  frame = requestAnimationFrame(loop)
}

function wake() {
  if (frame === 0 && animating()) {
    last = 0
    frame = requestAnimationFrame(loop)
  }
}

function stopLoop() {
  cancelAnimationFrame(frame)
  frame = 0
}

/** Pause (Fenster im Hintergrund, aus dem Blick) – eine laufende Runde wartet auf den Spieler. */
function suspend() {
  const s = session.value
  if (s && s.hop.phase === 'running') {
    pauseHop(s.hop)
    sync()
    draw(performance.now())
  }
  stopLoop()
}

function onHit() {
  const s = session.value
  if (!s) return
  hitAt = performance.now()
  remember()
  announce.value = s.hop.newBest ? t('launchGame.resultBest', { score: s.hop.score }) : t('launchGame.result', { score: s.hop.score })
}

// --- Eingaben -------------------------------------------------------------------------------------------------------

function press() {
  const s = session.value
  if (!s || view.closed) return
  if (s.hop.phase === 'ready') view.demo = false
  if (s.hop.phase === 'over') s.hop.sinceOver = Math.max(s.hop.sinceOver, (performance.now() - hitAt) / 1000)
  const event = hopInput(s.hop)
  if (event === 'start') announce.value = ''
  sync()
  draw(performance.now())
  wake()
}

function onKey(e: KeyboardEvent) {
  // Knöpfe im Spielfeld lösen ihren eigenen Klick aus.
  if ((e.target as Element | null)?.closest('button')) return
  if (e.repeat && e.key !== ' ') return
  if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'w' || e.key === 'W' || e.key === 'Enter') {
    e.preventDefault()
    press()
  }
}

function onPointer(e: PointerEvent) {
  // Knöpfe im Spielfeld (Start, Weiterspielen) haben eigene Aktionen.
  if ((e.target as Element | null)?.closest('button')) return
  if (e.pointerType === 'mouse' && e.button !== 0) return
  press()
}

// --- Lebenszyklus ---------------------------------------------------------------------------------------------------

let resizeObserver: ResizeObserver | undefined
let viewObserver: IntersectionObserver | undefined

function fitScale() {
  const el = stage.value
  if (!el) return
  const width = el.parentElement?.clientWidth ?? HOP_W * 3
  scale.value = Math.max(1, Math.min(3, Math.floor(width / HOP_W)))
}

function onVisibility() {
  if (document.hidden) suspend()
  else {
    draw(performance.now())
    wake()
  }
}
function onFocus() {
  focused.value = true
  draw(performance.now())
  wake()
}
function onBlur() {
  focused.value = false
  suspend()
}

watch(visible, (show) => {
  if (!show) {
    stopLoop()
    return
  }
  nextTick(() => {
    observe()
    setupCanvas()
    draw(performance.now())
    wake()
  })
})
watch(reduced, () => {
  draw(performance.now())
  wake()
})

function observe() {
  const el = stage.value
  if (!el) return
  resizeObserver?.disconnect()
  viewObserver?.disconnect()
  resizeObserver = new ResizeObserver(fitScale)
  if (el.parentElement) resizeObserver.observe(el.parentElement)
  viewObserver = new IntersectionObserver((entries) => {
    onScreen = entries[entries.length - 1]?.isIntersecting ?? true
    if (onScreen) {
      draw(performance.now())
      wake()
    } else suspend()
  })
  viewObserver.observe(el)
  fitScale()
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('focus', onFocus)
  window.addEventListener('blur', onBlur)
  if (visible.value) {
    observe()
    setupCanvas()
    draw(performance.now())
    wake()
  }
})

onBeforeUnmount(() => {
  remember()
  stopLoop()
  clearTimeout(fallbackTimer)
  clearInterval(countdownTimer)
  resizeObserver?.disconnect()
  viewObserver?.disconnect()
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('focus', onFocus)
  window.removeEventListener('blur', onBlur)
  ctx = null
  sprites = null
  sky = null
  // Eine laufende Runde wartet, bis der Spieler zurück ist (z. B. andere Seite).
  const s = session.value
  if (s) pauseHop(s.hop)
})

// Runde anlegen bzw. beenden – erst hier, wenn alles oben (Takt, Zeichnen) schon steht.
watch(
  [target, phase],
  ([id, p]) => {
    if (!id || p === 'idle') {
      // Start abgebrochen oder Spiel beendet: Runde vorbei.
      if (session.value) {
        for (const [key, s] of sessions) if (s === session.value) sessions.delete(key)
      }
      session.value = null
      stopLoop()
      return
    }
    ensureSession(id, p === 'preparing')
    const s = session.value!
    if (p === 'running' && s.runningSince === null) s.runningSince = Date.now()
    checkWindow()
    nextTick(() => {
      setupCanvas()
      draw(performance.now())
      wake()
    })
  },
  { immediate: true },
)

const percent = computed(() => {
  const p = game.value?.progress
  return p ? Math.floor(overallPercent(p.stage, p.percent)) : 0
})
const status = computed(() => {
  if (phase.value === 'preparing') {
    const p = game.value?.progress
    const stage = p ? stageLabel(p.stage) : t('play.preparing')
    return `${stage} · ${t('tasks.percent', { percent: percent.value })}`
  }
  return t('launchGame.windowSoon')
})
</script>

<template>
  <section v-if="visible" class="hop card" :aria-label="t('launchGame.title')" data-testid="launch-mini-game">
    <header class="hop-head">
      <span class="hop-badge" aria-hidden="true"><CubeMark :size="14" /></span>
      <div class="min-w-0 flex-1">
        <p class="text-sm font-semibold text-base-50">{{ t('launchGame.title') }}</p>
        <!-- Kein Live-Bereich: Der Fortschritt ändert sich ständig (der Spielen-Knopf meldet ihn schon). -->
        <p class="truncate text-xs text-base-400">{{ status }}</p>
      </div>
      <p class="hop-score" :aria-label="`${t('launchGame.score', { score: view.score })}, ${t('launchGame.best', { score: view.best })}`">
        <span>{{ t('launchGame.score', { score: view.score }) }}</span>
        <span class="text-base-500" aria-hidden="true">·</span>
        <span :class="view.newBest ? 'text-primary-300' : 'text-base-400'">{{ t('launchGame.best', { score: view.best }) }}</span>
      </p>
      <button type="button" class="btn-icon size-7 shrink-0" :title="t('launchGame.close')" :aria-label="t('launchGame.close')" @click="close">
        <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
      </button>
    </header>

    <!-- Minecraft ist da: kurz noch weiterspielen oder schließen. -->
    <div v-if="view.windowReady && !view.keep" class="hop-ready">
      <span class="size-2 shrink-0 animate-live rounded-full bg-live-400" aria-hidden="true" />
      <span class="min-w-0 flex-1 text-sm font-medium text-base-50" role="status">{{ t('launchGame.ready') }}</span>
      <!-- Countdown sichtbar, aber nicht jede Sekunde vorgelesen. -->
      <span v-if="view.phase !== 'running'" class="text-xs tabular-nums text-base-400" aria-hidden="true">{{ t('launchGame.closesIn', { seconds: countdown }) }}</span>
      <button type="button" class="btn btn-primary px-3 py-1 text-xs" @click="keepPlaying">{{ t('launchGame.keepPlaying') }}</button>
      <button type="button" class="btn btn-ghost px-3 py-1 text-xs" @click="close">{{ t('launchGame.closeNow') }}</button>
    </div>

    <div class="hop-frame">
      <div
        ref="stage"
        class="hop-stage"
        tabindex="0"
        role="application"
        :aria-label="t('launchGame.areaLabel')"
        :style="{ width: `${HOP_W * scale}px`, height: `${HOP_H * scale}px` }"
        @keydown="onKey"
        @pointerdown="onPointer"
      >
        <canvas ref="canvas" :width="HOP_W" :height="HOP_H" class="hop-canvas" />
        <div v-if="view.phase !== 'running'" class="hop-overlay">
          <template v-if="view.phase === 'ready'">
            <p class="hop-message">{{ t('launchGame.intro') }}</p>
            <button type="button" class="btn btn-primary px-4 py-1.5 text-sm" @click="press">
              <svg viewBox="0 0 24 24" class="size-3.5" fill="currentColor" aria-hidden="true"><path d="M7.5 4.6v14.8a1 1 0 0 0 1.5.86l12.2-7.4a1 1 0 0 0 0-1.72L9 3.74a1 1 0 0 0-1.5.86Z" /></svg>
              {{ t('launchGame.start') }}
            </button>
          </template>
          <p v-else-if="view.phase === 'paused'" class="hop-message">{{ t('launchGame.paused') }}</p>
          <template v-else-if="view.phase === 'over'">
            <p class="hop-message">
              <span v-if="view.newBest" class="mr-1.5 font-semibold text-primary-300">{{ t('launchGame.newBest') }}</span>{{ t('launchGame.over') }}
            </p>
          </template>
        </div>
      </div>
      <p class="hop-controls">{{ t('launchGame.controls') }}</p>
    </div>
    <p class="sr-only" aria-live="polite">{{ announce }}</p>
  </section>
</template>

<style scoped>
.hop {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.85rem 1rem 1rem;
}
.hop-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.hop-badge {
  display: grid;
  width: 2rem;
  height: 2rem;
  flex-shrink: 0;
  place-items: center;
  border-radius: 0.6rem;
  background: var(--color-primary-900);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-primary-600) 40%, transparent);
}
.hop-score {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  font-family: var(--font-display);
  font-size: 0.85rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-base-50);
  white-space: nowrap;
}
.hop-ready {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.75rem;
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--color-live-900) 70%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-live-400) 35%, transparent);
}
.hop-frame {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}
/* Spielfeld: ganzzahlig vergrößert, Pixel scharf, Navy-Rahmen wie die Karten. */
.hop-stage {
  position: relative;
  max-width: 100%;
  overflow: hidden;
  border-radius: 0.75rem;
  outline: none;
  cursor: pointer;
  touch-action: manipulation;
  user-select: none;
  box-shadow:
    inset 0 0 0 1px var(--color-line-strong),
    0 14px 30px -18px rgb(0 0 0 / 0.8);
}
.hop-stage:focus-visible {
  box-shadow:
    0 0 0 2px var(--color-primary-400),
    0 14px 30px -18px rgb(0 0 0 / 0.8);
}
.hop-canvas {
  display: block;
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}
.hop-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  padding: 0.75rem;
  text-align: center;
  background: linear-gradient(180deg, rgb(7 10 29 / 0.15), rgb(7 10 29 / 0.55));
}
.hop-message {
  max-width: 28rem;
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
  color: #f3f6f4;
  background: rgb(7 10 29 / 0.72);
  text-wrap: balance;
}
.hop-controls {
  font-size: 0.7rem;
  color: var(--color-base-400);
  text-align: center;
}
</style>
