import fs from 'node:fs'
import path from 'node:path'
import { inflateSync } from 'node:zlib'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { INTRO_PREFS_KEY, introPlan, mirrorIntroPrefs, parseIntroPrefs, readIntroPrefs, shouldPlayIntro, type IntroPrefs } from '../app/utils/startIntro'
import { START_SOUND_VOLUME } from '../app/utils/startSound'
import { SPARKLES } from '../scripts/start-sound.mjs'
import { chunks, unfilter } from './fixtures/png'

const root = path.resolve(__dirname, '..')
const read = (file: string) => fs.readFileSync(path.join(root, file), 'utf8')
const template = read('app/spa-loading-template.html')
const intro = read('app/components/StartIntro.vue')

/** Einfache CSS-Regeln (Selektor → Deklarationen); verschachtelte Blöcke (@keyframes) liefern nur ihre inneren Regeln. */
function rules(css: string): Map<string, Record<string, string>> {
  const out = new Map<string, Record<string, string>>()
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '')
  for (const m of clean.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selector = m[1]!.trim().replace(/\s+/g, ' ')
    const decls: Record<string, string> = { ...out.get(selector) }
    // Deklarationen an `;` trennen – aber nicht innerhalb von Klammern (Verläufe, Polygone).
    let depth = 0
    let cur = ''
    for (const ch of `${m[2]!};`) {
      if (ch === '(') depth++
      if (ch === ')') depth--
      if (ch === ';' && depth === 0) {
        const at = cur.indexOf(':')
        if (at > 0) decls[cur.slice(0, at).trim()] = cur.slice(at + 1).trim().replace(/\s+/g, ' ')
        cur = ''
      } else cur += ch
    }
    out.set(selector, decls)
  }
  return out
}

function rule(css: Map<string, Record<string, string>>, selector: string): Record<string, string> {
  const found = css.get(selector)
  expect(found, selector).toBeDefined()
  return found!
}

const splashCss = rules(template.slice(template.indexOf('<style>') + '<style>'.length, template.indexOf('</style>')))
const introCss = rules(intro.slice(intro.indexOf('<style scoped>') + '<style scoped>'.length, intro.indexOf('</style>', intro.indexOf('<style scoped>'))))

describe('Spiegel der Einstellungen', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('liest Spiegel – auch alte ohne Ton (= an) und kaputte (= Vorgaben)', () => {
    expect(parseIntroPrefs(null)).toEqual({ enabled: true, motion: 'full', sound: true })
    expect(parseIntroPrefs('{"enabled":false,"motion":"reduced"}')).toEqual({ enabled: false, motion: 'reduced', sound: true })
    expect(parseIntroPrefs('{"enabled":true,"motion":"system","sound":false}')).toEqual({ enabled: true, motion: 'system', sound: false })
    expect(parseIntroPrefs('{kaputt')).toEqual({ enabled: true, motion: 'full', sound: true })
    expect(parseIntroPrefs('null')).toEqual({ enabled: true, motion: 'full', sound: true })
    expect(parseIntroPrefs('{"enabled":"ja","motion":3,"sound":"aus"}')).toEqual({ enabled: true, motion: 'full', sound: true })
  })

  it('spiegelt Start-Animation, Animationen und Start-Ton', () => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
    })
    mirrorIntroPrefs({ startIntro: true, startIntroSound: false, motion: 'system' })
    expect(JSON.parse(store.get(INTRO_PREFS_KEY)!)).toEqual({ enabled: true, motion: 'system', sound: false })
    expect(readIntroPrefs()).toEqual({ enabled: true, motion: 'system', sound: false })
    // Ältere Kerne kennen die Felder nicht: an, wie ab Werk.
    mirrorIntroPrefs({})
    expect(readIntroPrefs()).toEqual({ enabled: true, motion: 'full', sound: true })
    mirrorIntroPrefs({ startIntro: false, motion: 'reduced' })
    expect(readIntroPrefs()).toEqual({ enabled: false, motion: 'reduced', sound: true })
    mirrorIntroPrefs(undefined)
    expect(readIntroPrefs().enabled).toBe(false)
  })

  it('ohne Speicher gelten die Vorgaben', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('privat')
      },
      setItem: () => {
        throw new Error('privat')
      },
    })
    expect(readIntroPrefs()).toEqual({ enabled: true, motion: 'full', sound: true })
    expect(() => mirrorIntroPrefs({ startIntro: false })).not.toThrow()
  })
})

describe('Wann Animation und Ton laufen', () => {
  const base = { systemReduced: false, pathname: '/', windowLabel: 'main', hidden: false, mobile: false }

  it('Matrix: Einstellung, Animationen, System, Ton, Fenster, Sichtbarkeit, Handy', () => {
    for (const enabled of [true, false])
      for (const motion of ['full', 'system', 'reduced'])
        for (const systemReduced of [false, true])
          for (const sound of [true, false])
            for (const pathname of ['/', '/clip-overlay', '/clip-overlay/'])
              for (const windowLabel of ['main', 'clip-overlay', null])
                for (const hidden of [false, true])
                  for (const mobile of [false, true]) {
                    const prefs: IntroPrefs = { enabled, motion, sound }
                    const plan = introPlan({ prefs, systemReduced, pathname, windowLabel, hidden, mobile })
                    const reduced = motion === 'reduced' || (motion === 'system' && systemReduced)
                    const play = enabled && !reduced && pathname === '/' && windowLabel !== 'clip-overlay' && !hidden
                    expect(plan, JSON.stringify({ prefs, systemReduced, pathname, windowLabel, hidden, mobile })).toEqual({
                      play,
                      sound: play && sound && !mobile,
                    })
                    expect(plan.play).toBe(play && shouldPlayIntro(prefs, systemReduced))
                  }
  })

  it('„Immer an“ spielt auch, wenn das System Bewegung reduziert', () => {
    expect(introPlan({ ...base, prefs: { enabled: true, motion: 'full', sound: true }, systemReduced: true })).toEqual({ play: true, sound: true })
  })

  it('nie im REC-Overlay über dem Spiel – auch nicht ohne Fensternamen', () => {
    expect(introPlan({ ...base, prefs: { enabled: true, motion: 'full', sound: true }, pathname: '/clip-overlay', windowLabel: null }).play).toBe(false)
    expect(introPlan({ ...base, prefs: { enabled: true, motion: 'full', sound: true }, windowLabel: 'clip-overlay' }).play).toBe(false)
  })

  it('Ton fest auf 30 %', () => {
    expect(START_SOUND_VOLUME).toBe(0.3)
  })
})

describe('Ladebildschirm und Start-Animation zeigen dieselbe Bühne', () => {
  const parts = ['arm-top', 'arm-right', 'arm-left', 'cube'] as const

  it('vier Teile mit denselben Umrissen, Startpose und demselben Drehpunkt', () => {
    for (const part of parts) {
      const a = rule(splashCss, `.cubiq-part[data-part='${part}']`)
      const b = rule(introCss, `.si-part[data-part='${part}']`)
      expect(a['clip-path'], part).toMatch(/^polygon\(/)
      expect(b['clip-path'], part).toBe(a['clip-path'])
      expect(b['--dx'], part).toBe(a['--dx'])
      expect(b['--dy'], part).toBe(a['--dy'])
    }
    expect(rule(splashCss, '.cubiq-part')['transform-origin']).toBe('45.31% 43.52%')
    expect(rule(introCss, '.si-part')['transform-origin']).toBe('45.31% 43.52%')
    // Startpose: Ladebildschirm (data-intro=on) = Grundzustand der Teile = erstes Bild der Keyframes.
    const waitingArm = rule(splashCss, "html[data-intro='on'] .cubiq-part")
    const waitingCube = rule(splashCss, "html[data-intro='on'] .cubiq-part[data-part='cube']")
    expect(waitingArm).toEqual({ transform: 'translate(var(--dx), var(--dy)) scale(0.92)', opacity: '0.5' })
    expect(waitingCube).toEqual({ transform: 'scale(0.8)', opacity: '0.55' })
    expect(rule(introCss, '.si-part').transform).toBe(waitingArm.transform)
    expect(rule(introCss, '.si-part').opacity).toBe(waitingArm.opacity)
    expect(rule(introCss, ".si-part[data-part='cube']").transform).toBe(waitingCube.transform)
    expect(rule(introCss, ".si-part[data-part='cube']").opacity).toBe(waitingCube.opacity)
    expect(intro).toMatch(/@keyframes intro-arm \{\s*0% \{\s*transform: translate\(var\(--dx\), var\(--dy\)\) scale\(0\.92\);\s*opacity: 0\.5;/)
    expect(intro).toMatch(/@keyframes intro-cube \{\s*0% \{\s*transform: scale\(0\.8\);\s*opacity: 0\.55;/)
  })

  it('Logo 128 px, 30 px über der Mitte; Ladebalken 62 px darunter; Name an derselben Stelle', () => {
    for (const [css, selector] of [
      [splashCss, '.cubiq-mark'],
      [introCss, '.si-mark'],
    ] as const) {
      const mark = rule(css, selector)
      expect([mark.top, mark.width, mark.height, mark.margin, mark.left]).toEqual(['calc(50% - 30px)', '128px', '128px', '-64px 0 0 -64px', '50%'])
    }
    const splashBar = { ...rule(splashCss, '.cubiq-splash-bar'), ...rule(splashCss, "html[data-intro='on'] .cubiq-splash-bar") }
    const introBar = rule(introCss, '.si-bar')
    for (const key of ['top', 'width', 'height', 'margin-left', 'border-radius', 'left']) expect(introBar[key], key).toBe(splashBar[key])
    expect(introBar.top).toBe('calc(50% + 62px)')
    expect(rule(introCss, '.si-bar span').width).toBe(rule(splashCss, '.cubiq-splash-bar span').width)
    expect(rule(introCss, '.si-bar span').animation).toBe(rule(splashCss, '.cubiq-splash-bar span').animation.replace('cubiq-load', 'intro-load'))
    expect(rule(introCss, '.si-name').top).toBe(rule(splashCss, '.cubiq-splash-name').top)
    expect(rule(introCss, '.si-name')['font-size']).toBe(rule(splashCss, '.cubiq-splash-name')['font-size'])
  })

  it('Farben je Aussehen gleich (Cubiq, Polarlicht, Halloween) – und das Fenster hat schon die Farbe der Bühne', () => {
    const looks = [
      ['.cubiq-splash', '.start-intro'],
      ['html[data-design="winter"]:not([data-event="halloween"]) .cubiq-splash', ".start-intro[data-look='winter']"],
      ['html[data-event="halloween"] .cubiq-splash', ".start-intro[data-look='halloween']"],
    ] as const
    const stages: string[] = []
    for (const [splash, own] of looks) {
      const a = rule(splashCss, splash)
      const b = rule(introCss, own)
      for (const key of ['--stage', '--ink', '--ink-accent', '--track', '--bar']) {
        expect(a[key], `${splash} ${key}`).toBeTruthy()
        expect(b[key], `${own} ${key}`).toBe(a[key])
      }
      stages.push(a['--stage']!)
    }
    // Grundfarbe der Bühne = Hintergrund von Seite und Fenster (kein Navy-Bild dazwischen).
    expect(stages[0]).toMatch(/#0c100e$/)
    expect(rule(splashCss, 'html, body').background).toBe('#0c100e')
    expect(stages[1]).toMatch(/#070c18$/)
    expect(rule(splashCss, 'html[data-design="winter"]:not([data-event="halloween"]), html[data-design="winter"]:not([data-event="halloween"]) body').background).toBe('#070c18')
    expect(stages[2]).toMatch(/#07060c$/)
    for (const conf of ['src-tauri/tauri.conf.json', 'src-tauri/tauri.messe.conf.json']) {
      const windows = JSON.parse(read(conf)).app.windows as { label: string; backgroundColor: string }[]
      expect(windows.find((w) => w.label === 'main')?.backgroundColor, conf).toBe('#0c100e')
    }
    expect(template).not.toMatch(/#070a1d|#1a2046|#f2f4fd/i)
    // Schnee auf den Teilen im Polarlicht – in beiden gleich.
    expect(rule(introCss, ".start-intro[data-look='winter'] .si-part").filter).toBe(rule(splashCss, 'html[data-design="winter"]:not([data-event="halloween"]) .cubiq-part').filter)
  })

  it('der Ladebildschirm entscheidet wie shouldPlayIntro und bleibt im REC-Overlay unsichtbar', () => {
    expect(template).toContain("localStorage")
    expect(template).toContain("'cubiq-intro'")
    expect(template).toContain("motion === 'reduced' || (motion === 'system' && systemReduced)")
    expect(template).toContain("prefs.enabled !== false && !reduced && label === 'main'")
    expect(template).toContain("indexOf('/clip-overlay') === 0")
    expect(rule(splashCss, 'html[data-overlay] .cubiq-splash').display).toBe('none')
    expect(rule(splashCss, 'html[data-overlay], html[data-overlay] body').background).toBe('transparent !important')
    expect(rule(splashCss, "html[data-intro='still'] .cubiq-splash-bar span").animation).toBe('none')
  })

  it('Zeitplan: Uhr = Ausblenden ab `hold`; keine var()/calc()-Zeiten in der Kurzschreibweise (der Minifier sortiert sie um)', () => {
    const cues = JSON.parse(read('app/utils/startIntroCues.json')) as { hold: number; leave: number }
    const match = intro.match(/@keyframes intro-stage \{\s*0%,\s*([\d.]+)% \{\s*opacity: 1;/)
    expect(match).not.toBeNull()
    expect(Number(match![1])).toBeCloseTo((100 * cues.hold) / (cues.hold + cues.leave), 2)
    for (const [selector, decls] of introCss) {
      if (decls.animation) expect(decls.animation, selector).not.toMatch(/var\(|calc\(/)
    }
    expect(rule(introCss, '.go')['animation-duration']).toBe('calc(var(--hold) + var(--leave))')
    expect(rule(introCss, '.go .si-center')['animation-delay']).toBe('var(--hold)')
  })

  it('nichts blitzt vor seinem Einsatz auf: wer rückwärts füllt, beginnt mit der Deckkraft seines Grundzustands', () => {
    const keyframes = intro.slice(intro.lastIndexOf('<style>'))
    const firstOpacity = (name: string): string | undefined => {
      const block = keyframes.match(new RegExp(`@keyframes ${name} \\{\\s*(?:0%|from)[^{]*\\{([^}]*)\\}`))
      return block?.[1]!.match(/opacity:\s*([\d.]+)/)?.[1]
    }
    let checked = 0
    for (const [selector, decls] of introCss) {
      if (!selector.startsWith('.go') || !decls.animation) continue
      const [name, ...rest] = decls.animation.split(' ')
      if (!rest.includes('both') && !rest.includes('backwards')) continue
      const base = rule(introCss, selector === '.go' ? '.start-intro' : selector.replace(/^\.go /, ''))
      const resting = base.opacity ?? '1'
      expect(firstOpacity(name!) ?? resting, `${selector} (${name})`).toBe(resting)
      checked++
    }
    expect(checked).toBeGreaterThan(8)
  })

  it('so viele funkelnde Pixel wie Funken im Klang', () => {
    expect(intro).toContain(`* var(--k) / ${SPARKLES})`)
    const list = intro.slice(intro.indexOf('const twinkles = ['), intro.indexOf('].map(([x, y], k)'))
    expect(list.match(/\[-?\d+, -?\d+\]/g)).toHaveLength(SPARKLES)
  })
})

describe('Umrisse der Logo-Teile', () => {
  /** public/logo.png als Alphakanal (RGBA, 8 Bit, ohne Zeilensprung). */
  function logoAlpha() {
    const png = fs.readFileSync(path.join(root, 'public', 'logo.png'))
    const list = chunks(png)
    const ihdr = list.find((c) => c.type === 'IHDR')!.data
    const width = ihdr.readUInt32BE(0)
    const height = ihdr.readUInt32BE(4)
    expect([ihdr[8], ihdr[9], ihdr[12]]).toEqual([8, 6, 0])
    const rgba = unfilter(inflateSync(Buffer.concat(list.filter((c) => c.type === 'IDAT').map((c) => c.data))), width, height, 4)
    const alpha = new Uint8Array(width * height)
    for (let i = 0; i < alpha.length; i++) alpha[i] = rgba[i * 4 + 3]!
    return { width, height, alpha }
  }

  function polygon(css: string): [number, number][] {
    return css
      .replace(/^polygon\(|\)$/g, '')
      .split(',')
      .map((p) => p.trim().split(/\s+/).map((v) => Number.parseFloat(v)) as [number, number])
  }

  function inside(poly: [number, number][], x: number, y: number) {
    let hit = false
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [xi, yi] = poly[i]!
      const [xj, yj] = poly[j]!
      if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) hit = !hit
    }
    return hit
  }

  it('jedes Polygon enthält genau einen Teil ganz und keinen Pixel der anderen', () => {
    const { width, height, alpha } = logoAlpha()
    // Teile = zusammenhängende deckende Flächen.
    const label = new Int32Array(width * height)
    let count = 0
    for (let start = 0; start < label.length; start++) {
      if (alpha[start]! <= 128 || label[start]) continue
      count++
      const stack = [start]
      label[start] = count
      while (stack.length) {
        const at = stack.pop()!
        const x = at % width
        const y = (at - x) / width
        for (const [nx, ny] of [
          [x + 1, y],
          [x - 1, y],
          [x, y + 1],
          [x, y - 1],
        ] as const) {
          const n = ny * width + nx
          if (nx >= 0 && ny >= 0 && nx < width && ny < height && alpha[n]! > 128 && !label[n]) {
            label[n] = count
            stack.push(n)
          }
        }
      }
    }
    expect(count).toBe(4)
    const shapes = Object.fromEntries(
      ['arm-top', 'arm-right', 'arm-left', 'cube'].map((part) => [part, polygon(rule(introCss, `.si-part[data-part='${part}']`)['clip-path']!).map(([x, y]) => [(x / 100) * width, (y / 100) * height] as [number, number])]),
    )
    const owner = new Map<string, number>()
    for (const [part, poly] of Object.entries(shapes)) {
      const hits = new Set<number>()
      for (let i = 0; i < label.length; i++) {
        const x = i % width
        const y = (i - x) / width
        const isIn = inside(poly, x + 0.5, y + 0.5)
        if (isIn && label[i]) hits.add(label[i]!)
        if (!isIn && label[i]) owner.set(`${part}:${label[i]}`, (owner.get(`${part}:${label[i]}`) ?? 0) + 1)
      }
      expect([...hits], part).toHaveLength(1)
      const k = [...hits][0]!
      expect(owner.get(`${part}:${k}`) ?? 0, `${part}: Pixel außerhalb`).toBe(0)
    }
    // Auch halbdurchsichtige Kantenpixel liegen in genau einem Umriss – nichts wird doppelt oder gar nicht gezeichnet.
    let lost = 0
    let doubled = 0
    for (let i = 0; i < alpha.length; i++) {
      if (!alpha[i]) continue
      const x = (i % width) + 0.5
      const y = Math.floor(i / width) + 0.5
      const n = Object.values(shapes).filter((poly) => inside(poly, x, y)).length
      if (n === 0) lost++
      if (n > 1) doubled++
    }
    expect({ lost, doubled }).toEqual({ lost: 0, doubled: 0 })
  })
})
