# F08 Phase A — Inventory Catalog and Valuation Preview

Status: **PARTIAL_VERIFIED locally (bounded Phase A only)**. It was developed in the isolated worktree `C:/Users/Hp/FYP/wt-f08-inventory-phase-a`, local branch `claude/f08-inventory-phase-a`, based exactly on `dev/fatima` `311de78f1b517aaaf09420914ebe6a32371280e1`. It is **not integrated** into `dev/fatima` and was not pushed. Developer-A review and approval have **not** been given. Full F08 (stock movements, positions, COGS, production) remains DRAFT / BLOCKED_DEPENDENCY.

**F08 Phase A has no financial effect.** It does not record stock, value inventory, compute COGS or post anything to accounting.

## Implemented scope

1. **Inventory item catalog** (persisted identity only): create, list, get, update, archive.
2. **Pure deterministic weighted-average-cost (WAC) calculator** (`WeightedAverageCostCalculator`). It never reads or writes the database.
3. **Stateless valuation preview** endpoint over that calculator: authoritative = false, persisted = false.
4. **Inventory register UI** at `/inventory`.
5. **Valuation sandbox UI** at `/inventory/valuation-preview`.
6. **Catalog audit events.**
7. **EN/UR copy, RTL and mobile layouts.**
8. **Unit, PostgreSQL, frontend and real-browser tests.**

## Migration V37

`V37__inventory_catalog_foundation.sql` is additive. It changes nothing in V1–V17 or V30–V34, and it creates a single table, `inventory_items`.

| Column | Rule |
|---|---|
| `id`, `business_id` | UUID. Tenant FK to `businesses`; `UNIQUE (business_id, id)` |
| `sku` | `VARCHAR(40)`, stored upper-cased and trimmed, pattern `^[A-Z0-9][A-Z0-9._/-]{0,39}$`. `UNIQUE (business_id, sku)`, so SKUs are case-insensitively unique per business |
| `name` (160), `unit` (40), `category` (80, nullable) | Trimmed and non-blank |
| `item_kind` | `RAW_MATERIAL`, `RESALE_GOOD`, `CONSUMABLE` or `FINISHED_GOOD` |
| `reorder_level` | `NUMERIC(15,6)`, nullable, `>= 0`. A reminder threshold only, **not a quantity on hand** |
| `status` | `ACTIVE` or `ARCHIVED` (default `ACTIVE`) |
| `version` | `>= 0`; every update must strictly increase it |
| `created_*` / `updated_*` | Actor and timestamp provenance |

There is **no** quantity, on-hand, value, average-cost, opening-stock or movement column. A test asserts the exact column list, and asserts that `inventory_items` is the only `inventory%` table.

The trigger `enforce_inventory_item_lifecycle` enforces four rules (SQLSTATE `55000` on violation):
- TRUNCATE and DELETE are forbidden; items are archived, never deleted.
- `id`, `business_id`, `created_by` and `created_at` are immutable.
- The version must increase on every update.
- `ARCHIVED` is terminal, and an archived row is immutable.

Grants:
- `finsight_app` has `SELECT, INSERT, UPDATE` only: no DELETE, TRUNCATE, REFERENCES or TRIGGER.
- `finsight_backup` has `SELECT` only.
- `PUBLIC` is revoked.

### Temporary migration history

V35 (F09) and V36 (F06 Phase B) were reserved but not present on the isolated F08 branch, so its local Flyway history was **`V1–V17, V30–V34, V37`**; no placeholder V35/V36 was created and no F09 or F06 migration was copied. In the **local integrated Developer-B candidate** (`claude/dev-b-integration-v35-v37`) V37 follows F09 V35 and F06 V36: fresh history is exactly `V1–V17, V30–V37`, the temporary `version <= 34` filters in the shared history tests are removed, and `InventoryCatalogMigrationPostgreSqlIT` asserts the exact combined history and a populated V34 → V35 → V36 → V37 upgrade.
- `InventoryCatalogMigrationPostgreSqlIT` accepts exactly that local history or the eventual combined `V1–V17, V30–V37`, and requires V37 to be last.
- The shared `CommercialDocumentMigrationPostgreSqlIT` and `MonthlyIntegrityMigrationPostgreSqlIT` assertions now check the history **through V34** only. This stays true in the combined history, and later versions are verified by their own feature tests.

**Final integration must repeat migration verification against the complete `V1–V37` sequence** after F09 (V35) and F06 (V36) land.

## API surface

All endpoints use the existing session, CSRF and active-business architecture (`BusinessAuthorizationService`).
- Tenant and actor come only from the server context.
- Request schemas are closed: unknown fields, duplicate keys, trailing tokens and numeric JSON for decimal strings are all rejected.
- Bodies are bounded to 64 KiB; anything larger returns `413`.
- Responses are `Cache-Control: no-store`.

| Method / path | Permission | Notes |
|---|---|---|
| `GET /api/inventory/items` | `FINANCIAL_DATA_READ` | Filters: `status`, `kind`, `q` (SKU, name or category; LIKE-escaped), `includeArchived`. Ordered by SKU, at most 500 rows |
| `POST /api/inventory/items` | `RECORD_CREATE_UPDATE` | Fields: `sku, name, unit, itemKind, category?, reorderLevel?`. Returns `201` |
| `GET /api/inventory/items/{id}` | `FINANCIAL_DATA_READ` | |
| `PUT /api/inventory/items/{id}` | `RECORD_CREATE_UPDATE` | Same fields plus `expectedVersion` |
| `POST /api/inventory/items/{id}/archive` | `RECORD_CREATE_UPDATE` | Body `{expectedVersion}` |
| `POST /api/inventory/valuation/preview` | `FINANCIAL_DATA_READ` | Stateless; follows the F06 estimate-preview permission precedent |

Errors:
- **`400 inventory_invalid`** with a field-level `errors` map. Codes include:
  - `required`, `sku_invalid`, `text_invalid`, `decimal_invalid`
  - `quantity_invalid`, `value_invalid`, `value_without_quantity`, `value_not_allowed`
  - `insufficient_stock`, `step_count_invalid`, `enum_invalid`, `unknown_field`
- **`404 inventory_item_not_found`.** A missing item and another tenant's item return the same response, so existence never leaks.
- **`409` conflicts:** `duplicate_inventory_sku`, `stale_inventory_item_version`, `invalid_inventory_item_state` (archived).

Every item response carries `catalogOnly: true` and `stockTracking: "NOT_IMPLEMENTED_PHASE_A"`.

No `BusinessPermission` value was added. `ModuleRegistry` still lists `inventory` as a **future** module that depends on future `transactions`; the routes are permission-gated directly, following the F04/F05 precedent.

## Calculator semantics (draft policy `f08-wac-draft-1`)

State is `Q` (quantity) and `V` (total value). The average `V / Q` is derived for display only and is never stored or used as an authority.

| Step | Rule |
|---|---|
| RECEIPT `q > 0`, extended value `c >= 0` | `Q' = Q + q`, `V' = V + c`. A zero-value receipt is accepted and lowers the average |
| ISSUE `0 < q < Q` | `issueValue = round(V × q / Q, 4, HALF_EVEN)`, `Q' = Q − q`, `V' = V − issueValue` |
| ISSUE `q = Q` | `issueValue = V`; the final state is exactly `Q = 0`, `V = 0`, so no residual survives |
| ISSUE `q > Q` | Rejected as `insufficient_stock`; stock never goes negative |
| Zero stock | The average is `null`, not zero |
| Opening `Q = 0` with `V ≠ 0` | Rejected as `value_without_quantity` |
| An issue that carries a value | Rejected as `value_not_allowed`; issues are always valued by the calculator |

Precision and limits:
- **Quantity:** at most 9 integer and 6 fractional digits.
- **Value:** at most 12 integer digits, with fractional digits up to the policy scale (4).
- **Displayed average:** 6 decimal places.
- **Steps:** at most 100.
- Inputs that cannot be represented exactly are **rejected, never rounded**.
- These scales are independent of MonthlyRecord's `NUMERIC(14,2)`, and no shared accounting rounding policy was introduced.

Rounding parameters live in one record, `WacPolicy`. `WacPolicy.DRAFT` is version `f08-wac-draft-1`, value scale 4, `HALF_EVEN`, average scale 6 and `approved = false`. Tests construct alternative policies to show the rounding is configurable.

**Scale-4 / HALF_EVEN is not frozen accounting policy.** Every preview response states this explicitly:
- `mode: "PLANNING_VALUATION_PREVIEW"`, `authoritative: false`, `persisted: false`, `financialEffect: "NONE"`
- `policy.version: "f08-wac-draft-1"` and `policy.status: "DRAFT_NOT_APPROVED_ACCOUNTING_POLICY"`

The response returns every intermediate state, the final state, totals and `valueConserved`, which checks that opening + received − issued = closing.

Verified fixtures:
- 10 units valued 1000, then receive 10 valued 2000, gives Q20 / V3000 / average 150. Issuing 4 is valued at 600, leaving Q16 / V2400.
- Receive 3 for 10.0000, then issue 1 three times. The issue values are 3.3333, 3.3334 (HALF_EVEN tie) and 3.3333, with a final state of 0 / 0 and total issued exactly 10.0000.
- 300 seeded random sequences, each checked for conservation, non-negativity and zero residual at zero stock.

## Stock journal, COGS and financial effect

None are implemented. The calculator and preview **never**:
- create stock movements, positions, receipts, issues, opening stock, adjustments or reversals;
- update an inventory item, MonthlyRecord, score, project, commercial document or estimate;
- create COGS, accounting transactions or `document_financial_contributions`.

The preview service has no repository and no transaction. Item writes change only `inventory_items` and `security_audit_events`; the preview writes no audit row.

## Audit events

`INVENTORY_ITEM_CREATED`, `INVENTORY_ITEM_UPDATED` and `INVENTORY_ITEM_ARCHIVED` were added. Each uses target type `inventory_item` with metadata `sku, itemKind, status, version`. No accounting audit events were invented.

## Frontend

- **`/inventory`:**
  - The register shows only SKU, name, kind, unit, category, reorder level and status. It shows no on-hand stock, stock value, WAC or COGS.
  - A banner reads "Catalog only — no stock is recorded".
  - It offers search, kind filter and an include-archived option, plus inline create/edit with field-level server errors and conflict messages (stale version, duplicate SKU, archived).
  - Archive asks for confirmation.
  - Write controls appear only with `RECORD_CREATE_UPDATE`; read-only roles see a notice instead.
- **`/inventory/valuation-preview`:**
  - A prominent banner reads **"PLANNING PREVIEW — NO STOCK IS RECORDED"** (Urdu: "منصوبہ بندی کا پیش منظر — کوئی اسٹاک درج نہیں ہوتا"), with the draft-policy notice and a "Not authoritative · Not saved · No financial effect" badge.
  - The page takes an opening position and ordered steps (add, reorder, remove), shows each step's resulting quantity, value and average, then the final state and totals.
  - Inputs stay on the page only; nothing is written to local storage.
  - The client verifies that the response is non-authoritative and belongs to the active business.
- **RTL handling:** numeric and SKU inputs are `dir="ltr"` with decimal keyboards, and numbers are isolated LTR spans that align with their labels. The back arrow mirrors in RTL.
- **Navigation:** one Sidebar item, `/inventory` added to the Sidebar active-prefix list, and one mobile quick-action entry. Sidebar and MobileNav were not otherwise redesigned.

## Deferred to later F08 phases (BLOCKED_DEPENDENCY / DRAFT)

- Stock journal or movements, positions, opening stock, receipts, issues, adjustments, reversals and returns.
- COGS, production batches, WIP, BOM, MRP, serial/lot and warehouse/location support.
- Actual project or sample usage.
- F04 linkage, F05 actuals, F06 persisted-estimate linkage and F09 linkage.
- MonthlyRecord or score writes, ledger posting, AR/AP, payments and FX.
- ModuleRegistry `inventory` activation.

Valuation policy (scale, rounding) needs recorded owner/reviewer approval before any stored movement uses it. Financial effect requires Developer-A C01/C02.

## Test evidence (local, 31 October 2026)

| Gate | Result |
|---|---|
| New unit tests: `WeightedAverageCostCalculatorTests` (13), `InventoryRequestAndPreviewTests` (3) | 16 passed |
| New PostgreSQL: `InventoryCatalogMigrationPostgreSqlIT` (2), `InventoryCatalogPostgreSqlIT` (3) | 5 passed |
| Shared migration and role ITs: `CommercialDocumentMigrationPostgreSqlIT` (2), `MonthlyIntegrityMigrationPostgreSqlIT` (2), `DisposableDatabaseRolesIT` (6) | 10 passed |
| Backend default suite | 1,070 run, 0 failures/errors, 1 existing Windows symlink skip |
| Full accepted local PostgreSQL (`*IT` excluding `DatabaseRolePrivilegesPostgreSqlIT` and `PostgreSqlTlsConnectionIT`) | **Complete: all 36 accepted classes, 178 cases, 0 failures/errors/skips** on the isolated F08 commit `cd0f624`. The first single run was stopped by the host for low memory after 31 classes (154 cases, all passing); the remaining five (`Feature8PostgreSqlIT` 6, `PlatformSecurityIT` 6, `ProductionIngressSecurityIT` 5, `SecurityIdentityPostgreSqlIT` 6, `SpringSessionLifecyclePostgreSqlIT` 1 = 24 cases) then passed individually with no source change. The 19 production-target cases stay excluded and unaccepted. In the local integrated Developer-B candidate the accepted suite is re-run in full (see [integrated evidence](verification/2026-10-31-dev-b-integration-v35-v37.json)) |
| Frontend `node --test tests/*.test.mjs` | 125/125 (6 new in `tests/inventory.test.mjs`) |
| TypeScript / ESLint | `tsc --noEmit` clean; ESLint 0 errors, 1 existing `SessionProvider` warning |
| Production build | `next build --webpack` passes, with only the existing middleware-convention warning. The worktree's `node_modules` is a junction, which Turbopack rejects |
| Real browser (`F08InventoryBrowserAcceptance` + `scripts/test_f08_inventory_browser.mjs`) | 15/15 checks: production Next, real Spring/session/CSRF, disposable PostgreSQL, Edge |

The real-browser run covers:
- **Desktop EN:** register, create/edit/archive and the sandbox.
- **Mobile 390px** in EN and in UR/RTL.
- **Roles:** an OWNER and an ACCOUNTANT write. A VIEWER reads the register, has no write controls, is refused (`403`) on writes, and can use the preview.
- **Data integrity:**
  - 16 protected tables are byte-identical in whole rows.
  - Statement probes on 8 tables recorded zero INSERT/UPDATE/DELETE/TRUNCATE.
  - MonthlyRecord and score payloads are unchanged.
- **Runtime:** no page, hydration or CSP errors and no external requests.

The harness defaults to the documented ports (Next 3100, Spring 8080). `F08_BROWSER_WEB_PORT` and `F08_BROWSER_API_PORT` override them when another task owns those ports; this run used 3108 and 8088 because a separate F09 task held 3100.

```powershell
# From backend, after a live production Next build whose /api rewrite targets the chosen Spring port
$env:FINSIGHT_BROWSER_NODE='<node.exe>'; $env:PLAYWRIGHT_MODULE='<playwright module>'; $env:BROWSER_ARTIFACT_DIR='<task-local dir>'
.\mvnw.cmd '-Dtest=F08InventoryBrowserAcceptance' test
```
