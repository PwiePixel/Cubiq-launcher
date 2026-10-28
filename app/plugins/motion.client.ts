import { backend } from '~/utils/backend'
import { reportSystemMotion, sharedMotion } from '~/utils/motion'
import { bindMotionLibrary, vStagger } from '~/utils/motionFx'

// Motion for Vue: Komponenten (Layout, Präsenz) folgen der Einstellung „Animationen“,
// dazu die Direktive `v-stagger` für gestaffelte Auftritte (siehe utils/motionFx.ts).
// Der Kern erfährt „Bewegung reduzieren“ des Systems – für „Wie System“ im Cubiq Client.
export default defineNuxtPlugin((nuxtApp) => {
  bindMotionLibrary()
  nuxtApp.vueApp.directive('stagger', vStagger)
  reportSystemMotion(sharedMotion(), backend.setSystemReducedMotion)
})

declare module 'vue' {
  interface GlobalDirectives {
    vStagger: typeof vStagger
  }
}
