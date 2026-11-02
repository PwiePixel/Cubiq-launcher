<script setup lang="ts">
import { OFFICIAL_DOWNLOAD_URL, installationName } from '~/utils/officialLauncher'

// Dialoge rund um „Mit dem Minecraft Launcher spielen“: die kurze Erklärung vor dem ersten Mal und was nach
// der Übergabe zu tun ist (dort spielen, Launcher erst schließen, installieren, Flatpak freigeben …).
// Einmal im Layout eingebunden – der Zustand steht im Store.
const official = useOfficialLauncherStore()
const dialog = computed(() => official.dialog)
const result = computed(() => (dialog.value?.kind === 'result' ? dialog.value : null))
const view = computed(() => result.value?.view ?? null)
const installation = computed(() => (dialog.value ? installationName(dialog.value.name) : ''))

const copied = ref(false)
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    // Dann eben abtippen – der Befehl steht markierbar da.
  }
}

function openDownload() {
  backend.openExternalUrl(OFFICIAL_DOWNLOAD_URL).catch((e) => useToasts().error(e))
}

async function openLauncher() {
  if (!(await official.openLauncher())) useToasts().error(t('officialLauncher.dialog.openFailed'))
}
</script>

<template>
  <!-- Vor dem ersten Mal: was gleich passiert -->
  <BaseDialog v-if="dialog?.kind === 'explain'" :title="t('officialLauncher.dialog.explainTitle')" @close="official.answerExplain(false)">
    <div data-testid="official-explain">
      <p class="text-sm text-base-200">{{ t('officialLauncher.dialog.explainIntro') }}</p>
      <ol class="mt-3 space-y-2 text-sm text-base-200">
        <li class="flex gap-2.5"><span class="display text-primary-300">1</span><span>{{ t('officialLauncher.dialog.step1', { name: dialog.name }) }}</span></li>
        <li class="flex gap-2.5"><span class="display text-primary-300">2</span><span>{{ t('officialLauncher.dialog.step2', { installation }) }}</span></li>
        <li class="flex gap-2.5"><span class="display text-primary-300">3</span><span>{{ t('officialLauncher.dialog.step3') }}</span></li>
      </ol>
      <p class="mt-3 rounded-md bg-base-900 px-3 py-2 text-xs text-base-400">{{ t('officialLauncher.dialog.explainNote') }}</p>
    </div>
    <template #actions>
      <button class="btn btn-ghost" @click="official.answerExplain(false)">{{ t('common.actions.cancel') }}</button>
      <button class="btn btn-primary" data-testid="official-explain-continue" @click="official.answerExplain(true)">{{ t('common.actions.continue') }}</button>
    </template>
  </BaseDialog>

  <!-- Eingetragen: dort anmelden, auswählen, spielen -->
  <BaseDialog v-else-if="view?.kind === 'done' && result" :title="view.opened ? t('officialLauncher.dialog.doneTitle') : t('officialLauncher.dialog.manualTitle')" @close="official.close()">
    <div data-testid="official-done">
      <p class="text-sm text-base-200">
        {{ view.opened ? t('officialLauncher.dialog.doneText', { installation: view.installation }) : t('officialLauncher.dialog.manualText', { installation: view.installation }) }}
      </p>
      <p v-if="result.join.kind === 'serverList'" class="mt-2 text-sm text-base-300">{{ t('officialLauncher.dialog.joinServer', { server: result.join.name }) }}</p>
      <p v-else-if="result.join.kind === 'address'" class="mt-2 text-sm text-base-300">
        <i18n-t keypath="officialLauncher.dialog.joinAddress" tag="span" scope="global">
          <template #address><span class="font-mono text-base-50 select-text">{{ result.join.address }}</span></template>
        </i18n-t>
      </p>
      <p v-if="result.forced && view.opened" class="mt-2 text-xs text-caution-300">{{ t('officialLauncher.dialog.restartHint') }}</p>
      <p class="mt-3 text-xs text-base-400">{{ t('officialLauncher.dialog.limits') }}</p>
    </div>
    <template #actions>
      <button v-if="!view.opened" class="btn btn-ghost" @click="openLauncher">{{ t('officialLauncher.dialog.openLauncher') }}</button>
      <button class="btn btn-primary" data-bp-autofocus @click="official.close()">{{ t('common.actions.done') }}</button>
    </template>
  </BaseDialog>

  <!-- Läuft gerade: erst schließen -->
  <BaseDialog v-else-if="view?.kind === 'running'" :title="t('officialLauncher.dialog.runningTitle')" @close="official.close()">
    <p class="text-sm text-base-200">{{ t('officialLauncher.dialog.runningText') }}</p>
    <p class="mt-2 text-xs text-base-400">{{ t('officialLauncher.dialog.runningAnyway', { installation }) }}</p>
    <template #actions>
      <button class="btn btn-ghost" @click="official.close()">{{ t('common.actions.cancel') }}</button>
      <button class="btn btn-ghost" data-testid="official-continue-anyway" @click="official.retry(true)">{{ t('officialLauncher.dialog.continueAnyway') }}</button>
      <button class="btn btn-primary" @click="official.retry()">{{ t('officialLauncher.dialog.tryAgain') }}</button>
    </template>
  </BaseDialog>

  <!-- Nicht installiert -->
  <BaseDialog v-else-if="view?.kind === 'notInstalled'" :title="t('officialLauncher.dialog.notInstalledTitle')" @close="official.close()">
    <p class="text-sm text-base-200">{{ t('officialLauncher.dialog.notInstalledText') }}</p>
    <template #actions>
      <button class="btn btn-ghost" @click="official.close()">{{ t('common.actions.close') }}</button>
      <button class="btn btn-ghost" @click="openDownload">{{ t('officialLauncher.dialog.download') }}</button>
      <button class="btn btn-primary" @click="official.retry()">{{ t('officialLauncher.dialog.tryAgain') }}</button>
    </template>
  </BaseDialog>

  <!-- Installiert, aber noch nie geöffnet -->
  <BaseDialog v-else-if="view?.kind === 'neverOpened'" :title="t('officialLauncher.dialog.neverOpenedTitle')" @close="official.close()">
    <p class="text-sm text-base-200">{{ t('officialLauncher.dialog.neverOpenedText') }}</p>
    <template #actions>
      <button class="btn btn-ghost" @click="official.close()">{{ t('common.actions.close') }}</button>
      <button v-if="view.canOpen" class="btn btn-ghost" @click="openLauncher">{{ t('officialLauncher.dialog.openLauncher') }}</button>
      <button class="btn btn-primary" @click="official.retry()">{{ t('officialLauncher.dialog.tryAgain') }}</button>
    </template>
  </BaseDialog>

  <!-- Flatpak: Zugriff auf den Instanzordner freigeben -->
  <BaseDialog v-else-if="view?.kind === 'needsAccess'" :title="t('officialLauncher.dialog.accessTitle')" wide @close="official.close()">
    <p class="text-sm text-base-200">{{ t('officialLauncher.dialog.accessText') }}</p>
    <pre class="mt-3 overflow-x-auto rounded-md border border-base-700 bg-base-950 p-3 font-mono text-xs whitespace-pre-wrap break-all text-base-50 select-text" data-testid="official-access-command">{{ view.command }}</pre>
    <p class="mt-2 text-xs text-base-400">{{ t('officialLauncher.dialog.accessNote') }}</p>
    <template #actions>
      <button class="btn btn-ghost" @click="official.close()">{{ t('common.actions.cancel') }}</button>
      <button class="btn btn-ghost" @click="copy(view.command)">{{ copied ? t('common.status.copied') : t('officialLauncher.dialog.copyCommand') }}</button>
      <button class="btn btn-primary" @click="official.retry()">{{ t('officialLauncher.dialog.tryAgain') }}</button>
    </template>
  </BaseDialog>
</template>
