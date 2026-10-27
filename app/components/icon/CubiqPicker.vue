<script setup lang="ts">
import type { PixelGrid } from '~/utils/iconEditor'
import { gridDataUrl } from '~/utils/iconRender'
import { CUBIQ_ICON_IDS, cubiqIconGrid } from '~/utils/cubiqIcons'

// Fertige Cubiq-Symbole (eigene Pixel-Art) als Motiv.
defineProps<{ selected?: string | null }>()
const emit = defineEmits<{ pick: [value: { grid: PixelGrid; id: string }] }>()

const query = ref('')
const icons = CUBIQ_ICON_IDS.map((id) => ({ id, src: gridDataUrl(cubiqIconGrid(id)!) }))
const visible = computed(() => {
  const q = query.value.trim().toLowerCase()
  return q ? icons.filter((i) => t(`iconEditor.cubiq.names.${i.id}`).toLowerCase().includes(q) || i.id.includes(q)) : icons
})

function pick(id: string) {
  const grid = cubiqIconGrid(id)
  if (grid) emit('pick', { grid, id })
}
</script>

<template>
  <div>
    <input v-model="query" type="search" class="field mb-3" maxlength="64" :placeholder="t('iconEditor.cubiq.search')" :aria-label="t('iconEditor.cubiq.search')" />
    <p v-if="!visible.length" class="py-6 text-center text-sm text-base-400">{{ t('iconEditor.mc.empty') }}</p>
    <div class="grid max-h-80 grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-2 overflow-y-auto pr-1">
      <button
        v-for="icon in visible"
        :key="icon.id"
        type="button"
        class="flex flex-col items-center gap-1 rounded-lg border bg-base-900 px-1 py-2 transition-colors hover:border-primary-500 hover:bg-base-850"
        :class="selected === icon.id ? 'border-primary-500 bg-primary-900/40' : 'border-base-800'"
        @click="pick(icon.id)"
      >
        <img :src="icon.src" alt="" class="size-10" style="image-rendering: pixelated" draggable="false" />
        <span class="w-full truncate text-center text-[11px] text-base-300">{{ t(`iconEditor.cubiq.names.${icon.id}`) }}</span>
      </button>
    </div>
  </div>
</template>
