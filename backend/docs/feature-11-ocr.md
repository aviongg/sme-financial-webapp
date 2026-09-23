# Feature 11 OCR integration

Feature 11 provides a standalone Python extraction service and an opt-in Java
client under `com.app.sme_health_backend.documents`. Phase 1 was published
separately in commit `3691b2b`; this OCR change publishes the isolated extraction
service and Java integration components. Automatic document processing remains
inactive until its persistence and human-review workflow are connected. No database migration,
existing controller, shared contract, queue, scheduling, camera or bulk-upload
behavior is changed.

## Reviewable drafts

OCR supplies suggestions. Known date/amount/party fields survive missing fields;
unknown values remain null. `vendor_or_party: null` may display as "No contact"
in a future UI, but that label must never become stored contact data.

The existing statuses retain these meanings:

| Transition | Meaning |
| --- | --- |
| `pending` to `processing` | An atomic claim starts extraction |
| `processing` to `extracted` | All fields supported and confidence high; still unapproved |
| `processing` to `needs_review` | Partial, uncertain or empty extraction; supported fields preserved |
| `processing` to `failed` | Terminal HTTP/provider/contract failure, including exhausted Python retries |
| `extracted` or `needs_review` to `confirmed` | Reserved for an explicit human approval workflow, not implemented here |

There is no new `draft` database status: both `extracted` and `needs_review` are
unconfirmed drafts. No successful OCR response automatically approves an invoice,
writes to monthly records, or feeds unconfirmed amounts into insights.

## Java components

All production files are new and isolated under `documents`:

- `ocr/OcrRequest`, `OcrExtraction`: exact snake_case request/response contract,
  with `LocalDate`, `BigDecimal`, explicit enum values, and serialized null keys.
- `ocr/OcrJsonCodec`: rejects missing/extra/duplicate fields, trailing JSON,
  wrong types, invalid dates, string amounts and unknown enum values.
- `ocr/OcrClient`, `HttpOcrClient`: replaceable client making one `/extract` call.
- `ocr/OcrHttpTransport`, `JdkOcrHttpTransport`: injectable transport with bounded
  response consumption, no redirects, and a complete response timeout.
- `ocr/OcrClientSettings`, `OcrIntegrationConfiguration`: opt-in environment-based
  Boot auto-configuration, registered through the feature's `META-INF/spring`
  imports resource. A supplied `OcrClient` bean replaces the default implementation
  regardless of user configuration order; the default is evaluated after user beans.
- `ocr/OcrClientException`: safe failure reasons without leaking source URLs,
  provider responses or secrets.
- `processing/DocumentStatus`, `DocumentDraftStore`, `DocumentDraftProcessor`:
  isolated state-handling logic with an atomic persistence port.

`DocumentDraftStore` deliberately has **no database implementation**. The
processor has **no automatically registered bean or scheduled trigger**. Its
tests use mocked persistence, and existing `uploaded_documents` rows are not
being processed or updated. The FastAPI request has no document ID, so Python
cannot independently mark a stored document failed.

Before activating a live workflow, implement and verify the store against the
existing table, including atomic status checks, ownership, recovery for abandoned
processing, and preservation of human edits. Keep `extracted_data` distinct from
`confirmed_data`. Then separately connect the review/approval experience. Those
integration steps are deferred under the approved isolated scope.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OCR_INTEGRATION_ENABLED` | `false` | Registers the Java client only when true |
| `OCR_SERVICE_URL` | `http://127.0.0.1:8001` | Python base URL; Java appends `/extract` |
| `OCR_CONNECT_TIMEOUT_SECONDS` | `5` | Connection timeout |
| `OCR_REQUEST_TIMEOUT_SECONDS` | `90` | Whole HTTP response deadline |
| `OCR_MAX_RESPONSE_BYTES` | `65536` | Maximum buffered response bytes |

Setting the enable flag creates the client; it does not start document processing.
No Google credentials or SDK dependencies belong in Spring Boot. Provider selection
and credentials live entirely in Python. Adjust the Java deadline if Python's
provider/download timeout or retry delays change; allow time for three provider
attempts, both delays, downloading and parsing. Java makes no automatic retry.

## Tests

Verify the Python service first:

```powershell
# From ai-service, after installing dependencies
.\.venv\Scripts\python.exe -m pytest -q
```

Then from `backend` run the focused Java tests:

```powershell
.\mvnw.cmd '-Dtest=OcrJsonCodecTests,HttpOcrClientTests,OcrIntegrationConfigurationTests,JdkOcrHttpTransportTests,DocumentDraftProcessorTests' test
```

Tests cover exact contract serialization/validation, client replacement and
disabled startup, bounded responses and timeouts, one-request failure handling,
partial drafts, complete-but-unconfirmed drafts, state preservation, and failure
transitions. HTTP/provider/persistence boundaries are mocked; no Google API or
real credentials are used. This verifies the integration components, not a live
database workflow or production extraction accuracy.

Java targets release 21; this workstation also has JDK 26 available. Run the same
suite on the team's deployment JDK before production integration. Python setup,
all provider settings and adapter-switch instructions are in `ai-service/README.md`.

## Publication verification

On 21 September 2026, a clean export of the staged OCR commit passed:

- 159 Python tests, including safe 504 responses for connection/read timeouts.
- 319 backend unit/controller tests, including the 58 OCR component tests.
- 12 isolated PostgreSQL advice/migration regression tests from Phase 1.

There were no failures or skipped tests. The local export and reports are under
`backend/target/ocrpub-84d5d673/` (ignored build output). Tests use fake provider
and HTTP boundaries; no live Google request or production database was used.
This verifies the extraction components and compatibility with Phase 1. The
database-backed OCR processing and human-review workflow described above remain
to be implemented and verified before activation.

## Subsequent hardening verification

The quality review added regressions and fixes for transient PDF page errors
without metadata, permanent failures mixed with transient page failures,
misaligned low-confidence OCR values, truncated JPEGs, empty contact fields,
metadata mistaken for contacts or totals, conflicting currencies, and bank
statement mentions in invoice footers. The shared six-field contract is unchanged.

The Java default client now uses registered Boot auto-configuration so an
application-defined replacement wins in either configuration order. The
integration remains disabled by default.

Verification after these changes:

- 226 Python tests passed, including the HTTP request/adapter/retry/parser path
  with fake SDK responses. Python dependency checks passed.
- 322 backend unit/controller tests passed, including 61 OCR tests and the
  application context test. Java compiled for release 21 using the local JDK 26.
- No failures or skipped tests. Existing test-library deprecation warnings remain.
- No Google API calls, real credentials, or production data were used. PostgreSQL
  integration tests were not rerun because this review changed no schema or
  persistence implementation.

These checks verify deterministic behavior and integration compatibility. Real
document extraction accuracy still needs a controlled manual evaluation, and
database draft persistence and human approval remain deferred as described above.
