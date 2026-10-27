<script setup lang="ts">
import { motion } from 'motion-v'
import type { MobileNavItem } from '~/utils/mobileNav'

// Handy: schwebende Tab-Leiste unten statt Dock. „Mehr“ öffnet ein Sheet mit den übrigen Seiten.
const route = useRoute()
const router = useRouter()
const cubiq = useCubiqStore()
const chat = useChatStore()
const games = useGamesStore()
const settings = useSettingsStore()
const ui = useUiStore()
const openDocs = useDocs()

const socialCount = computed(() => cubiq.incomingCount + chat.unreadTotal)
const active = computed(() => activeMobileTab(route.path))
const moreOpen = ref(false)
const moreItems = computed(() => mobileMoreItems(platformCaps.value))

// Seitenwechsel schließt das Sheet (auch per Zurück-Taste).
watch(
  () => route.fullPath,
  () => (moreOpen.value = false),
)

function pick(item: MobileNavItem) {
  moreOpen.value = false
  if (item.to) {
    void router.push(item.to)
    return
  }
  if (item.action === 'settings') settings.open()
  else if (item.action === 'search') ui.openPalette()
  else if (item.action === 'create') ui.creating = true
  else if (item.action === 'import') ui.importing = true
  else if (item.action === 'docs') openDocs()
}

function isOn(item: MobileNavItem) {
  return item.to ? route.path === item.to || route.path.startsWith(`${item.to}/`) : false
}
</script>

<template>
  <!-- Schwebende Leiste unten: weiche Pille mit den vier Bereichen und „Mehr“. -->
  <nav
    class="tabbar relative z-30 shrink-0 px-2.5 pt-1.5 pr-[calc(0.625rem+var(--safe-right))] pb-[max(0.5rem,var(--safe-bottom))] pl-[calc(0.625rem+var(--safe-left))]"
    :aria-label="t('nav.mainLabel')"
    data-testid="mobile-tabbar"
  >
    <div class="tabbar-pill flex">
      <NuxtLink
        v-for="tab in mobileTabs"
        :key="tab.to"
        :to="tab.to!"
        class="tabbar-btn"
        :class="{ 'tabbar-on': active === tab.to }"
        :aria-current="active === tab.to ? 'page' : undefined"
      >
        <!-- Aktiver Tab: die grüne Fläche hinter dem Symbol gleitet zum neuen Tab. -->
        <span class="relative grid h-8 w-12 place-items-center">
          <motion.span v-if="active === tab.to" layout-id="tab-signal" class="tab-signal" :transition="NAV_SIGNAL_TRANSITION" aria-hidden="true" />
          <svg viewBox="0 0 24 24" class="relative size-[1.35rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons[tab.icon]" /></svg>
          <span v-if="tab.to === '/instances' && games.runningCount" class="absolute top-0.5 right-2 size-2 animate-live rounded-full bg-live-400" />
          <span
            v-if="tab.to === '/social' && socialCount"
            class="absolute -top-1 -right-0.5 grid min-w-4 place-items-center rounded-[0.4rem] bg-primary-500 px-1 text-[10px] leading-4 font-bold text-on-primary ring-2 ring-base-900"
            data-testid="nav-social-badge"
          >
            {{ socialCount > 99 ? '99+' : socialCount }}
          </span>
        </span>
        <span class="max-w-full truncate">{{ t(tab.label) }}</span>
      </NuxtLink>
      <button class="tabbar-btn" :class="{ 'tabbar-on': active === 'more' || moreOpen }" :aria-expanded="moreOpen" data-testid="mobile-more" @click="moreOpen = !moreOpen">
        <span class="relative grid h-8 w-12 place-items-center">
          <motion.span v-if="active === 'more'" layout-id="tab-signal" class="tab-signal" :transition="NAV_SIGNAL_TRANSITION" aria-hidden="true" />
          <span v-else-if="moreOpen" class="tab-signal" aria-hidden="true" />
          <svg viewBox="0 0 24 24" class="relative size-[1.35rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.menu" /></svg>
        </span>
        <span class="max-w-full truncate">{{ t('mobile.more') }}</span>
      </button>
    </div>
  </nav>

  <MobileSheet v-if="moreOpen" :title="t('mobile.more')" @close="moreOpen = false">
    <div class="grid grid-cols-3 gap-2" data-testid="mobile-more-sheet">
      <button
        v-for="item in moreItems"
        :key="item.to ?? item.action"
        class="flex min-h-20 flex-col items-center justify-center gap-1.5 rounded-2xl border px-1 py-3 text-center text-xs transition-colors active:bg-base-700"
        :class="isOn(item) ? 'more-on' : 'border-line bg-base-900 text-base-200'"
        @click="pick(item)"
      >
        <svg viewBox="0 0 24 24" class="size-6" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons[item.icon]" /></svg>
        <span class="line-clamp-2 leading-tight">{{ t(item.label) }}</span>
      </button>
    </div>
  </MobileSheet>
</template>

<style scoped>
@reference "~/assets/css/main.css";

.tabbar {
  background: linear-gradient(to top, var(--color-base-950) 60%, transparent);
}
.tabbar-pill {
  @apply rounded-[1.375rem] border border-line bg-base-900 p-1;
  box-shadow: var(--edge-light), var(--elev-2);
}
@supports (corner-shape: squircle) {
  .tabbar-pill {
    corner-shape: squircle;
    border-radius: 1.75rem;
  }
}
.tabbar-btn {
  @apply relative flex h-[3.125rem] min-w-0 flex-1 flex-col items-center justify-center gap-0.5 rounded-2xl px-1 text-[11px] font-medium text-base-400 transition-colors active:bg-base-850;
}
.tabbar-on {
  @apply text-base-50;
}
/* Aktiver Tab: eine grüne Fläche hinter dem Symbol, wie die Auswahl im Dock am Desktop. */
.tab-signal {
  @apply pointer-events-none absolute inset-0 rounded-xl bg-primary-500;
  box-shadow: 0 6px 16px -8px var(--glow-primary);
}
.tabbar-on svg {
  color: var(--color-on-primary);
}
.more-on {
  @apply border-transparent text-base-50;
  background: var(--pick-bg);
  box-shadow: inset 0 0 0 1.5px var(--pick-edge);
}
</style>
