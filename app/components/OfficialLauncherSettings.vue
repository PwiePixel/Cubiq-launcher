<script setup lang="ts">
import { playWithSchema, type PlayWith } from '~/utils/officialLauncher'

// Einstellungen → Verhalten: Womit „Spielen“ startet (Cubiq, offizieller Minecraft Launcher oder automatisch)
// und die eingetragenen Installationen „Cubiq · …“ im offiziellen Launcher wieder entfernen.
const official = useOfficialLauncherStore()
const toasts = useToasts()
const choices = playWithSchema.options
const saving = ref(false)
const confirmRemove = ref(false)
const removing = ref(false)

onMounted(() => void official.load())

async function choose(value: PlayWith) {
  if (value === official.playWith) return
  saving.value = true
  try {
    await official.setPlayWith(value)
  } catch (e) {
    toasts.error(e)
  } finally {
    saving.value = false
  }
}

async function remove() {
  confirmRemove.value = false
  removing.value = true
  try {
    const removed = await official.removeAll()
    toasts.ok(t('officialLauncher.settings.removed', removed))
  } catch (e) {
    toasts.error(e)
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <template v-if="official.supported">
    <h3 class="section-heading mt-6">{{ t('officialLauncher.settings.title') }}</h3>
    <SettingRow :title="t('officialLauncher.settings.playWithTitle')" :description="t(`officialLauncher.settings.hint.${official.playWith}`)" data-testid="settings-play-with">
      <select
        class="field w-56 py-1.5 mobile:w-40"
        :value="official.playWith"
        :disabled="saving"
        :aria-label="t('officialLauncher.settings.playWithTitle')"
        @change="choose(($event.target as HTMLSelectElement).value as PlayWith)"
      >
        <option v-for="choice in choices" :key="choice" :value="choice">{{ t(`officialLauncher.settings.choice.${choice}`) }}</option>
      </select>
    </SettingRow>
    <SettingRow
      :title="t('officialLauncher.settings.removeTitle')"
      :description="t('officialLauncher.settings.removeDescription', official.status?.entries ?? 0)"
    >
      <button class="btn btn-ghost" :disabled="!official.status?.entries || removing" data-testid="settings-remove-official" @click="confirmRemove = true">
        {{ t('common.actions.remove') }}
      </button>
    </SettingRow>

    <BaseDialog v-if="confirmRemove" :title="t('officialLauncher.settings.removeTitle')" @close="confirmRemove = false">
      <p class="text-sm text-base-200">{{ t('officialLauncher.settings.removeConfirm') }}</p>
      <p v-if="official.status?.running" class="mt-2 text-xs text-caution-300">{{ t('officialLauncher.settings.removeRunning') }}</p>
      <template #actions>
        <button class="btn btn-ghost" @click="confirmRemove = false">{{ t('common.actions.cancel') }}</button>
        <button class="btn btn-danger" @click="remove">{{ t('common.actions.remove') }}</button>
      </template>
    </BaseDialog>
  </template>
</template>
