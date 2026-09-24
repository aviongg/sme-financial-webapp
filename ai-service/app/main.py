"""POST /extract returns suggestions only; approval is a separate human action."""
import hmac
import time
from collections.abc import Callable
from typing import Any

from fastapi import Depends, FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings
from app.documents import inspect_document
from app.errors import OcrError
from app.models import DocumentType, ExtractResponse
from app.providers.base import OcrProvider
from app.providers.registry import create_provider
from app.service import ExtractionService


def get_extraction_service(request: Request) -> ExtractionService:
    return request.app.state.extraction_service


def verify_ocr_service_key(request: Request) -> None:
    # Reject secret if passed in query parameters
    query_keys = {k.lower() for k in request.query_params.keys()}
    if query_keys.intersection({"key", "secret", "x-ocr-service-key", "x-internal-service-key"}):
        raise OcrError("unauthorized", "Service key in query parameters is forbidden", 401)

    settings: Settings = request.app.state.settings
    expected = settings.ocr_service_key or settings.internal_service_key
    if not expected:
        raise OcrError("auth_unconfigured", "OCR service secret is not configured", 503)

    key = request.headers.get("x-ocr-service-key") or request.headers.get("x-internal-service-key")
    if not key:
        raise OcrError("unauthorized", "Missing OCR service secret", 401)

    if not hmac.compare_digest(key, expected):
        raise OcrError("unauthorized", "Invalid OCR service secret", 401)


def create_app(settings: Settings | None = None, *, provider: OcrProvider | None = None,
               loader: Any = None, sleep: Callable = time.sleep,
               inspector: Callable | None = None) -> FastAPI:
    config = settings if settings is not None else Settings.from_env()
    api = FastAPI(title="SME OCR draft extraction", version="0.1.0")
    api.state.settings = config
    service_inspector = inspector if inspector is not None else inspect_document
    api.state.extraction_service = ExtractionService(
        provider if provider is not None else create_provider(config),
        config, sleep=sleep, inspector=service_inspector,
    )

    @api.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError):
        return JSONResponse(status_code=422, content={"error": {
            "code": "invalid_request", "message": "Request does not match the OCR contract",
        }})

    @api.exception_handler(OcrError)
    async def ocr_error(request: Request, error: OcrError):
        return JSONResponse(status_code=error.status_code, content={"error": {
            "code": error.code, "message": error.message,
        }})

    @api.exception_handler(Exception)
    async def unexpected_error(request: Request, error: Exception):
        return JSONResponse(status_code=500, content={"error": {
            "code": "processing_failed", "message": "Document processing failed",
        }})

    @api.post("/extract", response_model=ExtractResponse)
    async def extract(
        request: Request,
        file: UploadFile = File(...),
        document_type_hint: DocumentType = Form("unknown"),
        document_id: str | None = Form(None),
        service: ExtractionService = Depends(get_extraction_service),
    ):
        verify_ocr_service_key(request)
        try:
            max_allowed = request.app.state.settings.max_document_bytes
            chunk_size = 64 * 1024
            buffer = bytearray()
            while True:
                remaining = (max_allowed + 1) - len(buffer)
                read_len = min(chunk_size, remaining)
                chunk = await file.read(read_len)
                if not chunk:
                    break
                buffer.extend(chunk)
                if len(buffer) > max_allowed:
                    raise OcrError("payload_too_large", f"Document exceeds maximum allowed size of {max_allowed} bytes", 413)

            content = bytes(buffer)
            return service.extract(content, document_type_hint=document_type_hint, document_id=document_id)
        finally:
            await file.close()

    return api


app = create_app()
