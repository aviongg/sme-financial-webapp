"""POST /extract returns suggestions only; approval is a separate human action."""
import time
from collections.abc import Callable

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Settings
from app.documents import DocumentLoader, SafeDocumentLoader
from app.errors import OcrError
from app.models import ExtractRequest, ExtractResponse
from app.providers.base import OcrProvider
from app.providers.registry import create_provider
from app.service import ExtractionService


def get_extraction_service(request: Request) -> ExtractionService:
    return request.app.state.extraction_service


def create_app(settings: Settings | None = None, *, provider: OcrProvider | None = None,
               loader: DocumentLoader | None = None, sleep: Callable = time.sleep) -> FastAPI:
    config = settings if settings is not None else Settings.from_env()
    api = FastAPI(title="SME OCR draft extraction", version="0.1.0")
    api.state.extraction_service = ExtractionService(
        provider if provider is not None else create_provider(config),
        loader if loader is not None else SafeDocumentLoader(config), config, sleep=sleep,
    )

    @api.exception_handler(RequestValidationError)
    async def validation_error(request: Request, error: RequestValidationError):
        # FastAPI's default detail includes raw input, which can contain signed URLs.
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
    def extract(request: ExtractRequest, service: ExtractionService = Depends(get_extraction_service)):
        return service.extract(request)

    return api


app = create_app()
