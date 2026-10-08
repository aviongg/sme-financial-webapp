"""Safe domain errors: never expose provider messages, URLs or credentials."""


class OcrError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 502):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


class TransientProviderError(OcrError):
    def __init__(self):
        super().__init__("provider_unavailable", "OCR provider is temporarily unavailable", 503)


class PermanentProviderError(OcrError):
    def __init__(self, code: str = "provider_rejected"):
        super().__init__(code, "OCR provider could not process this document")


class InvalidDocumentError(OcrError):
    def __init__(self, message: str, status_code: int = 422):
        super().__init__("invalid_document", message, status_code)
