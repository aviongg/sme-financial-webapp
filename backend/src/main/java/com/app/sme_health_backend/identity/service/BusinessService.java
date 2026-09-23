package com.app.sme_health_backend.identity.service;

import com.app.sme_health_backend.identity.dto.BusinessResponse;
import com.app.sme_health_backend.identity.dto.CreateBusinessRequest;
import com.app.sme_health_backend.identity.entity.Business;
import com.app.sme_health_backend.identity.entity.BusinessMembership;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.model.MembershipStatus;
import com.app.sme_health_backend.identity.repository.BusinessMembershipRepository;
import com.app.sme_health_backend.identity.repository.BusinessRepository;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final BusinessMembershipRepository membershipRepository;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessProfileRepository businessProfileRepository,
            BusinessMembershipRepository membershipRepository
    ) {
        this.businessRepository = businessRepository;
        this.businessProfileRepository = businessProfileRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public BusinessResponse createBusiness(CreateBusinessRequest request, UUID userId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        // Validate WhatsApp opt-in rules
        if (request.whatsappOptIn() && (request.whatsappNumber() == null || request.whatsappNumber().isBlank())) {
            throw new IllegalArgumentException("WhatsApp number is required when opting in to WhatsApp notifications");
        }

        String normalizedPhone = null;
        if (request.whatsappNumber() != null && !request.whatsappNumber().isBlank()) {
            normalizedPhone = PhoneNumberValidator.normalizeAndValidate(request.whatsappNumber());
        }

        // 1. Generate Business UUID & create Business entity
        UUID businessId = UUID.randomUUID();
        Business business = new Business(businessId, "ACTIVE");
        businessRepository.save(business);

        // 2. Create BusinessProfile entity (using compatibility bridge: BusinessProfile.userId == Business.id)
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(businessId);
        profile.setBusinessType(request.businessType());
        profile.setLanguagePreference(request.languagePreference() != null ? request.languagePreference() : "en");
        profile.setWhatsappNumber(normalizedPhone);
        profile.setWhatsappOptIn(request.whatsappOptIn());
        if (request.whatsappOptIn()) {
            profile.setWhatsappOptedInAt(LocalDateTime.now());
        }
        profile.setPaymentBehavior(request.paymentBehavior());
        profile.setNtnRegistered(request.ntnRegistered());
        profile.setBusinessRegistered(request.businessRegistered());
        profile.setCreatedAt(LocalDateTime.now());
        businessProfileRepository.save(profile);

        // 3. Create BusinessMembership (role = OWNER, status = ACTIVE)
        BusinessMembership membership = new BusinessMembership(
                userId,
                businessId,
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE
        );
        membershipRepository.save(membership);

        return new BusinessResponse(
                businessId,
                profile.getBusinessType(),
                profile.getLanguagePreference(),
                MembershipRole.OWNER,
                MembershipStatus.ACTIVE,
                true
        );
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listUserBusinesses(UUID userId, UUID activeBusinessId) {
        Objects.requireNonNull(userId, "userId must not be null");

        List<BusinessMembership> memberships = membershipRepository.findByUserId(userId);
        List<BusinessResponse> responses = new ArrayList<>();

        for (BusinessMembership membership : memberships) {
            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                continue;
            }

            Business business = businessRepository.findById(membership.getBusinessId()).orElse(null);
            if (business == null || !"ACTIVE".equalsIgnoreCase(business.getStatus())) {
                continue;
            }

            BusinessProfile profile = businessProfileRepository.findById(business.getId()).orElse(null);
            boolean isActive = activeBusinessId != null && activeBusinessId.equals(business.getId());

            responses.add(new BusinessResponse(
                    business.getId(),
                    profile != null ? profile.getBusinessType() : "trade",
                    profile != null ? profile.getLanguagePreference() : "en",
                    membership.getRole(),
                    membership.getStatus(),
                    isActive
            ));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public BusinessResponse validateAndGetBusinessForActivation(UUID userId, UUID businessId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessId, "businessId must not be null");

        Business business = businessRepository.findById(businessId).orElse(null);
        if (business == null || !"ACTIVE".equalsIgnoreCase(business.getStatus())) {
            throw new ResourceNotFoundException("Business not found or access denied");
        }

        BusinessMembership membership = membershipRepository
                .findByUserIdAndBusinessId(userId, businessId)
                .orElse(null);

        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User does not have active membership in this business");
        }

        BusinessProfile profile = businessProfileRepository.findById(businessId).orElse(null);

        return new BusinessResponse(
                business.getId(),
                profile != null ? profile.getBusinessType() : "trade",
                profile != null ? profile.getLanguagePreference() : "en",
                membership.getRole(),
                membership.getStatus(),
                true
        );
    }

    @Transactional(readOnly = true)
    public BusinessResponse getActiveBusiness(UUID userId, UUID activeBusinessId) {
        if (activeBusinessId == null) {
            return null;
        }
        return validateAndGetBusinessForActivation(userId, activeBusinessId);
    }
}
