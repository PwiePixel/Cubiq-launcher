import { z } from 'zod'
// Relativ importiert, damit Tests die Datei ohne Nuxt laden können.
import { t } from './i18n'
import { compareText, formatRelative, formatShortDate, loaderLabels } from './format'
import { myTeamSchema } from './teamAccess'

// Cubiq-Dienste (Umhänge, Freunde, Verwaltung): Schemas für das, was der Kern
// liefert (wird beim Empfang geprüft), und für Eingaben, bevor sie rausgehen.
// Alle Netzwerkzugriffe laufen im Rust-Kern – hier gibt es nur Daten.

const uuid = z.string().regex(/^[0-9a-f]{32}$/)
const capeId = z.string().regex(/^[a-z0-9][a-z0-9_-]{0,39}$/)
const text = (max: number) => z.string().max(max)
const pngDataUrl = z.string().startsWith('data:image/png;base64,').nullable()
/** Umhang-Faktor 1–16 (bis 1024×512 je Frame), mitgelieferte wie eigene – wie `MAX_CAPE_SCALE` im Kern. */
const capeScale = z.number().int().min(1).max(16)
/** Event-ID (`halloween`) – wie `event_id` im Kern. */
export const eventId = z.string().regex(/^[a-z][a-z0-9_-]{0,31}$/)

export const cubiqStatusSchema = z.object({
  /** Ist in diesem Build ein Cubiq-Server eingetragen? Sonst gibt es die Online-Dienste nicht. */
  available: z.boolean().default(true),
  consent: z.enum(['accepted', 'declined']).nullable(),
  account: uuid.nullable(),
  signedIn: z.boolean(),
})

export const cubiqPrivacySchema = z.object({
  showBadge: z.boolean(),
  showCapeToOthers: z.boolean(),
  presenceVisibility: z.enum(['friends', 'nobody']),
  shareServer: z.boolean(),
  /** Lesebestätigungen im Chat (gegenseitig). */
  chatReadReceipts: z.boolean().default(true),
  /** „Schreibt …“ im Chat (gegenseitig). */
  chatTypingIndicator: z.boolean().default(true),
})

export const cubiqMeSchema = z.object({
  uuid,
  name: text(16),
  admin: z.boolean(),
  /** Alte Team-Rolle (API §22.1) – nur noch Anzeige; maßgeblich ist `team`. */
  role: z.enum(['admin', 'moderator']).nullable().catch(null).default(null),
  /** Rollen, Rechte, Rang und Straf-Grenzen (§24.2); `null` = kein Team-Mitglied. */
  team: myTeamSchema.nullable().catch(null).default(null),
  createdAt: text(40).nullable(),
  settings: cubiqPrivacySchema,
  activeCapeId: capeId.nullable(),
  /** Events, die für diesen Spieler aktiv sind (API §31); ältere Server: leer. */
  events: z.array(eventId).max(16).catch([]).default([]),
  /** Davon die Events, deren Look der Spieler sehen soll (Team-Sperre abgezogen). Fehlt ⇒ = `events`. */
  eventLook: z.array(eventId).max(16).optional().catch(undefined),
})

const userRef = z.object({ uuid, name: text(16) })

/** Bei einem geteilten Umhang: wer ihn dir gegeben hat und wer ihn gemacht hat. */
export const cubiqCapeShareSourceSchema = z.object({ from: userRef, creator: userRef })

export const cubiqCapeSchema = z.object({
  id: capeId,
  name: text(48),
  kind: z.enum(['builtin', 'upload', 'other']),
  unlock: z.enum(['free', 'code', 'admin', 'owner', 'event', 'reward', 'other']).catch('other'),
  /** Bei `event`: welches Event den Umhang gratis macht. */
  unlockEvent: eventId.nullable().catch(null).default(null),
  /** Bei `reward`: wofür (`halloween2026`) – nur als Belohnung, nicht einlösbar. */
  rewardOf: capeId.nullable().catch(null).default(null),
  status: z.enum(['approved', 'pending', 'rejected', 'other']),
  width: z.number().int().positive(),
  height: z.number().int().positive(),
  scale: capeScale,
  frames: z.number().int().min(1).max(64),
  frameTimeMs: z.number().int().min(20).max(10_000).nullable(),
  owned: z.boolean(),
  active: z.boolean(),
  rejectReason: text(200).nullable(),
  texture: pngDataUrl,
  /** An Freunde weitergebbar (eigener freigegebener Upload oder angenommener geteilter Umhang). */
  shareable: z.boolean(),
  /** Von einem Freund geteilt – sonst null. */
  shared: cubiqCapeShareSourceSchema.nullable(),
  /** Inhaber, die man sieht (als Ersteller alle, sonst der eigene Ast). */
  holders: z.number().int().min(0).max(1000),
})

const offerTime = text(40).nullable()
export const cubiqIncomingOfferSchema = z.object({ cape: cubiqCapeSchema, from: userRef, creator: userRef, createdAt: offerTime })
export const cubiqOutgoingOfferSchema = z.object({ cape: cubiqCapeSchema, to: userRef, createdAt: offerTime })
export const cubiqCapeOffersSchema = z.object({
  incoming: z.array(cubiqIncomingOfferSchema),
  outgoing: z.array(cubiqOutgoingOfferSchema),
})

export const cubiqCapeHolderSchema = z.object({
  uuid,
  name: text(16),
  status: z.enum(['offered', 'accepted']),
  grantedBy: userRef,
  createdAt: offerTime,
  acceptedAt: offerTime,
})
export const cubiqCapeHoldersSchema = z.object({
  holders: z.array(cubiqCapeHolderSchema),
  count: z.number().int().min(0),
  limit: z.number().int().min(0),
})

export const cubiqRedeemSchema = z.object({
  kind: z.enum(['cape', 'cosmetic']),
  capeId: capeId.nullable(),
  cosmeticId: capeId.nullable(),
  name: text(48),
  alreadyOwned: z.boolean(),
  /** Kopf-Kosmetik, die der Cubiq Client zeichnet (Quietscheente, v2-Modelle) – kann gleich aufgesetzt werden. */
  wearableHat: z.boolean(),
  /** Fach des Teils, damit „Jetzt aufsetzen?“ das richtige Fach belegt (ältere Kerne: `null` = Hut). */
  slot: z.enum(['hat', 'companion', 'wings', 'pet']).nullable().catch(null).default(null),
})

/**
 * Kosmetik-Fach: `hat` (Kopf), `companion` (Begleiter am Kopf, z. B. Fledermaus), `wings` (Rücken)
 * oder `pet` (Begleiter am Boden, z. B. Pinguin).
 */
export const CUBIQ_COSMETIC_SLOTS = ['hat', 'companion', 'wings', 'pet'] as const
export const cubiqCosmeticSlotSchema = z.enum(CUBIQ_COSMETIC_SLOTS).catch('hat').default('hat')
/** Wahl der abschaltbaren Teile (`{ beard: false }`, Anhang D): gültige Kennungen, höchstens 4. */
export const cubiqCosmeticOptionsSchema = z
  .record(z.string().regex(/^[a-z][a-z0-9_]{0,19}$/), z.boolean())
  .refine((o) => Object.keys(o).length <= 4)
  .catch({})
  .default({})
export const cubiqHatSchema = z.object({ id: capeId, name: text(48), slot: cubiqCosmeticSlotSchema, template: text(32), equipped: z.boolean() })
export type CubiqHat = z.infer<typeof cubiqHatSchema>

/** Wie man an ein Kosmetik-Teil kommt (unbekannte Arten → `other`). */
export const cubiqCosmeticUnlockSchema = z.enum(['free', 'code', 'admin', 'achievement', 'owner', 'event', 'reward', 'other']).catch('other')
export type CubiqCosmeticUnlock = z.infer<typeof cubiqCosmeticUnlockSchema>

/** Größe eines Begleiters am Boden (Standard 1; Kaiserpinguin 1,25). */
export const cubiqPetScaleSchema = z.number().min(0.5).max(2).catch(1).default(1)
/** Gefolge: zusätzlicher Begleiter am getragenen Pet (nur beim Creator selbst); das Modell lädt `headCosmeticModel(id)`. */
export const cubiqPetExtraSchema = z.object({
  id: capeId,
  name: text(48),
  petScale: cubiqPetScaleSchema,
  hash: z.string().regex(/^[A-Za-z0-9]{1,64}$/).nullable().catch(null).default(null),
  preview: z.boolean().catch(false).default(false),
})
export type CubiqPetExtra = z.infer<typeof cubiqPetExtraSchema>

/** Kopf-Kosmetik für die Skins-Seite: besessen oder gesperrt, v2 = 3D-Modell mit Vorschau. */
export const cubiqHeadCosmeticSchema = z.object({
  id: capeId,
  name: text(48),
  format: z.union([z.literal(1), z.literal(2)]),
  slot: cubiqCosmeticSlotSchema,
  template: text(32).nullable(),
  unlock: cubiqCosmeticUnlockSchema,
  /** Bei `event`: welches Event das Teil gratis macht (solange es läuft, danach behalten). */
  unlockEvent: eventId.nullable().catch(null).default(null),
  owned: z.boolean(),
  equipped: z.boolean(),
  /** Der Launcher kann das Modell in der 3D-Vorschau zeigen. */
  preview: z.boolean(),
  hash: z.string().regex(/^[A-Za-z0-9]{1,64}$/).nullable(),
  card: pngDataUrl,
  cardNight: pngDataUrl,
  frames: z.number().int().min(1).max(16),
  glowFrames: z.number().int().min(0).max(16),
  /** Getragener Begleiter am Boden: so viele sehen andere (1…4); `null` = unbekannt (gilt als 1). */
  count: z.number().int().min(1).max(4).nullable().catch(null).default(null),
  /** Über die Creator-Stufe bekommen (automatisch nach Fans). */
  creator: z.boolean().catch(false).default(false),
  /** Getragenes Teil: gewählte Optionen; fehlende gelten als Standard des Modells. */
  options: cubiqCosmeticOptionsSchema,
  /** Größe des Begleiters am Boden (Standard 1). */
  petScale: cubiqPetScaleSchema,
  /** Gefolge am getragenen Begleiter (höchstens 4); ältere Kerne kennen es nicht. */
  extras: z.array(cubiqPetExtraSchema).max(4).catch([]).default([]),
})
export type CubiqHeadCosmetic = z.infer<typeof cubiqHeadCosmeticSchema>

/** v2-Modell (grob im Kern geprüft, vollständig vor dem Bauen) + Texturen als PNG-Data-URLs. */
export const cubiqHeadCosmeticModelSchema = z.object({
  id: capeId,
  hash: z.string().regex(/^[A-Za-z0-9]{1,64}$/),
  model: z.record(z.string(), z.unknown()),
  texture: z.string().startsWith('data:image/png;base64,'),
  glow: pngDataUrl,
})
export type CubiqHeadCosmeticModel = z.infer<typeof cubiqHeadCosmeticModelSchema>

export const cubiqPlayerCapeSchema = z.object({
  uuid,
  badge: z.boolean(),
  capeId: capeId.nullable(),
  upload: z.boolean(),
  scale: capeScale,
  frames: z.number().int().min(1).max(64),
  frameTimeMs: z.number().int().nullable(),
  texture: pngDataUrl,
  /** Profil-Abzeichen (`halloween2026`, `pumpkinhunter2026` …); ältere Kerne: leer. */
  badges: z.array(capeId).max(16).catch([]).default([]),
  /** Sichtbare Spuk-Effekte (`hits`, `trail`), nur während Halloween. */
  fx: z.array(z.string().max(16)).max(4).catch([]).default([]),
})

export const cubiqGameSchema = z.object({
  version: text(32),
  loader: z.enum(['vanilla', 'fabric', 'quilt', 'forge', 'neoforge']),
  server: text(261).optional(),
})

export const cubiqPresenceSchema = z.object({
  state: z.enum(['online', 'in-game']),
  game: cubiqGameSchema.nullable(),
  updatedAt: text(40).nullable(),
})

export const cubiqFriendSchema = z.object({
  uuid,
  name: text(16),
  since: text(40).nullable(),
  presence: cubiqPresenceSchema.nullable(),
})

const requestEntry = z.object({ uuid, name: text(16), createdAt: text(40).nullable() })

export const cubiqFriendsSchema = z.object({
  friends: z.array(cubiqFriendSchema),
  requests: z.object({ incoming: z.array(requestEntry), outgoing: z.array(requestEntry) }),
  /** Offene Umhang-Angebote an mich (Details über `capeOffers`). */
  capeOffers: z.number().int().min(0).max(1000),
})

export const cubiqUserRefSchema = userRef
export const cubiqFriendRequestResultSchema = z.object({ status: z.enum(['sent', 'accepted']), user: cubiqUserRefSchema })
export const cubiqBlockedSchema = z.object({ uuid, name: text(16), since: text(40).nullable() })

const count = z.number().int().min(0)
export const cubiqAdminStatsSchema = z.object({
  users: z.object({ total: count, banned: count, activeLast24h: count, online: count }),
  sessions: count,
  capes: z.object({ builtin: count, approved: count, pending: count, rejected: count, reported: count, activeUsers: count }),
  codes: z.object({ active: count, redemptions: count }),
  friendships: count,
  pendingFriendRequests: count,
  eventStreams: count,
  /** Chat (ältere Server: `null`). */
  chat: z
    .object({
      conversations: count,
      groups: count,
      messages: count,
      messagesLast24h: count,
      images: count,
      storageBytes: count,
      storageLimitBytes: count,
    })
    .nullable()
    .default(null),
  /** Chat-Meldungen (ältere Server: `null`). */
  reports: z.object({ open: count, inReview: count, resolved: count, activeMutes: count }).nullable().default(null),
})

export const cubiqAdminCapeSchema = cubiqCapeSchema.extend({
  owner: cubiqUserRefSchema.nullable(),
  createdAt: text(40).nullable(),
  reviewedAt: text(40).nullable(),
  reviewedBy: text(64).nullable(),
  reports: z.object({ count, reasons: z.record(z.string(), count) }),
  /** Größe der gespeicherten Datei (ältere Server: `null`). */
  bytes: count.nullable(),
  /** Alle Uploads des Hochladers nach Status (inklusive dieses). */
  ownerStats: z.object({ uploads: count, approved: count, pending: count, rejected: count }).nullable(),
})

// Gewählte Dateien für den Umhang-Dialog (vom Kern geprüft; Bilder als Data-URL).
const imageDataUrl = z.string().regex(/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/)
const sourceName = text(64)
/** Bis 8192 für Bilder; fertige Umhang-Streifen (1024 × 512·32) sind bis 16384 hoch – wie der Kern. */
const sourceSide = z.number().int().min(1).max(16384)
export const cubiqCapeSourceSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('image'), name: sourceName, dataUrl: imageDataUrl, width: sourceSide, height: sourceSide }),
  z.object({
    kind: z.literal('animation'),
    name: sourceName,
    /** Herkunft (nur Anzeige). */
    format: z.enum(['gif', 'apng', 'video']),
    width: sourceSide,
    height: sourceSide,
    frames: z.array(imageDataUrl).min(1).max(32),
    /** Standzeit je gelieferter Frame (ms). */
    delaysMs: z.array(count).max(32),
    durationMs: count,
    sourceFrames: z.number().int().min(1),
  }),
  z.object({ kind: z.literal('studio'), name: sourceName, frames: z.number().int().min(1).max(64), scale: z.number().int().min(1).max(16) }),
])

/** Gewähltes Video (Pfad bleibt im Kern, hier nur Kennung + Eckdaten). */
export const cubiqCapeVideoSchema = z.object({
  token: z.string().regex(/^[0-9a-f]{32}$/),
  name: sourceName,
  durationMs: z.number().int().min(1),
  width: z.number().int().min(1).max(16_384),
  height: z.number().int().min(1).max(16_384),
})
export type CubiqCapeVideo = z.infer<typeof cubiqCapeVideoSchema>

export const cubiqCodeSchema = z.object({
  id: z.number().int().positive(),
  hint: text(8),
  /** Kosmetik- und Emote-Codes haben keinen Umhang (§8). */
  capeId: capeId.nullable().default(null),
  cosmeticId: text(40).nullable().default(null),
  maxUses: count,
  uses: count,
  expiresAt: text(40).nullable(),
  revokedAt: text(40).nullable(),
  note: text(200).nullable(),
  createdAt: text(40).nullable(),
  createdBy: text(80).nullable(),
  code: text(32).optional(),
})

export const cubiqAdminUserSchema = z.object({
  uuid,
  name: text(16).nullable(),
  known: z.boolean(),
  admin: z.boolean(),
  banned: z
    .object({ reason: text(200).nullable(), bannedAt: text(40).nullable(), bannedBy: text(80).nullable() })
    .nullable(),
  createdAt: text(40).nullable(),
  lastLoginAt: text(40).nullable(),
  settings: cubiqPrivacySchema.nullable(),
  activeCapeId: capeId.nullable(),
  grantedCapes: z.array(z.object({ capeId, source: text(40), grantedAt: text(40).nullable() })),
  uploads: count,
  friends: count,
  sessions: count,
  online: z.boolean(),
})

/** Stand der Cubiq-Synchronisation (Skins, Presets, Theme/Sprache). */
export const cubiqSyncStatusSchema = z.object({
  active: z.boolean(),
  syncing: z.boolean(),
  lastSyncAt: text(40).nullable(),
  problem: z.enum(['offline', 'error']).nullable(),
})

/** Event `cubiq-sync` nach jedem Abgleich: was sich lokal geändert hat. */
export const cubiqSyncEventSchema = z.object({
  account: uuid,
  changes: z.object({ skins: z.boolean(), presets: z.boolean(), settings: z.boolean() }),
  status: cubiqSyncStatusSchema,
})

export type CubiqStatus = z.infer<typeof cubiqStatusSchema>
export type CubiqPrivacy = z.infer<typeof cubiqPrivacySchema>
/** Event in der Team-Verwaltung (`GET /v1/admin/events`). */
const adminEventPlayers = z
  .array(
    z.object({
      uuid,
      name: text(16).catch(''),
      addedAt: z.union([z.string().max(40), z.number()]).nullable().catch(null).default(null),
    }),
  )
  .max(500)
  .catch([])
  .default([])

export const cubiqAdminEventSchema = z.object({
  id: eventId,
  enabled: z.boolean(),
  updatedAt: z.union([z.string().max(40), z.number()]).nullable().catch(null).default(null),
  players: adminEventPlayers,
  /** Spieler, für die das Team den Look des Events abgeschaltet hat (Features bleiben). */
  lookBlocked: adminEventPlayers,
})
export type CubiqAdminEvent = z.infer<typeof cubiqAdminEventSchema>
export type CubiqCosmeticSlot = z.infer<typeof cubiqCosmeticSlotSchema>

export type CubiqMe = z.infer<typeof cubiqMeSchema>
export type CubiqCape = z.infer<typeof cubiqCapeSchema>
export type CubiqIncomingOffer = z.infer<typeof cubiqIncomingOfferSchema>
export type CubiqOutgoingOffer = z.infer<typeof cubiqOutgoingOfferSchema>
export type CubiqCapeOffers = z.infer<typeof cubiqCapeOffersSchema>
export type CubiqCapeHolder = z.infer<typeof cubiqCapeHolderSchema>
export type CubiqCapeHolders = z.infer<typeof cubiqCapeHoldersSchema>
export type CubiqRedeem = z.infer<typeof cubiqRedeemSchema>
export type CubiqPlayerCape = z.infer<typeof cubiqPlayerCapeSchema>
export type CubiqGame = z.infer<typeof cubiqGameSchema>
export type CubiqPresence = z.infer<typeof cubiqPresenceSchema>
export type CubiqFriend = z.infer<typeof cubiqFriendSchema>
export type CubiqFriends = z.infer<typeof cubiqFriendsSchema>
export type CubiqUserRef = z.infer<typeof cubiqUserRefSchema>
export type CubiqBlocked = z.infer<typeof cubiqBlockedSchema>
export type CubiqAdminStats = z.infer<typeof cubiqAdminStatsSchema>
export type CubiqAdminCape = z.infer<typeof cubiqAdminCapeSchema>
export type CubiqCode = z.infer<typeof cubiqCodeSchema>
export type CubiqCapeSource = z.infer<typeof cubiqCapeSourceSchema>
export type CubiqAdminUser = z.infer<typeof cubiqAdminUserSchema>
export type CubiqSyncStatus = z.infer<typeof cubiqSyncStatusSchema>
export type CubiqSyncEvent = z.infer<typeof cubiqSyncEventSchema>
export type CubiqReportReason = 'inappropriate' | 'copyright' | 'impersonation' | 'other'
export type CubiqReviewList = 'pending' | 'approved' | 'rejected' | 'reported'

/** Antwort des Kerns prüfen; kaputte Daten werden zu einem verständlichen Fehler. */
export function cubiqParse<S extends z.ZodType>(schema: S, value: unknown): z.output<S> {
  const r = schema.safeParse(value)
  if (!r.success) throw new Error(t('cubiq.invalidData'))
  return r.data
}

// --- Eingaben -----------------------------------------------------------------------

const noControl = /^[^\u0000-\u001f\u007f​-‏‪-‮⁠-⁯﻿]*$/

export const cubiqPlayerNameSchema = z
  .string()
  .trim()
  .regex(/^[A-Za-z0-9_]{1,16}$/, { error: () => t('cubiq.validation.playerName') })

/** Name oder UUID (mit/ohne Bindestriche). */
export const cubiqTargetSchema = z
  .string()
  .trim()
  .refine(
    (s) => /^[A-Za-z0-9_]{1,16}$/.test(s) || /^[0-9a-fA-F]{32}$/.test(s) || /^[0-9a-fA-F-]{36}$/.test(s),
    { error: () => t('cubiq.validation.target') },
  )

/** Einlösecode wie der Server: Groß, ohne Trenner, O→0, I/L→1, 20 Zeichen Crockford-Base32. */
export function cubiqNormalizeCode(input: string): string | null {
  const s = input
    .toUpperCase()
    .replace(/[\s-]/g, '')
    .replace(/O/g, '0')
    .replace(/[IL]/g, '1')
  return /^[0-9A-HJKMNP-TV-Z]{20}$/.test(s) ? s : null
}

export const cubiqRedeemCodeSchema = z
  .string()
  .max(64, { error: () => t('cubiq.validation.codeTooLong') })
  .refine((s) => cubiqNormalizeCode(s) !== null, { error: () => t('cubiq.validation.codeFormat') })

export const cubiqCapeNameSchema = z
  .string()
  .trim()
  .max(32, { error: () => t('cubiq.validation.max32') })
  .regex(/^[\p{L}\p{N} _.,'!?&()+-]*$/u, { error: () => t('cubiq.validation.capeNameChars') })

export const cubiqNoteSchema = z
  .string()
  .trim()
  .max(200, { error: () => t('cubiq.validation.max200') })
  .regex(noControl, { error: () => t('cubiq.validation.invalidChars') })

export const cubiqNewCodesSchema = z.object({
  capeId: capeId,
  maxUses: z
    .number()
    .int()
    .min(1, { error: () => t('cubiq.validation.minUses') })
    .max(100_000, { error: () => t('cubiq.validation.maxUses') }),
  count: z
    .number()
    .int()
    .min(1, { error: () => t('cubiq.validation.minCount') })
    .max(100, { error: () => t('cubiq.validation.maxCount') }),
  expiresAt: z
    .string()
    .nullable()
    .refine((s) => s === null || !Number.isNaN(Date.parse(s)), { error: () => t('cubiq.validation.invalidExpiry') })
    .refine((s) => s === null || Date.parse(s) > Date.now(), { error: () => t('cubiq.validation.expiryPast') }),
  note: cubiqNoteSchema.nullable(),
})

// --- Helfer -------------------------------------------------------------------------

/** Aktueller Frame eines animierten Umhangs – für alle Spieler gleich (Wanduhr). */
export function cubiqFrameIndex(now: number, frames: number, frameTimeMs: number | null): number {
  if (frames <= 1 || !frameTimeMs || frameTimeMs <= 0) return 0
  return Math.floor(now / frameTimeMs) % frames
}

/** Kurzlabel, wie man an den Umhang kommt. */
export function cubiqUnlockLabel(cape: Pick<CubiqCape, 'unlock' | 'kind'> & { shared?: CubiqCape['shared']; rewardOf?: string | null }): string {
  if (cape.shared) return t('cubiq.unlock.shared')
  if (cape.kind === 'upload') return t('cubiq.unlock.own')
  switch (cape.unlock) {
    case 'free':
      return t('cubiq.unlock.free')
    case 'code':
      return t('cubiq.unlock.code')
    case 'admin':
      return t('cubiq.unlock.team')
    case 'event':
      return t('cubiq.unlock.event')
    case 'reward':
      return cape.rewardOf && !cape.rewardOf.startsWith('halloween') ? t('cubiq.unlock.reward') : t('cubiq.unlock.halloweenReward')
    default:
      return t('cubiq.unlock.locked')
  }
}

/** Status eines eigenen Uploads in Worten. */
export function cubiqStatusLabel(status: CubiqCape['status']): string | null {
  if (status === 'pending') return t('cubiq.status.pending')
  if (status === 'rejected') return t('cubiq.status.rejected')
  return null
}

/** „Spielt 1.21.1 (Fabric) auf play.example.net“ usw. */
export function cubiqPresenceText(presence: CubiqPresence | null): string {
  if (!presence) return t('common.status.offline')
  if (presence.state === 'online') return t('cubiq.presence.online')
  const game = presence.game
  if (!game) return t('cubiq.presence.inGame')
  const version = game.version
  const server = game.server
  if (game.loader === 'vanilla') {
    return server ? t('cubiq.presence.playingServer', { version, server }) : t('cubiq.presence.playing', { version })
  }
  const loader = loaderLabels[game.loader]
  return server
    ? t('cubiq.presence.playingLoaderServer', { version, loader, server })
    : t('cubiq.presence.playingLoader', { version, loader })
}

/** Sortierung: im Spiel, online, offline – innerhalb nach Name. */
export function cubiqSortFriends<T extends { name: string; presence: CubiqPresence | null }>(list: T[]): T[] {
  const rank = (f: T) => (f.presence?.state === 'in-game' ? 0 : f.presence ? 1 : 2)
  return [...list].sort((a, b) => rank(a) - rank(b) || compareText(a.name, b.name))
}

interface InstanceLike {
  id: string
  gameVersion: string
  loader: { kind: string }
}

/**
 * Passende Instanz zum Beitreten: gleiche Version + gleicher Loader, sonst
 * gleiche Version, sonst die zuletzt gespielte (die Liste ist danach sortiert).
 */
export function cubiqJoinInstance<T extends InstanceLike>(instances: T[], game: CubiqGame | null): T | null {
  if (!instances.length) return null
  if (game) {
    const exact = instances.find((i) => i.gameVersion === game.version && i.loader.kind === game.loader)
    if (exact) return exact
    const sameVersion = instances.find((i) => i.gameVersion === game.version)
    if (sameVersion) return sameVersion
  }
  return instances[0] ?? null
}

/** Fehler, die still als Zustand angezeigt werden statt als Meldung. */
export function cubiqIsQuiet(kind: string | undefined): boolean {
  return kind === 'cubiq_offline' || kind === 'cubiq_disabled' || kind === 'cubiq_no_account'
}

/**
 * Dezenter Hinweis zur Synchronisation („Mit Cubiq-Konto synchronisiert · vor 2 Minuten“).
 * `null` = aus (keine Einwilligung, Schalter aus oder kein Account).
 */
export function cubiqSyncLabel(status: CubiqSyncStatus | null): string | null {
  if (!status?.active) return null
  if (status.syncing) return t('cubiqSync.syncing')
  if (status.problem) return t('cubiqSync.offline')
  if (status.lastSyncAt) return t('cubiqSync.synced', { time: formatRelative(status.lastSyncAt, true) })
  return t('cubiqSync.pending')
}

/** Datum kurz in der eingestellten Sprache („23.09.2026“ / „9/23/26“), unbekannt → „–“. */
export function cubiqDate(iso: string | null | undefined): string {
  return formatShortDate(iso)
}

/**
 * Adresse der Cubiq API und der Website. Die alte Adresse `api.cubiq-launcher.invalid`
 * bleibt parallel erreichbar; alle Anfragen stellt ohnehin der Kern.
 */
export const CUBIQ_HOST = 'cubiq-launcher.invalid'

export const CUBIQ_PRIVACY_URL ='https://github.com/PwiePixel/Cubiq-launcher/blob/main/PRIVACY.md#cubiq-services'
