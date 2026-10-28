<script setup lang="ts">
// Kleiner isometrischer Würfel – die Mitte des Cubiq-Logos als Zeichen. Sparsam: vor Seiten- und
// Abschnittstiteln, in Leer-Zuständen und Dialogköpfen. `outline` = nur Kanten (ruhiger).
withDefaults(defineProps<{ size?: number; outline?: boolean }>(), { size: 14, outline: false })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" class="cube-mark shrink-0" aria-hidden="true" focusable="false">
    <path v-if="outline" d="M12 2 20.66 7v10L12 22l-8.66-5V7Z M12 12 3.34 7 M12 12l8.66-5 M12 12v10" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
    <template v-else>
      <path class="cube-top" d="M12 2 20.66 7 12 12 3.34 7Z" />
      <path class="cube-left" d="M3.34 7 12 12v10l-8.66-5Z" />
      <path class="cube-right" d="M12 12l8.66-5v10L12 22Z" />
    </template>
  </svg>
</template>

<style scoped>
.cube-mark {
  color: var(--color-primary-400);
}
/* Drei Flächen in drei Stufen der Akzentfarbe: oben hell, links satt, rechts im Schatten. */
.cube-top {
  fill: var(--color-primary-300);
}
.cube-left {
  fill: var(--color-primary-500);
}
.cube-right {
  fill: var(--color-primary-600);
}
</style>
