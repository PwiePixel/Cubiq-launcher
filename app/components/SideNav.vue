<script setup lang="ts">
import { messeRestricted } from '~/utils/messe'
import { motion } from 'motion-v'
import type { IconName } from '~/utils/icons'
import type { MessageKey } from '~/utils/i18n'

// Dock links im Fensterrahmen: oben Logo und Name (zugleich Ziehbereich des Fensters), darunter die
// Bereiche in beschrifteten Gruppen, dann „Schnellstart“ mit den zuletzt gespielten Instanzen und
// unten die Konto-Kachel mit den Einstellungen. Der gewählte Bereich ist eine grüne Fläche, die beim
// Wechsel zum neuen Eintrag gleitet. Eingeklappt bleiben nur Symbole (mit Kurzhinweisen).
interface NavItem {
  to: string
  label: MessageKey
  icon: IconName
  /** Seiten, die ein anderer Bereich baut – fehlen sie noch, bleibt der Punkt ruhig stehen. */
  optional?: boolean
  /** Nur für das Cubiq-Team (Admins und Moderatoren). */
  admin?: boolean
  /** Nur wo es Spiel-Clips gibt (derzeit Windows). */
  clipsOnly?: boolean
  /** Nur, wenn es lokale Server gibt. */
  localServers?: boolean
  /** Nur in der mobilen App (und beim Entwickeln). */
  touchOnly?: boolean
  /** Nur am Handy (PC-Fernbedienung). */
  phoneOnly?: boolean
  /** Nur mit eingeschalteten Cubiq-Diensten (Creator-Programm). */
  cubiqOnly?: boolean
  /** Nur, wenn der Build einen Cubiq-Server kennt (Freunde, Chat). */
  servicesOnly?: boolean
}

interface NavGroup {
  /** Überschrift der Gruppe; die erste Gruppe (Hauptbereiche) hat keine. */
  label: MessageKey | null
  items: NavItem[]
}

const groups: NavGroup[] = [
  {
    label: null,
    items: [
      { to: '/', label: 'nav.home', icon: 'home' },
      { to: '/instances', label: 'nav.library', icon: 'library' },
      { to: '/browse', label: 'nav.discover', icon: 'compass' },
      { to: '/presets', label: 'nav.presets', icon: 'presets' },
    ],
  },
  {
    label: 'nav.groups.together',
    items: [
      { to: '/servers', label: 'nav.servers', icon: 'server' },
      { to: '/local-servers', label: 'nav.localServers', icon: 'terminal', localServers: true },
      { to: '/social', label: 'nav.social', icon: 'chat', servicesOnly: true },
      { to: '/creator', label: 'nav.creator', icon: 'star', cubiqOnly: true },
    ],
  },
  {
    label: 'nav.groups.collection',
    items: [
      { to: '/skins', label: 'nav.skins', icon: 'skins', optional: true },
      { to: '/screenshots', label: 'nav.screenshots', icon: 'screenshots', optional: true },
      { to: '/clips', label: 'nav.clips', icon: 'clips', optional: true, clipsOnly: true },
    ],
  },
  {
    label: 'nav.groups.tools',
    items: [
      { to: '/controls', label: 'nav.controls', icon: 'touch', touchOnly: true },
      { to: '/pc', label: 'nav.pc', icon: 'monitor', phoneOnly: true },
      { to: '/admin', label: 'nav.admin', icon: 'admin', admin: true },
    ],
  },
]

const route = useRoute()
const router = useRouter()
const games = useGamesStore()
const instances = useInstancesStore()
const settings = useSettingsStore()
const ui = useUiStore()
const cubiq = useCubiqStore()
const chat = useChatStore()
const localServers = useLocalServersStore()
/** Anfragen + Umhang-Angebote + ungelesene Nachrichten. */
const socialCount = computed(() => cubiq.incomingCount + chat.unreadTotal)

const uiSettings = computed(() => settings.current?.ui)
const expanded = computed(() => ui.navExpanded)
/** Konto-Kachel im Dock (Einstellung „Konto-Kachel“); sonst sitzt das Konto oben rechts. */
const accountTile = computed(() => uiSettings.value?.sidebarAccount !== false)
// Die Liste ist nach „zuletzt gespielt“ sortiert; laufende Instanzen immer zuerst.
const quick = computed(() => {
  const running = instances.items.filter((i) => games.state(i.id).phase !== 'idle')
  const rest = instances.items.filter((i) => !running.includes(i))
  return [...running, ...rest].slice(0, 5)
})
const showQuick = computed(() => quick.value.length > 0 && uiSettings.value?.sidebarRecent !== false)

function visible(item: NavItem): boolean {
  return (
    // Seiten anderer Bereiche erscheinen erst, wenn es sie wirklich gibt.
    (!item.optional || router.resolve(item.to).matched.length > 0) &&
    (!item.admin || (cubiq.isStaff && !messeRestricted.value)) &&
    (!item.cubiqOnly || (cubiq.enabled && !messeRestricted.value)) &&
    (!item.servicesOnly || cubiq.available) &&
    // Clips (Spielaufnahme) gibt es vorerst nur unter Windows.
    (!item.clipsOnly || platformCaps.value.clips) &&
    // Lokale Server erst, wenn es einen gibt (Server-Export „lokal anlegen“).
    (!item.localServers || localServers.items.length > 0) &&
    // Touch-Steuerung braucht die mobile App; beim Entwickeln zum Testen sichtbar.
    (!item.touchOnly || isTouchApp() || import.meta.dev) &&
    // Die PC-Fernbedienung bedient man vom Handy aus.
    (!item.phoneOnly || detectRemoteRole() === 'phone')
  )
}

/** Gruppen mit ihren sichtbaren Einträgen – leere Gruppen fallen samt Überschrift weg. */
const visibleGroups = computed(() =>
  groups.map((g) => ({ label: g.label, items: g.items.filter(visible) })).filter((g) => g.items.length > 0),
)

function isActive(to: string) {
  return to === '/' ? route.path === '/' : route.path.startsWith(to)
}

function play(id: string) {
  const state = games.state(id)
  if (state.phase === 'idle') games.launch(id)
}
</script>

<template>
  <nav
    class="dock relative z-30 flex shrink-0 flex-col transition-[width] duration-200"
    :class="expanded ? 'dock-wide' : 'dock-narrow'"
    :aria-label="t('nav.mainLabel')"
  >
    <!-- Kopf: Logo und Name – zugleich Ziehbereich des rahmenlosen Fensters. -->
    <div data-tauri-drag-region class="dock-head" :class="expanded ? 'justify-between' : 'justify-center'">
      <NuxtLink to="/" class="dock-brand" :aria-label="t('nav.home')">
        <img src="/logo.png" alt="" class="pointer-events-none size-7 shrink-0" draggable="false" />
        <span v-if="expanded" class="display truncate text-[15px] leading-none text-base-50">Cubiq <span class="font-medium text-base-400">Launcher</span></span>
      </NuxtLink>
      <button
        v-if="expanded"
        class="dock-icon group size-8!"
        :aria-label="t('nav.collapseLabel')"
        :aria-pressed="true"
        :title="t('nav.collapse')"
        @click="ui.toggleNav()"
      >
        <svg viewBox="0 0 24 24" class="size-[1.1rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3.5" y="4.5" width="17" height="15" rx="3.5" />
          <path d="M9.5 4.5v15M15.5 10l-2 2 2 2" />
        </svg>
      </button>
    </div>

    <div class="dock-scroll">
      <div v-for="(g, gi) in visibleGroups" :key="g.label ?? 'main'" class="dock-group">
        <p v-if="g.label && expanded" class="dock-label">{{ t(g.label) }}</p>
        <span v-else-if="gi > 0" class="dock-sep" aria-hidden="true" />
        <NuxtLink
          v-for="item in g.items"
          :key="item.to"
          :to="item.to"
          class="dock-item group"
          :class="{ 'dock-item-on': isActive(item.to) }"
          :aria-label="t(item.label)"
          :aria-current="isActive(item.to) ? 'page' : undefined"
        >
          <!-- Gewählter Bereich: die grüne Fläche gleitet beim Wechsel zum neuen Eintrag. -->
          <motion.span v-if="isActive(item.to)" layout-id="dock-pick" class="dock-pick" :transition="NAV_SIGNAL_TRANSITION" aria-hidden="true" />
          <svg viewBox="0 0 24 24" class="relative size-[1.15rem] shrink-0" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
            <path :d="icons[item.icon]" />
          </svg>
          <span v-if="expanded" class="relative min-w-0 flex-1 truncate">{{ t(item.label) }}</span>
          <span
            v-if="item.to === '/instances' && games.runningCount"
            class="dock-dot animate-live bg-live-400"
            :class="expanded ? 'ml-auto' : 'absolute top-1.5 right-1.5'"
            :title="t('nav.runningCount', games.runningCount)"
          />
          <span
            v-if="item.to === '/local-servers' && localServers.running.length"
            class="dock-dot animate-live bg-ok"
            :class="expanded ? 'ml-auto' : 'absolute top-1.5 right-1.5'"
            :title="t('localServers.runningCount', { count: localServers.running.length })"
          />
          <span
            v-if="item.to === '/social' && socialCount"
            class="dock-count"
            :class="expanded ? 'ml-auto' : 'absolute top-0.5 right-0.5'"
            :title="t('nav.socialBadge', { messages: chat.unreadTotal, requests: cubiq.incomingCount })"
            data-testid="nav-social-badge"
          >
            {{ socialCount > 99 ? '99+' : socialCount }}
          </span>
          <span v-if="!expanded" class="tip" role="tooltip">{{ t(item.label) }}</span>
        </NuxtLink>
      </div>

      <!-- Schnellstart: laufende und zuletzt gespielte Instanzen, am Ende „Neue Instanz“. -->
      <div class="dock-group">
        <p v-if="expanded && showQuick" class="dock-label">{{ t('nav.quickLaunch') }}</p>
        <span v-else-if="showQuick" class="dock-sep" aria-hidden="true" />
        <template v-if="showQuick">
          <div v-for="i in quick" :key="i.id" class="group relative" :class="expanded ? '' : 'flex justify-center'">
            <NuxtLink
              :to="`/instances/${i.id}`"
              class="dock-inst"
              :class="{ 'dock-inst-on': route.path === `/instances/${i.id}` }"
              :aria-label="t('nav.openNamed', { name: i.name })"
            >
              <span class="relative shrink-0">
                <InstanceIcon :instance="i" :size="expanded ? 30 : 34" class="transition-transform duration-150 group-hover:scale-[1.05]" />
                <span v-if="games.state(i.id).phase !== 'idle'" class="absolute -right-0.5 -bottom-0.5 size-2.5 animate-live rounded-full bg-live-400 ring-2 ring-[var(--frame)]" />
              </span>
              <span v-if="expanded" class="min-w-0 flex-1 pr-7">
                <span class="block truncate text-[13px] leading-tight text-base-100">{{ i.name }}</span>
                <span class="block truncate font-mono text-[11px] leading-tight text-base-500">{{ i.gameVersion }}</span>
              </span>
            </NuxtLink>
            <button
              class="dock-play"
              :class="expanded ? 'top-1/2 right-1.5 -translate-y-1/2' : 'right-1.5 bottom-0'"
              :disabled="games.state(i.id).phase !== 'idle'"
              :aria-label="t('play.playNamed', { name: i.name })"
              @click="play(i.id)"
            >
              <svg viewBox="0 0 24 24" class="ml-px size-3" fill="currentColor"><path :d="icons.play" /></svg>
            </button>
            <span v-if="!expanded" class="tip">{{ i.name }} · {{ i.gameVersion }}</span>
          </div>
        </template>
        <button class="dock-new group" :aria-label="t('nav.newInstance')" @click="ui.creating = true">
          <svg viewBox="0 0 24 24" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.plus" /></svg>
          <span v-if="expanded" class="truncate">{{ t('nav.newInstance') }}</span>
          <span v-else class="tip">{{ t('nav.newInstance') }}</span>
        </button>
      </div>
    </div>

    <!-- Fuß: Konto-Kachel, Einstellungen, Ein-/Ausklappen. -->
    <div class="dock-foot" :class="expanded ? 'flex-row items-center' : 'flex-col items-center'">
      <AccountMenu v-if="accountTile" :placement="expanded ? 'dock' : 'dock-compact'" class="min-w-0" :class="expanded ? 'flex-1' : ''" />
      <div v-else-if="expanded" class="min-w-0 flex-1" />
      <button
        class="dock-icon group"
        :class="{ 'dock-icon-on': settings.dialog !== null }"
        :aria-label="t('nav.settings')"
        @click="settings.open()"
      >
        <svg viewBox="0 0 24 24" class="size-[1.15rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.gear" /></svg>
        <span class="tip">{{ t('nav.settings') }}</span>
      </button>
      <button
        v-if="!expanded"
        class="dock-icon group"
        :aria-label="t('nav.expandLabel')"
        :aria-pressed="false"
        @click="ui.toggleNav()"
      >
        <svg viewBox="0 0 24 24" class="size-[1.15rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3.5" y="4.5" width="17" height="15" rx="3.5" />
          <path d="M9.5 4.5v15M13.5 10l2 2-2 2" />
        </svg>
        <span class="tip">{{ t('nav.expand') }}</span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
@reference "~/assets/css/main.css";

/* Das Dock liegt direkt im Fensterrahmen (tiefste Stufe); Inhalte sitzen rechts daneben auf der Leinwand. */
.dock {
  --frame: var(--color-frame);
  @apply h-full py-2;
}
.dock-wide {
  @apply w-60 pr-2 pl-3;
}
.dock-narrow {
  @apply w-[4.75rem] items-center px-2.5;
}
.dock-head {
  @apply mb-3 flex h-10 shrink-0 items-center;
}
.dock-brand {
  @apply flex h-10 min-w-0 items-center gap-2.5 rounded-xl px-1.5 outline-none;
}
.dock-narrow .dock-brand {
  @apply px-0;
}
.dock-scroll {
  @apply -mr-1 flex min-h-0 flex-1 flex-col overflow-x-hidden overflow-y-auto pr-1;
  scrollbar-width: thin;
}
.dock-narrow .dock-scroll {
  @apply mr-0 items-center pr-0;
}
.dock-group {
  @apply flex w-full flex-col gap-0.5;
}
.dock-group + .dock-group {
  @apply mt-3;
}
.dock-narrow .dock-group {
  @apply items-center;
}
.dock-label {
  @apply mb-1 px-2.5 text-[10.5px] font-semibold tracking-[0.12em] text-base-600 uppercase;
}
.dock-sep {
  @apply mx-auto mb-2 block h-px w-6 bg-line-strong;
}
.dock-item {
  @apply relative flex h-9 w-full items-center gap-3 rounded-xl px-2.5 text-[13.5px] font-medium text-base-400 transition-[background-color,color,scale] outline-none hover:bg-base-900 hover:text-base-50 active:scale-[0.98];
}
.dock-narrow .dock-item {
  @apply size-11 justify-center px-0;
}
.dock-item:focus-visible {
  outline: 2px solid var(--color-primary-400);
  outline-offset: 1px;
}
/* Gewählt: eine grüne Fläche wie die Arme des Logos, Schrift und Symbol dunkel darauf. */
.dock-item-on,
.dock-item-on:hover {
  @apply bg-transparent;
  color: var(--color-on-primary);
}
.dock-pick {
  @apply pointer-events-none absolute inset-0 rounded-xl bg-primary-500;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.22),
    inset 0 -1px 0 rgb(0 0 0 / 0.14),
    0 6px 18px -8px var(--glow-primary);
}
.dock-dot {
  @apply relative inline-block size-2 shrink-0 rounded-full;
}
.dock-count {
  @apply relative grid min-w-[1.125rem] place-items-center rounded-[0.4rem] bg-primary-500 px-1 text-[10px] leading-[1.125rem] font-bold text-on-primary;
}
.dock-item-on .dock-count {
  @apply bg-base-950 text-primary-300;
}
.dock-inst {
  @apply flex items-center gap-2.5 rounded-xl p-1 outline-none transition-colors hover:bg-base-900;
}
.dock-wide .dock-inst {
  @apply w-full;
}
.dock-inst-on {
  @apply bg-base-900;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
.dock-play {
  @apply absolute grid size-6 place-items-center rounded-full bg-primary-500 text-on-primary opacity-0 shadow-md transition-[opacity,scale] group-hover:opacity-100 hover:scale-105 focus-visible:opacity-100 disabled:hidden;
}
.dock-new {
  @apply relative mt-1 flex h-9 w-full items-center gap-3 rounded-xl border border-dashed border-line-strong px-2.5 text-[13px] font-medium text-base-400 transition-colors hover:border-primary-500/70 hover:bg-base-900 hover:text-base-50;
}
.dock-narrow .dock-new {
  @apply size-11 justify-center px-0;
}
.dock-foot {
  @apply mt-2 flex shrink-0 gap-1 border-t border-line pt-2;
}
.dock-narrow .dock-foot {
  @apply w-full;
}
.dock-icon {
  @apply relative grid size-9 shrink-0 place-items-center rounded-xl text-base-400 transition-[background-color,color,scale] hover:bg-base-900 hover:text-base-50 active:scale-95;
}
.dock-narrow .dock-icon {
  @apply size-11;
}
.dock-icon-on {
  @apply bg-base-900 text-base-50;
  box-shadow: inset 0 0 0 1px var(--pick-edge);
}
/* Kurzhinweise neben dem eingeklappten Dock (ausgeklappt nur an den Symbol-Knöpfen im Fuß). */
.tip {
  @apply pointer-events-none absolute top-1/2 left-full z-50 ml-2.5 -translate-y-1/2 scale-95 rounded-lg border border-line-strong bg-base-850 px-2 py-1 text-xs font-medium whitespace-nowrap text-base-50 opacity-0 transition-[opacity,transform] duration-150;
  box-shadow: var(--elev-2);
}
.dock-wide .dock-foot .tip {
  @apply top-auto bottom-full left-1/2 mb-2 ml-0 -translate-x-1/2 translate-y-0;
}
.group:hover .tip,
.group:focus-visible .tip,
.group:focus-within .tip {
  @apply scale-100 opacity-100;
}
</style>
