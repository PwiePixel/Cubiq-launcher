<script setup lang="ts">
// Einwilligung vor der ersten Anfrage an die Cubiq API. Ohne Zustimmung sendet
// der Launcher nichts an den Cubiq-Server; ändern lässt sich das jederzeit unter
// Einstellungen → Datenschutz.
const cubiq = useCubiqStore()
const toasts = useToasts()
const busy = ref(false)

async function decide(accepted: boolean) {
  if (busy.value) return
  busy.value = true
  try {
    await cubiq.setConsent(accepted)
    if (accepted) toasts.ok(t('cubiqConsent.enabled'))
  } catch (e) {
    toasts.error(e)
  } finally {
    busy.value = false
  }
}

function openPrivacy() {
  backend.openExternalUrl(CUBIQ_PRIVACY_URL).catch((e) => toasts.error(e))
}
</script>

<template>
  <BaseDialog :title="t('cubiqConsent.title')" wide @close="cubiq.consentOpen = false">
    <div class="space-y-3 text-sm text-base-200">
      <i18n-t keypath="cubiqConsent.intro" tag="p" scope="global">
        <template #capes><strong class="text-base-50">{{ t('cubiqConsent.capes') }}</strong></template>
        <template #friendsList><strong class="text-base-50">{{ t('cubiqConsent.friendsList') }}</strong></template>
        <template #host><span class="font-mono text-xs">{{ CUBIQ_HOST }}</span></template>
      </i18n-t>
      <div class="rounded-lg border border-base-700 bg-base-850 px-3 py-2.5">
        <p class="mb-1.5 text-xs font-semibold text-base-50">{{ t('cubiqConsent.storedTitle') }}</p>
        <ul class="list-disc space-y-0.5 pl-4 text-xs text-base-200">
          <li>{{ t('cubiqConsent.stored.identity') }}</li>
          <li>{{ t('cubiqConsent.stored.capes') }}</li>
          <li>{{ t('cubiqConsent.stored.friends') }}</li>
          <li>{{ t('cubiqConsent.stored.chat') }}</li>
          <li>{{ t('cubiqConsent.stored.shares') }}</li>
          <li>{{ t('cubiqConsent.stored.settings') }}</li>
          <li>{{ t('cubiqConsent.stored.sync') }}</li>
          <li>{{ t('cubiqConsent.stored.presence') }}</li>
        </ul>
      </div>
      <p class="text-xs text-base-400">
        <i18n-t keypath="cubiqConsent.footer" tag="span" scope="global">
          <template #path><em>{{ t('cubiqConsent.settingsPath') }}</em></template>
        </i18n-t>
        {{ ' ' }}
        <button class="text-primary-300 hover:underline" @click="openPrivacy">{{ t('cubiqConsent.readPolicy') }}</button>
      </p>
    </div>
    <template #actions>
      <button class="btn btn-ghost" :disabled="busy" @click="decide(false)">{{ t('cubiqConsent.decline') }}</button>
      <button class="btn btn-primary" :disabled="busy" data-testid="cubiq-consent-accept" @click="decide(true)">
        {{ busy ? t('cubiqConsent.wait') : t('cubiqConsent.accept') }}
      </button>
    </template>
  </BaseDialog>
</template>
