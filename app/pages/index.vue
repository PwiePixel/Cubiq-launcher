<script setup lang="ts">
import type { Component } from 'vue'
import type { Instance, Server } from '~/types'

// Startseite: oben die Bühne – eine Karte mit der gewählten Instanz (ab Werk die zuletzt gespielte),
// ihrem Titelbild, einem großen Spielen-Knopf in Pillenform und darunter dem Regal mit den letzten
// Instanzen: ein Klick holt eine Instanz auf die Bühne, der kleine Knopf startet sie gleich. Darunter
// das neueste Update, empfohlene Modpacks, weitere laufende Spiele und – nebeneinander, sobald Platz
// ist – Neuigkeiten als Karten und die Server.
const instances = useInstancesStore()
const accounts = useAccountsStore()
const servers = useServersStore()
const games = useGamesStore()
const settings = useSettingsStore()
const ui = useUiStore()
const events = useEventsStore()

const addingServer = ref(false)
const ready = ref(false)
const lamp = ref<HTMLElement | null>(null)
// Am Handy (noch) kein Spielstart: Spielen/Beitreten verschwinden, die Instanz bleibt erreichbar.
const canLaunch = computed(() => platformCaps.value.gameLaunch)
const mobile = mobileUi
const news = ref<{ refresh: () => Promise<void> } | null>(null)
const featuredPacks = ref<{ refresh: () => Promise<void> } | null>(null)

/** Handy: „Zum Aktualisieren ziehen“ – Neuigkeiten, empfohlene Modpacks, Instanzen und Server-Status neu laden. */
async function refreshAll() {
  await Promise.allSettled([news.value?.refresh(), featuredPacks.value?.refresh(), instances.load(), servers.load()])
}

// Die Instanzliste ist nach „zuletzt gespielt“ sortiert. Auf der Bühne steht die gewählte Instanz,
// ohne Wahl (oder wenn sie verschwindet) die zuletzt gespielte.
const picked = ref<string | null>(null)
const featured = computed<Instance | null>(() => instances.items.find((i) => i.id === picked.value) ?? instances.items[0] ?? null)
const game = computed(() => (featured.value ? games.state(featured.value.id) : null))
/** Regal: die letzten Instanzen (die gewählte bleibt darin und ist markiert). */
const shelfAll = computed(() => instances.items.slice(0, 10))

// Das Regal bleibt eine Reihe: so viele Kacheln, wie nebeneinander passen.
const shelf = ref<HTMLElement | null>(null)
const shelfWidth = ref(0)
const CHIP_MIN = 200
const GAP = 8
const shownShelf = computed(() => {
  // Handy: alle als wischbare Reihe.
  if (mobile.value) return shelfAll.value
  if (!shelfWidth.value) return shelfAll.value.slice(0, 4)
  const fit = Math.floor((shelfWidth.value + GAP) / (CHIP_MIN + GAP))
  return shelfAll.value.slice(0, Math.max(1, fit))
})
let shelfObserver: ResizeObserver | undefined
watch(shelf, (el, _, onCleanup) => {
  if (!el) return
  shelfObserver = new ResizeObserver(([entry]) => (shelfWidth.value = entry?.contentRect.width ?? 0))
  shelfObserver.observe(el)
  onCleanup(() => shelfObserver?.disconnect())
})
const running = computed(() =>
  instances.items.filter((i) => i.id !== featured.value?.id && games.state(i.id).phase !== 'idle'),
)
const totalSeconds = computed(() => instances.items.reduce((sum, i) => sum + i.totalPlaySeconds, 0))
const showPlayTime = computed(() => settings.current?.ui.showPlayTime !== false)

const sceneMode = computed(() =>
  game.value?.phase === 'running' ? 'running' : game.value?.phase === 'preparing' ? 'starting' : 'idle',
)
const sceneProgress = computed(() =>
  game.value?.progress ? overallPercent(game.value.progress.stage, game.value.progress.percent) : 0,
)

const greeting = computed(() => {
  const hour = new Date().getHours()
  const part = hour < 5 ? 'lateNight' : hour < 11 ? 'morning' : hour < 18 ? 'day' : 'evening'
  const name = accounts.active?.name
  return name ? t(`home.greeting.${part}Named`, { name }) : t(`home.greeting.${part}`)
})

function loaderText(i: Instance): string {
  return i.loader.version ? `${loaderLabels[i.loader.kind]} ${i.loader.version}` : loaderLabels[i.loader.kind]
}

// Der News-Bereich kommt aus einer eigenen Komponente. `import.meta.glob`
// findet eine fehlende Datei einfach nicht, statt den Build scheitern zu lassen.
const newsSources = import.meta.glob('../components/NewsFeed.vue')
const NewsFeed = shallowRef<Component | null>(null)

let pingTimer: ReturnType<typeof setInterval> | undefined
onMounted(async () => {
  const loadNews = Object.values(newsSources)[0]
  if (loadNews) {
    loadNews()
      .then((mod) => (NewsFeed.value = (mod as { default: Component }).default))
      .catch(() => (NewsFeed.value = null))
  }
  await Promise.allSettled([instances.load(), servers.load()])
  ready.value = true
  // Spielerzahlen sollen nicht veralten, solange die Startseite offen ist.
  pingTimer = setInterval(() => servers.refresh(), 30_000)
})
onBeforeUnmount(() => clearInterval(pingTimer))

function join(server: Server) {
  if (featured.value) games.launch(featured.value.id, server.id)
}

function play(instance: Instance) {
  if (games.state(instance.id).phase === 'idle') games.launch(instance.id)
}
</script>

<template>
  <div class="home @container pb-10">
    <PullToRefresh :refresh="refreshAll" />
    <div class="space-y-8 px-7 pt-6 mobile:space-y-6 mobile:px-4 mobile:pt-4">
      <div class="flex items-center justify-between gap-4" :class="{ 'hw-room': events.halloween }">
        <p class="display text-[15px] text-base-300">{{ greeting }}</p>
        <span v-if="game?.phase === 'running'" class="badge bg-live-400 text-base-950">
          <span class="size-1.5 animate-live rounded-full bg-base-950" />{{ t('play.runningNow') }}
        </span>
      </div>

      <!-- Bühne: gewählte Instanz mit Titelbild und Spielen-Knopf, darunter das Regal. -->
      <section
        class="stage relative isolate overflow-hidden"
        :class="`stage-${sceneMode}`"
        :aria-label="featured ? t('home.lastPlayed', { name: featured.name }) : t('nav.home')"
      >
        <!-- Halloween 2026: Event-Karte oben rechts (Kürbis-Suche, Süßes oder Saures). -->
        <HalloweenEventCard v-if="events.halloween" class="hw-card" />

        <div class="stage-top relative">
          <DesignScene :mode="sceneMode" :progress="sceneProgress" :anchor="lamp">
            <div v-if="featured" class="stage-art">
              <InstanceBanner :instance="featured" shade="none" class="size-full" />
            </div>
            <div class="stage-scrim" />
          </DesignScene>

          <div class="relative flex min-h-[15.5rem] flex-col justify-end gap-6 p-7 mobile:min-h-0 mobile:gap-5 mobile:p-4">
            <div v-if="!ready" class="flex items-end gap-6 mobile:flex-wrap mobile:gap-4">
              <div class="skeleton size-24 rounded-2xl mobile:size-16" />
              <div class="flex-1 space-y-3"><div class="skeleton h-4 w-40" /><div class="skeleton h-11 w-96 max-w-full" /><div class="skeleton h-6 w-72 max-w-full" /></div>
            </div>

            <template v-else-if="featured">
              <div class="flex min-w-0 items-end gap-6 mobile:items-center mobile:gap-4">
                <NuxtLink :to="`/instances/${featured.id}`" class="icon-frame squircle shrink-0 outline-none" tabindex="-1" aria-hidden="true">
                  <InstanceIcon :instance="featured" :size="mobile ? 64 : 104" />
                </NuxtLink>
                <div class="min-w-0 flex-1 pb-1 mobile:pb-0">
                  <p class="eyebrow flex items-center gap-2 text-primary-300">
                    <span class="diamond" />
                    <span class="truncate">{{ t('instance.lastPlayed') }} · {{ formatRelative(featured.lastPlayed) }}</span>
                  </p>
                  <NuxtLink
                    :to="`/instances/${featured.id}`"
                    class="stage-title display mt-2 block truncate text-5xl leading-[1.08] text-base-50 transition-colors hover:text-primary-200 mobile:line-clamp-2 mobile:text-3xl mobile:whitespace-normal"
                  >
                    {{ featured.name }}
                  </NuxtLink>
                  <div class="mt-3 flex flex-wrap items-center gap-2 mobile:mt-2">
                    <span class="stage-chip">
                      <span class="size-2 rounded-full" :style="{ background: loaderColors[featured.loader.kind] }" />
                      <span class="font-mono text-base-50">{{ featured.gameVersion }}</span>{{ loaderText(featured) }}
                    </span>
                    <span v-if="showPlayTime && featured.totalPlaySeconds >= 60" class="stage-chip">
                      <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="8" /><path d="M12 8v4l3 2" /></svg>
                      {{ formatPlayTime(featured.totalPlaySeconds) }}
                    </span>
                    <span v-if="featured.group" class="stage-chip">{{ featured.group }}</span>
                  </div>
                </div>
              </div>

              <div ref="lamp" class="flex items-center gap-2.5 mobile:w-full">
                <div class="w-72 mobile:w-auto mobile:flex-1"><PlayButton :instance-id="featured.id" large /></div>
                <NuxtLink
                  v-if="!canLaunch"
                  :to="`/instances/${featured.id}`"
                  class="btn btn-ghost h-12 flex-1 rounded-full px-6 text-base"
                >
                  {{ t('home.openInstance') }}
                  <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
                </NuxtLink>
                <NuxtLink
                  v-else
                  :to="`/instances/${featured.id}`"
                  class="stage-open"
                  :title="t('home.openInstance')"
                  :aria-label="t('home.openInstance')"
                >
                  <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
                </NuxtLink>
              </div>
              <p v-if="game?.error" role="alert" class="-mt-2 text-sm text-danger-300">{{ game.error }}</p>
            </template>

            <!-- Noch keine Instanz: Willkommen mit den Wegen zur ersten. -->
            <div v-else class="flex flex-wrap items-end gap-x-8 gap-y-5">
              <div class="min-w-0 flex-1">
                <p class="eyebrow flex items-center gap-2 text-primary-300"><CubeMark :size="13" />Cubiq Launcher</p>
                <h1 class="display mt-2 text-5xl leading-[1.08] text-base-50 mobile:text-4xl">{{ t('home.empty.title') }}</h1>
                <p class="mt-3 max-w-md text-sm text-base-300">{{ t('home.empty.text') }}</p>
              </div>
              <div ref="lamp" class="flex shrink-0 flex-wrap gap-3 mobile:w-full mobile:[&>*]:flex-1">
                <button class="btn btn-primary h-12 rounded-full px-7 text-base" @click="ui.creating = true">{{ t('home.empty.create') }}</button>
                <NuxtLink :to="{ path: '/browse', query: { kind: 'modpack' } }" class="btn btn-ghost h-12 rounded-full px-7 text-base">{{ t('home.empty.modpacks') }}</NuxtLink>
              </div>
            </div>
          </div>
        </div>

        <!-- Regal: die letzten Instanzen. Klick holt sie auf die Bühne, der Knopf startet sie direkt. -->
        <div v-if="ready && featured" class="shelf">
          <div class="mb-2.5 flex items-center justify-between gap-4 px-1">
            <h2 class="eyebrow">{{ t('home.continue.title') }}</h2>
            <div class="flex items-center gap-4 text-xs text-base-400">
              <span v-if="showPlayTime && totalSeconds >= 60" class="mobile:hidden">{{ t('home.continue.totalPlayed', { time: formatPlayTime(totalSeconds) }) }}</span>
              <NuxtLink to="/instances" class="font-medium hover:text-base-50 mobile:-my-3 mobile:py-3">{{ t('home.continue.toLibrary') }} →</NuxtLink>
            </div>
          </div>
          <ul ref="shelf" class="flex gap-2 mobile:-mx-4 mobile:snap-x mobile:snap-mandatory mobile:overflow-x-auto mobile:px-4 mobile:pb-1 mobile-scroll-x">
            <li v-for="i in shownShelf" :key="i.id" class="shelf-item group">
              <button
                class="shelf-chip"
                :class="{ 'shelf-chip-on': featured.id === i.id, 'shelf-chip-live': games.state(i.id).phase !== 'idle' }"
                :aria-pressed="featured.id === i.id"
                :aria-label="t('home.stage.pick', { name: i.name })"
                @click="picked = i.id"
              >
                <span class="relative shrink-0">
                  <InstanceIcon :instance="i" :size="36" />
                  <span v-if="games.state(i.id).phase !== 'idle'" class="absolute -right-0.5 -bottom-0.5 size-2.5 animate-live rounded-full bg-live-400 ring-2 ring-base-900" />
                </span>
                <span class="min-w-0 flex-1 text-left">
                  <span class="block truncate text-[13px] leading-tight font-semibold text-base-50">{{ i.name }}</span>
                  <span class="block truncate text-[11px] leading-tight text-base-400">
                    <span class="font-mono">{{ i.gameVersion }}</span> {{ loaderLabels[i.loader.kind] }}
                  </span>
                </span>
              </button>
              <button
                v-if="canLaunch"
                class="shelf-play"
                :class="{ 'shelf-play-on': games.state(i.id).phase !== 'idle' }"
                :disabled="games.state(i.id).phase !== 'idle'"
                :aria-label="t('play.playNamed', { name: i.name })"
                :title="t('play.playNamed', { name: i.name })"
                @click="play(i)"
              >
                <svg viewBox="0 0 24 24" class="ml-px size-3.5" fill="currentColor"><path :d="icons.play" /></svg>
              </button>
            </li>
            <li class="shrink-0">
              <button class="shelf-add" :title="t('nav.newInstance')" :aria-label="t('nav.newInstance')" @click="ui.creating = true">
                <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.plus" /></svg>
              </button>
            </li>
          </ul>
        </div>

        <!-- Ohne Instanzen: die Wege zur ersten als Kacheln im Regal. -->
        <div v-else-if="ready" class="shelf">
          <ul class="grid gap-2 @2xl:grid-cols-3">
            <li>
              <button class="shelf-tile" @click="ui.creating = true">
                <span class="add-icon"><svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.plus" /></svg></span>
                <span class="min-w-0 text-left">
                  <span class="block text-sm font-semibold text-base-50">{{ t('nav.newInstance') }}</span>
                  <span class="block text-xs text-base-400">{{ t('home.add.newInstanceHint') }}</span>
                </span>
              </button>
            </li>
            <li>
              <NuxtLink :to="{ path: '/browse', query: { kind: 'modpack' } }" class="shelf-tile">
                <span class="add-icon"><svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.compass" /></svg></span>
                <span class="min-w-0 text-left">
                  <span class="block text-sm font-semibold text-base-50">{{ t('home.add.discoverModpack') }}</span>
                  <span class="block text-xs text-base-400">{{ t('home.add.discoverModpackHint') }}</span>
                </span>
              </NuxtLink>
            </li>
            <li>
              <button class="shelf-tile" @click="ui.importing = true">
                <span class="add-icon"><svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.install" /></svg></span>
                <span class="min-w-0 text-left">
                  <span class="block text-sm font-semibold text-base-50">{{ t('common.actions.import') }}</span>
                  <span class="block text-xs text-base-400">{{ t('home.add.importHint') }}</span>
                </span>
              </button>
            </li>
          </ul>
        </div>
      </section>

      <!-- Während Minecraft startet: „Cubi Hop“ zum Zeitvertreib (schließt, sobald das Spielfenster da ist). -->
      <LaunchMiniGame />

      <!-- Neuestes Update als Karte mit seinem Update-Banner. -->
      <UpdateNewsCard />

      <!-- Empfohlene Modpacks des Cubiq-Teams (Karussell, live von der Cubiq API; leer = unsichtbar). -->
      <FeaturedPacks ref="featuredPacks" />

      <!-- Weitere laufende Spiele (das gewählte zeigt schon die Bühne). -->
      <section v-if="running.length" aria-labelledby="running-heading">
        <h2 id="running-heading" class="heading mb-3 flex items-center gap-2.5"><CubeMark />{{ t('play.runningNow') }}</h2>
        <ul class="grid gap-2 md:grid-cols-2">
          <li v-for="i in running" :key="i.id" class="card flex items-center gap-3 px-3 py-2.5">
            <NuxtLink :to="`/instances/${i.id}`" class="flex min-w-0 flex-1 items-center gap-3">
              <span class="relative">
                <InstanceIcon :instance="i" :size="40" />
                <span class="absolute -right-1 -bottom-1 size-2.5 animate-live rounded-full bg-live-400 ring-2 ring-base-900" />
              </span>
              <span class="min-w-0">
                <span class="block truncate text-sm font-medium">{{ i.name }}</span>
                <span class="block truncate text-xs text-base-400">{{ games.state(i.id).phase === 'preparing' ? t('play.preparing') : t('common.status.running') }}</span>
              </span>
            </NuxtLink>
            <div v-if="canLaunch" class="w-36 shrink-0"><PlayButton :instance-id="i.id" /></div>
          </li>
        </ul>
      </section>

      <!-- Neuigkeiten und Server: nebeneinander, sobald die Leinwand breit genug ist. -->
      <div class="lower">
        <component :is="NewsFeed" v-if="NewsFeed" ref="news" class="@container" />

        <section aria-labelledby="servers-heading">
          <div class="mb-3 flex items-center justify-between gap-4">
            <h2 id="servers-heading" class="heading flex items-center gap-2.5"><CubeMark />{{ t('nav.servers') }}</h2>
            <NuxtLink v-if="servers.items.length" to="/servers" class="text-xs font-medium text-base-400 hover:text-base-50 mobile:-my-3 mobile:py-3">{{ t('home.servers.manageAll') }}</NuxtLink>
          </div>

          <div v-if="!ready" class="servers">
            <div v-for="i in 2" :key="i" class="skeleton h-[74px] rounded-xl" />
          </div>
          <div v-else v-stagger class="servers">
            <ServerCard
              v-for="s in servers.items.slice(0, 4)"
              :key="s.id"
              :server="s"
              compact
              :join-disabled="!featured || game?.phase !== 'idle'"
              :join-hint="featured ? t('home.servers.joinHint', { name: featured.name }) : t('home.servers.joinHintNone')"
              @join="join"
            />
            <button class="add-tile" @click="addingServer = true">
              <span class="add-icon"><svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.plus" /></svg></span>
              <span class="min-w-0 text-left">
                <span class="block text-sm font-semibold text-base-50">{{ t('home.servers.add') }}</span>
                <span class="block text-xs text-base-400">
                  {{ servers.items.length ? t('home.servers.addHint') : t('home.servers.addHintEmpty') }}
                </span>
              </span>
            </button>
          </div>
        </section>
      </div>

      <ServerDialog v-if="addingServer" @close="addingServer = false" />
    </div>
  </div>
</template>

<style scoped>
@reference "~/assets/css/main.css";

/*
 * Bühne: eine große Karte mit weichen Ecken. Oben das Titelbild der Instanz (rechts, läuft nach
 * links dunkel aus) mit dem Schein hinter dem Spielen-Knopf, unten das Regal auf einer etwas
 * tieferen Fläche.
 */
.stage {
  @apply rounded-3xl border border-line bg-base-900;
  box-shadow: var(--edge-light), var(--elev-2);
}
@supports (corner-shape: squircle) {
  .stage {
    corner-shape: squircle;
    border-radius: 2.25rem;
  }
}
.stage-top {
  min-height: 15.5rem;
}
.stage-art {
  position: absolute;
  inset: 0 0 0 30%;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 40%);
  mask-image: linear-gradient(90deg, transparent, #000 40%);
  opacity: 0.9;
}
/* Schleier: links dicht (Text), nach rechts offen für das Titelbild, unten weich ins Regal. */
.stage-scrim {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--color-base-900) 92%, transparent) 0%, color-mix(in srgb, var(--color-base-900) 55%, transparent) 45%, transparent 75%),
    linear-gradient(0deg, color-mix(in srgb, var(--color-base-900) 80%, transparent) 0%, transparent 55%);
}
.stage-title {
  letter-spacing: -0.03em;
}
.stage-chip {
  @apply inline-flex items-center gap-1.5 rounded-lg bg-base-950/70 px-2.5 py-1 text-xs text-base-200 backdrop-blur;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
/* Instanz-Bild in einem weichen Rahmen – wie ein App-Symbol. */
.icon-frame {
  @apply rounded-[1.75rem] p-1.5;
  background: color-mix(in srgb, var(--color-base-950) 70%, transparent);
  box-shadow:
    inset 0 0 0 1px var(--color-line-strong),
    0 18px 40px -14px rgb(var(--shadow-rgb) / 0.8);
  transition: transform 0.15s ease;
}
.icon-frame:hover {
  transform: translateY(-2px);
}
.stage-running .icon-frame {
  box-shadow:
    inset 0 0 0 2px var(--color-live-400),
    0 0 30px -6px var(--color-live-400);
}
.stage-open {
  @apply grid size-14 shrink-0 place-items-center rounded-full bg-base-950/70 text-base-200 backdrop-blur transition-colors hover:bg-base-800 hover:text-base-50;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}

/* Regal unter der Bühne. */
.shelf {
  @apply relative border-t border-line px-5 pt-4 pb-5 mobile:px-4;
  background: color-mix(in srgb, var(--color-base-950) 55%, var(--color-base-900));
}
.shelf-item {
  @apply relative min-w-0 flex-1;
}
.shelf-chip {
  @apply flex h-14 w-full items-center gap-3 rounded-2xl border border-line bg-base-900 pr-11 pl-2 outline-none transition-[border-color,background-color,box-shadow];
}
.shelf-chip:hover {
  @apply border-line-strong bg-base-850;
}
.shelf-chip:focus-visible {
  outline: 2px solid var(--color-primary-400);
  outline-offset: 2px;
}
/* Auf der Bühne: die gewählte Kachel trägt die grüne Auswahl-Kante. */
.shelf-chip-on,
.shelf-chip-on:hover {
  background: var(--pick-bg);
  border-color: transparent;
  box-shadow: inset 0 0 0 1.5px var(--pick-edge);
}
.shelf-chip-live {
  box-shadow: inset 0 0 0 1.5px color-mix(in srgb, var(--color-live-400) 55%, transparent);
}
.shelf-play {
  @apply absolute top-1/2 right-2.5 grid size-8 -translate-y-1/2 place-items-center rounded-full bg-primary-500 opacity-0 transition-[opacity,scale,background-color] group-hover:opacity-100 hover:scale-105 hover:bg-primary-400 focus-visible:opacity-100 disabled:cursor-default;
  color: var(--color-on-primary);
  box-shadow: 0 6px 16px -6px var(--glow-primary);
}
.shelf-chip-on + .shelf-play {
  @apply opacity-100;
}
.shelf-play-on {
  @apply bg-live-400 text-base-950 opacity-100;
}
.shelf-add {
  @apply grid h-14 w-14 place-items-center rounded-2xl border border-dashed border-line-strong text-base-400 transition-colors hover:border-primary-500/70 hover:bg-base-900 hover:text-base-50;
}
.shelf-tile {
  @apply flex h-full w-full items-center gap-3 rounded-2xl border border-dashed border-line-strong p-3 transition-colors hover:border-primary-500/70 hover:bg-base-900;
}

/* Kachel für Neues: gestrichelter Rahmen statt Karte. */
.add-tile {
  @apply flex min-h-[4.6rem] w-full items-center gap-3 rounded-xl border border-dashed border-line-strong bg-base-900/40 px-3 text-left transition-colors hover:border-primary-500/70 hover:bg-base-900;
}
.add-icon {
  @apply grid size-10 shrink-0 place-items-center rounded-xl bg-base-800 text-base-200 transition-colors;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
.add-tile:hover .add-icon,
.shelf-tile:hover .add-icon {
  @apply bg-primary-500;
  color: var(--color-on-primary);
  box-shadow: none;
}

.hw-card {
  position: absolute;
  right: 22px;
  top: 22px;
  z-index: 2;
  width: 296px;
}
.hw-room {
  padding-right: 0;
}
:root.is-mobile .hw-card {
  position: relative;
  right: auto;
  top: auto;
  width: auto;
  margin: 12px 16px 0;
}

/* Neuigkeiten und Server: untereinander, nebeneinander sobald die Leinwand breit genug ist. */
.lower {
  display: grid;
  gap: 2.5rem;
  grid-template-columns: minmax(0, 1fr);
}
.servers {
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr));
}
@container (min-width: 60rem) {
  .lower {
    grid-template-columns: minmax(0, 1fr) minmax(18rem, 22rem);
    gap: 2rem;
  }
  .servers {
    grid-template-columns: minmax(0, 1fr);
  }
}
/* Handy: Bühne so hoch wie ihr Inhalt, Regal als wischbare Reihe fester Kacheln, Server untereinander. */
:root.is-mobile .stage-top {
  min-height: 0;
}
:root.is-mobile .stage-art {
  inset: 0;
  opacity: 0.55;
}
:root.is-mobile .shelf-item {
  flex: 0 0 14rem;
  scroll-snap-align: start;
}
:root.is-mobile .shelf-play {
  opacity: 1;
}
:root.is-mobile .servers {
  grid-template-columns: minmax(0, 1fr);
}
</style>
