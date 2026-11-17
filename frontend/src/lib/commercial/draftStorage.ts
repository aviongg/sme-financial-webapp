import {
  MAX_COMMERCIAL_LINES,
  type CommercialDocumentDraft,
  type CommercialKind,
  type CommercialLineDraft,
} from './form';
import type { CommercialDocumentResponse } from '../api/commercial';

export const COMMERCIAL_DRAFT_STORAGE_PREFIX = 'finsight:commercial-draft:v1:';
export const COMMERCIAL_DRAFT_SCHEMA_VERSION = 1;
export const COMMERCIAL_DRAFT_TTL_MS = 30 * 60 * 1000;
const MAX_FUTURE_SKEW_MS = 60 * 1000;

export interface StoredCommercialDraftEnvelope {
  version: typeof COMMERCIAL_DRAFT_SCHEMA_VERSION;
  savedAt: number;
  userId: string;
  businessId: string;
  route: string;
  kind: CommercialKind;
  documentId: string | null;
  baseServerVersion: number | null;
  draft: CommercialDocumentDraft;
}

export interface DraftStorageContext {
  userId: string;
  businessId: string;
  route: string;
}

export interface LoadCommercialDraftQuery extends DraftStorageContext {
  kind: CommercialKind;
  documentId: string | null;
  now?: number;
}

export interface SaveCommercialDraftInput extends DraftStorageContext {
  kind: CommercialKind;
  documentId: string | null;
  baseServerVersion: number | null;
  draft: CommercialDocumentDraft;
  now?: number;
}

export function normalizeDraftRoute(route: string): string {
  const trimmed = route.trim();
  if (!trimmed) return '/';
  const [withoutQuery] = trimmed.split(/[?#]/, 1);
  if (withoutQuery.length > 1 && withoutQuery.endsWith('/')) {
    return withoutQuery.slice(0, -1);
  }
  return withoutQuery || '/';
}

export function commercialDraftStorageKey({ userId, businessId, route }: DraftStorageContext): string {
  return (
    COMMERCIAL_DRAFT_STORAGE_PREFIX +
    `${encodeURIComponent(userId)}:${encodeURIComponent(businessId)}:${encodeURIComponent(normalizeDraftRoute(route))}`
  );
}

function getSafeSessionStorage(): Storage | null {
  try {
    if (typeof window === 'undefined' || !window.sessionStorage) return null;
    return window.sessionStorage;
  } catch {
    return null;
  }
}

function sanitizeLine(line: CommercialLineDraft): CommercialLineDraft {
  const clean: CommercialLineDraft = {
    id: typeof line.id === 'string' && line.id.trim() ? line.id.slice(0, 80) : 'line',
    description: typeof line.description === 'string' ? line.description : '',
    quantity: typeof line.quantity === 'string' ? line.quantity : '',
    unit: typeof line.unit === 'string' ? line.unit : '',
    unitPrice: typeof line.unitPrice === 'string' ? line.unitPrice : '',
  };
  if (typeof line.inventoryItemId === 'string' && line.inventoryItemId.trim()) {
    clean.inventoryItemId = line.inventoryItemId.trim();
  }
  return clean;
}

/** Minimize stored financial fields: excludes tokens, CSRF state, secrets, blobs and PII metadata. */
export function sanitizeCommercialDraft(draft: CommercialDocumentDraft): CommercialDocumentDraft {
  const lines = Array.isArray(draft.lines) ? draft.lines.slice(0, MAX_COMMERCIAL_LINES).map(sanitizeLine) : [];
  return {
    counterpartyName: typeof draft.counterpartyName === 'string' ? draft.counterpartyName : '',
    documentDate: typeof draft.documentDate === 'string' ? draft.documentDate : '',
    dueDate: typeof draft.dueDate === 'string' ? draft.dueDate : '',
    notes: typeof draft.notes === 'string' ? draft.notes : '',
    lines,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function isValidLine(value: unknown): value is CommercialLineDraft {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === 'string' &&
    typeof value.description === 'string' &&
    typeof value.quantity === 'string' &&
    typeof value.unit === 'string' &&
    typeof value.unitPrice === 'string' &&
    (value.inventoryItemId === undefined || typeof value.inventoryItemId === 'string')
  );
}

export function isValidCommercialDraftEnvelope(value: unknown): value is StoredCommercialDraftEnvelope {
  if (!isRecord(value)) return false;
  if (value.version !== COMMERCIAL_DRAFT_SCHEMA_VERSION) return false;
  if (typeof value.savedAt !== 'number' || !Number.isFinite(value.savedAt)) return false;
  if (typeof value.userId !== 'string' || !value.userId) return false;
  if (typeof value.businessId !== 'string' || !value.businessId) return false;
  if (typeof value.route !== 'string' || !value.route) return false;
  if (value.kind !== 'INVOICE' && value.kind !== 'BILL') return false;
  if (value.documentId !== null && typeof value.documentId !== 'string') return false;
  if (value.baseServerVersion !== null && (typeof value.baseServerVersion !== 'number' || !Number.isInteger(value.baseServerVersion))) {
    return false;
  }
  if (!isRecord(value.draft)) return false;
  const { counterpartyName, documentDate, dueDate, notes, lines } = value.draft;
  if (
    typeof counterpartyName !== 'string' ||
    typeof documentDate !== 'string' ||
    typeof dueDate !== 'string' ||
    typeof notes !== 'string' ||
    !Array.isArray(lines) ||
    lines.length > MAX_COMMERCIAL_LINES
  ) {
    return false;
  }
  return lines.every(isValidLine);
}

export function isExpiredEnvelope(envelope: StoredCommercialDraftEnvelope, now: number = Date.now()): boolean {
  return envelope.savedAt > now + MAX_FUTURE_SKEW_MS || now - envelope.savedAt > COMMERCIAL_DRAFT_TTL_MS;
}

export function saveCommercialDraft(input: SaveCommercialDraftInput, storage: Storage | null = getSafeSessionStorage()): boolean {
  if (!storage || !input.userId || !input.businessId || !input.route) return false;
  const normalizedRoute = normalizeDraftRoute(input.route);
  const envelope: StoredCommercialDraftEnvelope = {
    version: COMMERCIAL_DRAFT_SCHEMA_VERSION,
    savedAt: input.now ?? Date.now(),
    userId: input.userId,
    businessId: input.businessId,
    route: normalizedRoute,
    kind: input.kind,
    documentId: input.documentId ?? null,
    baseServerVersion: input.baseServerVersion ?? null,
    draft: sanitizeCommercialDraft(input.draft),
  };
  try {
    storage.setItem(commercialDraftStorageKey({ userId: input.userId, businessId: input.businessId, route: normalizedRoute }), JSON.stringify(envelope));
    return true;
  } catch {
    return false;
  }
}

export function loadCommercialDraft(
  query: LoadCommercialDraftQuery,
  storage: Storage | null = getSafeSessionStorage(),
): StoredCommercialDraftEnvelope | null {
  if (!storage || !query.userId || !query.businessId || !query.route) return null;
  const normalizedRoute = normalizeDraftRoute(query.route);
  const key = commercialDraftStorageKey({ userId: query.userId, businessId: query.businessId, route: normalizedRoute });
  let raw: string | null = null;
  try {
    raw = storage.getItem(key);
  } catch {
    return null;
  }
  if (!raw) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    safeRemove(storage, key);
    return null;
  }
  if (!isValidCommercialDraftEnvelope(parsed)) {
    safeRemove(storage, key);
    return null;
  }
  const now = query.now ?? Date.now();
  if (isExpiredEnvelope(parsed, now)) {
    safeRemove(storage, key);
    return null;
  }
  if (
    parsed.userId !== query.userId ||
    parsed.businessId !== query.businessId ||
    normalizeDraftRoute(parsed.route) !== normalizedRoute ||
    parsed.kind !== query.kind ||
    parsed.documentId !== (query.documentId ?? null)
  ) {
    return null;
  }
  return parsed;
}

/**
 * Only restore an existing-document draft when the authoritative document still belongs to the
 * matching business/kind/id, remains DRAFT, and has not advanced past the captured version.
 */
export function canRestoreCommercialDraftIntoDocument(
  envelope: StoredCommercialDraftEnvelope,
  document: CommercialDocumentResponse,
  activeBusinessId: string,
): boolean {
  if (envelope.businessId !== activeBusinessId) return false;
  if (document.businessId !== activeBusinessId) return false;
  if (document.id !== envelope.documentId || document.kind !== envelope.kind) return false;
  if (document.status !== 'DRAFT') return false;
  return envelope.baseServerVersion === document.version;
}

export function clearCommercialDraft(context: DraftStorageContext, storage: Storage | null = getSafeSessionStorage()): void {
  if (!storage || !context.userId || !context.businessId || !context.route) return;
  safeRemove(storage, commercialDraftStorageKey(context));
}

export function clearAllCommercialDrafts(storage: Storage | null = getSafeSessionStorage()): void {
  if (!storage) return;
  try {
    const keysToDelete: string[] = [];
    for (let i = 0; i < storage.length; i++) {
      const k = storage.key(i);
      if (k && k.startsWith(COMMERCIAL_DRAFT_STORAGE_PREFIX)) {
        keysToDelete.push(k);
      }
    }
    for (const k of keysToDelete) safeRemove(storage, k);
  } catch {
    // Storage inaccessible
  }
}

export function sweepExpiredCommercialDrafts(now: number = Date.now(), storage: Storage | null = getSafeSessionStorage()): void {
  if (!storage) return;
  try {
    const keysToCheck: string[] = [];
    for (let i = 0; i < storage.length; i++) {
      const k = storage.key(i);
      if (k && k.startsWith(COMMERCIAL_DRAFT_STORAGE_PREFIX)) keysToCheck.push(k);
    }
    for (const k of keysToCheck) {
      const raw = storage.getItem(k);
      if (!raw) continue;
      try {
        const parsed = JSON.parse(raw);
        if (!isValidCommercialDraftEnvelope(parsed) || isExpiredEnvelope(parsed, now)) {
          safeRemove(storage, k);
        }
      } catch {
        safeRemove(storage, k);
      }
    }
  } catch {
    // Storage inaccessible
  }
}

function safeRemove(storage: Storage, key: string): void {
  try {
    storage.removeItem(key);
  } catch {
    // Ignore storage removal errors
  }
}
