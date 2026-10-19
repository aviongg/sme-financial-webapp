package com.app.sme_health_backend.counterparty;

import com.app.sme_health_backend.counterparty.dto.CounterpartyCreateRequest;
import com.app.sme_health_backend.counterparty.dto.CounterpartyDetailResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartyListResponse;
import com.app.sme_health_backend.counterparty.dto.CounterpartySummaryItem;
import com.app.sme_health_backend.counterparty.dto.CounterpartyUpdateRequest;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CounterpartyService {

    private static final Pattern BASIC_EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern ALLOWED_PHONE = Pattern.compile("^[0-9+\\-\\s()]{3,50}$");

    private final CounterpartyRepository counterpartyRepository;

    public CounterpartyService(CounterpartyRepository counterpartyRepository) {
        this.counterpartyRepository = counterpartyRepository;
    }

    @Transactional
    public CounterpartyDetailResponse createCounterparty(BusinessAccessContext context, CounterpartyCreateRequest request) {
        if (request == null) {
            throw new CounterpartyValidationException("body", "Request body is required");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        // Validate displayName
        String displayName = validateDisplayName(request.displayName(), errors);

        // Validate type
        CounterpartyType type = validateType(request.type(), errors);

        // Validate optional contact details
        String email = validateEmail(request.email(), errors);
        String phone = validatePhone(request.phone(), errors);
        String address = validateAddress(request.address(), errors);
        String referenceCode = validateReferenceCode(request.referenceCode(), errors);
        String notes = validateNotes(request.notes(), errors);

        if (!errors.isEmpty()) {
            throw new CounterpartyValidationException(errors);
        }

        Counterparty counterparty = new Counterparty(
                context.businessId(),
                displayName,
                type,
                email,
                phone,
                address,
                referenceCode,
                notes
        );

        Counterparty saved = counterpartyRepository.save(counterparty);
        return toDetailResponse(saved);
    }

    @Transactional
    public CounterpartyDetailResponse updateCounterparty(
            BusinessAccessContext context,
            UUID id,
            CounterpartyUpdateRequest request
    ) {
        if (request == null) {
            throw new CounterpartyValidationException("body", "Request body is required");
        }

        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty not found"));

        if (request.version() == null) {
            throw new CounterpartyValidationException("version", "Version is required for updating a counterparty");
        }

        if (!Objects.equals(request.version(), counterparty.getVersion())) {
            throw new CounterpartyConflictException(
                    "Counterparty version mismatch: expected " + counterparty.getVersion() + " but received " + request.version()
            );
        }

        Map<String, String> errors = new LinkedHashMap<>();

        String displayName = validateDisplayName(request.displayName(), errors);
        CounterpartyType type = validateType(request.type(), errors);
        String email = validateEmail(request.email(), errors);
        String phone = validatePhone(request.phone(), errors);
        String address = validateAddress(request.address(), errors);
        String referenceCode = validateReferenceCode(request.referenceCode(), errors);
        String notes = validateNotes(request.notes(), errors);

        if (!errors.isEmpty()) {
            throw new CounterpartyValidationException(errors);
        }

        counterparty.setDisplayName(displayName);
        counterparty.setType(type);
        counterparty.setEmail(email);
        counterparty.setPhone(phone);
        counterparty.setAddress(address);
        counterparty.setReferenceCode(referenceCode);
        counterparty.setNotes(notes);
        counterparty.setUpdatedAt(Instant.now());

        Counterparty updated = counterpartyRepository.save(counterparty);
        return toDetailResponse(updated);
    }

    @Transactional
    public CounterpartyDetailResponse archiveCounterparty(BusinessAccessContext context, UUID id) {
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty not found"));

        if (counterparty.getStatus() != CounterpartyStatus.ARCHIVED) {
            counterparty.setStatus(CounterpartyStatus.ARCHIVED);
            counterparty.setUpdatedAt(Instant.now());
            counterparty = counterpartyRepository.save(counterparty);
        }

        return toDetailResponse(counterparty);
    }

    @Transactional
    public CounterpartyDetailResponse restoreCounterparty(BusinessAccessContext context, UUID id) {
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty not found"));

        if (counterparty.getStatus() != CounterpartyStatus.ACTIVE) {
            counterparty.setStatus(CounterpartyStatus.ACTIVE);
            counterparty.setUpdatedAt(Instant.now());
            counterparty = counterpartyRepository.save(counterparty);
        }

        return toDetailResponse(counterparty);
    }

    @Transactional(readOnly = true)
    public CounterpartyDetailResponse getCounterparty(BusinessAccessContext context, UUID id) {
        Counterparty counterparty = counterpartyRepository.findByIdAndBusinessId(id, context.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Counterparty not found"));
        return toDetailResponse(counterparty);
    }

    @Transactional(readOnly = true)
    public CounterpartyListResponse listCounterparties(
            BusinessAccessContext context,
            String search,
            String typeStr,
            String statusStr,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new CounterpartyValidationException("page", "Page index cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new CounterpartyValidationException("size", "Page size must be between 1 and 100");
        }

        final CounterpartyStatus statusFilter;
        if (statusStr == null || statusStr.isBlank()) {
            statusFilter = CounterpartyStatus.ACTIVE;
        } else if ("ALL".equalsIgnoreCase(statusStr.trim())) {
            statusFilter = null;
        } else {
            try {
                statusFilter = CounterpartyStatus.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new CounterpartyValidationException("status", "Invalid status: " + statusStr + ". Supported: ACTIVE, ARCHIVED, ALL");
            }
        }

        final CounterpartyType specificType;
        final boolean isCustomerQuery;
        final boolean isSupplierQuery;

        if (typeStr == null || typeStr.isBlank()) {
            specificType = null;
            isCustomerQuery = false;
            isSupplierQuery = false;
        } else {
            String upper = typeStr.trim().toUpperCase(Locale.ROOT);
            if ("CUSTOMER".equals(upper)) {
                specificType = null;
                isCustomerQuery = true;
                isSupplierQuery = false;
            } else if ("SUPPLIER".equals(upper)) {
                specificType = null;
                isCustomerQuery = false;
                isSupplierQuery = true;
            } else if ("BOTH".equals(upper)) {
                specificType = CounterpartyType.BOTH;
                isCustomerQuery = false;
                isSupplierQuery = false;
            } else {
                throw new CounterpartyValidationException("type", "Invalid type: " + typeStr + ". Supported: CUSTOMER, SUPPLIER, BOTH");
            }
        }

        final String trimmedSearch = (search != null && !search.isBlank()) ? search.trim().toLowerCase(Locale.ROOT) : null;

        Specification<Counterparty> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), context.businessId()));

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }

            if (isCustomerQuery) {
                predicates.add(root.get("type").in(CounterpartyType.CUSTOMER, CounterpartyType.BOTH));
            } else if (isSupplierQuery) {
                predicates.add(root.get("type").in(CounterpartyType.SUPPLIER, CounterpartyType.BOTH));
            } else if (specificType != null) {
                predicates.add(cb.equal(root.get("type"), specificType));
            }

            if (trimmedSearch != null) {
                String pattern = "%" + trimmedSearch + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("displayName")), pattern);
                Predicate refMatch = cb.like(cb.lower(root.get("referenceCode")), pattern);
                predicates.add(cb.or(nameMatch, refMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("displayName").ignoreCase(),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        Page<Counterparty> resultPage = counterpartyRepository.findAll(spec, pageable);

        List<CounterpartySummaryItem> items = resultPage.getContent().stream()
                .map(this::toSummaryItem)
                .toList();

        return new CounterpartyListResponse(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    private String validateDisplayName(String name, Map<String, String> errors) {
        if (name == null || name.trim().isEmpty()) {
            errors.put("displayName", "Display name is required");
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() > 255) {
            errors.put("displayName", "Display name must not exceed 255 characters");
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                errors.put("displayName", "Display name contains invalid control characters");
                return null;
            }
        }
        return trimmed;
    }

    private CounterpartyType validateType(String typeStr, Map<String, String> errors) {
        if (typeStr == null || typeStr.trim().isEmpty()) {
            errors.put("type", "Type is required");
            return null;
        }
        try {
            return CounterpartyType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.put("type", "Invalid type: " + typeStr + ". Supported types: CUSTOMER, SUPPLIER, BOTH");
            return null;
        }
    }

    private String validateEmail(String email, Map<String, String> errors) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.length() > 255) {
            errors.put("email", "Email must not exceed 255 characters");
            return null;
        }
        if (!BASIC_EMAIL.matcher(trimmed).matches()) {
            errors.put("email", "Invalid email address format");
            return null;
        }
        return trimmed;
    }

    private String validatePhone(String phone, Map<String, String> errors) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() > 50) {
            errors.put("phone", "Phone must not exceed 50 characters");
            return null;
        }
        if (!ALLOWED_PHONE.matcher(trimmed).matches()) {
            errors.put("phone", "Phone contains invalid characters");
            return null;
        }
        return trimmed;
    }

    private String validateAddress(String address, Map<String, String> errors) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        String trimmed = address.trim();
        if (trimmed.length() > 500) {
            errors.put("address", "Address must not exceed 500 characters");
            return null;
        }
        return trimmed;
    }

    private String validateReferenceCode(String ref, Map<String, String> errors) {
        if (ref == null || ref.trim().isEmpty()) {
            return null;
        }
        String trimmed = ref.trim();
        if (trimmed.length() > 100) {
            errors.put("referenceCode", "Reference code must not exceed 100 characters");
            return null;
        }
        return trimmed;
    }

    private String validateNotes(String notes, Map<String, String> errors) {
        if (notes == null || notes.trim().isEmpty()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > 1000) {
            errors.put("notes", "Notes must not exceed 1000 characters");
            return null;
        }
        return trimmed;
    }

    private CounterpartyDetailResponse toDetailResponse(Counterparty c) {
        return new CounterpartyDetailResponse(
                c.getId(),
                c.getBusinessId(),
                c.getDisplayName(),
                c.getType().name(),
                c.getStatus().name(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getReferenceCode(),
                c.getNotes(),
                c.getVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CounterpartySummaryItem toSummaryItem(Counterparty c) {
        return new CounterpartySummaryItem(
                c.getId(),
                c.getDisplayName(),
                c.getType().name(),
                c.getStatus().name(),
                c.getEmail(),
                c.getPhone(),
                c.getAddress(),
                c.getReferenceCode(),
                c.getVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
