// „Mit dem Minecraft Launcher spielen“: Antworten des Kerns (geprüft), welchen Weg der Spielen-Knopf
// nimmt und was die Oberfläche nach einer Übergabe zeigt. Getestet in tests/official-launcher.test.ts.
import { z } from 'zod'

/** Ausgabe des offiziellen Launchers. */
export const officialVariantSchema = z.enum(['windowsLegacy', 'windowsStore', 'linuxNative', 'linuxFlatpak'])
export type OfficialVariant = z.infer<typeof officialVariantSchema>

/** Womit der Spielen-Knopf startet. */
export const playWithSchema = z.enum(['auto', 'cubiq', 'official'])
export type PlayWith = z.infer<typeof playWithSchema>

export const officialStatusSchema = z.object({
  /** Gibt es diesen Weg auf diesem System (Windows- und Linux-Desktop)? */
  supported: z.boolean(),
  installed: z.array(officialVariantSchema).max(8),
  preferred: officialVariantSchema.nullable(),
  /** Läuft er gerade (nur am Prozessnamen erkannt)? */
  running: z.boolean(),
  playWith: playWithSchema,
  /** Die Erklärung vor der ersten Übergabe wurde bestätigt. */
  explained: z.boolean(),
  /** Eingetragene Cubiq-Installationen. */
  entries: z.number().int().nonnegative(),
})
export type OfficialStatus = z.infer<typeof officialStatusSchema>

export const officialPrefsSchema = z.object({ playWith: playWithSchema, explained: z.boolean() })

const installation = z.string().max(400)
export const handoffOutcomeSchema = z.discriminatedUnion('status', [
  z.object({ status: z.literal('started'), variant: officialVariantSchema, installation }),
  z.object({ status: z.literal('openManually'), variant: officialVariantSchema, installation }),
  z.object({ status: z.literal('notInstalled') }),
  z.object({ status: z.literal('neverOpened'), variant: officialVariantSchema, canOpen: z.boolean() }),
  z.object({ status: z.literal('running'), variant: officialVariantSchema }),
  z.object({ status: z.literal('needsAccess'), command: z.string().min(1).max(8192) }),
])
export type HandoffOutcome = z.infer<typeof handoffOutcomeSchema>

/** Mojangs Download-Seite für den offiziellen Launcher. */
export const OFFICIAL_DOWNLOAD_URL = 'https://www.minecraft.net/download'

/** Welchen Weg der Spielen-Knopf nimmt. */
export type PlayRoute = 'cubiq' | 'official'

/**
 * „Automatisch“: mit angemeldetem Konto startet Cubiq selbst, sonst übernimmt der offizielle Minecraft Launcher.
 * Wo es ihn nicht gibt (Handy) und am Messe-PC (eigener Demo-Weg) bleibt es immer bei Cubiq.
 */
export function playRoute(o: { playWith: PlayWith; signedIn: boolean; supported: boolean; messe?: boolean }): PlayRoute {
  if (o.messe || !o.supported) return 'cubiq'
  if (o.playWith === 'official') return 'official'
  if (o.playWith === 'cubiq') return 'cubiq'
  return o.signedIn ? 'cubiq' : 'official'
}

/** Direkt beitreten geht über den offiziellen Launcher nicht: Was sagt Cubiq dazu? */
export type JoinNote =
  | { kind: 'none' }
  /** Server aus der Liste: steht im Spiel unter „Mehrspieler“. */
  | { kind: 'serverList'; name: string }
  /** Freie Adresse: im Spiel über „Direkt verbinden“ eingeben. */
  | { kind: 'address'; address: string }
  /** Gehostete Welt: braucht den Cubiq-Link – geht nur, wenn Cubiq selbst startet. */
  | { kind: 'unsupported' }

export function joinNote(join: { serverName?: string | null; address?: string | null; world?: unknown }): JoinNote {
  if (join.world) return { kind: 'unsupported' }
  if (join.serverName) return { kind: 'serverList', name: join.serverName }
  if (join.address?.trim()) return { kind: 'address', address: join.address.trim() }
  return { kind: 'none' }
}

/** Was die Oberfläche nach der Antwort des Kerns zeigt. */
export type HandoffView =
  /** Eingetragen; `opened`: der offizielle Launcher ist offen (sonst selbst öffnen). */
  | { kind: 'done'; installation: string; opened: boolean; variant: OfficialVariant }
  | { kind: 'notInstalled' }
  | { kind: 'neverOpened'; canOpen: boolean }
  | { kind: 'running' }
  | { kind: 'needsAccess'; command: string }

export function handoffView(outcome: HandoffOutcome): HandoffView {
  switch (outcome.status) {
    case 'started':
      return { kind: 'done', installation: outcome.installation, opened: true, variant: outcome.variant }
    case 'openManually':
      return { kind: 'done', installation: outcome.installation, opened: false, variant: outcome.variant }
    case 'notInstalled':
      return { kind: 'notInstalled' }
    case 'neverOpened':
      return { kind: 'neverOpened', canOpen: outcome.canOpen }
    case 'running':
      return { kind: 'running' }
    case 'needsAccess':
      return { kind: 'needsAccess', command: outcome.command }
  }
}

/** Name der Installation, wie Cubiq sie im offiziellen Launcher anlegt (wie im Kern). */
export function installationName(instanceName: string): string {
  return `Cubiq · ${instanceName.trim()}`
}
