package com.app.sme_health_backend.ai;

import com.app.sme_health_backend.configuration.service.ModuleAccessPolicy;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final BusinessAuthorizationService authorization;
    private final ModuleAccessPolicy modules;
    private final AiAvailability availability;
    private final AiExplanationService explanations;
    public AiController(BusinessAuthorizationService authorization, ModuleAccessPolicy modules,
                        AiAvailability availability, AiExplanationService explanations) {
        this.authorization = authorization; this.modules = modules;
        this.availability = availability; this.explanations = explanations;
    }
    @GetMapping("/status")
    public ResponseEntity<AiTypes.Status> status(HttpServletRequest request) {
        var actor = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        var status = modules.isEnabled(actor.businessId(), "ai") ? availability.status(actor.businessId())
                : availability.unavailable("module_disabled");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(status);
    }
    @PostMapping("/explain")
    public ResponseEntity<AiTypes.Response> explain(@Valid @RequestBody AiTypes.Request input, HttpServletRequest request) {
        var actor = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        modules.requireEnabled(actor.businessId(), "ai");
        if (("insight".equals(input.type()) || "recommendation".equals(input.type())) && input.sourceId() == null)
            throw new IllegalArgumentException("The existing financial source is required");
        if (input.sourceId() != null && !"insight".equals(input.type()) && !"recommendation".equals(input.type()))
            throw new IllegalArgumentException("Source identifiers are only supported for insight or recommendation explanations");
        var result = explanations.explain(actor, input);
        // Membership, role and active-business context may change while a provider call is in flight.
        var current = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        if (!actor.equals(current)) throw new AccessDeniedException("Business context changed; retry in the active business");
        modules.requireEnabled(current.businessId(), "ai");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
}
