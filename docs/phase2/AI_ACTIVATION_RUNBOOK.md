# AI-LAUNCH-01 activation and operations

Owner: Developer B. Updated 15 October 2026. **Paid activation and client launch are deferred.** Engineering and local simulations do not establish real GPT compatibility, financial quality, billing or production acceptance. This document authorizes no account, billing, credential or deployment action.

## Current implementation

The Spring backend contains a Responses API adapter targeting `gpt-6-luna`, a deterministic simulated provider, a B-owned read-only MVP evidence adapter, strict grounding, explanation caching, quotas and audit metadata. Existing deterministic insights, recommendations and their lifecycle remain authoritative. AI is requested explicitly by the user; loading the dashboard does not call a provider.

The first release deliberately constrains model output to selecting backend-approved explanation clauses and evidence references. The backend renders their exact financial values and language. Arbitrary model prose, amounts, causes or actions are rejected. This trades conversational breadth for checkable financial grounding. Ask FinSight routes supported score, monthly snapshot, recorded cash, projection and existing advice questions locally. It does not query unfinished inventory, invoice, receivable or project services. It cannot post financial changes or use arbitrary SQL, URLs or tools.

Default application configuration is `AI_ENABLED=false`, `AI_PROVIDER=mock`, `AI_DEMO_ENABLED=false`, `AI_LIVE_ACTIVATION_APPROVED=false`, an empty key and an empty pilot allowlist. Normal startup requires no API account. A business must also enable the `ai` module and the session must retain financial-read permission.

## Development simulation

For an isolated development environment using synthetic data, set the backend's Spring profile to `dev` or `demo`, `AI_ENABLED=true`, `AI_PROVIDER=mock`, and `AI_DEMO_ENABLED=true`. Keep live approval false and the key empty. A `prod` or `production` profile prohibits simulation even when combined with `dev`. The UI identifies simulated responses explicitly. Provider failures produce labeled deterministic responses, never silently simulated GPT responses.

Use the tests in [DEVELOPMENT_SETUP](DEVELOPMENT_SETUP.md) and [offline evaluation](../../backend/src/test/resources/ai/README.md). Loopback HTTP fixtures exercise the production adapter without connecting to OpenAI. The 100 distinct-question synthetic cases across 20 families test local routing, grounding and validation; they do not measure a real model.

## Future activation: separate owner-controlled milestone

1. Obtain explicit authorization for funded provider tests and the intended data scope. Record P12 reciprocal review: A reviews financial evidence and permissions; B reviews orchestration, evaluation, provider privacy and cost controls. C11 remains A-owned; its implementation is not required for the current B adapter. Review P09/C03 module access as part of security acceptance.
2. In the OpenAI API platform, create or select the authorized organization and a dedicated project. An authorized account administrator activates billing, chooses a budget and sets available project/model access, rate limits and spend controls. Review the current [production guidance](https://developers.openai.com/api/docs/guides/production-best-practices) and [spend limits](https://developers.openai.com/api/docs/guides/spend-limits). Alerts alone are not enforcement; hard-limit propagation may not be instantaneous. Do not treat application request counts as a currency budget.
3. Create a project-scoped backend key with the least permissions needed for Responses. Put it in the deployment's secret manager as `OPENAI_API_KEY`, never a committed env file, frontend `NEXT_PUBLIC_*` variable, screenshot or log. Restrict access and establish rotation/revocation ownership. Do not print the value to verify injection.
4. Verify model availability for that project against the current [model documentation](https://developers.openai.com/api/docs/models/gpt-6-luna). Recheck Responses and strict structured-output support. The model is configurable through `OPENAI_MODEL`; changing it requires compatibility and quality reevaluation. Offline contract tests cannot guarantee zero code changes at live activation.
5. Review provider data terms, residency and retention for the actual project and endpoint using [data controls](https://developers.openai.com/api/docs/guides/your-data). The adapter sends `store:false`; that does not establish zero retention for abuse monitoring or other provider controls. Obtain appropriate approval before sending customer financial evidence. First tests must use approved synthetic fixtures.
6. Prepare a staging backend with `AI_PROVIDER=openai`, `AI_ENABLED=true`, `AI_DEMO_ENABLED=false`, `AI_LIVE_ACTIVATION_APPROVED=true`, the approved model/key and `AI_ALLOWED_BUSINESS_IDS` containing only selected synthetic pilot business UUIDs. Module settings and existing role checks still apply. **Existing Compose files do not forward these new variables automatically**: explicitly pass approved variables and the secret through the deployment configuration, then verify status without revealing the key. Merely editing the root `.env` is insufficient.
7. Run controlled compatibility checks: valid structured response, refusal/incomplete response, malformed output, rate limit/authentication errors, timeout and unreachable service. Confirm fallback provenance, no repeated retries, no credential/provider-body logging and no unexpected external destinations. Production HTTP targets only `https://api.openai.com/v1/responses`; redirects are disabled. Inspect the current [Responses contract](https://developers.openai.com/api/reference/python/resources/responses/methods/create) and [structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs) when assessing drift.
8. Rerun all 100 offline contract cases, including the deliberately malformed provider-output cases. Separately build an approved real-provider evaluation runner using the supported-topic and unsupported-question fixtures, with independent expert review of English/Urdu explanations, sparse data, evidence parity, null/zero distinctions and planned/actual qualification. The offline mutation suite is not a live-model quality runner. Record disagreements and rejection/fallback rates. Do not promote merely because JSON parses. Recheck existing score and recommendation status invariants.
9. Measure actual input/output tokens, per-request cost, latency percentiles, timeout/rejection rate and cache hit rate. Tune bounded output and quotas from measured results. The configured output cap also bounds reasoning/output budget according to the current API contract. The app does not currently export a provider token-cost ledger; use project usage and approved operational monitoring, and do not claim request counts are actual token costs.
10. Complete relevant deployed HTTPS/session/CSRF/tenant/role tests, production DB migration/grants and mobile/Urdu acceptance. Record remaining baseline regression defects separately. Add only approved pilot businesses, monitor them, and expand after explicit acceptance. Never mark this current offline milestone client-launch complete.

## Runtime bounds

All variables are backend-only. Values outside the ranges below are clamped by `AiProperties`.

| Variable | Default | Effective range / scope |
|---|---|---|
| `AI_TIMEOUT_SECONDS` | 15 | 1–30; whole provider exchange |
| `AI_REQUESTS_PER_MINUTE` | 10 | 1–60 per business, including cache/fallback requests |
| `AI_DAILY_PROVIDER_CALLS` | 100 | 1–10,000 per business per UTC day |
| `AI_MAX_CONCURRENT_PROVIDER_CALLS` | 4 | 1–32 process-wide; also one in flight per business |
| `AI_CACHE_TTL_SECONDS` | 300 | 1–3,600 |
| `AI_MAX_CACHE_ENTRIES` | 500 | 1–5,000 across this process |
| `AI_MAX_TRACKED_BUSINESSES` | 1,000 | 1–10,000; reject new tracking when capacity is full |
| `AI_CIRCUIT_FAILURE_THRESHOLD` | 3 | 1–10 failures |
| `AI_CIRCUIT_COOLDOWN_SECONDS` | 60 | 1–600 |
| `AI_MAX_OUTPUT_TOKENS` | 800 | 256–2,000 |

Quotas, circuit state and caches are bounded **process-local memory**, reset on restart and do not constitute a durable billing cap. Use a single backend replica for the initial controlled pilot, or establish shared enforced quotas at the gateway/provider project before enabling multiple replicas. Even a single replica requires provider spend controls because restarts reset counters. No automatic provider retry occurs. Request and response payloads are bounded; questions are at most 1,000 characters and context at most eight turns.

Cache keys include business, actor, role, language, explanation/source/month, evidence fingerprint, schema/prompt and provider/model. Evidence is fetched again after the provider exchange; stale content is rejected. Session, membership and module access are rechecked before delivery. Kill-switch/provider/pilot changes are also rechecked for in-flight success. Runtime environment changes normally require a controlled restart/redeploy.

## Privacy, retention and operations

The provider receives only a bounded anonymous evidence/approved-clause catalog. Raw questions, conversation history, user/business identifiers, names and contact details are not sent. The browser keeps conversation state in memory within the active business session, clears it on business/auth changes, and does not persist chats to local storage or a database. Provider-selected explanations may reside in server memory until the cache expires or the process restarts. Existing financial records and audit retention follow their existing policies; this feature does not delete or reclassify them.

Audit events record fixed diagnostic metadata: type, mode, result, prompt version, cache hit and latency, with actual actor and business linkage. They exclude prompts, financial payloads, credentials and provider error bodies. Monitor reason-code counts, failures, saturation, quota/circuit fallbacks and the provider project's usage. Investigate sustained `provider_unavailable`, `provider_timeout`, `invalid_provider_response`, stale evidence or unavailable status before raising limits. Configure operational alerts through the existing deployment's monitoring; no external alert recipient is added by this implementation.

For an immediate product kill switch, set `AI_ENABLED=false` through deployment configuration and restart/redeploy the backend. Confirm `/api/ai/status` reports unavailable for an authorized test session and deterministic dashboard/advice still work. Remove pilot IDs to withdraw individual live access; disable the business AI module when appropriate. For suspected credential compromise revoke/rotate the key in the provider project as well. An already transmitted request cannot be unsent, and process-local cancellation does not prove the provider did not bill it. Roll back AI code only through a normal reviewed deployment; do not roll back financial records or delete F02 configuration/history.

## Acceptance record

Keep four separate entries in IMPLEMENTATION_STATE: (1) offline/local tests, (2) real-model quality evaluation, (3) real API and billing compatibility, (4) deployed client acceptance. Entries 2–4 remain **not run / not accepted** for the current assignment. Record reviewers, actual environment, model, prompt/schema, dates, measurements and exact delivered commit for any later activation.
