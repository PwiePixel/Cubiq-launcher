<script setup lang="ts">
// Hintergrund-Szene des Launchers (Startseite, Seiten-Hintergrund, Leer-Zustände, Big Picture).
// Solange Halloween läuft, überall die Mondnacht – das Event geht vor dem Design.
// `noEvent` behält das Design (z. B. Update-Titelbilder).
const props = defineProps<{
  mode?: 'idle' | 'starting' | 'running'
  progress?: number
  anchor?: HTMLElement | null
  fill?: boolean
  seed?: number
  /** Standbild statt Animation (Einstellung „Animierter Hintergrund“ aus). */
  still?: boolean
  /** Halloween: Blitze an die Atmosphäre melden – nur die eine große Hintergrund-Szene. */
  bolts?: boolean
  /** Event-Szene nie zeigen, immer das Design. */
  noEvent?: boolean
}>()

const events = useEventsStore()
const moonnight = computed(() => events.lookHalloween && !props.noEvent)
</script>

<template>
  <MoonnightScene v-if="moonnight" :seed="seed" :still="still" :bolts="bolts">
    <slot />
  </MoonnightScene>
  <CubiqScene v-else :mode="mode" :progress="progress" :anchor="anchor" :fill="fill" :seed="seed" :still="still">
    <slot />
  </CubiqScene>
</template>
