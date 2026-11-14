# Dependency audit findings — 14 November 2026

**Historical original findings, retained as evidence.** Reviewed upgrades and current
zero-match scan are in [the security closure](security/UPGRADE_LOG.md); every original
record remains individually documented in [OSV triage](security/OSV_TRIAGE.md).

Read-only OSV query of exact resolved package versions. Inventory: 439 npm, 44 PyPI and 136 Maven packages. Ten package/version matches, 95 unique advisory records, zero lookup errors. Alias records may describe the same vulnerability; these are database matches, not a reachability/exploitability assessment. The dependency gate correctly exits nonzero; no suppressions or dependency upgrades were introduced.

| Ecosystem | Resolved package | Version | OSV severity labels | Advisory records |
|---|---|---|---|---|
| PyPI | certifi | 2026.7.22 | CRITICAL, UNKNOWN | 2 |
| PyPI | opentelemetry-api | 1.44.0 | MODERATE | 2 |
| PyPI | pillow | 12.3.0 | CRITICAL, HIGH, MODERATE, UNKNOWN | 62 |
| PyPI | pyasn1 | 0.6.4 | HIGH | 4 |
| PyPI | pypdf | 6.19.0 | HIGH, LOW, MODERATE | 18 |
| npm | minimatch | 10.2.6 | MODERATE | 1 |
| npm | next | 16.3.8 | CRITICAL, HIGH | 3 |
| npm | sharp | 0.35.4 | HIGH | 1 |
| npm | source-map-js | 1.2.1 | HIGH | 1 |
| npm | zod | 4.6.2 | HIGH | 1 |

Priority triage: runtime Next.js, OCR image/PDF parsing, CA trust dependencies and high-severity transitive matches. Confirm affected/fixed ranges, withdrawal status, aliases, runtime versus build-only reachability, and vendor remediation before selecting patches. Coordinate backend/OCR production fixes with Claude. Re-lock only reviewed versions and rerun all affected gates; do not weaken the scanner to make this baseline green.

Representative primary database records (the CI artifact contains the full metadata and complete match list):

- certifi 2026.7.22: [GHSA-6q48-6c7w-9gw2](https://osv.dev/vulnerability/GHSA-6q48-6c7w-9gw2).
- pillow 12.3.0: [GHSA-22hg-r758-rrw9](https://osv.dev/vulnerability/GHSA-22hg-r758-rrw9).
- pyasn1 0.6.4: [GHSA-2qm8-x737-gmw5](https://osv.dev/vulnerability/GHSA-2qm8-x737-gmw5).
- pypdf 6.19.0: [GHSA-42gh-23hr-vphw](https://osv.dev/vulnerability/GHSA-42gh-23hr-vphw).
- next 16.3.8: [GHSA-2464-fhr4-w9xw](https://osv.dev/vulnerability/GHSA-2464-fhr4-w9xw).
- sharp 0.35.4: [GHSA-wq5f-xc86-pv6w](https://osv.dev/vulnerability/GHSA-wq5f-xc86-pv6w).
- source-map-js 1.2.1: [GHSA-68fv-2mgg-jv7q](https://osv.dev/vulnerability/GHSA-68fv-2mgg-jv7q).
- zod 4.6.2: [GHSA-mrhx-c7xc-m3w5](https://osv.dev/vulnerability/GHSA-mrhx-c7xc-m3w5).
