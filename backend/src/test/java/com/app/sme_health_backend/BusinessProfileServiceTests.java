package com.app.sme_health_backend;

import com.app.sme_health_backend.profile.dto.BusinessProfileRequest;
import com.app.sme_health_backend.profile.entity.BusinessProfile;
import com.app.sme_health_backend.profile.repository.BusinessProfileRepository;
import com.app.sme_health_backend.profile.service.BusinessProfileService;
import com.app.sme_health_backend.shared.exception.DuplicateResourceException;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTests {

    @Mock
    private BusinessProfileRepository businessProfileRepository;

    private BusinessProfileService businessProfileService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        businessProfileService =
                new BusinessProfileService(businessProfileRepository);

        userId = UUID.randomUUID();
    }

    @Test
    void shouldUpdateLanguagePreference() {
        BusinessProfile profile = new BusinessProfile();
        profile.setUserId(userId);
        profile.setLanguagePreference("en");

        when(businessProfileRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(profile));
        when(businessProfileRepository.save(profile)).thenReturn(profile);

        BusinessProfile updated = businessProfileService.updateLanguagePreference(userId, "ur");

        assertEquals("ur", updated.getLanguagePreference());
        verify(businessProfileRepository).findByUserIdForUpdate(userId);
        verify(businessProfileRepository).save(profile);
    }

    @Test
    void shouldRejectInvalidLanguagePreference() {
        assertThrows(IllegalArgumentException.class,
                () -> businessProfileService.updateLanguagePreference(userId, "fr"));
        assertThrows(IllegalArgumentException.class,
                () -> businessProfileService.updateLanguagePreference(null, "en"));
    }

    @Test
    void shouldCreateBusinessProfile() {
        BusinessProfileRequest request = validRequest();

        when(businessProfileRepository.existsById(userId))
                .thenReturn(false);

        when(businessProfileRepository.save(any(BusinessProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfile result =
                businessProfileService.createProfile(userId, request);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("retail", result.getBusinessType());
        assertEquals("en", result.getLanguagePreference());
        assertFalse(result.isWhatsappOptIn());

        verify(businessProfileRepository).existsById(userId);
        verify(businessProfileRepository).save(any(BusinessProfile.class));
    }

    @Test
    void shouldRejectDuplicateBusinessProfile() {
        BusinessProfileRequest request = validRequest();

        when(businessProfileRepository.existsById(userId))
                .thenReturn(true);

        DuplicateResourceException exception =
                assertThrows(
                        DuplicateResourceException.class,
                        () -> businessProfileService.createProfile(userId, request)
                );

        assertEquals(
                "Business profile already exists for this user",
                exception.getMessage()
        );

        verify(businessProfileRepository).existsById(userId);
        verify(businessProfileRepository, never())
                .save(any(BusinessProfile.class));
    }

    @Test
    void shouldReturnExistingBusinessProfile() {
        BusinessProfile profile = validProfile();

        when(businessProfileRepository.findById(userId))
                .thenReturn(java.util.Optional.of(profile));

        BusinessProfile result =
                businessProfileService.getProfile(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());

        verify(businessProfileRepository).findById(userId);
    }

    @Test
    void shouldThrowNotFoundWhenBusinessProfileDoesNotExist() {
        when(businessProfileRepository.findById(userId))
                .thenReturn(java.util.Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> businessProfileService.getProfile(userId)
                );

        assertEquals(
                "Business profile not found for this user",
                exception.getMessage()
        );

        verify(businessProfileRepository).findById(userId);
    }

    @Test
    void shouldRejectWhatsappOptInWithoutWhatsappNumber() {
        BusinessProfileRequest request = validRequest();

        request.setWhatsappOptIn(true);
        request.setWhatsappNumber(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(userId, request)
                );

        assertEquals(
                "WhatsApp number is required when WhatsApp opt-in is enabled",
                exception.getMessage()
        );

        verify(businessProfileRepository, never())
                .save(any(BusinessProfile.class));
    }

    @Test
    void shouldRejectWhatsappOptInWithBlankWhatsappNumber() {
        BusinessProfileRequest request = validRequest();

        request.setWhatsappOptIn(true);
        request.setWhatsappNumber("   ");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(userId, request)
                );

        assertEquals(
                "WhatsApp number is required when WhatsApp opt-in is enabled",
                exception.getMessage()
        );

        verify(businessProfileRepository, never())
                .save(any(BusinessProfile.class));
    }

    @Test
    void shouldAllowWhatsappOptInWithValidWhatsappNumber() {
        BusinessProfileRequest request = validRequest();

        request.setWhatsappOptIn(true);
        request.setWhatsappNumber("+923001234567");

        when(businessProfileRepository.existsById(userId))
                .thenReturn(false);

        when(businessProfileRepository.save(any(BusinessProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfile result =
                businessProfileService.createProfile(userId, request);

        assertNotNull(result);
        assertTrue(result.isWhatsappOptIn());
        assertEquals("+923001234567", result.getWhatsappNumber());

        verify(businessProfileRepository)
                .save(any(BusinessProfile.class));
    }

    @Test
    void shouldRejectNullUserId() {
        BusinessProfileRequest request = validRequest();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(null, request)
                );

        assertEquals(
                "Business ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(businessProfileRepository);
    }

    @Test
    void shouldRejectNullRequest() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(userId, null)
                );

        assertEquals(
                "Business profile request is required",
                exception.getMessage()
        );

        verifyNoInteractions(businessProfileRepository);
    }

    @Test
    void shouldRejectNullUserIdWhenGettingProfile() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.getProfile(null)
                );

        assertEquals(
                "User ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(businessProfileRepository);
    }

    @Test
    void shouldCreateBusinessProfileWithComplianceAndPaymentBehavior() {
        BusinessProfileRequest request = validRequest();
        request.setPaymentBehavior("immediate");
        request.setNtnRegistered(true);
        request.setBusinessRegistered(false);

        when(businessProfileRepository.existsById(userId)).thenReturn(false);
        when(businessProfileRepository.save(any(BusinessProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfile result = businessProfileService.createProfile(userId, request);

        assertNotNull(result);
        assertEquals("immediate", result.getPaymentBehavior());
        assertTrue(result.getNtnRegistered());
        assertFalse(result.getBusinessRegistered());
    }

    @Test
    void shouldUpdateWhatsAppPreferenceOptIn() {
        BusinessProfile profile = validProfile();
        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));

        BusinessProfile updated = businessProfileService.updateWhatsAppPreference(userId, "03001234567", true);

        assertTrue(updated.isWhatsappOptIn());
        assertEquals("+923001234567", updated.getWhatsappNumber());
        assertNotNull(updated.getWhatsappOptedInAt());
        verify(businessProfileRepository).save(profile);
    }

    @Test
    void shouldUpdateWhatsAppPreferenceOptOut() {
        BusinessProfile profile = validProfile();
        profile.setWhatsappOptIn(true);
        profile.setWhatsappNumber("+923001234567");

        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));
        when(businessProfileRepository.save(any(BusinessProfile.class))).thenAnswer(i -> i.getArgument(0));

        BusinessProfile updated = businessProfileService.updateWhatsAppPreference(userId, null, false);

        assertFalse(updated.isWhatsappOptIn());
        verify(businessProfileRepository).save(profile);
    }

    @Test
    void shouldRejectOptInWithoutPhoneNumber() {
        BusinessProfile profile = validProfile();
        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));

        assertThrows(IllegalArgumentException.class,
                () -> businessProfileService.updateWhatsAppPreference(userId, "", true));
        assertThrows(IllegalArgumentException.class,
                () -> businessProfileService.updateWhatsAppPreference(userId, null, true));
    }

    @Test
    void shouldThrowWhenUpdatingWhatsAppForNonExistentUser() {
        when(businessProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> businessProfileService.updateWhatsAppPreference(userId, "03001234567", true));
    }


    private BusinessProfileRequest validRequest() {
        BusinessProfileRequest request =
                new BusinessProfileRequest();

        request.setBusinessType("retail");
        request.setLanguagePreference("en");
        request.setWhatsappNumber(null);
        request.setWhatsappOptIn(false);

        return request;
    }

    private BusinessProfile validProfile() {
        BusinessProfile profile = new BusinessProfile();

        profile.setUserId(userId);
        profile.setBusinessType("retail");
        profile.setLanguagePreference("en");
        profile.setWhatsappNumber(null);
        profile.setWhatsappOptIn(false);

        return profile;
    }
}