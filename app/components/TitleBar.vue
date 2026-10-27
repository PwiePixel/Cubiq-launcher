<script setup lang="ts">
import { MESSE } from '~/utils/messe'
import { isTauri } from '@tauri-apps/api/core'
import { getCurrentWindow } from '@tauri-apps/api/window'

// Obere Leiste über der Leinwand (das Logo sitzt links im Dock). Das Fenster ist rahmenlos
// (`decorations: false`) – Steuerung übernehmen wir selbst: Ziehbereich, Vor/Zurück, die Suche
// in der Mitte, rechts Laufendes, Aufgaben und Hilfe, ganz außen Minimieren, Maximieren, Schließen.
// Doppelklick auf den Ziehbereich maximiert (übernimmt Windows selbst),
// Andocken per Ziehen an den Bildschirmrand und Windows+Pfeil bleibt erhalten.
const win = isTauri() ? getCurrentWindow() : null
const router = useRouter()
const ui = useUiStore()
const settings = useSettingsStore()
const openDocs = useDocs()
const bigPicture = useBigPictureStore()
const maximized = ref(false)
const events = useEventsStore()
/** Ohne Konto-Kachel im Dock sitzt das Konto hier oben. */
const accountHere = computed(() => settings.current?.ui.sidebarAccount === false)

let stop: (() => void) | undefined
onMounted(async () => {
  if (!win) return
  maximized.value = await win.isMaximized().catch(() => false)
  stop = await win.onResized(async () => {
    maximized.value = await win.isMaximized().catch(() => false)
  })
})
onBeforeUnmount(() => stop?.())

async function toggleMaximize() {
  if (!win) return
  await win.toggleMaximize()
  maximized.value = await win.isMaximized().catch(() => false)
}
</script>

<template>
  <header data-tauri-drag-region class="topbar relative z-40 grid h-[var(--chrome-h)] shrink-0 items-center gap-3 pl-1">
    <div data-tauri-drag-region class="flex min-w-0 items-center gap-1">
      <button class="nav-btn" :aria-label="t('common.actions.back')" :title="t('common.actions.back')" @click="router.back()">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 5-7 7 7 7" /></svg>
      </button>
      <button class="nav-btn" :aria-label="t('titleBar.forward')" :title="t('titleBar.forward')" @click="router.forward()">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 5 7 7-7 7" /></svg>
      </button>
      <!-- Halloween 2026: Kürbis und Fledermaus neben den Pfeilen (nur der Look). -->
      <template v-if="events.lookHalloween">
        <HalloweenPixelSprite name="pumpkin" class="pumpkin ml-1" />
        <HalloweenPixelSprite name="moonbat" class="moonbat" />
      </template>
    </div>

    <!-- Suche in der Mitte: öffnet die Befehlspalette (Instanzen, Mods, Seiten, Einstellungen). -->
    <button class="search" data-testid="titlebar-search" @click="ui.openPalette()">
      <svg viewBox="0 0 24 24" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.search" /></svg>
      <span class="min-w-0 flex-1 truncate text-left">{{ t('common.actions.search') }}</span>
      <kbd class="kbd">{{ t('titleBar.ctrl') }} K</kbd>
    </button>

    <div data-tauri-drag-region class="flex min-w-0 items-center justify-end gap-1">
      <!-- Messe-Build: keine Updates (feste Fassung am Stand) -->
      <UpdateButton v-if="!MESSE" />

      <!-- Laufende Instanzen und Hintergrund-Aufgaben. -->
      <RunningPill class="mr-0.5" />
      <TasksButton />

      <!-- Big-Picture-Modus (Fernseher, Steam Deck, Controller). -->
      <button class="nav-btn" :aria-label="t('titleBar.bigPicture')" :title="t('titleBar.bigPicture')" data-testid="titlebar-big-picture" @click="bigPicture.open()">
        <svg viewBox="0 0 24 24" class="size-[1.05rem]" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.gamepad" /></svg>
      </button>

      <!-- Hilfe: Dokumentation auf der Website in der Launcher-Sprache. -->
      <button class="nav-btn" :aria-label="t('docs.open')" :title="t('docs.open')" data-testid="titlebar-docs" @click="openDocs()">
        <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.help" /></svg>
      </button>

      <AccountMenu v-if="accountHere" placement="bar" />

      <!-- Halloween 2026: Spinnennetz neben den Fensterknöpfen, darin versteckt ein Kürbis (Kürbis-Suche 1/13).
           Der Kürbis hängt am Event (Features), das Netz am Look – ohne Event-Design bleibt er auffindbar. -->
      <div v-if="events.halloween" class="web-corner">
        <svg v-if="events.lookHalloween" class="web" viewBox="0 0 46 36" aria-hidden="true"><g fill="none" stroke="#cfd5e3" stroke-width=".6"><path d="M46 0 L14 36 M46 0 L28 36 M46 0 L4 20 M46 0 L0 6" /><path d="M38 0 Q36 8 46 10 M28 0 Q26 14 46 18 M18 0 Q16 20 46 28" /></g></svg>
        <HalloweenPumpkin id="launcher-titlebar" :size="11" class="web-pumpkin" />
      </div>
      <div v-if="win" class="ml-1 flex h-[var(--chrome-h)] self-start">
        <button class="ctl" :aria-label="t('titleBar.minimize')" @click="win.minimize()">
          <svg viewBox="0 0 10 10" class="size-2.5"><path d="M0 5h10" stroke="currentColor" /></svg>
        </button>
        <button class="ctl" :aria-label="maximized ? t('titleBar.restore') : t('titleBar.maximize')" @click="toggleMaximize">
          <svg v-if="maximized" viewBox="0 0 10 10" class="size-2.5" fill="none" stroke="currentColor">
            <rect x=".5" y="2.5" width="7" height="7" rx="1.5" />
            <path d="M2.5 2.5v-1a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-1" />
          </svg>
          <svg v-else viewBox="0 0 10 10" class="size-2.5"><rect x=".5" y=".5" width="9" height="9" rx="2" fill="none" stroke="currentColor" /></svg>
        </button>
        <button class="ctl ctl-close" :aria-label="t('common.actions.close')" @click="win.close()">
          <svg viewBox="0 0 10 10" class="size-2.5"><path d="M0 0l10 10M10 0L0 10" stroke="currentColor" /></svg>
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
@reference "~/assets/css/main.css";

/* Drei Spalten: links Navigation, Mitte die Suche (bleibt mittig), rechts Status und Fenster. */
.topbar {
  grid-template-columns: auto minmax(0, 1fr) auto;
}
.search {
  @apply flex h-8 w-full max-w-md items-center gap-2.5 justify-self-center rounded-xl border border-line bg-base-950 px-3 text-[13px] text-base-500 transition-[border-color,background-color,color] hover:border-line-strong hover:text-base-200;
  box-shadow: inset 0 1px 2px rgb(var(--shadow-rgb) / 0.35);
}
.kbd {
  @apply shrink-0 rounded-md border border-line-strong px-1.5 py-px font-mono text-[10px] text-base-400;
}
.pumpkin {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  pointer-events: none;
  filter: drop-shadow(0 0 4px rgba(224, 138, 60, 0.6));
}
.moonbat {
  width: 18px;
  height: 16px;
  flex-shrink: 0;
  pointer-events: none;
}
.web-corner {
  position: relative;
  width: 46px;
  height: var(--chrome-h);
  flex-shrink: 0;
  align-self: flex-start;
}
.web {
  position: absolute;
  inset: 0;
  width: 46px;
  height: 36px;
  opacity: 0.55;
  pointer-events: none;
}
.web-pumpkin {
  position: absolute;
  right: 9px;
  top: 8px;
}
.ctl {
  @apply flex h-full w-11 items-center justify-center text-base-400 transition-colors hover:bg-base-800 hover:text-base-50;
}
.ctl-close {
  @apply hover:bg-danger-500 hover:text-white;
}
.nav-btn {
  @apply grid size-8 shrink-0 place-items-center rounded-xl text-base-400 transition-colors hover:bg-base-900 hover:text-base-50;
}
</style>
