<script setup lang="ts">
import type { Instance } from '~/types'
import { restoredName, restoreStamp, sortBackups, type BackupInfo, type BackupKind, type RestoreMode } from '~/utils/worldBackups'

// Backups einer Welt: Liste mit Datum, Art und Größe; wiederherstellen als Kopie oder statt der
// jetzigen Welt (die vorher als Sicherheits-Backup gesichert wird), löschen, Ordner öffnen und
// „Jetzt sichern“. Auch für gelöschte Welten, von denen es noch Backups gibt.
const props = defineProps<{ instance: Instance; folder: string; name: string; exists: boolean; autoText: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const toasts = useToasts()
const tasks = useTasksStore()
const games = useGamesStore()
const mobile = mobileUi

const backups = ref<BackupInfo[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
/** Gewähltes Backup zum Wiederherstellen (zweiter Schritt mit Auswahl Kopie/Ersetzen). */
const restoring = ref<BackupInfo | null>(null)
const mode = ref<RestoreMode>('copy')
const confirmDelete = ref<string | null>(null)
const running = computed(() => games.state(props.instance.id).phase !== 'idle')

async function load() {
  loading.value = true
  try {
    backups.value = sortBackups(await backend.worldBackupList(props.instance.id, props.folder))
    error.value = null
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}
onMounted(load)

const KIND_CLASS: Record<BackupKind, string> = {
  auto: 'bg-base-800 text-base-200',
  manual: 'bg-primary-900 text-primary-300',
  safety: 'bg-caution-900 text-caution-300',
  legacy: 'bg-base-800 text-base-400',
}

// --- Jetzt sichern --------------------------------------------------------------
const backupKey = computed(() => taskKey('world-backup', props.instance.id, props.folder))
const backupTask = computed(() => tasks.get(backupKey.value))
async function backupNow() {
  const result = await tasks.run(
    {
      key: backupKey.value,
      kind: 'export',
      title: props.name,
      stage: t('worlds.backingUp'),
      instanceId: props.instance.id,
      cancellable: true,
      doneText: t('worlds.backupDone', { name: props.name }),
    },
    (ctx) => backend.backupWorld(props.instance.id, props.folder, (p) => ctx.progress(p), ctx.taskId),
  )
  if (result.ok) {
    await load()
    emit('changed')
  }
}

// --- Wiederherstellen -------------------------------------------------------------
function startRestore(b: BackupInfo) {
  restoring.value = b
  // Gelöschte Welt: es gibt nichts zu ersetzen – sie kommt unter ihrem Ordner zurück.
  mode.value = props.exists ? 'copy' : 'replace'
}
const copyName = computed(() => (restoring.value ? restoredName(props.name, t('worldBackups.restore.copySuffix'), restoreStamp(restoring.value.created)) : ''))

async function restore() {
  const b = restoring.value
  if (!b) return
  restoring.value = null
  const chosen = mode.value
  const result = await tasks.run(
    {
      key: taskKey('world-restore', props.instance.id, props.folder),
      kind: 'export',
      title: t('worldBackups.restore.taskTitle', { name: props.name }),
      stage: t('worldBackups.restore.stage'),
      instanceId: props.instance.id,
      cancellable: true,
      notify: false,
    },
    (ctx) => backend.restoreWorldBackup(props.instance.id, props.folder, b.id, chosen, chosen === 'copy' ? copyName.value : null, (p) => ctx.progress(p), ctx.taskId),
  )
  if (result.ok) {
    const outcome = result.value
    toasts.ok(
      chosen === 'copy'
        ? t('worldBackups.restore.doneCopy', { name: outcome.folder })
        : outcome.safetyBackup
          ? t('worldBackups.restore.doneReplace', { name: props.name })
          : t('worldBackups.restore.doneDeleted', { name: props.name }),
    )
    await load()
    emit('changed')
  } else if (!result.cancelled) {
    toasts.error(result.error)
  }
}

// --- Löschen und Ordner --------------------------------------------------------------
async function remove(b: BackupInfo) {
  confirmDelete.value = null
  try {
    await backend.deleteWorldBackup(props.instance.id, props.folder, b.id)
    backups.value = backups.value.filter((x) => x.id !== b.id)
    emit('changed')
  } catch (e) {
    toasts.error(e)
  }
}
function openFolder() {
  backend.openWorldBackups(props.instance.id, props.folder).catch((e) => toasts.error(e))
}
</script>

<template>
  <BaseDialog :title="t('worldBackups.dialog.title', { name })" wide @close="emit('close')">
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <p class="min-w-0 flex-1 text-xs text-base-400">{{ autoText }}</p>
      <button v-if="!mobile" type="button" class="btn btn-ghost h-8 px-3 py-0 text-xs" @click="openFolder">
        <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M3 6a1 1 0 0 1 1-1h5l2 2h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" /></svg>
        {{ t('common.actions.openFolder') }}
      </button>
      <button v-if="exists" type="button" class="btn btn-primary h-8 px-3 py-0 text-xs" :disabled="backupTask?.status === 'running'" data-testid="world-backup-now" @click="backupNow">
        <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><path d="M4 7h16v13H4zM2 4h20v3H2zM12 10v6m0 0-2.5-2.5M12 16l2.5-2.5" /></svg>
        {{ backupTask?.status === 'running' ? t('worldBackups.dialog.backingUp', { percent: backupTask.percent ?? 0 }) : t('worldBackups.dialog.backupNow') }}
      </button>
    </div>

    <!-- Zweiter Schritt: Kopie oder Ersetzen -->
    <div v-if="restoring" class="rounded-xl border border-line bg-base-900 p-4" data-testid="world-restore-step">
      <p class="text-sm font-medium text-base-50">{{ t('worldBackups.restore.title', { date: formatDate(restoring.created) }) }}</p>
      <div class="mt-3 grid gap-2" role="radiogroup" :aria-label="t('worldBackups.restore.modeLabel')">
        <button
          v-if="exists"
          type="button"
          role="radio"
          class="choice flex items-start gap-3 p-3 text-left"
          :class="{ 'choice-on': mode === 'copy' }"
          :aria-checked="mode === 'copy'"
          @click="mode = 'copy'"
        >
          <span class="tick mt-0.5" :class="{ 'tick-on': mode === 'copy' }" aria-hidden="true"><svg v-if="mode === 'copy'" viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="3.5"><path :d="icons.check" /></svg></span>
          <span>
            <span class="block text-sm font-medium text-base-50">{{ t('worldBackups.restore.copy') }}</span>
            <span class="block text-xs text-base-400">{{ t('worldBackups.restore.copyText', { name: copyName }) }}</span>
          </span>
        </button>
        <button type="button" role="radio" class="choice flex items-start gap-3 p-3 text-left" :class="{ 'choice-on': mode === 'replace' }" :aria-checked="mode === 'replace'" @click="mode = 'replace'">
          <span class="tick mt-0.5" :class="{ 'tick-on': mode === 'replace' }" aria-hidden="true"><svg v-if="mode === 'replace'" viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="3.5"><path :d="icons.check" /></svg></span>
          <span>
            <span class="block text-sm font-medium text-base-50">{{ exists ? t('worldBackups.restore.replace') : t('worldBackups.restore.bringBack') }}</span>
            <span class="block text-xs text-base-400">{{ exists ? t('worldBackups.restore.replaceText') : t('worldBackups.restore.bringBackText') }}</span>
          </span>
        </button>
      </div>
      <p v-if="running" class="mt-3 text-xs text-caution-300">{{ t('worldBackups.restore.stopFirst') }}</p>
      <div class="mt-3 flex justify-end gap-2">
        <button type="button" class="btn btn-ghost py-1.5 text-xs" @click="restoring = null">{{ t('common.actions.cancel') }}</button>
        <button type="button" class="btn btn-primary py-1.5 text-xs" :disabled="running" data-testid="world-restore-confirm" @click="restore">{{ t('worldBackups.restore.confirm') }}</button>
      </div>
    </div>

    <div v-else class="max-h-[55vh] overflow-y-auto pr-1 mobile:max-h-none">
      <div v-if="loading" class="space-y-2">
        <div v-for="i in 3" :key="i" class="skeleton h-14" />
      </div>
      <p v-else-if="error" role="alert" class="text-sm text-danger-300">{{ error }}</p>
      <p v-else-if="!backups.length" class="py-6 text-center text-sm text-base-400">{{ t('worldBackups.dialog.empty') }}</p>
      <ul v-else class="space-y-2" data-testid="world-backup-list">
        <li v-for="b in backups" :key="b.id" class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-xl border border-line bg-base-900 px-3 py-2.5">
          <div class="min-w-0 flex-1">
            <p class="flex flex-wrap items-center gap-2 text-sm text-base-50">
              <span class="font-medium tabular-nums">{{ formatDate(b.created) }}</span>
              <span class="badge" :class="KIND_CLASS[b.kind]">{{ t(`worldBackups.kinds.${b.kind}`) }}</span>
            </p>
            <p class="mt-0.5 truncate text-[11px] text-base-400">
              {{ formatRelative(b.created) }} · {{ formatBytes(b.size) }}<template v-if="b.version"> · {{ b.version }}</template><template v-if="b.name && b.name !== name"> · {{ b.name }}</template>
            </p>
          </div>
          <template v-if="confirmDelete === b.id">
            <span class="text-xs text-base-300">{{ t('worldBackups.dialog.deleteConfirm') }}</span>
            <button type="button" class="btn btn-ghost h-8 px-3 py-0 text-xs" @click="confirmDelete = null">{{ t('common.actions.cancel') }}</button>
            <button type="button" class="btn btn-danger h-8 px-3 py-0 text-xs" @click="remove(b)">{{ t('common.actions.delete') }}</button>
          </template>
          <template v-else>
            <button type="button" class="btn btn-ghost h-8 px-3 py-0 text-xs" :disabled="running" :title="running ? t('worldBackups.restore.stopFirst') : ''" @click="startRestore(b)">
              <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 12a8.5 8.5 0 1 0 2.5-6M3 4v4h4M12 8v4l3 2" /></svg>
              {{ t('worldBackups.dialog.restore') }}
            </button>
            <button type="button" class="btn-icon size-8 hover:text-danger-300" :title="t('common.actions.delete')" :aria-label="t('worldBackups.dialog.deleteOf', { date: formatDate(b.created) })" @click="confirmDelete = b.id">
              <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2"><path :d="icons.trash" /></svg>
            </button>
          </template>
        </li>
      </ul>
    </div>

    <template #actions>
      <button type="button" class="btn btn-ghost" @click="emit('close')">{{ t('common.actions.close') }}</button>
    </template>
  </BaseDialog>
</template>
