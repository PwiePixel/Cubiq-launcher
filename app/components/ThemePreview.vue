<script setup lang="ts">
// Kleine Vorschau eines Farbschemas (Einstellungen → Aussehen): der Fensterrahmen mit dem Dock links –
// der gewählte Eintrag als grüne Fläche –, daneben die Leinwand mit einer Karte und dem Spielen-Knopf als
// grüne Pille. Grün folgt der gewählten Akzentfarbe.
defineProps<{ frame: string; canvas: string; card: string; line: string; ink: string }>()
</script>

<template>
  <div class="mini" :style="{ background: frame }" aria-hidden="true">
    <div class="mini-dock">
      <span class="mini-pick" />
      <span class="mini-row" :style="{ background: ink }" />
      <span class="mini-row mini-row-short" :style="{ background: ink }" />
      <span class="mini-row" :style="{ background: ink }" />
    </div>
    <div class="mini-canvas" :style="{ background: canvas, borderColor: line }">
      <div class="mini-card" :style="{ background: card, borderColor: line }">
        <span class="mini-title" :style="{ background: ink }" />
        <span class="mini-play" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.mini {
  position: absolute;
  inset: 0;
}
.mini-dock {
  position: absolute;
  top: 0.6rem;
  bottom: 0.6rem;
  left: 0.5rem;
  display: flex;
  width: 1.75rem;
  flex-direction: column;
  gap: 0.3rem;
}
.mini-pick {
  height: 0.4rem;
  border-radius: 999px;
  background: var(--color-primary-500);
}
.mini-row {
  height: 0.25rem;
  border-radius: 999px;
}
.mini-row-short {
  width: 70%;
}
.mini-canvas {
  position: absolute;
  inset: 0.35rem 0.35rem 0.35rem 2.65rem;
  border: 1px solid;
  border-radius: 0.6rem;
}
.mini-card {
  position: absolute;
  inset: 0.45rem;
  border: 1px solid;
  border-radius: 0.5rem;
}
.mini-title {
  position: absolute;
  top: 0.55rem;
  left: 0.5rem;
  width: 45%;
  height: 0.35rem;
  border-radius: 999px;
}
.mini-play {
  position: absolute;
  bottom: 0.5rem;
  left: 0.5rem;
  width: 2.25rem;
  height: 0.6rem;
  border-radius: 999px;
  background: var(--color-primary-500);
}
</style>
