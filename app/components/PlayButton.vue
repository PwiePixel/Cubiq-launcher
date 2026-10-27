<script setup lang="ts">
// Spielen-Knopf. In groß (Startseite, Instanzseite) eine kräftige Pille in der Akzentfarbe:
// beim Start füllt er sich mit dem Fortschritt, läuft das Spiel, zeigt er „Läuft“ in hellem
// Lime und stoppt auf Klick. In klein ein schlichter Knopf für Listen und Kacheln.
const props = withDefaults(defineProps<{ instanceId: string; large?: boolean }>(), { large: false })

const games = useGamesStore()
const events = useEventsStore()
const game = computed(() => games.state(props.instanceId))

const percent = computed(() =>
  game.value.progress ? Math.floor(overallPercent(game.value.progress.stage, game.value.progress.percent)) : 0,
)
const stage = computed(() => (game.value.progress ? stageLabel(game.value.progress.stage) : ''))
// Die Vorbereitung ist eine Aufgabe – abbrechbar, bis das Spiel startet.
const tasks = useTasksStore()
const canCancel = computed(() => {
  const task = tasks.get(taskKey('launch', props.instanceId))
  return task?.status === 'running' && task.cancellable && !task.cancelling
})
/** Modpack lädt noch Dateien in diese Instanz: statt „Spielen“ der Installationsstand. */
const install = computed(() => (game.value.phase === 'idle' ? tasks.installingInstance(props.instanceId) : null))
const procCount = computed(() => games.processes(props.instanceId).length)
/** Am Handy (noch) kein Spielstart: dann nur der Installationsstand, sonst nichts. */
const hidden = computed(() => !platformCaps.value.gameLaunch && !install.value)
const files = computed(() => {
  const p = game.value.progress
  return p && p.totalFiles > 1 ? `${p.doneFiles} / ${p.totalFiles}` : ''
})
</script>

<template>
  <!-- Groß, während Halloween: Starten und Installieren als Mondlauf statt Füllung. -->
  <template v-if="hidden" />
  <div v-else-if="large && events.lookHalloween && (install || game.phase === 'preparing')" class="relative min-w-0 flex-1">
    <div
      class="rounded-2xl bg-base-900/80 px-4 py-2"
      role="progressbar"
      :aria-valuenow="install ? (install.percent ?? 0) : percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="install ? t('instanceCard.stillInstalling') : stage"
    >
      <div class="mb-1 flex items-baseline justify-between gap-2 text-xs font-medium">
        <span class="min-w-0 truncate">{{ install ? (install.stage || t('instanceCard.installing')) : stage }}<span v-if="!install && files" class="ml-1.5 font-normal opacity-70">{{ files }}</span></span>
        <span class="display shrink-0 tabular-nums text-primary-300">{{ t('tasks.percent', { percent: install ? (install.percent ?? 0) : percent }) }}</span>
      </div>
      <MoonProgress :percent="install ? (install.percent ?? 0) : percent" :indeterminate="!!install && install.percent == null" />
    </div>
    <button
      v-if="game.phase === 'preparing' && canCancel"
      class="cancel"
      :aria-label="t('play.cancelLaunch')"
      :title="t('play.cancelLaunch')"
      @click="games.cancelLaunch(instanceId)"
    >
      <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
    </button>
  </div>

  <!-- Groß -->
  <div v-else-if="large" class="relative min-w-0 flex-1">
    <button
      v-if="install"
      class="play play-busy cursor-progress"
      role="progressbar"
      :aria-valuenow="install.percent ?? 0"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="t('instanceCard.stillInstalling')"
      :title="t('instanceCard.stillInstalling')"
      @click="tasks.openPanel(install.key)"
    >
      <span class="play-fill" :style="{ width: `${install.percent ?? 0}%` }" />
      <span class="relative flex w-full items-baseline justify-between gap-3 px-4">
        <span class="min-w-0 truncate text-left text-xs font-medium">{{ install.stage || t('instanceCard.installing') }}</span>
        <span class="display shrink-0 text-lg tabular-nums">{{ t('tasks.percent', { percent: install.percent ?? 0 }) }}</span>
      </span>
    </button>

    <button v-else-if="game.phase === 'idle'" class="play play-idle" @click="games.launch(instanceId)">
      <span class="play-glyph" aria-hidden="true"><svg viewBox="0 0 24 24" class="ml-0.5 size-4" fill="currentColor"><path d="M7.5 4.6v14.8a1 1 0 0 0 1.5.86l12.2-7.4a1 1 0 0 0 0-1.72L9 3.74a1 1 0 0 0-1.5.86Z" /></svg></span>
      <span class="display text-lg">{{ t('common.actions.play') }}</span>
    </button>

    <div
      v-else-if="game.phase === 'preparing'"
      class="play play-busy cursor-progress"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="stage"
    >
      <span class="play-fill" :style="{ width: `${percent}%` }" />
      <span class="relative flex w-full items-baseline justify-between gap-3 px-4">
        <span class="min-w-0 truncate text-left text-xs font-medium">
          {{ stage }}<span v-if="files" class="ml-1.5 font-normal opacity-70">{{ files }}</span>
        </span>
        <span class="display shrink-0 text-lg tabular-nums">{{ t('tasks.percent', { percent }) }}</span>
      </span>
    </div>

    <button v-else class="play play-running" :title="t('play.stopGame')" @click="games.requestStop(instanceId)">
      <span class="size-2 animate-live rounded-full bg-live-400" aria-hidden="true" />
      <span class="display text-lg">{{ t('common.status.running') }}</span>
      <span class="text-xs font-medium opacity-75">{{ procCount > 1 ? t('play.stopMany', { n: procCount }) : t('common.actions.stop') }}</span>
    </button>
    <!-- Nach der v-if-Kette, sonst hinge der Abbrechen-Knopf an dieser Bedingung. -->
    <button
      v-if="game.phase === 'preparing' && canCancel"
      class="cancel"
      :aria-label="t('play.cancelLaunch')"
      :title="t('play.cancelLaunch')"
      @click="games.cancelLaunch(instanceId)"
    >
      <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
    </button>
  </div>

  <!-- Klein: schlichter Knopf (Listen, Kacheln) -->
  <div v-else class="min-w-0 flex-1">
    <button
      v-if="install"
      class="flex h-9 w-full flex-col justify-center rounded-lg bg-base-800 px-3 text-left"
      role="progressbar"
      :aria-valuenow="install.percent ?? 0"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="t('instanceCard.stillInstalling')"
      @click="tasks.openPanel(install.key)"
    >
      <span class="flex w-full items-baseline justify-between gap-2 text-xs font-medium">
        <span class="truncate">{{ install.stage || t('instanceCard.installing') }}</span>
        <span class="display tabular-nums text-primary-300">{{ t('tasks.percent', { percent: install.percent ?? 0 }) }}</span>
      </span>
      <ProgressBar :percent="install.percent ?? 0" class="mt-1 w-full" />
    </button>
    <button v-else-if="game.phase === 'idle'" class="btn btn-primary w-full" @click="games.launch(instanceId)">
      <svg viewBox="0 0 24 24" class="size-4" fill="currentColor"><path d="M7.5 4.6v14.8a1 1 0 0 0 1.5.86l12.2-7.4a1 1 0 0 0 0-1.72L9 3.74a1 1 0 0 0-1.5.86Z" /></svg>
      {{ t('common.actions.play') }}
    </button>

    <div
      v-else-if="game.phase === 'preparing'"
      class="flex h-9 flex-col justify-center rounded-lg bg-base-800 px-3"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="stage"
    >
      <div class="flex items-baseline justify-between gap-2 text-xs font-medium">
        <span class="truncate">{{ stage }}</span>
        <span class="flex items-center gap-1.5">
          <span class="display tabular-nums text-primary-300">{{ t('tasks.percent', { percent }) }}</span>
          <button
            v-if="canCancel"
            class="grid size-4 place-items-center rounded text-base-400 hover:text-danger-300"
            :aria-label="t('play.cancelLaunch')"
            :title="t('play.cancelLaunch')"
            @click="games.cancelLaunch(instanceId)"
          >
            <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        </span>
      </div>
      <ProgressBar :percent="percent" class="mt-1" />
    </div>

    <button v-else class="btn w-full bg-live-900 text-live-300 ring-1 ring-live-400/40 hover:bg-base-800" @click="games.requestStop(instanceId)">
      <span class="size-2 animate-live rounded-full bg-live-400" />
      {{ t('play.runningStop') }}
    </button>
  </div>
</template>

<style scoped>
/* Groß: eine kräftige Pille in der Akzentfarbe – das eine Ziel auf Bühne und Instanzseite. */
.play {
  position: relative;
  display: flex;
  width: 100%;
  height: 3.5rem;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  overflow: hidden;
  border-radius: 999px;
  transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.1s ease, color 0.2s ease;
}
button.play:active {
  transform: translateY(1px) scale(0.99);
}
.play:focus-visible {
  outline-offset: 3px;
}
.play-idle {
  background: var(--color-primary-500);
  color: var(--color-on-primary);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.24),
    inset 0 -2px 0 rgb(0 0 0 / 0.14),
    0 12px 30px -12px var(--glow-primary);
}
.play-idle:hover {
  background: var(--color-primary-400);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.28),
    inset 0 -2px 0 rgb(0 0 0 / 0.14),
    0 16px 38px -10px var(--glow-primary);
}
/* Das Dreieck sitzt in einem kleinen dunklen Kreis – wie ein Knopf im Knopf. */
.play-glyph {
  display: grid;
  width: 2rem;
  height: 2rem;
  place-items: center;
  border-radius: 999px;
  background: rgb(0 0 0 / 0.16);
}
/* Beim Start und Installieren: dunkle Pille, die sich von links in der Akzentfarbe füllt. */
.play-busy {
  color: var(--color-base-50);
  background: var(--color-base-850);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-primary-500) 45%, transparent);
}
.play-fill {
  position: absolute;
  inset-block: 0;
  left: 0;
  background: linear-gradient(90deg, color-mix(in srgb, var(--color-primary-600) 55%, transparent), color-mix(in srgb, var(--color-primary-400) 45%, transparent));
  transition: width 0.3s ease;
}
/* Läuft: helles Lime, ein Klick stoppt. */
.play-running {
  color: var(--color-live-300);
  background: var(--color-live-900);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--color-live-400) 45%, transparent),
    0 10px 30px -14px var(--color-live-400);
}
.play-running:hover {
  background: color-mix(in srgb, var(--color-live-900) 70%, var(--color-base-800));
}
.cancel {
  position: absolute;
  top: -0.5rem;
  right: -0.5rem;
  z-index: 10;
  display: grid;
  width: 1.5rem;
  height: 1.5rem;
  place-items: center;
  border-radius: 999px;
  background: var(--color-base-850);
  color: var(--color-base-200);
  box-shadow: inset 0 0 0 1px var(--color-line-strong);
}
.cancel:hover {
  color: var(--color-danger-300);
}
</style>
