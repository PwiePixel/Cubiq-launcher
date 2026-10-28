// Adresse der Cubiq-Website, unter der auch die Cubiq API läuft. Sie kommt beim Bauen aus der Repo-Variable
// CUBIQ_API_BASE (nuxt.config.ts, geprüft wie in src-tauri/crates/core/build.rs) – dieselbe, mit der der Kern spricht.
// Ohne eingerichteten Server bleibt ein Platzhalter unter `.invalid` (löst nie auf); die Dienste sind dann aus.
// Ohne Importe: cubiq, teamAccess, applications und push laden diese Datei, ohne sich gegenseitig zu brauchen.
export const CUBIQ_SITE: string = import.meta.env.VITE_CUBIQ_SITE || 'https://cubiq-launcher.invalid'
