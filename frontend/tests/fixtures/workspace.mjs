/** Synthetic workspace contract fixture. These calculations are fixture setup, never application finance. */
import { configurationFixture, aiStatusFixture } from './phase-two.mjs';

const implemented = ['health-score', 'health-components', 'score-completeness', 'cash-flow', 'cash-projection',
  'financial-evidence', 'next-step', 'recommendation', 'financial-summary', 'ai-explanation', 'document-workflow'];
const future = { transactions: 'transactions', receivables: 'receivables', payables: 'receivables', invoices: 'invoices',
  projects: 'projects', costing: 'costing', inventory: 'inventory', production: 'production', commissions: 'commissions',
  'multi-currency': 'multi_currency', budgets: 'core_finance', 'funding-gap': 'projects', reports: 'reports' };
const presets = {
  manufacturing: ['health-score', 'cash-flow', 'financial-summary', 'health-components', 'financial-evidence', 'next-step', 'score-completeness', 'recommendation', 'cash-projection', 'ai-explanation'],
  'retail-trade': ['health-score', 'financial-summary', 'cash-flow', 'health-components', 'next-step', 'recommendation', 'score-completeness', 'cash-projection', 'ai-explanation'],
  services: ['health-score', 'financial-summary', 'health-components', 'cash-flow', 'financial-evidence', 'score-completeness', 'next-step', 'ai-explanation'],
  'project-order': ['health-score', 'cash-flow', 'next-step', 'cash-projection', 'financial-evidence', 'score-completeness', 'recommendation', 'ai-explanation'],
};
const monthIndex = month => { const [year, value] = month.split('-').map(Number); return year * 12 + value; };

export function workspaceFixture({ dashboard, records = [], configuration = configurationFixture(),
  aiStatus = aiStatusFixture(), role = 'OWNER', presetKey = configuration.presetKey,
  overrides = [], preferenceVersion = 0 } = {}) {
  const catalog = [...implemented, ...Object.keys(future)].map(key => {
    const moduleKey = key === 'ai-explanation' ? 'ai' : key === 'document-workflow' ? 'documents' : future[key] || 'core_finance';
    const isImplemented = implemented.includes(key), moduleState = configuration.modules.find(item => item.key === moduleKey);
    const reason = key === 'document-workflow' && role === 'VIEWER' ? 'PERMISSION_REQUIRED'
      : !isImplemented || !moduleState?.implemented ? 'NOT_IMPLEMENTED'
      : !moduleState.available ? 'MODULE_DISABLED'
      : key === 'ai-explanation' && !aiStatus.available ? 'AI_UNAVAILABLE' : null;
    return { key, module: moduleKey, implemented: isImplemented, required: key === 'health-score',
      sizes: key === 'health-score' ? ['wide'] : ['compact', 'wide'], available: reason === null, reason };
  });
  const selected = presets[presetKey];
  if (!selected) throw new Error(`Unknown synthetic workspace preset ${presetKey}`);
  const ordered = [...selected, ...implemented.filter(key => !selected.includes(key))];
  const preferences = ordered.map((key, order) => ({ key, order, hidden: !selected.includes(key),
    size: ['health-score', 'cash-flow', 'health-components', 'financial-evidence', 'ai-explanation'].includes(key)
      || (key === 'financial-summary' && presetKey !== 'project-order') ? 'wide' : 'compact',
    ...overrides.find(item => item.key === key) }))
    .filter(item => catalog.find(entry => entry.key === item.key).available)
    .sort((left, right) => left.order - right.order || left.key.localeCompare(right.key))
    .map((item, order) => ({ ...item, order }));
  const cashHistory = [...records].sort((a, b) => a.month.localeCompare(b.month)).map(record => ({ month: record.month,
    inflow: record.cashInflow, outflow: record.cashOutflow,
    net: record.cashInflow != null && record.cashOutflow != null ? record.cashInflow - record.cashOutflow : null,
    runningBalance: record.cashBalanceEom }));
  const cashSummaryByPeriod = Object.fromEntries(['3', '6', '12', 'all'].map(period => {
    const latest = cashHistory.at(-1), included = cashHistory.filter(point => period === 'all' || monthIndex(latest.month) - monthIndex(point.month) < Number(period));
    const sum = key => included.length && included.every(point => point[key] != null) ? included.reduce((total, point) => total + point[key], 0) : null;
    const totalInflow = sum('inflow'), totalOutflow = sum('outflow');
    return [period, { totalInflow, totalOutflow, net: sum('net'),
      endingCash: latest?.runningBalance ?? null, fromMonth: included[0]?.month ?? null, toMonth: latest?.month ?? null, recordCount: included.length }];
  }));
  return { registryVersion: 'f13-v1', presetVersion: 'f13-presets-v1', presetKey, recommendedPresetKey: configuration.presetKey,
    preferenceVersion, configurationVersion: configuration.version, personalized: overrides.length > 0,
    widgets: preferences.filter(item => !item.hidden).map(({ key, size }, order) => ({ key, order, size })),
    preferences, catalog, dashboard, records, cashHistory, cashSummaryByPeriod,
    sourceMode: 'SNAPSHOT', generatedAt: '2026-10-15T12:00:00Z', aiStatus };
}
