<script setup lang="ts">
// Banner eines Updates – feste Vorlage: dunkler Grund, weicher Schein in der Farbe des Updates,
// feines Würfel-Raster wie im Logo, Schrift mit Version links und das Motiv rechts.
// Je Update ändern sich nur Akzentfarbe und Motiv (Angabe im CHANGELOG.md); ohne eigenes Motiv das Cubiq-Logo.
// Gibt es eine bewegte Fassung des Motivs, läuft sie – außer bei „Weniger Animationen“, dann das ruhige Bild.
const props = withDefaults(
  defineProps<{
    /** z. B. „v0.4.3 · 24. September 2026“ */
    kicker: string
    title: string
    accent?: string | null
    /** Bildadresse des Motivs, sonst das Cubiq-Logo. */
    motif?: string | null
    /** Bewegte Fassung des Motivs (animiertes PNG), sonst `null`. */
    anim?: string | null
    size?: 'sm' | 'md' | 'lg'
    tag?: 'h1' | 'h2' | 'h3' | 'p'
    /** Ohne Schrift (wenn der Titel schon daneben steht). */
    bare?: boolean
  }>(),
  { accent: null, motif: null, anim: null, size: 'md', tag: 'h3', bare: false },
)

const { reduced } = useMotion()
const src = computed(() => (!reduced.value && props.motif && props.anim) || props.motif || '/logo.png')

const style = computed(() => ({ '--accent': props.accent && /^#[0-9a-f]{6}$/i.test(props.accent) ? props.accent : '#71a330' }))
</script>

<template>
  <div class="update-banner" :class="`ub-${size}`" :style="style">
    <div class="ub-glow" aria-hidden="true" />
    <div class="ub-grid" aria-hidden="true" />
    <img :src="src" alt="" class="ub-motif" loading="lazy" decoding="async" />
    <div v-if="!bare" class="ub-text">
      <p class="ub-kicker">{{ kicker }}</p>
      <component :is="tag" class="ub-title">{{ title }}</component>
      <span class="ub-bar" aria-hidden="true" />
    </div>
    <slot />
  </div>
</template>

<style scoped>
.update-banner {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  height: 100%;
  min-height: 8rem;
  background: linear-gradient(135deg, #111613 0%, #0c100e 100%);
}
/* Schein in der Farbe des Updates hinter dem Motiv, links dunkel für die Schrift. */
.ub-glow {
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(60% 95% at 78% 50%, color-mix(in srgb, var(--accent) 34%, transparent), transparent 70%),
    linear-gradient(90deg, rgb(10 13 11 / 0.9) 0%, rgb(10 13 11 / 0.5) 45%, rgb(10 13 11 / 0.05) 75%);
}
/* Feines isometrisches Raster, nach links ausgeblendet. */
.ub-grid {
  position: absolute;
  inset: 0;
  z-index: -1;
  opacity: 0.5;
  background-image:
    linear-gradient(30deg, color-mix(in srgb, var(--accent) 16%, transparent) 1px, transparent 1px),
    linear-gradient(150deg, color-mix(in srgb, var(--accent) 16%, transparent) 1px, transparent 1px);
  background-size: 34px 20px;
  -webkit-mask-image: linear-gradient(90deg, transparent 20%, #000 75%);
  mask-image: linear-gradient(90deg, transparent 20%, #000 75%);
}
.ub-motif {
  position: absolute;
  right: 5%;
  top: 50%;
  height: 84%;
  aspect-ratio: 1;
  translate: 0 -50%;
  object-fit: contain;
  filter: drop-shadow(0 10px 24px rgb(0 0 0 / 0.5)) drop-shadow(0 0 26px color-mix(in srgb, var(--accent) 35%, transparent));
  animation: ub-float 6s ease-in-out infinite;
}
@keyframes ub-float {
  50% {
    transform: translateY(-4px);
  }
}
:root[data-reduced-motion] .ub-motif {
  animation: none;
}
.ub-text {
  position: absolute;
  left: 1.5rem;
  right: 42%;
  bottom: 1.4rem;
}
.ub-kicker {
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--accent) 55%, #ffffff);
}
.ub-title {
  margin-top: 0.3rem;
  font-family: var(--font-display);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.05;
  color: #f3f6f4;
  text-wrap: balance;
}
.ub-bar {
  display: block;
  margin-top: 0.7rem;
  width: 3rem;
  height: 3px;
  border-radius: 999px;
  background: var(--accent);
}
.ub-sm .ub-title {
  font-size: 1.5rem;
}
.ub-sm .ub-text {
  left: 1.1rem;
  bottom: 1.15rem;
  right: 44%;
}
.ub-sm .ub-motif {
  height: 70%;
  right: 3%;
}
.ub-sm .ub-kicker {
  font-size: 0.64rem;
  letter-spacing: 0.1em;
}
.ub-md .ub-title {
  font-size: 2.25rem;
}
.ub-lg .ub-title {
  font-size: clamp(2.5rem, 5vw, 4rem);
}
.ub-lg .ub-text {
  left: clamp(1.5rem, 6vw, 4rem);
  bottom: 2.2rem;
}
@media (max-width: 640px) {
  .ub-motif {
    right: -6%;
    opacity: 0.55;
  }
  .ub-text {
    right: 1.25rem;
  }
}
</style>
