# Canonical FinSight application branch

**`main` contains the full current FinSight MVP and is the canonical baseline for future development.** Check out this one branch for the matched frontend, backend, security, OCR service, migrations, deployment configuration and MVP refinements. No historical branches need to be combined.

The [main integration handoff](MAIN_INTEGRATION_HANDOFF.md) records the exact PR/merge status, source hashes and validation. This branch policy takes effect when that integration PR is merged; a pre-merge view of this file is the proposed main documentation.

## Verified integration source

On 8 October 2026, `git fetch --all --prune` confirmed:

| Ref | SHA |
| --- | --- |
| Previous `origin/main` | `9becac35dd75539218470a6a4d1b6720f212a6f0` |
| `origin/codex/mvp-core-refinement` | `da62a4164f3ed90a9d1a75f6a3e75da5c881c4a1` |

`git merge-base --is-ancestor origin/main origin/codex/mvp-core-refinement` succeeded. The left/right count was **0 / 73**: main had no unique commits to reconcile. The integration branch starts at the consolidated refinement source, which already contains secure frontend/backend integration and security-production closure. Older branches were not merged individually.

The original frontend consolidation comparison remains available in [the 28 September snapshot](CONSOLIDATED_APP_BRANCH_2026-09-28.md). It established that Fatima's earlier frontend paths were already present/adapted, even though every older frontend commit was not an ancestor. Its branch selection instructions and feature-gap table are historical; later security closure and MVP refinement supersede them.

## Team workflow

With local work safely committed or otherwise preserved:

```sh
git fetch origin
git switch main
git pull --ff-only
git switch -c feature/<future-feature>
```

Follow [frontend setup](../frontend/README.md), [OCR setup](../ai-service/README.md), and the [production runbook](PRODUCTION_RUNBOOK.md). The authoritative database migrations are `backend/src/main/resources/db/migration/V1…V17`; run the migration role separately from the restricted runtime.

**ALL OLD BRANCHES WERE LEFT UNTOUCHED.** `dev/fatima`, `dev/suleman`, `codex/phase1-frontend`, `codex/fatima-secure-frontend`, `codex/security-production-closure` and `codex/mvp-core-refinement` still exist as historical/development references. This task does not delete, rename, retag, rewrite, force-push or add commits to them. Existing developer checkouts and untracked files are preserved.

## Scope and acceptance

The [current progress report](../PROJECT_PROGRESS.md) lists all implemented modules and limitations. The score methodology and formulas are preserved; the app still uses deterministic evidence-based advice. **NO AI/LLM FINANCIAL API WAS ADDED. NO PHASE 2 TRANSACTION/ACCOUNTING MODULE WAS ADDED.**

Code integration does not establish production/provider acceptance. Production containers, PostgreSQL 17 TLS/HBA/privileges, live browser/ingress behavior, persistent storage, encrypted recovery and actual Google Vision, SMTP and WhatsApp delivery require the environment-specific gates recorded in the handoff. Historical audits are retained; their findings must be read alongside subsequent fixes and verification.