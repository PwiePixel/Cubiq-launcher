<script setup lang="ts">
// Fortschritt als schlanker, abgerundeter Balken in der Akzentfarbe.
// `powered` = Dauerbetrieb (Spiel läuft) – dann ist der ganze Balken hell und atmet leicht.
// `indeterminate` = Fortschritt unbekannt – ein Lichtstreifen läuft immer wieder durch.
// `segments` bleibt für ältere Aufrufer erhalten und wird nicht mehr gebraucht.
const props = withDefaults(defineProps<{ percent?: number; powered?: boolean; segments?: number; indeterminate?: boolean }>(), {
  percent: 0,
  powered: false,
  segments: 24,
  indeterminate: false,
})

const events = useEventsStore()
const width = computed(() => (props.powered ? 100 : Math.min(100, Math.max(0, props.percent))))
</script>

<template>
  <!-- Halloween: jeder Fortschritt (Installieren, Starten, Downloads) wird zum Mondlauf. -->
  <MoonProgress v-if="events.lookHalloween" :percent="percent" :powered="powered" :indeterminate="indeterminate" />
  <div v-else class="progress" :class="{ powered, indeterminate }" aria-hidden="true">
    <span v-if="!indeterminate" class="progress-fill" :style="{ width: `${width}%` }" />
  </div>
</template>

<style scoped>
.progress {
  position: relative;
  height: 0.375rem;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-base-50) 8%, var(--color-base-800));
}
.progress-fill {
  position: absolute;
  inset-block: 0;
  left: 0;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--color-primary-600), var(--color-primary-400));
  box-shadow: 0 0 10px -2px var(--glow-primary);
  transition: width 0.3s ease;
}
.powered .progress-fill {
  background: linear-gradient(90deg, var(--color-live-400), var(--color-live-300));
  animation: powered 2.4s ease-in-out infinite;
}
.indeterminate::after {
  content: "";
  position: absolute;
  inset-block: 0;
  left: 0;
  width: 35%;
  border-radius: inherit;
  background: linear-gradient(90deg, transparent, var(--color-primary-400), transparent);
  animation: sweep 1.3s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}
@keyframes sweep {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(290%);
  }
}
@keyframes powered {
  50% {
    opacity: 0.7;
  }
}
:root[data-reduced-motion] .indeterminate::after {
  animation: none;
  width: 100%;
  opacity: 0.5;
}
</style>
