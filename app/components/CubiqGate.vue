<script setup lang="ts">
// Zeigt statt des Inhalts einen ruhigen Hinweis, solange die Cubiq-Dienste nicht
// nutzbar sind: nicht zugestimmt, kein Account, gesperrt oder offline.
/** Wofür die Dienste gebraucht werden – bestimmt den Hinweistext. */
defineProps<{ what: 'friends' | 'capes' | 'cosmetics' | 'shares' | 'achievements' | 'remote' | 'creator' }>()
const cubiq = useCubiqStore()
const accounts = useAccountsStore()

const state = computed(() => {
  if (!cubiq.status) return 'loading'
  if (!cubiq.available) return 'unavailable'
  if (!cubiq.enabled) return 'consent'
  if (!accounts.active) return 'account'
  if (cubiq.problem === 'banned') return 'banned'
  return 'ok'
})
</script>

<template>
  <slot v-if="state === 'ok'" />
  <div v-else-if="state === 'loading'" class="skeleton h-28" />
  <div v-else class="card flex items-start gap-3 px-4 py-4">
    <span class="grid size-9 shrink-0 place-items-center rounded-lg bg-primary-900/40 text-primary-300">
      <svg viewBox="0 0 24 24" class="size-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path :d="icons.cubiq" />
      </svg>
    </span>
    <div class="min-w-0 flex-1 text-sm">
      <template v-if="state === 'unavailable'">
        <p class="font-semibold text-base-50">{{ t('cubiqGate.unavailable.title') }}</p>
        <p class="mt-0.5 text-base-400">{{ t('cubiqGate.unavailable.text') }}</p>
      </template>
      <template v-else-if="state === 'consent'">
        <p class="font-semibold text-base-50">{{ t('cubiqGate.consent.title') }}</p>
        <p class="mt-0.5 text-base-400">{{ t(`cubiqGate.consent.${what}`) }}</p>
        <button class="btn btn-primary mt-3 px-3 py-1.5 text-xs" @click="cubiq.askConsent()">{{ t('cubiqGate.consent.button') }}</button>
      </template>
      <template v-else-if="state === 'account'">
        <p class="font-semibold text-base-50">{{ t('cubiqGate.account.title') }}</p>
        <p class="mt-0.5 text-base-400">{{ t('cubiqGate.account.text') }}</p>
        <NuxtLink to="/accounts" class="btn btn-ghost mt-3 px-3 py-1.5 text-xs">{{ t('cubiqGate.account.button') }}</NuxtLink>
      </template>
      <template v-else>
        <p class="font-semibold text-base-50">{{ t('cubiqGate.banned.title') }}</p>
        <p class="mt-0.5 text-base-400">{{ t('cubiqGate.banned.text') }}</p>
      </template>
    </div>
  </div>
</template>
