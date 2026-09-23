package com.app.sme_health_backend.identity.controller;

import com.app.sme_health_backend.identity.dto.BusinessResponse;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.dto.SelectActiveBusinessRequest;
import com.app.sme_health_backend.identity.entity.AppUser;
import com.app.sme_health_backend.identity.repository.AppUserRepository;
import com.app.sme_health_backend.identity.service.ActiveBusinessContext;
import com.app.sme_health_backend.identity.service.BusinessService;
import com.app.sme_health_backend.security.service.AppUserDetails;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final ActiveBusinessContext activeBusinessContext;
    private final AppUserRepository userRepository;

    public BusinessController(
            BusinessService businessService,
            ActiveBusinessContext activeBusinessContext,
            AppUserRepository userRepository
    ) {
        this.businessService = businessService;
        this.activeBusinessContext = activeBusinessContext;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<BusinessResponse> createBusiness(
            @Valid @RequestBody CreateBusinessRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = resolveCurrentUserId();
        BusinessResponse response = businessService.createBusiness(request, userId);

        // Mutate session active business only AFTER transaction has successfully committed
        activeBusinessContext.setActiveBusinessId(httpRequest, response.businessId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BusinessResponse>> listBusinesses(HttpServletRequest httpRequest) {
        UUID userId = resolveCurrentUserId();
        UUID activeBusinessId = activeBusinessContext.getSessionActiveBusinessId(httpRequest);
        List<BusinessResponse> businesses = businessService.listUserBusinesses(userId, activeBusinessId);
        return ResponseEntity.ok(businesses);
    }

    @PostMapping("/active")
    public ResponseEntity<BusinessResponse> setActiveBusiness(
            @Valid @RequestBody SelectActiveBusinessRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID userId = resolveCurrentUserId();
        BusinessResponse response = businessService.validateAndGetBusinessForActivation(userId, request.businessId());

        // Update session active business
        activeBusinessContext.setActiveBusinessId(httpRequest, response.businessId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    public ResponseEntity<BusinessResponse> getActiveBusiness(HttpServletRequest httpRequest) {
        UUID userId = resolveCurrentUserId();
        UUID activeBusinessId = activeBusinessContext.getSessionActiveBusinessId(httpRequest);
        if (activeBusinessId == null) {
            throw new ResourceNotFoundException("No active business selected");
        }

        BusinessResponse response = businessService.getActiveBusiness(userId, activeBusinessId);
        if (response == null) {
            activeBusinessContext.clearActiveBusinessId(httpRequest);
            throw new ResourceNotFoundException("Active business is no longer accessible");
        }

        return ResponseEntity.ok(response);
    }

    private UUID resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated");
        }
        if (auth.getPrincipal() instanceof AppUserDetails userDetails) {
            return userDetails.getId();
        } else if (auth.getName() != null) {
            return userRepository.findByEmail(auth.getName())
                    .map(AppUser::getId)
                    .orElseThrow(() -> new AccessDeniedException("User account not found"));
        }
        throw new AccessDeniedException("Invalid authentication principal");
    }
}
