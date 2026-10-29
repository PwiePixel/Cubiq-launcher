import { existsSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { checkBuilds, chunks, compareMinecraft, PROJECTS, plan, projectVersions, summarize } from '../scripts/client-mod-build.mjs'

const clientMod = join(__dirname, '..', 'client-mod')

describe('Cubiq Client: Bau in Teil-Jobs', () => {
  it('liest die Versionslisten aller Projekte aus ihren settings.gradle', () => {
    for (const project of PROJECTS) {
      const versions = projectVersions(project)
      expect(versions.length, project.id).toBeGreaterThan(0)
      expect(new Set(versions).size, `${project.id}: doppelte Versionen`).toBe(versions.length)
      for (const v of versions) expect(v, project.id).toMatch(/^\d+(\.\d+){1,2}$/)
      expect(existsSync(join(clientMod, project.dir, 'gradlew')), `${project.dir}/gradlew`).toBe(true)
    }
    expect(projectVersions(PROJECTS.find((p) => p.id === 'fabric'))).toEqual(expect.arrayContaining(['1.14.4', '1.21.1', '26.3']))
    expect(projectVersions(PROJECTS.find((p) => p.id === 'neoforge'))).toEqual(expect.arrayContaining(['1.20.2', '1.21.1']))
    expect(projectVersions(PROJECTS.find((p) => p.id === 'forge-mojmap-legacy'))).toEqual(expect.arrayContaining(['1.14.4', '1.16.5', '1.19.4']))
  })

  it('meldet eine fehlende Versionsliste statt still nichts zu bauen', () => {
    const project = { id: 'x', label: 'X', dir: 'x', list: /def xVersions = \[([^\]]*)\]/, task: 'collectLauncherJars' }
    expect(() => projectVersions(project, () => 'rootProject.name = "x"')).toThrow(/nicht gefunden/)
    expect(projectVersions(project, () => "def xVersions = ['1.20', '1.21']")).toEqual(['1.20', '1.21'])
  })

  it('teilt Listen in zusammenhängende, gleich große Stücke', () => {
    expect(chunks([1, 2, 3, 4, 5, 6, 7], 3)).toEqual([[1, 2, 3], [4, 5], [6, 7]])
    expect(chunks([1, 2], 4)).toEqual([[1], [2]])
    expect(chunks([1, 2, 3], 1)).toEqual([[1, 2, 3]])
  })

  it('plant jede Version genau einmal, in Jobs von handlicher Größe', () => {
    const jobs = plan()
    expect(new Set(jobs.map((j) => j.id)).size).toBe(jobs.length)
    for (const project of PROJECTS) {
      const planned = jobs.filter((j) => j.project === project.id).flatMap((j) => j.versions)
      expect(planned, project.id).toEqual(projectVersions(project))
    }
    for (const job of jobs) {
      expect(job.versions.length, job.id).toBeLessThanOrEqual(12)
      expect(job.id).toMatch(/^[a-z0-9.-]+$/)
      // Teil-Builds wählen ihre Versionen per -PcubiqVersions, ganze Projekte bauen alles.
      expect(job.partial).toBe(jobs.filter((j) => j.project === job.project).length > 1)
    }
    // GitHub Free: höchstens 20 Jobs gleichzeitig – der Release-Build braucht auch noch welche.
    expect(jobs.length).toBeLessThanOrEqual(16)
    expect(jobs.find((j) => j.id === 'fabric-1')?.name).toMatch(/^Fabric 1\.14\.4 – /)
  })

  it('prüft, dass ein Teil-Job jede seiner Versionen genau einmal liefert', () => {
    const jar = (file: string, ...minecraft: string[]) => ({ loader: 'forge', minecraft, file, requires: [] })
    expect(checkBuilds([jar('a.jar', '1.10', '1.10.2'), jar('b.jar', '1.11', '1.11.2')], ['1.10.2', '1.11.2'])).toEqual([])
    expect(checkBuilds([jar('a.jar', '1.10.2')], ['1.10.2', '1.11.2'])).toEqual(['1.11.2: 0 Jars statt einer'])
    // Die eingecheckte Version (z. B. Fabric 1.21.1) darf nicht in jedem Teil-Job mitkommen.
    expect(checkBuilds([jar('a.jar', '1.20'), jar('b.jar', '1.21.1')], ['1.20'])).toEqual(['unerwarteter Eintrag b.jar'])
    expect(checkBuilds([jar('a.jar', '1.20'), jar('c.jar', '1.20')], ['1.20'])).toEqual(['1.20: 2 Jars statt einer'])
    expect(checkBuilds({ not: 'a list' } as never, ['1.20'])).toEqual(['Liste ist kein Array'])
  })

  it('fasst die Jars je Loader zusammen, Versionen richtig sortiert', () => {
    expect(['1.21.11', '26.1', '1.7.10', '1.8.9', '1.21'].sort(compareMinecraft)).toEqual(['1.7.10', '1.8.9', '1.21', '1.21.11', '26.1'])
    const rows = summarize([
      { loader: 'fabric', minecraft: ['1.21.1'], file: 'a.jar' },
      { loader: 'forge', minecraft: ['1.12', '1.12.1', '1.12.2'], file: 'b.jar' },
      { loader: 'forge', minecraft: ['1.7.10'], file: 'c.jar' },
      { loader: 'fabric', minecraft: ['26.3'], file: 'd.jar' },
    ])
    expect(rows).toEqual([
      { loader: 'Fabric', jars: 2, versions: 2, range: '1.21.1 – 26.3' },
      { loader: 'Forge', jars: 2, versions: 4, range: '1.7.10 – 1.12.2' },
    ])
  })
})
