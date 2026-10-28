import { isTauri } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { defineStore } from 'pinia'
import { jobPercent, type BackupEvent, type BackupJob } from '../utils/worldBackups'

// Automatische Welt-Backups laufen im Kern (beim Spielstart und nach dem Spielen). Dieser Store zeigt
// sie als Aufgabe in der Titelleiste (Fortschritt, Abbrechen) und meldet Probleme: eine Welt, die im
// Spiel geöffnet wurde, bevor ihr Backup fertig war, oder ein Fehler. Erfolge bleiben still.
export const useWorldBackupsStore = defineStore('worldBackups', () => {
  /** Laufende Sicherungen je Job. */
  const jobs = ref<Record<number, BackupJob>>({})
  /** Zählt hoch, wenn eine Sicherung fertig ist – Seiten laden dann ihre Übersicht neu. */
  const revision = ref(0)
  const pending = new Map<number, { resolve: () => void; reject: (e: unknown) => void; progress: (percent: number, stage?: string) => void }>()
  let initialized = false

  function instanceName(id: string): string {
    return useInstancesStore().items.find((i) => i.id === id)?.name ?? id
  }

  function stageOf(job: BackupJob): string {
    return job.world ? t('worldBackups.task.stage', { world: job.world, n: job.index + 1, count: job.count }) : t('worldBackups.task.preparing')
  }

  function started(event: Extract<BackupEvent, { type: 'started' }>) {
    const job: BackupJob = { job: event.job, instanceId: event.instanceId, reason: event.reason, world: event.worlds[0] ?? null, index: 0, count: event.worlds.length, percent: 0 }
    jobs.value[event.job] = job
    void useTasksStore().run(
      {
        key: taskKey('world-backup-auto', event.instanceId, String(event.job)),
        kind: 'export',
        title: t('worldBackups.task.title', { name: instanceName(event.instanceId) }),
        stage: stageOf(job),
        instanceId: event.instanceId,
        cancellable: true,
        // Automatisch und meist in Sekunden fertig: kein Verlaufseintrag, keine Erfolgsmeldung.
        record: false,
        notify: false,
      },
      (ctx) =>
        new Promise<void>((resolve, reject) => {
          pending.set(event.job, { resolve, reject, progress: ctx.progress })
          // Abbrechen: Der Kern führt diese Sicherung nicht unter der Aufgaben-ID – selbst Bescheid geben.
          const poll = setInterval(() => {
            if (!pending.has(event.job)) clearInterval(poll)
            else if (ctx.cancelled()) {
              clearInterval(poll)
              void backend.cancelWorldBackupJob(event.job).catch(() => {})
            }
          }, 300)
        }),
    )
  }

  function progressed(event: Extract<BackupEvent, { type: 'progress' }>) {
    const job = jobs.value[event.job]
    if (!job) return
    Object.assign(job, { world: event.world, index: event.index, count: event.count, percent: event.percent })
    pending.get(event.job)?.progress(jobPercent(event.index, event.count, event.percent), stageOf(job))
  }

  function finished(event: Extract<BackupEvent, { type: 'finished' }>) {
    delete jobs.value[event.job]
    revision.value++
    const waiting = pending.get(event.job)
    pending.delete(event.job)
    if (event.cancelled) waiting?.reject(new BackendError('cancelled', 'Vorgang abgebrochen', 'cancelled'))
    else waiting?.resolve()
    const toasts = useToasts()
    // Welt im Spiel geöffnet, bevor ihr Backup fertig war: das vorige bleibt, nach dem Spielen kommt ein neues.
    for (const name of event.skipped) toasts.info(t('worldBackups.toast.opened', { world: name }))
    for (const f of event.failed) toasts.error(t('worldBackups.toast.failed', { world: f.name, error: userErrorText(f.error) }))
  }

  function onEvent(event: BackupEvent) {
    if (event.type === 'started') started(event)
    else if (event.type === 'progress') progressed(event)
    else finished(event)
  }

  /** Einmal beim App-Start (Plugin): Ereignisse abonnieren, laufende Sicherungen übernehmen. */
  async function init() {
    if (initialized || !isTauri()) return
    initialized = true
    await listen<BackupEvent>('world-backup', (e) => onEvent(e.payload))
    try {
      for (const job of await backend.worldBackupJobs()) {
        if (!jobs.value[job.job]) {
          started({ type: 'started', job: job.job, instanceId: job.instanceId, reason: job.reason, worlds: job.world ? [job.world] : [], totalBytes: 0 })
          Object.assign(jobs.value[job.job]!, job)
        }
      }
    } catch {
      // Ohne Kern nichts zu übernehmen.
    }
  }

  /** Läuft gerade eine automatische Sicherung für diese Instanz? */
  function runningFor(instanceId: string): BackupJob | null {
    return Object.values(jobs.value).find((j) => j.instanceId === instanceId) ?? null
  }

  return { jobs, revision, init, runningFor, onEvent }
})
