# F02 and AI-LAUNCH-01 implemented scope

15 October 2026. Developer B implementation under the user's explicit kickoff. The requested F02 and offline AI development journeys are complete and locally verified. This is the actual source contract for the bounded MVP extension; broader `/api/v2` C03/C10/C11/C12 designs remain proposals. Required reciprocal security/financial review is **pending**, not signed by this document. Test and publication evidence lives in [IMPLEMENTATION_STATE](IMPLEMENTATION_STATE.md).

## F02 configuration

`configuration/` in the existing Spring application owns the operating profile, registry, dependency resolver, aggregate repository and module checks. V30 (`V30__business_configuration.sql`, authored as V18 and renumbered on 30 October 2026 with byte-identical SQL) adds `business_configuration` with one business-scoped row, profile, JSON answers/module keys, optimistic version, configured flag and actual actor/timestamp. It backfills existing businesses with compatible capabilities and maps the existing category to an operating profile. New businesses have equivalent read-time defaults until first save; reads do not create configuration rows. V1–V17 and existing financial/profile/score records remain unchanged.

Profiles are `MANUFACTURING`, `RETAIL_TRADE`, `SERVICES`, `PROJECT_ORDER`; they are distinct from the legacy score category. `presetKey` is respectively `manufacturing`, `retail-trade`, `services`, `project-order`. It supplies future F13 composition, without implementing dashboard layout persistence now. Terminology keys are localized by the frontend.

Mandatory `core_finance` remains enabled. Implemented optional capabilities are `documents`, `whatsapp`, `ai`, each depending on core finance. Registry entries for transactions, receivables, invoices, projects, costing, inventory, production, commissions, multi-currency, operations and reports are explicitly unimplemented and cannot be activated. A recommended feature is not necessarily implemented or permitted. Legacy defaults retain existing document and WhatsApp behavior; enabling the business AI capability cannot override global disabled/live/pilot flags.

| Endpoint | Permission / behavior |
|---|---|
| `GET /api/business-configuration` | Existing `FINANCIAL_DATA_READ`; resolves current actor's availability and owner-only management flag |
| `POST /api/business-configuration/preview` | Existing `BUSINESS_SETTINGS_MANAGE` (OWNER), CSRF; validates and resolves without writing |
| `PUT /api/business-configuration` | Same owner permission and CSRF; locks business row, compares version, saves and audits atomically |

Write/preview body example:

```json
{"operatingProfile":"PROJECT_ORDER","operatingAnswers":{"holdsInventory":false,"tracksProjects":true,"sellsOnCredit":false},"requestedModules":["core_finance","documents","ai"],"version":0}
```

The response includes those values plus `modules:[{key,implemented,enabled,required,dependencies,available,reason}]`, `recommendedModules`, `terminology:{sales,work}`, `presetKey`, `registryVersion:"f02-v1"`, `configured`, `canManage`. Reasons are `NOT_IMPLEMENTED`, `PERMISSION_REQUIRED`, `DISABLED` or null. The three operating answers resolve to profile suggestions when omitted (manufacturing/retail holds stock, project/order tracks projects, credit sales false); explicit false overrides suggestions. These are editable setup preferences, not inferred financial facts. Stock/project/credit answers add unavailable inventory/projects/receivables planning recommendations; applying recommendations enables only implemented capabilities. Invalid profile/module/answer/dependency or removal of mandatory core returns 400, permission denial 403, stale version 409. Tenant and actor come solely from the existing authenticated active business context, never request body IDs.

Existing financial reads/writes and role grants remain authoritative. Disabling documents retains GET and filtered `POST /api/documents/query` history but denies upload/retry/review mutation routes. Disabling WhatsApp retains delivery history and saved consent/preferences, blocks settings writes and prevents subsequent scheduled delivery. Requests that already passed their module gate may finish during a concurrent disable; there is no claim of transactional cancellation of already-started document/provider work. Re-enabling restores eligible actions without deleting history. AI checks its module again before delivering a response.

The frontend uses the existing session boundary to remount configuration and assistant state on business/role/auth changes. The post-create `/business-setup` page and Settings share the editor, preview, recommendations, conflict handling and EN/UR copy. Navigation and action controls use the server-resolved capabilities, while historical read pages remain accessible through existing permissions. Configuration failures disable optional actions until refreshed; they do not synthesize new capability grants.

## AI-LAUNCH-01 contract and boundaries

All AI code is B-owned in `ai/`; it is not a financial calculation platform or an alternative to A's future C11. The read-only MVP adapter consumes existing score/monthly/cash services, tenant-scoped advice records and the existing deterministic `EvidenceAdvice` generators. It avoids refresh/save-on-read dashboard/advice entry points. Existing `health-score-v1`, component weights, priority, status and financial calculations are unchanged.

| Endpoint | Behavior |
|---|---|
| `GET /api/ai/status` | Authenticated financial read plus AI capability; returns availability/mode/reason and input bounds, never credentials |
| `POST /api/ai/explain` | Existing auth/CSRF/financial read plus AI capability; bounded typed request, exact source scope, validated output or deterministic fallback |

Request: `{type:"dashboard"|"insight"|"recommendation"|"ask",language:"en"|"ur",question?:string,sourceId?:UUID,month?:YYYY-MM,conversation?:[{role:"user"|"assistant",content:string}]}`. Insight/recommendation explanations require a source ID scoped to the active business; dashboard/Ask cannot choose one. Unknown JSON fields are rejected. Questions/turns are bounded to 1,000 characters and conversation to eight messages. Ask normalizes explicit YYYY-MM, rejects conflicting/multiple months and routes only supported topics. Short follow-ups can inherit the prior user topic; history never becomes authoritative financial evidence. Unsupported questions use a deterministic limitation response without contacting a provider.

Response: `{schemaVersion:"ai-explanation-v1",language,summary,interpretation:[],considerations:[],limitations:[],evidence:[{id,kind,label,value,unit,period,sourceMode}],provenance:{mode,model,promptVersion,evidenceFingerprint,cached},reasonCode}`. Exact numeric evidence is a decimal string or null, never a fabricated zero. Snapshot data and `PLAN` projection evidence are distinct. `evidence-selection-v1` lets a provider select only approved clause/evidence identifiers; backend validation rejects invented facts, amounts, unsupported causes, missing limitations and extra schema fields. Rendering and financial values remain backend-controlled. It is intentionally narrower than open-ended generative financial advice.

Cache isolation includes actor, role, business, language, source/month, topic/evidence revision, prompt/schema and provider/model. Authorization, evidence and capability are checked after provider completion as well. Per-business request/daily quotas, single in-flight work, process-wide concurrency/circuit, bounded memory, timeout and no automatic retry limit exposure. See [activation and operations](AI_ACTIVATION_RUNBOOK.md) for defaults, restart/multi-replica limitations, secret handling, provider retention and kill switch.

Dashboard, each existing insight and recommendation, and `/ask-finsight` expose opt-in explanations. Disabled mode retains deterministic finance. Development simulation requires explicit profile/flags and visible provenance. Failed/invalid provider output is labeled deterministic, never live GPT. Chat is kept in memory only and cleared on navigation/session/business changes; no conversation DB migration is introduced.

## Review and future enrichment

P09/C03 review covers capability defaults, historical reads and existing OWNER-only settings. P12 review covers read-service parity, grounding, prompt/schema, permissions, minimization, retention, live consent and budgets. C12 wire changes remain limited to these actual `/api` endpoints; there is no broad namespace or decimal migration. No financial recognition, valuation, rounding or scoring policy is changed, and no A-side implementation dependency is introduced for this slice.

Later A C01/C02/C04/C06/C08/C09/C11 and B project/costing/inventory/analytics producers may enrich the adapter through reviewed typed read interfaces once implemented. Add tests for source parity, null/zero, period/currency, planned/actual and permission scope at each extension; do not infer balances or incomplete domain coverage from document totals. C11 and all original F01–F13/F12A/F12B ownership stay unchanged.

Offline engineering, real-provider compatibility, real-model quality and client launch are separate milestones. Real OpenAI calls, account billing, provider credentials, production deployment and customer-data evaluation have not been performed under this assignment.
