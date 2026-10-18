import { apiClient } from '../client';
import { isAccountId } from './account-contracts';
const isIsoTimestamp = (value: unknown): value is string =>
  typeof value === 'string' &&
  /^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/.test(value) &&
  Number.isFinite(Date.parse(value));
export type DraftStatus = 'DRAFT' | 'RECORDED' | 'DISCARDED';

export const importDraftEventTypes = ['DIRECT_SALE_RECEIPT', 'OPERATING_EXPENSE_PAYMENT'] as const;
export type ImportDraftEventType = typeof importDraftEventTypes[number];

export interface ImportDraftRowPayload {
  sourceRowId: string;
  sourceRowNumber: number;
  accountId: string | null;
  eventType: ImportDraftEventType | null;
  effectiveDate: string | null;
  amountInput: string;
  description: string | null;
  sourceReference: string | null;
}

export interface ImportDraftRowResult {
  sourceRowId: string;
  sourceRowNumber: number;
  sourceReference: string | null;
  draftId: string;
  status: 'SAVED' | 'REPLAYED';
  draftStatus: DraftStatus;
  readyToRecord: boolean;
  version: number;
  recordedTransactionId: string | null;
}

export interface ImportDraftsResponse {
  batchId: string;
  sourceLabel: string;
  savedCount: number;
  replayedCount: number;
  totalRowsInBatch: number;
  rows: ImportDraftRowResult[];
}

export interface ImportBatchDetailResponse {
  batchId: string;
  sourceLabel: string;
  totalRows: number;
  createdAt: string;
  updatedAt: string;
  rows: ImportDraftRowResult[];
}

export interface ImportBatchSummaryItem {
  batchId: string;
  sourceLabel: string;
  totalRows: number;
  createdAt: string;
  updatedAt: string;
}

export interface ImportBatchListResponse {
  items: ImportBatchSummaryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export class InvalidImportDraftResponse extends Error {
  constructor(message = 'Invalid import draft response') {
    super(message);
    this.name = 'InvalidImportDraftResponse';
  }
}

const ensure: (condition: unknown, msg?: string) => asserts condition = (condition, msg) => {
  if (!condition) throw new InvalidImportDraftResponse(msg);
};

const uuid = (value: unknown): value is string => typeof value === 'string' && isAccountId(value);

function isObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

export function parseImportDraftRowResult(value: unknown): ImportDraftRowResult {
  ensure(isObject(value), 'Row result must be an object');
  const r = value;
  ensure(typeof r.sourceRowId === 'string' && r.sourceRowId.length > 0 && r.sourceRowId.length <= 100, 'Invalid sourceRowId');
  ensure(Number.isSafeInteger(r.sourceRowNumber) && (r.sourceRowNumber as number) >= 1, 'Invalid sourceRowNumber');
  ensure(r.sourceReference === null || (typeof r.sourceReference === 'string' && r.sourceReference.length <= 255), 'Invalid sourceReference');
  ensure(uuid(r.draftId), 'Invalid draftId');
  ensure(r.status === 'SAVED' || r.status === 'REPLAYED', 'Invalid action status');
  ensure(['DRAFT', 'RECORDED', 'DISCARDED'].includes(r.draftStatus as string), 'Invalid draftStatus');
  ensure(typeof r.readyToRecord === 'boolean', 'Invalid readyToRecord flag');
  ensure(Number.isSafeInteger(r.version) && (r.version as number) >= 0, 'Invalid version');
  ensure(r.recordedTransactionId === null || uuid(r.recordedTransactionId), 'Invalid recordedTransactionId');

  return {
    sourceRowId: r.sourceRowId as string,
    sourceRowNumber: r.sourceRowNumber as number,
    sourceReference: (r.sourceReference as string | null) ?? null,
    draftId: r.draftId as string,
    status: r.status as 'SAVED' | 'REPLAYED',
    draftStatus: r.draftStatus as DraftStatus,
    readyToRecord: r.readyToRecord as boolean,
    version: r.version as number,
    recordedTransactionId: (r.recordedTransactionId as string | null) ?? null,
  };
}

export function parseImportDraftsResponse(value: unknown): ImportDraftsResponse {
  ensure(isObject(value), 'Response must be an object');
  const d = value;
  ensure(uuid(d.batchId), 'Invalid batchId');
  ensure(typeof d.sourceLabel === 'string' && d.sourceLabel.length <= 255, 'Invalid sourceLabel');
  ensure(Number.isSafeInteger(d.savedCount) && (d.savedCount as number) >= 0, 'Invalid savedCount');
  ensure(Number.isSafeInteger(d.replayedCount) && (d.replayedCount as number) >= 0, 'Invalid replayedCount');
  ensure(Number.isSafeInteger(d.totalRowsInBatch) && (d.totalRowsInBatch as number) >= 0, 'Invalid totalRowsInBatch');
  ensure(Array.isArray(d.rows), 'rows must be an array');

  const rows = (d.rows as unknown[]).map(parseImportDraftRowResult);
  return {
    batchId: d.batchId as string,
    sourceLabel: d.sourceLabel as string,
    savedCount: d.savedCount as number,
    replayedCount: d.replayedCount as number,
    totalRowsInBatch: d.totalRowsInBatch as number,
    rows,
  };
}

export function parseImportBatchDetailResponse(value: unknown): ImportBatchDetailResponse {
  ensure(isObject(value), 'Detail response must be an object');
  const d = value;
  ensure(uuid(d.batchId), 'Invalid batchId');
  ensure(typeof d.sourceLabel === 'string' && d.sourceLabel.length <= 255, 'Invalid sourceLabel');
  ensure(Number.isSafeInteger(d.totalRows) && (d.totalRows as number) >= 0, 'Invalid totalRows');
  ensure(isIsoTimestamp(d.createdAt) && isIsoTimestamp(d.updatedAt), 'Invalid timestamps');
  ensure(Array.isArray(d.rows), 'rows must be an array');

  const rows = (d.rows as unknown[]).map(parseImportDraftRowResult);
  return {
    batchId: d.batchId as string,
    sourceLabel: d.sourceLabel as string,
    totalRows: d.totalRows as number,
    createdAt: d.createdAt as string,
    updatedAt: d.updatedAt as string,
    rows,
  };
}

const keyHeader = (key?: string) => {
  if (!key) return undefined;
  const trimmed = key.trim();
  if (!trimmed || trimmed.length > 100) throw new Error('Invalid idempotency key');
  return { 'Idempotency-Key': trimmed };
};

export const importDraftsApi = {
  saveImportDrafts: async (
    payload: {
      batchId: string;
      sourceLabel: string;
      rows: ImportDraftRowPayload[];
    },
    idempotencyKey?: string,
    signal?: AbortSignal
  ): Promise<ImportDraftsResponse> => {
    ensure(uuid(payload.batchId), 'Invalid batchId');
    ensure(payload.rows.length > 0 && payload.rows.length <= 100, 'Batch chunk must contain 1-100 rows');

    const raw = await apiClient<unknown>('/finance/drafts/import', {
      method: 'POST',
      signal,
      headers: keyHeader(idempotencyKey),
      body: JSON.stringify(payload),
    });

    return parseImportDraftsResponse(raw);
  },

  getImportBatch: async (batchId: string, signal?: AbortSignal): Promise<ImportBatchDetailResponse> => {
    ensure(uuid(batchId), 'Invalid batchId');
    const raw = await apiClient<unknown>(`/finance/drafts/import/${batchId}`, {
      method: 'GET',
      signal,
    });
    return parseImportBatchDetailResponse(raw);
  },

  listImportBatches: async (page = 0, size = 20, signal?: AbortSignal): Promise<ImportBatchListResponse> => {
    ensure(Number.isSafeInteger(page) && page >= 0, 'Invalid page');
    ensure(Number.isSafeInteger(size) && size >= 1 && size <= 100, 'Invalid size');

    const params = new URLSearchParams({ page: String(page), size: String(size) });
    const raw = await apiClient<unknown>(`/finance/drafts/import?${params}`, {
      method: 'GET',
      signal,
    });
    ensure(isObject(raw), 'List response must be an object');
    const p = raw;
    ensure(Array.isArray(p.items), 'items must be an array');
    const items: ImportBatchSummaryItem[] = (p.items as unknown[]).map((item) => {
      ensure(isObject(item), 'Item must be an object');
      ensure(uuid(item.batchId), 'Invalid batchId');
      ensure(typeof item.sourceLabel === 'string', 'Invalid sourceLabel');
      ensure(Number.isSafeInteger(item.totalRows), 'Invalid totalRows');
      ensure(isIsoTimestamp(item.createdAt) && isIsoTimestamp(item.updatedAt), 'Invalid timestamps');
      return {
        batchId: item.batchId as string,
        sourceLabel: item.sourceLabel as string,
        totalRows: item.totalRows as number,
        createdAt: item.createdAt as string,
        updatedAt: item.updatedAt as string,
      };
    });

    return {
      items,
      page: p.page as number,
      size: p.size as number,
      totalElements: p.totalElements as number,
      totalPages: p.totalPages as number,
    };
  },
};
