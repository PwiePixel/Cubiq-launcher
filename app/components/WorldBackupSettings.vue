<script setup lang="ts">
import type { BackupUsage, WorldBackupSettings } from '~/utils/worldBackups'

// Einstellungen → Welt-Backups: an/aus, wie viele automatische Backups je Welt bleiben, eine
// Obergrenze für alle zusammen und der belegte Platz (mit Ordner und Aufräumen gelöschter Instanzen).
const model = defineModel<WorldBackupSettings | undefined>({ required: true })
const toasts = useToasts()
const mobile = mobileUi

// Ältere Kerne kennen das Feld nicht: dann mit den Standardwerten anlegen.
watch(
  model,
  (value) => {
    if (!value) model.value = { enabled: true, keep: 5, maxTotalGb: 0 }
  },
  { immediate: true },
)
const value = computed(() => model.value ?? { enabled: true, keep: 5, maxTotalGb: 0 })
function set<K extends keyof WorldBackupSettings>(key: K, v: WorldBackupSettings[K]) {
  model.value = { ...value.value, [key]: v }
}

const KEEP = [1, 2, 3, 5, 10, 20, 30, 50]
const LIMITS = [0, 5, 10, 20, 50, 100, 200, 500]

const usage = ref<BackupUsage | null>(null)
const confirmPrune = ref(false)
async function loadUsage() {
  try {
    usage.value = await backend.worldBackupUsage()
  } catch {
    usage.value = null
  }
}
onMounted(loadUsage)
async function prune() {
  confirmPrune.value = false
  try {
    const freed = await backend.pruneWorldBackups()
    toasts.ok(t('worldBackups.settings.pruned', { size: formatBytes(freed) }))
    await loadUsage()
  } catch (e) {
    toasts.error(e)
  }
}
function openFolder() {
  backend.openWorldBackupRoot().catch((e) => toasts.error(e))
}
</script>

<template>
  <div>
    <h3 class="section-heading">{{ t('worldBackups.settings.title') }}</h3>
    <p class="mb-1 text-xs leading-relaxed text-base-400">{{ t('worldBackups.settings.intro') }}</p>
    <SettingRow :title="t('worldBackups.settings.enabledTitle')" :description="t('worldBackups.settings.enabledDescription')">
      <ToggleSwitch :model-value="value.enabled" :label="t('worldBackups.settings.enabledTitle')" data-testid="world-backups-enabled" @update:model-value="(on: boolean) => set('enabled', on)" />
    </SettingRow>
    <SettingRow :title="t('worldBackups.settings.keepTitle')" :description="t('worldBackups.settings.keepDescription')">
      <select :value="value.keep" class="field w-40 py-1.5" :aria-label="t('worldBackups.settings.keepTitle')" @change="set('keep', Number(($event.target as HTMLSelectElement).value))">
        <option v-for="n in KEEP" :key="n" :value="n">{{ t('worldBackups.settings.keepOption', n) }}</option>
      </select>
    </SettingRow>
    <SettingRow :title="t('worldBackups.settings.limitTitle')" :description="t('worldBackups.settings.limitDescription')">
      <select :value="value.maxTotalGb" class="field w-40 py-1.5" :aria-label="t('worldBackups.settings.limitTitle')" @change="set('maxTotalGb', Number(($event.target as HTMLSelectElement).value))">
        <option v-for="gb in LIMITS" :key="gb" :value="gb">{{ gb ? formatBytes(gb * 1_073_741_824) : t('worldBackups.settings.noLimit') }}</option>
      </select>
    </SettingRow>

    <h3 class="section-heading mt-6">{{ t('worldBackups.settings.storageTitle') }}</h3>
    <div v-if="!usage" class="skeleton h-20" />
    <div v-else class="rounded-xl border border-base-800 bg-base-850 p-4">
      <p class="text-sm text-base-50">
        {{ usage.count ? t('worldBackups.settings.usage', { count: usage.count, worlds: usage.worlds, size: formatBytes(usage.bytes) }) : t('worldBackups.settings.usageNone') }}
      </p>
      <p class="mt-1 text-xs text-base-400">{{ t('worldBackups.settings.where') }}</p>
      <div class="mt-3 flex flex-wrap items-center gap-2">
        <button v-if="!mobile" type="button" class="btn btn-ghost py-1.5 text-xs" @click="openFolder">{{ t('common.actions.openFolder') }}</button>
        <template v-if="usage.orphanedBytes > 0">
          <span class="text-xs text-base-400">{{ t('worldBackups.settings.orphaned', { size: formatBytes(usage.orphanedBytes) }) }}</span>
          <button v-if="!confirmPrune" type="button" class="btn btn-danger py-1.5 text-xs" @click="confirmPrune = true">{{ t('worldBackups.settings.prune') }}</button>
          <template v-else>
            <button type="button" class="btn btn-ghost py-1.5 text-xs" @click="confirmPrune = false">{{ t('common.actions.cancel') }}</button>
            <button type="button" class="btn btn-danger py-1.5 text-xs" @click="prune">{{ t('worldBackups.settings.pruneConfirm') }}</button>
          </template>
        </template>
      </div>
    </div>
  </div>
</template>
