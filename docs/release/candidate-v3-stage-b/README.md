# Reproduce Stage-B component verification

Use the exact published v3 SHA in an isolated checkout, synthetic resources only.
Run the complete normal release/security/historical workflows; the v3 CI now
includes `pnpm test:demo-standalone` alongside provider, smoke and full product.
Use declared Java 21, Node 22, Python 3.11, pnpm 10.28.2 and Playwright 1.62.0.

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
# Expected aggregate failure while the original WhatsApp empty-card defect exists.
node docs/release/candidate-v3-stage-a/run-provider-qa.mjs
```

Run the Python/OCR/HTTPS/backup/configuration/release/security commands from
[Stage A](../candidate-v3-stage-a/README.md) on the current Stage-B sources.
On Windows use `mvnw.cmd`, PowerShell assignments and the explicit Git MSYS shell
for configuration tests. This host used 768 MiB unit and 512 MiB browser heaps,
with its own `D:/FinSightRelease/candidate-v3-stage-b/java-temp`; these are optional
task-local settings, not another clone's prerequisite. Run browser suites serially
and let each release its owned ports before starting the next.

`SeededLoginProbe.java` takes repository root, tested SHA and an output JSON path.
Launch it with Java's test classpath: `backend/target/test-classes`,
`backend/target/classes` and this checkout's exported `test-classpath.txt` using
the platform path separator (a Java argument file avoids Windows command limits).
It creates/marks only its own embedded PostgreSQL, executes the actual seeder's
fresh preflight and Connection API, then ephemeral Spring and all five logins.
It never invokes the fixed-port seeder main, uses Docker, or accesses port 5433.
Passwords/key are read in memory from the public synthetic fixture source and
never included in JSON. It explicitly chooses an active business and asserts
Salesperson's general financial/configuration read remains forbidden.

Windows process probes, run only from a disposable task worktree:

```powershell
powershell -NoProfile -File docs/release/candidate-v3-stage-b/Probe-OwnedProcess.ps1 `
  -RepoRoot (Get-Location).Path -OutputDir (Join-Path (Get-Location) 'tmp/stage-b/owned-process')
powershell -NoProfile -File docs/release/candidate-v3-stage-b/Probe-ContainerShutdown.ps1 `
  -RepoRoot (Get-Location).Path -OutputFile (Join-Path (Get-Location) 'tmp/stage-b/container-shutdown.json')
```

The first creates a hidden owned Node listener and stops only its verified
PID/start-time identity. The second refuses any runtime manifest and substitutes
a **Docker function fixture**; it demonstrates the container-name gap without
invoking the real CLI or stopping a real container/process. Probe completion is
not acceptance of the unsafe behavior. Do not run the actual stop script against
an unverified existing demo environment.

The full Windows/Docker `init-demo -CreateFreshDisposableDemo → start-demo →
HTTP/API/assets/seeded login → stop-demo` drill was not executable on this host
because Docker CLI/daemon is absent. Linux component CI is not that drill.
See [the Stage-B report](../CANDIDATE_V3_STAGE_B_VERIFICATION.md) for prerequisites,
the baked backend rewrite target and unresolved conditions. Public tunnels remain
disabled. Evidence contains synthetic manifests/statuses only; full local logs
and generated scratch remain ignored. No database/key/build artifacts are committed.
