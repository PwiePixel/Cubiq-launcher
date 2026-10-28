<script setup lang="ts">
import type { RankRow } from '~/components/stats/StatsRankList.vue'
import { lastDayBars, lastWeekBars, summarizePlayStats, weekStartFor, type PlayStatsData } from '~/utils/playStats'

// Spielzeit-Statistik: Summen, Spielzeit je Tag/Woche, Kalender der letzten Monate, Lieblings-
// Instanzen, -Versionen und -Server, längste Sitzung und Serien. Rechnet alles aus den Sitzungen
// des Kerns (utils/playStats.ts); „?instance=<id>“ zeigt nur eine Instanz (Link von der Instanz-Seite).
const route = useRoute()
const router = useRouter()
const toasts = useToasts()
const instances = useInstancesStore()
const games = useGamesStore()
const mobile = mobileUi

const data = ref<PlayStatsData | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const range = ref<'days' | 'weeks'>('days')
const confirmReset = ref(false)
const resetting = ref(false)
const now = ref(new Date())

const filter = computed<string>({
  get: () => (typeof route.query.instance === 'string' ? route.query.instance : 'all'),
  set: (value) => void router.replace({ query: value === 'all' ? {} : { instance: value } }),
})

async function load() {
  try {
    data.value = await backend.playStats()
    error.value = null
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
    now.value = new Date()
  }
}
onMounted(() => {
  if (!instances.items.length) instances.load().catch(() => {})
  load()
})
// Spiel beendet: neue Sitzung laden.
watch(() => games.runningCount, (count, before) => count < before && load())

const weekStart = computed(() => weekStartFor(intlLocale()))
const instanceId = computed(() => (filter.value === 'all' ? null : filter.value))
const summary = computed(() => (data.value ? summarizePlayStats(data.value, { instanceId: instanceId.value, now: now.value, weekStart: weekStart.value }) : null))
const overall = computed(() => (data.value ? summarizePlayStats(data.value, { now: now.value, weekStart: weekStart.value }) : null))
const empty = computed(() => !!overall.value && overall.value.totalSeconds === 0)
// Läuft ein Spiel, zählt die Uhr minütlich mit.
let ticker: ReturnType<typeof setInterval> | undefined
watch(
  () => summary.value?.live,
  (live) => {
    clearInterval(ticker)
    if (live) ticker = setInterval(() => (now.value = new Date()), 60_000)
  },
)
onBeforeUnmount(() => clearInterval(ticker))

/** Name einer Instanz – gelöschte mit dem zuletzt bekannten Namen. */
function instanceName(id: string): { name: string; deleted: boolean } {
  const current = instances.items.find((i) => i.id === id)
  if (current) return { name: current.name, deleted: false }
  return { name: data.value?.names[id] ?? id, deleted: true }
}
/** Instanzen mit Spielzeit für die Auswahl oben (gelöschte am Ende). */
const choices = computed(() =>
  (overall.value?.instances ?? [])
    .map((r) => ({ id: r.key, ...instanceName(r.key) }))
    .sort((a, b) => Number(a.deleted) - Number(b.deleted) || compareText(a.name, b.name)),
)
const filterName = computed(() => (instanceId.value ? instanceName(instanceId.value).name : null))

const bars = computed(() => {
  const s = summary.value
  if (!s) return []
  return range.value === 'days' ? lastDayBars(s.days, 30, now.value) : lastWeekBars(s.days, 16, weekStart.value, now.value)
})

const sessionsText = (n: number) => t('stats.top.sessions', n)
const instanceRows = computed<RankRow[]>(() =>
  (summary.value?.instances ?? []).map((r) => {
    const info = instanceName(r.key)
    return { key: r.key, label: info.deleted ? t('stats.top.deleted', { name: info.name }) : info.name, seconds: r.seconds, share: r.share, detail: sessionsText(r.sessions), muted: info.deleted }
  }),
)
const versionRows = computed<RankRow[]>(() =>
  (summary.value?.versions ?? []).map((r) => ({ key: r.key, label: r.key, seconds: r.seconds, share: r.share, detail: sessionsText(r.sessions) })),
)
const serverRows = computed<RankRow[]>(() =>
  (summary.value?.servers ?? []).map((r) => ({ key: r.key, label: r.key, seconds: r.seconds, share: r.share, detail: sessionsText(r.sessions) })),
)
const loaderLine = computed(() =>
  (summary.value?.loaders ?? [])
    .slice(0, 4)
    .map((r) => `${loaderLabels[r.key as keyof typeof loaderLabels] ?? r.key} ${formatNumber(r.share * 100)} %`)
    .join(' · '),
)

const dateFormat = computed(() => new Intl.DateTimeFormat(intlLocale(), { dateStyle: 'medium' }))
const monthName = computed(() => new Intl.DateTimeFormat(intlLocale(), { month: 'long' }).format(now.value))
const since = computed(() => {
  const first = summary.value?.firstDay
  if (!first) return null
  const [y, m, d] = first.split('-').map(Number) as [number, number, number]
  return dateFormat.value.format(new Date(y, m - 1, d))
})
const longestHint = computed(() => {
  const l = summary.value?.longest
  if (!l) return ''
  const when = dateFormat.value.format(new Date(l.start * 1000))
  return instanceId.value ? when : `${when} · ${instanceName(l.instanceId).name}`
})
const subtitle = computed(() => (filterName.value ? t('stats.subtitleInstance', { name: filterName.value }) : t('stats.subtitle')))

async function reset() {
  confirmReset.value = false
  resetting.value = true
  try {
    await backend.resetPlayStats()
    toasts.ok(t('stats.reset.done'))
    await load()
  } catch (e) {
    toasts.error(e)
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col p-6 mobile:h-auto mobile:min-h-full mobile:p-4">
    <PullToRefresh :refresh="load" />
    <PageHeader :title="t('stats.title')" :subtitle="subtitle">
      <select v-if="choices.length > 1" v-model="filter" class="field h-9 w-56 py-1 mobile:w-48" :aria-label="t('stats.filterLabel')" data-testid="stats-filter">
        <option value="all">{{ t('stats.allInstances') }}</option>
        <option v-for="c in choices" :key="c.id" :value="c.id">{{ c.deleted ? t('stats.top.deleted', { name: c.name }) : c.name }}</option>
      </select>
      <button v-if="!mobile" class="btn-icon" :title="t('common.actions.refresh')" :aria-label="t('common.actions.refresh')" @click="load">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path :d="icons.sync" /></svg>
      </button>
    </PageHeader>

    <p v-if="error" role="alert" class="card border-danger-600/50 px-4 py-3 text-sm text-danger-300">{{ error }}</p>

    <div v-else-if="loading" class="space-y-3">
      <div class="grid grid-cols-[repeat(auto-fill,minmax(12rem,1fr))] gap-3">
        <div v-for="i in 6" :key="i" class="skeleton h-28" />
      </div>
      <div class="skeleton h-64" />
    </div>

    <EmptyState v-else-if="empty || !summary" :seed="0x5a" :title="t('stats.empty.title')" :text="t('stats.empty.text')" data-testid="stats-empty">
      <NuxtLink to="/instances" class="btn btn-primary">{{ t('stats.empty.action') }}</NuxtLink>
    </EmptyState>

    <div v-else class="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1 pb-4 mobile:overflow-visible mobile:pr-0">
      <!-- Kennzahlen -->
      <section v-stagger class="grid grid-cols-[repeat(auto-fill,minmax(17rem,1fr))] gap-3 mobile:grid-cols-2 mobile:gap-2" :aria-label="t('stats.tiles.label')">
        <StatsTile
          icon="behavior"
          testid="stats-total"
          :label="t('stats.tiles.total')"
          :value="formatPlayTime(summary.totalSeconds)"
          :hint="summary.carriedSeconds ? t('stats.tiles.totalCarried', { time: formatPlayTime(summary.carriedSeconds) }) : since ? t('stats.tiles.since', { date: since }) : ''"
        />
        <StatsTile icon="calendar" :label="t('stats.tiles.week')" :value="formatPlayTime(summary.weekSeconds)" :hint="t('stats.tiles.today', { time: formatPlayTime(summary.todaySeconds) })" />
        <StatsTile icon="chart" :label="t('stats.tiles.month')" :value="formatPlayTime(summary.monthSeconds)" :hint="monthName" />
        <StatsTile
          icon="list"
          :label="t('stats.tiles.sessions')"
          :value="formatNumber(summary.sessionCount)"
          :hint="summary.sessionCount ? t('stats.tiles.average', { time: formatPlayTime(summary.averageSeconds) }) : ''"
        />
        <StatsTile icon="trophy" :label="t('stats.tiles.longest')" :value="summary.longest ? formatPlayTime(summary.longest.seconds) : '–'" :hint="longestHint" />
        <StatsTile
          icon="flame"
          testid="stats-streak"
          :label="t('stats.tiles.streak')"
          :value="t('stats.tiles.days', summary.currentStreak)"
          :hint="t('stats.tiles.bestStreak', { days: t('stats.tiles.days', summary.longestStreak) })"
        />
      </section>

      <!-- Spielzeit je Tag / Woche -->
      <section class="card p-5 mobile:p-4" :aria-label="t('stats.chart.title')">
        <header class="mb-3 flex flex-wrap items-center gap-3">
          <h2 class="heading text-base">{{ t('stats.chart.title') }}</h2>
          <span v-if="summary.live" class="badge bg-live-900 text-live-300">
            <span class="size-1.5 animate-live rounded-full bg-live-400" aria-hidden="true" />{{ t('stats.live') }}
          </span>
          <div class="ml-auto flex overflow-hidden rounded-lg border border-line text-xs" role="group" :aria-label="t('stats.chart.rangeLabel')">
            <button type="button" class="seg" :class="{ 'seg-on': range === 'days' }" :aria-pressed="range === 'days'" @click="range = 'days'">{{ t('stats.chart.days') }}</button>
            <button type="button" class="seg" :class="{ 'seg-on': range === 'weeks' }" :aria-pressed="range === 'weeks'" @click="range = 'weeks'">{{ t('stats.chart.weeks') }}</button>
          </div>
        </header>
        <StatsBars :key="range" :bars="bars" :mode="range" :label="range === 'days' ? t('stats.chart.labelDays') : t('stats.chart.labelWeeks')" />
      </section>

      <!-- Kalender -->
      <section class="card p-5 mobile:p-4" :aria-label="t('stats.heatmap.title')">
        <header class="mb-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <h2 class="heading text-base">{{ t('stats.heatmap.title') }}</h2>
          <span class="text-xs text-base-400">{{ t('stats.heatmap.daysPlayed', summary.daysPlayed) }}</span>
        </header>
        <StatsHeatmap :days="summary.days" :week-start="weekStart" :now="now" :label="t('stats.heatmap.title')" />
      </section>

      <!-- Bestenlisten -->
      <div class="grid gap-4 lg:grid-cols-2">
        <section v-if="!instanceId" class="card p-5 mobile:p-4" :aria-label="t('stats.top.instances')">
          <h2 class="heading mb-4 text-base">{{ t('stats.top.instances') }}</h2>
          <StatsRankList :rows="instanceRows" :empty="t('stats.chart.none')">
            <template #lead="{ row }">
              <InstanceIcon v-if="instances.items.find((i) => i.id === row.key)" :instance="instances.items.find((i) => i.id === row.key)!" :size="32" class="shrink-0" />
              <span v-else class="grid size-8 shrink-0 place-items-center rounded-lg bg-base-800 text-xs text-base-400" aria-hidden="true">{{ row.label.charAt(0).toUpperCase() }}</span>
            </template>
          </StatsRankList>
        </section>
        <section class="card p-5 mobile:p-4" :aria-label="t('stats.top.versions')">
          <h2 class="heading mb-1 text-base">{{ t('stats.top.versions') }}</h2>
          <p class="mb-4 text-xs text-base-400">{{ loaderLine || t('stats.top.versionsHint') }}</p>
          <StatsRankList :rows="versionRows" :empty="t('stats.top.noVersions')" />
        </section>
        <section v-if="serverRows.length" class="card p-5 mobile:p-4" :aria-label="t('stats.top.servers')">
          <h2 class="heading mb-1 text-base">{{ t('stats.top.servers') }}</h2>
          <p class="mb-4 text-xs text-base-400">{{ t('stats.top.serversHint') }}</p>
          <StatsRankList :rows="serverRows" />
        </section>
      </div>

      <footer class="flex flex-wrap items-center gap-x-4 gap-y-2 px-1 text-xs text-base-400">
        <span v-if="data?.trackingSince">{{ t('stats.footer.tracking', { date: dateFormat.format(new Date(data.trackingSince * 1000)) }) }}</span>
        <span v-if="summary.carriedSeconds">{{ t('stats.footer.carried') }}</span>
        <button type="button" class="ml-auto underline-offset-2 hover:text-danger-300 hover:underline disabled:opacity-50" :disabled="resetting" data-testid="stats-reset" @click="confirmReset = true">
          {{ t('stats.reset.button') }}
        </button>
      </footer>
    </div>

    <BaseDialog v-if="confirmReset" :title="t('stats.reset.title')" @close="confirmReset = false">
      <p class="text-sm text-base-200">{{ t('stats.reset.text') }}</p>
      <template #actions>
        <button class="btn btn-ghost" @click="confirmReset = false">{{ t('common.actions.cancel') }}</button>
        <button class="btn btn-danger" @click="reset">{{ t('stats.reset.confirm') }}</button>
      </template>
    </BaseDialog>
  </div>
</template>
