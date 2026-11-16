/**
 * DEF-08 provider transparency. Pure classification of what actually produced a Copilot answer or a WhatsApp
 * delivery, from the per-item backend fields — never from the page-level status alone. No financial logic.
 */

export type CopilotProvenanceKind = 'ai' | 'fallback' | 'offline' | 'limited';
export type CopilotFallbackCause = 'provider' | 'quota' | 'ungrounded' | 'tool_budget';
export interface CopilotProvenance { kind: CopilotProvenanceKind; cause: CopilotFallbackCause | null }

/** AI not configured/approved for this business at status time: the offline assistant was the intended mode. */
const STATUS_UNAVAILABLE = new Set(['ai_disabled', 'simulation_prohibited', 'demo_not_enabled', 'provider_not_supported',
  'activation_pending', 'provider_not_configured', 'pilot_not_enabled']);
/** Provider usage/capacity guards: the provider was not called for this answer. */
const QUOTA = new Set(['provider_circuit_open', 'daily_quota_reached', 'provider_capacity_limited', 'request_in_progress', 'capacity_limited']);

/**
 * `ai` only when this response says the provider produced it (`mode: openai`). A deterministic response is a fallback
 * whenever the AI assistant was expected (status `openai`) or a per-answer failure reason is present; otherwise it is
 * the intended offline assistant. A rate-limited request is not an answer at all.
 */
export function copilotProvenance(response: { mode: string; reasonCode: string | null }, statusMode?: string | null): CopilotProvenance {
  const reason = response.reasonCode || null;
  if (response.mode === 'openai') return { kind: 'ai', cause: null };
  if (reason === 'rate_limited') return { kind: 'limited', cause: null };
  const aiExpected = statusMode === 'openai';
  if (!aiExpected && (reason === null || STATUS_UNAVAILABLE.has(reason))) return { kind: 'offline', cause: null };
  const cause: CopilotFallbackCause = reason === 'ungrounded_answer' ? 'ungrounded'
    : reason === 'tool_budget_exceeded' ? 'tool_budget'
      : reason !== null && QUOTA.has(reason) ? 'quota' : 'provider';
  return { kind: 'fallback', cause };
}

export type DeliveryProviderKind = 'meta' | 'mock' | 'disabled' | 'unknown';

/** Backend provider names: MetaWhatsAppCloudApiClient "meta", MockWhatsAppClient "mock", DisabledWhatsAppClient "disabled". */
export function deliveryProvider(providerName: string | null | undefined): DeliveryProviderKind {
  const name = (providerName || '').trim().toLowerCase();
  return name === 'meta' || name === 'mock' || name === 'disabled' ? name : 'unknown';
}

/** Backend GET /api/whatsapp/delivery-capability (WhatsAppDeliveryCapabilityResponse). */
export interface OutboundCapabilityResponse { provider: string | null; automaticWeeklySummaries: boolean | null }
export interface OutboundCapability { provider: DeliveryProviderKind; automatic: boolean | null }

/**
 * The outbound summary transport actually wired into the backend, for the "WhatsApp updates" card. Derived only from
 * the capability endpoint: consent, the module toggle, the inbound assistant and delivery history never imply it. A
 * missing, failed or unrecognised response is `unknown`, with no claim about automatic sending.
 */
export function outboundCapability(response: OutboundCapabilityResponse | null | undefined): OutboundCapability {
  if (!response) return { provider: 'unknown', automatic: null };
  const provider = deliveryProvider(response.provider);
  if (provider === 'unknown') return { provider, automatic: null };
  const automatic = typeof response.automaticWeeklySummaries === 'boolean' ? response.automaticWeeklySummaries : null;
  return { provider, automatic: provider === 'disabled' ? false : automatic };
}
