#!/usr/bin/env node
// Baut den Cubiq Client (In-Game-Mod) in Teil-Jobs – für .github/workflows/client-mod.yml, geht aber auch lokal.
//
//   node scripts/client-mod-build.mjs plan          Teil-Jobs als JSON (Zeile „jobs=…“ für $GITHUB_OUTPUT)
//   node scripts/client-mod-build.mjs build <id>    einen Teil-Job bauen, z. B. fabric-2 oder legacy-1.7.10
//   node scripts/client-mod-build.mjs verify        nach dem Zusammenführen: von jedem Teil-Job genau eine Liste da?
//   node scripts/client-mod-build.mjs summary       Übersicht (Markdown) für $GITHUB_STEP_SUMMARY
//
// Jedes Gradle-Projekt unter client-mod/ (eigener Build, siehe client-mod/README.md) legt seine Jars und eine
// Liste builds*.json in client-mod/dist/ ab. Viele Minecraft-Versionen in einem Job dauerten Stunden und
// sprengten den Speicher des Runners – deshalb teilt „plan“ die Versionen der großen Projekte auf mehrere Jobs auf
// (-PcubiqVersions=…, die Versionslisten stehen in den settings.gradle). „build“ prüft danach, dass für jede
// Version genau eine Jar entstanden ist, und benennt die Liste in builds-<id>.json um – so lassen sich die
// Ergebnisse aller Jobs in einem Ordner zusammenführen (scripts/publish-client-mod.mjs liest sie alle).
//
// Java: GitHub-Runner haben JAVA_HOME_8_X64 … JAVA_HOME_25_X64 (setup-java); Projekte, deren Gradle ein bestimmtes
// JDK braucht, bekommen es als JAVA_HOME. GRADLE_TOOLCHAINS (Umgebung) wird an jeden Gradle-Aufruf angehängt.

import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync, renameSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const CLIENT = join(ROOT, 'client-mod')

/**
 * Die Gradle-Projekte. `list`: Versionsliste in settings.gradle (Gruppe 1 = die Versionen in Anführungszeichen),
 * `shards`: in so viele Jobs aufteilen, `task`: Gradle-Aufgabe, die Jars + builds*.json nach dist/ schreibt.
 */
export const PROJECTS = [
  { id: 'fabric', label: 'Fabric', dir: '.', list: /def fabricVersions = \[([^\]]*)\]/, task: 'collectLauncherJars', shards: 4 },
  { id: 'neoforge', label: 'NeoForge', dir: 'neoforge', list: /create\(rootProject\)\s*\{\s*versions\s+((?:'[^']+'[\s,]*)+)/, task: 'collectLauncherJars', shards: 3 },
  // ":" davor: ein unqualifizierter Name ließe Gradle alle Versionsprojekte konfigurieren (forge/stonecutter.gradle).
  { id: 'forge', label: 'Forge', dir: 'forge', list: /create\(rootProject\)\s*\{\s*versions\s+((?:'[^']+'[\s,]*)+)/, task: ':collectLauncherJars', shards: 3 },
  { id: 'forge-mojmap-legacy', label: 'Forge', dir: 'forge-mojmap-legacy', list: /create\(rootProject\)\s*\{\s*versions\s+((?:'[^']+'[\s,]*)+)/, task: 'collectLauncherJars', shards: 2 },
  { id: 'legacy', label: 'Forge', dir: 'legacy', list: /def legacyVersions = \[([^\]]*)\]/, task: 'collectLauncherJars', shards: 1 },
  // ForgeGradle 6 läuft mit Gradle 8.14 – das startet nicht unter Java 25 (gradle/gradle-daemon-jvm.properties: 17).
  { id: 'forge-1.13.2', label: 'Forge', dir: 'forge-1.13.2', versions: ['1.13.2'], task: 'build', java: 17 },
  { id: 'legacy-1.7.10', label: 'Forge', dir: 'legacy-1.7.10', versions: ['1.7.10'], task: 'build' },
]

/** Versionen eines Projekts aus seiner settings.gradle (oder fest angegeben). */
export function projectVersions(project, read = (file) => readFileSync(file, 'utf8')) {
  if (project.versions) return project.versions
  const settings = read(join(CLIENT, project.dir, 'settings.gradle'))
  const match = settings.match(project.list)
  if (!match) throw new Error(`${project.dir}/settings.gradle: Versionsliste nicht gefunden`)
  const versions = [...match[1].matchAll(/'([^']+)'/g)].map((m) => m[1])
  if (!versions.length) throw new Error(`${project.dir}/settings.gradle: Versionsliste ist leer`)
  return versions
}

/** `items` in `n` zusammenhängende, möglichst gleich große Teile. */
export function chunks(items, n) {
  const parts = Math.max(1, Math.min(n, items.length))
  const result = []
  let start = 0
  for (let i = 0; i < parts; i++) {
    const size = Math.ceil((items.length - start) / (parts - i))
    result.push(items.slice(start, start + size))
    start += size
  }
  return result
}

/** Alle Teil-Jobs: { id, name, project, versions, partial }. */
export function plan(read) {
  const jobs = []
  for (const project of PROJECTS) {
    const parts = chunks(projectVersions(project, read), project.shards ?? 1)
    parts.forEach((versions, i) => {
      const range = versions.length > 1 ? `${versions[0]} – ${versions.at(-1)}` : versions[0]
      jobs.push({
        id: parts.length > 1 ? `${project.id}-${i + 1}` : project.id,
        name: `${project.label} ${range}`,
        project: project.id,
        versions,
        // Nur ein Teil der Versionen: per -PcubiqVersions auswählen.
        partial: parts.length > 1,
      })
    })
  }
  return jobs
}

/**
 * Prüft die Liste eines Teil-Jobs: jede gewünschte Version genau einmal, keine fremden Einträge.
 * Eine Jar kann mehrere Minecraft-Versionen abdecken (Legacy: 1.10.2 auch für 1.10) – gezählt wird der Eintrag,
 * der die Version des Projekts nennt.
 */
export function checkBuilds(entries, versions) {
  if (!Array.isArray(entries)) return ['Liste ist kein Array']
  const problems = []
  for (const version of versions) {
    const hits = entries.filter((e) => Array.isArray(e?.minecraft) && e.minecraft.includes(version))
    if (hits.length !== 1) problems.push(`${version}: ${hits.length} Jars statt einer`)
  }
  for (const e of entries) {
    if (!Array.isArray(e?.minecraft) || !e.minecraft.some((v) => versions.includes(v))) {
      problems.push(`unerwarteter Eintrag ${e?.file ?? JSON.stringify(e)}`)
    }
  }
  return problems
}

/** Minecraft-Versionen aufsteigend vergleichen (1.7.10 < 1.8.9 < 1.21.11 < 26.1). */
export function compareMinecraft(a, b) {
  const x = a.split(/[.-]/).map((n) => Number.parseInt(n, 10) || 0)
  const y = b.split(/[.-]/).map((n) => Number.parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] ?? 0) - (y[i] ?? 0)
    if (d !== 0) return d
  }
  return 0
}

/** Übersicht je Loader: Zahl der Jars und abgedeckte Minecraft-Versionen. */
export function summarize(builds) {
  const names = { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge' }
  const rows = new Map()
  for (const b of builds) {
    const row = rows.get(b.loader) ?? { loader: names[b.loader] ?? b.loader, jars: 0, minecraft: [] }
    row.jars++
    row.minecraft.push(...(b.minecraft ?? []))
    rows.set(b.loader, row)
  }
  return [...rows.values()].map((r) => {
    const sorted = [...new Set(r.minecraft)].sort(compareMinecraft)
    return { loader: r.loader, jars: r.jars, versions: sorted.length, range: sorted.length > 1 ? `${sorted[0]} – ${sorted.at(-1)}` : (sorted[0] ?? '') }
  })
}

function fail(message) {
  console.error(`Fehler: ${message}`)
  process.exit(1)
}

const DIST = join(CLIENT, 'dist')
const listFiles = () => (existsSync(DIST) ? readdirSync(DIST).filter((f) => /^builds(-.+)?\.json$/.test(f)).sort() : [])

/** Nach dem Zusammenführen der Artefakte: genau die Listen der geplanten Teil-Jobs, nichts sonst. */
function verify() {
  const jobs = process.env.JOBS ? JSON.parse(process.env.JOBS) : plan()
  const expected = jobs.map((j) => `builds-${j.id}.json`)
  const present = listFiles()
  const missing = expected.filter((f) => !present.includes(f))
  const extra = present.filter((f) => !expected.includes(f))
  if (missing.length || extra.length) {
    fail(`Listen in ${DIST} passen nicht zum Plan${missing.length ? `\n  fehlen: ${missing.join(', ')}` : ''}${extra.length ? `\n  zu viel: ${extra.join(', ')}` : ''}`)
  }
  const jars = present.reduce((n, f) => n + JSON.parse(readFileSync(join(DIST, f), 'utf8')).length, 0)
  console.log(`${present.length} Teil-Jobs, ${jars} Jars – vollständig.`)
}

/** Markdown für die Zusammenfassung des Laufs (PUBLISH/STATUS aus dem Workflow). */
function summary() {
  const lines = []
  const manifestFile = join(DIST, 'channel', 'client-mod.json')
  const manifest = existsSync(manifestFile) ? JSON.parse(readFileSync(manifestFile, 'utf8')) : null
  const builds = manifest?.builds ?? listFiles().flatMap((f) => JSON.parse(readFileSync(join(DIST, f), 'utf8')))
  lines.push(`### Cubiq Client${manifest?.version ? ` ${manifest.version}` : ''}`, '')
  const repo = process.env.GITHUB_REPOSITORY ?? 'PwiePixel/Cubiq-launcher'
  if (process.env.PUBLISH !== 'true') lines.push('Probebau – nichts veröffentlicht.')
  else if (process.env.STATUS === 'success') lines.push(`Veröffentlicht im Update-Kanal: https://github.com/${repo}/releases/tag/client-mod`)
  else lines.push('**Nicht veröffentlicht** – der Grund steht im Log dieses Laufs. Launcher behalten ihren bisherigen Stand.')
  if (builds.length) {
    lines.push('', '| Loader | Jars | Minecraft |', '| --- | ---: | --- |')
    for (const row of summarize(builds)) lines.push(`| ${row.loader} | ${row.jars} | ${row.range} (${row.versions} ${row.versions === 1 ? 'Version' : 'Versionen'}) |`)
  }
  console.log(lines.join('\n'))
}

function build(id) {
  const job = plan().find((j) => j.id === id) ?? fail(`unbekannter Teil-Job ${id} (siehe \`plan\`)`)
  const project = PROJECTS.find((p) => p.id === job.project)
  const dir = join(CLIENT, project.dir)
  const dist = DIST
  const lists = listFiles
  if (lists().length) fail(`${dist} enthält schon builds*.json – Teil-Jobs brauchen einen leeren dist-Ordner`)

  const env = { ...process.env }
  if (project.java) {
    const home = process.env[`JAVA_HOME_${project.java}_X64`]
    if (home) env.JAVA_HOME = home
    else console.warn(`Hinweis: JAVA_HOME_${project.java}_X64 fehlt – nehme das vorhandene JAVA_HOME (braucht Java ${project.java}).`)
  }
  const args = ['--no-daemon', '--stacktrace', ...(process.env.GRADLE_TOOLCHAINS ?? '').split(/\s+/).filter(Boolean), project.task]
  if (job.partial) args.push(`-PcubiqVersions=${job.versions.join(',')}`)
  const windows = process.platform === 'win32'
  console.log(`${job.name}: ${windows ? 'gradlew.bat' : './gradlew'} ${args.join(' ')}  (in client-mod/${project.dir})`)
  const run = spawnSync(windows ? 'gradlew.bat' : './gradlew', args, { cwd: dir, env, stdio: 'inherit', shell: windows })
  if (run.error) fail(`Gradle nicht startbar: ${run.error.message}`)
  if (run.status !== 0) fail(`Gradle-Bau fehlgeschlagen (${run.status})`)

  const produced = lists()
  if (produced.length !== 1) fail(`erwartet genau eine builds*.json in ${dist}, gefunden: ${produced.join(', ') || 'keine'}`)
  const entries = JSON.parse(readFileSync(join(dist, produced[0]), 'utf8'))
  const problems = checkBuilds(entries, job.versions)
  if (problems.length) fail(`${produced[0]} passt nicht zu ${job.name}:\n  ${problems.join('\n  ')}`)
  for (const e of entries) {
    if (!existsSync(join(dist, e.file))) fail(`${e.file} fehlt in ${dist}`)
  }
  const target = `builds-${job.id}.json`
  if (produced[0] !== target) renameSync(join(dist, produced[0]), join(dist, target))
  console.log(`${job.name}: ${entries.length} Jars, Liste ${target}`)
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const [command, id] = process.argv.slice(2)
  if (command === 'plan') {
    const jobs = plan()
    console.error(jobs.map((j) => `${j.id.padEnd(22)} ${j.name} (${j.versions.length})`).join('\n'))
    console.log(`jobs=${JSON.stringify(jobs)}`)
  } else if (command === 'build' && id) {
    build(id)
  } else if (command === 'verify') {
    verify()
  } else if (command === 'summary') {
    summary()
  } else {
    console.error('Aufruf: client-mod-build.mjs plan | build <id> | verify | summary')
    process.exit(2)
  }
}
