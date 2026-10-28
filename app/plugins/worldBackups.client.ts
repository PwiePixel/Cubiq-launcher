// Automatische Welt-Backups: Ereignisse des Kerns von Anfang an mitlesen (Aufgabe in der
// Titelleiste, Hinweise bei Problemen) – unabhängig davon, welche Seite gerade offen ist.
export default defineNuxtPlugin(() => {
  void useWorldBackupsStore().init()
})
