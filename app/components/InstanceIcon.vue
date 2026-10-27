<script setup lang="ts">
import type { Instance } from '~/types'

// Instanz-Bild oder – ohne eigenes Bild – ein Pixel-Muster in der Farbe des Modloaders.
const props = withDefaults(defineProps<{ instance: Pick<Instance, 'id' | 'name' | 'loader' | 'iconPath'>; size?: number }>(), {
  size: 48,
})

const src = computed(() => instanceIconSrc(props.instance))
const failed = ref(false)
watch(src, () => (failed.value = false))
</script>

<template>
  <!-- Weiches Quadrat wie ein App-Symbol: Rundung wächst mit der Größe. -->
  <span
    class="squircle relative block shrink-0 overflow-hidden bg-base-800 ring-1 ring-white/5"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${Math.max(4, Math.round(size * 0.24))}px` }"
  >
    <img v-if="src && !failed" :src="src" alt="" class="size-full object-cover" draggable="false" @error="failed = true" />
    <PixelIdenticon v-else :seed="instance.id" :color="loaderColors[instance.loader.kind]" :letter="size >= 28 ? instance.name.charAt(0).toUpperCase() : null" />
  </span>
</template>
