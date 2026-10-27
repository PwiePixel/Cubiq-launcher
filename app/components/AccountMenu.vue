<script setup lang="ts">
import { messeRestricted } from '~/utils/messe'
// Konto: Skin-Kopf und Name, im Menü alle Accounts zum Wechseln, dazu „Account hinzufügen“ und
// „Accounts verwalten“. Sitzt als Kachel unten im Dock (`dock`, eingeklappt nur der Kopf:
// `dock-compact`) – oder oben rechts in der Leiste (`bar`), wenn die Konto-Kachel aus ist.
const props = withDefaults(defineProps<{ placement?: 'bar' | 'dock' | 'dock-compact' }>(), { placement: 'bar' })
const accounts = useAccountsStore()
const toasts = useToasts()
const router = useRouter()

const open = ref(false)
const switching = ref<string | null>(null)
const root = useTemplateRef<HTMLElement>('root')
const trigger = useTemplateRef<HTMLButtonElement>('trigger')

/** Das Menü öffnet dorthin, wo Platz ist: unter der Leiste, über der Kachel, neben dem eingeklappten Dock. */
const menuPosition = computed(() => {
  if (props.placement === 'bar') return 'top-full right-0 mt-1.5'
  if (props.placement === 'dock') return 'bottom-full left-0 mb-2 origin-bottom'
  return 'bottom-0 left-full ml-2.5 origin-bottom-left'
})

async function activate(id: string) {
  if (accounts.active?.id === id) {
    open.value = false
    return
  }
  switching.value = id
  try {
    await accounts.setActive(id)
    open.value = false
  } catch (e) {
    toasts.error(e)
  } finally {
    switching.value = null
  }
}

function go(path: string, query?: Record<string, string>) {
  open.value = false
  router.push({ path, query })
}

function onDocPointer(e: PointerEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false
}
function onKey(e: KeyboardEvent) {
  if (open.value && e.key === 'Escape') {
    open.value = false
    trigger.value?.focus()
  }
}
onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div ref="root" class="relative" :class="{ 'h-full': placement === 'bar' }">
    <button
      ref="trigger"
      class="account-trigger group"
      :class="[`account-${placement}`, { 'account-open': open }]"
      :aria-expanded="open"
      aria-haspopup="menu"
      :aria-label="accounts.active ? t('nav.accountNamed', { name: accounts.active.name }) : t('nav.signIn')"
      @click="open = !open"
    >
      <span class="relative shrink-0">
        <SkinHead :skin-url="accounts.active?.skinUrl ?? null" :name="accounts.active?.name ?? '?'" :size="placement === 'bar' ? 22 : 30" />
        <span v-if="accounts.active" class="account-online" />
      </span>
      <span v-if="placement === 'bar'" class="max-w-36 truncate">{{ accounts.active?.name ?? t('nav.signIn') }}</span>
      <span v-else-if="placement === 'dock'" class="min-w-0 flex-1 text-left">
        <span class="block truncate text-[13px] leading-tight font-semibold text-base-50">{{ accounts.active?.name ?? t('nav.signIn') }}</span>
        <span class="block truncate text-[11px] leading-tight text-base-500">{{ accounts.active ? t('accountMenu.playingAs') : t('accountMenu.add') }}</span>
      </span>
      <svg
        v-if="placement !== 'dock-compact'"
        viewBox="0 0 24 24"
        class="size-3 shrink-0 text-base-400 transition-transform"
        :class="{ 'rotate-180': placement === 'bar' ? open : !open }"
        fill="none"
        stroke="currentColor"
        stroke-width="2.6"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="m6 9 6 6 6-6" />
      </svg>
      <span v-else class="tip-account">{{ accounts.active?.name ?? t('nav.signIn') }}</span>
    </button>

    <div v-if="open" class="menu w-64 animate-pop" :class="menuPosition" role="menu" :aria-label="t('accountMenu.menuLabel')">
      <p class="px-2.5 pt-1.5 pb-1 text-[11px] text-base-400">{{ t('accountMenu.playingAs') }}</p>
      <template v-if="accounts.items.length">
        <button
          v-for="a in accounts.items"
          :key="a.id"
          class="menu-item gap-3 py-2"
          role="menuitemradio"
          :aria-checked="a.active"
          :disabled="switching !== null"
          @click="activate(a.id)"
        >
          <SkinHead :skin-url="a.skinUrl" :name="a.name" :size="28" />
          <span class="min-w-0 flex-1">
            <span class="block truncate font-medium text-base-50">{{ a.name }}</span>
            <span class="block text-[11px]" :class="a.active ? 'text-ok' : 'text-base-400'">
              {{ a.active ? t('common.status.active') : switching === a.id ? t('accountMenu.switching') : t('accountMenu.switch') }}
            </span>
          </span>
          <span v-if="a.active" class="diamond text-ok" />
        </button>
      </template>
      <p v-else class="px-2.5 pb-2 text-xs text-base-400">{{ t('accountMenu.noAccount') }}</p>

      <div class="my-1 h-px bg-line-strong" />
      <button v-if="!messeRestricted" class="menu-item" role="menuitem" @click="go('/accounts', { add: 'browser' })">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
        {{ t('accountMenu.add') }}
      </button>
      <button v-if="accounts.active" class="menu-item" role="menuitem" data-testid="account-achievements" @click="go('/achievements')">
        <SocialIcon name="trophy" class="size-4" />
        {{ t('nav.achievements') }}
      </button>
      <button class="menu-item" role="menuitem" @click="go('/accounts')">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4" /><path d="M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6" /></svg>
        {{ t('accountMenu.manage') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
@reference "~/assets/css/main.css";

.account-trigger {
  @apply relative flex items-center gap-2 text-xs font-medium text-base-200 transition-colors outline-none hover:text-base-50;
}
.account-trigger:focus-visible {
  outline: 2px solid var(--color-primary-400);
  outline-offset: 1px;
}
/* Oben in der Leiste: schlicht, wie die übrigen Knöpfe dort. */
.account-bar {
  @apply h-8 rounded-xl px-2 hover:bg-base-900;
}
/* Kachel unten im Dock: Kopf, Name und „Du spielst als“. */
.account-dock {
  @apply h-12 w-full gap-2.5 rounded-xl px-1.5 hover:bg-base-900;
}
.account-dock-compact {
  @apply size-11 justify-center rounded-xl hover:bg-base-900;
}
.account-open {
  @apply bg-base-900 text-base-50;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
/* Online-Punkt am Kopf, mit Rand in der Farbe des Rahmens. */
.account-online {
  @apply absolute -right-0.5 -bottom-0.5 size-2 rounded-full bg-ok;
  box-shadow: 0 0 0 2px var(--color-frame);
}
/* Kurzhinweis neben dem eingeklappten Dock. */
.tip-account {
  @apply pointer-events-none absolute top-1/2 left-full z-50 ml-2.5 -translate-y-1/2 scale-95 rounded-lg border border-line-strong bg-base-850 px-2 py-1 text-xs font-medium whitespace-nowrap text-base-50 opacity-0 transition-[opacity,transform] duration-150;
}
.group:hover .tip-account,
.group:focus-visible .tip-account {
  @apply scale-100 opacity-100;
}
</style>
