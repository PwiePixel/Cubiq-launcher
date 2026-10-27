<script setup lang="ts">
import { messeRestricted } from '~/utils/messe'
import type { Accent, AppInfo, ClientModStatus, Settings, SharedFolder, StorageStats, Theme } from '~/types'
import type { ShellSection } from '~/components/SettingsShell.vue'
import type { Locale } from '~/utils/i18n'

// Globale Einstellungen. Alles speichert
// automatisch; Darstellung wirkt sofort als Vorschau.
const store = useSettingsStore()
const accounts = useAccountsStore()
const onboarding = useOnboardingStore()
const cubiq = useCubiqStore()
const toasts = useToasts()
const openDocs = useDocs()

// Namen und Gruppen sind Getter in sections.ts – sie folgen der eingestellten Sprache.
// Was es auf diesem System nicht gibt (Clips, Java, Spiel-Standards am Handy), fehlt auch hier.
const sectionList = useAppSettingsSections()
const sections = computed<ShellSection[]>(() => sectionList.value)
const active = computed({
  get: () => (sections.value.some((s) => s.key === store.dialog) ? store.dialog! : 'appearance'),
  set: (v: string) => (store.dialog = v),
})

const events = useEventsStore()
const form = ref<Settings | null>(null)
const info = ref<AppInfo | null>(null)
/** Was es auf diesem System gibt – bis `appInfo` da ist, aus dem User-Agent geschätzt. */
const caps = computed(() => info.value?.capabilities ?? platformCaps.value)
/** Handy: Seitenleiste und Ordner im Datei-Explorer gibt es dort nicht. */
const mobile = mobileUi
const mobileOs = computed(() => isMobileOs(caps.value.platform))
const updatesLine = computed(() => {
  if (caps.value.updates === 'package') return t('settings.footer.updatesPackage')
  if (caps.value.updates === 'flatpak') return t('settings.footer.updatesFlatpak')
  if (caps.value.updates === 'portable') return t('settings.footer.updatesPortable')
  return null
})
const clientMod = ref<ClientModStatus | null>(null)
/** Speicherstatus: Fehler als fertiger Text, „Speichere/Gespeichert“ als Schlüssel (folgt einem Sprachwechsel). */
const status = ref<{ ok: boolean; text: string } | { ok: true; state: 'saving' | 'saved' } | null>(null)
const statusView = computed(() => {
  const s = status.value
  if (!s) return null
  return 'state' in s ? { ok: true, text: t(`common.status.${s.state}`) } : s
})
let lastSaved = ''
let timer: ReturnType<typeof setTimeout> | undefined

onMounted(async () => {
  try {
    form.value = structuredClone(toRaw(store.current ?? (await store.load())))
    lastSaved = JSON.stringify(form.value)
    info.value = await loadAppInfo()
  } catch (e) {
    status.value = { ok: false, text: errorMessage(e) }
  }
  if (!accounts.loaded) accounts.load().catch(() => {})
  backend.clientModStatus().then((s) => (clientMod.value = s)).catch(() => {})
})

// Der Cubiq Client aktualisiert sich still über seinen eigenen Kanal – hier nur der Stand.
const clientModLine = computed(() => {
  const s = clientMod.value
  if (!s) return null
  if (s.update) return t('settings.footer.clientModUpdate', { version: s.update })
  return s.bundled ? t('settings.footer.clientModCurrent', { version: s.bundled }) : null
})

// Darstellung sofort anwenden, gespeichert wird gleich danach.
watch(() => form.value?.ui, (ui) => ui && applyAppearance(ui), { deep: true })

// Theme/Akzent/Sprache kamen vom Cubiq-Konto: ins offene Formular übernehmen,
// ohne sie als eigene Änderung zurückzuspeichern.
watch(
  () => store.syncRevision,
  () => {
    const ui = store.current?.ui
    if (!form.value || !ui) return
    const unchanged = JSON.stringify(form.value) === lastSaved
    form.value.ui.theme = ui.theme
    form.value.ui.accent = ui.accent
    form.value.ui.language = ui.language
    if (unchanged) lastSaved = JSON.stringify(form.value)
  },
)

// Clips wurden im Spiel eingeschaltet: Schalter im offenen Formular nachziehen,
// ohne es als eigene Änderung zurückzuspeichern.
watch(
  () => store.externalRevision,
  () => {
    const clips = store.current?.clips
    if (!form.value || !clips) return
    const unchanged = JSON.stringify(form.value) === lastSaved
    form.value.clips.enabled = clips.enabled
    if (unchanged) lastSaved = JSON.stringify(form.value)
  },
)

watch(
  () => JSON.stringify(form.value),
  (json) => {
    clearTimeout(timer)
    if (!form.value || json === lastSaved) return
    timer = setTimeout(save, 500)
  },
)

async function save() {
  timer = undefined
  if (!form.value) return
  const parsed = settingsSchema.safeParse(form.value)
  if (!parsed.success) {
    status.value = { ok: false, text: firstIssue(parsed.error) }
    return
  }
  const json = JSON.stringify(form.value)
  status.value = { ok: true, state: 'saving' }
  try {
    await store.save(parsed.data as Settings)
    lastSaved = json
    status.value = { ok: true, state: 'saved' }
  } catch (e) {
    status.value = { ok: false, text: errorMessage(e) }
  }
}

async function close() {
  if (timer) {
    clearTimeout(timer)
    await save()
  }
  // Nicht gespeicherte Vorschau zurücknehmen.
  applyAppearance(store.current?.ui)
  store.dialog = null
}
onBeforeUnmount(() => clearTimeout(timer))

/** Einstellungen schließen (speichert Offenes) und in den Big-Picture-Modus. */
async function openBigPicture() {
  await close()
  useBigPictureStore().open()
}

// --- Aussehen -----------------------------------------------------------------
// Namen kommen beim Rendern aus settings.appearance.themes/accents. Die Vorschau zeigt Rahmen, Leinwand,
// Karte, Kanten und Platzhalter-Schrift des Schemas (Werte aus main.css); „Wie System“ halb dunkel, halb hell.
const darkLook = { frame: '#04061a', canvas: '#070a1d', card: '#0c1028', line: '#1e2550', ink: '#283062' }
const lightLook = { frame: '#e1e5f1', canvas: '#edf0f8', card: '#ffffff', line: '#dde1ef', ink: '#d2d7e8' }
const themes: { key: Theme; look: typeof darkLook; split?: typeof darkLook }[] = [
  { key: 'dark', look: darkLook },
  { key: 'oled', look: { frame: '#000000', canvas: '#000000', card: '#05060f', line: '#151937', ink: '#1f2447' } },
  { key: 'light', look: lightLook },
  { key: 'system', look: darkLook, split: lightLook },
]
const accents: { key: Accent; color: string }[] = [
  { key: 'cubiq', color: '#71a330' },
  { key: 'emerald', color: '#17a34a' },
  { key: 'ice', color: '#2b97d6' },
  { key: 'lapis', color: '#3563e9' },
  { key: 'amethyst', color: '#9b4ddf' },
  { key: 'orange', color: '#e8702a' },
  { key: 'amber', color: '#e0900c' },
]

// --- Sprache ------------------------------------------------------------------
// Sofort umschalten und speichern (die Oberfläche wechselt ohne Neustart).
const language = computed<Locale>({
  get: () => form.value?.ui.language ?? currentLocale.value,
  set: (code) => {
    if (!form.value) return
    form.value.ui.language = code
    void setLocale(code)
  },
})

// --- Profil -------------------------------------------------------------------
async function activateAccount(id: string) {
  try {
    await accounts.setActive(id)
  } catch (e) {
    toasts.error(e)
  }
}

// --- Java ---------------------------------------------------------------------
// Hinweis je Slot: settings.java.slots.<key>
const javaSlots = [
  { major: 8, key: 'java8' },
  { major: 17, key: 'java17' },
  { major: 21, key: 'java21' },
  { major: 25, key: 'java25' },
] as const
function javaModel(key: (typeof javaSlots)[number]['key']) {
  return computed({
    get: () => form.value?.java[key] ?? '',
    set: (v: string) => {
      if (form.value) form.value.java[key] = v.trim() || null
    },
  })
}
const javaModels = Object.fromEntries(javaSlots.map((s) => [s.key, javaModel(s.key)])) as Record<(typeof javaSlots)[number]['key'], WritableComputedRef<string>>
const globalJava = computed({
  get: () => form.value?.javaPath ?? '',
  set: (v: string) => {
    if (form.value) form.value.javaPath = v.trim() || null
  },
})
// Java lädt als Aufgabe weiter, auch wenn der Dialog zugeht.
const tasks = useTasksStore()
const installing = computed(() => {
  const task = tasks.active.find((a) => a.kind === 'java')
  return task ? { major: Number(task.tag), percent: task.percent ?? 0 } : null
})
let dialogOpen = true
onBeforeUnmount(() => (dialogOpen = false))
function installJava(slot: (typeof javaSlots)[number]) {
  tasks.run(
    {
      key: taskKey('java', String(slot.major)),
      kind: 'java',
      title: `Java ${slot.major}`,
      stage: t('settings.java.taskStage'),
      tag: String(slot.major),
      cancellable: true,
      pausable: true,
      doneText: t('settings.java.taskDone', { major: slot.major }),
    },
    async (ctx) => {
      const path = await backend.installJava(slot.major, (p) => ctx.progress(p), ctx.taskId)
      if (dialogOpen && form.value) {
        // Offener Dialog: über das Formular (speichert selbst).
        javaModels[slot.key].value = path
      } else {
        const current = store.current ?? (await store.load())
        await store.save({ ...current, java: { ...current.java, [slot.key]: path } })
      }
      return path
    },
  )
}

/** Gemeinsame Ordner, die neue Instanzen von Anfang an teilen. */
function toggleDefaultShared(kind: SharedFolder, on: boolean) {
  if (!form.value) return
  const list = (form.value.sharedFolders ?? []).filter((k) => k !== kind)
  form.value.sharedFolders = on ? sharedFolderKeys.filter((k) => k === kind || list.includes(k)) : list
}

// --- Speicher -----------------------------------------------------------------
const stats = ref<StorageStats | null>(null)
const statsBusy = ref(false)
const verifying = ref(false)
const confirmClean = ref(false)
const cleaning = ref(false)
async function loadStats() {
  statsBusy.value = true
  try {
    stats.value = await backend.storageStats()
  } catch (e) {
    toasts.error(e)
  } finally {
    statsBusy.value = false
  }
}
watch(active, (a) => a === 'storage' && !stats.value && loadStats(), { immediate: true })
const bars = computed(() => {
  const s = stats.value
  if (!s) return []
  const shared = s.libraries + s.assets + s.versions - s.unused + s.java + s.shared
  const parts = [
    { key: 'instances', label: t('settings.storage.instances'), bytes: s.instances, color: 'bg-primary-500' },
    { key: 'shared', label: t('settings.storage.sharedFiles'), bytes: shared, color: 'bg-live-400' },
    { key: 'sharedFolders', label: t('settings.storage.sharedFolders'), bytes: s.sharedFolders ?? 0, color: 'bg-sky-400' },
    { key: 'unused', label: t('settings.storage.unused'), bytes: s.unused, color: 'bg-base-600' },
  ]
  const total = parts.reduce((sum, p) => sum + p.bytes, 0) || 1
  return parts.map((p) => ({ ...p, share: (p.bytes / total) * 100 }))
})
async function verify() {
  verifying.value = true
  try {
    const r = await backend.verifyStorage()
    toasts.ok(
      r.removed
        ? t('settings.storage.verifyRemoved', { checked: r.checked, removed: r.removed })
        : t('settings.storage.verifyOk', { checked: r.checked }),
    )
  } catch (e) {
    toasts.error(e)
  } finally {
    verifying.value = false
  }
}
async function clean() {
  confirmClean.value = false
  cleaning.value = true
  try {
    const freed = await backend.cleanUnusedStorage()
    toasts.ok(t('settings.storage.freed', { size: formatBytes(freed) }))
    await loadStats()
  } catch (e) {
    toasts.error(e)
  } finally {
    cleaning.value = false
  }
}
function openDataDir() {
  backend.openDataDir().catch(() => {})
}
// Datenordner verschieben (Assistent mit Neustart).
const games = useGamesStore()
const movingData = ref(false)

// --- Netzwerk -----------------------------------------------------------------
const firewall = ref<{ total: number; missing: number } | null>(null)
const firewallBusy = ref(false)
// Die Firewall-Freigabe gibt es nur unter Windows.
watch(
  active,
  (a) => a === 'network' && caps.value.firewall && !firewall.value && backend.firewallStatus().then((s) => (firewall.value = s)).catch(() => {}),
  { immediate: true },
)
async function allowFirewall() {
  firewallBusy.value = true
  try {
    const n = await backend.firewallAllowAll()
    toasts.ok(n ? t('settings.network.allowed', n) : t('settings.network.noJava'))
    firewall.value = await backend.firewallStatus()
  } catch (e) {
    if (!isCancelled(e)) toasts.error(e)
  } finally {
    firewallBusy.value = false
  }
}
</script>

<template>
  <SettingsShell v-model="active" :title="t('settings.title')" :sections="sections" :status="statusView" @close="close">
    <template #nav-footer>
      <p v-if="info" class="flex items-center gap-1.5">Cubiq Launcher v{{ info.version }}<HalloweenPumpkin v-if="events.halloween" id="launcher-settings" :size="12" /></p>
      <p v-if="info">{{ info.os }}</p>
      <p v-if="clientModLine">{{ clientModLine }}</p>
      <p v-if="updatesLine">{{ updatesLine }}</p>
      <!-- Hilfe: Dokumentation auf der Website (Einstellungen-Seite in der Launcher-Sprache). -->
      <button type="button" class="mt-1 inline-flex items-center gap-1 text-primary-300 hover:underline" data-testid="settings-docs" @click="openDocs('launcher/settings')">
        <svg viewBox="0 0 24 24" class="size-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="icons.help" /></svg>
        {{ t('docs.open') }}
      </button>
    </template>

    <div v-if="!form" class="space-y-3">
      <div v-for="i in 4" :key="i" class="skeleton h-14" />
    </div>

    <!-- Aussehen --------------------------------------------------------------- -->
    <div v-else-if="active === 'appearance'">
      <h3 class="section-heading">{{ t('settings.appearance.colorScheme') }}</h3>
      <div class="grid grid-cols-4 gap-3 border-b border-base-800 pb-5 mobile:grid-cols-2">
        <button
          v-for="theme in themes"
          :key="theme.key"
          class="squircle overflow-hidden rounded-xl border-2 text-left transition-colors"
          :class="form.ui.theme === theme.key ? 'border-primary-500' : 'border-line hover:border-line-strong'"
          :aria-pressed="form.ui.theme === theme.key"
          @click="form.ui.theme = theme.key"
        >
          <div class="relative h-20 overflow-hidden">
            <ThemePreview v-bind="theme.look" />
            <ThemePreview v-if="theme.split" v-bind="theme.split" class="[clip-path:polygon(100%_0,100%_100%,0_100%)]" />
          </div>
          <div class="flex items-center gap-2 bg-base-850 px-3 py-2 text-sm font-medium">
            <span class="grid size-4 place-items-center rounded-full border-2" :class="form.ui.theme === theme.key ? 'border-primary-500' : 'border-base-600'">
              <span v-if="form.ui.theme === theme.key" class="size-2 rounded-full bg-primary-500" />
            </span>
            {{ t(`settings.appearance.themes.${theme.key}`) }}
          </div>
        </button>
      </div>

      <SettingRow :title="t('settings.appearance.accentTitle')" :description="t('settings.appearance.accentDescription')" :stacked="mobile">
        <div class="flex gap-2 mobile:flex-wrap">
          <button
            v-for="a in accents"
            :key="a.key"
            class="squircle size-7 rounded-lg ring-offset-2 ring-offset-base-900 transition-transform hover:scale-110 mobile:size-9"
            :class="form.ui.accent === a.key ? 'ring-2 ring-base-50' : ''"
            :style="{ background: a.color }"
            :title="t(`settings.appearance.accents.${a.key}`)"
            :aria-label="t('settings.appearance.accentLabel', { name: t(`settings.appearance.accents.${a.key}`) })"
            :aria-pressed="form.ui.accent === a.key"
            @click="form.ui.accent = a.key"
          />
        </div>
      </SettingRow>
      <SettingRow :title="t('settings.appearance.advancedRenderingTitle')" :description="t('settings.appearance.advancedRenderingDescription')">
        <ToggleSwitch v-model="form.ui.advancedRendering" :label="t('settings.appearance.advancedRenderingTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.appearance.animatedBackgroundTitle')" :description="t('settings.appearance.animatedBackgroundDescription')">
        <ToggleSwitch v-model="form.ui.animatedBackground" :label="t('settings.appearance.animatedBackgroundTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.appearance.eventLookTitle')" :description="t('settings.appearance.eventLookDescription')" data-testid="settings-event-look">
        <ToggleSwitch v-model="form.ui.eventLook" :label="t('settings.appearance.eventLookTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.appearance.eventIconsTitle')" :description="t('settings.appearance.eventIconsDescription')">
        <ToggleSwitch v-model="form.ui.eventIcons" :label="t('settings.appearance.eventIconsTitle')" />
      </SettingRow>
      <SettingRow v-if="events.lookHalloween" :title="t('settings.appearance.halloweenFlickerTitle')" :description="t('settings.appearance.halloweenFlickerDescription')" data-testid="settings-halloween-flicker">
        <ToggleSwitch v-model="form.ui.halloweenFlicker" :label="t('settings.appearance.halloweenFlickerTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.appearance.motionTitle')" :description="t('settings.appearance.motionDescription')">
        <select v-model="form.ui.motion" class="field w-52 py-1.5 mobile:w-40" :aria-label="t('settings.appearance.motionTitle')">
          <option v-for="mode in motionModes" :key="mode" :value="mode">{{ t(`settings.appearance.motionModes.${mode}`) }}</option>
        </select>
      </SettingRow>
      <SettingRow :title="t('settings.appearance.startIntroTitle')" :description="t('settings.appearance.startIntroDescription')" data-testid="settings-start-intro">
        <div class="flex items-center gap-2">
          <button type="button" class="btn btn-ghost shrink-0 py-1.5 text-xs" @click="replayStartIntro()">{{ t('settings.appearance.startIntroReplay') }}</button>
          <ToggleSwitch
            :model-value="form.ui.startIntro !== false"
            :label="t('settings.appearance.startIntroTitle')"
            @update:model-value="(on: boolean) => form && (form.ui.startIntro = on)"
          />
        </div>
      </SettingRow>
    </div>

    <!-- Funktionen ------------------------------------------------------------- -->
    <div v-else-if="active === 'features'">
      <h3 class="section-heading">{{ t('settings.features.instancePage') }}</h3>
      <SettingRow :title="t('settings.features.historyTabTitle')" :description="t('settings.features.historyTabDescription')">
        <ToggleSwitch v-model="form.ui.historyTab" :label="t('settings.features.historyTabTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.features.screenshotsTabTitle')" :description="t('settings.features.screenshotsTabDescription')">
        <ToggleSwitch v-model="form.ui.screenshotsTab" :label="t('settings.features.screenshotsTabTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.features.worldsTabTitle')" :description="t('settings.features.worldsTabDescription')">
        <ToggleSwitch v-model="form.ui.worldsTab" :label="t('settings.features.worldsTabTitle')" />
      </SettingRow>
      <template v-if="!mobile">
        <h3 class="section-heading mt-6">{{ t('settings.features.sidebar') }}</h3>
        <SettingRow :title="t('settings.features.sidebarRecentTitle')" :description="t('settings.features.sidebarRecentDescription')">
          <ToggleSwitch v-model="form.ui.sidebarRecent" :label="t('settings.features.sidebarRecentTitle')" />
        </SettingRow>
        <SettingRow :title="t('settings.features.sidebarAccountTitle')" :description="t('settings.features.sidebarAccountDescription')">
          <ToggleSwitch v-model="form.ui.sidebarAccount" :label="t('settings.features.sidebarAccountTitle')" />
        </SettingRow>
      </template>
    </div>

    <!-- Verhalten -------------------------------------------------------------- -->
    <div v-else-if="active === 'behavior'">
      <h3 class="section-heading">{{ t('settings.behavior.title') }}</h3>
      <SettingRow v-if="caps.gameLaunch" :title="t('settings.behavior.closeOnLaunchTitle')" :description="t('settings.behavior.closeOnLaunchDescription')">
        <ToggleSwitch v-model="form.closeOnLaunch" :label="t('settings.behavior.closeOnLaunchTitle')" />
      </SettingRow>
      <SettingRow v-if="caps.platform === 'windows'" :title="t('settings.behavior.showBedrockTitle')" :description="t('settings.behavior.showBedrockDescription')">
        <ToggleSwitch v-model="form.showBedrock" :label="t('settings.behavior.showBedrockTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.behavior.compactLibraryTitle')" :description="t('settings.behavior.compactLibraryDescription')">
        <ToggleSwitch v-model="form.ui.compactLibrary" :label="t('settings.behavior.compactLibraryTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.behavior.showPlayTimeTitle')" :description="t('settings.behavior.showPlayTimeDescription')">
        <ToggleSwitch v-model="form.ui.showPlayTime" :label="t('settings.behavior.showPlayTimeTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.behavior.showSnapshotsTitle')" :description="t('settings.behavior.showSnapshotsDescription')">
        <ToggleSwitch v-model="form.showSnapshots" :label="t('settings.behavior.showSnapshotsTitle')" />
      </SettingRow>
      <SettingRow v-if="!messeRestricted" :title="t('settings.behavior.restartOnboardingTitle')" :description="t('settings.behavior.restartOnboardingDescription')">
        <button class="btn btn-ghost" @click="close(); onboarding.restart()">{{ t('common.actions.start') }}</button>
      </SettingRow>

      <h3 class="section-heading mt-6">{{ t('settings.bigPicture.title') }}</h3>
      <SettingRow :title="t('settings.bigPicture.openTitle')" :description="t('settings.bigPicture.openDescription')">
        <button class="btn btn-ghost" data-testid="settings-big-picture" @click="openBigPicture">{{ t('settings.bigPicture.open') }}</button>
      </SettingRow>
      <SettingRow :title="t('settings.bigPicture.onStartTitle')" :description="t('settings.bigPicture.onStartDescription')">
        <ToggleSwitch v-model="form.ui.bigPictureOnStart" :label="t('settings.bigPicture.onStartTitle')" />
      </SettingRow>
      <SettingRow v-if="caps.platform === 'linux'" :title="t('settings.bigPicture.autoTitle')" :description="t('settings.bigPicture.autoDescription')">
        <ToggleSwitch v-model="form.ui.bigPictureAuto" :label="t('settings.bigPicture.autoTitle')" />
      </SettingRow>
    </div>

    <!-- Sprache -------------------------------------------------------------- -->
    <div v-else-if="active === 'language'">
      <h3 class="section-heading">{{ t('language.title') }}</h3>
      <p class="mb-4 text-xs leading-relaxed text-base-400">{{ t('language.changeHint') }}</p>
      <LanguagePicker v-model="language" />
    </div>

    <!-- Profil ---------------------------------------------------------------- -->
    <div v-else-if="active === 'profile'">
      <h3 class="section-heading">{{ t('settings.profile.title') }}</h3>
      <ul v-if="accounts.items.length" class="divide-y divide-base-800 rounded-xl border border-base-800">
        <li v-for="a in accounts.items" :key="a.id" class="flex items-center gap-3 px-4 py-3">
          <SkinHead :skin-url="a.skinUrl" :name="a.name" :size="36" />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-semibold">{{ a.name }}</p>
            <p class="text-xs text-base-400">{{ t('settings.profile.added', { time: formatRelative(a.addedAt) }) }}</p>
          </div>
          <span v-if="a.active" class="badge bg-ok/10 text-ok">{{ t('common.status.active') }}</span>
          <button v-else class="btn btn-ghost py-1.5 text-xs" @click="activateAccount(a.id)">{{ t('settings.profile.use') }}</button>
        </li>
      </ul>
      <p v-else class="text-sm text-base-400">{{ t('settings.profile.none') }}</p>
      <NuxtLink to="/accounts" class="btn btn-primary mt-4" @click="close">{{ t('settings.profile.manage') }}</NuxtLink>
    </div>

    <!-- Benachrichtigungen ------------------------------------------------------ -->
    <div v-else-if="active === 'notifications'">
      <!-- Handy: Push-Benachrichtigungen auch bei geschlossener App (§33). -->
      <MobilePushSettings v-if="platformCaps.pushSupported" class="mb-6" />
      <SocialNotificationSettings v-if="form.social" v-model="form.social" />
    </div>

    <!-- Fernbedienung (vom Handy) ------------------------------------------------ -->
    <div v-else-if="active === 'remote'">
      <RemoteControlSettings v-if="form.remote" v-model="form.remote" />
    </div>

    <!-- Datenschutz -------------------------------------------------------------- -->
    <div v-else-if="active === 'privacy'">
      <h3 class="section-heading">{{ t('settings.privacy.title') }}</h3>
      <SettingRow :title="t('settings.privacy.logUploadTitle')" :description="t('settings.privacy.logUploadDescription')">
        <ToggleSwitch v-model="form.allowLogUpload" :label="t('settings.privacy.logUploadTitle')" />
      </SettingRow>
      <SettingRow v-if="caps.gameLaunch" :title="t('settings.privacy.discordTitle')" :description="t('settings.privacy.discordDescription')">
        <ToggleSwitch v-model="form.discordPresence" :label="t('settings.privacy.discordTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.privacy.telemetryTitle')" :description="t('settings.privacy.telemetryDescription')">
        <ToggleSwitch :model-value="false" :label="t('settings.privacy.telemetryTitle')" disabled />
      </SettingRow>
      <CubiqPrivacySettings v-if="cubiq.available" v-model:sync="form.cubiqSync" />
    </div>

    <!-- Standard-Einstellungen ------------------------------------------------------ -->
    <div v-else-if="active === 'defaults'">
      <h3 class="section-heading">{{ t('settings.defaults.title') }}</h3>
      <p class="mb-2 text-xs text-base-400">{{ t('settings.defaults.hint') }}</p>
      <SettingRow :title="t('settings.defaults.memoryTitle')" :description="t('settings.defaults.memoryDescription')" stacked>
        <div class="flex items-center gap-4">
          <input v-model.number="form.maxMemoryMb" type="range" min="1024" max="32768" step="512" class="flex-1 accent-primary-500" :aria-label="t('settings.defaults.maxMemoryLabel')" />
          <div class="flex items-center gap-1.5">
            <input v-model.number="form.maxMemoryMb" type="number" min="512" max="131072" step="256" class="field w-28 font-mono" :aria-label="t('settings.defaults.maxMemoryMbLabel')" />
            <span class="text-xs text-base-400">MB</span>
          </div>
        </div>
        <p class="mt-1 text-xs text-base-600">{{ formatMemory(form.maxMemoryMb) }}</p>
        <p v-if="form.maxMemoryMb >= 16384" class="mt-1 text-xs text-amber-300">{{ t('common.memoryHigh') }}</p>
      </SettingRow>
      <SettingRow :title="t('settings.defaults.minMemoryTitle')" :description="t('settings.defaults.minMemoryDescription')">
        <input v-model.number="form.minMemoryMb" type="number" min="128" step="128" class="field w-28 font-mono" :aria-label="t('settings.defaults.minMemoryTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.defaults.jvmArgsTitle')" :description="t('settings.defaults.jvmArgsDescription')" stacked>
        <div class="flex gap-2">
          <input
            v-model="form.jvmArgs"
            class="field font-mono text-xs"
            maxlength="4096"
            :placeholder="t('common.status.none')"
            spellcheck="false"
            :aria-label="t('settings.defaults.jvmArgsTitle')"
          />
          <button class="btn btn-ghost shrink-0 py-1.5 text-xs" @click="form.jvmArgs = optimizedJvmArgs">{{ t('settings.defaults.optimizedArgs') }}</button>
        </div>
      </SettingRow>
      <SettingRow :title="t('settings.defaults.envTitle')" :description="t('settings.defaults.envDescription')" stacked>
        <EnvEditor v-model="form.env" />
      </SettingRow>
      <SettingRow :title="t('settings.defaults.fullscreenTitle')" :description="t('settings.defaults.fullscreenDescription')">
        <ToggleSwitch v-model="form.fullscreen" :label="t('settings.defaults.fullscreenTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.defaults.resolutionTitle')" :description="t('settings.defaults.resolutionDescription')">
        <input v-model.number="form.resolution.width" type="number" min="320" class="field w-24 font-mono" :aria-label="t('settings.defaults.windowWidth')" />
        <span class="text-base-600">×</span>
        <input v-model.number="form.resolution.height" type="number" min="240" class="field w-24 font-mono" :aria-label="t('settings.defaults.windowHeight')" />
      </SettingRow>
      <SettingRow
        :title="t('settings.defaults.dedicatedGpuTitle')"
        :description="caps.platform === 'linux' ? t('settings.defaults.dedicatedGpuDescriptionLinux') : t('settings.defaults.dedicatedGpuDescription')"
      >
        <ToggleSwitch v-model="form.preferDedicatedGpu" :label="t('settings.defaults.dedicatedGpuTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.defaults.tuningTitle')" :description="t('settings.defaults.tuningDescription')">
        <ToggleSwitch v-model="form.performanceTuning" :label="t('settings.defaults.tuningTitle')" />
      </SettingRow>
      <SettingRow :title="t('settings.defaults.priorityTitle')" :description="t('settings.defaults.priorityDescription')">
        <ToggleSwitch v-model="form.highPriority" :label="t('settings.defaults.priorityTitle')" />
      </SettingRow>
      <SettingRow :title="t('cubiqChoice.settings.label')" :description="t('cubiqChoice.settings.hint')">
        <select v-model="form.modpackCubiqClient" class="field w-52 py-1.5" :aria-label="t('cubiqChoice.settings.label')">
          <option value="ask">{{ t('cubiqChoice.settings.ask') }}</option>
          <option value="always">{{ t('cubiqChoice.settings.always') }}</option>
          <option value="never">{{ t('cubiqChoice.settings.never') }}</option>
        </select>
      </SettingRow>

      <h3 class="section-heading mt-6">{{ t('settings.hooks.title') }}</h3>
      <SettingRow :title="t('settings.hooks.preLaunchTitle')" :description="t('settings.hooks.preLaunchDescription')" stacked>
        <input
          v-model="form.hooks.preLaunch"
          class="field font-mono text-xs"
          maxlength="1024"
          :placeholder="t('settings.hooks.noCommand')"
          spellcheck="false"
          :aria-label="t('settings.hooks.preLaunchLabel')"
        />
      </SettingRow>
      <SettingRow :title="t('settings.hooks.wrapperTitle')" :description="t('settings.hooks.wrapperDescription')" stacked>
        <input
          v-model="form.hooks.wrapper"
          class="field font-mono text-xs"
          maxlength="1024"
          :placeholder="t('settings.hooks.wrapperPlaceholder')"
          spellcheck="false"
          :aria-label="t('settings.hooks.wrapperLabel')"
        />
      </SettingRow>
      <SettingRow :title="t('settings.hooks.postExitTitle')" :description="t('settings.hooks.postExitDescription')" stacked>
        <input
          v-model="form.hooks.postExit"
          class="field font-mono text-xs"
          maxlength="1024"
          :placeholder="t('settings.hooks.noCommand')"
          spellcheck="false"
          :aria-label="t('settings.hooks.postExitLabel')"
        />
      </SettingRow>

      <h3 class="section-heading mt-6">{{ t('settings.sync.title') }}</h3>
      <p class="mb-1 text-xs leading-relaxed text-base-400">{{ t('settings.sync.description') }}</p>
      <SettingRow v-for="item in syncItemList()" :key="item.key" :title="item.label" :description="item.description">
        <ToggleSwitch v-model="form.sync[item.key]" :label="t('settings.sync.toggleLabel', { item: item.label })" />
      </SettingRow>

      <h3 class="section-heading mt-6">{{ t('settings.sharedFolders.title') }}</h3>
      <p class="mb-1 text-xs leading-relaxed text-base-400">{{ t('settings.sharedFolders.description') }}</p>
      <SettingRow v-for="kind in sharedFolderKeys" :key="kind" :title="sharedFolderLabel(kind)" :description="sharedFolderDescription(kind)">
        <ToggleSwitch
          :model-value="(form.sharedFolders ?? []).includes(kind)"
          :label="t('settings.sharedFolders.toggleLabel', { folder: sharedFolderLabel(kind) })"
          @update:model-value="toggleDefaultShared(kind, $event)"
        />
      </SettingRow>
    </div>

    <!-- Java-Installationen ------------------------------------------------------------ -->
    <div v-else-if="active === 'java'">
      <h3 class="section-heading">{{ t('settings.java.title') }}</h3>
      <p class="mb-3 text-xs text-base-400">
        {{ t('settings.java.hint') }}
        <button type="button" class="text-primary-300 hover:underline" @click="openDocs('help/java')">{{ t('docs.java') }}</button>
      </p>
      <div class="space-y-3">
        <section v-for="slot in javaSlots" :key="slot.major" class="rounded-xl border border-base-800 bg-base-850 p-4">
          <div class="mb-2 flex items-center justify-between">
            <div>
              <h4 class="text-sm font-semibold">Java {{ slot.major }}</h4>
              <p class="text-xs text-base-400">{{ t(`settings.java.slots.${slot.key}`) }}</p>
            </div>
            <button class="btn btn-ghost py-1.5 text-xs" :disabled="!!installing" @click="installJava(slot)">
              {{
                installing?.major === slot.major
                  ? t('settings.java.installing', { percent: Math.floor(installing.percent) })
                  : t('settings.java.installRecommended')
              }}
            </button>
          </div>
          <ProgressBar v-if="installing?.major === slot.major" class="mb-2" :percent="installing.percent" :segments="40" />
          <JavaPathField v-model="javaModels[slot.key].value" :expected-major="slot.major" :placeholder="t('settings.java.autoPlaceholder', { major: slot.major })" />
        </section>
        <section class="rounded-xl border border-base-800 p-4">
          <h4 class="text-sm font-semibold">{{ t('settings.java.globalTitle') }}</h4>
          <p class="mb-2 text-xs text-base-400">{{ t('settings.java.globalDescription') }}</p>
          <JavaPathField v-model="globalJava" :placeholder="t('settings.java.notSet')" />
        </section>
      </div>
    </div>

    <!-- Clips & Aufnahme -------------------------------------------------------------- -->
    <div v-else-if="active === 'clips'">
      <ClipsSettings v-model="form.clips" />
    </div>

    <!-- Speicherverwaltung --------------------------------------------------------------- -->
    <div v-else-if="active === 'storage'">
      <h3 class="section-heading">{{ t('settings.storage.title') }}</h3>
      <div v-if="!stats" class="skeleton h-24" />
      <div v-else class="rounded-xl border border-base-800 bg-base-850 p-4">
        <div class="flex h-3 overflow-hidden rounded-full bg-base-800">
          <div v-for="b in bars" :key="b.key" :class="b.color" :style="{ width: `${b.share}%` }" />
        </div>
        <ul class="mt-3 grid grid-cols-2 gap-3 text-xs sm:grid-cols-4 mobile:grid-cols-2">
          <li v-for="b in bars" :key="b.key" class="flex items-start gap-2">
            <span class="mt-1 size-2.5 shrink-0 rounded-full" :class="b.color" />
            <span><span class="block text-base-400">{{ b.label }}</span><span class="font-mono text-sm text-base-50">{{ formatBytes(b.bytes) }}</span></span>
          </li>
        </ul>
      </div>
      <SettingRow v-if="caps.gameLaunch" :title="t('settings.storage.verifyTitle')" :description="t('settings.storage.verifyDescription')">
        <button class="btn btn-ghost" :disabled="verifying" @click="verify">{{ verifying ? t('settings.storage.verifying') : t('settings.storage.verifyTitle') }}</button>
      </SettingRow>
      <SettingRow
        v-if="caps.gameLaunch"
        :title="t('settings.storage.cleanTitle')"
        :description="stats ? t('settings.storage.cleanDescription', { size: formatBytes(stats.unused) }, stats.unusedVersions) : t('settings.storage.cleanDescriptionEmpty')"
        danger
      >
        <button class="btn btn-danger" :disabled="cleaning || !stats?.unusedVersions" @click="confirmClean = true">
          {{ cleaning ? t('settings.storage.cleaning') : t('settings.storage.cleanTitle') }}
        </button>
      </SettingRow>
      <SettingRow :title="t('settings.storage.dataDirTitle')" stacked>
        <template #description>
          <p v-if="info" class="mt-0.5 text-xs text-base-400">
            <span class="badge mr-1" :class="info.dataSource === 'portable' ? 'bg-live-400/15 text-live-300' : 'bg-base-800 text-base-300'">{{ t(dataSourceKey(info.dataSource)) }}</span>
          </p>
          <p class="mt-0.5 truncate font-mono text-xs text-base-400" :title="info?.dataDir">{{ info?.dataDir }}</p>
          <p v-if="info?.missingDataDir" class="mt-1 text-xs text-warn" role="alert">{{ t('settings.storage.missingDataDir', { path: info.missingDataDir }) }}</p>
          <p v-if="info && !info.dataMovable" class="mt-1 text-xs text-base-400">
            {{ info.dataSource === 'portable' ? t('settings.storage.portableHint') : t('settings.storage.envHint') }}
          </p>
          <p v-if="caps.updates === 'portable'" class="mt-1 text-xs text-base-400">
            {{ t('updater.portableHint') }}
            <button type="button" class="text-primary-300 hover:underline" @click="backend.openExternalUrl(releasesUrl).catch(() => {})">{{ t('settings.storage.downloadPage') }}</button>
          </p>
        </template>
        <div class="flex flex-wrap gap-2">
          <button v-if="!mobileOs" class="btn btn-ghost" @click="openDataDir">{{ t('common.actions.openFolder') }}</button>
          <button
            v-if="info?.dataMovable"
            class="btn btn-ghost"
            :disabled="games.runningCount > 0"
            :title="games.runningCount > 0 ? t('relocate.blockedGames') : undefined"
            data-testid="move-data-dir"
            @click="movingData = true"
          >
            {{ t('settings.storage.moveDataDir') }}
          </button>
        </div>
      </SettingRow>
      <!-- Linux: Anmeldedaten im Schlüsselbund oder – ohne Schlüsselbund – nur per Dateirechte geschützt. -->
      <SettingRow
        v-if="info && !mobileOs && (info.tokenProtection === 'keyring' || info.tokenProtection === 'file')"
        :title="t('settings.storage.credentialsTitle')"
        :description="info.tokenProtection === 'keyring' ? t('settings.storage.credentialsKeyring') : t('settings.storage.credentialsFile')"
      >
        <template #description>
          <button type="button" class="mt-1 text-xs text-primary-300 hover:underline" @click="openDocs('help/linux')">{{ t('docs.linux') }}</button>
        </template>
        <span class="text-xs" :class="info.tokenProtection === 'keyring' ? 'text-ok' : 'text-warn'">
          {{ info.tokenProtection === 'keyring' ? t('settings.storage.credentialsKeyringShort') : t('settings.storage.credentialsFileShort') }}
        </span>
      </SettingRow>
    </div>

    <!-- Netzwerk --------------------------------------------------------------------- -->
    <div v-else-if="active === 'network'">
      <h3 class="section-heading">{{ t('settings.network.title') }}</h3>
      <SettingRow v-if="caps.firewall" :title="t('settings.network.autoFirewallTitle')" :description="t('settings.network.autoFirewallDescription')">
        <ToggleSwitch v-model="form.autoFirewall" :label="t('settings.network.autoFirewallTitle')" />
      </SettingRow>
      <SettingRow v-if="caps.firewall" :title="t('settings.network.firewallTitle')" :description="t('settings.network.firewallDescription')">
        <template #description>
          <p v-if="firewall" class="mt-1 text-xs" :class="firewall.missing ? 'text-warn' : 'text-ok'">
            {{
              firewall.total === 0
                ? t('settings.network.noJava')
                : firewall.missing
                  ? t('settings.network.missing', { missing: firewall.missing, total: firewall.total })
                  : t('settings.network.allAllowed')
            }}
          </p>
        </template>
        <button class="btn btn-ghost" :disabled="firewallBusy || firewall?.total === 0" @click="allowFirewall">
          {{ firewallBusy ? t('settings.network.waitingForWindows') : t('settings.network.allowAll') }}
        </button>
      </SettingRow>
      <SettingRow :title="t('settings.network.downloadsTitle')" :description="t('settings.network.downloadsDescription')">
        <input v-model.number="form.concurrentDownloads" type="number" min="1" max="64" class="field w-24 font-mono" :aria-label="t('settings.network.downloadsTitle')" />
      </SettingRow>
    </div>
  </SettingsShell>

  <DataMoveDialog v-if="movingData && info" :current="info.dataDir" @close="movingData = false" />

  <BaseDialog v-if="confirmClean" :title="t('settings.storage.confirmTitle')" @close="confirmClean = false">
    <p class="text-sm text-base-200">
      {{ t('settings.storage.confirmText', { size: formatBytes(stats?.unused ?? 0) }, stats?.unusedVersions ?? 0) }}
    </p>
    <template #actions>
      <button class="btn btn-ghost" @click="confirmClean = false">{{ t('common.actions.cancel') }}</button>
      <button class="btn btn-danger" @click="clean">{{ t('common.actions.delete') }}</button>
    </template>
  </BaseDialog>
</template>

<style scoped>
@reference "~/assets/css/main.css";

.section-heading {
  @apply mb-2 text-base font-semibold text-base-50;
}
</style>
