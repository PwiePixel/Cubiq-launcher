<script setup lang="ts">
// Leerer Zustand: oben ein isometrischer Würfel aus dem Logo auf einem feinen Würfel-Raster, ein paar
// Pixel-Rauten treiben davon weg; darunter Titel, Text und Aktionen (Slot). Für „Noch keine …“ und
// „Nichts gefunden“ auf allen Seiten.
withDefaults(
  defineProps<{
    title: string
    text?: string
    /** Andere Anordnung der Rauten je Seite. */
    seed?: number
    /** Kompakter (z. B. „Nichts gefunden“ in Listen). */
    compact?: boolean
  }>(),
  { text: '', seed: 0x51, compact: false },
)
</script>

<template>
  <div class="empty card relative flex flex-col items-center overflow-hidden px-6 text-center" :class="compact ? 'pb-8' : 'pb-12'">
    <div class="empty-art relative w-full" :class="compact ? 'h-24' : 'h-36'" aria-hidden="true">
      <div class="iso-grid absolute inset-0" />
      <div class="empty-glow absolute inset-0" />
      <svg class="empty-cube" :class="compact ? 'size-16' : 'size-20'" viewBox="0 0 100 100">
        <!-- Würfel mit drei Flächen (hell oben, satt links, im Schatten rechts), wie die Mitte des Logos. -->
        <path class="face-top" d="M50 8 86.4 29 50 50 13.6 29Z" />
        <path class="face-left" d="M13.6 29 50 50v42L13.6 71Z" />
        <path class="face-right" d="M50 50l36.4-21v42L50 92Z" />
        <path d="M50 8 86.4 29v42L50 92 13.6 71V29Z M50 50 13.6 29 M50 50 86.4 29 M50 50v42" fill="none" class="edge" stroke-width="2.5" stroke-linejoin="round" />
      </svg>
      <!-- Pixel-Rauten wie die Spuren im Logo: treiben vom Würfel nach rechts oben weg. -->
      <span v-for="n in 4" :key="n" class="empty-bit diamond" :style="{ '--n': n, '--s': ((seed >> n) & 3) - 1.5 }" />
    </div>
    <h2 class="heading relative mt-4 text-lg">{{ title }}</h2>
    <p v-if="text" class="relative mx-auto mt-1.5 max-w-md text-sm text-base-400">{{ text }}</p>
    <div v-if="$slots.default" class="relative mt-5 flex flex-wrap justify-center gap-2">
      <slot />
    </div>
  </div>
</template>

<style scoped>
/* Das Raster läuft nach unten und zu den Seiten aus – weich in die Karte übergehen. */
.empty-art {
  margin-inline: -1.5rem;
  width: calc(100% + 3rem);
  -webkit-mask-image: radial-gradient(60% 100% at 50% 30%, #000 30%, transparent 85%);
  mask-image: radial-gradient(60% 100% at 50% 30%, #000 30%, transparent 85%);
}
.empty-glow {
  background: radial-gradient(40% 70% at 50% 60%, color-mix(in srgb, var(--color-primary-500) 20%, transparent), transparent 70%);
}
.empty-cube {
  position: absolute;
  left: 50%;
  bottom: 0.25rem;
  translate: -50% 0;
  filter: drop-shadow(0 12px 18px rgb(var(--shadow-rgb) / 0.6));
}
.face-top {
  fill: var(--color-primary-300);
}
.face-left {
  fill: var(--color-primary-500);
}
.face-right {
  fill: var(--color-primary-600);
}
.edge {
  stroke: color-mix(in srgb, var(--color-base-950) 35%, transparent);
}
/* Rauten: je weiter weg, desto kleiner und blasser. */
.empty-bit {
  position: absolute;
  left: calc(50% + 2.2rem + var(--n) * 0.95rem);
  bottom: calc(2.6rem + var(--n) * 0.55rem + var(--s) * 0.15rem);
  color: var(--color-primary-400);
  opacity: calc(0.85 - var(--n) * 0.17);
  scale: calc(1.1 - var(--n) * 0.12);
}
</style>
