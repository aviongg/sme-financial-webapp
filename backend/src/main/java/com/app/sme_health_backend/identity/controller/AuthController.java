package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.dto.CsrfResponse;
import com.app.sme_health_backend.identity.dto.LoginRequest;
import com.app.sme_health_backend.identity.dto.RegisterRequest;
import com.app.sme_health_backend.identity.dto.UserResponse;
import com.app.sme_health_backend.identity.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken != null) {
            return ResponseEntity.ok(new CsrfResponse(
                    csrfToken.getToken(),
                    csrfToken.getHeaderName(),
                    csrfToken.getParameterName()
            ));
        }
        return ResponseEntity.ok(new CsrfResponse("FINSIGHT-CSRF-TOKEN", "X-XSRF-TOKEN", "_csrf"));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserResponse response = authenticationService.login(request, httpRequest, httpResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authenticationService.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(authenticationService.getCurrentUser());
    }
}
