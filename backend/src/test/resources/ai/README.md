# Offline AI evaluation v1

`offline-evaluation-v1.json` revision 2 contains 100 distinct-question synthetic contract cases across 20 parameterized families: 30 accepted explanations, 45 rejected provider outputs and 25 unsupported requests. It exercises the actual topic router and explicit-month normalization, deterministic provider selection, strict grounding validator and server renderer. Expected topics are asserted exactly. The five unsupported examples in each family use different request patterns rather than repeating a question with unused numeric variants.

Supported fixtures use topic-specific evidence catalogs: score/component changes across months, cash history, monthly records, forecasts labeled `PLAN`, existing insights, and recommendations with priority and all four existing lifecycle states. They include negative cash, explicit zero, null/missing inputs, sparse history, multiple periods, and exact decimal strings with large values and trailing precision. English and Urdu cases check the selected language and required qualifications. Every supported catalog first passes a valid baseline; the rejection cases then apply different malformed-field, unbound-reference, fabricated-number/prose, unsupported-causality, missing-qualification or duplicate-property mutations. Assertions preserve the entire evidence catalog, including periods, units, status and `SNAPSHOT`/`PLAN`, and bound selected clauses.

These catalogs are hand-authored synthetic inputs to the orchestration contract. They do not calculate expected financial answers or replace authoritative service/DB parity tests. The 100 count is a parameterized case count, not 100 independent financial situations or adversarial strategies. Topic diversity, multiple periods and planned/actual markers are now present in the fixture data; financial meaning, source freshness and authorization still need the separate adapter, service/controller and PostgreSQL suites. A live-model evaluation will require an approved provider runner and expert judgments, not simply replaying these schema-mutation tests.

These are local safety and contract checks. They do not measure the real GPT model's financial explanation quality, API compatibility, billing, privacy acceptance or client deployment readiness. No customer data or paid request is involved. The provider HTTP tests use a controlled loopback server and never follow redirects.

Normal integrated test commands, after the coordinated F02 module policy and shared audit event are present:

```powershell
./mvnw.cmd '-Dtest=Ai*Tests,OpenAiResponsesProviderTests,MvpFinancialEvidenceAdapterTests' test
./mvnw.cmd -Ppostgres-it '-Dit.test=AiEvidencePostgreSqlIT' verify
```

The suite also contains controller/request bounds, session/module revalidation, cache/permission/tenant isolation, disabled/mock/live configuration, quotas/circuit/timeout behavior, authoritative read mapping and recommendation lifecycle checks. `AiEvidencePostgreSqlIT` uses real disposable PostgreSQL, synthetic persisted financial data, real Spring Security/CSRF and simulated AI; it does not imply a live-provider test.

## Final integrated evidence on 15 October 2026

Revision 2's 100 cases and the explicit score-intent routing regression pass in the final default Maven suite (919 run, 918 passed, one existing Windows skip). All four AI PostgreSQL cases and all eight new F02/configuration migration cases pass using real login/JDBC sessions and CSRF cookies. The full local integration gate remains failed at 103/104 because the previously recorded document-recovery assertion R01 recurred; it was not changed or skipped. See [current verification and limits](../../../../../docs/phase2/IMPLEMENTATION_STATE.md) and [machine-readable evidence](../../../../../docs/phase2/verification/2026-10-15-f02-ai.json). No real model call was made.

## Historical local evidence on 12 October 2026

While the other workstreams' compile dependencies were incomplete, the standalone AI core and actual evidence adapter were compiled with Java `--release 21` and the repository's existing exact Maven test dependency classpath. A temporary JUnit Platform launcher ran **122 AI-core tests plus 7 evidence-adapter tests: 129 passed, zero failures/skips**. This includes all 100 evaluation scenarios. Raw compiler arguments and output are untracked under `tmp/f02-ai-launch/ai-core-tests/`.

This isolated run did not replace full Maven verification. The explanation-service/controller tests and four PostgreSQL tests were authored but had not yet run at this checkpoint. The incomplete coordinated dependencies were `configuration.service.ModuleAccessPolicy` and shared `AuditEventType.AI_EXPLANATION_REQUESTED`. No production stubs were introduced to hide those missing dependencies. This historical result covers revision 1, not the strengthened revision 2 dataset. The coordinated final Maven/PostgreSQL results are recorded in `docs/phase2/IMPLEMENTATION_STATE.md`; do not infer a revision 2 pass from this checkpoint.
