<script setup lang="ts">
// Hinweis bei der Anmeldung (Konten-Seite, Einrichtung): Spielen geht auch über den offiziellen Minecraft
// Launcher – Cubiq bereitet die Instanz vor, angemeldet und gespielt wird dort.
const emit = defineEmits<{ chosen: [] }>()
const official = useOfficialLauncherStore()
const busy = ref(false)

onMounted(() => {
  if (!official.status) void official.load()
})

async function choose() {
  // „Automatisch“ reicht: Ohne Konto übernimmt ohnehin der offizielle Launcher. Nur „Immer Cubiq“ wird zurückgestellt.
  if (official.playWith === 'cubiq') {
    busy.value = true
    try {
      await official.setPlayWith('auto')
    } catch (e) {
      useToasts().error(e)
      return
    } finally {
      busy.value = false
    }
  }
  emit('chosen')
}
</script>

<template>
  <div v-if="official.supported" class="card flex items-start gap-3 p-4 text-left" data-testid="official-hint">
    <span class="grid size-9 shrink-0 place-items-center rounded-lg bg-primary-900 text-primary-300" aria-hidden="true">
      <svg viewBox="0 0 24 24" class="ml-0.5 size-4" fill="currentColor"><path d="M7.5 4.6v14.8a1 1 0 0 0 1.5.86l12.2-7.4a1 1 0 0 0 0-1.72L9 3.74a1 1 0 0 0-1.5.86Z" /></svg>
    </span>
    <div class="min-w-0 flex-1">
      <p class="text-sm font-semibold text-base-50">{{ t('officialLauncher.hint.title') }}</p>
      <p class="mt-0.5 text-xs leading-relaxed text-base-400">{{ t('officialLauncher.hint.text') }}</p>
      <button class="btn btn-ghost mt-3 py-1.5 text-xs" :disabled="busy" data-testid="official-hint-use" @click="choose">{{ t('officialLauncher.hint.action') }}</button>
    </div>
  </div>
</template>
