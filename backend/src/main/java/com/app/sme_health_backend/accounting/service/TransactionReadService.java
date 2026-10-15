package com.app.sme_health_backend.accounting.service;

import com.app.sme_health_backend.accounting.dto.MovementDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionDetailResponse;
import com.app.sme_health_backend.accounting.dto.TransactionListResponse;
import com.app.sme_health_backend.accounting.dto.TransactionSummaryResponse;
import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.exception.TransactionValidationException;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.AccountMovementSum;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionReadService {

    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;
    private final AccountMovementRepository movementRepository;
    private final EntityManager entityManager;

    public TransactionReadService(
            FinancialTransactionRepository transactionRepository,
            FinancialAccountRepository accountRepository,
            AccountMovementRepository movementRepository,
            EntityManager entityManager
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionDetailResponse getTransactionDetail(UUID businessId, UUID transactionId) {
        FinancialTransaction tx = transactionRepository.findByIdAndBusinessId(transactionId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial transaction not found"));

        FinancialAccount account = accountRepository.findByIdAndBusinessId(tx.getAccountId(), businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial account not found"));

        LocalDate today = LocalDate.now(KARACHI_ZONE);
        List<AccountMovement> movements = movementRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(tx.getId());
        String resultingBalance = computeCurrentBalanceString(account, today);

        List<MovementDetailResponse> movementDtos = movements.stream()
                .map(m -> new MovementDetailResponse(
                        m.getId(),
                        m.getAccountId(),
                        m.getMovementDirection().name(),
                        m.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                        m.getCurrency(),
                        m.getEffectiveDate(),
                        ISO_INSTANT_FORMATTER.format(m.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
                ))
                .toList();

        return new TransactionDetailResponse(
                tx.getId(),
                tx.getBusinessId(),
                tx.getAccountId(),
                account.getAccountName(),
                account.getAccountType().name(),
                tx.getEventType().name(),
                tx.getMovementDirection().name(),
                tx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                tx.getCurrency(),
                tx.getBaseAmountPkr().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                tx.getEffectiveDate(),
                tx.getDescription(),
                resultingBalance,
                movementDtos,
                tx.getCreatedByUserId(),
                ISO_INSTANT_FORMATTER.format(tx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionListResponse listTransactions(
            UUID businessId,
            UUID accountId,
            String eventTypeStr,
            String startDateStr,
            String endDateStr,
            int page,
            int size
    ) {
        TransactionEventType eventType = null;
        if (eventTypeStr != null && !eventTypeStr.isBlank()) {
            try {
                eventType = TransactionEventType.valueOf(eventTypeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new TransactionValidationException("eventType", "Invalid eventType: " + eventTypeStr);
            }
        }

        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                startDate = LocalDate.parse(startDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new TransactionValidationException("startDate", "Invalid date format for startDate. Expected YYYY-MM-DD");
            }
        }

        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                endDate = LocalDate.parse(endDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new TransactionValidationException("endDate", "Invalid date format for endDate. Expected YYYY-MM-DD");
            }
        }

        if (page < 0) {
            throw new TransactionValidationException("page", "Page index cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new TransactionValidationException("size", "Page size must be between 1 and 100");
        }

        final TransactionEventType finalEventType = eventType;
        final LocalDate finalStartDate = startDate;
        final LocalDate finalEndDate = endDate;

        org.springframework.data.domain.Pageable pageable = PageRequest.of(
                page,
                size,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("effectiveDate"),
                        org.springframework.data.domain.Sort.Order.desc("createdAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")
                )
        );

        org.springframework.data.jpa.domain.Specification<FinancialTransaction> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            if (finalEventType != null) {
                predicates.add(cb.equal(root.get("eventType"), finalEventType));
            }
            if (finalStartDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("effectiveDate"), finalStartDate));
            }
            if (finalEndDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("effectiveDate"), finalEndDate));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<FinancialTransaction> txPage = transactionRepository.findAll(spec, pageable);

        LocalDate effectiveCutoffDate = LocalDate.now(KARACHI_ZONE);
        String observationAt = ISO_INSTANT_FORMATTER.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));

        // Batch fetch accounts
        Set<UUID> accountIds = txPage.getContent().stream()
                .map(FinancialTransaction::getAccountId)
                .collect(Collectors.toSet());

        Map<UUID, FinancialAccount> accountsMap = accountRepository.findAllById(accountIds).stream()
                .filter(a -> a.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(FinancialAccount::getId, a -> a));

        // Batch fetch account movement sums for resulting balances
        Map<UUID, BigDecimal> netMovementsByAccount = new HashMap<>();
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByBusinessIdAndCutoffDate(businessId, effectiveCutoffDate);
        for (AccountMovementSum sum : sums) {
            BigDecimal current = netMovementsByAccount.getOrDefault(sum.getAccountId(), BigDecimal.ZERO);
            if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                netMovementsByAccount.put(sum.getAccountId(), current.add(sum.getTotalAmount()));
            } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                netMovementsByAccount.put(sum.getAccountId(), current.subtract(sum.getTotalAmount()));
            }
        }

        List<TransactionDetailResponse> content = txPage.getContent().stream().map(tx -> {
            FinancialAccount account = accountsMap.get(tx.getAccountId());
            String accountName = account != null ? account.getAccountName() : "Unknown";
            String accountType = account != null ? account.getAccountType().name() : "BANK";

            BigDecimal opening = account != null ? account.getOpeningBalance() : BigDecimal.ZERO;
            BigDecimal net = netMovementsByAccount.getOrDefault(tx.getAccountId(), BigDecimal.ZERO);
            String resultingBalance = opening.add(net).setScale(2, RoundingMode.UNNECESSARY).toPlainString();

            return new TransactionDetailResponse(
                    tx.getId(),
                    tx.getBusinessId(),
                    tx.getAccountId(),
                    accountName,
                    accountType,
                    tx.getEventType().name(),
                    tx.getMovementDirection().name(),
                    tx.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    tx.getCurrency(),
                    tx.getBaseAmountPkr().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                    tx.getEffectiveDate(),
                    tx.getDescription(),
                    resultingBalance,
                    Collections.emptyList(), // Content list omits nested movements for payload efficiency
                    tx.getCreatedByUserId(),
                    ISO_INSTANT_FORMATTER.format(tx.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
            );
        }).toList();

        long totalElements = txPage.getTotalElements();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return new TransactionListResponse(
                content,
                page,
                size,
                totalElements,
                totalPages,
                effectiveCutoffDate,
                observationAt
        );
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionSummaryResponse getTransactionSummary(
            UUID businessId,
            UUID accountId,
            String eventTypeStr,
            String startDateStr,
            String endDateStr
    ) {
        TransactionEventType eventType = null;
        if (eventTypeStr != null && !eventTypeStr.isBlank()) {
            try {
                eventType = TransactionEventType.valueOf(eventTypeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new TransactionValidationException("eventType", "Invalid eventType: " + eventTypeStr + ". Supported types: DIRECT_SALE_RECEIPT, OPERATING_EXPENSE_PAYMENT");
            }
        }

        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                startDate = LocalDate.parse(startDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new TransactionValidationException("startDate", "Invalid date format for startDate. Expected YYYY-MM-DD");
            }
        }

        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                endDate = LocalDate.parse(endDateStr.trim());
            } catch (DateTimeParseException e) {
                throw new TransactionValidationException("endDate", "Invalid date format for endDate. Expected YYYY-MM-DD");
            }
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new TransactionValidationException("startDate", "Start date cannot be after end date");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<FinancialTransaction> root = cq.from(FinancialTransaction.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("businessId"), businessId));
        if (accountId != null) {
            predicates.add(cb.equal(root.get("accountId"), accountId));
        }
        if (eventType != null) {
            predicates.add(cb.equal(root.get("eventType"), eventType));
        }
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("effectiveDate"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveDate"), endDate));
        }

        cq.multiselect(
                cb.count(root),
                cb.sum(
                        cb.<BigDecimal>selectCase()
                                .when(cb.equal(root.get("movementDirection"), MovementDirection.INCREASE), root.get("amount"))
                                .otherwise(BigDecimal.ZERO)
                ),
                cb.sum(
                        cb.<BigDecimal>selectCase()
                                .when(cb.equal(root.get("movementDirection"), MovementDirection.DECREASE), root.get("amount"))
                                .otherwise(BigDecimal.ZERO)
                )
        ).where(predicates.toArray(new Predicate[0]));

        Tuple result = entityManager.createQuery(cq).getSingleResult();

        Long countVal = result.get(0, Long.class);
        Number receivedNum = (Number) result.get(1);
        Number paidNum = (Number) result.get(2);

        long count = countVal != null ? countVal : 0L;
        BigDecimal moneyReceivedDec = receivedNum != null ? new BigDecimal(receivedNum.toString()) : BigDecimal.ZERO;
        BigDecimal moneyPaidDec = paidNum != null ? new BigDecimal(paidNum.toString()) : BigDecimal.ZERO;
        BigDecimal netDec = moneyReceivedDec.subtract(moneyPaidDec);

        String moneyReceived = moneyReceivedDec.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        String moneyPaid = moneyPaidDec.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        String netCashMovement = (netDec.signum() == 0)
                ? "0.00"
                : netDec.setScale(2, RoundingMode.UNNECESSARY).toPlainString();

        String observationAt = ISO_INSTANT_FORMATTER.format(Instant.now().truncatedTo(ChronoUnit.SECONDS));

        return new TransactionSummaryResponse(
                "PKR",
                startDate,
                endDate,
                accountId,
                eventType != null ? eventType.name() : null,
                count,
                moneyReceived,
                moneyPaid,
                netCashMovement,
                observationAt
        );
    }

    private String computeCurrentBalanceString(FinancialAccount account, LocalDate cutoffDate) {
        List<AccountMovementSum> sums = movementRepository.findMovementSumsByAccountIdAndCutoffDate(account.getId(), cutoffDate);
        BigDecimal totalIncrease = BigDecimal.ZERO;
        BigDecimal totalDecrease = BigDecimal.ZERO;
        for (AccountMovementSum sum : sums) {
            if (sum.getMovementDirection() == MovementDirection.INCREASE) {
                totalIncrease = totalIncrease.add(sum.getTotalAmount());
            } else if (sum.getMovementDirection() == MovementDirection.DECREASE) {
                totalDecrease = totalDecrease.add(sum.getTotalAmount());
            }
        }
        BigDecimal current = account.getOpeningBalance().add(totalIncrease).subtract(totalDecrease);
        return current.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
