# Reviewed dependency groups

Baseline: `28bc6ba6fd49e35ebc0626e698a1a803e337c8b5`. No scanner exceptions.
All 95 original advisory records are retained individually in [OSV_TRIAGE.md](OSV_TRIAGE.md)
and its JSON counterpart (exact disjoint ranges, aliases, withdrawal status, reachability,
selected fixed version and compatibility risk). Every original version is genuinely inside
at least one matching range; none of the records was withdrawn. Aliases are not independent
vulnerabilities. Disabled features are documented, not used as suppressions.

## Group 1 — frontend runtime and matching build toolchain

Reviewed minimal selections: Next.js and eslint-config-next **16.4.1**; Next's optional
sharp **0.35.5** / libvips **1.3.4** native family; narrowly targeted vulnerable-version
overrides for source-map-js **1.2.2**, zod **4.6.6**, minimatch **11.0.1**.
React/ReactDOM, TypeScript, Tailwind, ESLint, Playwright and unrelated dependency versions
stay unchanged. Lock comparison includes only those families and minimatch's required
`@isaacs/nonrecursive-glob-match` helper. Peer-resolution text changes are not blanket upgrades.

Minimatch's first fixed release crosses a major; the vendor supports Node 22, and its
existing consumer API and repository glob discovery pass the complete lint/type/build
journey. The app does not import minimatch. Pillow's separate major update is reviewed
below and is not bundled into this group.

Validation after the frozen pnpm 10.28.2 re-lock/install: **460/460 frontend tests**,
TypeScript pass, ESLint **0 errors / 51 warnings**, production webpack build pass, full
real Spring/disposable PostgreSQL/Chromium product browser **20/20** (mock AI).
Strict OSV rerun: **no npm matches**, five not-yet-upgraded Python packages / 88 advisory
records remain, zero lookup errors. This intentionally remains a failing security gate
until the OCR/auth group is completed. No core/backend assertion was changed or retried.

Source review: [Next SVG advisory](https://github.com/advisories/GHSA-2464-fhr4-w9xw),
[Next cache advisory](https://github.com/advisories/GHSA-673q-mjm6-r5xr),
[Next Server Actions advisory](https://github.com/advisories/GHSA-p3m5-59mp-jv5r),
[sharp patch](https://github.com/lovell/sharp/releases/tag/v0.35.5),
[minimatch fix](https://github.com/isaacs/minimatch/releases/tag/v11.0.1).

## Group 2 — OCR parsing, trust and authentication

Pillow **13.0.0**, pypdf **6.21.4**, certifi **2026.10.18**, pyasn1 **0.6.5**,
opentelemetry-api **1.45.2**. No pypdf 7.x or unrelated Python upgrade.
Pillow 13 is the first version outside all matched ranges; its Python 3.10 removal does
not change this service's Python >=3.11 contract. Removed I-mode PNG saving/fromarray
coercion/get_child_images/_show/CMS metadata APIs are unused in app/tests. Existing
Image.open/verify/load and PdfReader(strict=True)/page-count checks remain unchanged.
Unaccepted image plugins may still parse headers before the application allow-list check;
not every named pixel-decoding/GUI/ImageMath exploit is proven reachable here.

Certifi removes compromised trust anchors: real provider TLS compatibility remains an
external acceptance gate; no CA verification bypass is allowed. Google auth and optional
tracing APIs are runtime-installed even when providers are disabled for offline tests.
See [Pillow 13 changes](https://pillow.readthedocs.io/en/stable/releasenotes/13.0.0.html),
[pypdf patch](https://github.com/py-pdf/pypdf/releases/tag/6.21.4) and
[certifi advisory](https://github.com/advisories/GHSA-6q48-6c7w-9gw2).

Validation after the exact constrained install: `pip check` passed; **248 Python tests**
passed; **15 backend OCR HTTPS/configuration contract tests** passed; real offline
browser smoke **6/6** passed. Final strict OSV scan: **620 package versions**
(440 npm / 44 PyPI / 136 Maven), **zero matches and zero lookup errors**.
The scanner has no new exceptions. Security closure does not change the overall
RED release decision: the three preserved backend findings remain unresolved.
