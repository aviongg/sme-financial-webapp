import type { DashboardWorkspaceResponse, WidgetMetadata, WidgetPreference, WidgetSize } from '../api/workspace';

/** Build only currently permitted preferences; the server retains inactive overrides. */
export function editableLayout(workspace: DashboardWorkspaceResponse): WidgetPreference[] {
  if (workspace.preferences) return workspace.preferences.map(widget => ({ ...widget })).sort((a, b) => a.order - b.order);
  const visible = [...workspace.widgets].sort((a, b) => a.order - b.order);
  const entries = visible.map(widget => ({ ...widget, hidden: false }));
  for (const widget of workspace.catalog) {
    if (widget.available && !entries.some(entry => entry.key === widget.key)) {
      entries.push({ key: widget.key, hidden: true, order: entries.length, size: widget.sizes[0] });
    }
  }
  return entries.map((widget, order) => ({ ...widget, order }));
}
export function moveWidget(layout: WidgetPreference[], key: string, direction: -1 | 1): WidgetPreference[] {
  const ordered = [...layout].sort((a, b) => a.order - b.order);
  const visible = ordered.filter(widget => !widget.hidden);
  const index = visible.findIndex(widget => widget.key === key);
  const target = visible[index + direction];
  if (index < 0 || !target) return layout;
  const from = ordered.findIndex(widget => widget.key === key), to = ordered.findIndex(widget => widget.key === target.key);
  [ordered[from], ordered[to]] = [ordered[to], ordered[from]];
  return ordered.map((widget, order) => ({ ...widget, order }));
}
export function changeWidget(layout: WidgetPreference[], metadata: WidgetMetadata, change: { hidden?: boolean; size?: WidgetSize }): WidgetPreference[] {
  if (!metadata.available || (metadata.required && change.hidden) || (change.size && !metadata.sizes.includes(change.size))) return layout;
  return layout.map(widget => widget.key === metadata.key ? { ...widget, ...change } : widget);
}
/** A local renderer is an explicit allowlist, never a server-supplied component path. */
export const implementedWidgetKeys = new Set(['health-score', 'health-components', 'score-completeness', 'cash-flow', 'cash-projection', 'financial-evidence', 'next-step', 'recommendation', 'financial-summary', 'ai-explanation', 'document-workflow']);

/** Rebase an unsaved customization draft onto the latest server workspace without overwriting untouched server changes or retaining unavailable widgets. */
export function rebaseDraft(previous: DashboardWorkspaceResponse, draft: WidgetPreference[], latest: DashboardWorkspaceResponse): WidgetPreference[] {
  const base = editableLayout(previous);
  const latestBase = editableLayout(latest);
  const baseByKey = new Map(base.map(widget => [widget.key, widget]));
  const latestByKey = new Map(latestBase.map(widget => [widget.key, widget]));
  const catalogByKey = new Map(latest.catalog.map(widget => [widget.key, widget]));
  const sortedDraft = [...draft].sort((a, b) => a.order - b.order);
  const draftByKey = new Map(sortedDraft.map(widget => [widget.key, widget]));
  const isPermitted = (key: string) => Boolean(catalogByKey.get(key)?.available && latestByKey.has(key));
  const baseCommonOrder = base.map(widget => widget.key).filter(key => draftByKey.has(key) && isPermitted(key));
  const draftCommonOrder = sortedDraft.map(widget => widget.key).filter(key => baseByKey.has(key) && isPermitted(key));
  const reorderedInDraft = baseCommonOrder.length !== draftCommonOrder.length || baseCommonOrder.some((key, index) => key !== draftCommonOrder[index]);
  const orderedKeys = reorderedInDraft
    ? [...sortedDraft.map(widget => widget.key).filter(isPermitted), ...latestBase.map(widget => widget.key).filter(key => isPermitted(key) && !draftByKey.has(key))]
    : latestBase.map(widget => widget.key).filter(isPermitted);

  return orderedKeys.map((key, order) => {
    const meta = catalogByKey.get(key)!;
    const latestItem = latestByKey.get(key)!;
    const baseItem = baseByKey.get(key);
    const draftItem = draftByKey.get(key);
    if (!draftItem) return { ...latestItem, order };
    const hiddenChanged = !baseItem || draftItem.hidden !== baseItem.hidden;
    const sizeChanged = !baseItem || draftItem.size !== baseItem.size;
    const hidden = meta.required ? false : hiddenChanged ? draftItem.hidden : latestItem.hidden;
    const size = sizeChanged && meta.sizes.includes(draftItem.size) ? draftItem.size : latestItem.size;
    return { key, hidden, order, size };
  });
}

export function cashNetDirection(net: number | null | undefined): 'surplus' | 'deficit' | 'unavailable' {
  if (net == null || Number.isNaN(net)) return 'unavailable';
  return net >= 0 ? 'surplus' : 'deficit';
}

export function resolveRecordNet(
  record: { month: string; cashInflow?: number | null; cashOutflow?: number | null; netCashFlow?: number | null },
  history?: { month: string; net: number | null }[]
): number | null {
  if (history) {
    const point = history.find(entry => entry.month === record.month);
    return point ? point.net ?? null : null;
  }
  if (record.netCashFlow !== undefined) return record.netCashFlow;
  return record.cashInflow != null && record.cashOutflow != null ? record.cashInflow - record.cashOutflow : null;
}
