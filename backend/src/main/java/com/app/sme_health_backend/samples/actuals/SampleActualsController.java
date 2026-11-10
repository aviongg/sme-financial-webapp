package com.app.sme_health_backend.samples.actuals;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.productsupport.DomainException;
import com.app.sme_health_backend.productsupport.Money;
import com.app.sme_health_backend.projects.profitability.ProjectProfitabilityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** F09 sample actuals and F05 project profitability read/write API. */
@RestController
public class SampleActualsController {
    private final BusinessAuthorizationService authorization;
    private final SampleActualsService samples;
    private final ProjectProfitabilityService projects;

    public SampleActualsController(BusinessAuthorizationService authorization, SampleActualsService samples,
                                   ProjectProfitabilityService projects) {
        this.authorization = authorization;
        this.samples = samples;
        this.projects = projects;
    }

    public record CostBody(UUID transactionId, String amount, String costCategory, String notes) { }
    public record RecoveryBody(UUID documentId, String amount, String notes) { }
    public record ReasonBody(String reason) { }

    @GetMapping("/api/samples/{id}/actuals")
    public ResponseEntity<?> actuals(@PathVariable("id") UUID id, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(samples.actuals(ctx.businessId(), id));
    }

    @PostMapping("/api/samples/{id}/actuals/costs")
    public ResponseEntity<?> addCost(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                     @PathVariable("id") UUID id, @RequestBody CostBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = post(request);
        if (body == null) throw DomainException.invalid("body", "required");
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(samples.addCost(ctx, key, id,
                body.transactionId(), Money.amount(body.amount(), "amount", false), body.costCategory(), body.notes()));
    }

    @PostMapping("/api/samples/{id}/actuals/costs/{costId}/reverse")
    public ResponseEntity<?> reverseCost(@PathVariable("id") UUID id, @PathVariable("costId") UUID costId,
                                         @RequestBody ReasonBody body, HttpServletRequest request) {
        return ok(samples.reverseCost(post(request), id, costId, body == null ? null : body.reason()));
    }

    @PostMapping("/api/samples/{id}/actuals/recoveries")
    public ResponseEntity<?> link(@PathVariable("id") UUID id, @RequestBody RecoveryBody body, HttpServletRequest request) {
        BusinessAccessContext ctx = post(request);
        if (body == null || body.documentId() == null) throw DomainException.invalid("documentId", "required");
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(samples.linkRecovery(ctx, id,
                body.documentId(), Money.amount(body.amount(), "amount", false), body.notes()));
    }

    @PostMapping("/api/samples/{id}/actuals/recoveries/{linkId}/unlink")
    public ResponseEntity<?> unlink(@PathVariable("id") UUID id, @PathVariable("linkId") UUID linkId,
                                    @RequestBody ReasonBody body, HttpServletRequest request) {
        return ok(samples.unlinkRecovery(post(request), id, linkId, body == null ? null : body.reason()));
    }

    @GetMapping("/api/projects/{id}/profitability")
    public ResponseEntity<?> profitability(@PathVariable("id") UUID id, HttpServletRequest request) {
        BusinessAccessContext ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        return ok(projects.profitability(ctx.businessId(), id));
    }

    private BusinessAccessContext post(HttpServletRequest request) {
        return authorization.requirePermission(request, BusinessPermission.FINANCIAL_TRANSACTION_POST);
    }

    private static ResponseEntity<?> ok(Object body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }
}
