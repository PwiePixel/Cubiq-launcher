<script setup lang="ts">
// Start-Animation: das Cubiq-Logo blendet über dem startenden Launcher ein, ein weicher grüner Schein
// zieht auf, dann gibt die Fläche den Launcher frei (rund 1,6 s). Ein Klick oder eine Taste überspringt.
// Aus bei ausgeschalteter Einstellung und bei „Animationen: reduziert“; „Jetzt abspielen“ in den
// Einstellungen spielt sie erneut.
import { sharedMotion } from '~/utils/motion'
import { introReplay, readIntroPrefs, shouldPlayIntro } from '~/utils/startIntro'

const DURATION_MS = 1600
const LEAVE_MS = 380

const visible = ref(false)
const leaving = ref(false)
let timer: ReturnType<typeof setTimeout> | undefined

function play() {
  clearTimeout(timer)
  leaving.value = false
  visible.value = true
  timer = setTimeout(skip, DURATION_MS)
}

function skip() {
  if (!visible.value || leaving.value) return
  clearTimeout(timer)
  leaving.value = true
  timer = setTimeout(() => {
    visible.value = false
    leaving.value = false
  }, LEAVE_MS)
}

function onKey() {
  skip()
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  if (shouldPlayIntro(readIntroPrefs(), sharedMotion().systemReduced.value)) play()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  clearTimeout(timer)
})

watch(introReplay, () => play())
</script>

<template>
  <div
    v-if="visible"
    class="start-intro"
    :class="{ leaving }"
    role="img"
    :aria-label="t('startIntro.label')"
    data-testid="start-intro"
    @click="skip"
  >
    <div class="start-intro-glow" aria-hidden="true" />
    <div class="start-intro-mark">
      <img src="/logo.png" alt="" width="132" height="132" draggable="false" />
      <p class="start-intro-name display">Cubiq <span>Launcher</span></p>
    </div>
    <span class="start-intro-skip" aria-hidden="true">{{ t('startIntro.skip') }}</span>
  </div>
</template>

<style scoped>
.start-intro {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  background: var(--color-base-950);
  cursor: pointer;
  transition: opacity 0.38s ease;
}
.start-intro.leaving {
  opacity: 0;
}
.start-intro-glow {
  position: absolute;
  width: 46rem;
  height: 46rem;
  border-radius: 50%;
  background: radial-gradient(closest-side, color-mix(in srgb, var(--color-primary-500) 30%, transparent), transparent 72%);
  opacity: 0;
  animation: intro-glow 1.2s ease-out 0.1s forwards;
}
.start-intro-mark {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.1rem;
}
.start-intro-mark img {
  opacity: 0;
  transform: scale(0.86);
  filter: drop-shadow(0 12px 32px rgb(0 0 0 / 0.45));
  animation: intro-mark 0.6s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
}
.start-intro-name {
  font-size: 1.75rem;
  line-height: 1;
  color: var(--color-base-50);
  opacity: 0;
  transform: translateY(6px);
  animation: intro-name 0.45s ease-out 0.32s forwards;
}
.start-intro-name span {
  color: var(--color-primary-300);
}
.start-intro-skip {
  position: absolute;
  bottom: 2rem;
  font-size: 0.75rem;
  color: var(--color-base-400);
  opacity: 0;
  animation: intro-name 0.4s ease-out 0.7s forwards;
}
.leaving .start-intro-mark img {
  transform: scale(1.04);
  transition: transform 0.38s ease;
}
@keyframes intro-mark {
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes intro-name {
  to {
    opacity: 1;
    transform: none;
  }
}
@keyframes intro-glow {
  from {
    opacity: 0;
    transform: scale(0.6);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
</style>
