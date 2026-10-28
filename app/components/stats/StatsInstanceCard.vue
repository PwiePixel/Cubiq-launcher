<script setup lang="ts">
import { lastDayBars, summarizePlayStats, weekStartFor, type PlayStatsData } from '~/utils/playStats'

// Spielzeit einer Instanz im Überblick (oben im Verlauf): Summe, Sitzungen, Schnitt, Rekord und
// die letzten 30 Tage als kleine Säulen; „Alle Statistiken“ öffnet die Seite gefiltert auf sie.
const props = defineProps<{ instanceId: string; refreshKey?: number }>()

const data = ref<PlayStatsData | null>(null)
const games = useGamesStore()

async function load() {
  try {
    data.value = await backend.playStats()
  } catch {
    data.value = null
  }
}
watch(() => [props.instanceId, props.refreshKey], load, { immediate: true })
// Spielende: neue Sitzung zeigen.
watch(
  () => games.state(props.instanceId).phase,
  (phase, before) => phase === 'idle' && before !== 'idle' && load(),
)

const summary = computed(() => (data.value ? summarizePlayStats(data.value, { instanceId: props.instanceId, weekStart: weekStartFor(intlLocale()) }) : null))
const days = computed(() => (summary.value ? lastDayBars(summary.value.days, 30) : []))
const peak = computed(() => Math.max(1, ...days.value.map((d) => d.seconds)))
const recent = computed(() => days.value.reduce((sum, d) => sum + d.seconds, 0))
</script>

<template>
  <section v-if="summary && summary.totalSeconds > 0" class="card mb-4 p-4" :aria-label="t('stats.instance.title')" data-testid="instance-stats">
    <div class="flex flex-wrap items-start gap-x-6 gap-y-3">
      <div class="min-w-36">
        <p class="text-xs font-medium text-base-400">{{ t('stats.instance.title') }}</p>
        <p class="mt-1 text-xl font-semibold text-base-50">{{ formatPlayTime(summary.totalSeconds) }}</p>
        <p class="text-xs text-base-400">{{ t('stats.instance.recent', { time: formatPlayTime(recent) }) }}</p>
      </div>
      <dl class="grid flex-1 grid-cols-3 gap-3 text-sm mobile:grid-cols-2">
        <div>
          <dt class="text-xs text-base-400">{{ t('stats.tiles.sessions') }}</dt>
          <dd class="mt-0.5 font-medium text-base-50">{{ formatNumber(summary.sessionCount) }}</dd>
        </div>
        <div>
          <dt class="text-xs text-base-400">{{ t('stats.instance.average') }}</dt>
          <dd class="mt-0.5 font-medium text-base-50">{{ summary.sessionCount ? formatPlayTime(summary.averageSeconds) : '–' }}</dd>
        </div>
        <div>
          <dt class="text-xs text-base-400">{{ t('stats.tiles.longest') }}</dt>
          <dd class="mt-0.5 font-medium text-base-50">{{ summary.longest ? formatPlayTime(summary.longest.seconds) : '–' }}</dd>
        </div>
      </dl>
      <!-- Letzte 30 Tage: Mini-Säulen (Details und Tabelle auf der Statistik-Seite). -->
      <div class="flex h-12 w-40 items-end gap-px mobile:w-full" role="img" :aria-label="t('stats.instance.last30', { time: formatPlayTime(recent) })">
        <span
          v-for="d in days"
          :key="d.day"
          class="mini flex-1 rounded-t-[2px]"
          :class="d.seconds ? 'mini-on' : ''"
          :style="{ height: d.seconds ? `${Math.max(8, (d.seconds / peak) * 100)}%` : '2px' }"
          :title="`${formatShortDate(d.date.toISOString())} · ${d.seconds ? formatPlayTime(d.seconds) : '–'}`"
        />
      </div>
    </div>
    <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line pt-3 text-xs text-base-400">
      <span v-if="summary.crashes">{{ t('stats.instance.crashes', summary.crashes) }}</span>
      <span v-if="summary.versions[0]">{{ t('stats.instance.topVersion', { version: summary.versions[0].key }) }}</span>
      <NuxtLink :to="{ path: '/stats', query: { instance: instanceId } }" class="ml-auto inline-flex items-center gap-1 font-medium text-primary-300 hover:underline" data-testid="instance-stats-link">
        {{ t('stats.instance.open') }}
        <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14m0 0-5-5m5 5-5 5" /></svg>
      </NuxtLink>
    </div>
  </section>
</template>

<style scoped>
.mini {
  background: var(--color-base-800);
}
.mini-on {
  background: color-mix(in oklab, var(--color-primary-500) 85%, transparent);
}
</style>
