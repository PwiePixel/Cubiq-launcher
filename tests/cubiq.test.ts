import { beforeEach, describe, expect, it } from 'vitest'
import { setLocale } from '../app/utils/i18n'
import {
  cubiqAdminCapeSchema,
  cubiqCapeHoldersSchema,
  cubiqCapeNameSchema,
  cubiqCapeOffersSchema,
  cubiqCapeSchema,
  cubiqCapeSourceSchema,
  cubiqCapeVideoSchema,
  cubiqDate,
  cubiqFrameIndex,
  cubiqFriendsSchema,
  cubiqJoinInstance,
  cubiqNewCodesSchema,
  cubiqNormalizeCode,
  cubiqParse,
  cubiqPresenceText,
  cubiqRedeemCodeSchema,
  cubiqSortFriends,
  cubiqStatusLabel,
  cubiqStatusSchema,
  cubiqSyncEventSchema,
  cubiqSyncLabel,
  cubiqSyncStatusSchema,
  cubiqTargetSchema,
  cubiqUnlockLabel,
  CUBIQ_HOST,
  cubiqRedeemSchema,
  cubiqHatSchema,
  cubiqSlotHidden,
} from '../app/utils/cubiq'

// Die meisten Erwartungen sind die deutschen Texte; Englisch wird extra geprüft.
beforeEach(async () => {
  await setLocale('de')
})

const cape = {
  id: 'team',
  name: 'Cubiq Team',
  kind: 'builtin',
  unlock: 'admin',
  status: 'approved',
  width: 128,
  height: 64,
  scale: 2,
  frames: 8,
  frameTimeMs: 150,
  owned: true,
  active: false,
  rejectReason: null,
  texture: 'data:image/png;base64,iVBORw0KGgo=',
  shareable: false,
  shared: null,
  holders: 0,
}

const ALEX = { uuid: '75c1a6f3112240abbdb57b9d21c64232', name: 'Alex' }
const BOB = { uuid: 'b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0', name: 'Bob' }

describe('Cubiq-Daten aus dem Kern', () => {
  it('prüft Status und Umhänge', () => {
    expect(cubiqStatusSchema.safeParse({ consent: null, account: '75c1a6f3112240abbdb57b9d21c64232', signedIn: false }).success).toBe(true)
    expect(cubiqStatusSchema.safeParse({ consent: 'vielleicht', account: null, signedIn: false }).success).toBe(false)
    expect(cubiqCapeSchema.safeParse(cape).success).toBe(true)
    // Texturen nur als PNG-Data-URL – nie eine fremde Adresse.
    expect(cubiqCapeSchema.safeParse({ ...cape, texture: 'https://evil.example/x.png' }).success).toBe(false)
    expect(cubiqCapeSchema.safeParse({ ...cape, id: '../x' }).success).toBe(false)
    expect(() => cubiqParse(cubiqCapeSchema, { ...cape, frames: 0 })).toThrow('ungültig')
    // HD-Umhänge bis Faktor 16 (1024×512) – darüber nicht.
    expect(cubiqCapeSchema.safeParse({ ...cape, width: 512, height: 256, scale: 8 }).success).toBe(true)
    expect(cubiqCapeSchema.safeParse({ ...cape, width: 1024, height: 512, scale: 16, frames: 32 }).success).toBe(true)
    expect(cubiqCapeSchema.safeParse({ ...cape, width: 1088, height: 544, scale: 17 }).success).toBe(false)
  })

  it('prüft Umhänge in der Prüfliste (Größe, Hochlader)', () => {
    const admin = {
      ...cape,
      kind: 'upload',
      unlock: 'owner',
      status: 'pending',
      owner: { uuid: '75c1a6f3112240abbdb57b9d21c64232', name: 'PwiePixel' },
      createdAt: '2026-09-25T10:00:00Z',
      reviewedAt: null,
      reviewedBy: null,
      reports: { count: 0, reasons: {} },
      bytes: 123_456,
      ownerStats: { uploads: 3, approved: 1, pending: 1, rejected: 1 },
    }
    expect(cubiqAdminCapeSchema.safeParse(admin).success).toBe(true)
    // Ältere Server kennen Größe und Statistik noch nicht.
    expect(cubiqAdminCapeSchema.safeParse({ ...admin, bytes: null, ownerStats: null }).success).toBe(true)
    expect(cubiqAdminCapeSchema.safeParse({ ...admin, bytes: -1 }).success).toBe(false)
  })

  it('prüft gewählte Dateien für den Umhang-Dialog', () => {
    const png = 'data:image/png;base64,iVBORw0KGgo='
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'image', name: 'a', dataUrl: png, width: 64, height: 32 }).success).toBe(true)
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'image', name: 'a', dataUrl: 'data:image/jpeg;base64,/9j/', width: 1, height: 1 }).success).toBe(true)
    // Nur Bild-Data-URLs – kein SVG, keine fremden Adressen.
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'image', name: 'a', dataUrl: 'data:image/svg+xml;base64,PHN2Zz4=', width: 1, height: 1 }).success).toBe(false)
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'image', name: 'a', dataUrl: 'https://evil.example/x.png', width: 1, height: 1 }).success).toBe(false)
    const gif = { kind: 'animation', format: 'gif', name: 'g', width: 20, height: 32, frames: [png, png], delaysMs: [100, 100], durationMs: 200, sourceFrames: 40 }
    expect(cubiqCapeSourceSchema.safeParse(gif).success).toBe(true)
    expect(cubiqCapeSourceSchema.safeParse({ ...gif, format: 'apng' }).success).toBe(true)
    expect(cubiqCapeSourceSchema.safeParse({ ...gif, format: 'video', frames: Array(32).fill(png) }).success).toBe(true)
    expect(cubiqCapeSourceSchema.safeParse({ ...gif, frames: Array(33).fill(png) }).success).toBe(false)
    expect(cubiqCapeSourceSchema.safeParse({ ...gif, format: 'flash' }).success).toBe(false)
    expect(cubiqCapeVideoSchema.safeParse({ token: 'a'.repeat(32), name: 'clip', durationMs: 5000, width: 1920, height: 1080 }).success).toBe(true)
    expect(cubiqCapeVideoSchema.safeParse({ token: '../etc', name: 'clip', durationMs: 5000, width: 1920, height: 1080 }).success).toBe(false)
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'studio', name: 'ender', frames: 4, scale: 8 }).success).toBe(true)
    expect(cubiqCapeSourceSchema.safeParse({ kind: 'studio', name: 'x', frames: 4, scale: 99 }).success).toBe(false)
  })

  it('prüft die Freundesliste', () => {
    const view = {
      friends: [
        {
          uuid: 'b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0',
          name: 'Bob',
          since: null,
          presence: { state: 'in-game', game: { version: '1.21.1', loader: 'fabric', server: 'play.example.net' }, updatedAt: null },
        },
      ],
      requests: { incoming: [], outgoing: [] },
      capeOffers: 2,
    }
    expect(cubiqFriendsSchema.safeParse(view).success).toBe(true)
    expect(cubiqFriendsSchema.safeParse({ ...view, capeOffers: -1 }).success).toBe(false)
    const broken = structuredClone(view)
    broken.friends[0]!.presence!.state = 'away'
    expect(cubiqFriendsSchema.safeParse(broken).success).toBe(false)
  })
})

describe('Umhänge teilen', () => {
  const upload = { ...cape, id: 'u0123456789abcdef0123', kind: 'upload', unlock: 'owner', frames: 1, frameTimeMs: null }

  it('prüft geteilte Umhänge, Angebote und Inhaber', () => {
    const shared = { ...upload, shareable: true, shared: { from: BOB, creator: ALEX }, holders: 1 }
    expect(cubiqCapeSchema.safeParse(shared).success).toBe(true)
    expect(cubiqCapeSchema.safeParse({ ...shared, shared: { from: { uuid: 'x', name: 'Bob' }, creator: ALEX } }).success).toBe(false)
    expect(cubiqCapeSchema.safeParse({ ...shared, holders: -1 }).success).toBe(false)

    const offers = {
      incoming: [{ cape: shared, from: BOB, creator: ALEX, createdAt: '2026-09-25T18:00:00.000Z' }],
      outgoing: [{ cape: upload, to: BOB, createdAt: null }],
    }
    expect(cubiqCapeOffersSchema.safeParse(offers).success).toBe(true)
    expect(cubiqCapeOffersSchema.safeParse({ ...offers, incoming: [{ ...offers.incoming[0], from: null }] }).success).toBe(false)

    const holders = {
      holders: [{ ...BOB, status: 'accepted', grantedBy: ALEX, createdAt: 'x', acceptedAt: 'y' }],
      count: 1,
      limit: 20,
    }
    expect(cubiqCapeHoldersSchema.safeParse(holders).success).toBe(true)
    expect(cubiqCapeHoldersSchema.safeParse({ ...holders, holders: [{ ...holders.holders[0], status: 'pending' }] }).success).toBe(false)
  })

  it('beschriftet geteilte Umhänge', async () => {
    const shared = { kind: 'upload' as const, unlock: 'owner' as const, shared: { from: BOB, creator: ALEX } }
    expect(cubiqUnlockLabel(shared)).toBe('Geteilt')
    expect(cubiqUnlockLabel({ kind: 'upload', unlock: 'owner', shared: null })).toBe('Eigener')
    await setLocale('en')
    expect(cubiqUnlockLabel(shared)).toBe('Shared')
  })
})

describe('Animierte Umhänge', () => {
  it('wählt den Frame nach der Wanduhr', () => {
    expect(cubiqFrameIndex(0, 8, 150)).toBe(0)
    expect(cubiqFrameIndex(149, 8, 150)).toBe(0)
    expect(cubiqFrameIndex(150, 8, 150)).toBe(1)
    expect(cubiqFrameIndex(150 * 8, 8, 150)).toBe(0)
    expect(cubiqFrameIndex(1_758_650_000_123, 8, 150)).toBe(Math.floor(1_758_650_000_123 / 150) % 8)
    expect(cubiqFrameIndex(12345, 1, null)).toBe(0)
  })

  it('beschriftet den Sperr-Status', () => {
    expect(cubiqUnlockLabel({ kind: 'builtin', unlock: 'free' })).toBe('Frei')
    expect(cubiqUnlockLabel({ kind: 'builtin', unlock: 'code' })).toBe('Code')
    expect(cubiqUnlockLabel({ kind: 'builtin', unlock: 'admin' })).toBe('Team')
    expect(cubiqUnlockLabel({ kind: 'upload', unlock: 'owner' })).toBe('Eigener')
    expect(cubiqStatusLabel('pending')).toBe('Wartet auf Freigabe')
    expect(cubiqStatusLabel('approved')).toBeNull()
  })

  it('beschriftet auf Englisch', async () => {
    await setLocale('en')
    expect(cubiqUnlockLabel({ kind: 'builtin', unlock: 'free' })).toBe('Free')
    expect(cubiqUnlockLabel({ kind: 'builtin', unlock: 'other' })).toBe('Locked')
    expect(cubiqStatusLabel('rejected')).toBe('Rejected')
  })
})

describe('Eingaben', () => {
  it('normalisiert Codes wie der Server', () => {
    expect(cubiqNormalizeCode('7k3qf-m2xpa-9rtvb-c4hjn')).toBe('7K3QFM2XPA9RTVBC4HJN')
    expect(cubiqNormalizeCode('OOOOO IIIII LLLLL 22222')).toBe('00000111111111122222')
    expect(cubiqNormalizeCode('UUUUU-UUUUU-UUUUU-UUUUU')).toBeNull()
    expect(cubiqRedeemCodeSchema.safeParse('kurz').success).toBe(false)
    expect(cubiqRedeemCodeSchema.safeParse('7K3QF-M2XPA-9RTVB-C4HJN').success).toBe(true)
  })

  it('kennt die neue Adresse der Cubiq API', () => {
    expect(CUBIQ_HOST).toBe('cubiq-launcher.invalid')
  })

  it('prüft Namen und Ziele', () => {
    expect(cubiqTargetSchema.safeParse('PwiePixel').success).toBe(true)
    expect(cubiqTargetSchema.safeParse('75c1a6f3-1122-40ab-bdb5-7b9d21c64232').success).toBe(true)
    expect(cubiqTargetSchema.safeParse('zwei Wörter').success).toBe(false)
    expect(cubiqTargetSchema.safeParse('x'.repeat(17)).success).toBe(false)
    expect(cubiqCapeNameSchema.safeParse('Mein Umhang!').success).toBe(true)
    expect(cubiqCapeNameSchema.safeParse('<script>').success).toBe(false)
  })

  it('prüft neue Codes', () => {
    const base = { capeId: 'team', maxUses: 1, count: 1, expiresAt: null, note: null }
    expect(cubiqNewCodesSchema.safeParse(base).success).toBe(true)
    expect(cubiqNewCodesSchema.safeParse({ ...base, count: 101 }).success).toBe(false)
    expect(cubiqNewCodesSchema.safeParse({ ...base, maxUses: 0 }).success).toBe(false)
    expect(cubiqNewCodesSchema.safeParse({ ...base, expiresAt: '2001-01-01T00:00:00Z' }).success).toBe(false)
    expect(cubiqNewCodesSchema.safeParse({ ...base, expiresAt: '2999-01-01T00:00:00Z' }).success).toBe(true)
  })

  it('meldet Fehler in der eingestellten Sprache', async () => {
    const tooMany = { capeId: 'team', maxUses: 1, count: 101, expiresAt: null, note: null }
    expect(cubiqNewCodesSchema.safeParse(tooMany).error?.issues[0]?.message).toBe('Höchstens 100 Codes auf einmal.')
    expect(cubiqRedeemCodeSchema.safeParse('kurz').error?.issues[0]?.message).toContain('20 Zeichen')
    await setLocale('en')
    expect(cubiqNewCodesSchema.safeParse(tooMany).error?.issues[0]?.message).toBe('At most 100 codes at once.')
    expect(cubiqTargetSchema.safeParse('zwei Wörter').error?.issues[0]?.message).toContain('Minecraft name')
  })
})

describe('Freunde', () => {
  const game = { version: '1.21.1', loader: 'fabric' as const, server: 'play.example.net' }

  it('beschreibt den Status', () => {
    expect(cubiqPresenceText(null)).toBe('Offline')
    expect(cubiqPresenceText({ state: 'online', game: null, updatedAt: null })).toBe('Online im Launcher')
    expect(cubiqPresenceText({ state: 'in-game', game, updatedAt: null })).toBe('Spielt 1.21.1 (Fabric) auf play.example.net')
    expect(cubiqPresenceText({ state: 'in-game', game: { version: '1.8.9', loader: 'vanilla' }, updatedAt: null })).toBe('Spielt 1.8.9')
    expect(cubiqPresenceText({ state: 'in-game', game: { version: '1.8.9', loader: 'vanilla', server: 'mc.example.net' }, updatedAt: null })).toBe(
      'Spielt 1.8.9 auf mc.example.net',
    )
  })

  it('beschreibt den Status auf Englisch', async () => {
    await setLocale('en')
    expect(cubiqPresenceText(null)).toBe('Offline')
    expect(cubiqPresenceText({ state: 'online', game: null, updatedAt: null })).toBe('Online in the launcher')
    expect(cubiqPresenceText({ state: 'in-game', game, updatedAt: null })).toBe('Playing 1.21.1 (Fabric) on play.example.net')
    expect(cubiqPresenceText({ state: 'in-game', game: null, updatedAt: null })).toBe('In game')
  })

  it('formatiert Daten nach Sprache', async () => {
    expect(cubiqDate(null)).toBe('–')
    expect(cubiqDate('kaputt')).toBe('–')
    expect(cubiqDate('2026-09-23T12:00:00Z')).toBe('23.09.2026')
    await setLocale('en')
    expect(cubiqDate('2026-09-23T12:00:00Z')).toBe('09/23/2026')
  })

  it('sortiert: im Spiel, online, offline', () => {
    const list = cubiqSortFriends([
      { name: 'zed', presence: null },
      { name: 'Anna', presence: null },
      { name: 'Bob', presence: { state: 'online' as const, game: null, updatedAt: null } },
      { name: 'Cid', presence: { state: 'in-game' as const, game, updatedAt: null } },
    ])
    expect(list.map((f) => f.name)).toEqual(['Cid', 'Bob', 'Anna', 'zed'])
  })

  it('findet die passende Instanz zum Beitreten', () => {
    const instances = [
      { id: 'zuletzt', gameVersion: '1.20.1', loader: { kind: 'vanilla' } },
      { id: 'gleich-vanilla', gameVersion: '1.21.1', loader: { kind: 'vanilla' } },
      { id: 'genau', gameVersion: '1.21.1', loader: { kind: 'fabric' } },
    ]
    expect(cubiqJoinInstance(instances, game)?.id).toBe('genau')
    expect(cubiqJoinInstance(instances, { ...game, loader: 'forge' })?.id).toBe('gleich-vanilla')
    expect(cubiqJoinInstance(instances, { ...game, version: '1.12.2' })?.id).toBe('zuletzt')
    expect(cubiqJoinInstance([], game)).toBeNull()
  })
})

describe('Cubiq-Synchronisation', () => {
  const status = { active: true, syncing: false, lastSyncAt: null, problem: null }

  it('prüft Status und Event aus dem Kern', () => {
    expect(cubiqSyncStatusSchema.safeParse(status).success).toBe(true)
    expect(cubiqSyncStatusSchema.safeParse({ ...status, problem: 'kaputt' }).success).toBe(false)
    const event = {
      account: '75c1a6f3112240abbdb57b9d21c64232',
      changes: { skins: true, presets: false, settings: true },
      status: { ...status, lastSyncAt: '2026-09-25T10:00:00Z' },
    }
    expect(cubiqSyncEventSchema.safeParse(event).success).toBe(true)
    expect(cubiqSyncEventSchema.safeParse({ ...event, account: '../x' }).success).toBe(false)
    expect(cubiqSyncEventSchema.safeParse({ ...event, changes: { skins: 'ja' } }).success).toBe(false)
  })

  it('zeigt einen dezenten Hinweis – nur wenn aktiv', () => {
    expect(cubiqSyncLabel(null)).toBeNull()
    expect(cubiqSyncLabel({ ...status, active: false, lastSyncAt: '2026-09-25T10:00:00Z' })).toBeNull()
    expect(cubiqSyncLabel({ ...status, syncing: true })).toBe('Synchronisiere mit deinem Cubiq-Konto …')
    expect(cubiqSyncLabel({ ...status, problem: 'offline' })).toContain('nicht erreichbar')
    expect(cubiqSyncLabel(status)).toBe('Wird gleich mit deinem Cubiq-Konto synchronisiert')
    const twoMinutesAgo = new Date(Date.now() - 2 * 60_000).toISOString()
    expect(cubiqSyncLabel({ ...status, lastSyncAt: twoMinutesAgo })).toBe('Mit Cubiq-Konto synchronisiert · vor 2 Minuten')
  })
})

describe('Kosmetik-Codes und Kopf-Kosmetik', () => {
  it('liest Umhang- und Kosmetik-Ergebnisse beim Einlösen', () => {
    expect(cubiqRedeemSchema.parse({ kind: 'cape', capeId: 'team', cosmeticId: null, name: 'Cubiq Team', alreadyOwned: false, wearableHat: false }).kind).toBe('cape')
    const duck = cubiqRedeemSchema.parse({ kind: 'cosmetic', capeId: null, cosmeticId: 'rubber_duck', name: 'Quietscheente', alreadyOwned: false, wearableHat: true })
    expect(duck.wearableHat).toBe(true)
    expect(() => cubiqRedeemSchema.parse({ kind: 'other', capeId: null, cosmeticId: null, name: 'x', alreadyOwned: false, wearableHat: false })).toThrow()
  })

  it('prüft Kopf-Kosmetik streng', () => {
    expect(cubiqHatSchema.parse({ id: 'rubber_duck', name: 'Quietscheente', template: 'duck', equipped: true }).equipped).toBe(true)
    expect(() => cubiqHatSchema.parse({ id: '../x', name: 'x', template: 'duck', equipped: false })).toThrow()
  })

  it('blendet Kosmetik-Fächer ohne Teile aus – Hüte erst, wenn es welche gibt', () => {
    const items = [{ slot: 'wings' }, { slot: 'pet' }, { slot: 'pet' }]
    expect(cubiqSlotHidden('hat', items)).toBe(true)
    expect(cubiqSlotHidden('companion', items)).toBe(true)
    expect(cubiqSlotHidden('wings', items)).toBe(false)
    expect(cubiqSlotHidden('pet', items)).toBe(false)
    expect(cubiqSlotHidden('hat', [...items, { slot: 'hat' }])).toBe(false)
    // Noch nicht geladen (oder nicht angemeldet): die anderen Fächer zeigen ihren Hinweis, der Hut-Abschnitt nicht.
    expect(cubiqSlotHidden('hat', null)).toBe(true)
    expect(cubiqSlotHidden('pet', null)).toBe(false)
  })
})
