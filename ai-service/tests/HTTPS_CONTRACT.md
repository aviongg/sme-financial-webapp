# Java to FastAPI HTTPS acceptance probe

Create a Python environment with `ai-service/requirements-tested.txt` and
`cryptography` installed. From `backend/`, point `FINSIGHT_OCR_CONTRACT_PYTHON` at
that environment's Python executable and run:

```sh
./mvnw -Dtest=OcrFastApiClosureProbe test
```

On Windows use `mvnw.cmd` and set the environment variable with PowerShell's
`$env:FINSIGHT_OCR_CONTRACT_PYTHON` syntax. The probe is explicitly selected, not
part of the default unit-test or PostgreSQL integration-test name patterns. A
missing interpreter/dependency/server fails the probe; there is no skip/fallback.

Four tests start a real uvicorn HTTPS server, use the actual FastAPI application
factory/authentication/multipart handling/image validation/extraction rules and
Java `OcrIntegrationConfiguration`, `HttpOcrClient`, `JdkOcrHttpTransport` and JSON
codec. Only the external OCR provider is a controlled fixture. They verify
trusted CA + matching secret + valid PNG → parsed six-field draft; wrong secret →
real HTTP 401 before provider execution; wrong CA → failed transport while the
trusted connection still works; and production HTTP URL → rejected configuration.

CA/server private keys, random service key and fixture logs are temporary and
removed with the JUnit temp directory. No secret value appears in arguments or
normal output. `OCR_SERVICE_KEY_FILE` in Java and `OCR_SERVICE_SECRET_FILE` in
Python read the same file. An explicitly configured missing/unreadable/empty
Java key file fails configuration instead of falling back to another secret.

The local connection uses `127.0.0.1`; the disposable certificate includes that
IP, `localhost`, and `ai.finsight.internal`. This proves local HTTPS and the real
cross-language contract, **not** Docker DNS/network routing, production
certificates, Google Vision credentials/accuracy, or the browser document journey.
