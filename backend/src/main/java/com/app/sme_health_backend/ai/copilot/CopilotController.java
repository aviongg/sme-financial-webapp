package com.app.sme_health_backend.ai.copilot;

import com.app.sme_health_backend.ai.AiAvailability;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.configuration.service.ModuleAccessPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Copilot API: read-only financial Q&A grounded in tool evidence. Requires FINANCIAL_DATA_READ and the AI module. */
@RestController
@RequestMapping("/api/copilot")
public class CopilotController {
    private final BusinessAuthorizationService authorization;
    private final ModuleAccessPolicy modules;
    private final CopilotService copilot;
    private final CopilotToolRegistry registry;
    private final AiAvailability availability;

    public CopilotController(BusinessAuthorizationService authorization, ModuleAccessPolicy modules, CopilotService copilot,
                             CopilotToolRegistry registry, AiAvailability availability) {
        this.authorization = authorization;
        this.modules = modules;
        this.copilot = copilot;
        this.registry = registry;
        this.availability = availability;
    }

    public record AskBody(String question, String language, List<CopilotTypes.Turn> conversation) { }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        var ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        var status = availability.status(ctx.businessId());
        boolean moduleEnabled = modules.isEnabled(ctx.businessId(), "ai");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of(
                "version", CopilotTypes.VERSION,
                "moduleEnabled", moduleEnabled,
                "mode", moduleEnabled ? (status.available() && "openai".equals(status.mode()) ? "openai" : "deterministic") : "disabled",
                "providerReason", status.reasonCode() == null ? "" : status.reasonCode(),
                "tools", registry.specs(ctx).stream().map(CopilotTypes.ToolSpec::name).toList()));
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskBody body, HttpServletRequest request) {
        var ctx = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        modules.requireEnabled(ctx.businessId(), "ai");
        var response = copilot.ask(ctx, body == null ? null : body.question(), body == null ? "en" : body.language(),
                body == null ? List.of() : body.conversation());
        // Role, membership or active business may change during a provider call; never deliver across contexts.
        var current = authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ);
        if (!ctx.equals(current)) throw new AccessDeniedException("Business context changed; retry in the active business");
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(response);
    }
}
