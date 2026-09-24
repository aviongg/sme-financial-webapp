"""The fixed wire contract and SDK-independent provider evidence."""
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, field_serializer

DocumentType = Literal["receipt", "invoice", "bank_statement", "unknown"]
Category = Literal["sales", "expense", "purchase", "unknown"]
Confidence = Literal["high", "medium", "low"]


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
