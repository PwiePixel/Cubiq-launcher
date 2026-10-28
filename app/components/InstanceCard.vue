<script setup lang="ts">
import { messeRestricted } from '~/utils/messe'
import type { Instance } from '~/types'
import MobileSheet from '~/components/MobileSheet.vue'

// Bibliothekskachel mit Rahmen: das Titelbild eingelassen, darauf Loader und Version; darunter Instanz-Bild,
// Name und „zuletzt gespielt“. Beim Überfahren erscheint der Spielen-Knopf auf dem Bild; laufende Instanzen
// leuchten in hellem Lime.
const props = defineProps<{ instance: Instance; groups: string[]; compact?: boolean; showPlayTime?: boolean }>()
const emit = defineEmits<{ delete: [instance: Instance]; move: [group: string | null]; newGroup: [instance: Instance] }>()

const games = useGamesStore()
const game = computed(() => games.state(props.instance.id))
/** Neue Version des geteilten Modpacks, aus dem die Instanz stammt. */
const packUpdate = computed(() => usePacksStore().updateOf(props.instance.id))
const menu = ref<'main' | 'groups' | null>(null)
/** „Als Server exportieren“ aus dem Menü. */
const exportingServer = ref(false)
// Handy: kein Hover – Menü als Bottom-Sheet (Tippen auf ⋮ oder lange drücken), Spielen nur mit Spielstart.
const mobile = mobileUi
const canLaunch = computed(() => platformCaps.value.gameLaunch)
// „Nochmal starten“ braucht eigene Java-Prozesse – die Spiel-Engine am Handy hat nur einen.
const canLaunchAgain = computed(() => platformCaps.value.gameLaunch && platformCaps.value.java)
const longPress = useLongPress(() => (menu.value = 'main'))

/** Modpack wird noch installiert (Instanz ist schon angelegt): Fortschritt statt „Spielen“. */
const install = computed(() => useTasksStore().installingInstance(props.instance.id))

const percent = computed(() =>
  game.value.progress ? Math.floor(overallPercent(game.value.progress.stage, game.value.progress.percent)) : 0,
)

function play() {
  if (game.value.phase === 'idle') games.launch(props.instance.id)
  else if (game.value.phase === 'running') games.requestStop(props.instance.id)
}

function openFolder() {
  menu.value = null
  backend.openInstanceDir(props.instance.id).catch(() => {})
}

function closeMenu(e: MouseEvent) {
  // Das Sheet am Handy schließt sich selbst (Hintergrund, Wischen, Zurück).
  if (mobile.value) return
  if (!(e.target as HTMLElement | null)?.closest(`[data-card-menu="${props.instance.id}"]`)) menu.value = null
}
onMounted(() => document.addEventListener('mousedown', closeMenu))
onBeforeUnmount(() => document.removeEventListener('mousedown', closeMenu))
</script>

<template>
  <!--
    Kachel mit Rahmen: das Titelbild sitzt eingelassen in der Karte, darauf oben Loader und Version (laufend:
    „Läuft“) und rechts das Menü, unten rechts der Spielen-Knopf beim Überfahren. Darunter Instanz-Bild, Name und
    „zuletzt gespielt“ (bzw. ein Modpack-Update).
  -->
  <article
    class="tile card group relative flex flex-col p-1.5 transition-[border-color,background-color,box-shadow,translate] hover:-translate-y-0.5 active:translate-y-0 hover:border-line-strong hover:bg-base-850"
    :class="{ 'tile-live': game.phase === 'running', 'z-30': menu && !mobile }"
    v-on="longPress"
  >
    <div class="tile-art squircle relative overflow-hidden">
      <NuxtLink
        :to="`/instances/${instance.id}`"
        class="block outline-none"
        :aria-label="instance.name"
      >
        <InstanceBanner :instance="instance" shade="none" class="w-full" :class="compact ? 'h-20' : 'h-28'" />
      </NuxtLink>

      <span v-if="game.phase === 'running'" class="badge absolute top-2 left-2 bg-live-400 text-base-950">
        <span class="size-1.5 animate-live rounded-full bg-base-950" />{{ t('common.status.running') }}
      </span>
      <span v-else class="tile-chip absolute top-2 left-2 max-w-[calc(100%-3rem)]">
        <span class="size-1.5 shrink-0 rounded-full" :style="{ background: loaderColors[instance.loader.kind] }" />
        <span class="truncate">{{ loaderLabels[instance.loader.kind] }} <span class="font-mono text-base-50">{{ instance.gameVersion }}</span></span>
      </span>
      <!-- Spielen beim Überfahren; während der Vorbereitung Prozent, beim Laufen Stopp -->
      <button
        v-if="install"
        class="absolute inset-x-2 bottom-2 rounded-lg bg-base-950/85 px-2.5 py-1.5 text-left backdrop-blur"
        role="progressbar"
        :aria-valuenow="install.percent ?? 0"
        aria-valuemin="0"
        aria-valuemax="100"
        :aria-label="t('instanceCard.installingLabel', { name: instance.name, percent: install.percent ?? 0 })"
        @click="useTasksStore().openPanel(install.key)"
      >
        <div class="flex justify-between gap-2 text-[11px]">
          <span class="truncate text-base-200">{{ install.stage || t('instanceCard.installing') }}</span>
          <span class="display shrink-0 text-primary-300">{{ t('tasks.percent', { percent: install.percent ?? 0 }) }}</span>
        </div>
        <ProgressBar :percent="install.percent ?? 0" :segments="16" class="mt-1" />
      </button>
      <button
        v-else-if="game.phase !== 'preparing' && canLaunch"
        class="tile-play absolute right-2 bottom-2 grid place-items-center rounded-full transition-all"
        :class="[
          compact ? 'size-9' : 'size-10',
          game.phase === 'running'
            ? 'bg-live-400 text-base-950 opacity-100'
            : 'translate-y-1 bg-primary-500 text-on-primary opacity-0 group-hover:translate-y-0 group-hover:opacity-100 focus-visible:translate-y-0 focus-visible:opacity-100 hover:bg-primary-400',
        ]"
        :aria-label="game.phase === 'running' ? t('play.stopNamed', { name: instance.name }) : t('play.playNamed', { name: instance.name })"
        :title="game.phase === 'running' ? t('play.runningStop') : t('common.actions.play')"
        @click="play"
      >
        <svg v-if="game.phase === 'running'" viewBox="0 0 24 24" class="size-4" fill="currentColor"><rect x="7" y="7" width="10" height="10" rx="1.5" /></svg>
        <svg v-else viewBox="0 0 24 24" class="ml-0.5 size-[1.1rem]" fill="currentColor"><path d="M7.5 4.6v14.8a1 1 0 0 0 1.5.86l12.2-7.4a1 1 0 0 0 0-1.72L9 3.74a1 1 0 0 0-1.5.86Z" /></svg>
      </button>
      <div v-else-if="game.phase === 'preparing'" class="absolute inset-x-2 bottom-2 rounded-lg bg-base-950/85 px-2.5 py-1.5 backdrop-blur" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
        <div class="flex justify-between text-[11px]"><span class="text-base-200">{{ t('play.starting') }}</span><span class="display text-primary-300">{{ t('tasks.percent', { percent }) }}</span></div>
        <ProgressBar :percent="percent" :segments="16" class="mt-1" />
      </div>
    </div>

    <div class="flex items-center gap-2.5 px-1 pt-2.5 pb-1">
      <NuxtLink :to="`/instances/${instance.id}`" class="shrink-0 rounded-[0.6rem] outline-none" tabindex="-1" aria-hidden="true">
        <InstanceIcon :instance="instance" :size="compact ? 30 : 38" class="transition-transform duration-150 group-hover:scale-105" />
      </NuxtLink>
      <div class="min-w-0 flex-1">
        <NuxtLink :to="`/instances/${instance.id}`" class="block truncate font-semibold text-base-50 hover:text-primary-300" :class="compact ? 'text-[13px]' : 'text-sm'" :title="instance.name">
          {{ instance.name }}
        </NuxtLink>
        <p v-if="packUpdate && game.phase !== 'running'" class="mt-0.5 truncate text-[11px] font-medium text-live-300" :title="t('packs.update.available', { name: packUpdate.latest.name, version: packUpdate.latest.packVersion, owner: packUpdate.latest.owner.name })">
          {{ t('packs.update.badge') }}
        </p>
        <p v-else class="mt-0.5 truncate text-[11px] text-base-500">
          {{ game.phase === 'running' ? t('play.runningNow') : formatRelative(instance.lastPlayed) }}<template v-if="!compact && showPlayTime && instance.totalPlaySeconds >= 60"> · {{ formatPlayTime(instance.totalPlaySeconds) }}</template>
        </p>
      </div>
    </div>

    <!-- Menü oben rechts auf dem Bild (außerhalb des Bild-Rahmens, sonst schneidet er das Menü ab). -->
    <div class="absolute top-3 right-3" :data-card-menu="instance.id">
      <button class="tile-more" :class="{ 'tile-more-open': menu }" :aria-label="t('instanceCard.actionsFor', { name: instance.name })" :aria-expanded="!!menu" @click="menu = menu ? null : 'main'">
        <svg viewBox="0 0 24 24" class="size-4" fill="currentColor"><circle cx="12" cy="5.5" r="1.7" /><circle cx="12" cy="12" r="1.7" /><circle cx="12" cy="18.5" r="1.7" /></svg>
      </button>
      <component
        :is="mobile ? MobileSheet : 'div'"
        v-if="menu === 'main'"
        v-bind="mobile ? { title: instance.name } : { class: 'menu top-9 right-0', role: 'menu' }"
        @close="menu = null"
      >
        <button v-if="canLaunch" class="menu-item" role="menuitem" :disabled="game.phase !== 'idle' || !!install" @click="menu = null; games.launch(instance.id)">{{ t('common.actions.play') }}</button>
        <button v-if="canLaunchAgain && game.phase === 'running'" class="menu-item" role="menuitem" :disabled="games.extraBusy.has(instance.id)" data-testid="card-play-again" @click="menu = null; games.extraPrompt = instance.id">{{ t('play.again.menu') }}</button>
        <NuxtLink :to="{ path: `/instances/${instance.id}`, query: { settings: 'general' } }" class="menu-item" role="menuitem">{{ t('instanceCard.settings') }}</NuxtLink>
        <button class="menu-item justify-between" role="menuitem" @click="menu = 'groups'">
          {{ t('instanceCard.moveToGroup') }}
          <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m9 5 7 7-7 7" /></svg>
        </button>
        <button v-if="!mobile && !messeRestricted" class="menu-item" role="menuitem" @click="openFolder">{{ t('common.actions.openFolder') }}</button>
        <button v-if="!mobile && !messeRestricted" class="menu-item" role="menuitem" @click="menu = null; exportingServer = true">{{ t('instanceCard.exportServer') }}</button>
        <div v-if="!messeRestricted" class="my-1 border-t border-base-700" />
        <button v-if="!messeRestricted" class="menu-item text-danger-300" role="menuitem" :disabled="game.phase !== 'idle'" @click="menu = null; emit('delete', instance)">{{ t('common.actions.delete') }}</button>
      </component>
      <component
        :is="mobile ? MobileSheet : 'div'"
        v-else-if="menu === 'groups'"
        v-bind="mobile ? { title: t('instanceCard.moveToGroup') } : { class: 'menu top-9 right-0 max-h-72 overflow-y-auto', role: 'menu', 'aria-label': t('instanceCard.moveToGroup') }"
        @close="menu = null"
      >
        <button class="menu-item text-base-400" role="menuitem" @click="menu = 'main'">
          <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2.5"><path d="m15 5-7 7 7 7" /></svg>
          {{ t('common.actions.back') }}
        </button>
        <button v-for="g in groups" :key="g" class="menu-item" role="menuitemradio" :aria-checked="instance.group === g" @click="menu = null; emit('move', g)">
          <span class="size-1.5 rounded-full" :class="instance.group === g ? 'bg-primary-400' : 'bg-transparent'" />{{ g }}
        </button>
        <button v-if="instance.group" class="menu-item" role="menuitem" @click="menu = null; emit('move', null)">{{ t('instanceCard.removeFromGroup') }}</button>
        <div class="my-1 border-t border-base-700" />
        <button class="menu-item" role="menuitem" @click="menu = null; emit('newGroup', instance)">{{ t('instanceCard.newGroup') }}</button>
      </component>
    </div>
    <!-- Außerhalb der Karte, sonst hängt der Dialog an ihrem Hover-Effekt. -->
    <Teleport to="body">
      <ServerExportDialog v-if="exportingServer" :instance="instance" @close="exportingServer = false" />
    </Teleport>
  </article>
</template>

<style scoped>
@reference "~/assets/css/main.css";

/* Das Titelbild liegt mit etwas Rand in der Karte – wie ein Bild im Rahmen, mit eigener Rundung. */
.tile-art {
  border-radius: calc(var(--radius-xl) - 0.375rem);
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 0.04);
}
/* Loader und Version auf dem Bild: kleines dunkles Schild mit feiner Kante. */
.tile-chip {
  @apply inline-flex items-center gap-1.5 rounded-lg bg-base-950/75 px-2 py-0.5 text-[11px] leading-4 text-base-200 backdrop-blur;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
.tile-play {
  box-shadow: 0 8px 20px -8px rgb(var(--shadow-rgb) / 0.9);
}
/* Menü-Knopf auf dem Bild: erscheint beim Überfahren (am Handy immer), dunkles Schild wie der Loader-Chip. */
.tile-more {
  @apply grid size-7 place-items-center rounded-lg bg-base-950/75 text-base-200 opacity-0 backdrop-blur transition-[opacity,background-color,color] group-hover:opacity-100 hover:bg-base-800 hover:text-base-50 focus-visible:opacity-100;
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
.tile-more-open {
  @apply bg-base-800 text-base-50 opacity-100;
}
/* Handy: kein Hover – immer sichtbar, mit großer Trefferfläche um das kleine Schild. */
:root.is-mobile .tile-more {
  @apply opacity-100;
  outline-offset: 0;
  position: relative;
}
:root.is-mobile .tile-more::before {
  content: "";
  position: absolute;
  inset: -0.5rem;
}
/* Läuft: Kante und Schein in hellem Lime. */
.tile-live {
  border-color: color-mix(in srgb, var(--color-live-400) 50%, transparent);
  box-shadow: var(--edge-light), 0 0 26px -8px var(--color-live-400);
}
/* Fokus auf dem Bild: Ring nach innen, sonst schneidet der Rahmen ihn ab. */
.tile-art > a:focus-visible {
  border-radius: inherit;
  outline-offset: -3px;
}
</style>
