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
  if (!configuration?.modules) return false;
  const unconfigured = configuration.configured === false;
  return configuration.modules.some(module =>
    module.key === key &&
    module.implemented &&
    (module.enabled || unconfigured) &&
    (module.available || (unconfigured && module.reason === 'DISABLED'))
  );
}
export function routeModule(path: string): string | null {
  if (path === '/scan') return 'documents';
  if (path === '/ask-finsight') return 'ai';
  if (path === '/invoices' || path.startsWith('/invoices/') || path === '/bills' || path.startsWith('/bills/') || path === '/finance/recognition') return 'invoices';
  if (path === '/inventory' || path.startsWith('/inventory/')) return 'inventory';
  if (path === '/projects' || path.startsWith('/projects/') || path === '/finance/projects' || path.startsWith('/finance/projects/') || path === '/finance/funding') return 'projects';
  if (path === '/estimates' || path.startsWith('/estimates/')) return 'costing';
  if (path === '/samples' || path.startsWith('/samples/')) return 'production';
  if (path === '/reports' || path.startsWith('/reports/')) return 'reports';
  if (path === '/operations/calendar' || path.startsWith('/operations/') || path === '/records?view=calendar') return 'operations';
  if (path === '/finance/commissions' || path.startsWith('/finance/commissions/')) return 'commissions';
  if (path === '/finance/fx' || path.startsWith('/finance/fx/')) return 'multi_currency';
  if (path === '/records/receivables' || path === '/records/payables' || path === '/records?view=advances') return 'receivables';
  if (
    path === '/finance/accounts' ||
    path === '/finance/transactions' ||
    path === '/records/import' ||
    path === '/records/contacts' ||
    path === '/records/reconciliation' ||
    path === '/records/recurring' ||
    path === '/records?add=1'
  ) {
    return 'transactions';
  }
  return null;
}
