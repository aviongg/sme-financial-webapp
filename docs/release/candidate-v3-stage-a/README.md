# Reproduce Candidate-v3 Stage-A validation

Use the dedicated integration branch and its exact recorded SHA in a clean
isolated checkout, synthetic data only. No demo-tooling branch is required.
Supported CI uses Java 21 / Node 22 / Python 3.11 / pnpm 10.28.2 on Ubuntu 22.04.
The complete workflow commands are in `.github/workflows/validation.yml`,
`security.yml` and `historical-browser.yml`; all three run on v3 pushes.

```sh
cd backend
./mvnw -B test test-compile dependency:build-classpath \
  -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt
cd ..
python scripts/release/postgres_gate.py
cd frontend
pnpm install --frozen-lockfile
pnpm exec playwright install --with-deps chromium
pnpm test
pnpm typecheck
pnpm lint
BACKEND_API_URL=http://127.0.0.1:18080 NEXT_PUBLIC_DATA_MODE=live pnpm build:release
pnpm test:browser:smoke
pnpm test:browser:product --candidate-v2
pnpm test:browser:provider-disclosure
node tests/run-historical-browser-reconciliation.mjs
cd ..
# Real Spring/PostgreSQL plus the original independent disclosure assertions.
# Exits nonzero while the empty-history WhatsApp card is undisclosed.
node docs/release/candidate-v3-stage-a/run-provider-qa.mjs
```

The QA wrapper runs the unchanged **full 26-check** product prerequisite, then
**both** original independent DEF-08 assertions; no retry, expected-failure or
green suppression. It records the actual HEAD and refuses dirty application/
backend/script sources. Only the labelled Copilot response uses fault injection.
Generated files, synthetic DB/documents and detailed logs stay in ignored task
scratch/artifact directories. The wrapper requires both exact merged source
commits to be ancestors. It does not import/modify the v2 QA report or need a
previous worktree or report XML. Exit 1 is retained as QA failure, not acceptance.

The historical runner requires its build-time rewrite target 18080 and refuses
occupied fixture/frontend ports. The real product runner owns 8093/3593/3592
and refuses overlap. Do not use a working demo or customer database.

```sh
python -m pip install -c ai-service/requirements-tested.txt \
  -e './ai-service[test]' -r scripts/release/requirements.txt
cd ai-service
python -m pytest -q
cd ..
python -m unittest discover -s scripts/backup -p 'test_*.py' -v
python -m unittest discover -s scripts -p test_production_configuration.py -v
python -m unittest discover -s scripts/release -p 'test_*.py' -v
python scripts/release/review_v52_inventory.py
python scripts/audit_dependencies.py
cd backend
FINSIGHT_OCR_CONTRACT_PYTHON=/absolute/path/to/test-venv/bin/python \
  ./mvnw -B -Dtest=OcrIntegrationConfigurationTests,OcrFastApiClosureProbe test
```

On Windows use `mvnw.cmd`, PowerShell environment assignments and Chromium
without Linux `--with-deps`. The tested POSIX-shell override is
`FINSIGHT_TEST_SH=C:/Program Files/Git/bin/sh.exe`; Windows' default WSL launcher
is not a substitute for Git MSYS path/environment behavior in these tests.
Optional local constraints used in this task: `-Dfinsight.test.jvm.args=-Xmx768m
-XX:+UseSerialGC`, browser heap `FINSIGHT_BROWSER_JAVA_HEAP=512m`, and
`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=D:/FinSightRelease/candidate-v3-stage-a/java-temp`.
Those paths are local evidence, not clean-clone prerequisites. The checked
Playwright cache contains the declared Chromium revision, not tool-owned code.

`evidence/` contains selected synthetic JSON/screenshots and hash/count manifests.
Complete hosted XML/logs are attached to the linked workflows for 90 days;
original local failures and logs remain in ignored `tmp/` and `test-artifacts/`.
Do not confuse passing fixture tests with Docker/PG17/provider/operator acceptance.
See [the Stage-A report](../CANDIDATE_V3_STAGE_A_VERIFICATION.md) for all limits.
