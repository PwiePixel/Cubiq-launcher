import { defineStore } from 'pinia'
import type { HostedWorld } from '~/utils/hosting'
import { MESSE } from '~/utils/messe'
import {
  handoffView,
  joinNote,
  playRoute,
  type HandoffView,
  type JoinNote,
  type OfficialStatus,
  type PlayRoute,
  type PlayWith,
} from '~/utils/officialLauncher'
import { platformCaps } from '~/utils/system'

// „Mit dem Minecraft Launcher spielen“: Ohne angemeldetes Konto (oder auf Wunsch immer) bereitet Cubiq die
// Instanz vor, trägt sie im offiziellen Minecraft Launcher als „Cubiq · <Name>“ ein und öffnet ihn – dort
// meldet sich der Spieler an und drückt „Spielen“. Die Dialoge dazu stehen in OfficialLauncherDialogs.vue.

/** Offener Dialog: Erklärung vor dem ersten Mal oder das Ergebnis einer Übergabe. */
export type OfficialDialog =
  | { kind: 'explain'; instanceId: string; name: string }
  /** `forced`: trotz offenem Launcher eingetragen – er zeigt die Installation evtl. erst nach einem Neustart. */
  | { kind: 'result'; instanceId: string; name: string; view: HandoffView; join: JoinNote; forced: boolean }

export const useOfficialLauncherStore = defineStore('officialLauncher', () => {
  const status = ref<OfficialStatus | null>(null)
  const dialog = ref<OfficialDialog | null>(null)
  let loading: Promise<void> | null = null
  /** Antwort auf die Erklärung („Weiter“ / „Abbrechen“). */
  let explainAnswer: ((go: boolean) => void) | null = null

  /** Stand vom Kern holen (gefundene Ausgaben, Wahl, eingetragene Installationen). */
  function load(): Promise<void> {
    loading ??= (async () => {
      try {
        status.value = await backend.officialLauncher.status()
      } catch {
        // Ohne Kern (Vorschau im Browser) bleibt es beim Standard.
      } finally {
        loading = null
      }
    })()
    return loading
  }

  /** Bis der Kern geantwortet hat: auf jedem Desktop mit Spielstart. */
  const supported = computed(
    () => status.value?.supported ?? (platformCaps.value.gameLaunch && !['android', 'ios'].includes(platformCaps.value.platform)),
  )
  const playWith = computed<PlayWith>(() => status.value?.playWith ?? 'auto')
  /** Weg des Spielen-Knopfs – folgt Anmeldung und Einstellung. */
  const route = computed<PlayRoute>(() =>
    playRoute({ playWith: playWith.value, signedIn: !!useAccountsStore().active, supported: supported.value, messe: MESSE }),
  )

  async function setPlayWith(value: PlayWith) {
    const prefs = await backend.officialLauncher.setPrefs(value, null)
    if (status.value) status.value = { ...status.value, ...prefs }
    else await load()
  }

  function explain(instanceId: string, name: string): Promise<boolean> {
    explainAnswer?.(false)
    return new Promise((resolve) => {
      explainAnswer = resolve
      dialog.value = { kind: 'explain', instanceId, name }
    })
  }

  /** „Weiter“ (`true`) oder „Abbrechen“ in der Erklärung. */
  function answerExplain(go: boolean) {
    const answer = explainAnswer
    explainAnswer = null
    dialog.value = null
    answer?.(go)
  }

  /**
   * Spielen über den offiziellen Launcher – vom Spielen-Knopf, wenn `route` „official“ ist. Server und Adressen
   * kann er nicht direkt ansteuern (das sagt der Ergebnis-Dialog), gehostete Welten gar nicht.
   * Liefert, ob die Instanz eingetragen wurde.
   */
  async function play(
    id: string,
    join: { serverName?: string | null; address?: string | null; world?: HostedWorld | null } = {},
  ): Promise<boolean> {
    const games = useGamesStore()
    if (games.state(id).phase !== 'idle' || useTasksStore().installingInstance(id)) return false
    const note = joinNote(join)
    if (note.kind === 'unsupported') {
      useToasts().error(t('officialLauncher.hostedWorld'))
      return false
    }
    const name = useInstancesStore().items.find((i) => i.id === id)?.name ?? id
    if (!status.value) await load()
    // Beim ersten Mal kurz erklären, was gleich passiert.
    if (!status.value?.explained) {
      if (!(await explain(id, name))) return false
      backend.officialLauncher
        .setPrefs(null, true)
        .then((prefs) => {
          if (status.value) status.value = { ...status.value, ...prefs }
        })
        .catch(() => {})
    }
    return run(id, name, note, false)
  }

  async function run(id: string, name: string, note: JoinNote, ignoreRunning: boolean): Promise<boolean> {
    const s = useGamesStore().state(id)
    if (s.phase !== 'idle') return false
    // Wie beim eigenen Start: Der Spielen-Knopf zeigt den Fortschritt der Vorbereitung.
    s.phase = 'preparing'
    s.error = null
    s.errorCode = null
    s.progress = { stage: 'version', percent: 0, doneFiles: 0, totalFiles: 0 }
    const result = await useTasksStore().run(
      {
        key: taskKey('launch', id),
        kind: 'launch',
        title: name,
        stage: t('officialLauncher.preparing'),
        instanceId: id,
        cancellable: true,
        pausable: true,
        record: false,
        notify: false,
      },
      (ctx) =>
        backend.officialLauncher.play(
          id,
          ignoreRunning,
          (p) => {
            s.progress = p
            ctx.progress(overallPercent(p.stage, p.percent), stageLabel(p.stage))
            // Ab hier wird eingetragen – nichts mehr anzuhalten.
            if (p.stage === 'starting') ctx.update({ cancellable: false, pausable: false })
          },
          ctx.taskId,
        ),
    )
    s.progress = null
    s.phase = 'idle'
    if (!result.ok) {
      if (!result.cancelled) {
        s.error = errorMessage(result.error)
        useToasts().error(result.error)
      }
      return false
    }
    const view = handoffView(result.value)
    dialog.value = { kind: 'result', instanceId: id, name, view, join: note, forced: ignoreRunning }
    void load()
    return view.kind === 'done'
  }

  /** „Erneut versuchen“ bzw. „Trotzdem fortfahren“ (Launcher offen) im Ergebnis-Dialog. */
  async function retry(ignoreRunning = false): Promise<boolean> {
    const d = dialog.value
    if (d?.kind !== 'result') return false
    dialog.value = null
    return run(d.instanceId, d.name, d.join, ignoreRunning)
  }

  /** Offiziellen Launcher nur öffnen (z. B. damit er beim ersten Mal seine Dateien anlegt). */
  async function openLauncher(): Promise<boolean> {
    try {
      return await backend.officialLauncher.open()
    } catch (e) {
      useToasts().error(e)
      return false
    }
  }

  /** Alle Cubiq-Installationen aus dem offiziellen Launcher entfernen; liefert, wie viele. */
  async function removeAll(): Promise<number> {
    const removed = await backend.officialLauncher.removeAll()
    await load()
    return removed
  }

  function close() {
    if (dialog.value?.kind === 'explain') answerExplain(false)
    else dialog.value = null
  }

  return { status, dialog, supported, playWith, route, load, setPlayWith, play, retry, answerExplain, openLauncher, removeAll, close }
})
