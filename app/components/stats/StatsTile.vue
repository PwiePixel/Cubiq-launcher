<script setup lang="ts">
import type { IconName } from '~/utils/icons'

// Kennzahl-Kachel: Beschriftung, große Zahl, darunter ein kleiner Zusatz.
defineProps<{ label: string; value: string; hint?: string; icon: IconName; testid?: string }>()
</script>

<template>
  <div class="tile card relative overflow-hidden p-4" :data-testid="testid">
    <div class="flex items-center gap-2 text-xs font-medium text-base-400">
      <span class="grid size-6 place-items-center rounded-md bg-base-800 text-primary-300" aria-hidden="true">
        <svg viewBox="0 0 24 24" class="size-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="icons[icon]" /></svg>
      </span>
      {{ label }}
    </div>
    <p class="mt-2.5 text-2xl leading-tight font-semibold text-base-50 mobile:text-lg">{{ value }}</p>
    <p v-if="hint" class="mt-1 truncate text-xs text-base-400" :title="hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
/* Ein Hauch Akzent in der Ecke – wie die Würfel-Flächen im Logo. */
.tile::after {
  content: '';
  position: absolute;
  inset: 0 0 auto auto;
  width: 5rem;
  height: 5rem;
  background: radial-gradient(circle at 100% 0, color-mix(in srgb, var(--color-primary-500) 12%, transparent), transparent 70%);
  pointer-events: none;
}
</style>
