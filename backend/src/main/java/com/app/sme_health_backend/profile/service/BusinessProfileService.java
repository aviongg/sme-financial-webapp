package com.app.sme_health_backend.profile.service;

import com.app.sme_health_backend.profile.dto.BusinessProfileRequest;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class BusinessProfileService {

    private final BusinessProfileRepository businessProfileRepository;

    public BusinessProfileService(
            BusinessProfileRepository businessProfileRepository
    ) {
        this.businessProfileRepository = businessProfileRepository;
    }

    @Transactional
    public BusinessProfile createProfile(BusinessProfileRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Business profile request is required"
            );
        }

        if (request.getUserId() == null) {
            throw new IllegalArgumentException(
                    "User ID is required"
            );
        }

        if (businessProfileRepository.existsById(request.getUserId())) {
            throw new DuplicateResourceException(
                    "Business profile already exists for this user"
            );
        }

        validateWhatsAppOptIn(request);

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
            throw new IllegalArgumentException(
                    "User ID is required"
            );
        }

        return businessProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Business profile not found for this user"
                        )
                );
    }

    /** Existing advice is rendered in the new language on its next read. */
    @Transactional
    public BusinessProfile updateLanguagePreference(UUID userId, String language) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (!"en".equals(language) && !"ur".equals(language)) {
            throw new IllegalArgumentException("Language must be en or ur");
        }
        BusinessProfile profile = businessProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Business profile not found for this user"));
        profile.setLanguagePreference(language);
        return businessProfileRepository.save(profile);
    }

    private void validateWhatsAppOptIn(
            BusinessProfileRequest request
    ) {
        if (request.isWhatsappOptIn()
                && (request.getWhatsappNumber() == null
                || request.getWhatsappNumber().isBlank())) {

            throw new IllegalArgumentException(
                    "WhatsApp number is required when WhatsApp opt-in is enabled"
            );
        }
    }
}
