package com.app.sme_health_backend.commercial.lifecycle;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * F04 lifecycle API: composite lifecycle view, credit/debit notes, adjustment cancellation and void of a
 * recognized document. Financial corrections require FINANCIAL_TRANSACTION_POST, the same permission that
 * recognizes and settles documents; the read view requires FINANCIAL_DATA_READ.
 */
@RestController
@RequestMapping("/api/commercial")
public class CommercialLifecycleController {
    private final BusinessAuthorizationService authorization;
    private final CommercialLifecycleService lifecycle;

    public CommercialLifecycleController(BusinessAuthorizationService authorization, CommercialLifecycleService lifecycle) {
        this.authorization = authorization;
        this.lifecycle = lifecycle;
    }

    public record AdjustmentBody(String amount, String adjustmentDate, String reason) { }

    public record ReasonBody(String reason) { }

    public record EvidenceBody(UUID uploadedDocumentId) { }

    @PostMapping("/documents/{id}/evidence")
    public ResponseEntity<?> linkEvidence(@PathVariable("id") UUID id, @RequestBody EvidenceBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(lifecycle.linkEvidence(ctx, id, body == null ? null : body.uploadedDocumentId()));
    }

    @PostMapping("/documents/{id}/evidence/{linkId}/unlink")
    public ResponseEntity<?> unlinkEvidence(@PathVariable("id") UUID id, @PathVariable("linkId") UUID linkId,
                                            @RequestBody ReasonBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.RECORD_CREATE_UPDATE);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(lifecycle.unlinkEvidence(ctx, id, linkId, body == null ? null : body.reason()));
    }

    @GetMapping("/documents/{id}/lifecycle")
    public ResponseEntity<?> lifecycle(@PathVariable("id") UUID id, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(lifecycle.lifecycle(ctx, id));
    }

    @PostMapping("/documents/{id}/adjustments")
    public ResponseEntity<?> adjust(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @PathVariable("id") UUID id, @RequestBody AdjustmentBody body,
                                    HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        if (body == null) {
            throw DomainException.invalid("body", "required");
        }
        LocalDate date = null;
        if (body.adjustmentDate() != null && !body.adjustmentDate().isBlank()) {
            try {
                date = LocalDate.parse(body.adjustmentDate().trim());
            } catch (DateTimeParseException e) {
                throw DomainException.invalid("adjustmentDate", "date_invalid");
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(lifecycle.issueAdjustment(ctx, key, id, Money.amount(body.amount(), "amount", false), date, body.reason()));
    }

    @PostMapping("/adjustments/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable("id") UUID id, @RequestBody ReasonBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(lifecycle.cancelAdjustment(ctx, id, body == null ? null : body.reason()));
    }

    @PostMapping("/documents/{id}/void-recognized")
    public ResponseEntity<?> voidRecognized(@PathVariable("id") UUID id, @RequestBody ReasonBody body,
                                            HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(lifecycle.voidRecognized(ctx, id, body == null ? null : body.reason()));
    }
}
