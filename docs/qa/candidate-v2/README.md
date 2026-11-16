# Reproduce the independent frozen-v2 QA

Application SHA: `71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3`.
The QA branch adds documentation/evidence only. `run-independent-qa.mjs` refuses
different tracked application, original-test or script source. Use a new disposable
worktree and resources; never point these checks at a demo/customer database.

Prerequisites: supported Node 22, JDK 21 (local evidence used supported-engine Node
26 / JDK 26 target 21), pnpm 10.28.2, declared Playwright Chromium, enough memory
for Next and embedded PostgreSQL. Build/source/test outputs must belong to this
checkout; no prior worktree XML/classpath is borrowed.

```powershell
# In the QA-only worktree rooted at frozen v2
cd backend
.\mvnw.cmd -B test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/test-classpath.txt'
cd ../frontend
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
pnpm test
pnpm typecheck
pnpm lint
$env:NEXT_PUBLIC_DATA_MODE='live'
$env:BACKEND_API_URL='http://127.0.0.1:8093'
pnpm build:release
cd ..
$env:FINSIGHT_BROWSER_JAVA_HEAP='512m'
# Windows: if long temporary paths prevent embedded PostgreSQL startup, set
# JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=<a new, short, task-owned temporary directory>.
node docs/qa/candidate-v2/run-independent-qa.mjs
```

On Linux use `./mvnw` and `pnpm exec playwright install --with-deps chromium`;
set environment with `export NAME=value` instead of PowerShell syntax.
The original harness checks 8093/3593/3592 are unused. Each QA invocation creates
a new scratch subdirectory. The normal run executes the existing 26-check real
gate, saves its result, then all **27** independent probes. It exits nonzero while
the real findings remain. See `frontend/test-artifacts/independent-qa-<timestamp>/`.

An explicit focused follow-up is labelled and cannot be mistaken for a whole run:

```powershell
$env:FINSIGHT_QA_CASES='SECURITY-CSRF'
node docs/qa/candidate-v2/run-independent-qa.mjs --smoke
Remove-Item Env:FINSIGHT_QA_CASES
```

Focused runs still create their own accounts, real database and runtime. Empty or
unknown selections cannot produce a false all-green result. `--smoke` runs the
existing 6-check/36-page prerequisite instead of the full financial assertions;
it is not reported as a new 26-check pass.

Focused backend checks actually used:

```powershell
cd backend
.\mvnw.cmd -B -Ppostgres-it '-Dtest=NONE' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=DocumentManualReviewPostgreSqlIT,InventoryModuleEnforcementPostgreSqlIT,BusinessConfigurationPostgreSqlIT,CommercialLifecyclePostgreSqlIT,CommercialDocumentPostgreSqlIT,InventoryLedgerPostgreSqlIT,WorkspacePostgreSqlIT,SpringSessionLifecyclePostgreSqlIT,CommercialRecognitionPostgreSqlIT' verify
.\mvnw.cmd -B '-Dtest=DemoSeederModuleContractTest,InventoryWriteRouteModuleContractTest,CopilotSecurityTests,OpenAiCopilotProviderTests' test
```

Optional heap override used locally:
`-Dfinsight.test.jvm.args=-Xmx512m -XX:+UseSerialGC` (one quoted argument).
Production-target DB role/TLS fixtures were not used or passed.

## Demo-tooling review reproduction

Use `git archive` of exact `7c784c28a332d0703a201bcc4ee9496308f1b3f4`
into a new **ignored** review directory. Verify all archive paths stay in that
directory before extracting. Do not checkout/modify the source branch or run
`start-demo.ps1`, `stop-demo.ps1`, seeder `main`, reset tools or public verifier.

In that archive's backend, `mvnw -B -Dtest=DemoToolingContractTest test` compiles
the reviewed Java scripts and uses **PlanOnly** launcher tests. It starts no demo
stack/tunnel. These nine tests do not prove data preservation or Docker startup.

`DemoSeedQa.java` takes **(seeder source path, source SHA label, report path)**.
Run it with the freshly exported candidate backend/test classpath (use a Java
argument file on Windows). It creates its own `DisposablePostgres`, inserts a
synthetic sentinel, compiles the supplied exact seeder and invokes **only the
Connection constructor + seedAll**, never its fixed-port `main`. It records
sentinel mutation/modules/roles and exercises valid authenticated seeded-business
writes through an ephemeral local Spring runtime. It supplies all datasource/
storage/provider overrides explicitly. Run only the verified frozen/reviewed
script source. Its fixture credentials are read from that source, never printed.

`standalone-assets-probe.mjs` runs from the repository root after the clean build.
It starts only the built standalone frontend on an ephemeral owned loopback port,
requests anonymous landing/static assets, records their statuses, and stops its
own child. It does not copy assets to repair the result or start a tunnel.

The readiness negative probe extracts the **exact** `Test-LocalPort` and
`Wait-LocalPort` function ASTs from the archive's launcher. A task-owned inert
loopback `TcpListener` on port 0 is the only target; both return true without any
HTTP application. The launcher itself is never invoked outside PlanOnly.

## Evidence and limits

`evidence/qa-results.json` is the final complete 27-probe result, not the last
focused run. Selected screenshots and financial before/after JSON contain only
synthetic data. Earlier harness mistakes, transient layout observations and all
raw run logs remain in the local worktree's ignored `tmp/` and test artifacts;
`evidence/verification-manifest.json` identifies the run/report hashes.
No database, key, build output, dependency cache or secret is committed.

See [the definitive report](../CANDIDATE_V2_INDEPENDENT_QA_71ac4e0.md) for the
status matrix, demo/public/pilot decisions and unverified external gates.
