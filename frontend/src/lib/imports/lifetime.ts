import type { SourceTable } from './delimited';
import type { Classification, PreviewOptions } from './preview';

/** Independent local read generation. Clearing context aborts requests and rejects late file results. */
export class PreviewJob {
 private generation = 0;
 private controller: AbortController | null = null;
 private closed = false;
 start() {
  if (this.closed) throw new DOMException('Context closed', 'AbortError');
  this.controller?.abort(); const signal = (this.controller = new AbortController()).signal;
  const generation = ++this.generation; return { generation, signal };
 }
 accepts(job: { generation: number; signal: AbortSignal }) { return !this.closed && !job.signal.aborted && job.generation === this.generation; }
 close() { this.closed = true; this.generation++; this.controller?.abort(); }
}

export type StoredPreviewSettings = Pick<PreviewOptions, 'headings' | 'mapping' | 'mode' | 'dateFormat' | 'inToken' | 'outToken' | 'classification'>;

export interface StoredPreviewSession {
 source: { table: SourceTable; filename: string | null } | null;
 paste: string;
 settings: StoredPreviewSettings;
 step: 'mapping' | 'review';
 accountId: string;
 currency: string;
 excluded: Set<number>;
 selected: Set<number>;
 overrides: Record<number, Classification>;
 issuesOnly: boolean;
 page: number;
}

let activeStoreKey: string | null = null;
let activeStoreSession: StoredPreviewSession | null = null;

export function getStoredPreview(key: string | null | undefined): StoredPreviewSession | null {
 if (!key || key !== activeStoreKey) return null;
 return activeStoreSession;
}

export function saveStoredPreview(key: string | null | undefined, state: StoredPreviewSession): void {
 if (!key) return;
 activeStoreKey = key;
 activeStoreSession = state;
}

export function clearStoredPreview(key?: string | null): void {
 if (!key || key === activeStoreKey) {
  activeStoreKey = null;
  activeStoreSession = null;
 }
}

if (typeof window !== 'undefined') {
 window.addEventListener('finsight-session-expired', () => clearStoredPreview());
 window.addEventListener('finsight-business-changed', () => clearStoredPreview());
}
