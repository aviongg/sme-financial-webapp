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
    void shouldCreateBusinessProfile() {
        BusinessProfileRequest request = validRequest();

        when(businessProfileRepository.existsById(userId))
                .thenReturn(false);

        when(businessProfileRepository.save(any(BusinessProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessProfile result =
                businessProfileService.createProfile(request);

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
                        () -> businessProfileService.createProfile(request)
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
                        () -> businessProfileService.createProfile(request)
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
                        () -> businessProfileService.createProfile(request)
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
                businessProfileService.createProfile(request);

        assertNotNull(result);
        assertTrue(result.isWhatsappOptIn());
        assertEquals("+923001234567", result.getWhatsappNumber());

        verify(businessProfileRepository)
                .save(any(BusinessProfile.class));
    }

    @Test
    void shouldRejectNullUserId() {
        BusinessProfileRequest request = validRequest();
        request.setUserId(null);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(request)
                );

        assertEquals(
                "User ID is required",
                exception.getMessage()
        );

        verifyNoInteractions(businessProfileRepository);
    }

    @Test
    void shouldRejectNullRequest() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> businessProfileService.createProfile(null)
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

    private BusinessProfileRequest validRequest() {
        BusinessProfileRequest request =
                new BusinessProfileRequest();

        request.setUserId(userId);
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