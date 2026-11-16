// DEF-08 synthetic transport fixtures (copilot-v1 and WhatsApp delivery history). No provider, tool or finance logic.
// The unavailable shape mirrors Codex's captured evidence: docs/release/evidence/historical-browser-def08.json.
export const EXACT_VALUE = '123456789012345.6700';

export function copilotFixture({ language = 'en', unavailable = false, unsupported = false, limited = false, model = 'gpt-synthetic' } = {}) {
  const evidence = [
    { id: 'health_evidence.ledgerCashInflow', tool: 'health_evidence', label: 'ledgerCashInflow', value: EXACT_VALUE },
    { id: 'health_evidence.readinessStatus', tool: 'health_evidence', label: 'readinessStatus', value: 'UNKNOWN' },
    { id: 'budget_goal_status.budgets[0].name', tool: 'budget_goal_status', label: 'budgets #1 name', value: 'Synthetic planned budget (not posted)' },
  ];
  const deterministic = unavailable || unsupported || limited;
  return {
    version: 'copilot-v1', language,
    answer: limited ? 'No verified answer is available.' : unsupported ? 'This request is outside the read-only scope.'
      : unavailable ? evidence.map(item => `${item.label}: ${item.value}.`).join(' ')
        : language === 'ur' ? `تصدیق شدہ نقد آمد ${EXACT_VALUE} ہے۔` : `Verified cash inflow is ${EXACT_VALUE}.`,
    evidence: limited || unsupported ? [] : evidence,
    tools: limited || unsupported ? [] : ['health_evidence', 'budget_goal_status'].map(name => ({ name, status: 'OK', message: null })),
    mode: deterministic ? 'deterministic' : 'openai',
    model: deterministic ? null : model,
    reasonCode: limited ? 'rate_limited' : unavailable ? 'provider_unavailable' : null,
  };
}

export function deliveryFixture({ id = '44444444-4444-4444-8444-444444444444', providerName = 'mock', deliveryStatus = 'SENT', failureReason = null, targetMonth = '2026-10' } = {}) {
  return {
    id, businessId: '11111111-1111-4111-8111-111111111111', scoreResultId: null, targetMonth, sourceFingerprint: 'synthetic',
    deliveryCycle: 'weekly', idempotencyKey: `synthetic-${id}`, maskedDestinationNumber: '+92******4567', language: 'en',
    templateName: 'financial_health_weekly_summary_v1', providerName,
    providerMessageId: providerName === 'meta' && deliveryStatus === 'SENT' ? 'wamid.synthetic' : providerName === 'mock' ? `mock-${id}` : null,
    deliveryStatus, attemptCount: 1, scheduledAt: null, sentAt: deliveryStatus === 'SENT' ? '2026-10-06T09:00:00' : null,
    failedAt: deliveryStatus === 'FAILED' ? '2026-10-06T09:00:00' : null, failureReason, createdAt: '2026-10-06T09:00:00', updatedAt: '2026-10-06T09:00:00',
  };
}
