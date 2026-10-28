// Adresse der Cubiq-Dienste aus der Repo-Variable CUBIQ_API_BASE – dieselbe Regel wie
// src-tauri/crates/core/site_rule.rs, damit Oberfläche und Kern dieselbe Adresse nutzen (app/utils/site.ts).

/**
 * Die Adresse ohne Schrägstrich am Ende; leer = kein Server eingerichtet (die Dienste bleiben aus).
 * Ein Tippfehler bricht den Build ab – sonst erschiene ein Release, das ohne Dienste oder gar nicht startet.
 */
export function cubiqSite(raw) {
  const base = String(raw ?? '').trim().replace(/\/+$/, '')
  if (!base) return ''
  if (!isSite(base)) {
    throw new Error(`CUBIQ_API_BASE „${base}“ ist keine Adresse wie https://deine-domain.de (ohne Pfad) – Build abgebrochen.`)
  }
  return base
}

/**
 * `https://host[:port]` ohne Pfad, Query oder Zugangsdaten; der letzte Namensteil beginnt mit einem Buchstaben
 * (kein reines IP). Für lokale Tests auch `http://127.0.0.1:port` bzw. `http://localhost:port`. Zusätzlich muss
 * der Browser die Adresse lesen können – die Oberfläche liest daraus beim Laden ihren Host.
 */
export function isSite(base) {
  const m = /^(https?):\/\/([A-Za-z0-9.-]+)(?::([0-9]{1,5}))?$/.exec(base)
  if (!m) return false
  const [, scheme, host, port] = m
  if (port !== undefined && !(Number(port) >= 1 && Number(port) <= 65535)) return false
  if (scheme === 'http') return (host === '127.0.0.1' || host === 'localhost') && port !== undefined
  const labels = host.split('.')
  if (!labels.every((l) => /^[A-Za-z0-9-]+$/.test(l)) || !/^[A-Za-z]/.test(labels.at(-1))) return false
  try {
    return new URL(base).host !== ''
  } catch {
    return false
  }
}
