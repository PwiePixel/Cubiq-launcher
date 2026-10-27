<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <!-- Seitenkopf: der Würfel aus dem Logo als Zeichen vor dem Titel, Aktionen rechts. -->
  <!-- Handy: Titel oben, die Aktionen darunter als wischbare Reihe (Seiten haben dort p-4). -->
  <div class="mb-6 flex items-end justify-between gap-4 mobile:mb-4 mobile:flex-col mobile:items-stretch mobile:gap-3">
    <div class="min-w-0">
      <h1 class="display flex items-center gap-3 text-[1.625rem] leading-tight text-base-50 mobile:text-2xl">
        <CubeMark :size="22" />
        <span class="min-w-0 truncate">{{ title }}</span>
      </h1>
      <p v-if="subtitle" class="mt-1.5 text-sm text-base-400">{{ subtitle }}</p>
    </div>
    <div class="flex shrink-0 items-center gap-2 mobile-scroll-x mobile:-mx-4 mobile:px-4 mobile:empty:hidden mobile:[&>*]:shrink-0">
      <slot />
    </div>
  </div>
</template>
