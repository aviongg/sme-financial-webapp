// copilot-v1 transport fixture. No external provider, tool execution or finance calculation.
export function copilotFixture({ language = 'en', businessLabel = 'fixture A', mode = 'openai', unavailable = false, unsupported = false, limited = false } = {}) {
  const evidence = [
    { id: 'health_evidence.ledgerCashInflow', tool: 'health_evidence', label: 'ledgerCashInflow', value: '123456789012345.6700' },
    { id: 'health_evidence.readinessStatus', tool: 'health_evidence', label: 'readinessStatus', value: 'UNKNOWN' },
    { id: 'budget_goal_status.budgets[0].name', tool: 'budget_goal_status', label: 'budgets #1 name', value: 'Synthetic planned budget (not posted)' },
  ];
  return {
    version: 'copilot-v1', language,
    answer: limited ? 'No verified answer is available.' : unsupported ? 'This request is outside the read-only scope.'
      : unavailable ? evidence.map(item => `${item.label}: ${item.value}.`).join(' ')
      : language === 'ur' ? 'یہ تصدیق شدہ مالی شواہد کی وضاحت ہے۔' : `Verified explanation for ${businessLabel}.`,
    evidence: limited || unsupported ? [] : evidence,
    tools: limited || unsupported ? [] : ['health_evidence', 'budget_goal_status'].map(name => ({ name, status: 'OK', message: null })),
    mode: unavailable || unsupported || limited || mode === 'disabled' ? 'deterministic' : 'openai', model: null,
    reasonCode: limited ? 'rate_limited' : unavailable ? 'provider_unavailable' : null,
  };
}
