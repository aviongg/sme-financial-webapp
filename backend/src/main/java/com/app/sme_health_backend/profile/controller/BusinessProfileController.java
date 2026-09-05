package com.app.sme_health_backend.profile.controller;

import com.app.sme_health_backend.profile.dto.BusinessProfileRequest;
import com.app.sme_health_backend.profile.dto.BusinessProfileResponse;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
public class BusinessProfileController {

    private final BusinessProfileService businessProfileService;

    public BusinessProfileController(BusinessProfileService businessProfileService) {
        this.businessProfileService = businessProfileService;
    }

    @PostMapping
    public ResponseEntity<BusinessProfileResponse> createProfile(
            @Valid @RequestBody BusinessProfileRequest request) {

        BusinessProfile profile = businessProfileService.createProfile(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BusinessProfileResponse.fromEntity(profile));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<BusinessProfileResponse> getProfile(
            @PathVariable UUID userId) {

        BusinessProfile profile = businessProfileService.getProfile(userId);

        return ResponseEntity.ok(
                BusinessProfileResponse.fromEntity(profile)
        );
    }
}