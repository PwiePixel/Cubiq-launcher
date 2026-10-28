<script setup lang="ts">
// `huge` = Werkstatt-Dialoge mit Vorschau (Umhang zuschneiden, Umhang prüfen), `giant` = Editor (Umhang-Werkstatt).
// Am Handy: Bottom-Sheet über die ganze Breite, nach unten wegwischbar, Zurück-Taste schließt.
defineProps<{ title: string; wide?: boolean; huge?: boolean; giant?: boolean }>()
const emit = defineEmits<{ close: [] }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

useOverlay(() => emit('close'))
const swipe = useSheetSwipe(() => emit('close'))
const mobile = mobileUi
// Auftritt/Abtritt: Desktop leicht heranzoomen, Handy als Sheet von unten.
const backdrop = useTemplateRef<HTMLElement>('backdrop')
useOverlayMotion(backdrop, () => (mobile.value ? 'sheet' : 'dialog'))
</script>

<template>
  <div ref="backdrop" class="dialog-backdrop fixed inset-0 z-50 flex items-center justify-center p-6 mobile:items-end mobile:p-0 mobile:pt-[calc(var(--safe-top)+1.5rem)]" data-dialog-over-settings @mousedown.self="emit('close')">
    <section
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      data-motion-panel
      class="dialog panel w-full bg-base-850 mobile:flex mobile:max-h-full mobile:max-w-none mobile:flex-col mobile:rounded-b-none mobile:rounded-t-3xl mobile:transition-transform"
      :class="giant ? 'flex max-h-full max-w-7xl flex-col' : huge ? 'flex max-h-full max-w-5xl flex-col' : wide ? 'max-w-2xl' : 'max-w-md'"
      :style="swipe.style.value"
    >
      <!-- Kopf: Würfel-Zeichen und Titel auf einer Fläche mit feinem Würfel-Raster. -->
      <header class="dialog-head relative isolate border-b border-line px-5 py-3.5 mobile:relative mobile:shrink-0 mobile:touch-none mobile:pt-5" v-on="mobile ? swipe.handlers : {}">
        <!-- Zwei Ebenen: außen das Ausblenden nach links, innen das Raster (beides sind Masken). -->
        <span class="dialog-grid" aria-hidden="true"><span class="iso-grid absolute inset-0" /></span>
        <span v-if="mobile" class="absolute top-2 left-1/2 h-1 w-10 -translate-x-1/2 rounded-full bg-base-700" aria-hidden="true" />
        <h2 class="display flex items-center gap-2.5 text-[15px] text-base-50"><CubeMark :size="15" />{{ title }}</h2>
      </header>
      <div class="px-5 py-4 mobile:min-h-0 mobile:flex-1 mobile:overflow-y-auto mobile:overscroll-contain" :class="{ 'min-h-0 overflow-y-auto': huge || giant }">
        <slot />
      </div>
      <footer class="dialog-foot flex justify-end gap-2 border-t border-line px-5 py-3 mobile:shrink-0 mobile:flex-wrap mobile:pb-[calc(0.75rem+var(--safe-bottom))]">
        <slot name="actions" />
      </footer>
    </section>
  </div>
</template>

<style scoped>
/* Hintergrund: tiefes Dunkel, leicht weichgezeichnet. */
.dialog-backdrop {
  background: rgb(var(--shadow-rgb) / 0.62);
  backdrop-filter: blur(3px);
}
.dialog {
  box-shadow: var(--edge-light), var(--elev-3);
}
/* Kopf und Fuß folgen den Ecken der Karte (ohne den ganzen Dialog abzuschneiden – Menüs dürfen hinaus). */
.dialog-head {
  overflow: hidden;
  border-top-left-radius: inherit;
  border-top-right-radius: inherit;
  corner-shape: inherit;
}
.dialog-foot {
  border-bottom-left-radius: inherit;
  border-bottom-right-radius: inherit;
  corner-shape: inherit;
}
.dialog-grid {
  position: absolute;
  inset: 0;
  z-index: -1;
  -webkit-mask-image: linear-gradient(90deg, transparent 35%, #000);
  mask-image: linear-gradient(90deg, transparent 35%, #000);
}
.dialog-foot {
  background: color-mix(in srgb, var(--color-base-950) 35%, var(--color-base-850));
}
</style>
