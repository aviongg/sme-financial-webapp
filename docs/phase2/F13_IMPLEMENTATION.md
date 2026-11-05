# F13 dashboard workspace — actual implementation

17 October 2026, Developer B. This document records the explicitly requested independent MVP workspace implementation. C03/C10/C12/P09 reciprocal review remains pending; successful local tests do not supply owner/reviewer approval. Read [implementation state](IMPLEMENTATION_STATE.md) for measured verification and publication. The older `/api/v2` design remains a proposal.

## Product and source boundaries

F02 remains the only operating-profile authority. `MANUFACTURING`, `RETAIL_TRADE`, `SERVICES`, and `PROJECT_ORDER` select `manufacturing`, `retail-trade`, `services`, and `project-order`. The workspace changes selection, order, width and explanatory context; it does not change the legacy score category or `health-score-v1`.

Manufacturing emphasizes health, cash, recorded operating costs and evidence; retail/trade emphasizes recorded revenue and cash; services emphasizes revenue/expenses, health components and completeness; project/order emphasizes health, cash, next actions, outlook and evidence. The initial selection deliberately uses existing snapshot facts. Inventory/material valuation, customer receivables, supplier payments, projects/orders, estimates, budgets, funding gaps and other future producers remain unavailable metadata in the widget library, with no fabricated live financial cards.

Personal customization supports add, hide, keyboard/button reorder, compact/wide width, draft preview, save, cancel, reset confirmation and viewing each default preset. Small screens stack the same order. Health remains essential, and permanent links preserve access to health/evidence, recommendations and records. Previewing another preset does not change the business's F02 configuration. Reset uses the current F02 preset.

With no saved records and no available score, the ordinary dashboard shows one localized first-record prompt (or a records link for read-only users). Preset preview and customization still expose the permitted layout. This empty-state presentation does not mutate preferences. Tall evidence/health components default to wide cards; users can choose supported compact widths.

The existing `DashboardService`, `MonthlyRecordService`, score/evidence, deterministic recommendation actions and AI panel remain the authorities. The batch includes the unchanged dashboard DTO and monthly DTOs. Historical cash points reuse `CashFlowChartPointResponse.fromEntity`; calendar-window totals are exact `BigDecimal` presentation aggregates. The frontend formats supplied totals/net and plots supplied revenue/expense values. It does not calculate a new score, profitability, cash forecast or financial balance. Missing months remain absent; nullable optional values remain null. The legacy numeric JSON/PKR display convention is preserved, not migrated to a new monetary contract.

`sourceMode: SNAPSHOT`, score month/computedAt, each record's updatedAt, and each cash window's from/to/count preserve basis and freshness. `generatedAt` identifies response assembly, not a claim that old source data is current. Cash outlook retains the existing explicit projection disclosure. DashboardService retains its existing advice-refresh behavior; this workspace is not a replacement read-only financial-query facade.

## Actual C10 API subset

All routes use the existing cookie session, active business resolution and CSRF transport. No request accepts actor or business identity. Existing `FINANCIAL_DATA_READ` permits personal layout changes; these changes grant no financial write authority. Document-history metadata additionally requires `DOCUMENT_READ`. AI requires the existing business module, finance permission and global AI availability. No role grants are changed.

| Route | Behavior |
|---|---|
| `GET /api/workspace` | Current resolved layout, permitted preferences, catalog and batched authoritative summaries |
| `GET /api/workspace/widgets` | Widget metadata/availability only; no financial queries |
| `GET /api/workspace/presets/{presetKey}` | Default layout preview with the same current user's permissions and data; no preference mutation |
| `PUT /api/workspace/preferences` | Validate and replace currently available personal overrides with optimistic checks |
| `POST /api/workspace/reset` | Clear overrides using current F02 preset, preserving a monotonically increasing version |

Save body: `{version,registryVersion,configurationVersion,widgets:[{key,hidden,order,size}]}`. Reset takes the three version fields. Sizes are `compact` and `wide`, subject to each definition's supported sizes. Unknown fields, duplicate keys/positions, invalid size/order and hiding an essential widget fail predictably. Versions refer to preferences, widget definitions and current F02 configuration. A 409 preserves the editor draft and requires an explicit reload; writes are never automatically replayed.

Response fields: `registryVersion`, `presetVersion`, `presetKey`, `recommendedPresetKey`, `preferenceVersion`, `configurationVersion`, `personalized`, visible `widgets`, editable `preferences`, availability `catalog`, existing `dashboard`, `records`, full recorded `cashHistory`, `cashSummaryByPeriod` (`3`, `6`, `12`, `all`), `sourceMode`, `generatedAt`, `aiStatus`. Catalog entries have `key,module,implemented,required,sizes,available,reason`. They expose no restricted financial values or saved inactive choices.

Resolution order is authenticated permission, implemented/enabled module, allowed registry, current business preset, then personal override. Unknown retired saved keys never render. Available preferences are replacement input, so omitting an available key restores that key's default; the UI sends the full editable list. Disabled/restricted saved overrides remain in storage and are merged back only when allowed again. Stored positions are retained; ties introduced by intervening edits resolve by position then stable widget key, and displayed ordinals normalize. The relative displayed rank can therefore shift after other cards change while a module is disabled. Reset intentionally clears all personal overrides, including inactive ones. No browser request is made per financial card; explanations remain separate explicit opt-in requests.

Errors include `unknown_widget`/`layout_invalid` (400), `widget_forbidden`/`widget_unavailable` (403), and `stale_version`/`stale_configuration`/`stale_registry` (409), using the existing API error envelope. All workspace responses use `Cache-Control: no-store`. Session/business changes reuse the existing generation cancellation and keyed private-tree remount; preferences are never stored as a browser-selected tenant authority.

## Persistence and concurrency

Additive **V31__dashboard_preferences.sql** (authored as V19; renumbered on 30 October 2026 with byte-identical SQL) creates one optional row per `(business_id,user_id)`, both existing identity foreign keys, JSONB overrides, registry version, optimistic version and updated timestamp. Presets and financial data are not copied into database rows. V1–V17 and the business-configuration migration (now V30) remain unchanged. Runtime and backup grants follow the existing role pattern.

Save/reset lock the existing business row before reading configuration/preferences, matching F02's configuration lock. This serializes the first insert, later updates, reset and configuration changes. Reset retains its versioned row to prevent an old version-zero tab becoming valid again. Existing security audit events record the actual actor, business, workspace operation and resulting version; no balances or provider credentials enter the preference audit payload.

## Extending the registry

1. Keep stable widget keys and version definitions/presets when behavior changes. Add the producing module only after its real owner service, permissions and accepted contract exist. Metadata alone never makes a widget implemented.
2. Register a typed backend provider that invokes that authoritative service under the current server-derived scope. Enforce any future row restrictions before aggregation. Never infer AR/AP, inventory, commission, FX, project margin or funding from unrelated snapshot/document values.
3. Batch compatible provider reads. Preserve period, source revision, explicit missing/restricted status, null/zero and actual/plan distinctions. For future independently failing providers, return their unavailable state without fabricating zeros or discarding valid independent summaries.
4. Extend typed response fixtures and the explicit frontend renderer allowlist, EN/UR labels, sizes and availability UI. Do not accept component paths, arbitrary queries, HTML or URLs from preferences.
5. Add authority-parity, tenant/user/row-permission, module off/on, concurrency, no-data and real browser acceptance tests. Record owner/reviewer approval and producer commit before describing the extension as accepted.

The initial batch uses mandatory core finance and fails visibly if that existing service fails. No future placeholder service or database record is created. C11 remains Developer A's future query facade; F13 does not add a competing financial engine or reassign A implementation work. Global AI remains disabled by default; simulated development output stays labeled and no paid API activation is part of this task.
