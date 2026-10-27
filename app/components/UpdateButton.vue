<script setup lang="ts">
// Kleiner Update-Knopf in der Titelleiste: Neustart in die fertig geladene Version.
const updater = useUpdaterStore()
const games = useGamesStore()
onMounted(() => updater.start())

const running = computed(() => games.runningCount > 0)
const label = computed(() => {
  switch (updater.phase) {
    case 'ready':
      return t('updater.restartToUpdate')
    case 'installing':
      return t('updater.installing')
    case 'failed':
      return t('updater.retry')
    case 'external':
      return t('updater.availableExternal', { version: updater.version ?? '' })
    default:
      return ''
  }
})
const title = computed(() => {
  const version = updater.version ?? ''
  if (updater.phase === 'external') return updater.mode === 'portable' ? t('updater.portableHint') : t('updater.externalHint')
  if (updater.phase === 'downloading') return t('updater.downloading', { version, percent: updater.percent })
  if (updater.phase === 'failed') return t('updater.failed', { reason: updater.failReason })
  return running.value ? t('updater.readyRunning', { version }) : t('updater.ready', { version })
})

function openRelease() {
  if (updater.version) backend.openExternalUrl(`https://github.com/PwiePixel/Cubiq-launcher/releases/tag/v${updater.version}`).catch(() => {})
}
</script>

<template>
  <!-- Während des Downloads nur ein dezenter Fortschrittsring. -->
  <span
    v-if="updater.phase === 'downloading'"
    class="mr-1 grid size-6 place-items-center"
    :title="title"
    role="status"
    :aria-label="title"
  >
    <svg viewBox="0 0 20 20" class="size-4 -rotate-90">
      <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" stroke-width="2.5" class="text-base-800" />
      <circle
        cx="10"
        cy="10"
        r="8"
        fill="none"
        stroke="currentColor"
        stroke-width="2.5"
        stroke-linecap="round"
        class="text-primary-500 transition-[stroke-dashoffset] duration-300"
        :stroke-dasharray="50.27"
        :stroke-dashoffset="50.27 * (1 - updater.percent / 100)"
      />
    </svg>
  </span>

  <div v-else-if="label" class="mr-1 flex items-center gap-1.5">
    <button
      v-if="updater.phase === 'failed' && updater.failures > 1"
      class="text-[11px] text-base-400 underline-offset-2 hover:text-base-200 hover:underline"
      @click="openRelease"
    >
      {{ t('updater.downloadInstaller') }}
    </button>
    <button
      class="update-pill"
      :class="{ 'update-pill-failed': updater.phase === 'failed' }"
      :disabled="updater.phase === 'installing'"
      :title="title"
      @click="updater.phase === 'external' ? openRelease() : updater.restart()"
    >
      <svg viewBox="0 0 24 24" class="size-3.5" :class="{ 'animate-spin': updater.phase === 'installing' }" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20 12a8 8 0 1 1-2.34-5.66" />
        <path d="M20 4v5h-5" />
      </svg>
      {{ label }}
    </button>
  </div>

  <!-- Wie der Start-Ladebildschirm: Das Fenster wird nahtlos zur Aktualisierung. -->
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="updater.phase === 'installing'" class="update-splash" role="status" aria-live="polite">
        <img src="/logo.png" alt="" width="80" height="80" />
        <p class="display text-lg text-base-50">Cubiq <span class="text-primary-300">Launcher</span> <span class="font-medium text-base-400">{{ updater.version }}</span></p>
        <p class="text-xs text-base-400">{{ t('updater.splash') }}</p>
        <div class="update-bar"><span /></div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
@reference "~/assets/css/main.css";

.update-pill {
  @apply flex items-center gap-1.5 rounded-md bg-primary-500 px-2.5 py-1 text-[11px] font-semibold text-on-primary shadow-[0_0_12px_-2px_var(--color-primary-500)] transition-colors hover:bg-primary-400 disabled:opacity-70;
}
/* Wie der Ladebildschirm beim Start: Navy mit grünem Schein, Logo und ein ruhig laufender Balken. */
.update-splash {
  @apply fixed inset-0 z-[9999] flex flex-col items-center justify-center gap-3;
  background:
    radial-gradient(circle at 50% 46%, color-mix(in srgb, var(--color-primary-500) 16%, transparent) 0%, transparent 38%),
    var(--color-base-950);
}
.update-splash img {
  filter: drop-shadow(0 10px 28px rgb(var(--shadow-rgb) / 0.6));
}
.update-bar {
  @apply relative mt-1 h-[3px] w-36 overflow-hidden rounded-full bg-base-800;
}
.update-bar span {
  @apply absolute inset-y-0 w-2/5 rounded-full;
  background: linear-gradient(90deg, transparent, var(--color-primary-500), var(--color-primary-300), transparent);
  animation: update-signal 1.2s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}
@keyframes update-signal {
  from { transform: translateX(-100%); }
  to { transform: translateX(250%); }
}
:root[data-reduced-motion] .update-bar span { animation: none; @apply w-full; }
.fade-enter-active { transition: opacity 0.2s ease; }
.fade-enter-from { opacity: 0; }
.update-pill-failed {
  @apply bg-base-800 text-danger-300 shadow-none hover:bg-base-700;
}
</style>
