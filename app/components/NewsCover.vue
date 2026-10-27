<script setup lang="ts">
import type { NewsItem } from '~/types'

// Titelbild einer Meldung. Mit Bild: das Bild. Launcher-Versionen: ihr Update-Banner (ohne Schrift – der
// Titel steht auf der Karte); ohne Changelog-Eintrag die Hintergrund-Szene.
// Modrinth (nur Icons): Icon groß und scharf auf einem weichgezeichneten Hintergrund aus sich selbst.
// Sonst: eine ruhige Fläche mit dem Würfel-Raster.
defineProps<{ item: NewsItem; image?: string; post?: ChangelogEntry | null; large?: boolean }>()
</script>

<template>
  <UpdateBanner
    v-if="item.source === 'launcher' && post?.banner"
    kicker=""
    :title="item.title"
    :accent="post.banner.accent"
    :motif="post.banner.motif"
    :anim="post.banner.anim"
    bare
    class="absolute inset-0"
  />
  <DesignScene v-else-if="item.source === 'launcher'" fill no-event :seed="versionSeed(post?.version ?? item.title)" class="absolute inset-0" />
  <template v-else-if="image && item.source === 'modrinth'">
    <img :src="image" alt="" class="absolute inset-0 size-full scale-125 object-cover opacity-60 blur-xl" />
    <div class="absolute inset-0 grid place-items-center">
      <img
        :src="image"
        alt=""
        loading="lazy"
        class="rounded-xl shadow-2xl ring-1 ring-white/10 [image-rendering:pixelated] transition-transform duration-500 group-hover:scale-105"
        :class="large ? 'size-28' : 'size-16'"
      />
    </div>
  </template>
  <img
    v-else-if="image"
    :src="image"
    alt=""
    loading="lazy"
    class="absolute inset-0 size-full object-cover transition-transform duration-500 group-hover:scale-[1.03]"
  />
  <div v-else class="cover-fallback absolute inset-0" />
</template>

<style scoped>
/* Platzhalter ohne Bild: ruhige Fläche mit feinem Würfel-Raster und einem Schein in der Akzentfarbe. */
.cover-fallback {
  background:
    radial-gradient(70% 90% at 75% 40%, color-mix(in srgb, var(--color-primary-500) 22%, transparent), transparent 70%),
    linear-gradient(30deg, color-mix(in srgb, var(--color-primary-500) 10%, transparent) 1px, transparent 1px) 0 0 / 34px 20px,
    linear-gradient(150deg, color-mix(in srgb, var(--color-primary-500) 10%, transparent) 1px, transparent 1px) 0 0 / 34px 20px,
    var(--color-base-850);
}
</style>
