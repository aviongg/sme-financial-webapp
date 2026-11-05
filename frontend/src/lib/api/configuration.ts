import { apiClient } from './client';

export const operatingProfiles = ['MANUFACTURING', 'RETAIL_TRADE', 'SERVICES', 'PROJECT_ORDER'] as const;
export type OperatingProfile = typeof operatingProfiles[number];
export interface OperatingAnswers { holdsInventory: boolean; tracksProjects: boolean; sellsOnCredit: boolean }
export interface ModuleAvailability {
  key: string; implemented: boolean; enabled: boolean; required: boolean;
  dependencies: string[]; available: boolean;
  reason: 'NOT_IMPLEMENTED' | 'DISABLED' | 'PERMISSION_REQUIRED' | null;
}
export interface ConfigurationUpdate {
  operatingProfile: OperatingProfile; operatingAnswers: OperatingAnswers;
  requestedModules: string[]; version: number;
}
export interface BusinessConfiguration extends ConfigurationUpdate {
  modules: ModuleAvailability[]; recommendedModules: string[];
  terminology: { sales: 'sales' | 'serviceRevenue' | 'projectRevenue'; work: 'products' | 'services' | 'orders' | 'projects' };
  presetKey: string; registryVersion: string; configured: boolean; canManage: boolean;
}
/** Only capability settings cross the wire. Scoring categories and tenant identity never do. */
export function configurationPayload(input: ConfigurationUpdate): ConfigurationUpdate {
  return { operatingProfile: input.operatingProfile,
    operatingAnswers: { holdsInventory: input.operatingAnswers.holdsInventory, tracksProjects: input.operatingAnswers.tracksProjects, sellsOnCredit: input.operatingAnswers.sellsOnCredit },
    requestedModules: [...new Set(input.requestedModules)], version: input.version };
}
export const configurationApi = {
  get: (signal?: AbortSignal) => apiClient<BusinessConfiguration>('/business-configuration', { signal }),
  save: (input: ConfigurationUpdate, signal?: AbortSignal) => apiClient<BusinessConfiguration>('/business-configuration', { method: 'PUT', body: JSON.stringify(configurationPayload(input)), signal }),
  preview: (input: ConfigurationUpdate, signal?: AbortSignal) => apiClient<BusinessConfiguration>('/business-configuration/preview', { method: 'POST', body: JSON.stringify(configurationPayload(input)), signal }),
};
/** Missing configuration fails closed for optional actions, without suppressing historical reads. */
export function moduleAvailable(configuration: BusinessConfiguration | null, key: string) {
  return configuration?.modules?.some(module => module.key === key && module.implemented && module.enabled && module.available) === true;
}
export function routeModule(path: string): string | null {
  if (path === '/scan') return 'documents';
  if (path === '/ask-finsight') return 'ai';
  return null;
}
