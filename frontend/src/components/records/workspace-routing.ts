export const recordViews = ['activity', 'accounts', 'contacts', 'receivables', 'payables', 'advances', 'calendar', 'reconciliation', 'recurring', 'forecast', 'risks', 'review'] as const;
export type RecordView = typeof recordViews[number];
export function recordsView(params: URLSearchParams): RecordView {
 const explicit = params.get('view');
 if (recordViews.includes(explicit as RecordView)) return explicit as RecordView;
 return params.has('draftId') ? 'review' : 'activity';
}
/** Keep independent workspace filters, but never reopen an old form/detail on another tab. */
export function recordsTabHref(params: URLSearchParams, view: RecordView): string {
 const next = new URLSearchParams(params);
 for (const key of ['draftId', 'transactionId', 'detail', 'add', 'focus']) next.delete(key);
 next.set('view', view);
 return '/records?' + next.toString();
}
