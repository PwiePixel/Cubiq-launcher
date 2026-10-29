import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { supportedLocales } from '../app/utils/i18n'

// Die Microsoft-Anmeldung scheitert an Mojangs Freigabe (`login_with_xbox` antwortet 403). Die Texte dazu sagen das
// ehrlich – ohne Termin und ohne das frühere Versprechen, dass es bald bzw. „ohne Update“ klappt.
const root = path.resolve(__dirname, '..')
const read = (file: string) => fs.readFileSync(path.join(root, file), 'utf8')
const load = (locale: string) => JSON.parse(read(`app/locales/${locale}.json`))

describe('Microsoft-Anmeldung nicht freigegeben', () => {
  it('verspricht in keiner Sprache, dass es bald oder ohne Update klappt', () => {
    const promise =
      /without an update|waiting for approval|not enabled yet|ohne Update|wartet auf die Freigabe|noch nicht freigeschaltet|sin actualizar|pendiente de aprobación|aún no está habilitado|sans mise à jour|attend l'approbation|pas encore activée|zonder update|wacht op goedkeuring|nog niet ingeschakeld|bez aktualizacji|czeka na zatwierdzenie|nie jest jeszcze włączone|sem precisar de atualização|aguarda aprovação|ainda não está ativado|güncelleme gerekmeden|onayını bekliyor|henüz etkin değil/i
    for (const locale of supportedLocales) {
      const messages = load(locale)
      for (const text of [messages.errors.authNotApproved, messages.accounts.login.notApprovedHint]) {
        expect(text, locale).toBeTruthy()
        expect(text, locale).not.toMatch(promise)
      }
    }
  })

  it('nennt den Grund: Mojang hat die App nicht freigegeben', () => {
    expect(load('en').errors.authNotApproved).toMatch(/isn't available in Cubiq Launcher.*Mojang hasn't approved/)
    expect(load('de').errors.authNotApproved).toMatch(/nicht verfügbar.*Mojang hat die App nicht/)
    expect(load('es').errors.authNotApproved).toMatch(/no está disponible.*Mojang no ha aprobado/)
    for (const locale of supportedLocales) expect(load(locale).errors.authNotApproved, locale).toMatch(/Mojang/)
    // Rückfalltext des Kerns (ohne Übersetzung) ebenso.
    expect(read('src-tauri/crates/core/src/error.rs')).not.toMatch(/wartet\s+auf die Freigabe|noch nicht freigeschaltet/)
  })

  it('Konten-Seite und Einrichtung zeigen denselben Hinweis – eine Stelle für spätere Auswege', () => {
    for (const file of ['app/pages/accounts.vue', 'app/components/OnboardingWizard.vue']) {
      const source = read(file)
      expect(source, file).toContain('<SignInUnavailable v-if="notApproved" />')
      expect(source, file).toContain("e.kind === 'auth_not_approved'")
      expect(source, file).not.toContain("t('accounts.login.notApprovedHint')")
    }
    expect(read('app/components/SignInUnavailable.vue')).toContain("t('accounts.login.notApprovedHint')")
  })
})
