# Core/backend follow-up for Claude

Source baseline: `153e94b214ba24ac1d81a094eff3805334edaad9`.
First published infrastructure candidate: `7f13a0ac6675d33850efe0a1fb55372e3a987706`.
No production recurring/scoring service or financial migration changed in this branch.

## P1: concurrent recurring generation can fail unexpectedly

Clean-clone Ubuntu 22.04 / Temurin Java 21 / real disposable PostgreSQL reproduced this
failure in [PostgreSQL shard 4](https://github.com/aviongg/sme-financial-webapp/actions/runs/43494083018/job/132122491330).
The run executed 126 cases in that shard: zero assertion failures, **one error**, zero skips.
Evidence is retained as the run's `postgres-4` artifact (ID `14070793509`, SHA-256
`e130689960d20398f82e339272ce2329df9e087a4d1086840bc6eb776b8cbc45`).

Failing test: `RecurringTransactionsPostgreSqlIT.testConcurrentGenerationRacingAndIdempotency`,
at `f1.get(...)` / line 551 in the baseline. Two threads invoke
`RecurringRuleService.generateDueOccurrencesForBusiness` simultaneously. One receives:

```text
ObjectOptimisticLockingFailureException:
Query result contains conflicting version of entity already held in persistence context
for entity RecurringRule
```

The existing contract expects the two calls' generated counts to sum to exactly one,
with one occurrence in the database. This failure proves an unexpected racing-call error;
it does **not** establish duplicate postings or customer data loss. Treat it as a release
blocker pending the domain owner's diagnosis, fix and concurrency regression.

Source-based inference to investigate: the service loads managed `RecurringRule` entities
with `findByBusinessIdAndStatusAndNextDueDateLessThanEqual`, then requests a pessimistic
lock for each entity in the same persistence context. A competing transaction can update
the version between that initial load and the locked query. The exact remediation remains
with Claude; this infrastructure task does not replace locking/idempotency logic.

```sh
# backend; isolated fixture only, no external DB/provider credentials needed
./mvnw -B -Ppostgres-it -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
  '-Dit.test=RecurringTransactionsPostgreSqlIT#testConcurrentGenerationRacingAndIdempotency' verify
```

The race can be intermittent. A later green rerun alone does not close this finding.
Do not swallow the exception in the test, disable the class or add a CI retry to manufacture
success. Retain this first failure when validating a reviewed core fix.

## Synthetic score fixture repaired, production semantics unchanged

The read-only test in `FinancialQueryPostgreSqlIT` seeded score JSON with obsolete keys
(`cashFlowStability`, `repaymentCapacity`, `financialTrend`, `recordCompliance`).
`ComponentScoresDto` actually persists `cashflow`, `profitability`, `repayment`, `trend`,
`compliance`. The test passed while facade fallback paths logged repeated deserialization
exceptions, so it did not establish read-only behavior over a valid stored score.

The closure branch aligns **only the synthetic JSON keys**, preserves every seeded numeric
value and composite score, and asserts that the stored score loads through the existing JPA
repository before taking read-only snapshots. Production DTOs, score methodology, financial
logic and migration SQL remain unchanged. This fixture observation is not evidence of
malformed real customer records.

**The valid fixture exposes a second failing invariant.** Targeted local command:

```sh
./mvnw -B -Ppostgres-it -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=FinancialQueryPostgreSqlIT verify
```

Six cases executed: five passed, one failed. In
`testReadOnlyDatabaseValueLevelSnapshotInvariants`, `monthly_records` grows from **1 to 3**
after facade service/HTTP GET calls. The stored-score deserialization warning is gone.
The existing row-count/hash assertions remain active. V52 intentionally materializes derived
monthly snapshots on some reads, so the owner must resolve whether this facade's read-only
contract requires a different read path or whether a reviewed snapshot/source distinction
belongs in the test. This is a P1 release-gate/contract issue pending that decision; the
observation does not prove authoritative ledger writes, double counting or incorrect amounts.
No production fix or assertion waiver is supplied by this branch.

## P1: Linux unit gate exposes a storage-path contract mismatch

The clean-clone [backend-unit job](https://github.com/aviongg/sme-financial-webapp/actions/runs/43494083018/job/132122490702)
executed **1,789 cases, one failure, zero errors, zero skips**. Failure:
`LocalFileSystemStorageServiceTests.pathTraversalAttemptIsRejected(String)[5]`, baseline
line 103. Input `C:\\windows\\system32\\cmd.exe` expects `DocumentStorageException` but gets
`DocumentNotFoundException` on Linux.

Source-based explanation: `Paths.get(...).isAbsolute()` uses the host platform. A Windows
drive path is treated as a literal relative filename on Linux and the lookup remains beneath
the storage root. This is not evidence that an outside file was read. The declared test
contract nevertheless fails on the production OS; decide explicit cross-platform key
validation with the storage/security owner. The test and production storage service are
unchanged here; no platform skip or broadened exception assertion conceals the failure.

```sh
./mvnw -B -Dtest=LocalFileSystemStorageServiceTests test
```

## Separate release blockers

The current dependency inventory has high/critical OSV labels; review
[dependency findings](DEPENDENCY_FINDINGS.md) for exact versions and public advisory records.
The V52 recovery/privilege manifest gaps and real-provider acceptance remain in
[the closure record](PRODUCTION_CLOSURE.md). These findings are not waived by passing CI.
