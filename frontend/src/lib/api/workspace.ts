import { apiClient } from './client';
import type { Dashboard } from './finance';
import type { MonthlyRecordResponse } from './contracts';
import type { AiStatus } from './ai';

export const presetKeys = ['manufacturing', 'retail-trade', 'services', 'project-order'] as const;
export type PresetKey = typeof presetKeys[number];
export type WidgetSize = 'compact' | 'wide';
export interface WorkspaceWidget { key: string; order: number; size: WidgetSize }
export interface WidgetPreference extends WorkspaceWidget { hidden: boolean }
export interface WidgetMetadata {
  key: string; module: string; implemented: boolean; required: boolean;
  sizes: WidgetSize[]; available: boolean; reason: string | null;
}
export interface CashPeriodSummary {
  totalInflow: number | null; totalOutflow: number | null; net: number | null;
  endingCash: number | null; fromMonth: string | null; toMonth: string | null; recordCount: number;
}
export type CashSummaryByPeriod = Record<'3' | '6' | '12' | 'all', CashPeriodSummary>;
export interface DashboardWorkspaceResponse {
  registryVersion: string; presetVersion: string; presetKey: PresetKey; recommendedPresetKey: PresetKey;
  preferenceVersion: number; configurationVersion: number; personalized: boolean;
  widgets: WorkspaceWidget[]; catalog: WidgetMetadata[];
  preferences: WidgetPreference[];
  dashboard: Dashboard; records: MonthlyRecordResponse[];
  cashHistory: Dashboard['cashFlowHistory'];
  cashSummaryByPeriod: CashSummaryByPeriod; sourceMode: 'SNAPSHOT';
  generatedAt: string; aiStatus: AiStatus;
}
export interface WorkspaceVersion { version: number; registryVersion: string; configurationVersion: number }
export interface WorkspaceUpdate extends WorkspaceVersion { widgets: WidgetPreference[] }
/** Presentation preferences only. Identity, permissions and financial data never cross this boundary. */
export function workspacePayload(input: WorkspaceUpdate): WorkspaceUpdate {
  return { ...workspaceVersion(input), widgets: input.widgets.map(({ key, order, size, hidden }) => ({ key, order, size, hidden })) };
}
export function workspaceVersion(input: WorkspaceVersion): WorkspaceVersion {
  return { version: input.version, registryVersion: input.registryVersion, configurationVersion: input.configurationVersion };
}
export const workspaceApi = {
  get: (signal?: AbortSignal) => apiClient<DashboardWorkspaceResponse>('/workspace', { signal }),
  widgets: (signal?: AbortSignal) => apiClient<WidgetMetadata[]>('/workspace/widgets', { signal }),
  preview: (preset: PresetKey, signal?: AbortSignal) => {
    if (!presetKeys.includes(preset)) throw new Error('Unknown dashboard preset.');
    return apiClient<DashboardWorkspaceResponse>(`/workspace/presets/${preset}`, { signal });
  },
  save: (input: WorkspaceUpdate, signal?: AbortSignal) => apiClient<DashboardWorkspaceResponse>('/workspace/preferences', { method: 'PUT', body: JSON.stringify(workspacePayload(input)), signal }),
  reset: (input: WorkspaceVersion, signal?: AbortSignal) => apiClient<DashboardWorkspaceResponse>('/workspace/reset', { method: 'POST', body: JSON.stringify(workspaceVersion(input)), signal }),
};
