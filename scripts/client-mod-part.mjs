// CI (.github/workflows/client-mod.yml): Ein Job baut nur einen Teil der Minecraft-Versionen eines Mod-Projekts
// (-PcubiqVersions). Stonecutter baut die eingecheckte Version dabei immer mit – damit sie im Kanal nicht doppelt
// steht, behält jeder Job nur seine eigenen Versionen. Danach heißt die Liste des Jobs builds-<teil>.json, damit sich
// die Artefakte der Jobs beim Zusammenführen nicht überschreiben.
//
//   node scripts/client-mod-part.mjs <dist-ordner> <teil> [versionen]
//
// [versionen]: kommagetrennt wie bei -PcubiqVersions; leer = alle Builds behalten.

import { existsSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

/** Nur Builds dieser Minecraft-Versionen behalten. `wanted` leer = alle. Liefert die behaltenen Einträge. */
export function keepVersions(entries, wanted) {
  const set = new Set((wanted ?? []).map((v) => String(v).trim()).filter(Boolean))
  if (set.size === 0) return entries
  return entries
    .map((e) => ({ ...e, minecraft: (e.minecraft ?? []).filter((v) => set.has(v)) }))
    .filter((e) => e.minecraft.length > 0)
}

/** Teil-Name für Dateinamen: nur Buchstaben, Ziffern, Punkt und Minus. */
export function partName(part) {
  const name = String(part ?? '').trim()
  if (!/^[A-Za-z0-9][A-Za-z0-9.-]*$/.test(name)) throw new Error(`ungültiger Teil-Name „${name}“`)
  return name
}

/** Führt das für einen Job-Ordner aus; liefert die Anzahl behaltener Builds. */
export function applyPart(dist, part, versions) {
  const name = partName(part)
  const lists = readdirSync(dist).filter((f) => f === 'builds.json' || /^builds-.+\.json$/.test(f))
  if (lists.length !== 1) throw new Error(`${dist}: genau eine builds*.json erwartet, gefunden: ${lists.join(', ') || 'keine'}`)
  const source = join(dist, lists[0])
  const entries = JSON.parse(readFileSync(source, 'utf8'))
  if (!Array.isArray(entries)) throw new Error(`${lists[0]} ist keine Liste`)
  const wanted = String(versions ?? '').split(',')
  const kept = keepVersions(entries, wanted)
  const keptFiles = new Set(kept.map((e) => e.file))
  for (const e of entries) {
    if (!keptFiles.has(e.file) && existsSync(join(dist, e.file))) rmSync(join(dist, e.file))
  }
  rmSync(source)
  writeFileSync(join(dist, `builds-${name}.json`), `${JSON.stringify(kept, null, 2)}\n`)
  return kept.length
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [dist, part, versions] = process.argv.slice(2)
  if (!dist || !part) {
    console.error('Aufruf: client-mod-part.mjs <dist-ordner> <teil> [versionen]')
    process.exit(2)
  }
  const count = applyPart(dist, part, versions)
  console.log(`builds-${partName(part)}.json: ${count} Builds`)
}
