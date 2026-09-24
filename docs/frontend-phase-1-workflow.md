# Frontend alignment with Phase 1

Reviewed 23 September 2026 against freshly fetched GitHub branches. Fatima can work on the existing frontend while Suleman completes the remaining backend integration. The first implementation connects profiles and monthly records; the financial dashboard follows once the combined backend is stable.

## Sources and branch state

- Original ownership and shared contracts: `C:/Users/Hp/Desktop/Backend_Work_Division.docx`.
- Remaining-work checklist: `docs/Phase_1_Backend_Remaining_Work.docx`, dated 19 September. Its Fatima tasks are superseded by the committed integration handoff.
- Published Fatima handoff: `backend/docs/phase-1-fatima-integration.md`, commit `3691b2b`.
- `origin/main`: `9becac3`; initial profile/records/database foundation, no implemented frontend.
- `origin/dev/fatima`: `e3ccd65`; Phase 1 advice/bilingual/Zakat work plus isolated OCR commits `cc918f1` and `e3ccd65`.
- `origin/dev/suleman`: `4202dc1`; latest change removes agent instruction files. Its functional backend baseline remains `d571629`.
- Frontend source: Suleman's `348737d`, `a4af8e3`, and `b3301c7`, exported from `4202dc1` into the current `dev/fatima` worktree. Only `frontend/` was imported. No backend branch merge occurred.

The original document assigns Dev A to Suleman and Dev B to Fatima through the team's remaining-work checklist and handoff. Its "frontend phases 1–5" implementation history must not be confused with backend Phase 1/Phase 2 readiness.

## What the backend already does

| Area | Owner | Published state |
| --- | --- | --- |
| Monthly input and validation, Feature 1 | Suleman | Implemented; hardened record validation and nullable COGS are on his branch. POST creates or replaces a user's selected month. |
| Financial scoring, Feature 2 | Suleman | Persists monthly scores with five components and 0–1 completeness; triggered synchronously after record save. Remaining failure/recalculation issues below. |
| Cash-flow history and projection, Features 3/9 | Suleman | Implemented; projection still treats spaced records as consecutive months. |
| Insights, Feature 4 | Fatima | Reads canonical persisted scores, generates English/Urdu insights and exact prior-calendar-month change, with no mock fallback. |
| Recommendations, Feature 5 | Fatima | Uses persisted score components, health band and completeness; refreshes stale advice and prevents duplicate categories. |
| Bilingual backend, Feature 6 | Fatima | Profile language PATCH plus English/Urdu generation, including explicitly requested historical months. |
| Dashboard, Feature 7 | Suleman leads; Fatima assists | Aggregation exists on Suleman's branch; reviewed lock-consistent service and tests are in Fatima's `backend/integration/` handoff. |
| Zakat, Feature 10 | Fatima | Manual and saved-record business preview POST endpoints; accepts declarations and caller-supplied nisab price, with explicit incomplete/unsupported states. |
| OCR, Feature 11 | Fatima | Standalone Python extraction service and Java client/draft processing published separately. This does not establish the document upload/storage/confirmation HTTP lifecycle. |
| Search, Feature 13 | Whoever is free; implemented by Suleman | Backend search exists and is included in the combined integration harness. |

Fatima's advice workflow is refresh on read. Advice defaults to the latest score month; a missing requested score returns an empty list. It preserves canonical score values and uses source fingerprints plus database locks to keep advice current. The frontend must never calculate its own replacement financial score.

The existing handoff records 261 Fatima unit/controller tests, 12 PostgreSQL integration tests, 150 retained Suleman tests and two combined workflow tests passing on 21 September. These are historical recorded results, not a claim that all suites were rerun for this frontend change. Some suite coverage overlaps.

## Remaining backend integration work

Confirmed in the fetched Suleman source:

1. `MonthlyRecordController.triggerRescore` catches scoring exceptions while allowing the record request to succeed. A successful record save does not establish that scoring succeeded.
2. `ScoringService` recalculates the selected month only. Older record corrections can leave later history-dependent scores stale.
3. `TrendProjectionCalculator` uses record positions rather than actual calendar-month offsets, so gaps distort the projection timeline.
4. The actual branch integration remains pending. Preserve Suleman's scoring engine, record hooks, cash-flow/search modules and extra profile fields; retain Fatima's advice, translations, V6, language endpoint and locks. Apply the reviewed dashboard service/test handoff and three profile creation assignments.

Fatima's standalone records service also retains the older required-COGS validator even though V3 makes the database column nullable. The frontend preserves blank COGS as null and shows the backend error. Entering an actual COGS value allows the standalone workflow; the combined backend uses Suleman's nullable-COGS implementation. Do not replace unknown COGS with zero to work around this difference.

Run the committed combination harness after backend changes, inspect its recorded source hashes, and verify fresh/upgrade migration paths using the documented process. Do not repair Flyway history generically or merge older mock advice implementations over the handoff.

## Existing frontend and contract gaps

The imported Next.js/React frontend already has a responsive shell, reusable form components, PKR formatting, English/Urdu layout, dashboard, onboarding, record list/create/edit, health breakdown, search, settings, scan/upload/review and Zakat screens. Before this change all data-bearing routes called the in-memory mock adapter. The HTTP client existed but was unused.

| Subject | Integration requirement |
| --- | --- |
| Identity | Required UUID `userId`, replacing sample `bp-1001`. A browser-held development profile reference is not authentication. |
| Profile | Actual response has `userId`, `businessType`, `languagePreference`, WhatsApp fields and creation time. No persisted business name, industry or currency fields. General profile updates are not supported. |
| Records | POST `/api/records/monthly` upserts by `(userId, month)`; GET list and GET by user/month exist on both branches. No invented PUT route. Suleman's additional ID lookup is not needed by this slice. |
| Score | Backend HTTP fields are camelCase, health bands are canonical strings, completeness is 0–1, null components mean unavailable. Existing presentation models use different names and percentage completeness. Map explicitly at the display boundary. |
| Advice | Backend returns `text`, `category`, priority where applicable, month and source metadata. Do not synthesize the demo's promised impact numbers or action text. |
| Dashboard | Real score/top advice can be absent. Cash-flow and projection shapes differ from the demo; record lists may need their own request. |
| Zakat | Existing mock summary assumes a fixed threshold and classification. Real preview needs price source/date, haul status, asset classifications and eligible liabilities. It requires a form and result-state mapping before connection. |
| Networking | Same-origin `/api` proxy avoids requiring browser CORS access to the Spring process. `BACKEND_API_URL` is a server-side origin. |
| Errors | Show loading, empty, validation, missing record and network failures separately; never substitute sample financial data on failure. |

## Workflow and acceptance gates

### Slice 1 — profile and monthly records

Implemented locally in this change:

- Import the existing frontend and preserve its visual components and demo screens.
- Default to real API mode. Enable the old demo only with `NEXT_PUBLIC_DATA_MODE=demo`, with a visible sample-data banner.
- Create a real profile or verify and reopen an existing UUID. Retain a retry identity so a lost response does not create another profile.
- List, create, reopen and update monthly records through the backend. Use the month in live edit URLs and keep it fixed during edits.
- Keep optional blanks null and explicit zero zero; display backend validation errors without clearing the form.
- Persist English/Urdu preference through the profile language endpoint. Show only saved records and counts; do not claim a score was calculated.
- Hide unconnected features from live navigation and show an unavailable view for direct links. The original demo experience remains selectable for design work.

Acceptance: create/reopen profile, zero values, null optional fields, same-month update retaining ID, reload persistence, server validation, unavailable API, language PATCH, no mock fallback, desktop/mobile and Urdu layout. A required COGS error on standalone Fatima is a known backend difference, not a frontend conversion opportunity.

### Slice 2 — integrated dashboard and advice

After Suleman's fixes and combined verification: adapt the real dashboard DTO, canonical score and nullable components; render persisted insights/recommendations; display recorded month and completeness correctly; refresh after record edits and language changes. Verify empty/no-score state, exact historical month, adjacent-month corrections, gaps, and matching score/advice versions. Keep the backend the sole scoring authority.

### Slice 3 — cash-flow, components and Zakat

Connect history/projection after calendar-gap handling is fixed; use backend values and do not add financial formulas to React. Replace demo component explanations with supported response data. Build the supplementary Zakat declarations and map incomplete, unsupported, warning and final-preview states to the approved backend contract. No fixed market threshold or automatic certification language.

### Slice 4 — search and Phase 2

Connect the already implemented search, accounting for its cached-advice behavior: it does not itself refresh untouched historical advice. Then connect documents only after upload/storage, processing status, review, confirmation and aggregation endpoints are established. Coordinate WhatsApp delivery separately with Suleman. Standalone OCR availability is not evidence that these delivery flows are complete.

## Working agreement

Keep working on `dev/fatima` for this frontend slice. Preserve Suleman's backend ownership. Make each slice reviewable with its endpoint mapping, visible error/empty states and relevant tests. The original document requires the other developer's review before merging changes to shared schema/contracts; this slice consumes existing contracts and makes no backend/schema changes. No merge or publication is performed here.

See `frontend/README.md` for local startup, data modes and the verification results for this implementation.

## Teammate feedback included

The screenshot supplied by Fatima adds five UI requirements. The full WhatsApp opt-in card now toggles the phone field; business-type selection retains its selected style without the tick; the cash-flow card has a chevron disclosure, a 3/6/12-month or all-records selector, a chart and monthly detail table; the monthly-record add button sits above the table while the return link sits in the page header; and each health factor's weight sits below its score/status. Record-page changes appear in live mode. The existing multi-step onboarding, full dashboard and health breakdown changes are available in explicit demo mode until those workflows are connected.
