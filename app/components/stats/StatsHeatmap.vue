<script setup lang="ts">
import { heatmapGrid, MIN_DAY_SECONDS, type HeatCell } from '~/utils/playStats'

// Kalender der letzten Monate: Spalten sind Wochen, Zeilen Wochentage; je mehr gespielt, desto
// satter das Grün (eine Farbe, vier Stufen nach den Quartilen der gespielten Tage). So viele Wochen,
// wie in die Breite passen. Bedienung: Fokus + Pfeiltasten (eine Tab-Stelle) oder Maus; der Tag
// steht als Text darüber. „Als Tabelle“ listet die gespielten Tage.
const props = defineProps<{ days: Map<string, number>; weekStart: number; now: Date; label: string }>()

const box = useTemplateRef<HTMLElement>('box')
const weeks = ref(26)
let observer: ResizeObserver | null = null
onMounted(() => {
  const fit = () => {
    const width = box.value?.clientWidth ?? 0
    // Zelle 13 px + 3 px Abstand, links Platz für die Wochentage.
    if (width) weeks.value = Math.max(13, Math.min(53, Math.floor((width - 32) / 16)))
  }
  fit()
  observer = new ResizeObserver(fit)
  if (box.value) observer.observe(box.value)
})
onBeforeUnmount(() => observer?.disconnect())

const grid = computed(() => heatmapGrid(props.days, weeks.value, props.weekStart, props.now))
const flat = computed(() => grid.value.columns.flat())
const active = ref<number | null>(null)
const table = ref(false)
const current = computed<HeatCell | null>(() => (active.value !== null ? (flat.value[active.value] ?? null) : null))
const lastIndex = computed(() => {
  let last = 0
  flat.value.forEach((c, i) => {
    if (!c.future) last = i
  })
  return last
})

const dayFormat = computed(() => new Intl.DateTimeFormat(intlLocale(), { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }))
const monthFormat = computed(() => new Intl.DateTimeFormat(intlLocale(), { month: 'short' }))
const weekdayFormat = computed(() => new Intl.DateTimeFormat(intlLocale(), { weekday: 'short' }))

/** Monatsnamen über der Spalte, in der ein Monat beginnt (nicht zu dicht). */
const months = computed(() => {
  const out: { col: number; text: string }[] = []
  grid.value.columns.forEach((column, col) => {
    const first = column.find((c) => c.date.getDate() === 1)
    if ((first || col === 0) && (!out.length || col - out.at(-1)!.col >= 3)) {
      out.push({ col, text: monthFormat.value.format((first ?? column[0]!).date) })
    }
  })
  return out
})
/** Wochentage links: jede zweite Zeile. */
const weekdays = computed(() => grid.value.columns[0]?.map((c, row) => (row % 2 === 1 ? weekdayFormat.value.format(c.date) : '')) ?? [])

function onKey(e: KeyboardEvent) {
  const now = active.value ?? lastIndex.value
  const step: Record<string, number> = { ArrowUp: -1, ArrowDown: 1, ArrowLeft: -7, ArrowRight: 7 }
  let next: number | null = null
  if (e.key in step) next = now + step[e.key]!
  else if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = lastIndex.value
  if (next === null) return
  e.preventDefault()
  active.value = Math.min(lastIndex.value, Math.max(0, next))
}

const playedDays = computed(() => flat.value.filter((c) => c.seconds >= MIN_DAY_SECONDS))
const describe = computed(() =>
  t('stats.heatmap.summary', {
    label: props.label,
    weeks: weeks.value,
    days: playedDays.value.length,
  }),
)
const cellText = (c: HeatCell) => `${dayFormat.value.format(c.date)} · ${c.seconds >= MIN_DAY_SECONDS ? formatPlayTime(c.seconds) : t('stats.chart.none')}`
</script>

<template>
  <figure ref="box" class="min-w-0">
    <p class="mb-2 min-h-5 text-right text-xs font-medium text-base-50 tabular-nums" aria-live="polite">
      <template v-if="current">{{ cellText(current) }}</template>
    </p>
    <div class="flex gap-1.5">
      <div class="grid shrink-0 grid-rows-[1rem_repeat(7,13px)] gap-y-[3px] pr-1 text-[10px] leading-[13px] text-base-400" aria-hidden="true">
        <span />
        <span v-for="(w, i) in weekdays" :key="i" class="h-[13px]">{{ w }}</span>
      </div>
      <div class="min-w-0">
        <div class="relative h-4 text-[10px] text-base-400" aria-hidden="true">
          <span v-for="m in months" :key="m.col" class="absolute whitespace-nowrap" :style="{ left: `${m.col * 16}px` }">{{ m.text }}</span>
        </div>
        <div
          class="heat grid w-max grid-flow-col grid-rows-[repeat(7,13px)] gap-[3px] rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-primary-400 focus-visible:ring-offset-2 focus-visible:ring-offset-base-900"
          tabindex="0"
          role="img"
          :aria-label="describe"
          data-testid="stats-heatmap"
          @keydown="onKey"
          @focus="active ??= lastIndex"
          @blur="active = null"
          @mouseleave="active = null"
        >
          <span
            v-for="(c, i) in flat"
            :key="c.day"
            class="cell"
            :class="[c.future ? 'cell-future' : `cell-${c.level}`, { 'cell-on': active === i }]"
            aria-hidden="true"
            @mouseenter="!c.future && (active = i)"
          />
        </div>
      </div>
    </div>
    <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px] text-base-400">
      <button type="button" class="underline-offset-2 hover:text-base-50 hover:underline" :aria-expanded="table" @click="table = !table">
        {{ table ? t('stats.chart.hideTable') : t('stats.chart.showTable') }}
      </button>
      <span class="ml-auto flex items-center gap-1" aria-hidden="true">
        {{ t('stats.heatmap.less') }}
        <span v-for="l in 5" :key="l" class="cell" :class="`cell-${l - 1}`" />
        {{ t('stats.heatmap.more') }}
      </span>
    </div>
    <div v-if="table" class="mt-2 max-h-64 overflow-y-auto rounded-lg border border-line">
      <table class="w-full text-xs">
        <caption class="sr-only">{{ label }}</caption>
        <thead class="sticky top-0 bg-base-900">
          <tr class="text-left text-[11px] tracking-wide text-base-400 uppercase">
            <th scope="col" class="px-3 py-1.5 font-medium">{{ t('stats.chart.day') }}</th>
            <th scope="col" class="px-3 py-1.5 text-right font-medium">{{ t('stats.chart.playTime') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in [...playedDays].reverse()" :key="c.day" class="border-t border-line">
            <th scope="row" class="px-3 py-1 text-left font-normal text-base-200">{{ dayFormat.format(c.date) }}</th>
            <td class="px-3 py-1 text-right text-base-50 tabular-nums">{{ formatPlayTime(c.seconds) }}</td>
          </tr>
          <tr v-if="!playedDays.length">
            <td colspan="2" class="px-3 py-2 text-base-400">{{ t('stats.chart.none') }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </figure>
</template>

<style scoped>
/*
 * Eine Farbe in fünf Stufen: leere Mulde, dann der Akzent von gedämpft bis voll. Dunkel geht es zum
 * Leuchten hin, hell zum Satten hin – die schwächste Stufe hebt sich in beiden Schemata mit mindestens
 * 2:1 von der Fläche ab, der Farbton bleibt gleich (gemischt mit Schwarz/Weiß, nicht mit dem Navy).
 */
.cell {
  display: inline-block;
  width: 13px;
  height: 13px;
  border-radius: 3px;
}
.cell-0 {
  background: var(--color-base-800);
}
.cell-1 {
  background: color-mix(in oklab, var(--color-primary-500) 62%, black);
}
.cell-2 {
  background: color-mix(in oklab, var(--color-primary-500) 75%, black);
}
.cell-3 {
  background: color-mix(in oklab, var(--color-primary-500) 88%, black);
}
.cell-4 {
  background: var(--color-primary-500);
}
:root[data-theme='light'] .cell-1 {
  background: color-mix(in oklab, var(--color-primary-500) 70%, white);
}
:root[data-theme='light'] .cell-2 {
  background: var(--color-primary-500);
}
:root[data-theme='light'] .cell-3 {
  background: color-mix(in oklab, var(--color-primary-500) 85%, black);
}
:root[data-theme='light'] .cell-4 {
  background: color-mix(in oklab, var(--color-primary-500) 70%, black);
}
.cell-future {
  background: transparent;
}
.cell-on {
  box-shadow:
    0 0 0 2px var(--color-base-900),
    0 0 0 3px var(--color-base-50);
}
</style>
