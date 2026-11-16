# Reproduce Stage-C verification on the combined candidate

Use the exact published v3 SHA, pinned dependencies and a clean disposable worktree.
The normal release/security/historical workflows remain unchanged from Stage B.
Supported hosted toolchain: Java 21, Node 22, Python 3.11, pnpm 10.28.2 and declared
Playwright 1.62.0 Chromium. Never target a persistent demo/client database.

```sh
cd backend
./mvnw -B test dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt
cd ..
python scripts/release/postgres_gate.py
cd frontend
pnpm install --frozen-lockfile
pnpm exec playwright install --with-deps chromium
pnpm test
pnpm typecheck
pnpm lint
BACKEND_API_URL=http://127.0.0.1:18080 NEXT_PUBLIC_DATA_MODE=live pnpm build:release
pnpm test:demo-standalone
pnpm test:browser:smoke
pnpm test:browser:product --candidate-v2
pnpm test:browser:provider-disclosure
node tests/run-historical-browser-reconciliation.mjs
cd ..
# Full 26-check real gate + unchanged original two DEF-08 assertions + extra
# capability read-only/financial-before-after verification. All must pass.
node docs/release/candidate-v3-stage-c/run-capability-qa.mjs
# The original Stage-A wrapper also remains executable unchanged:
node docs/release/candidate-v3-stage-a/run-provider-qa.mjs
```

The Stage-C wrapper verifies both exact source heads are ancestors, tracked source
is clean, and original assertion-helper/wrapper files have not changed from Stage B.
It generates a task-owned copy of the existing product gate and delegates to the
**original** Stage-A `providerJourneys` without changing either assertion. Additional
read-only checks run only after both pass. No retries, skip, expected-failure or
assertion relaxation. Actual HEAD and synthetic results are retained under unique
`frontend/test-artifacts/stage-c-capability-<timestamp>/` and ignored scratch paths.

The extra probe observes account/transaction/month/delivery API facts before/after
three capability reads and all four denied write verbs, with a valid CSRF token.
Only request `observationAt` metadata is excluded; it settles the established
projection-on-read before the pair. Its output contains synthetic facts, no tokens.

Run the OCR/Python/HTTPS/backup/configuration/release/security commands from
[Stage A](../candidate-v3-stage-a/README.md) against this checkout. Explicit new
PostgreSQL contracts can also be executed as a clearly labelled subset:

```sh
cd backend
./mvnw -B -Ppostgres-it -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=WhatsAppDisabledOutboundCapabilityPostgreSqlIT,WhatsAppMetaOutboundCapabilityPostgreSqlIT,WhatsAppMockOutboundCapabilityPostgreSqlIT verify
```

These boot actual mock/disabled/Meta clients with the scheduler off. Meta uses
synthetic credentials and an unreachable loopback base URL, with no send operation.
Twelve tests do not claim real-provider acceptance. Full discovery includes 91 IT
classes; production-target PG17/TLS/grants fixtures remain separately unaccepted.

On Windows use `mvnw.cmd`, PowerShell environment assignments and Git MSYS via
`FINSIGHT_TEST_SH` for the shell-boundary configuration tests. Local task used JDK
26 target21, Node26, Python3.12, 768 MiB unit / 512 MiB browser heaps and its own
`D:/FinSightRelease/candidate-v3-stage-c/java-temp`; these are optional host settings,
not hidden prior-worktree dependencies. Run browser harnesses serially and wait
for owned processes/ports to close before starting the next.

No shutdown correction, actual Docker rehearsal, provider activation or deployment
is part of these commands. [Stage-C report](../CANDIDATE_V3_STAGE_C_VERIFICATION.md).
