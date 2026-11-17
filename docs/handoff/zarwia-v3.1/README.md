# Zarwia Candidate v3.1: handoff package

**ZARWIA CANDIDATE V3.1: TECHNICAL HANDOFF READY FOR SULEMAN'S DEPLOYMENT.**
This does **not** mean the application has been deployed. No Docker build, container,
server, database or provider was run to produce this package.

| | |
|---|---|
| Application to deploy | `integration/finsight-production-candidate-v3.1` @ **`eb4d7f5bea2392cf07bf5d17f3efa551c333e7b8`** (12/12 hosted checks green) |
| This branch | `handoff/zarwia-v3.1-deployment-20261117`: documentation only, on top of `eb4d7f5`. The application source is byte-identical. |
| Deployment owner | Suleman (Developer A): machine/hosting assessment, deployment, demo recording |

Read in this order:

1. [DEPLOYMENT_AUDIT.md](DEPLOYMENT_AUDIT.md): source audit, plus the configuration gaps **F1–F11**.
2. [DEPLOYMENT_HANDOFF.md](DEPLOYMENT_HANDOFF.md): stack, setup, Path A (local Windows demo) and Path B (Linux private staging), secrets checklist, migration, troubleshooting, backup/rollback, verified vs unverified, GO/NO-GO.
3. [DEMO_WALKTHROUGH.md](DEMO_WALKTHROUGH.md): presentation script with prerequisites and expected results.

The existing release evidence is retained, not duplicated:
[`docs/release/CANDIDATE_V31_PRESENTATION_RELEASE.md`](../../release/CANDIDATE_V31_PRESENTATION_RELEASE.md)
and [`docs/release/candidate-v31/evidence/`](../../release/candidate-v31/evidence/).
Operator runbooks: [`docs/PRODUCTION_RUNBOOK.md`](../../PRODUCTION_RUNBOOK.md),
[`docs/release/STAGING_DEPLOYMENT.md`](../../release/STAGING_DEPLOYMENT.md),
[`docs/release/V52_RECOVERY.md`](../../release/V52_RECOVERY.md),
[`scripts/backup/README.md`](../../../scripts/backup/README.md).

This package supersedes the deployment documents on `handoff/finsight-v3-dev-a-audit-20261117`.
Those documents target frozen v3 and reference a non-existent `scripts/verify-full-demo-lifecycle.mjs`.

No deployment-helper scripts are included. An earlier untested scratch staging script was
reviewed and rejected because its secret-file permission check contradicted the runbook
(audit F11).
