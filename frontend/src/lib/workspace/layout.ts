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
