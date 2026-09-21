# Feature 11 OCR draft extraction

This standalone FastAPI service extracts reviewable suggestions from a single
document URL. Recognized values are preserved; missing or uncertain values are
`null`. It does not approve invoices, post accounting entries, generate insights,
upload files, or persist documents. A user must review the source document and
confirm its fields before later workflows use them as approved data.

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
$env:OCR_ALLOWED_IMAGE_HOSTS = 'storage.googleapis.com'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --no-access-log
```

The service reads process environment variables. It does not automatically load
`.env`. `.env.example` lists all settings. The service is intended for local or
private backend access; authentication/public deployment is outside this isolated
stage. Enabling it on a public interface without an access-control layer is not
part of this implementation.

Use a controlled invoice image or PDF at an allowed HTTPS URL (a signed storage
URL is supported). Calling `/extract` with the real adapter makes a billable
provider request under your account's applicable terms. Tests never do this.

```json
{
  "image_url": "https://your-configured-storage-host.example/invoice.pdf",
  "document_type_hint": "invoice"
}
```

## Fixed API contract

`POST /extract` accepts exactly `image_url` and `document_type_hint`. The hint is
one of `receipt`, `invoice`, `bank_statement`, `unknown`; it is advisory and is
not proof of document type. Despite the original field name, `image_url` may
point to an accepted PDF. No additional request field or endpoint was added.

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

Invalid input/documents use 422, byte/pixel limits 413, download timeout 504,
terminal provider failure 502, and exhausted retries/busy/unconfigured source
503. Unexpected processing failures use 500. Errors do not echo signed URLs,
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

The later UI can display `null` as a blank or "No contact" without saving that
label as real contact data. A complete high-confidence result is still a draft.
No code in this feature changes a draft to `confirmed` or updates monthly records,
insights, or recommendations.

Conservative MVP limitations:

- English financial labels and unambiguous ISO/month-name/numeric dates are
  supported. Ambiguous dates and monetary punctuation stay blank. Vision can
  recognize other languages, but Urdu financial parsing is not implemented.
- Names need explicit vendor/merchant/supplier/seller/from labels. Logo/header
  recognition and customer-versus-supplier resolution need more context. The
  existing contract has only one `vendor_or_party` field; it cannot independently
  represent both customer and supplier.
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

## Accepted files and download boundary

PNG, JPEG, single-frame WebP, and unencrypted PDFs are accepted. Files are inspected
by content, not filename or claimed MIME type. The default operational byte limit
is 10 MiB, image limit 25 million pixels, and PDF limit five pages. These are not
pricing allowances. Vision's synchronous file API supports at most five pages;
this adapter rejects incompatible configuration. Another adapter can support a
different page limit without changing shared configuration code.

Oversized PDFs are rejected rather than partially processed. All validated pages
are requested in one synchronous call; missing provider page responses fail the
operation. The download is performed once, then provider retries reuse the bytes.
PDF support is document parsing, not a camera/bulk upload workflow.

Only exact configured trusted hosts can be fetched. Use dedicated storage hosts
you control; no wildcard host patterns. Redirects, URL credentials, fragments,
nonstandard ports and private/non-global resolved addresses are rejected. The
checked IP address is pinned for the connection while preserving Host and TLS
SNI. Proxy environment variables are ignored. Byte limits apply while streaming;
download elapsed time is checked per received chunk as well as per-operation
HTTP timeouts. OS DNS resolution and local file parsing have no hard preemption
deadline. Prefer HTTPS; HTTP is accepted for compatible public storage setups.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `OCR_PROVIDER` | `google-cloud-vision` | Registered provider name; unknown names fail at startup |
| `OCR_ALLOWED_IMAGE_HOSTS` | empty | Required exact trusted URL hosts, comma-separated |
| `OCR_DOWNLOAD_TIMEOUT_SECONDS` | `10` | HTTP operation timeout and streaming elapsed budget |
| `OCR_RETRY_DELAYS_SECONDS` | `2,4` | Exactly two delays; defaults implement the team policy |
| `OCR_MAX_DOCUMENT_BYTES` | `10485760` | Maximum downloaded file bytes |
| `OCR_MAX_PDF_PAGES` | `5` | Accepted PDF page limit, within adapter capabilities |
| `OCR_MAX_IMAGE_PIXELS` | `25000000` | Accepted image pixel limit |
| `OCR_MAX_URL_LENGTH` | `8192` | URL length limit |
| `OCR_MAX_TEXT_CHARS` | `100000` | OCR text evidence limit |
| `OCR_MAX_CONCURRENT_REQUESTS` | `4` | In-process concurrent extraction limit per worker |
| `GOOGLE_CLOUD_VISION_TIMEOUT_SECONDS` | `20` | Per-attempt provider RPC timeout |
| `GOOGLE_CLOUD_VISION_ENDPOINT` | `vision.googleapis.com` | Provider endpoint |
| `GOOGLE_CLOUD_VISION_LANGUAGE_HINTS` | empty | Optional comma-separated recognition language hints |
| `GOOGLE_APPLICATION_CREDENTIALS` | unset | Optional external ADC file path; ADC also supports other methods |

Only transient provider unavailability, deadlines and internal errors are retried:
one initial attempt plus two retries after 2 and 4 seconds by default. SDK retries
are disabled. Invalid arguments/URLs, auth/permission errors and quota exhaustion
are terminal; quota exhaustion is not assumed to clear after a short delay.
Transient errors embedded in Vision's response are handled like transport errors.
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

`main.py` exposes `create_app(provider=..., loader=..., sleep=...)`, and FastAPI's
`get_extraction_service` dependency can also be overridden. Tests inject providers,
loaders, sleeps and SDK client fakes before ADC or network activity occurs.

## Sources and integration

- [Google OCR](https://docs.cloud.google.com/vision/docs/ocr)
- [Vision synchronous PDF annotation](https://docs.cloud.google.com/vision/docs/file-small-batch)
- [Vision Python client retry/timeout parameters](https://docs.cloud.google.com/python/docs/reference/vision/latest/google.cloud.vision_v1.services.image_annotator.ImageAnnotatorClient)
- [Application Default Credentials](https://docs.cloud.google.com/docs/authentication/application-default-credentials)

See `backend/docs/feature-11-ocr.md` for the inactive Java client and the remaining
persistence/human-approval integration boundary.
