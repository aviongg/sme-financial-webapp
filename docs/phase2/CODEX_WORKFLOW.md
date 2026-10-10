# Repeatable Developer B feature-chat workflow

The repository, not chat memory, is the continuity source. Applies to authorized work for Developer B on `aviongg/sme-financial-webapp`, persistent branch **dev/fatima**. Direct user instructions override older branch instructions in the work-division attachment/README. Do not create a long-lived integration branch, work on main/dev/suleman, rewrite history, force-push or create a main PR without explicit request.

## Start every chat

1. Read root AGENTS.md, IMPLEMENTATION_STATE, FEATURE_REGISTER and the relevant package specification. Read applicable INTEGRATION_CONTRACTS/FINANCIAL_POLICIES and MASTER_IMPLEMENTATION_PLAN; use audit files as dated source maps, not current truth. Read docs/MAIN_INTEGRATION_HANDOFF.md and relevant security/runbook files.
2. Verify repository/checkout before editing: `git remote -v`, `git status --short`, `git branch --show-current`, `git fetch origin`, `git rev-parse HEAD origin/main origin/dev/fatima`, `git rev-list --left-right --count HEAD...origin/dev/fatima`, `git rev-list --left-right --count origin/main...origin/dev/fatima`, `git diff --stat origin/main origin/dev/fatima`. Fetch may require approved Git metadata access in this environment. Do not claim remote state from a stale tracking ref if fetch failed; use authenticated read/ls-remote and report limits.
3. Preserve uncommitted/untracked work and identify ownership. Never `git add .`, stash/delete/reset someone else's changes or delete tmp blindly. If wrong branch/dirty conflict, use a permitted temporary checkout or ask about only the blocking files. If clean and strictly behind dev/fatima, fast-forward normally. If diverged, inspect commits/diffs and reconcile with a non-destructive merge on dev/fatima; do not overwrite/reset/rebase published commits. Do not silently merge A's whole historical branch.
4. Inspect latest implementation and migrations. Confirm the feature is requested in this chat and the producer services actually exist. Verify contracts and policies have **real owner/reviewer approval evidence**, not generated draft status. Refresh estimates/paths/API shapes; finish partial work rather than rebuild it. Reserve migration numbers and shared-file ownership before edits, after checking other developer reservations.
5. State the bounded deliverable and prerequisites. If A dependency is missing, proceed only with independently useful work against an explicitly frozen contract, disclose blocked end-to-end parts, and never add fake production balances/obligations. Required policy approval has no timeout substitute. Synthetic tests/AI experiments must stay isolated and labeled.

## Implement and verify

Implement the requested complete vertical slice: schema/grants/migration upgrade, entities/repos, deterministic service logic/locking/idempotency/audit, typed API, server tenant/role/module scope, frontend forms/navigation/errors/loading/empty states, EN/UR/RTL/mobile, and meaningful tests. Reuse existing identity/session/CSRF, source/score/advice systems. One authoritative calculation per metric. Preserve legacy behavior and no mock fallback in live mode. Planned numbers never post actuals.

Use new `/api/v2`/decimal/source contracts only after C12 freeze; until then they are proposals. Current `/api` numeric payloads must remain compatible. Do not change `health-score-v1` or Zakat policy implicitly. Do not introduce a second tenant/auth/ledger/score authority. Keep diffs scoped; no unrelated refactor/dependency upgrades. New AI must use bounded typed read tools with evidence and explicit missing/restricted outcomes.

Run commands in DEVELOPMENT_SETUP appropriate to changed scope. Capture exact command, source SHA, environment, totals/failures/skips/exclusions and limits. At a wave gate run the shared regression and combined financial journey; a mocked fetch test is not PostgreSQL/browser/production verification. Existing audit findings or test failures need explicit tracking; do not disable assertions, manufacture success, or silently fix unrelated source during a docs-only task.

## Review, commit and push

1. Update register/state/contracts/policies only to the level actually achieved. Obtain required A/B financial cross-review before enabling dependent semantics. User's routine commit/push authorization does not fabricate reviewer signoff; nonfinancial documentation drafts can be committed as drafts.
2. Review `git diff`, `git diff --check`, intended file list, generated files/secrets and migration changes. Stage **explicit intended paths**; inspect `git diff --cached --stat` and `git diff --cached`. Run affected validation after final meaningful edits. For docs-only milestones, source/migration invariants and documentation checks are relevant gates; document unrelated baseline regression failures and do not claim application readiness.
3. Fetch again; verify branch remains dev/fatima, remote origin is authorized repository and the expected remote tip remains an ancestor. Reconcile intervening commits safely and rerun checks affected by merge. Reserve no stale migration number.
4. `git commit -m '<scope>: <concrete result>'`; `git push origin HEAD:refs/heads/dev/fatima` without force. A rejected non-fast-forward push means fetch/inspect/reconcile, not overwrite. Do not push to any other branch.
5. Verify `git ls-remote origin refs/heads/dev/fatima` matches/contains the commit, fetch and confirm ancestry. Report full SHA and usable GitHub commit link. If commit/push/auth fails, explicitly state what remains local. Never silently substitute local work for requested remote completion.
6. Update implementation state with measured prior remote/tested baseline and final publication identity as appropriate. A file cannot embed its own commit hash; use a prior verified hash plus `git log -1 -- docs/phase2/IMPLEMENTATION_STATE.md` for the containing commit, and report the exact final hash outside that commit. Do not create endless metadata-only commits to self-reference.

User approval is required for destructive operations, rewritten history/force push/branch deletion, main or another developer's changes, production/customer-data actions, irreversible financial migration, material methodology change or responsibility reallocation. Complete reviewable safe work first and identify the exact blocking action/rule if approval becomes necessary. Routine authorized dev/fatima commits/pushes do not require asking again.

## Recovery and persistent handoff

If a chat stops, first inspect Git status/log/remote and state rather than restarting implementation. Distinguish committed, staged, unstaged and untracked work. Look for running test/build processes created by the task; recover logs without treating old reports as fresh results. Rerun only incomplete/invalidated gates. Preserve interrupted work; do not delete test data outside a validated disposable test directory. Never terminate unrelated app processes.

Handoff entry format:

```text
Date/time and timezone:
Repository / branch / last fetched origin main and dev/fatima:
Feature / slice / owner / reviewer:
Baseline and delivered commit(s); push and remote verification:
Implemented user journeys / explicitly incomplete journeys:
Changed source/docs/contracts / frozen versions / approval evidence:
Latest migration / reservations / upgrade results:
Tests: exact commands, environment, counts, skips, failures:
Browser/API fixtures versus real DB/deployed/provider evidence:
Financial/source/tenant reconciliation evidence:
Known defects, absent producer services, pending policy decisions:
Local uncommitted/untracked work preserved:
Next concrete authorized step:
```

At session end update current state rather than append conflicting parallel truths. Keep dated evidence/ADRs when needed, link them once, and leave the next feature chat sufficient code/contract/test context without access to this conversation.
