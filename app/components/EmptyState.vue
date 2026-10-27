<script setup lang="ts">
// Leerer Zustand: oben ein ruhiges Würfel-Motiv auf dem Hintergrund der Szene, darunter Titel,
// Text und Aktionen (Slot). Für „Noch keine …“ und „Nichts gefunden“ auf allen Seiten.
withDefaults(
  defineProps<{
    title: string
    text?: string
    /** Andere Anordnung des Hintergrunds je Seite. */
    seed?: number
    /** Kompakter (z. B. „Nichts gefunden“ in Listen). */
    compact?: boolean
  }>(),
  { text: '', seed: 0x51, compact: false },
)
</script>

<template>
  <div class="card relative flex flex-col items-center overflow-hidden px-6 text-center" :class="compact ? 'pb-8' : 'pb-12'">
    <div class="empty-scene relative w-full" :class="compact ? 'h-20' : 'h-32'">
      <DesignScene fill :seed="seed" />
      <span class="empty-mark" :class="compact ? 'size-11' : 'size-14'" aria-hidden="true">
        <svg viewBox="0 0 100 100" fill="none" stroke="currentColor" stroke-width="6" stroke-linejoin="round" stroke-linecap="round">
          <path d="M50 8 86.4 29v42L50 92 13.6 71V29Z M50 50 13.6 29 M50 50 86.4 29 M50 50v42" />
        </svg>
      </span>
    </div>
    <h2 class="heading relative mt-4 text-lg">{{ title }}</h2>
    <p v-if="text" class="relative mx-auto mt-1.5 max-w-md text-sm text-base-400">{{ text }}</p>
    <div v-if="$slots.default" class="relative mt-5 flex flex-wrap justify-center gap-2">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* Der Hintergrund läuft oben aus – weich in die Karte übergehen. */
.empty-scene {
  margin-inline: -1.5rem;
  width: calc(100% + 3rem);
  mask-image: linear-gradient(180deg, #000 35%, transparent 100%);
}
.empty-mark {
  position: absolute;
  left: 50%;
  bottom: 0.25rem;
  translate: -50% 0;
  display: grid;
  place-items: center;
  padding: 0.6rem;
  border-radius: 1rem;
  color: var(--color-primary-400);
  background: color-mix(in srgb, var(--color-primary-500) 12%, var(--color-base-900));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--color-primary-500) 30%, transparent);
}
</style>
