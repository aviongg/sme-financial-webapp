package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.SalesAssignment;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.SalespersonConflictException;
import com.app.sme_health_backend.commission.exception.SalespersonNotFoundException;
import com.app.sme_health_backend.commission.exception.SalespersonValidationException;
import com.app.sme_health_backend.commission.model.AssignmentStatus;
import com.app.sme_health_backend.commission.model.SalespersonStatus;
import com.app.sme_health_backend.commission.repository.SalesAssignmentRepository;
import com.app.sme_health_backend.commission.repository.SalespersonProfileRepository;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.counterparty.CounterpartyType;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalespersonServiceTest {

    @Mock
    private SalespersonProfileRepository salespersonRepository;

    @Mock
    private SalesAssignmentRepository assignmentRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @InjectMocks
    private SalespersonService service;

    private UUID businessId;
    private UUID userId;
    private BusinessAccessContext ownerContext;
    private BusinessAccessContext salespersonContext;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ownerContext = new BusinessAccessContext(userId, businessId, MembershipRole.OWNER);
        salespersonContext = new BusinessAccessContext(userId, businessId, MembershipRole.SALESPERSON);
    }

    @Test
    @DisplayName("Successfully creates a salesperson profile")
    void createSalesperson_Success() {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood",
                "tariq@example.com",
                "+923001234567",
                null,
                "North Region"
        );

        when(salespersonRepository.existsByBusinessIdAndEmail(businessId, "tariq@example.com")).thenReturn(false);
        when(salespersonRepository.save(any(SalespersonProfile.class))).thenAnswer(invocation -> {
            SalespersonProfile profile = invocation.getArgument(0);
            profile.setId(UUID.randomUUID());
            return profile;
        });

        SalespersonResponse response = service.createSalesperson(ownerContext, request);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("Tariq Mahmood");
        assertThat(response.email()).isEqualTo("tariq@example.com");
        assertThat(response.status()).isEqualTo(SalespersonStatus.ACTIVE);
        assertThat(response.activeAssignmentsCount()).isZero();
        verify(salespersonRepository).save(any(SalespersonProfile.class));
    }

    @Test
    @DisplayName("Throws conflict if salesperson email already exists in business")
    void createSalesperson_DuplicateEmail() {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood",
                "tariq@example.com",
                null,
                null,
                null
        );

        when(salespersonRepository.existsByBusinessIdAndEmail(businessId, "tariq@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createSalesperson(ownerContext, request))
                .isInstanceOf(SalespersonConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Throws conflict if userId is already linked in this business")
    void createSalesperson_DuplicateUserId() {
        UUID linkedUserId = UUID.randomUUID();
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "Tariq Mahmood",
                "tariq@example.com",
                null,
                linkedUserId,
                null
        );

        when(salespersonRepository.existsByBusinessIdAndEmail(businessId, "tariq@example.com")).thenReturn(false);
        when(salespersonRepository.existsByBusinessIdAndUserId(businessId, linkedUserId)).thenReturn(true);

        assertThatThrownBy(() -> service.createSalesperson(ownerContext, request))
                .isInstanceOf(SalespersonConflictException.class)
                .hasMessageContaining("already linked to user");
    }

    @Test
    @DisplayName("Throws validation exception if display name is blank")
    void createSalesperson_BlankName() {
        SalespersonCreateRequest request = new SalespersonCreateRequest(
                "   ",
                "tariq@example.com",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.createSalesperson(ownerContext, request))
                .isInstanceOf(SalespersonValidationException.class)
                .hasMessageContaining("Display name is required");
    }

    @Test
    @DisplayName("Lists salespersons with active assignment counts")
    void listSalespersons_Success() {
        SalespersonProfile p1 = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        p1.setId(UUID.randomUUID());
        SalespersonProfile p2 = new SalespersonProfile(businessId, null, "Bob", "bob@example.com", null, null);
        p2.setId(UUID.randomUUID());

        when(salespersonRepository.findByBusinessId(businessId)).thenReturn(List.of(p1, p2));

        SalesAssignment a1 = new SalesAssignment(businessId, p1.getId(), UUID.randomUUID(), LocalDate.now(), null, userId, null);
        a1.setStatus(AssignmentStatus.ACTIVE);
        when(assignmentRepository.findByBusinessId(businessId)).thenReturn(List.of(a1));

        List<SalespersonResponse> list = service.listSalespersons(ownerContext, null);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).displayName()).isEqualTo("Alice");
        assertThat(list.get(0).activeAssignmentsCount()).isEqualTo(1);
        assertThat(list.get(1).displayName()).isEqualTo("Bob");
        assertThat(list.get(1).activeAssignmentsCount()).isZero();
    }

    @Test
    @DisplayName("Gets salesperson by ID")
    void getSalesperson_Success() {
        UUID salespersonId = UUID.randomUUID();
        SalespersonProfile profile = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        profile.setId(salespersonId);

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(profile));
        when(assignmentRepository.findByBusinessIdAndSalespersonIdAndStatus(businessId, salespersonId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        SalespersonResponse response = service.getSalesperson(ownerContext, salespersonId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(salespersonId);
    }

    @Test
    @DisplayName("Get salesperson throws 404 if not found")
    void getSalesperson_NotFound() {
        UUID salespersonId = UUID.randomUUID();
        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSalesperson(ownerContext, salespersonId))
                .isInstanceOf(SalespersonNotFoundException.class);
    }

    @Test
    @DisplayName("Current user gets their own salesperson profile")
    void getSalespersonForCurrentUser_Success() {
        SalespersonProfile profile = new SalespersonProfile(businessId, userId, "Sales Rep", "rep@example.com", null, null);
        profile.setId(UUID.randomUUID());

        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(profile));
        when(assignmentRepository.findByBusinessIdAndSalespersonId(businessId, profile.getId())).thenReturn(List.of());

        SalespersonSelfResponse response = service.getSalespersonForCurrentUser(salespersonContext);

        assertThat(response).isNotNull();
        assertThat(response.displayName()).isEqualTo("Sales Rep");
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Inactive salesperson user is denied access to /me")
    void getSalespersonForCurrentUser_InactiveDenied() {
        SalespersonProfile profile = new SalespersonProfile(businessId, userId, "Sales Rep", "rep@example.com", null, null);
        profile.setId(UUID.randomUUID());
        profile.setStatus(SalespersonStatus.SUSPENDED);

        when(salespersonRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.getSalespersonForCurrentUser(salespersonContext))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("inactive or suspended");
    }

    @Test
    @DisplayName("Updates salesperson status")
    void updateSalespersonStatus_Success() {
        UUID salespersonId = UUID.randomUUID();
        SalespersonProfile profile = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        profile.setId(salespersonId);
        profile.setStatus(SalespersonStatus.ACTIVE);

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(profile));
        when(salespersonRepository.save(any(SalespersonProfile.class))).thenAnswer(i -> i.getArgument(0));

        SalespersonResponse response = service.updateSalespersonStatus(ownerContext, salespersonId, SalespersonStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(SalespersonStatus.INACTIVE);
    }

    @Test
    @DisplayName("Assigns a customer to an active salesperson")
    void assignCustomer_Success() {
        UUID salespersonId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        SalespersonProfile profile = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        profile.setId(salespersonId);
        profile.setStatus(SalespersonStatus.ACTIVE);

        Counterparty customer = new Counterparty();
        customer.setDisplayName("Acme Corp");
        customer.setType(CounterpartyType.CUSTOMER);

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(profile));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(customer));
        when(assignmentRepository.existsByBusinessIdAndCounterpartyIdAndStatus(businessId, counterpartyId, AssignmentStatus.ACTIVE)).thenReturn(false);
        when(assignmentRepository.save(any(SalesAssignment.class))).thenAnswer(i -> {
            SalesAssignment a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        SalesAssignmentCreateRequest req = new SalesAssignmentCreateRequest(
                counterpartyId,
                LocalDate.of(2026, 1, 1),
                null,
                "Exclusive customer"
        );

        SalesAssignmentResponse response = service.assignCustomer(ownerContext, salespersonId, req);

        assertThat(response).isNotNull();
        assertThat(response.salespersonName()).isEqualTo("Alice");
        assertThat(response.counterpartyName()).isEqualTo("Acme Corp");
        assertThat(response.status()).isEqualTo(AssignmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Cannot assign customer if counterparty is SUPPLIER")
    void assignCustomer_SupplierRejected() {
        UUID salespersonId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        SalespersonProfile profile = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        profile.setId(salespersonId);
        profile.setStatus(SalespersonStatus.ACTIVE);

        Counterparty supplier = new Counterparty();
        supplier.setDisplayName("Supplier Inc");
        supplier.setType(CounterpartyType.SUPPLIER);

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(profile));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(supplier));

        SalesAssignmentCreateRequest req = new SalesAssignmentCreateRequest(
                counterpartyId,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        assertThatThrownBy(() -> service.assignCustomer(ownerContext, salespersonId, req))
                .isInstanceOf(SalespersonValidationException.class)
                .hasMessageContaining("CUSTOMER or BOTH");
    }

    @Test
    @DisplayName("Cannot assign customer if customer already has active assignment")
    void assignCustomer_DuplicateActiveRejected() {
        UUID salespersonId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();

        SalespersonProfile profile = new SalespersonProfile(businessId, null, "Alice", "alice@example.com", null, null);
        profile.setId(salespersonId);
        profile.setStatus(SalespersonStatus.ACTIVE);

        Counterparty customer = new Counterparty();
        customer.setDisplayName("Acme Corp");
        customer.setType(CounterpartyType.CUSTOMER);

        when(salespersonRepository.findByIdAndBusinessId(salespersonId, businessId)).thenReturn(Optional.of(profile));
        when(counterpartyRepository.findByIdAndBusinessId(counterpartyId, businessId)).thenReturn(Optional.of(customer));
        when(assignmentRepository.existsByBusinessIdAndCounterpartyIdAndStatus(businessId, counterpartyId, AssignmentStatus.ACTIVE)).thenReturn(true);

        SalesAssignmentCreateRequest req = new SalesAssignmentCreateRequest(
                counterpartyId,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        assertThatThrownBy(() -> service.assignCustomer(ownerContext, salespersonId, req))
                .isInstanceOf(SalespersonConflictException.class)
                .hasMessageContaining("already actively assigned");
    }

    @Test
    @DisplayName("Terminates customer assignment")
    void terminateAssignment_Success() {
        UUID salespersonId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        SalesAssignment assignment = new SalesAssignment(
                businessId, salespersonId, UUID.randomUUID(), LocalDate.of(2026, 1, 1), null, userId, null
        );
        assignment.setId(assignmentId);
        assignment.setStatus(AssignmentStatus.ACTIVE);

        when(assignmentRepository.findByIdAndBusinessId(assignmentId, businessId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(SalesAssignment.class))).thenAnswer(i -> i.getArgument(0));

        SalesAssignmentResponse response = service.terminateAssignment(ownerContext, salespersonId, assignmentId);

        assertThat(response.status()).isEqualTo(AssignmentStatus.INACTIVE);
        assertThat(response.effectiveEndDate()).isNotNull();
    }
}
