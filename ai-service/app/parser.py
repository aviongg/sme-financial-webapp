"""Conservative extraction from provider-independent OCR evidence.

Missing or ambiguous evidence deliberately produces null/unknown fields. The
result is a draft for human review, never a statement that an invoice is valid.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Callable

from app.models import ExtractResponse, NormalizedOcrResult, OcrLine


MIN_RELIABLE_CONFIDENCE = 0.8
HIGH_CONFIDENCE = 0.9
# A defensive OCR-token bound, not a pricing, transaction, or business limit.
# It also guarantees parsed decimal amounts fit a finite JSON floating number.
MAX_AMOUNT_TEXT_LENGTH = 64
_MONTHS = {
    name: number
    for number, names in enumerate(
        (
            ("jan", "january"), ("feb", "february"), ("mar", "march"),
            ("apr", "april"), ("may",), ("jun", "june"),
            ("jul", "july"), ("aug", "august"), ("sep", "sept", "september"),
            ("oct", "october"), ("nov", "november"), ("dec", "december"),
        ),
        start=1,
    )
    for name in names
}
_NULL_NAMES = {
    "unknown", "no contact", "no vendor", "not available", "not detected",
    "not provided", "not found", "no customer", "unreadable", "n/a", "na",
    "none", "null", "tbd", "-",
}
_DATE_LABEL = r"invoice\s+date|receipt\s+date|transaction\s+date|issue\s+date|issued\s+on|date"
_AMOUNT_LABEL = r"grand\s+total|invoice\s+total|total\s+amount\s+due|total\s+amount|total\s+due|amount\s+due|total"
_PARTY_LABEL = r"(?:vendor|merchant|supplier|seller)(?:\s+name)?|sold\s+by|from"
_CATEGORY_LABEL = r"transaction\s+category|document\s+role"


@dataclass
class _Evidence:
    values: list[object] = field(default_factory=list)
    confidences: list[float | None] = field(default_factory=list)
    uncertain: bool = False

    def add(self, value: object | None, confidence: float | None) -> None:
        if value is None:
            self.uncertain = True
        else:
            self.values.append(value)
            self.confidences.append(confidence)

    @property
    def conflicting(self) -> bool:
        return len({_comparison_key(value) for value in self.values}) > 1

    def resolved(self) -> object | None:
        # One reliable value cannot erase competing or unreadable evidence.
        if self.uncertain or self.conflicting or not self.values:
            return None
        return self.values[0]

    @property
    def strong(self) -> bool:
        return bool(self.confidences) and all(
            confidence is not None and confidence >= HIGH_CONFIDENCE
            for confidence in self.confidences
        )


def _comparison_key(value: object) -> object:
    return value.casefold() if isinstance(value, str) else value


def _reliable(confidence: float | None) -> bool:
    return confidence is None or MIN_RELIABLE_CONFIDENCE <= confidence <= 1


def _combined_confidence(*confidences: float | None) -> float | None:
    for confidence in confidences:
        if not _reliable(confidence):
            return confidence
    if any(confidence is None for confidence in confidences):
        return None
    return min(confidences)


def _labelled_values(lines: list[OcrLine], labels: str) -> list[tuple[str, float | None]]:
    pattern = re.compile(rf"^(?:{labels})(?:\s*[:=]\s*|\s+)(.+)$", re.IGNORECASE)
    standalone = re.compile(rf"^(?:{labels})\s*[:=]?\s*$", re.IGNORECASE)
    values: list[tuple[str, float | None]] = []
    for index, line in enumerate(lines):
        match = pattern.fullmatch(line.text)
        if match:
            values.append((match.group(1).strip(), line.confidence))
        elif standalone.fullmatch(line.text) and index + 1 < len(lines):
            next_line = lines[index + 1]
            values.append((
                next_line.text,
                _combined_confidence(line.confidence, next_line.confidence),
            ))
    return values


def _parse_date(raw: str) -> date | None:
    value = raw.strip()
    # A labelled issue timestamp can still provide an unambiguous date.
    value = re.sub(r"(?:T|\s+)(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?:Z|\s*[AP]M)?$", "", value, flags=re.IGNORECASE)
    iso = re.fullmatch(r"(\d{4})[-/](\d{1,2})[-/](\d{1,2})", value)
    try:
        if iso:
            year, month, day = map(int, iso.groups())
            return date(year, month, day)
        numeric = re.fullmatch(r"(\d{1,2})([-/.])(\d{1,2})\2(\d{4})", value)
        if numeric:
            first, second, year = int(numeric[1]), int(numeric[3]), int(numeric[4])
            if first <= 12 and second <= 12 and first != second:
                return None
            month, day = (second, first) if first > 12 else (first, second)
            return date(year, month, day)
        day_first = re.fullmatch(r"(\d{1,2})\s+([A-Za-z]+)\.?[,]?\s+(\d{4})", value)
        month_first = re.fullmatch(r"([A-Za-z]+)\.?\s+(\d{1,2})(?:,)?\s+(\d{4})", value)
        if day_first:
            day, month_name, year = day_first.groups()
        elif month_first:
            month_name, day, year = month_first.groups()
        else:
            return None
        month = _MONTHS.get(month_name.casefold())
        return date(int(year), month, int(day)) if month else None
    except ValueError:
        return None


def _parse_amount(raw: str) -> Decimal | None:
    value = raw.strip()
    if len(value) > MAX_AMOUNT_TEXT_LENGTH:
        return None
    # Currency markers are allowed only at the edges. Never extract a random
    # number from descriptive text, an invoice ID, or an account number.
    currency = r"(?:PKR|USD|EUR|GBP|INR|CAD|AUD|AED|SAR|RS\.?|[$€£₹])"
    value = re.sub(rf"^{currency}\s*", "", value, flags=re.IGNORECASE)
    value = re.sub(rf"\s*{currency}$", "", value, flags=re.IGNORECASE).strip()
    if value.startswith("(") and value.endswith(")"):
        value = "-" + value[1:-1].strip()
    # A three-digit decimal/grouping suffix is ambiguous (1,234 / 1.234).
    # Comma-grouped amounts are accepted only when a decimal part disambiguates
    # them, or multiple correctly grouped thousands delimiters are present.
    patterns = (
        (r"[+-]?\d+(?:\.\d{1,2})?", lambda text: text),
        (r"[+-]?\d{1,3}(?:,\d{3})+\.\d{1,2}", lambda text: text.replace(",", "")),
        (r"[+-]?\d{1,3}(?:,\d{3}){2,}", lambda text: text.replace(",", "")),
        (r"[+-]?\d{1,3}(?:\.\d{3})+,\d{1,2}", lambda text: text.replace(".", "").replace(",", ".")),
        (r"[+-]?\d+,\d{1,2}", lambda text: text.replace(",", ".")),
        (r"[+-]?\d{1,3}(?: \d{3})+(?:[.,]\d{1,2})?", lambda text: text.replace(" ", "").replace(",", ".")),
    )
    for pattern, normalize in patterns:
        if re.fullmatch(pattern, value):
            try:
                number = Decimal(normalize(value))
                # The fixed wire contract requires a JSON number. Keep the field
                # blank if conversion would change its decimal value.
                if number.is_finite() and Decimal(str(float(number))) == number:
                    return number
                return None
            except InvalidOperation:
                return None
    return None


def _parse_party(raw: str) -> str | None:
    value = " ".join(raw.split()).strip(":; ")
    if (
        not value
        or value.casefold() in _NULL_NAMES
        or len(value) > 200
        or not any(character.isalpha() for character in value)
        or "@" in value
        or re.search(r"https?://|www\.", value, flags=re.IGNORECASE)
        or _parse_date(value) is not None
        or re.match(
            rf"^(?:{_DATE_LABEL}|{_AMOUNT_LABEL}|bill\s+to|ship\s+to|id|no\.?|number|account|address|phone|tel|fax|email|tax\s*id|vat|invoice\s+(?:no|number))\s*[:#]",
            value,
            flags=re.IGNORECASE,
        )
    ):
        return None
    return value


def _parse_category(raw: str) -> str | None:
    value = raw.strip().casefold()
    return value if value in {"sales", "expense", "purchase"} else None


def _parse_document_type(raw: str) -> str | None:
    value = raw.strip().casefold()
    return value if value in {"receipt", "invoice", "bank_statement"} else None


def _text_document_types(lines: list[OcrLine]) -> list[tuple[str, float | None]]:
    types: list[tuple[str, float | None]] = []
    statements: list[OcrLine] = []
    balances: list[OcrLine] = []
    for line in lines:
        if re.search(r"\bbank\s+statement\b", line.text, flags=re.IGNORECASE):
            types.append(("bank_statement", line.confidence))
        if re.search(r"\b(?:account\s+statement|statement\s+of\s+account)\b", line.text, flags=re.IGNORECASE):
            statements.append(line)
        if re.search(r"\b(?:opening|closing)\s+balance\b", line.text, flags=re.IGNORECASE):
            balances.append(line)
        if re.fullmatch(r"(?:tax\s+|cash\s+|payment\s+)?receipt(?:\s*(?:no\.?|number|#|:)\s*.*)?", line.text, flags=re.IGNORECASE):
            types.append(("receipt", line.confidence))
        if re.fullmatch(r"(?:(?:tax|commercial|pro\s*forma)\s+)?invoice", line.text, flags=re.IGNORECASE):
            types.append(("invoice", line.confidence))
    if statements and balances:
        types.append(("bank_statement", _combined_confidence(
            *(line.confidence for line in statements + balances),
        )))
    # An explicit receipt title is stronger than an invoice-reference number.
    if not types:
        for line in lines:
            if re.search(r"^invoice\s*(?:no\.?|number|#|:)\s*\S+", line.text, flags=re.IGNORECASE):
                types.append(("invoice", line.confidence))
    return types


def parse_document(result: NormalizedOcrResult, document_type_hint: str = "unknown") -> ExtractResponse:
    """Build a reviewable draft from evidence; a caller hint is never evidence.

    Providers may use ``document_type_hint`` to select extraction strategies.
    Here, a wrong or unsupported hint cannot fill fields or boost confidence.
    """
    parsers: dict[str, Callable[[str], object | None]] = {
        "date": _parse_date,
        "amount": _parse_amount,
        "vendor_or_party": _parse_party,
        "category": _parse_category,
        "document_type_detected": _parse_document_type,
    }
    evidence = {name: _Evidence() for name in parsers}
    source_lines = result.lines or tuple(
        OcrLine(text=text, confidence=result.confidence)
        for text in result.text.splitlines()
    )
    lines = [
        OcrLine(text=line.text.strip(), confidence=line.confidence)
        for line in source_lines if line.text.strip()
    ]
    for name, labels in (
        ("date", _DATE_LABEL), ("amount", _AMOUNT_LABEL),
        ("vendor_or_party", _PARTY_LABEL), ("category", _CATEGORY_LABEL),
    ):
        for raw, confidence in _labelled_values(lines, labels):
            value = parsers[name](raw) if _reliable(confidence) else None
            evidence[name].add(value, confidence)
    detected_types = _text_document_types(lines)
    for document_type, confidence in detected_types:
        value = document_type if _reliable(confidence) else None
        evidence["document_type_detected"].add(value, confidence)
    for candidate in result.candidates:
        if candidate.field not in parsers:
            continue
        confidence = candidate.confidence if candidate.confidence is not None else result.confidence
        if _reliable(confidence):
            evidence[candidate.field].add(parsers[candidate.field](candidate.value), confidence)

    values = {name: item.resolved() for name, item in evidence.items()}
    document_type = values["document_type_detected"] or "unknown"
    if (
        "bank_statement" in evidence["document_type_detected"].values
        or any(document_type == "bank_statement" for document_type, _ in detected_types)
    ):
        # The shared contract has no definition for a single statement amount.
        values["amount"] = None
    values["document_type_detected"] = document_type
    values["category"] = values["category"] or "unknown"

    supported = sum(values[name] is not None for name in ("date", "amount", "vendor_or_party"))
    conflict = any(item.conflicting or item.uncertain for item in evidence.values())
    complete = supported == 3 and values["category"] != "unknown" and document_type != "unknown"
    if conflict or supported == 0:
        confidence = "low"
    elif complete and all(item.strong for item in evidence.values()):
        confidence = "high"
    else:
        confidence = "medium"
    return ExtractResponse(**values, confidence=confidence)
