<script setup lang="ts">
// Bestenliste (Instanzen, Versionen, Server): Name, Spielzeit und ein dünner Balken im Verhältnis
// zum Ersten. Schrift in Textfarben, nur der Balken trägt den Akzent. Erst fünf, „Alle zeigen“ klappt
// den Rest auf.
export interface RankRow {
  key: string
  label: string
  /** Zweite Zeile (z. B. „12 Sitzungen“). */
  detail?: string
  seconds: number
  /** Anteil an der Summe (0–1). */
  share: number
  /** Ausgegraut (gelöschte Instanz). */
  muted?: boolean
}

const props = withDefaults(defineProps<{ rows: RankRow[]; limit?: number; empty?: string }>(), { limit: 5, empty: '' })
const all = ref(false)
const shown = computed(() => (all.value ? props.rows : props.rows.slice(0, props.limit)))
const top = computed(() => props.rows[0]?.seconds || 1)
</script>

<template>
  <div>
    <ol v-if="rows.length" class="space-y-3">
      <li v-for="(row, i) in shown" :key="row.key" class="flex items-start gap-3">
        <span class="mt-0.5 w-4 shrink-0 text-right text-xs text-base-500 tabular-nums" aria-hidden="true">{{ i + 1 }}</span>
        <slot name="lead" :row="row" />
        <div class="min-w-0 flex-1">
          <div class="flex items-baseline gap-3">
            <span class="min-w-0 flex-1 truncate text-sm" :class="row.muted ? 'text-base-400 italic' : 'text-base-50'" :title="row.label">{{ row.label }}</span>
            <span class="shrink-0 text-sm font-medium text-base-50 tabular-nums">{{ formatPlayTime(row.seconds) }}</span>
          </div>
          <div class="mt-1 flex items-center gap-3">
            <span class="track h-1.5 flex-1 overflow-hidden rounded-full" aria-hidden="true">
              <span class="fill block h-full rounded-full" :style="{ width: `${Math.max(2, (row.seconds / top) * 100)}%` }" />
            </span>
            <span class="w-10 shrink-0 text-right text-[11px] text-base-400 tabular-nums">{{ formatNumber(row.share * 100) }} %</span>
          </div>
          <p v-if="row.detail" class="mt-0.5 truncate text-[11px] text-base-400">{{ row.detail }}</p>
        </div>
      </li>
    </ol>
    <p v-else class="py-4 text-sm text-base-400">{{ empty }}</p>
    <button
      v-if="rows.length > limit"
      type="button"
      class="mt-3 text-xs text-base-400 underline-offset-2 hover:text-base-50 hover:underline"
      :aria-expanded="all"
      @click="all = !all"
    >
      {{ all ? t('stats.top.showLess') : t('stats.top.showAll', { n: rows.length }) }}
    </button>
  </div>
</template>

<style scoped>
.track {
  background: var(--color-base-800);
}
.fill {
  background: color-mix(in oklab, var(--color-primary-500) 85%, transparent);
}
</style>
