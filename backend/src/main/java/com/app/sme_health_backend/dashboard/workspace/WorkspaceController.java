package com.app.sme_health_backend.dashboard.workspace;

import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.app.sme_health_backend.dashboard.workspace.WorkspaceTypes.*;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final DashboardWorkspaceService workspace;
    private final BusinessAuthorizationService authorization;
    public WorkspaceController(DashboardWorkspaceService workspace, BusinessAuthorizationService authorization) {
        this.workspace = workspace; this.authorization = authorization;
    }
    @GetMapping public ResponseEntity<Response> get(HttpServletRequest request) {
        return response(workspace.get(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)));
    }
    @GetMapping("/widgets") public ResponseEntity<List<Metadata>> widgets(HttpServletRequest request) {
        return response(workspace.catalog(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ)));
    }
    @GetMapping("/presets/{presetKey}") public ResponseEntity<Response> preview(@PathVariable String presetKey, HttpServletRequest request) {
        return response(workspace.preview(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ), presetKey));
    }
    @PutMapping("/preferences") public ResponseEntity<Response> save(@Valid @RequestBody SaveRequest input, HttpServletRequest request) {
        return response(workspace.save(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ), input));
    }
    @PostMapping("/reset") public ResponseEntity<Response> reset(@Valid @RequestBody ResetRequest input, HttpServletRequest request) {
        return response(workspace.reset(authorization.requirePermission(request, BusinessPermission.FINANCIAL_DATA_READ), input));
    }
    @ExceptionHandler(WorkspaceException.class) public ResponseEntity<Map<String, Object>> workspaceError(WorkspaceException error) {
        return ResponseEntity.status(error.status()).header("Cache-Control", "no-store")
                .body(Map.of("status", error.status(), "error", error.code(), "message", error.getMessage()));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> invalidBody() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(Map.of("status", 400, "error", "layout_invalid", "message", "Invalid dashboard request."));
    }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().header("Cache-Control", "no-store").body(value); }
}
