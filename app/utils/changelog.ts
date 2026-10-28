// Liest CHANGELOG.md: je Version ein Abschnitt „## <version> – <datum>“ mit „### English“ und
// „### Deutsch“. Dieselbe Funktion nutzt der Release-Build (scripts/changelog.mjs), der Launcher
// („Was ist neu“, Update-Karte) und die Website (Blog) – daher ohne Nuxt-Abhängigkeiten.
// Die Website hat zwei Kopien dieser Datei (api/app/utils/changelog.ts, api/server/lib/changelog.ts):
// bei Änderungen alle drei gleich halten.
//
// Optional trägt die Überschrift einen Update-Namen (Englisch | Deutsch):
//   ## 0.5.0 – 2026-09-25 – The Clip Update | Das Clip-Update
//
// Das Banner jedes Updates hat einen festen Stil (dunkle Fläche, weicher Schein, feines Würfel-Raster); je
// Version ändern sich nur Akzentfarbe und Motiv (ein freigestelltes Bild). Das Motiv zeichnet die
// Release-Automatik für jede Version passend zum Inhalt (scripts/release-art.mjs) – als ruhiges Bild (`motif`)
// und als bewegte Fassung (`anim`, animiertes PNG); ohne Motiv zeigt der Launcher das Cubiq-Logo:
//   <!-- banner: accent=#71a330 motif=/news/0.1.2/banner.png anim=/news/0.1.2/banner-anim.png -->
// Bei „Weniger Animationen“ zeigen Launcher und Website das ruhige Motiv.
//
// Screenshots der Neuerungen stehen als zweiter Kommentar direkt darunter – je Zeile eine Datei aus
// public/news/<version>/ (PNG oder WebP, höchstens SHOTS_MAX), optional mit Bildunterschrift auf
// Englisch und Deutsch (fehlt eine Sprache, gilt die andere):
//   <!-- shots:
//   /news/0.6.5/clips.png | The clip gallery | Die Clip-Galerie
//   /news/0.6.5/trim.webp | Trimming a clip | Einen Clip zuschneiden
//   /news/0.6.5/menu.png
//   -->
// Einzeilig geht auch, Einträge dann mit „;“ trennen: <!-- shots: /news/0.6.5/a.png | A | A ; /news/0.6.5/b.png -->
// Ältere Beiträge haben Bilder noch als eigene Zeile „![Bildunterschrift](/news/<version>/<datei>.png)“ im
// Text – postContent() sammelt beides in einer Galerie.


export interface ChangelogEntry {
  /** z. B. „0.4.4“; der Abschnitt „Unreleased“ hat `null`. */
  version: string | null
  date: string | null
  en: string
  de: string
  /** Update-Name wie „The Clip Update“ / „Das Clip-Update“, sonst `null`. */
  title: { en: string; de: string } | null
  /** Banner: Akzentfarbe und Motiv des Updates, sonst `null`. */
  banner: UpdateBanner | null
  /** Screenshots der Neuerungen (Kommentar „shots:“) in Dateireihenfolge. */
  shots: UpdateShot[]
  /** Einträge im Kommentar „shots:“, die nicht gelesen werden konnten (meldet scripts/changelog.mjs check). */
  shotIssues: string[]
}

export interface UpdateBanner {
  /** `#rrggbb` */
  accent: string
  /** Eigenes Bild unter `/news/…`, sonst `null`. */
  motif: string | null
  /** Bewegte Fassung des Motivs (animiertes PNG unter `/news/…`), sonst `null`; nur zusammen mit `motif`. */
  anim: string | null
}

export interface UpdateShot {
  /** `/news/<version>/<datei>.png|webp` */
  src: string
  /** Bildunterschrift je Sprache, sonst `null`. */
  caption: { en: string; de: string } | null
}

/** Höchstens so viele Screenshots je Update. */
export const SHOTS_MAX = 8

const BANNER_COMMENT = /^<!-- *banner: *(.*?) *-->$/
/** Platzhalter für gesicherte Banner-Zeilen, bevor die übrigen Kommentare entfernt werden. */
const BANNER_MARK = '\u0001banner '
/** Kommentar „shots:“ – nur, wenn er am Zeilenanfang beginnt und am Zeilenende schließt. */
const SHOTS_COMMENT = /^[ \t]*<!--\s*shots:([\s\S]*?)-->[ \t]*$/gm
const SHOT_MARK = '\u0001shot '
const SHOT_PATH = /^\/news\/[\w.-]+\/[\w.-]+\.(?:png|webp)$/
/** Bildunterschrift: kurz, eine Zeile, ohne Zeichen, die in Markdown oder HTML etwas bedeuten. */
// eslint-disable-next-line no-control-regex -- Steuerzeichen sind genau das, was ausgeschlossen wird
const SHOT_CAPTION = /^[^[\]<>\u0000-\u001f]{1,160}$/

/** Bild des Banners (`motif=` oder `anim=`): nur Dateien unter `/news/<ordner>/`, sonst `null`. */
function bannerImage(raw: string, key: 'motif' | 'anim'): string | null {
  const value = new RegExp(`(?:^|\\s)${key}=(\\/news\\/[\\w.-]+\\/[\\w.-]+\\.(?:png|webp))(?:\\s|$)`).exec(raw)?.[1] ?? null
  return value && !value.includes('..') ? value : null
}

/** „accent=#ff7ab8 motif=/news/0.4.3/banner.png anim=…“ → Banner; ungültige Werte werden ignoriert. */
export function parseBanner(raw: string): UpdateBanner | null {
  const accent = /(?:^|\s)accent=(#[0-9a-fA-F]{6})(?:\s|$)/.exec(raw)?.[1]
  if (!accent) return null
  const motif = bannerImage(raw, 'motif')
  return { accent: accent.toLowerCase(), motif, anim: motif ? bannerImage(raw, 'anim') : null }
}

/**
 * „/news/0.6.5/a.png | English | Deutsch“ → Screenshot. Ungültiger Pfad, zu viele Teile oder eine
 * Bildunterschrift mit verbotenen Zeichen → `null`.
 */
export function parseShot(raw: string): UpdateShot | null {
  const parts = raw.split('|').map((part) => part.trim())
  if (parts.length > 3) return null
  const [src = '', en = '', de = ''] = parts
  if (!SHOT_PATH.test(src) || src.includes('..')) return null
  if ([en, de].some((text) => text && !SHOT_CAPTION.test(text))) return null
  return { src, caption: en || de ? { en: en || de, de: de || en } : null }
}

const VERSION_HEADING =
  /^## +(?:\[?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)\]?|(Unreleased))(?: +[–-] +(\d{4}-\d{2}-\d{2}))?(?: +[–-] +(.+?))? *$/i

/** „The Clip Update | Das Clip-Update“ → beide Namen; ohne „|“ gilt der Name für beide Sprachen. */
function parseTitle(raw: string | undefined): ChangelogEntry['title'] {
  if (!raw) return null
  const [en = '', de] = raw.split('|').map((part) => part.trim())
  if (!en) return null
  return { en, de: de || en }
}

/** Alle Abschnitte in Dateireihenfolge (neueste zuerst, wie im Changelog). */
export function parseChangelog(text: string): ChangelogEntry[] {
  const withoutComments = text
    .replace(SHOTS_COMMENT, (_, body: string) =>
      body
        .split(/\r?\n|;/)
        .map((item) => item.trim())
        .filter(Boolean)
        .map((item) => SHOT_MARK + item)
        .join('\n'),
    )
    .split(/\r?\n/)
    .map((line) => {
      const m = BANNER_COMMENT.exec(line.trim())
      return m ? BANNER_MARK + m[1] : line
    })
    .join('\n')
    .replace(/<!--[\s\S]*?-->/g, '')
  const entries: ChangelogEntry[] = []
  let current: ChangelogEntry | null = null
  let lang: 'en' | 'de' | null = null
  const push = () => {
    if (!current) return
    current.en = current.en.trim()
    current.de = current.de.trim()
    entries.push(current)
  }
  for (const line of withoutComments.split(/\r?\n/)) {
    const heading = VERSION_HEADING.exec(line)
    if (heading) {
      push()
      current = {
        version: heading[1] ?? null,
        date: heading[3] ?? null,
        en: '',
        de: '',
        title: parseTitle(heading[4]),
        banner: null,
        shots: [],
        shotIssues: [],
      }
      lang = null
      continue
    }
    if (/^## /.test(line)) {
      // Andere Überschrift zweiter Ebene: nicht Teil eines Versionsabschnitts.
      push()
      current = null
      lang = null
      continue
    }
    if (!current) continue
    if (line.startsWith(BANNER_MARK)) {
      current.banner ??= parseBanner(line.slice(BANNER_MARK.length))
      continue
    }
    if (line.startsWith(SHOT_MARK)) {
      const raw = line.slice(SHOT_MARK.length)
      const shot = parseShot(raw)
      if (!shot) current.shotIssues.push(raw)
      else if (current.shots.some((s) => s.src === shot.src)) current.shotIssues.push(`${raw} (doppelt)`)
      else current.shots.push(shot)
      continue
    }
    const sub = /^### +(.+?) *$/.exec(line)
    if (sub) {
      const name = sub[1]!.toLowerCase()
      lang = name === 'english' ? 'en' : name === 'deutsch' ? 'de' : null
      continue
    }
    if (lang) current[lang] += `${line}\n`
  }
  push()
  return entries
}

/** Der Abschnitt einer Version (ohne führendes „v“), sonst `null`. */
export function changelogFor(entries: ChangelogEntry[], version: string): ChangelogEntry | null {
  const wanted = version.replace(/^v/, '')
  return entries.find((e) => e.version === wanted) ?? null
}

/** Vergleicht „1.2.3“-Versionen; Zusätze wie „-beta“ zählen als älter als die Version ohne. */
export function compareVersions(a: string, b: string): number {
  const split = (v: string) => {
    const [core = '', pre] = v.replace(/^v/, '').split('-', 2)
    return { parts: core.split('.').map((n) => Number.parseInt(n, 10) || 0), pre: pre ?? null }
  }
  const x = split(a)
  const y = split(b)
  for (let i = 0; i < Math.max(x.parts.length, y.parts.length); i++) {
    const d = (x.parts[i] ?? 0) - (y.parts[i] ?? 0)
    if (d !== 0) return Math.sign(d)
  }
  if (x.pre === y.pre) return 0
  if (x.pre === null) return 1
  if (y.pre === null) return -1
  return x.pre < y.pre ? -1 : 1
}

/**
 * Was ist neu seit `seen`? Alle veröffentlichten Versionen > `seen` und ≤ `current`,
 * neueste zuerst, höchstens `limit` Stück.
 */
export function changesSince(entries: ChangelogEntry[], seen: string, current: string, limit = 5): ChangelogEntry[] {
  return entries
    .filter((e): e is ChangelogEntry & { version: string } => e.version !== null)
    .filter((e) => compareVersions(e.version, seen) > 0 && compareVersions(e.version, current) <= 0)
    .sort((a, b) => compareVersions(b.version, a.version))
    .slice(0, limit)
}

/** Eigene Bilder eines Beitrags: nur Dateien aus public/news/, nie fremde Adressen. */
export const NEWS_IMAGE = /^!\[([^\]\n]{0,200})\]\((\/news\/[\w.-]+\/[\w.-]+\.(?:png|webp|jpe?g))\) *$/

export type PostBlock = { kind: 'text'; markdown: string } | { kind: 'image'; src: string; caption: string }

/** Teilt einen Changelog-Text in Textblöcke und eigene Screenshots (je eine Zeile „![…](/news/…)“). */
export function splitPost(markdown: string): PostBlock[] {
  const blocks: PostBlock[] = []
  let text: string[] = []
  const flush = () => {
    const md = text.join('\n').trim()
    if (md) blocks.push({ kind: 'text', markdown: md })
    text = []
  }
  for (const line of markdown.split(/\r?\n/)) {
    const image = NEWS_IMAGE.exec(line.trim())
    if (image && !image[2]!.includes('..')) {
      flush()
      blocks.push({ kind: 'image', src: image[2]!, caption: image[1]!.trim() })
    } else text.push(line)
  }
  flush()
  return blocks
}

/** Ein Bild der Galerie eines Beitrags, Bildunterschrift schon in der Sprache des Lesers (oder leer). */
export interface PostShot {
  src: string
  caption: string
}

/**
 * Ein Beitrag in einer Sprache: der Text ohne eigene Bildzeilen und die Galerie – erst die Screenshots
 * aus dem Kommentar „shots:“, dann ältere Bildzeilen „![…](/news/…)“ aus dem Text (jede Datei nur
 * einmal), höchstens SHOTS_MAX Bilder.
 */
export function postContent(
  entry: Pick<ChangelogEntry, 'en' | 'de' | 'shots'>,
  lang: 'en' | 'de',
): { markdown: string; shots: PostShot[] } {
  const shots: PostShot[] = entry.shots.map((s) => ({ src: s.src, caption: s.caption?.[lang] ?? '' }))
  const text: string[] = []
  for (const block of splitPost(lang === 'de' ? entry.de : entry.en)) {
    if (block.kind === 'text') text.push(block.markdown)
    else if (!shots.some((s) => s.src === block.src)) shots.push({ src: block.src, caption: block.caption })
  }
  return { markdown: text.join('\n\n'), shots: shots.slice(0, SHOTS_MAX) }
}

/** Adresse einer Datei aus public/ im Repo (zum Tag der Version oder z. B. zu `main`). */
function githubFile(path: string, ref: string): string {
  return `https://raw.githubusercontent.com/PwiePixel/Cubiq-launcher/${ref}/public${path}`
}

/** Für GitHub: „/news/…“-Bilder auf die Datei im Repo zeigen lassen. */
function githubImages(markdown: string, ref: string): string {
  return markdown.replace(/\]\((\/news\/[\w.-]+\/[\w.-]+\.(?:png|webp|jpe?g))\)/g, (_, path: string) => `](${githubFile(path, ref)})`)
}

/** Screenshots aus dem Kommentar „shots:“ als Bildzeilen unter den Text einer Sprache. */
function withShots(markdown: string, entry: Pick<ChangelogEntry, 'shots'>, lang: 'en' | 'de'): string {
  const images = entry.shots.slice(0, SHOTS_MAX).map((s) => `![${s.caption?.[lang] ?? ''}](${s.src})`)
  return images.length ? `${markdown}\n\n${images.join('\n\n')}` : markdown
}

/**
 * Text für GitHub-Release und Auto-Update: das Bild des Updates, dann Update-Name, Englisch und Deutsch – jeweils mit
 * Screenshots. Nur Text für die Spieler, keine Namen oder Danksagungen. `ref` = Stand im Repo, aus dem die Bilder
 * kommen (Standard: der Tag der Version).
 */
export function releaseNotes(entry: ChangelogEntry, { ref }: { ref?: string } = {}): string {
  const version = entry.version ?? ''
  const at = ref ?? `v${version}`
  const en = githubImages(withShots(entry.en, entry, 'en'), at)
  const de = githubImages(withShots(entry.de, entry, 'de'), at)
  // Bewegt, wenn es die bewegte Fassung gibt – GitHub zeigt animierte PNGs als Animation.
  const motif = entry.banner?.anim ?? entry.banner?.motif
  const picture = motif ? `<p align="center"><img src="${githubFile(motif, at)}" alt="" width="220"></p>\n\n` : ''
  if (entry.title) return `${picture}# ${entry.title.en}\n\n${en}\n\n# ${entry.title.de}\n\n${de}\n`
  return `${picture}## What's new\n\n${en}\n\n## Neu in dieser Version\n\n${de}\n`
}

/** Titel des GitHub-Release: „Cubiq Launcher 0.4.4“, mit Update-Namen „Cubiq Launcher 0.5.0 – The Clip Update“. */
export function releaseTitle(entry: Pick<ChangelogEntry, 'title'> | null, version: string): string {
  const name = `Cubiq Launcher ${version.replace(/^v/, '')}`
  return entry?.title ? `${name} – ${entry.title.en}` : name
}

/** Fester Startwert für die Szene eines Updates – jede Version sieht anders, aber immer gleich aus. */
export function versionSeed(version: string): number {
  let h = 0x7e5
  for (const ch of version) h = (Math.imul(h, 31) + ch.charCodeAt(0)) >>> 0
  return h % 0xffffff
}
