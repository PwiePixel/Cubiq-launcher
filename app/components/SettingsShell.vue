<script setup lang="ts">
import type { IconName } from '~/utils/icons'

// Gemeinsamer Rahmen für die globalen und die Instanz-Einstellungen: Titel mit Würfel-Zeichen (oder
// dem Bild der Instanz) und Schließen-Knopf, links eine Navigation mit Gruppen – der gewählte Abschnitt
// ist eine grüne Fläche wie im Dock –, rechts eine eigenständig scrollende Inhaltsspalte.
export interface ShellSection {
  key: string
  label: string
  icon: IconName
  group?: string
}

const props = defineProps<{ title: string; sections: ShellSection[]; status?: { ok: boolean; text: string } | null }>()
const active = defineModel<string>({ required: true })
const emit = defineEmits<{ close: [] }>()

const grouped = computed(() => {
  const out: { group: string | undefined; items: ShellSection[] }[] = []
  for (const s of props.sections) {
    const last = out[out.length - 1]
    if (last && last.group === s.group) last.items.push(s)
    else out.push({ group: s.group, items: [s] })
  }
  return out
})

const content = ref<HTMLElement | null>(null)
const nav = ref<HTMLElement | null>(null)
/** Handy: Die Abschnitte liegen in einer wischbaren Leiste – der aktive soll sichtbar sein. */
function revealActive() {
  if (!mobileUi.value) return
  nav.value?.querySelector<HTMLElement>('[aria-current="page"]')?.scrollIntoView({ block: 'nearest', inline: 'center' })
}
watch(active, () => {
  content.value?.scrollTo({ top: 0 })
  void nextTick(revealActive)
  // Neuer Abschnitt: Inhalt blendet kurz ein (nur transform/opacity).
  void nextTick(() => {
    if (content.value && motionAllowed()) void motionPlay(content.value, { opacity: [0, 1], transform: ['translateY(4px)', 'translateY(0px)'] }, { duration: MOTION_DURATION.fast })
  })
})
onMounted(() => void nextTick(revealActive))

function onKey(e: KeyboardEvent) {
  // Ein offener Dialog darüber (z. B. Bestätigung) schließt zuerst sich selbst.
  if (e.key === 'Escape' && !document.querySelector('[data-dialog-over-settings]')) emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
// Am Handy: Vollbild, die Abschnitte als wischbare Leiste oben; Zurück-Taste schließt.
useOverlay(() => emit('close'))
// Auftritt/Abtritt wie bei Dialogen (am Handy füllt die Ansicht den Bildschirm).
const backdrop = useTemplateRef<HTMLElement>('backdrop')
useOverlayMotion(backdrop, () => 'dialog')
</script>

<template>
  <div ref="backdrop" class="shell-backdrop fixed inset-0 z-50 flex items-center justify-center p-6 mobile:p-0" @mousedown.self="emit('close')">
    <section
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      data-motion-panel
      class="shell-panel flex h-[min(720px,90vh)] w-[min(1000px,94vw)] flex-col overflow-hidden rounded-2xl border border-line bg-base-900 mobile:h-full mobile:w-full mobile:rounded-none mobile:border-0 mobile:pt-[var(--safe-top)]"
    >
      <header class="shell-head flex items-center gap-3 border-b border-line px-6 py-4 mobile:px-4 mobile:py-2.5">
        <slot name="title-icon"><CubeMark :size="18" /></slot>
        <h2 class="display min-w-0 flex-1 truncate text-lg text-base-50">{{ title }}</h2>
        <span v-if="status" role="status" class="text-xs" :class="status.ok ? 'text-base-400' : 'text-danger-300'">{{ status.text }}</span>
        <button class="btn-icon mobile:size-11" :aria-label="t('common.actions.close')" @click="emit('close')">
          <svg viewBox="0 0 24 24" class="size-4" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path :d="icons.close" /></svg>
        </button>
      </header>

      <div class="flex min-h-0 flex-1 mobile:flex-col">
        <nav ref="nav" class="shell-nav flex w-64 shrink-0 flex-col overflow-y-auto border-r border-line p-3 mobile-scroll-x mobile:w-full mobile:flex-row mobile:gap-1 mobile:overflow-y-hidden mobile:border-r-0 mobile:border-b mobile:px-3 mobile:py-2" :aria-label="t('settingsShell.sectionsLabel')">
          <div v-for="(g, i) in grouped" :key="i" :class="{ 'mt-4': i > 0 && g.group }" class="mobile:mt-0! mobile:flex mobile:shrink-0 mobile:gap-1">
            <p v-if="g.group" class="mobile:hidden mb-1.5 px-3 text-[10.5px] font-semibold tracking-[0.12em] text-base-600 uppercase">{{ g.group }}</p>
            <button
              v-for="s in g.items"
              :key="s.key"
              class="mb-0.5 flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-sm font-medium transition-colors mobile:mb-0 mobile:min-h-10 mobile:w-auto mobile:shrink-0 mobile:whitespace-nowrap"
              :class="active === s.key ? 'shell-on' : 'text-base-400 hover:bg-base-850 hover:text-base-50'"
              :aria-current="active === s.key ? 'page' : undefined"
              @click="active = s.key"
            >
              <svg viewBox="0 0 24 24" class="size-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="icons[s.icon]" /></svg>
              <span class="min-w-0 leading-tight">{{ s.label }}</span>
            </button>
          </div>
          <div class="mt-auto pt-4 px-3 text-[11px] leading-5 text-base-600 mobile:hidden">
            <slot name="nav-footer" />
          </div>
        </nav>

        <div ref="content" class="min-w-0 flex-1 overflow-y-auto px-7 py-5 mobile:px-4 mobile:pb-[calc(1.25rem+var(--safe-bottom))]">
          <slot />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
@reference "~/assets/css/main.css";

.shell-backdrop {
  background: rgb(var(--shadow-rgb) / 0.62);
  backdrop-filter: blur(3px);
}
.shell-panel {
  box-shadow: var(--edge-light), var(--elev-3);
}
@supports (corner-shape: squircle) {
  .shell-panel {
    corner-shape: squircle;
    border-radius: 2rem;
  }
  :root.is-mobile .shell-panel {
    border-radius: 0;
  }
}
/* Navigation eine Stufe tiefer als der Inhalt – wie das Dock neben der Leinwand. */
.shell-nav {
  background: color-mix(in srgb, var(--color-base-950) 45%, var(--color-base-900));
}
/* Gewählter Abschnitt: grüne Fläche mit Lichtkante, Schrift dunkel darauf (wie im Dock). */
.shell-on {
  @apply bg-primary-500;
  color: var(--color-on-primary);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.22),
    inset 0 -1px 0 rgb(0 0 0 / 0.14),
    0 6px 18px -8px var(--glow-primary);
}
</style>
