<script setup lang="ts">
import type { CubiqPrivacy } from '~/utils/cubiq'
import { WEBSITE_LOGIN_URL } from '~/utils/teamAccess'

// Datenschutz der Cubiq-Dienste (Einstellungen → Datenschutz). Die Schalter
// liegen auf dem Cubiq-Server je Minecraft-Account; die Einwilligung selbst
// liegt nur lokal. „Mit Cubiq-Konto synchronisieren“ ist eine lokale Einstellung
// (`cubiqSync`) und wird vom Einstellungsfenster mitgespeichert.
const sync = defineModel<boolean>('sync', { default: true })
const cubiq = useCubiqStore()
const sanctions = useSanctionsStore()
const applications = useApplicationsStore()
const accounts = useAccountsStore()
const toasts = useToasts()

const saving = ref<keyof CubiqPrivacy | null>(null)
const confirmDelete = ref(false)
const deleting = ref(false)
const switching = ref(false)
const myReports = ref(false)

const settings = computed(() => cubiq.me?.settings ?? null)

/** Website-Anmeldung (§29): Code von der Website hier eingeben – oder die Website im Browser öffnen. */
const webLogin = useWebLoginStore()
function openWebsite() {
  backend.openExternalUrl(WEBSITE_LOGIN_URL).catch((e) => toasts.error(e))
}

onMounted(async () => {
  if (!cubiq.status) await cubiq.refreshStatus()
  if (cubiq.enabled && !cubiq.me) await cubiq.loadMe()
})

async function toggleServices(on: boolean) {
  if (on) {
    // Einschalten nur mit dem Hinweis, was gespeichert wird.
    cubiq.askConsent()
    return
  }
  switching.value = true
  try {
    await cubiq.setConsent(false)
    toasts.ok(t('cubiqPrivacy.turnedOff'))
  } catch (e) {
    toasts.error(e)
  } finally {
    switching.value = false
  }
}

async function update<K extends keyof CubiqPrivacy>(key: K, value: CubiqPrivacy[K]) {
  if (!cubiq.me || saving.value) return
  saving.value = key
  try {
    cubiq.me = await backend.cubiq.updateMe({ [key]: value } as Partial<CubiqPrivacy>)
  } catch (e) {
    toasts.error(e)
  } finally {
    saving.value = null
  }
}

async function deleteAll() {
  deleting.value = true
  try {
    await cubiq.deleteAll()
    confirmDelete.value = false
    toasts.ok(t('cubiqPrivacy.delete.done'))
  } catch (e) {
    toasts.error(e)
  } finally {
    deleting.value = false
  }
}

function openPrivacy() {
  backend.openExternalUrl(CUBIQ_PRIVACY_URL).catch((e) => toasts.error(e))
}
</script>

<template>
  <div class="mt-6">
    <h3 class="section-heading">{{ t('cubiqPrivacy.heading') }}</h3>
    <p class="mb-2 text-xs text-base-400">
      {{ t('cubiqPrivacy.intro') }}
      <button class="text-primary-300 hover:underline" @click="openPrivacy">{{ t('cubiqPrivacy.whatStored') }}</button>
    </p>

    <SettingRow :title="t('cubiqPrivacy.services.title')" :description="t('cubiqPrivacy.services.description')">
      <ToggleSwitch
        :model-value="cubiq.enabled"
        :label="t('cubiqPrivacy.services.title')"
        :disabled="switching || !cubiq.status"
        @update:model-value="toggleServices"
      />
    </SettingRow>

    <template v-if="cubiq.enabled">
      <SettingRow :title="t('cubiqPrivacy.sync.title')" :description="t('cubiqPrivacy.sync.description')">
        <ToggleSwitch v-model="sync" :label="t('cubiqPrivacy.sync.title')" data-testid="cubiq-sync-toggle" />
      </SettingRow>
      <SettingRow v-if="accounts.active" :title="t('applications.settings.title')" :description="t('applications.settings.description')">
        <button class="btn btn-ghost" data-testid="settings-my-applications" @click="applications.show()">
          {{ t('applications.settings.button') }}
          <span v-if="applications.openCount" class="rounded-full bg-base-700 px-1.5 text-[10px] font-bold text-base-50">{{ applications.openCount }}</span>
        </button>
      </SettingRow>
      <SettingRow v-if="accounts.active" :title="t('sanctions.settings.title')" :description="t('sanctions.settings.description')">
        <button class="btn btn-ghost" data-testid="settings-my-sanctions" @click="sanctions.open()">
          {{ t('sanctions.settings.button') }}
          <span v-if="sanctions.current.length" class="rounded-full bg-danger-500 px-1.5 text-[10px] font-bold text-white">{{ sanctions.current.length }}</span>
        </button>
      </SettingRow>
      <p v-if="!accounts.active" class="mt-2 text-xs text-base-400">{{ t('cubiqPrivacy.noAccount') }}</p>
      <p v-else-if="!settings" class="mt-2 text-xs text-base-400">
        {{ cubiq.problem === 'offline' ? t('cubiqPrivacy.offline') : t('cubiqPrivacy.loading') }}
      </p>
      <template v-else>
        <p class="mt-3 mb-1 text-[11px] text-base-600">{{ t('cubiqPrivacy.perAccount', { name: cubiq.me?.name ?? '' }) }}</p>
        <SettingRow :title="t('cubiqPrivacy.badge.title')" :description="t('cubiqPrivacy.badge.description')">
          <ToggleSwitch
            :model-value="settings.showBadge"
            :label="t('cubiqPrivacy.badge.title')"
            :disabled="saving !== null"
            @update:model-value="update('showBadge', $event)"
          />
        </SettingRow>
        <SettingRow :title="t('cubiqPrivacy.cape.title')" :description="t('cubiqPrivacy.cape.description')">
          <ToggleSwitch
            :model-value="settings.showCapeToOthers"
            :label="t('cubiqPrivacy.cape.title')"
            :disabled="saving !== null"
            @update:model-value="update('showCapeToOthers', $event)"
          />
        </SettingRow>
        <SettingRow :title="t('cubiqPrivacy.presence.title')" :description="t('cubiqPrivacy.presence.description')">
          <select
            class="field w-40 py-1.5"
            :value="settings.presenceVisibility"
            :disabled="saving !== null"
            :aria-label="t('cubiqPrivacy.presence.label')"
            @change="update('presenceVisibility', ($event.target as HTMLSelectElement).value === 'nobody' ? 'nobody' : 'friends')"
          >
            <option value="friends">{{ t('cubiqPrivacy.presence.friends') }}</option>
            <option value="nobody">{{ t('cubiqPrivacy.presence.nobody') }}</option>
          </select>
        </SettingRow>
        <SettingRow :title="t('cubiqPrivacy.server.title')" :description="t('cubiqPrivacy.server.description')">
          <ToggleSwitch
            :model-value="settings.shareServer"
            :label="t('cubiqPrivacy.server.title')"
            :disabled="saving !== null"
            @update:model-value="update('shareServer', $event)"
          />
        </SettingRow>
        <AchievementsVisibility />

        <h4 class="mt-5 mb-1 text-xs font-semibold tracking-wide text-base-400 uppercase">{{ t('cubiqPrivacy.chat.heading') }}</h4>
        <SettingRow :title="t('cubiqPrivacy.chat.receiptsTitle')" :description="t('cubiqPrivacy.chat.receiptsDescription')">
          <ToggleSwitch
            :model-value="settings.chatReadReceipts"
            :label="t('cubiqPrivacy.chat.receiptsTitle')"
            :disabled="saving !== null"
            data-testid="privacy-read-receipts"
            @update:model-value="update('chatReadReceipts', $event)"
          />
        </SettingRow>
        <SettingRow :title="t('cubiqPrivacy.chat.typingTitle')" :description="t('cubiqPrivacy.chat.typingDescription')">
          <ToggleSwitch
            :model-value="settings.chatTypingIndicator"
            :label="t('cubiqPrivacy.chat.typingTitle')"
            :disabled="saving !== null"
            data-testid="privacy-typing"
            @update:model-value="update('chatTypingIndicator', $event)"
          />
        </SettingRow>
        <SettingRow :title="t('cubiqPrivacy.chat.reportsTitle')" :description="t('cubiqPrivacy.chat.reportsDescription')">
          <button class="btn btn-ghost" @click="myReports = true">{{ t('cubiqPrivacy.chat.reportsButton') }}</button>
        </SettingRow>
        <SocialMyReportsDialog v-if="myReports" @close="myReports = false" />

        <SettingRow :title="t('websiteLogin.title')" :description="t('websiteLogin.description', { host: CUBIQ_HOST })">
          <div class="flex flex-wrap justify-end gap-2">
            <button class="btn btn-ghost" data-testid="settings-website-open" @click="openWebsite">
              {{ t('websiteLogin.open') }} <SocialIcon name="external" class="size-3.5" />
            </button>
            <button class="btn btn-primary" data-testid="settings-website-login" @click="webLogin.open()">
              {{ t('websiteLogin.button') }}
            </button>
          </div>
        </SettingRow>

        <SettingRow
          :title="t('cubiqPrivacy.delete.title')"
          :description="t('cubiqPrivacy.delete.description')"
          danger
          stacked
        >
          <button v-if="!confirmDelete" class="btn btn-danger" data-testid="cubiq-delete" @click="confirmDelete = true">{{ t('cubiqPrivacy.delete.button') }}</button>
          <div v-else class="rounded-lg border border-danger-600/50 bg-danger-900/30 px-3 py-3" role="alert">
            <i18n-t keypath="cubiqPrivacy.delete.confirm" tag="p" scope="global" class="text-sm text-danger-300">
              <template #name><strong>{{ cubiq.me?.name }}</strong></template>
            </i18n-t>
            <div class="mt-3 flex gap-2">
              <button class="btn btn-danger" :disabled="deleting" data-testid="cubiq-delete-confirm" @click="deleteAll">
                {{ deleting ? t('cubiqPrivacy.delete.deleting') : t('cubiqPrivacy.delete.confirmButton') }}
              </button>
              <button class="btn btn-ghost" :disabled="deleting" @click="confirmDelete = false">{{ t('common.actions.cancel') }}</button>
            </div>
          </div>
        </SettingRow>
      </template>
    </template>
  </div>
</template>
