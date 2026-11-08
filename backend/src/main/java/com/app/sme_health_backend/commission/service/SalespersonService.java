package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.commission.dto.SalesAssignmentCreateRequest;
import com.app.sme_health_backend.commission.dto.SalesAssignmentResponse;
import com.app.sme_health_backend.commission.dto.SalespersonCreateRequest;
import com.app.sme_health_backend.commission.dto.SalespersonResponse;
import com.app.sme_health_backend.commission.dto.SalespersonSelfResponse;
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
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.MembershipRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class SalespersonService {

    private final SalespersonProfileRepository salespersonRepository;
    private final SalesAssignmentRepository assignmentRepository;
    private final CounterpartyRepository counterpartyRepository;

    public SalespersonService(
            SalespersonProfileRepository salespersonRepository,
            SalesAssignmentRepository assignmentRepository,
            CounterpartyRepository counterpartyRepository
    ) {
        this.salespersonRepository = salespersonRepository;
        this.assignmentRepository = assignmentRepository;
        this.counterpartyRepository = counterpartyRepository;
    }

    public SalespersonResponse createSalesperson(BusinessAccessContext context, SalespersonCreateRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        String displayName = request.displayName() != null ? request.displayName().trim() : "";
        if (displayName.isBlank()) {
            throw new SalespersonValidationException("Display name is required");
        }

        String email = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : "";
        if (email.isBlank() || !email.contains("@")) {
            throw new SalespersonValidationException("Valid email is required");
        }

        if (salespersonRepository.existsByBusinessIdAndEmail(context.businessId(), email)) {
            throw new SalespersonConflictException("Salesperson with email '" + email + "' already exists in this business");
        }

        if (request.userId() != null && salespersonRepository.existsByBusinessIdAndUserId(context.businessId(), request.userId())) {
            throw new SalespersonConflictException("Salesperson profile is already linked to user '" + request.userId() + "' in this business");
        }

        SalespersonProfile profile = new SalespersonProfile(
                context.businessId(),
                request.userId(),
                displayName,
                email,
                request.phone() != null ? request.phone().trim() : null,
                request.notes() != null ? request.notes().trim() : null
        );

        SalespersonProfile saved = salespersonRepository.save(profile);
        return mapToSalespersonResponse(saved, 0L);
    }

    @Transactional(readOnly = true)
    public List<SalespersonResponse> listSalespersons(BusinessAccessContext context, SalespersonStatus statusFilter) {
        Objects.requireNonNull(context, "context must not be null");

        List<SalespersonProfile> profiles;
        if (statusFilter != null) {
            profiles = salespersonRepository.findByBusinessIdAndStatus(context.businessId(), statusFilter);
        } else {
            profiles = salespersonRepository.findByBusinessId(context.businessId());
        }

        // Count active assignments per salesperson
        List<SalesAssignment> allAssignments = assignmentRepository.findByBusinessId(context.businessId());
        Map<UUID, Long> activeCountMap = allAssignments.stream()
                .filter(a -> a.getStatus() == AssignmentStatus.ACTIVE)
                .collect(Collectors.groupingBy(SalesAssignment::getSalespersonId, Collectors.counting()));

        return profiles.stream()
                .sorted(Comparator.comparing(SalespersonProfile::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> mapToSalespersonResponse(p, activeCountMap.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SalespersonResponse getSalesperson(BusinessAccessContext context, UUID salespersonId) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(salespersonId, "salespersonId must not be null");

        SalespersonProfile profile = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElseThrow(() -> new SalespersonNotFoundException("Salesperson not found with ID: " + salespersonId));

        long activeCount = assignmentRepository.findByBusinessIdAndSalespersonIdAndStatus(
                context.businessId(), profile.getId(), AssignmentStatus.ACTIVE
        ).size();

        return mapToSalespersonResponse(profile, activeCount);
    }

    @Transactional(readOnly = true)
    public SalespersonSelfResponse getSalespersonForCurrentUser(BusinessAccessContext context) {
        Objects.requireNonNull(context, "context must not be null");

        Optional<SalespersonProfile> profileOpt = salespersonRepository.findByBusinessIdAndUserId(
                context.businessId(), context.userId()
        );

        if (profileOpt.isEmpty()) {
            throw new SalespersonNotFoundException("No salesperson profile linked to user " + context.userId() + " in this business");
        }

        SalespersonProfile profile = profileOpt.get();

        if (context.role() == MembershipRole.SALESPERSON && profile.getStatus() != SalespersonStatus.ACTIVE) {
            throw new AccessDeniedException("Salesperson account is inactive or suspended");
        }

        List<SalesAssignment> assignments = assignmentRepository.findByBusinessIdAndSalespersonId(
                context.businessId(), profile.getId()
        );

        Map<UUID, String> counterpartyNames = getCounterpartyNames(context.businessId(), assignments);

        List<SalesAssignmentResponse> assignmentResponses = assignments.stream()
                .map(a -> mapToAssignmentResponse(a, profile.getDisplayName(), counterpartyNames.getOrDefault(a.getCounterpartyId(), "Unknown Customer")))
                .toList();

        return new SalespersonSelfResponse(
                profile.getId(),
                profile.getBusinessId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getStatus(),
                profile.getNotes(),
                assignmentResponses,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public SalespersonResponse updateSalespersonStatus(BusinessAccessContext context, UUID salespersonId, SalespersonStatus newStatus) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(salespersonId, "salespersonId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        SalespersonProfile profile = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElseThrow(() -> new SalespersonNotFoundException("Salesperson not found with ID: " + salespersonId));

        profile.setStatus(newStatus);
        SalespersonProfile updated = salespersonRepository.save(profile);

        long activeCount = assignmentRepository.findByBusinessIdAndSalespersonIdAndStatus(
                context.businessId(), profile.getId(), AssignmentStatus.ACTIVE
        ).size();

        return mapToSalespersonResponse(updated, activeCount);
    }

    public SalesAssignmentResponse assignCustomer(BusinessAccessContext context, UUID salespersonId, SalesAssignmentCreateRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(salespersonId, "salespersonId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElseThrow(() -> new SalespersonNotFoundException("Salesperson not found with ID: " + salespersonId));

        if (salesperson.getStatus() != SalespersonStatus.ACTIVE) {
            throw new SalespersonValidationException("Cannot assign customers to an inactive or suspended salesperson");
        }

        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(request.counterpartyId(), context.businessId())
                .orElseThrow(() -> new SalespersonValidationException("Counterparty not found in this business: " + request.counterpartyId()));

        if (!counterparty.getType().isCustomerCompatible()) {
            throw new SalespersonValidationException("Counterparty must be of type CUSTOMER or BOTH");
        }

        if (assignmentRepository.existsByBusinessIdAndCounterpartyIdAndStatus(context.businessId(), request.counterpartyId(), AssignmentStatus.ACTIVE)) {
            throw new SalespersonConflictException("Customer '" + counterparty.getDisplayName() + "' is already actively assigned to a salesperson");
        }

        if (request.effectiveEndDate() != null && request.effectiveEndDate().isBefore(request.effectiveStartDate())) {
            throw new SalespersonValidationException("Effective end date cannot precede start date");
        }

        SalesAssignment assignment = new SalesAssignment(
                context.businessId(),
                salespersonId,
                request.counterpartyId(),
                request.effectiveStartDate(),
                request.effectiveEndDate(),
                context.userId(),
                request.notes() != null ? request.notes().trim() : null
        );

        SalesAssignment saved = assignmentRepository.save(assignment);
        return mapToAssignmentResponse(saved, salesperson.getDisplayName(), counterparty.getDisplayName());
    }

    @Transactional(readOnly = true)
    public List<SalesAssignmentResponse> listAssignments(BusinessAccessContext context, UUID salespersonId) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(salespersonId, "salespersonId must not be null");

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElseThrow(() -> new SalespersonNotFoundException("Salesperson not found with ID: " + salespersonId));

        List<SalesAssignment> assignments = assignmentRepository.findByBusinessIdAndSalespersonId(
                context.businessId(), salespersonId
        );

        Map<UUID, String> counterpartyNames = getCounterpartyNames(context.businessId(), assignments);

        return assignments.stream()
                .sorted(Comparator.comparing(SalesAssignment::getEffectiveStartDate).reversed())
                .map(a -> mapToAssignmentResponse(a, salesperson.getDisplayName(), counterpartyNames.getOrDefault(a.getCounterpartyId(), "Unknown Customer")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SalesAssignmentResponse> listCurrentUserAssignments(BusinessAccessContext context) {
        Objects.requireNonNull(context, "context must not be null");

        Optional<SalespersonProfile> profileOpt = salespersonRepository.findByBusinessIdAndUserId(
                context.businessId(), context.userId()
        );

        if (profileOpt.isEmpty()) {
            if (context.role() == MembershipRole.SALESPERSON) {
                throw new SalespersonNotFoundException("No salesperson profile linked to user " + context.userId());
            }
            return List.of();
        }

        SalespersonProfile profile = profileOpt.get();

        if (context.role() == MembershipRole.SALESPERSON && profile.getStatus() != SalespersonStatus.ACTIVE) {
            throw new AccessDeniedException("Salesperson account is inactive or suspended");
        }

        List<SalesAssignment> assignments = assignmentRepository.findByBusinessIdAndSalespersonIdAndStatus(
                context.businessId(), profile.getId(), AssignmentStatus.ACTIVE
        );

        Map<UUID, String> counterpartyNames = getCounterpartyNames(context.businessId(), assignments);

        return assignments.stream()
                .sorted(Comparator.comparing(SalesAssignment::getEffectiveStartDate).reversed())
                .map(a -> mapToAssignmentResponse(a, profile.getDisplayName(), counterpartyNames.getOrDefault(a.getCounterpartyId(), "Unknown Customer")))
                .toList();
    }

    public SalesAssignmentResponse terminateAssignment(BusinessAccessContext context, UUID salespersonId, UUID assignmentId) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(salespersonId, "salespersonId must not be null");
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");

        SalesAssignment assignment = assignmentRepository.findByIdAndBusinessId(assignmentId, context.businessId())
                .orElseThrow(() -> new SalespersonNotFoundException("Assignment not found with ID: " + assignmentId));

        if (!assignment.getSalespersonId().equals(salespersonId)) {
            throw new SalespersonNotFoundException("Assignment does not belong to salesperson: " + salespersonId);
        }

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(salespersonId, context.businessId())
                .orElse(null);
        String salespersonName = salesperson != null ? salesperson.getDisplayName() : "Unknown";

        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(assignment.getCounterpartyId(), context.businessId())
                .orElse(null);
        String counterpartyName = counterparty != null ? counterparty.getDisplayName() : "Unknown Customer";

        if (assignment.getStatus() == AssignmentStatus.INACTIVE) {
            return mapToAssignmentResponse(assignment, salespersonName, counterpartyName);
        }

        assignment.setStatus(AssignmentStatus.INACTIVE);
        if (assignment.getEffectiveEndDate() == null || assignment.getEffectiveEndDate().isAfter(LocalDate.now())) {
            assignment.setEffectiveEndDate(LocalDate.now());
        }

        SalesAssignment updated = assignmentRepository.save(assignment);
        return mapToAssignmentResponse(updated, salespersonName, counterpartyName);
    }

    private Map<UUID, String> getCounterpartyNames(UUID businessId, List<SalesAssignment> assignments) {
        Set<UUID> counterpartyIds = assignments.stream()
                .map(SalesAssignment::getCounterpartyId)
                .collect(Collectors.toSet());

        if (counterpartyIds.isEmpty()) {
            return Map.of();
        }

        return counterpartyRepository.findAllById(counterpartyIds).stream()
                .filter(c -> c.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(Counterparty::getId, Counterparty::getDisplayName));
    }

    private SalespersonResponse mapToSalespersonResponse(SalespersonProfile profile, long activeAssignmentsCount) {
        return new SalespersonResponse(
                profile.getId(),
                profile.getBusinessId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getStatus(),
                profile.getNotes(),
                activeAssignmentsCount,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    private SalesAssignmentResponse mapToAssignmentResponse(SalesAssignment assignment, String salespersonName, String counterpartyName) {
        return new SalesAssignmentResponse(
                assignment.getId(),
                assignment.getBusinessId(),
                assignment.getSalespersonId(),
                salespersonName,
                assignment.getCounterpartyId(),
                counterpartyName,
                assignment.getStatus(),
                assignment.getEffectiveStartDate(),
                assignment.getEffectiveEndDate(),
                assignment.getAssignedByUserId(),
                assignment.getNotes(),
                assignment.getCreatedAt()
        );
    }
}
