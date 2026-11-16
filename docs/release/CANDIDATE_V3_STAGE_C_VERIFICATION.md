# Zarwia Candidate v3 — Stage C outbound-disclosure verification

**DEF-08 is resolved in the tested scenarios. Candidate v3 remains NON-FINAL.**
The independent container-shutdown ownership correction is not integrated or
certified here. No main merge, deployment, public tunnel or real-provider call.

Branch: `integration/finsight-production-candidate-v3`.
Worktree: `C:/Users/Hp/FYP/sme-financial-webapp/tmp/finsight-production-candidate-v3`.
Combined application verification head: **`5cf46349272960baadd261140a61aebc8602a5b6`**.
The containing Stage-C publication adds verification helpers/evidence/docs only;
resolve its full SHA with `git log -1 --format=%H -- docs/release/CANDIDATE_V3_STAGE_C_VERIFICATION.md`.
The final published head's own CI status/URLs are reported in the publication handoff.

## Exact branch provenance and scope

| Operation / input | Exact SHA |
|---|---|
| Verified Stage-B starting point | `ce3d1b06436c478b71f792b700b8e42467ad5e84` |
| Authorized `fix/finsight-v3-whatsapp-outbound-disclosure` head | `7ecf6097779cbb64963baf3f50f6bb724f0dd140` |
| Normal no-ff Stage-C merge | `10e4b71937177b3957f3c333117b06abfec22156` |
| Combined verification/handoff head | `5cf46349272960baadd261140a61aebc8602a5b6` |

The merge has the exact Stage-B and fix heads as parents, **without conflicts**.
The fix branch starts from Stage-A `93c268d`, so preservation of Stage-B-only
work was explicitly checked. Both exact source heads are ancestors; all 14 changed
source-path blobs match the published fix. Stage-B demo implementation, the
historical-driver login synchronization, full product assertions and all release,
security, historical, provider and standalone gates remain intact. Package scripts,
dependency lockfiles and workflow files are unchanged from Stage B.

The only new production behavior is the authorized read-only capability endpoint,
its scheduler-state getter and outbound-card disclosure. Accounting/financial
services, V1–V52 SQL, health/runtime probes, role policy, tenant/session/CSRF
architecture, demo/shutdown scripts and deferred UX items are unchanged. No
unfinished shutdown correction was integrated. Main, Suleman's branch and source
fix branches were not modified. Stage-A/B and independent-v2 reports stay historical;
their earlier failures are not erased or relabelled as Stage-C executions.

## Read-only contract/security review and runtime evidence

`GET /api/whatsapp/delivery-capability` returns exactly:

```json
{"provider":"mock","automaticWeeklySummaries":false}
```

- Provider comes from the actual outbound `WhatsAppClient` bean. Only normalized
  `mock`, `disabled`, `meta` survive the allowlist; absent/unrecognized names become
  `unknown`. It is not inferred from consent, enabled modules, inbound configuration
  or delivery history. The scheduler getter reads the same two existing guards as
  scheduled delivery; it neither triggers a job nor changes scheduling behavior.
- Controller requires existing **FINANCIAL_DATA_READ**, like delivery history.
  Owner/Accountant/Manager/Viewer read it; Salesperson is **403**; anonymous is **401**.
  Active context remains server-resolved; no tenant identifier is accepted by the
  contract. Separate-business actors see the same global environment signal with
  no business/user/phone fields, not another business's data.
- Exactly two response fields: provider classification and scheduler boolean.
  No token, credential, phone, provider URL, model, business/user ID or financial
  amount is returned. Authorization denial occurs before provider lookup.
- All four write verbs (**POST/PUT/PATCH/DELETE**) return **405** with valid CSRF
  in the real runtime; a body requesting `provider=meta` cannot activate anything.
- Independent real-runtime snapshots of accounts, transactions, monthly records
  and delivery rows are **exactly equal before/after** repeated reads and denied
  writes. The existing projection-on-read is settled before the observation pair;
  only request `observationAt` metadata is excluded. Amounts/IDs/counts/balances and
  stored month observations are not relaxed. This is API-fact evidence, not a claim
  that normal JDBC session timestamps never advance.
- Production call graph contains authorization plus two read-only getters; no
  financial service, persistence mutation, send method or scheduler trigger is called.
  Three real Spring/PostgreSQL configurations exercise actual mock/disabled/Meta
  client wiring. The Meta fixture has synthetic credentials and loopback port 9;
  nothing is sent and no paid/live API is contacted.

See [read-only before/after evidence](candidate-v3-stage-c/evidence/capability-readonly.json)
and [contract/role test reports](candidate-v3-stage-c/evidence/local-verification.json).

## DEF-08 closure after integration

| Historical slice | Fresh combined-candidate result |
|---|---|
| Copilot `provider_unavailable` under an `openai` status | **PASS**: original independent regex/assertion unchanged; per-answer evidence fallback is clearly not AI-generated, exact answer values retained |
| WhatsApp delivery rows | **PASS**: mock SENT is explicitly simulated/no transmission; disabled, Meta and unknown recorded providers remain distinct; recorded SENT/FAILED status preserved |
| WhatsApp outbound card with **empty history** | **PASS**: original independent regex/assertion unchanged against real Spring/PostgreSQL; card now explicitly says simulated/no messages transmitted and automatic summaries off |
| EN/UR configuration disclosure | **PASS**: empty-history mock/disabled/configured Meta/unknown cases in both languages; Meta wording explicitly says configuration is not proof of transmission/delivery |
| Missing/failed capability response | **PASS**: UI says unknown/unverified, makes no automatic-sending claim, and does not infer a provider from consent/module/inbound/history |

The real runtime still has **zero delivery rows**, so this is not passing because
an existing history row happened to provide a label. The exact outbound-card text
is retained in [empty-history evidence](candidate-v3-stage-c/evidence/whatsapp-empty-history.json).
Both [original independent results](candidate-v3-stage-c/evidence/independent-provider-results.json)
are PASS; original Stage-A helper and wrapper blobs remain identical to Stage B.
No matching expression, assertion, timeout, skip or retry was changed for closure.

**Closure boundary:** complete historical DEF-08 disclosure finding is resolved
for these tested contracts/UI scenarios. This is not Meta/OpenAI/Google account,
credential, template, quota, delivery or real-provider production acceptance.

## Fresh regression and environment results

Local isolated Windows: JDK 26.0.1 (target 21), Node 26.13.0, **pnpm 10.28.2** frozen
lock, Python 3.12 tested constraints, declared **Playwright 1.62.0 Chromium**.
Fresh embedded PostgreSQL **14.22**, random port/password, restricted roles,
Flyway V1–V52, task-owned document storage and synthetic keys. Real full browser
stack owns loopback 8093/3593/3592, refusing occupied ports. Historical fixtures use
13100/18080; provider UI fixtures use their separate loopback gateway. No existing
demo/client database, port-5433 seeder main, Docker volume or public tunnel is used.
Hosted Ubuntu 22.04 uses Java 21 / Node 22 / Python 3.11 and pinned pnpm.

| Check | Fresh result / qualification |
|---|---|
| Full backend unit | **1,837 cases**, 0 failures/errors locally and hosted. One platform skip per OS: Windows symlink capability; Linux Windows-only ownership guard. Complementary platform tests execute on the other OS |
| Full PostgreSQL | **91 classes / 485 cases**, 0 failures/errors/skips, hosted Linux. Four shards: 133 + 102 + 118 + 132; includes fresh/upgrade/one-shot migrations, finance/lifecycle/security/isolation and new provider-mode contracts |
| Capability PostgreSQL locally | **12/12**, zero skipped: four inherited cases × actual mock/disabled/Meta wiring, permissions/other business/empty history/module-consent independence/no credentials/read-only behavior |
| Frontend | **482/482**, zero skipped; TypeScript, lint and live production build passed. Lint: 0 errors / 51 existing warnings |
| Real full product + original DEF-08 | **26/26 + 2/2 PASS**. 72 responsive EN/UR page loads, financial/stock assertions, manual-review non-posting/provenance, module enforcement and tenant regressions |
| Additional independent capability read-only probe | **PASS**: strict response schema, anonymous denial, four write denials and unchanged financial/month/delivery observations |
| Separate real smoke | **6/6**, 36 responsive page loads, no rebuild storm |
| Historical browser | **5 suites / 86 checks PASS**, including preserved Stage-B login synchronization and strict fallback assertion |
| Expanded provider browser | **10/10 PASS**, including eight EN/UR empty-history capability cases, accurate history rows, exact Copilot strings, disabled/unknown/fallback distinctions and no provider/financial/consent writes |
| Demo contracts | **12/12 locally** within full unit, 5 fresh-seeding PostgreSQL cases within the full hosted suite; no shutdown implementation change |
| Real demo standalone | **PASS**: unpackaged assets rejected; packaged **3 CSS + 21 JS** assets return 200. Generated paths stay within this worktree |
| OCR + explicit HTTPS | **248 Python + 15 Java–FastAPI HTTPS** passed, zero skipped; fake OCR provider and synthetic certificates |
| Backup/configuration/release fixtures | **63 / 17 / 14 PASS**; V52 ACL inventory matches 84 tables. Not an operator restore drill |
| Security | Strict OSV **0 matches / 0 lookup errors**: npm 440, PyPI 44, Maven 136; current-source secret scan passed hosted. No dependency changes or scanner suppressions |

See [local report/log hashes](candidate-v3-stage-c/evidence/local-verification.json),
[full-product results](candidate-v3-stage-c/evidence/real-product-26.json),
[historical results](candidate-v3-stage-c/evidence/historical-results.json) and
[provider-browser results](candidate-v3-stage-c/evidence/provider-browser.json).
Full PostgreSQL was executed hosted; the 12 new provider cases were also executed
locally. Earlier stage/Claude-branch results are not substituted for these runs.

## Exact hosted evidence

Combined verification head **`5cf46349272960baadd261140a61aebc8602a5b6`**:

- [Release validation — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43877475968)
- [Historical browser — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43877475845)
- [Dependency/secret checks — success](https://github.com/aviongg/sme-financial-webapp/actions/runs/43877476080)

[Hosted manifest](candidate-v3-stage-c/evidence/hosted-verification.json) records
actual jobs and parsed log counts. These are integration-branch executions,
not runs of Claude's `7ecf609` branch. The later evidence publication changes no
application/test/gate/shutdown source. Its own exact-head workflow status must
also be checked; final publication SHA/status/URLs are given in the handoff.
Retain 90-day workflow XML/logs/artifacts and archive approved evidence before expiry.

## Non-final conditions and coordination

- **Container-shutdown ownership correction remains pending Claude's separate
  completed branch/review/authorized integration.** Stage-B finding is unchanged:
  fixed-name container stop without runtime ownership proof. This task neither
  modifies those files nor integrates an unfinished correction. Do not infer safe
  operator shutdown from the green component/source gates.
- Full fresh Windows/Docker init → start → app/API/assets/seeded roles → stop
  rehearsal remains **not executed**: Docker CLI/daemon is unavailable here.
  Do not reuse an existing demo/client DB or anyone else's processes. Public
  exposure remains disabled by the preserved demo tooling.
- PostgreSQL 17/TLS/HBA/production grants, Linux image builds/scans, VPS/ingress/
  secrets, real providers/SMTP, paired DB/document/all-historical-key recovery,
  rollback and measured RPO/RTO still need real operator/infrastructure acceptance.
- P0 DEF-01/02/21 closures remain regression-supported. DEF-11 is still the
  unsaved-work UX decision with tenant isolation unchanged. DEF-09 raw activity
  enums and DEF-12/14/15/16/19/20 remain for Suleman's usability/device review.
  **DEF-22 remains retracted**; real inventory writes are `/api/inventory/**`.

Stage C closes the tested disclosure application blocker. It does **not** declare
Candidate v3 final or approve a public synthetic demo/real-client pilot, main merge
or deployment. [Reproduction commands and boundaries](candidate-v3-stage-c/README.md).
