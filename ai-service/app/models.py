"""The fixed wire contract and SDK-independent provider evidence."""
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Literal
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, field_serializer, field_validator

DocumentType = Literal["receipt", "invoice", "bank_statement", "unknown"]
Category = Literal["sales", "expense", "purchase", "unknown"]
Confidence = Literal["high", "medium", "low"]


def validate_url(value: str) -> str:
    """Validate syntax only; trust/size checks belong to the document loader."""
    try:
        parsed = urlsplit(value)
        valid = (
            parsed.scheme in ("http", "https") and parsed.hostname
            and not parsed.username and not parsed.password and not parsed.fragment
            and not any(c.isspace() or ord(c) < 32 for c in value)
            and "\\" not in value and parsed.port in (None, 80, 443)
        )
        if not valid:
            raise ValueError()
        parsed.hostname.encode("idna")
    except (ValueError, UnicodeError):
        raise ValueError("A valid HTTP(S) document URL is required") from None
    return value


class ExtractRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    image_url: str = Field(min_length=1)
    document_type_hint: DocumentType

    _url = field_validator("image_url")(validate_url)


class ExtractResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)
    date: date | None
    amount: Decimal | None
    vendor_or_party: str | None
    category: Category
    confidence: Confidence
    document_type_detected: DocumentType

    @field_serializer("amount", when_used="json")
    def numeric_amount(self, value: Decimal | None) -> float | None:
        # Pydantic otherwise emits Decimal as a string, violating the shared API.
        if value is None:
            return None
        number = float(value)
        if Decimal(str(number)) != value:
            raise ValueError("Amount cannot be represented faithfully as a JSON number")
        return number


@dataclass(frozen=True)
class FieldCandidate:
    field: str
    value: str
    confidence: float | None = None


@dataclass(frozen=True)
class OcrLine:
    text: str
    confidence: float | None = None


@dataclass(frozen=True)
class NormalizedOcrResult:
    text: str
    confidence: float | None = None
    candidates: tuple[FieldCandidate, ...] = ()
    lines: tuple[OcrLine, ...] = ()


@dataclass(frozen=True)
class OcrDocument:
    content: bytes
    mime_type: str
    page_count: int = 1
