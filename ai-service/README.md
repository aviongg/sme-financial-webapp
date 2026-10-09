# FinSight OCR draft extraction

This FastAPI service extracts reviewable suggestions from document bytes sent by
the FinSight backend over authenticated multipart HTTP. Recognized values are
preserved; missing or uncertain values are `null`. Python does not persist or
approve documents, post accounting entries, or generate financial insights. The
backend persists uploads and drafts, preserves the original extraction and review
history, and requires explicit confirmation before applying a contribution once
to monthly financial records. The complete current application is on `main`.

Google Cloud Vision is the first OCR adapter. Google Document AI is **not**
implemented. No free allowance, pricing, monthly quota or billing assumption is
encoded in this service.

## Start independently

Use Python 3.11+ (verified with Python 3.12). From this directory in PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -c requirements-tested.txt -e '.[test]'
.\.venv\Scripts\python.exe -m pytest -q
```

`requirements-tested.txt` captures the dependency versions used for verification;
review and update it when upgrading. Automated tests prohibit the real Google
client, use synthetic fixtures and fake transports, and require no credentials.
Windows event-loop loopback sockets are allowed; external sockets are blocked.

To run with Vision manually, enable the Vision API in your own Google Cloud
project and configure Application Default Credentials (ADC) outside this repo.
Prefer a local ADC login for development or workload identity in deployment.
If a credential file is needed, `GOOGLE_APPLICATION_CREDENTIALS` points to its
external location. Never copy keys into this project or paste them into logs.

```powershell
$env:OCR_PROVIDER = 'google-cloud-vision'
$env:OCR_SERVICE_SECRET_FILE = 'C:/external-secure-location/ocr_service_key'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --no-access-log
```

The service reads process environment variables. It does not automatically load
`.env`. `.env.example` lists current extraction settings. The service key file must
contain the same secret as Java's `OCR_SERVICE_KEY_FILE`. `/extract` requires the
`X-OCR-Service-Key` header (the legacy `X-Internal-Service-Key` header is also
accepted); query-string secrets are rejected. Missing configuration returns 503,
and a missing or incorrect key returns 401. `/health` is an unauthenticated
readiness endpoint. Keep this service on the private backend network. The local
command above uses loopback HTTP; production requires HTTPS and a trusted CA as
configured in [the production runbook](../docs/PRODUCTION_RUNBOOK.md).

The backend reads uploaded document bytes from its persistent storage and sends
them to `/extract`. Calling it with the real adapter makes a billable provider
request under your account's applicable terms. Tests never do this. No AI/LLM
financial API is included.

## Fixed API contract

`POST /extract` accepts `multipart/form-data` with a required `file`, an optional
`document_type_hint` (default `unknown`), and an optional `document_id`. The hint is
one of `receipt`, `invoice`, `bank_statement`, `unknown`; it is advisory and is
not proof of document type. `document_id` does not authorize Python to update a
database. URL/JSON extraction requests from the original isolated implementation
are no longer the active contract.

Every successful response contains exactly these six keys, including nulls:

```json
{
  "date": "2026-09-19",
  "amount": 1250.50,
  "vendor_or_party": null,
  "category": "unknown",
  "confidence": "medium",
  "document_type_detected": "invoice"
}
```

- `date`: ISO calendar date or null.
- `amount`: JSON number or null, never a numeric string or fabricated zero.
- `vendor_or_party`: supported party text or null. No placeholder contacts.
- `category`: `sales`, `expense`, `purchase`, `unknown`.
- `confidence`: `high`, `medium`, `low`, assessing extraction evidence rather than approval.
- `document_type_detected`: `receipt`, `invoice`, `bank_statement`, `unknown`.

Failures use non-2xx status codes and a separate error envelope, for example:

```json
{"error":{"code":"provider_retries_exhausted","message":"OCR processing failed after retries"}}
```

Invalid input/documents use 422, byte/pixel limits 413,
terminal provider failure 502, and exhausted retries/busy/unconfigured service
503. Unexpected processing failures use 500. Errors do not echo request contents,
raw OCR text, provider exception messages, or credentials. An unreadable but
successfully processed document produces a low-confidence draft; it is distinct
from a failed network/provider call.

## Human review behavior

The parser uses explicit labels, independently assessed lines and optional
provider-neutral field candidates. A readable date and total survive a poor
contact-name line. Conflicting or unreadable evidence for one field leaves that
field blank; the program does not choose a random total or an unlabelled name.
An unresolved labelled field makes overall confidence low while retaining other
supported fields. Missing fields otherwise cap confidence at medium.

The review UI can display `null` as a blank or "No contact" without saving that
label as real contact data. A complete high-confidence result is still a draft.
Python never changes a draft to `confirmed`. The integrated Java workflow
separately authorizes and records user confirmation before updating monthly
records and recalculating their score/advice.

Conservative MVP limitations:

- English financial labels and unambiguous ISO/month-name/numeric dates are
  supported. Ambiguous dates and monetary punctuation stay blank. Vision can
  recognize other languages, but Urdu financial parsing is not implemented.
- Names need explicit vendor/merchant/supplier/seller/from labels. Logo/header
  recognition and customer-versus-supplier resolution need more context. The
  existing contract has only one `vendor_or_party` field; it cannot independently
  represent both customer and supplier. Document headings and vendor IDs are not
  contact names. An empty labelled field remains blank, including at end of file.
- Categories stay `unknown` without explicit role/category evidence. A receipt
  or invoice alone does not establish whether the business was buyer or seller.
- Bank statements can be detected, but `amount` stays null until the team defines
  whether it means balance, credits, debits or a transaction. No transaction list
  or line-item schema is introduced.
- The confidence policy is heuristic, not calibrated extraction accuracy. Real
  accuracy across supplier layouts has not been validated with production data.
- No currency field exists in the fixed contract. Amounts are suggestions in the
  source document's units; there is no currency conversion. Python parses with
  Decimal and emits a JSON number. Values that would change through numeric
  serialization are left null for human entry instead of being silently rounded.
  Conflicting currency markers also leave the amount null, even when the numeric
  totals match. Item counts and tax subtotals do not replace the invoice total.

## Accepted files and upload boundary

PNG, JPEG, single-frame WebP, and unencrypted PDFs are accepted. Files are inspected
by content, not filename or claimed MIME type. Images must pass both structural
validation and pixel decoding, so truncated JPEGs fail before the provider call.
The default operational byte limit
is 10 MiB, image limit 25 million pixels, and PDF limit five pages. These are not
pricing allowances. Vision's synchronous file API supports at most five pages;
this adapter rejects incompatible configuration. Another adapter can support a
different page limit without changing shared configuration code.

Oversized PDFs are rejected rather than partially processed. All validated pages
are requested in one synchronous call; missing provider page responses fail the
operation. Multipart bytes are read once, then provider retries reuse those bytes.
Bulk upload is coordinated by the backend; each extraction handles one document.

The active extraction endpoint does not fetch a document URL. It reads the
uploaded file in bounded chunks and validates its content before contacting the
provider. Authentication is checked in the extraction handler before processing;
production edge and service limits remain necessary for multipart request ingress.
The retained URL-related settings in `Settings` are legacy configuration and do
not enable a URL download endpoint.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OCR_PROVIDER` | `google-cloud-vision` | Registered provider name; unknown names fail at startup |
| `OCR_SERVICE_SECRET_FILE` | `/run/secrets/ocr_service_key` | Shared backend/service secret file |
| `OCR_SERVICE_SECRET` | unset | Direct process secret; takes precedence over the file (prefer the file) |
| `INTERNAL_SERVICE_SECRET` | unset | Legacy fallback process secret |
| `OCR_RETRY_DELAYS_SECONDS` | `2,4` | Exactly two delays; defaults implement the team policy |
| `OCR_MAX_DOCUMENT_BYTES` | `10485760` | Maximum uploaded file bytes |
| `OCR_MAX_PDF_PAGES` | `5` | Accepted PDF page limit, within adapter capabilities |
| `OCR_MAX_IMAGE_PIXELS` | `25000000` | Accepted image pixel limit |
| `OCR_MAX_TEXT_CHARS` | `100000` | OCR text evidence limit |
| `OCR_MAX_CONCURRENT_REQUESTS` | `4` | In-process concurrent extraction limit per worker |
| `GOOGLE_CLOUD_VISION_TIMEOUT_SECONDS` | `20` | Per-attempt provider RPC timeout |
| `GOOGLE_CLOUD_VISION_ENDPOINT` | `vision.googleapis.com` | Provider endpoint |
| `GOOGLE_CLOUD_VISION_LANGUAGE_HINTS` | empty | Optional comma-separated recognition language hints |
| `GOOGLE_APPLICATION_CREDENTIALS` | unset | Optional external ADC file path; ADC also supports other methods |

Only transient provider unavailability, deadlines and internal errors are retried:
one initial attempt plus two retries after 2 and 4 seconds by default. SDK retries
are disabled. Invalid arguments, auth/permission errors and quota exhaustion
are terminal; quota exhaustion is not assumed to clear after a short delay.
Transient errors embedded in Vision's response are handled like transport errors.
PDF page errors are classified before checking page metadata, which may be absent
on failure. If any page has a permanent error, the file is not retried merely
because another page has a transient error.
Java must not multiply these attempts by retrying `/extract`.

## Replace the provider

1. Add an adapter under `app/providers/` implementing `OcrProvider.extract`.
   Input is `OcrDocument` (bytes, MIME type, page count); output is
   `NormalizedOcrResult` (text, optional line confidence and field candidates).
2. Keep SDK imports, credentials, error mapping and response conversion inside
   that adapter. Translate temporary errors to `TransientProviderError`, terminal
   errors to `PermanentProviderError`; perform a single attempt in the adapter.
3. Register its factory in `app/providers/registry.py` and add documented settings
   for that provider. Registering `google-document-ai` is a future implementation,
   not an alias or fallback to Vision.
4. Set `OCR_PROVIDER` to the registered name and restart. Controllers, parser,
   success DTOs and Java do not need changing. Tests demonstrate configuration
   switching with two fake factories and application dependency replacement.

`main.py` exposes `create_app(provider=..., sleep=..., inspector=...)`, and FastAPI's
`get_extraction_service` dependency can also be overridden. Tests inject providers,
inspectors, sleeps and SDK client fakes before ADC or network activity occurs.

## Sources and integration

- [Google OCR](https://docs.cloud.google.com/vision/docs/ocr)
- [Vision synchronous PDF annotation](https://docs.cloud.google.com/vision/docs/file-small-batch)
- [Vision Python client retry/timeout parameters](https://docs.cloud.google.com/python/docs/reference/vision/latest/google.cloud.vision_v1.services.image_annotator.ImageAnnotatorClient)
- [Application Default Credentials](https://docs.cloud.google.com/docs/authentication/application-default-credentials)

See [the MVP handoff](../docs/MVP_CORE_REFINEMENT_HANDOFF.md) for current persistent
processing, tenant permissions, provenance and explicit confirmation, and
[the HTTPS contract probe](tests/HTTPS_CONTRACT.md) for real Java/FastAPI TLS and
authentication checks with a controlled provider. The original
[Feature 11 report](../backend/docs/feature-11-ocr.md) is retained as a historical
record; its originally deferred persistence/review work is now integrated.
Production Vision credentials, document accuracy, deployed routing/TLS and
container persistence still require separate environment/provider acceptance.
