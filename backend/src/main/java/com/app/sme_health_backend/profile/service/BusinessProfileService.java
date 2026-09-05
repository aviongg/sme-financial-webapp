package com.app.sme_health_backend.profile.service;

import com.app.sme_health_backend.profile.dto.BusinessProfileRequest;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class BusinessProfileService {

    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfileService(BusinessProfileRepository businessProfileRepository) {
        this.businessProfileRepository = businessProfileRepository;
    }

    @Transactional
    public BusinessProfile createProfile(BusinessProfileRequest request) {

        if (businessProfileRepository.existsById(request.getUserId())) {
            throw new IllegalArgumentException("Business profile already exists for this user");
        }

        BusinessProfile profile = new BusinessProfile();

        profile.setUserId(request.getUserId());
        profile.setBusinessType(request.getBusinessType());
        profile.setLanguagePreference(
            request.getLanguagePreference() == null
                ? "en"
                : request.getLanguagePreference()
        );
        profile.setWhatsappNumber(request.getWhatsappNumber());
        profile.setWhatsappOptIn(request.isWhatsappOptIn());
        profile.setCreatedAt(LocalDateTime.now());

        return businessProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public BusinessProfile getProfile(UUID userId) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        return businessProfileRepository.findById(userId)
            .orElseThrow(() ->
                new IllegalArgumentException("Business profile not found for this user")
            );
    }
}