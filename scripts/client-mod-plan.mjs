// CI (.github/workflows/client-mod.yml): Plan der Build-Jobs für den Cubiq Client. Liest die Versionslisten aus den
// settings.gradle der Mod-Projekte und teilt sie in Pakete, die parallel bauen – neue Minecraft-Versionen kommen so
// ohne Änderung am Workflow in den Kanal.
//
//   node scripts/client-mod-plan.mjs [größe]     → {"include":[…]} für strategy.matrix (größe = Versionen je Job)

import { appendFileSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const MOD = join(ROOT, 'client-mod')

/**
 * Projekte der Mod: Ordner (relativ zu client-mod/), Gradle-Task, Name der Versionsliste in settings.gradle (oder feste
 * Version für Projekte mit nur einer) und Anzeigename. Die Tasks sind voll qualifiziert (`:collectLauncherJars` = nur
 * das Wurzelprojekt), damit Gradle sie nicht zusätzlich in Unterprojekten gleichen Namens sucht.
 */
export const PROJECTS = [
  { key: 'fabric', dir: '.', task: ':fabric:collectLauncherJars', list: 'fabricVersions', label: 'Fabric' },
  { key: 'forge', dir: 'forge', task: ':collectLauncherJars', list: 'forgeVersions', label: 'Forge' },
  { key: 'neoforge', dir: 'neoforge', task: ':collectLauncherJars', list: 'neoforgeVersions', label: 'NeoForge' },
  { key: 'forge-mojmap-legacy', dir: 'forge-mojmap-legacy', task: ':collectLauncherJars', list: 'mojmapVersions', label: 'Forge' },
  { key: 'legacy', dir: 'legacy', task: ':collectLauncherJars', list: 'legacyVersions', label: 'Forge' },
  { key: 'legacy-1.7.10', dir: 'legacy-1.7.10', task: ':collectLauncherJars', fixed: '1.7.10', label: 'Forge' },
  { key: 'forge-1.13.2', dir: 'forge-1.13.2', task: ':collectLauncherJars', fixed: '1.13.2', label: 'Forge' },
]

/** Versionsliste `def <name> = ['1.20', …]` aus einer settings.gradle. */
export function readVersionList(settings, name) {
  const m = new RegExp(`def ${name} = \\[([^\\]]*)\\]`).exec(settings)
  if (!m) throw new Error(`settings.gradle: Liste ${name} nicht gefunden`)
  const versions = [...m[1].matchAll(/'([^']+)'/g)].map((x) => x[1])
  if (versions.length === 0) throw new Error(`settings.gradle: Liste ${name} ist leer`)
  return versions
}

/** In Pakete zu höchstens `size` Versionen teilen (gleichmäßig, Reihenfolge bleibt). */
export function chunk(versions, size) {
  const parts = Math.max(1, Math.ceil(versions.length / size))
  const per = Math.ceil(versions.length / parts)
  const out = []
  for (let i = 0; i < versions.length; i += per) out.push(versions.slice(i, i + per))
  return out
}

/** Matrix-Einträge für alle Projekte. `read(dir)` liefert den Text der settings.gradle (für Tests austauschbar). */
export function plan(size = 12, read = (dir) => readFileSync(join(MOD, dir, 'settings.gradle'), 'utf8')) {
  const include = []
  for (const p of PROJECTS) {
    if (p.fixed) {
      include.push({ name: `${p.label} ${p.fixed}`, dir: p.dir, task: p.task, versions: '', part: p.key })
      continue
    }
    const parts = chunk(readVersionList(read(p.dir), p.list), size)
    parts.forEach((versions, i) => {
      const range = versions.length > 1 ? `${versions[0]} – ${versions.at(-1)}` : versions[0]
      include.push({
        name: `${p.label} ${range}`,
        dir: p.dir,
        task: p.task,
        versions: versions.join(','),
        part: parts.length > 1 ? `${p.key}-${i + 1}` : p.key,
      })
    })
  }
  return { include }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const size = Number(process.argv[2] ?? 12)
  const matrix = plan(Number.isFinite(size) && size > 0 ? size : 12)
  const json = JSON.stringify(matrix)
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `matrix=${json}\n`)
  for (const e of matrix.include) console.log(`${e.part.padEnd(24)} ${e.name}`)
  if (!process.env.GITHUB_OUTPUT) console.log(json)
}
