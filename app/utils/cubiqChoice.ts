import type { CubiqConflict, CubiqOffer } from '~/types'

// „Mit oder ohne Cubiq Client?“ beim Installieren bzw. Importieren von Modpacks
// und Instanzen mit Mods. Der Kern liefert je Pack ein Angebot (CubiqOffer:
// passt es, was ist vorausgewählt, welche Mods überschneiden sich) und
// entscheidet am Ende selbst noch einmal (Einstellung „immer …“ gewinnt).
// Hier liegt nur die Dialog-Logik – ohne Vue, damit sie testbar bleibt.

/** Stellt der Dialog die Frage (zwei wählbare Karten)? */
export function cubiqAsks(offer: CubiqOffer | null | undefined): boolean {
  return !!offer && offer.applies && offer.supported && offer.policy === 'ask'
}

/** Karten anzeigen – wählbar oder (ohne Build) ausgegraut mit Begründung. */
export function cubiqShowsChoice(offer: CubiqOffer | null | undefined): boolean {
  return !!offer && offer.applies && offer.policy === 'ask'
}

/**
 * Vorauswahl: „Mit Cubiq Client“, außer das Pack bringt Mods mit, die sich
 * beißen, oder es gibt keinen Build. Ohne Angebot (Vorschau fehlgeschlagen)
 * gilt die Empfehlung „mit“ – der Kern prüft beim Installieren trotzdem.
 */
export function cubiqPreselect(offer: CubiqOffer | null | undefined): boolean {
  if (!offer) return true
  return offer.supported && offer.recommended
}

/**
 * Wert für den Kern: die Wahl, wenn gefragt wurde; `null` = Kern entscheidet
 * nach Einstellung bzw. Vorauswahl (keine Frage, Vanilla, „immer …“).
 */
export function cubiqRequest(offer: CubiqOffer | null | undefined, choice: boolean): boolean | null {
  if (!offer) return choice
  if (!offer.applies || offer.policy !== 'ask') return null
  if (!offer.supported) return false
  return choice
}

/** Mods, derentwegen „Ohne“ vorausgewählt ist (andere Clients, Minimap, HUD). */
export function cubiqStrongConflicts(offer: CubiqOffer | null | undefined): CubiqConflict[] {
  return (offer?.conflicts ?? []).filter((c) => c.kind !== 'zoom')
}

/** Mods, die nur einen Hinweis wert sind (eigener Zoom/Freelook). */
export function cubiqSoftConflicts(offer: CubiqOffer | null | undefined): CubiqConflict[] {
  return (offer?.conflicts ?? []).filter((c) => c.kind === 'zoom')
}

/**
 * Einzel-Dialog: Kommt das Angebot erst nach dem Öffnen (Vorschau lädt),
 * übernimmt die Auswahl die Vorauswahl – außer der Nutzer hat schon gewählt.
 */
export function cubiqAfterPreview(offer: CubiqOffer | null, touched: boolean, current: boolean): boolean {
  if (offer && !offer.supported) return false
  return touched ? current : cubiqPreselect(offer)
}

// --- Sammel-Import: eine Frage für alle, Ausnahmen je Instanz -----------------

export interface CubiqBulkItem {
  id: string
  cubiqClient: CubiqOffer | null
}

export interface CubiqBulkState {
  /** Wahl für alle: `true` = mit Cubiq Client. */
  all: boolean
  /** Instanzen, die anders als `all` importiert werden. */
  exceptions: Set<string>
}

/** Instanzen, die überhaupt gefragt werden. */
export function cubiqBulkAsking<T extends CubiqBulkItem>(items: T[]): T[] {
  return items.filter((i) => cubiqAsks(i.cubiqClient))
}

/**
 * Ausnahmen zur Wahl „für alle“: Bei „mit“ bleiben Instanzen mit sich beißenden
 * Mods ohne Cubiq Client (abwählbar). Bei „ohne“ gibt es anfangs keine.
 */
export function cubiqBulkDefaultExceptions(items: CubiqBulkItem[], all: boolean): Set<string> {
  if (!all) return new Set()
  return new Set(cubiqBulkAsking(items).filter((i) => !i.cubiqClient!.recommended).map((i) => i.id))
}

/** Start: „mit“ (Empfehlung) – außer keine der gefragten Instanzen passt dazu. */
export function cubiqBulkInit(items: CubiqBulkItem[]): CubiqBulkState {
  const asking = cubiqBulkAsking(items)
  const all = asking.length === 0 || asking.some((i) => i.cubiqClient!.recommended)
  return { all, exceptions: cubiqBulkDefaultExceptions(items, all) }
}

/** Neue Wahl für alle setzt die Ausnahmen auf den Standard zurück. */
export function cubiqBulkSetAll(items: CubiqBulkItem[], all: boolean): CubiqBulkState {
  return { all, exceptions: cubiqBulkDefaultExceptions(items, all) }
}

/** Häkchen „hier anders“ an/aus. */
export function cubiqBulkToggle(state: CubiqBulkState, id: string): CubiqBulkState {
  const exceptions = new Set(state.exceptions)
  if (exceptions.has(id)) exceptions.delete(id)
  else exceptions.add(id)
  return { all: state.all, exceptions }
}

/** Mit Cubiq Client für diese Instanz (nur für gefragte Instanzen sinnvoll). */
export function cubiqBulkWith(state: CubiqBulkState, id: string): boolean {
  return state.exceptions.has(id) ? !state.all : state.all
}

/** Wert für den Kern beim Import dieser Instanz. */
export function cubiqBulkRequest(state: CubiqBulkState, item: CubiqBulkItem): boolean | null {
  if (!item.cubiqClient) return null
  if (!cubiqAsks(item.cubiqClient)) return cubiqRequest(item.cubiqClient, false)
  return cubiqBulkWith(state, item.id)
}
