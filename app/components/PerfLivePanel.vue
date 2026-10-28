<script setup lang="ts">
import type { Instance } from '~/types'
import { PERF_HISTORY, perfPoint, perfStats, perfTips, pushPerfPoint, sparkPath, type GamePerf, type PerfPoint, type PerfTip } from '~/utils/perfLive'

// Live-Leistung des laufenden Spiels (Instanzseite): FPS, Arbeitsspeicher und CPU als kleine Kurven der letzten
// Minute, etwa je Sekunde gemessen (Befehl `game_perf`), dazu Tipps, wenn es hakt – Speicher fast voll → mehr
// Speicher, niedrige FPS → Cubiq-Optimierung bzw. Leistungs-Mods. FPS und Java-Speicher meldet nur der Cubiq Client;
// ohne ihn bleiben Arbeitsspeicher und CPU. Gemessen wird nur, solange das Fenster sichtbar ist.
const props = defineProps<{ instance: Instance }>()
const emit = defineEmits<{ openSettings: [section: string] }>()

const games = useGamesStore()
const game = computed(() => games.state(props.instance.id))
const running = computed(() => game.value.phase === 'running' && game.value.mainAlive)

const COLLAPSED_KEY = 'cubiq.perfLive.collapsed'
function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}
const collapsed = ref(readCollapsed())
function toggle() {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem(COLLAPSED_KEY, collapsed.value ? '1' : '0')
  } catch {
    // ohne Speicher gilt die Wahl nur jetzt
  }
}

const history = shallowRef<PerfPoint[]>([])
const latest = shallowRef<GamePerf | null>(null)
let previous: GamePerf | null = null
let timer: ReturnType<typeof setTimeout> | undefined
let polling = false

async function poll() {
  timer = undefined
  if (!polling) return
  try {
    const next = await backend.gamePerf(props.instance.id)
    if (next && polling) {
      const list = history.value.slice()
      pushPerfPoint(list, perfPoint(previous, next))
      previous = next
      history.value = list
      latest.value = next
    }
  } catch {
    // Ein ausgelassener Messpunkt – beim nächsten Mal wieder.
  }
  if (polling) timer = setTimeout(poll, 1000)
}

function start() {
  if (polling || !running.value || document.hidden) return
  polling = true
  void poll()
}
function stop() {
  polling = false
  clearTimeout(timer)
  timer = undefined
  // Nach einer Pause rechnet die Auslastung neu (kein Riesenschritt über die Lücke).
  previous = null
}

function onVisibility() {
  if (document.hidden) stop()
  else start()
}

// Neues Spiel: Kurven von vorn.
watch(
  () => game.value.startedAt,
  () => {
    history.value = []
    latest.value = null
    previous = null
  },
)
watch(running, (now) => (now ? start() : stop()), { immediate: true })
onMounted(() => document.addEventListener('visibilitychange', onVisibility))
onBeforeUnmount(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibility)
})

// --- Kacheln ------------------------------------------------------------------------------------------------------

const W = 120
const H = 34

interface Tile {
  key: 'fps' | 'ram' | 'cpu'
  label: string
  value: string
  note: string
  values: (number | null)[]
  /** Oberer Rand der Kurve (mindestens der größte Wert). */
  max: number
  format: (v: number) => string
  summary: string
}

const fmtFps = (v: number) => t('perfLive.fpsValue', { fps: Math.round(v) })
const fmtRam = (v: number) => formatMemory(v)
const fmtCpu = (v: number) => t('tasks.percent', { percent: Math.round(v) })

function summary(label: string, values: (number | null)[], format: (v: number) => string): string {
  const s = perfStats(values)
  if (s.avg === null || s.min === null || s.max === null) return `${label}: ${t('perfLive.noData')}`
  return t('perfLive.chartSummary', {
    label,
    now: s.current === null ? '–' : format(s.current),
    avg: format(s.avg),
    min: format(s.min),
    max: format(s.max),
  })
}

const tiles = computed<Tile[]>(() => {
  const points = history.value
  const last = latest.value
  const fps = points.map((p) => (p.fpsLimited ? null : p.fps))
  const ram = points.map((p) => p.ramMb)
  const cpu = points.map((p) => p.cpu)
  const fpsStats = perfStats(fps)
  let fpsNote: string
  if (!last?.client) fpsNote = t('perfLive.fpsNoClient')
  else if (last.fpsLimited) fpsNote = t('perfLive.fpsLimited')
  else if (last.fps === null) fpsNote = t('perfLive.fpsWaiting')
  else fpsNote = fpsStats.avg !== null && fpsStats.min !== null ? t('perfLive.fpsStats', { avg: Math.round(fpsStats.avg), min: Math.round(fpsStats.min) }) : ''
  const heap = last && last.heapUsedMb !== null && last.heapMaxMb !== null ? t('perfLive.heap', { used: formatMemory(last.heapUsedMb), max: formatMemory(last.heapMaxMb) }) : null
  const free = last?.systemFreeMb != null ? t('perfLive.systemFree', { free: formatMemory(last.systemFreeMb) }) : ''
  const lastRam = ram.length ? ram[ram.length - 1] : null
  const lastCpu = cpu.length ? cpu[cpu.length - 1] : null
  const lastFps = last && !last.fpsLimited && last.fps !== null ? last.fps : null
  return [
    {
      key: 'fps',
      label: t('perfLive.fps'),
      value: lastFps === null ? '–' : String(Math.round(lastFps)),
      note: fpsNote,
      values: fps,
      max: 60,
      format: fmtFps,
      summary: summary(t('perfLive.fps'), fps, fmtFps),
    },
    {
      key: 'ram',
      label: t('perfLive.ram'),
      value: lastRam == null ? '–' : formatMemory(lastRam),
      note: heap ?? free,
      values: ram,
      max: (last?.heapMaxMb ?? 1024) * 1.1,
      format: fmtRam,
      summary: summary(t('perfLive.ram'), ram, fmtRam),
    },
    {
      key: 'cpu',
      label: t('perfLive.cpu'),
      value: lastCpu == null ? '–' : fmtCpu(lastCpu),
      note: last ? t('perfLive.cpuNote', { cores: last.cores }) : '',
      values: cpu,
      max: 100,
      format: fmtCpu,
      summary: summary(t('perfLive.cpu'), cpu, fmtCpu),
    },
  ]
})

function areaPath(line: string): string {
  if (!line) return ''
  const xs = [...line.matchAll(/[ML]([\d.]+) /g)].map((m) => Number(m[1]))
  // Nur das letzte zusammenhängende Stück füllen wäre genauer – eine leichte Fläche unter der ganzen Linie reicht.
  return `${line}L${Math.max(...xs).toFixed(1)} ${H}L${Math.min(...xs).toFixed(1)} ${H}Z`
}

/** Letzter Punkt (Akzent) einer Kurve. */
function lastDot(values: (number | null)[], max: number): { x: number; y: number } | null {
  const v = values.length ? values[values.length - 1] : null
  if (v === null || v === undefined) return null
  const top = Math.max(max, ...values.filter((x): x is number => x !== null), 1e-9)
  return { x: W, y: H - 1 - (Math.max(0, v) / top) * (H - 2) }
}

// Zeigen auf die Kurve: Wert dieser Sekunde.
const hover = ref<{ key: Tile['key']; index: number } | null>(null)
function onHover(tile: Tile, e: PointerEvent) {
  const el = e.currentTarget as SVGElement
  const box = el.getBoundingClientRect()
  if (box.width <= 0) return
  const slot = Math.round(((e.clientX - box.left) / box.width) * (PERF_HISTORY - 1))
  const index = slot - (PERF_HISTORY - tile.values.length)
  hover.value = index >= 0 && index < tile.values.length ? { key: tile.key, index } : null
}
function hoverText(tile: Tile): string | null {
  const h = hover.value
  if (!h || h.key !== tile.key) return null
  const v = tile.values[h.index]
  const ago = tile.values.length - 1 - h.index
  const when = ago === 0 ? t('perfLive.now') : t('perfLive.secondsAgo', { seconds: ago })
  return `${when} · ${v === null || v === undefined ? '–' : tile.format(v)}`
}
function hoverX(tile: Tile): number | null {
  const h = hover.value
  if (!h || h.key !== tile.key) return null
  return ((PERF_HISTORY - tile.values.length + h.index) / (PERF_HISTORY - 1)) * W
}

// --- Tipps ---------------------------------------------------------------------------------------------------------

const dismissed = ref(new Set<PerfTip>())
const tips = computed(() => perfTips(history.value).filter((tip) => !dismissed.value.has(tip)))
const boostOn = computed(() => props.instance.overrides.boost !== false)
function dismiss(tip: PerfTip) {
  dismissed.value = new Set([...dismissed.value, tip])
}

function tipText(tip: PerfTip): string {
  if (tip === 'systemMemory') return t('perfLive.tips.systemMemory.text', { free: formatMemory(latest.value?.systemFreeMb ?? 0) })
  if (tip === 'lowFps') return boostOn.value ? t('perfLive.tips.lowFps.text') : t('perfLive.tips.lowFps.boostOff')
  return t(`perfLive.tips.${tip}.text`)
}
/** Knopf je Tipp: Bereich der Instanz-Einstellungen. */
function tipAction(tip: PerfTip): { label: string; section: string } | null {
  if (tip === 'memory') return { label: t('perfLive.tips.memory.action'), section: 'java' }
  if (tip === 'lowFps' || tip === 'stutter') return { label: t('perfLive.tips.performanceAction'), section: 'installation' }
  return null
}

const compact = computed(() =>
  tiles.value
    .filter((tile) => tile.value !== '–')
    .map((tile) => (tile.key === 'fps' ? t('perfLive.fpsValue', { fps: tile.value }) : `${tile.label} ${tile.value}`))
    .join(' · '),
)
</script>

<template>
  <section v-if="running" class="perf card" :aria-label="t('perfLive.title')" data-testid="perf-live">
    <header class="flex items-center gap-2.5">
      <span class="size-2 shrink-0 animate-live rounded-full bg-live-400" aria-hidden="true" />
      <h2 class="text-sm font-semibold text-base-50">{{ t('perfLive.title') }}</h2>
      <span v-if="latest?.client" class="badge bg-primary-500/15 text-primary-300">{{ t('perfLive.client') }}</span>
      <span v-if="collapsed && compact" class="truncate text-xs tabular-nums text-base-300">{{ compact }}</span>
      <button
        type="button"
        class="btn-icon ml-auto size-7"
        :aria-expanded="!collapsed"
        :title="collapsed ? t('perfLive.expand') : t('perfLive.collapse')"
        :aria-label="collapsed ? t('perfLive.expand') : t('perfLive.collapse')"
        @click="toggle"
      >
        <svg viewBox="0 0 24 24" class="size-3.5 transition-transform" :class="{ '-rotate-90': collapsed }" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6" /></svg>
      </button>
    </header>

    <template v-if="!collapsed">
      <div class="perf-tiles">
        <div v-for="tile in tiles" :key="tile.key" class="perf-tile" role="group" :aria-label="tile.label">
          <div class="flex items-baseline justify-between gap-2">
            <p class="text-xs font-medium text-base-400">{{ tile.label }}</p>
            <p v-if="hoverText(tile)" class="text-[11px] tabular-nums text-base-300" aria-hidden="true">{{ hoverText(tile) }}</p>
          </div>
          <p class="perf-value">{{ tile.value }}</p>
          <svg
            :viewBox="`0 0 ${W} ${H}`"
            preserveAspectRatio="none"
            class="perf-spark"
            role="img"
            :aria-label="tile.summary"
            @pointermove="onHover(tile, $event)"
            @pointerleave="hover = null"
          >
            <line x1="0" :y1="H - 0.5" :x2="W" :y2="H - 0.5" class="perf-base" />
            <path v-if="sparkPath(tile.values, W, H, tile.max)" :d="areaPath(sparkPath(tile.values, W, H, tile.max))" class="perf-area" />
            <path :d="sparkPath(tile.values, W, H, tile.max)" class="perf-line" />
            <line v-if="hoverX(tile) !== null" :x1="hoverX(tile)!" y1="0" :x2="hoverX(tile)!" :y2="H" class="perf-cross" />
            <circle v-if="lastDot(tile.values, tile.max)" :cx="lastDot(tile.values, tile.max)!.x - 1.5" :cy="lastDot(tile.values, tile.max)!.y" r="2.5" class="perf-dot" />
          </svg>
          <p class="min-h-4 truncate text-[11px] text-base-400">{{ tile.note }}</p>
        </div>
      </div>

      <ul v-if="tips.length" class="mt-3 space-y-2" aria-live="polite">
        <li v-for="tip in tips" :key="tip" class="perf-tip">
          <svg viewBox="0 0 24 24" class="mt-0.5 size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3 2.5 20h19L12 3Z M12 10v4.5 M12 17.5v.01" />
          </svg>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-base-50">{{ t(`perfLive.tips.${tip}.title`) }}</p>
            <p class="text-xs text-base-300">{{ tipText(tip) }}</p>
          </div>
          <button v-if="tipAction(tip)" type="button" class="btn btn-ghost shrink-0 px-3 py-1 text-xs" @click="emit('openSettings', tipAction(tip)!.section)">
            {{ tipAction(tip)!.label }}
          </button>
          <button type="button" class="btn-icon size-6 shrink-0" :title="t('perfLive.dismissTip')" :aria-label="t('perfLive.dismissTip')" @click="dismiss(tip)">
            <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        </li>
      </ul>
    </template>
  </section>
</template>

<style scoped>
.perf {
  padding: 0.75rem 1rem 0.85rem;
}
.perf-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.6rem;
  margin-top: 0.6rem;
}
@media (max-width: 640px) {
  .perf-tiles {
    grid-template-columns: minmax(0, 1fr);
  }
}
.perf-tile {
  min-width: 0;
  padding: 0.55rem 0.7rem 0.45rem;
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--color-base-950) 55%, transparent);
  box-shadow: inset 0 0 0 1px var(--color-line);
}
/* Wert: gleiche Schrift wie alles andere, halbfett; feste Ziffernbreite, damit die Zahl beim Aktualisieren nicht springt. */
.perf-value {
  margin-top: 0.1rem;
  font-size: 1.25rem;
  font-weight: 600;
  line-height: 1.2;
  color: var(--color-base-50);
  font-variant-numeric: tabular-nums;
}
.perf-spark {
  display: block;
  width: 100%;
  height: 2.1rem;
  margin: 0.25rem 0 0.15rem;
  overflow: visible;
  cursor: crosshair;
}
.perf-base {
  stroke: var(--color-line);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}
/* Verlauf zurückhaltend, der aktuelle Wert in der Akzentfarbe. */
.perf-line {
  fill: none;
  stroke: color-mix(in srgb, var(--color-primary-400) 70%, var(--color-base-400));
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
  vector-effect: non-scaling-stroke;
}
.perf-area {
  fill: color-mix(in srgb, var(--color-primary-500) 14%, transparent);
  stroke: none;
}
.perf-cross {
  stroke: var(--color-base-300);
  stroke-width: 1;
  stroke-dasharray: 2 2;
  vector-effect: non-scaling-stroke;
}
.perf-dot {
  fill: var(--color-primary-300);
  stroke: var(--color-base-900);
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
}
.perf-tip {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.55rem 0.7rem;
  border-radius: 0.75rem;
  color: var(--color-warn);
  background: color-mix(in srgb, var(--color-warn) 9%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-warn) 35%, transparent);
}
</style>
