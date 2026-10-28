// Tauri-Attrappe für Release-Screenshots im normalen Browser (npx nuxt dev + Headless-Chrome).
// scripts/release-shots/cdp.mjs spielt sie vor dem Laden ein. Nur erfundene Testdaten – keine echten
// Namen, Pfade oder Tokens. Spielstarts sind gesperrt.
//
// Einstellbar über window.__MOCK_CFG (JSON, von cdp.mjs gesetzt):
//   { version: '0.1.0', locale: 'de', seen: '0.0.9' }   seen = zuletzt gesehene Version → „Was ist neu“
//   optional Aussehen: theme ('dark' | 'oled' | 'light'), accent ('cubiq' | 'amber' | 'emerald' | 'lapis' | 'amethyst' | 'ice' | 'orange'), animatedBackground (true)
// und window.__MOCK_RESPONSES (eigene Datei, cdp.mjs … --extra datei.js): { befehl: (args) => antwort }
;(() => {
  const cfg = window.__MOCK_CFG || {}
  const version = cfg.version || '0.1.0'
  const locale = cfg.locale || 'de'
  try {
    localStorage.setItem('cubiq.onboarding.done', '1')
    localStorage.setItem('cubiq.locale', locale)
    localStorage.setItem('cubiq.whatsNew.seen', cfg.seen || '9.9.9')
    localStorage.removeItem('cubiq.updateNews.hidden')
  } catch {}
  const iso = (msAgo) => new Date(Date.now() - msAgo).toISOString()
  const H = 36e5
  const settings = {
    minMemoryMb: 512, maxMemoryMb: 6144, javaPath: null, jvmArgs: '', resolution: { width: 1280, height: 720 },
    concurrentDownloads: 8, closeOnLaunch: false, showSnapshots: false, preferDedicatedGpu: true, performanceTuning: true,
    highPriority: false, autoFirewall: true, fullscreen: false, hooks: { preLaunch: null, wrapper: null, postExit: null },
    sync: { options: true, servers: true, resourcePacks: true, commandHistory: false, hotbar: false },
    ui: {
      theme: cfg.theme || 'dark', accent: cfg.accent || 'cubiq', design: 'cubiq',
      advancedRendering: true, animatedBackground: cfg.animatedBackground === true, worldsTab: true, screenshotsTab: true,
      historyTab: true, sidebarRecent: true, sidebarAccount: true, hideRightSidebar: false, compactLibrary: false, showPlayTime: true,
      language: locale, motion: 'full', bigPictureOnStart: false, bigPictureAuto: true, eventIcons: true, halloweenFlicker: true, halloweenFlickerHintSeen: true,
      startIntro: false, startIntroSound: false, startIntroFlicker: true, startIntroHintSeen: true,
    },
    allowLogUpload: true, discordPresence: true, java: { java8: null, java17: null, java21: null, java25: null },
    clips: { enabled: false, bufferSeconds: 30, resolution: '1080p', fps: 60, quality: 'medium', encoder: 'auto', systemAudio: true, microphone: false, folder: null, maxStorageGb: 20 },
    env: [],
    worldBackups: { enabled: true, keep: 5, maxTotalGb: 0 },
  }
  const overrides = { maxMemoryMb: null, javaPath: null, jvmArgs: null, resolution: null, cubiqClient: null, boost: null, performanceTuning: null, updateChannel: null, fullscreen: null, hooks: null, env: null, syncSeparate: [] }
  const instance = { id: 'survival', name: 'Survival mit Freunden', gameVersion: '1.21.1', loader: { kind: 'fabric', version: '0.16.5' }, createdAt: iso(40 * 24 * H), lastPlayed: iso(3 * H), totalPlaySeconds: 187 * 3600, overrides, icon: null, iconPath: null, bannerPath: null, group: null }
  const news = {
    fetchedAt: new Date().toISOString(),
    stale: false,
    items: [{ id: `cubiq-${version}`, source: 'launcher', title: `Cubiq Launcher v${version}`, summary: 'New version of the Cubiq Launcher.', date: iso(2 * H), tag: null, link: null }],
  }
  const opened = []
  window.__opened = opened
  const responses = {
    app_info: () => ({ version, os: 'windows', arch: 'x86_64' }),
    get_settings: () => settings,
    update_settings: ({ settings: next }) => Object.assign(settings, next),
    list_instances: () => [instance],
    get_instance: () => instance,
    // Spielzeit-Statistik: zwei Monate erfundene Sitzungen der einen Instanz.
    play_stats: () => {
      const now = Math.floor(Date.now() / 1000)
      const sessions = []
      for (let d = 60; d >= 1; d--) {
        if ((d * 7) % 5 === 0) continue
        const start = now - d * 86400 - ((d * 37) % 5) * 3600
        sessions.push({ instanceId: 'survival', gameVersion: '1.21.1', loader: 'fabric', start, end: start + (30 + ((d * 53) % 150)) * 60 })
      }
      return { sessions, running: [], carried: [], names: { survival: instance.name }, trackingSince: now - 60 * 86400, now }
    },
    reset_play_stats: () => null,
    // Welt-Backups: eine Welt mit drei Backups (Liste, Übersicht, Platz).
    world_backup_overview: () => ({
      enabled: true, global: true, instance: null, keep: 5, shared: false, job: null,
      worlds: [{ folder: 'Survival', name: 'Survival', exists: true, count: 3, latest: iso(3 * H), bytes: 412 * 1048576 }],
    }),
    world_backup_list: () => [
      { id: 'a-auto.zip', kind: 'auto', created: iso(3 * H), size: 138 * 1048576, name: 'Survival', version: '1.21.1', files: 2210 },
      { id: 'b-manual.zip', kind: 'manual', created: iso(30 * H), size: 137 * 1048576, name: 'Survival', version: '1.21.1', files: 2198 },
      { id: 'c-auto.zip', kind: 'auto', created: iso(54 * H), size: 137 * 1048576, name: 'Survival', version: '1.21.1', files: 2190 },
    ],
    world_backup_usage: () => ({ bytes: 412 * 1048576, count: 3, worlds: 1, orphanedBytes: 0 }),
    world_backup_jobs: () => [],
    list_accounts: () => [],
    running_games: () => [],
    bisect_active: () => [],
    list_servers: () => [],
    // Listen, die Seiten beim Laden abfragen – „null“ würde dort Fehler werfen.
    curseforge_blocked: () => [],
    all_screenshots: () => [],
    duplicate_mods: () => [],
    get_news: () => news,
    news_image: ({ url }) => url,
    open_external_url: ({ url }) => {
      opened.push(url)
      return null
    },
    'plugin:app|version': () => version,
    'plugin:app|name': () => 'Cubiq Launcher',
    'plugin:event|listen': ({ handler }) => handler,
    'plugin:event|unlisten': () => null,
    'plugin:window|is_maximized': () => false,
    'plugin:window|is_fullscreen': () => false,
    ...(window.__MOCK_RESPONSES || {}),
  }
  let cb = 1
  window.isTauri = true
  window.__TAURI_INTERNALS__ = {
    metadata: { currentWindow: { label: 'main' }, currentWebview: { windowLabel: 'main', label: 'main' } },
    plugins: {},
    transformCallback(callback, once) {
      const id = cb++
      window[`_${id}`] = (result) => {
        if (once) delete window[`_${id}`]
        callback?.(result)
      }
      return id
    },
    unregisterCallback(id) {
      delete window[`_${id}`]
    },
    convertFileSrc: (p) => p,
    async invoke(cmd, args) {
      if (/launch|join/i.test(cmd)) throw new Error('Im Screenshot-Modus gesperrt')
      const handler = responses[cmd]
      if (handler) return handler(args ?? {})
      if (cmd.startsWith('list_') || cmd.endsWith('_list') || cmd.endsWith('history')) return []
      return null
    },
  }
  window.__TAURI_EVENT_PLUGIN_INTERNALS__ = { unregisterListener() {} }
})()
