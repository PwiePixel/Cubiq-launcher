<script setup lang="ts">
import { niceHours, type DayBar } from '~/utils/playStats'

// Spielzeit je Tag bzw. Woche als schlanke Säulen: eine Reihe, eine Achse, Akzentfarbe.
// Bedienung: Maus/Touch über einer Säule oder Fokus + Pfeiltasten (eine Tab-Stelle); der gewählte
// Tag steht als Text oben (aria-live). „Als Tabelle“ zeigt dieselben Zahlen – nichts ist nur über
// die Grafik lesbar.
const props = defineProps<{ bars: DayBar[]; mode: 'days' | 'weeks'; label: string }>()

const { reduced } = useMotion()
const hover = ref<number | null>(null)
const table = ref(false)
const max = computed(() => niceHours(Math.max(0, ...props.bars.map((b) => b.seconds))))
const total = computed(() => props.bars.reduce((sum, b) => sum + b.seconds, 0))
const best = computed(() => props.bars.reduce<DayBar | null>((top, b) => (b.seconds > (top?.seconds ?? 0) ? b : top), null))

/** Achsen-Text: „30 min“, „2 h“, „1,5 h“. */
function axis(seconds: number): string {
  if (seconds <= 0) return '0'
  if (seconds < 3600) return t('format.playTime.minutes', { minutes: Math.round(seconds / 60) })
  return t('stats.units.hours', { n: formatNumber(seconds / 3600, 1) })
}

function dateLabel(bar: DayBar, long = false): string {
  const day = new Intl.DateTimeFormat(intlLocale(), long ? { weekday: 'short', day: 'numeric', month: 'short' } : { day: 'numeric', month: 'short' }).format(bar.date)
  return props.mode === 'weeks' && long ? t('stats.chart.weekOf', { date: day }) : day
}
const current = computed(() => (hover.value !== null ? (props.bars[hover.value] ?? null) : null))

/** Beschriftung unten: Anfang, Ende und zwei Zwischenschritte. */
const ticks = computed(() => {
  const n = props.bars.length
  if (!n) return []
  const idx = [...new Set([0, Math.round((n - 1) / 3), Math.round((2 * (n - 1)) / 3), n - 1])]
  return idx.map((i, k) => ({ i, k, text: dateLabel(props.bars[i]!), left: `${((i + 0.5) / n) * 100}%` }))
})

function onKey(e: KeyboardEvent) {
  const last = props.bars.length - 1
  if (last < 0) return
  const now = hover.value ?? last
  const next = e.key === 'ArrowLeft' ? now - 1 : e.key === 'ArrowRight' ? now + 1 : e.key === 'Home' ? 0 : e.key === 'End' ? last : null
  if (next === null) return
  e.preventDefault()
  hover.value = Math.min(last, Math.max(0, next))
}

const describe = computed(() =>
  t('stats.chart.summary', {
    label: props.label,
    total: formatPlayTime(total.value),
    best: best.value ? `${dateLabel(best.value, true)} (${formatPlayTime(best.value.seconds)})` : '–',
  }),
)
</script>

<template>
  <figure class="min-w-0">
    <div class="mb-2 flex min-h-5 flex-wrap items-baseline gap-x-3 gap-y-1 text-xs text-base-400">
      <span>{{ t('stats.chart.total', { time: formatPlayTime(total) }) }}</span>
      <span class="ml-auto font-medium text-base-50 tabular-nums" aria-live="polite">
        <template v-if="current">{{ dateLabel(current, true) }} · {{ current.seconds ? formatPlayTime(current.seconds) : t('stats.chart.none') }}</template>
      </span>
    </div>
    <div class="flex gap-2">
      <!-- y-Achse: 0, Hälfte, Obergrenze -->
      <div class="relative h-40 w-12 shrink-0 text-right text-[10px] text-base-400 tabular-nums" aria-hidden="true">
        <span class="absolute top-0 right-0 -translate-y-1/2">{{ axis(max) }}</span>
        <span class="absolute top-1/2 right-0 -translate-y-1/2">{{ axis(max / 2) }}</span>
        <span class="absolute right-0 bottom-0 translate-y-1/2">0</span>
      </div>
      <div class="min-w-0 flex-1">
        <div
          class="bars relative h-40 rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-primary-400"
          tabindex="0"
          role="img"
          :aria-label="describe"
          data-testid="stats-bars"
          @mouseleave="hover = null"
          @focus="hover ??= bars.length ? bars.length - 1 : null"
          @blur="hover = null"
          @keydown="onKey"
        >
          <!-- Raster: feine, durchgezogene Linien -->
          <span class="grid-line top-0" aria-hidden="true" />
          <span class="grid-line top-1/2" aria-hidden="true" />
          <span class="axis-line bottom-0" aria-hidden="true" />
          <div class="absolute inset-0 flex items-end">
            <div
              v-for="(b, i) in bars"
              :key="b.day"
              class="slot relative flex h-full flex-1 items-end justify-center"
              aria-hidden="true"
              @mouseenter="hover = i"
              @touchstart.passive="hover = i"
            >
              <span
                v-if="b.seconds > 0"
                class="bar"
                :class="{ 'bar-on': hover === i, 'bar-grow': !reduced }"
                :style="{ height: `${Math.max(2, (b.seconds / max) * 100)}%` }"
              />
            </div>
          </div>
        </div>
        <div class="relative mt-1.5 h-4 text-[10px] text-base-400" aria-hidden="true">
          <span
            v-for="tk in ticks"
            :key="tk.i"
            class="absolute whitespace-nowrap"
            :class="tk.k === 0 ? '' : tk.k === ticks.length - 1 ? '-translate-x-full' : '-translate-x-1/2'"
            :style="{ left: tk.k === 0 ? '0' : tk.k === ticks.length - 1 ? '100%' : tk.left }"
          >{{ tk.text }}</span>
        </div>
      </div>
    </div>
    <button type="button" class="mt-2 text-xs text-base-400 underline-offset-2 hover:text-base-50 hover:underline" :aria-expanded="table" @click="table = !table">
      {{ table ? t('stats.chart.hideTable') : t('stats.chart.showTable') }}
    </button>
    <div v-if="table" class="mt-2 max-h-64 overflow-y-auto rounded-lg border border-line">
      <table class="w-full text-xs">
        <caption class="sr-only">{{ label }}</caption>
        <thead class="sticky top-0 bg-base-900">
          <tr class="text-left text-[11px] tracking-wide text-base-400 uppercase">
            <th scope="col" class="px-3 py-1.5 font-medium">{{ mode === 'weeks' ? t('stats.chart.week') : t('stats.chart.day') }}</th>
            <th scope="col" class="px-3 py-1.5 text-right font-medium">{{ t('stats.chart.playTime') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in [...bars].reverse()" :key="b.day" class="border-t border-line">
            <th scope="row" class="px-3 py-1 text-left font-normal text-base-200">{{ dateLabel(b, true) }}</th>
            <td class="px-3 py-1 text-right tabular-nums" :class="b.seconds ? 'text-base-50' : 'text-base-400'">{{ b.seconds ? formatPlayTime(b.seconds) : '–' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </figure>
</template>

<style scoped>
/* Feine Linien eine Stufe über der Fläche; die Daten sind das einzig Laute. */
.grid-line,
.axis-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  pointer-events: none;
}
.grid-line {
  background: var(--color-base-800);
}
.axis-line {
  background: var(--color-base-700);
}
/* Säulen: höchstens 24 px breit, oben 4 px gerundet, unten gerade auf der Grundlinie; dazwischen Luft. */
.slot {
  padding-inline: 1px;
}
.bar {
  position: relative;
  display: block;
  width: min(100%, 1.5rem);
  border-radius: 4px 4px 0 0;
  background: color-mix(in oklab, var(--color-primary-500) 82%, transparent);
  transition: background-color 0.12s;
}
.bar-on {
  background: var(--color-primary-400);
}
.bar-grow {
  animation: bar-grow 0.45s cubic-bezier(0.2, 0.8, 0.2, 1) both;
  transform-origin: bottom;
}
@keyframes bar-grow {
  from {
    transform: scaleY(0);
  }
}
</style>
