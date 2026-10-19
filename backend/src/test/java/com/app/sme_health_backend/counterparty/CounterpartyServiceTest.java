package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyListResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyUpdateRequest;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterpartyServiceTest {

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @InjectMocks
    private CounterpartyService counterpartyService;

    private BusinessAccessContext context;
    private UUID businessId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        context = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Successfully creates CUSTOMER counterparty with valid fields")
    void testCreateCustomerSuccess() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest(
                "ABC Trading",
                "CUSTOMER",
                "abc@example.com",
                "+923001234567",
                "Plot 42, Karachi",
                "REF-001",
                "Reliable retail buyer"
        );

        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setVersion(0L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CounterpartyDetailResponse response = counterpartyService.createCounterparty(context, request);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("ABC Trading");
        assertThat(response.type()).isEqualTo("CUSTOMER");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.email()).isEqualTo("abc@example.com");
        assertThat(response.phone()).isEqualTo("+923001234567");
        assertThat(response.address()).isEqualTo("Plot 42, Karachi");
        assertThat(response.referenceCode()).isEqualTo("REF-001");
        assertThat(response.notes()).isEqualTo("Reliable retail buyer");

        ArgumentCaptor<Counterparty> captor = ArgumentCaptor.forClass(Counterparty.class);
        verify(counterpartyRepository).save(captor.capture());
        Counterparty saved = captor.getValue();
        assertThat(saved.getBusinessId()).isEqualTo(businessId);
        assertThat(saved.getDisplayName()).isEqualTo("ABC Trading");
        assertThat(saved.getType()).isEqualTo(CounterpartyType.CUSTOMER);
        assertThat(saved.getStatus()).isEqualTo(CounterpartyStatus.ACTIVE);
    }

    @Test
    @DisplayName("Successfully creates SUPPLIER counterparty with unicode/Urdu name")
    void testCreateSupplierWithUrduName() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest(
                "احمد ٹیکسٹائل",
                "SUPPLIER",
                null,
                "+92 21 34567890",
                "فیصل آباد",
                "SUP-99",
                "Raw materials vendor"
        );

        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setVersion(0L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CounterpartyDetailResponse response = counterpartyService.createCounterparty(context, request);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("احمد ٹیکسٹائل");
        assertThat(response.type()).isEqualTo("SUPPLIER");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Successfully creates BOTH counterparty")
    void testCreateBothSuccess() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest("Delta Co", "BOTH");

        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setVersion(0L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CounterpartyDetailResponse response = counterpartyService.createCounterparty(context, request);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("Delta Co");
        assertThat(response.type()).isEqualTo("BOTH");
    }

    @Test
    @DisplayName("Allows duplicate display names without error")
    void testDuplicateDisplayNamePermitted() {
        CounterpartyCreateRequest request1 = new CounterpartyCreateRequest("Duplicate Ltd", "CUSTOMER");
        CounterpartyCreateRequest request2 = new CounterpartyCreateRequest("Duplicate Ltd", "SUPPLIER");

        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setVersion(0L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        CounterpartyDetailResponse r1 = counterpartyService.createCounterparty(context, request1);
        CounterpartyDetailResponse r2 = counterpartyService.createCounterparty(context, request2);

        assertThat(r1.displayName()).isEqualTo("Duplicate Ltd");
        assertThat(r2.displayName()).isEqualTo("Duplicate Ltd");
        assertThat(r1.type()).isEqualTo("CUSTOMER");
        assertThat(r2.type()).isEqualTo("SUPPLIER");
    }

    @Test
    @DisplayName("Rejects missing or empty display name")
    void testRejectsMissingDisplayName() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest("   ", "CUSTOMER");

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("displayName");
                });
    }

    @Test
    @DisplayName("Rejects display name exceeding 255 characters")
    void testRejectsOversizedDisplayName() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest("A".repeat(256), "CUSTOMER");

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("displayName");
                });
    }

    @Test
    @DisplayName("Rejects display name containing ISO control characters")
    void testRejectsControlCharactersInDisplayName() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest("Bad\u0000Name", "CUSTOMER");

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("displayName");
                });
    }

    @Test
    @DisplayName("Rejects invalid counterparty type")
    void testRejectsInvalidType() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest("Valid Name", "INVALID_TYPE");

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("type");
                });
    }

    @Test
    @DisplayName("Rejects invalid email format")
    void testRejectsInvalidEmail() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest(
                "Valid Name", "CUSTOMER", "not-an-email", null, null, null, null
        );

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("email");
                });
    }

    @Test
    @DisplayName("Rejects phone with invalid characters")
    void testRejectsInvalidPhone() {
        CounterpartyCreateRequest request = new CounterpartyCreateRequest(
                "Valid Name", "CUSTOMER", null, "123-CALL-NOW", null, null, null
        );

        assertThatThrownBy(() -> counterpartyService.createCounterparty(context, request))
                .isInstanceOf(CounterpartyValidationException.class)
                .satisfies(e -> {
                    CounterpartyValidationException cve = (CounterpartyValidationException) e;
                    assertThat(cve.getErrors()).containsKey("phone");
                });
    }

    @Test
    @DisplayName("Successfully updates counterparty with matching version")
    void testUpdateCounterpartySuccess() {
        UUID id = UUID.randomUUID();
        Counterparty existing = new Counterparty(
                businessId, "Old Name", CounterpartyType.CUSTOMER,
                "old@example.com", "03001111111", "Old Address", "REF-OLD", "Old notes"
        );
        existing.setId(id);
        existing.setVersion(1L);

        when(counterpartyRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existing));
        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CounterpartyUpdateRequest updateRequest = new CounterpartyUpdateRequest(
                1L,
                "New Name Ltd",
                "BOTH",
                "new@example.com",
                "+923002222222",
                "New Address 123",
                "REF-NEW",
                "Updated notes"
        );

        CounterpartyDetailResponse response = counterpartyService.updateCounterparty(context, id, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("New Name Ltd");
        assertThat(response.type()).isEqualTo("BOTH");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.phone()).isEqualTo("+923002222222");
        assertThat(response.address()).isEqualTo("New Address 123");
        assertThat(response.referenceCode()).isEqualTo("REF-NEW");
        assertThat(response.notes()).isEqualTo("Updated notes");
    }

    @Test
    @DisplayName("Rejects update when version does not match (optimistic concurrency conflict)")
    void testUpdateVersionMismatch() {
        UUID id = UUID.randomUUID();
        Counterparty existing = new Counterparty(
                businessId, "Old Name", CounterpartyType.CUSTOMER,
                null, null, null, null, null
        );
        existing.setId(id);
        existing.setVersion(2L);

        when(counterpartyRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existing));

        CounterpartyUpdateRequest updateRequest = new CounterpartyUpdateRequest(
                1L, // Mismatched version
                "New Name",
                "CUSTOMER",
                null, null, null, null, null
        );

        assertThatThrownBy(() -> counterpartyService.updateCounterparty(context, id, updateRequest))
                .isInstanceOf(CounterpartyConflictException.class)
                .hasMessageContaining("version mismatch");
    }

    @Test
    @DisplayName("Archives counterparty successfully without deletion")
    void testArchiveCounterparty() {
        UUID id = UUID.randomUUID();
        Counterparty existing = new Counterparty(
                businessId, "Active Co", CounterpartyType.CUSTOMER,
                null, null, null, null, null
        );
        existing.setId(id);
        existing.setStatus(CounterpartyStatus.ACTIVE);

        when(counterpartyRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existing));
        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CounterpartyDetailResponse response = counterpartyService.archiveCounterparty(context, id);

        assertThat(response.status()).isEqualTo("ARCHIVED");
        assertThat(existing.getStatus()).isEqualTo(CounterpartyStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Restores archived counterparty to ACTIVE")
    void testRestoreCounterparty() {
        UUID id = UUID.randomUUID();
        Counterparty existing = new Counterparty(
                businessId, "Archived Co", CounterpartyType.SUPPLIER,
                null, null, null, null, null
        );
        existing.setId(id);
        existing.setStatus(CounterpartyStatus.ARCHIVED);

        when(counterpartyRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.of(existing));
        when(counterpartyRepository.save(any(Counterparty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CounterpartyDetailResponse response = counterpartyService.restoreCounterparty(context, id);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(existing.getStatus()).isEqualTo(CounterpartyStatus.ACTIVE);
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when counterparty is not found or belongs to another business")
    void testCrossBusinessAccessRejected() {
        UUID id = UUID.randomUUID();
        when(counterpartyRepository.findByIdAndBusinessId(id, businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> counterpartyService.getCounterparty(context, id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Counterparty not found");
    }

    @Test
    @DisplayName("Lists counterparties with pagination and filtering")
    void testListCounterparties() {
        Counterparty c1 = new Counterparty(businessId, "Alpha Corp", CounterpartyType.CUSTOMER, null, null, null, null, null);
        c1.setId(UUID.randomUUID());
        Counterparty c2 = new Counterparty(businessId, "Beta Traders", CounterpartyType.BOTH, null, null, null, null, null);
        c2.setId(UUID.randomUUID());

        when(counterpartyRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c1, c2)));

        CounterpartyListResponse response = counterpartyService.listCounterparties(
                context, "corp", "CUSTOMER", "ACTIVE", 0, 20
        );

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).displayName()).isEqualTo("Alpha Corp");
        assertThat(response.content().get(1).displayName()).isEqualTo("Beta Traders");
    }
}
