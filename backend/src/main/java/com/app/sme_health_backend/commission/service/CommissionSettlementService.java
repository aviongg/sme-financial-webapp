package com.app.sme_health_backend.commission.service;

import com.app.sme_health_backend.accounting.entity.AccountMovement;
import com.app.sme_health_backend.accounting.entity.FinancialAccount;
import com.app.sme_health_backend.accounting.entity.FinancialTransaction;
import com.app.sme_health_backend.accounting.model.AccountStatus;
import com.app.sme_health_backend.accounting.model.MovementDirection;
import com.app.sme_health_backend.accounting.model.TransactionEventType;
import com.app.sme_health_backend.accounting.repository.AccountMovementRepository;
import com.app.sme_health_backend.accounting.repository.FinancialAccountRepository;
import com.app.sme_health_backend.accounting.repository.FinancialTransactionRepository;
import com.app.sme_health_backend.commission.dto.*;
import com.app.sme_health_backend.commission.entity.CommissionAdvance;
import com.app.sme_health_backend.commission.entity.CommissionEarning;
import com.app.sme_health_backend.commission.entity.CommissionPayout;
import com.app.sme_health_backend.commission.entity.SalespersonProfile;
import com.app.sme_health_backend.commission.exception.CommissionSettlementConflictException;
import com.app.sme_health_backend.commission.exception.CommissionSettlementNotFoundException;
import com.app.sme_health_backend.commission.exception.CommissionSettlementValidationException;
import com.app.sme_health_backend.commission.model.*;
import com.app.sme_health_backend.commission.repository.*;
import com.app.sme_health_backend.counterparty.Counterparty;
import com.app.sme_health_backend.counterparty.CounterpartyRepository;
import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.model.MembershipRole;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommissionSettlementService {

    private static final ZoneId KARACHI_ZONE = ZoneId.of("Asia/Karachi");

    private final CommissionEarningRepository earningRepository;
    private final CommissionAdvanceRepository advanceRepository;
    private final CommissionPayoutRepository payoutRepository;
    private final SalespersonProfileRepository salespersonRepository;
    private final CommissionRuleRepository ruleRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final AccountMovementRepository movementRepository;
    private final BusinessAuthorizationService authService;

    public CommissionSettlementService(
            CommissionEarningRepository earningRepository,
            CommissionAdvanceRepository advanceRepository,
            CommissionPayoutRepository payoutRepository,
            SalespersonProfileRepository salespersonRepository,
            CommissionRuleRepository ruleRepository,
            CounterpartyRepository counterpartyRepository,
            FinancialAccountRepository accountRepository,
            FinancialTransactionRepository transactionRepository,
            AccountMovementRepository movementRepository,
            BusinessAuthorizationService authService
    ) {
        this.earningRepository = earningRepository;
        this.advanceRepository = advanceRepository;
        this.payoutRepository = payoutRepository;
        this.salespersonRepository = salespersonRepository;
        this.ruleRepository = ruleRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.movementRepository = movementRepository;
        this.authService = authService;
    }

    private void checkPermission(BusinessAccessContext context, BusinessPermission permission) {
        if (!authService.hasRolePermission(context.role(), permission)) {
            throw new AccessDeniedException("Role " + context.role() + " does not have permission: " + permission);
        }
    }

    @Transactional
    public CommissionEarningResponse approveEarning(BusinessAccessContext context, UUID earningId, CommissionApprovalRequest request) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        CommissionEarning earning = earningRepository.findForUpdateByIdAndBusinessId(earningId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Commission earning not found: " + earningId));

        if (earning.getStatus() != CommissionEarningStatus.EARNED) {
            throw new CommissionSettlementConflictException("Only EARNED commissions can be approved. Current status: " + earning.getStatus());
        }

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(earning.getSalespersonId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found"));

        if (salesperson.getUserId() != null && salesperson.getUserId().equals(context.userId())) {
            throw new AccessDeniedException("Salespeople cannot approve their own commissions");
        }

        BigDecimal approvedAmount = earning.getCommissionAmount();
        if (request != null && request.approvedAmount() != null) {
            if (request.approvedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new CommissionSettlementValidationException("Approved amount must be positive");
            }
            if (request.approvedAmount().compareTo(earning.getCommissionAmount()) > 0) {
                throw new CommissionSettlementValidationException("Approved amount (" + request.approvedAmount() +
                        ") cannot exceed earned commission amount (" + earning.getCommissionAmount() + ")");
            }
            approvedAmount = request.approvedAmount().setScale(2, RoundingMode.HALF_UP);
        }

        earning.setStatus(CommissionEarningStatus.APPROVED);
        earning.setApprovedAmount(approvedAmount);
        earning.setApprovedAt(Instant.now());
        earning.setApprovedByUserId(context.userId());
        if (request != null && request.notes() != null && !request.notes().isBlank()) {
            earning.setNotes((earning.getNotes() != null ? earning.getNotes() + " | " : "") + request.notes().trim());
        }

        CommissionEarning saved = earningRepository.save(earning);
        return mapEarningResponse(saved, salesperson.getDisplayName());
    }

    @Transactional
    public CommissionAdvanceResponse createAdvance(BusinessAccessContext context, String idempotencyKey, CommissionAdvanceCreateRequest request) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CommissionAdvance> existing = advanceRepository.findByBusinessIdAndIdempotencyKey(context.businessId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(existing.get().getSalespersonId(), context.businessId())
                        .orElse(null);
                return mapAdvanceResponse(existing.get(), sp != null ? sp.getDisplayName() : "Unknown");
            }
        }

        if (request.salespersonId() == null) throw new CommissionSettlementValidationException("Salesperson ID is required");
        if (request.accountId() == null) throw new CommissionSettlementValidationException("Account ID is required");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CommissionSettlementValidationException("Advance amount must be positive");
        }
        if (request.paymentDate() == null) throw new CommissionSettlementValidationException("Payment date is required");

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(request.salespersonId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found"));

        if (salesperson.getStatus() != SalespersonStatus.ACTIVE) {
            throw new CommissionSettlementConflictException("Cannot issue advance to inactive/suspended salesperson");
        }

        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CommissionSettlementConflictException("Financial account is not active");
        }
        if (!"PKR".equalsIgnoreCase(account.getCurrency())) {
            throw new CommissionSettlementValidationException("Account currency must be PKR");
        }
        if (request.paymentDate().isBefore(account.getOpeningDate())) {
            throw new CommissionSettlementValidationException("Payment date cannot precede account opening date (" + account.getOpeningDate() + ")");
        }

        BigDecimal roundedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                MovementDirection.DECREASE,
                roundedAmount,
                "PKR",
                roundedAmount,
                request.paymentDate(),
                "Commission advance to " + salesperson.getDisplayName(),
                context.userId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        AccountMovement movement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                account.getId(),
                MovementDirection.DECREASE,
                roundedAmount,
                "PKR",
                request.paymentDate()
        );
        movementRepository.saveAndFlush(movement);

        CommissionAdvance advance = new CommissionAdvance(
                context.businessId(),
                salesperson.getId(),
                roundedAmount,
                BigDecimal.ZERO,
                "PKR",
                request.paymentDate(),
                account.getId(),
                tx.getId(),
                CommissionAdvanceStatus.ACTIVE,
                request.notes() != null ? request.notes().trim() : null,
                idempotencyKey != null ? idempotencyKey.trim() : null,
                context.userId()
        );
        CommissionAdvance saved = advanceRepository.save(advance);

        return mapAdvanceResponse(saved, salesperson.getDisplayName());
    }

    @Transactional
    public CommissionPayoutResponse applyAdvance(BusinessAccessContext context, String idempotencyKey, UUID earningId, CommissionAdvanceApplyRequest request) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CommissionPayout> existing = payoutRepository.findByBusinessIdAndIdempotencyKey(context.businessId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(existing.get().getSalespersonId(), context.businessId())
                        .orElse(null);
                return mapPayoutResponse(existing.get(), sp != null ? sp.getDisplayName() : "Unknown");
            }
        }

        if (request.advanceId() == null) throw new CommissionSettlementValidationException("Advance ID is required");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CommissionSettlementValidationException("Application amount must be positive");
        }

        CommissionEarning earning = earningRepository.findForUpdateByIdAndBusinessId(earningId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Commission earning not found: " + earningId));

        if (earning.getStatus() != CommissionEarningStatus.APPROVED && earning.getStatus() != CommissionEarningStatus.PARTIALLY_SETTLED) {
            throw new CommissionSettlementConflictException("Advance can only be applied to APPROVED or PARTIALLY_SETTLED commissions. Current: " + earning.getStatus());
        }

        CommissionAdvance advance = advanceRepository.findForUpdateByIdAndBusinessId(request.advanceId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Commission advance not found: " + request.advanceId()));

        if (!advance.getSalespersonId().equals(earning.getSalespersonId())) {
            throw new CommissionSettlementConflictException("Advance belongs to a different salesperson");
        }

        if (advance.getStatus() != CommissionAdvanceStatus.ACTIVE) {
            throw new CommissionSettlementConflictException("Advance is not ACTIVE (current status: " + advance.getStatus() + ")");
        }

        BigDecimal applyAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal unapplied = advance.getUnappliedAmount();
        if (applyAmount.compareTo(unapplied) > 0) {
            throw new CommissionSettlementConflictException("Application amount (" + applyAmount +
                    ") exceeds unapplied advance balance (" + unapplied + ")");
        }

        BigDecimal approvedAmt = earning.getApprovedAmount() != null ? earning.getApprovedAmount() : earning.getCommissionAmount();
        BigDecimal currentSettled = payoutRepository.sumEffectiveSettlementsByEarning(context.businessId(), earning.getId());
        BigDecimal remainingPayable = approvedAmt.subtract(currentSettled);

        if (applyAmount.compareTo(remainingPayable) > 0) {
            throw new CommissionSettlementConflictException("Application amount (" + applyAmount +
                    ") exceeds remaining commission payable (" + remainingPayable + ")");
        }

        BigDecimal newApplied = advance.getAppliedAmount().add(applyAmount);
        advance.setAppliedAmount(newApplied);
        if (newApplied.compareTo(advance.getAmount()) >= 0) {
            advance.setStatus(CommissionAdvanceStatus.FULLY_APPLIED);
        }
        advanceRepository.save(advance);

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(earning.getSalespersonId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found"));

        CommissionPayout payout = new CommissionPayout(
                context.businessId(),
                salesperson.getId(),
                earning.getId(),
                CommissionPayoutType.ADVANCE_APPLICATION,
                applyAmount,
                "PKR",
                LocalDate.now(KARACHI_ZONE),
                null,
                null,
                advance.getId(),
                CommissionPayoutStatus.SETTLED,
                request.notes() != null ? request.notes().trim() : "Advance applied to commission",
                idempotencyKey != null ? idempotencyKey.trim() : null,
                context.userId()
        );
        CommissionPayout savedPayout = payoutRepository.save(payout);

        BigDecimal newSettledTotal = currentSettled.add(applyAmount);
        if (newSettledTotal.compareTo(approvedAmt) >= 0) {
            earning.setStatus(CommissionEarningStatus.SETTLED);
            earning.setPaidAt(Instant.now());
        } else {
            earning.setStatus(CommissionEarningStatus.PARTIALLY_SETTLED);
        }
        earningRepository.save(earning);

        return mapPayoutResponse(savedPayout, salesperson.getDisplayName());
    }

    @Transactional
    public CommissionPayoutResponse recordPayout(BusinessAccessContext context, String idempotencyKey, UUID earningId, CommissionPayoutCreateRequest request) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CommissionPayout> existing = payoutRepository.findByBusinessIdAndIdempotencyKey(context.businessId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(existing.get().getSalespersonId(), context.businessId())
                        .orElse(null);
                return mapPayoutResponse(existing.get(), sp != null ? sp.getDisplayName() : "Unknown");
            }
        }

        if (request.accountId() == null) throw new CommissionSettlementValidationException("Account ID is required");
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CommissionSettlementValidationException("Payout amount must be positive");
        }
        if (request.paymentDate() == null) throw new CommissionSettlementValidationException("Payment date is required");

        CommissionEarning earning = earningRepository.findForUpdateByIdAndBusinessId(earningId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Commission earning not found: " + earningId));

        if (earning.getStatus() != CommissionEarningStatus.APPROVED && earning.getStatus() != CommissionEarningStatus.PARTIALLY_SETTLED) {
            throw new CommissionSettlementConflictException("Payouts can only be recorded against APPROVED or PARTIALLY_SETTLED commissions. Current: " + earning.getStatus());
        }

        BigDecimal payoutAmount = request.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal approvedAmt = earning.getApprovedAmount() != null ? earning.getApprovedAmount() : earning.getCommissionAmount();
        BigDecimal currentSettled = payoutRepository.sumEffectiveSettlementsByEarning(context.businessId(), earning.getId());
        BigDecimal remainingPayable = approvedAmt.subtract(currentSettled);

        if (payoutAmount.compareTo(remainingPayable) > 0) {
            throw new CommissionSettlementConflictException("Payout amount (" + payoutAmount +
                    ") exceeds remaining payable balance (" + remainingPayable + ")");
        }

        FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(request.accountId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Financial account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CommissionSettlementConflictException("Financial account is not active");
        }
        if (!"PKR".equalsIgnoreCase(account.getCurrency())) {
            throw new CommissionSettlementValidationException("Account currency must be PKR");
        }
        if (request.paymentDate().isBefore(account.getOpeningDate())) {
            throw new CommissionSettlementValidationException("Payment date cannot precede account opening date (" + account.getOpeningDate() + ")");
        }

        SalespersonProfile salesperson = salespersonRepository.findByIdAndBusinessId(earning.getSalespersonId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found"));

        FinancialTransaction tx = new FinancialTransaction(
                context.businessId(),
                account.getId(),
                TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                MovementDirection.DECREASE,
                payoutAmount,
                "PKR",
                payoutAmount,
                request.paymentDate(),
                "Commission payout to " + salesperson.getDisplayName(),
                context.userId()
        );
        tx = transactionRepository.saveAndFlush(tx);

        AccountMovement movement = new AccountMovement(
                context.businessId(),
                tx.getId(),
                account.getId(),
                MovementDirection.DECREASE,
                payoutAmount,
                "PKR",
                request.paymentDate()
        );
        movementRepository.saveAndFlush(movement);

        CommissionPayout payout = new CommissionPayout(
                context.businessId(),
                salesperson.getId(),
                earning.getId(),
                CommissionPayoutType.PAYOUT,
                payoutAmount,
                "PKR",
                request.paymentDate(),
                account.getId(),
                tx.getId(),
                null,
                CommissionPayoutStatus.SETTLED,
                request.notes() != null ? request.notes().trim() : "Direct commission payout",
                idempotencyKey != null ? idempotencyKey.trim() : null,
                context.userId()
        );
        CommissionPayout savedPayout = payoutRepository.save(payout);

        BigDecimal newSettledTotal = currentSettled.add(payoutAmount);
        if (newSettledTotal.compareTo(approvedAmt) >= 0) {
            earning.setStatus(CommissionEarningStatus.SETTLED);
            earning.setPaidAt(Instant.now());
        } else {
            earning.setStatus(CommissionEarningStatus.PARTIALLY_SETTLED);
        }
        earningRepository.save(earning);

        return mapPayoutResponse(savedPayout, salesperson.getDisplayName());
    }

    @Transactional
    public CommissionPayoutResponse reversePayout(BusinessAccessContext context, String idempotencyKey, UUID payoutId, CommissionPayoutReverseRequest request) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new CommissionSettlementValidationException("Reversal reason is required");
        }

        CommissionPayout payout = payoutRepository.findForUpdateByIdAndBusinessId(payoutId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Commission payout not found: " + payoutId));

        if (payout.getStatus() != CommissionPayoutStatus.SETTLED) {
            throw new CommissionSettlementConflictException("Payout is already reversed");
        }

        CommissionEarning earning = earningRepository.findForUpdateByIdAndBusinessId(payout.getEarningId(), context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Earning not found"));

        if (payout.getPayoutType() == CommissionPayoutType.PAYOUT) {
            FinancialAccount account = accountRepository.findForUpdateByIdAndBusinessId(payout.getFinancialAccountId(), context.businessId())
                    .orElseThrow(() -> new CommissionSettlementNotFoundException("Source account not found"));

            FinancialTransaction revTx = new FinancialTransaction(
                    context.businessId(),
                    account.getId(),
                    TransactionEventType.OPERATING_EXPENSE_PAYMENT,
                    MovementDirection.INCREASE,
                    payout.getAmount(),
                    "PKR",
                    payout.getAmount(),
                    LocalDate.now(KARACHI_ZONE),
                    "Reversal of commission payout: " + request.reason().trim(),
                    context.userId()
            );
            revTx.setReversedTransactionId(payout.getFinancialTransactionId());
            revTx.setReversalReason(request.reason().trim());
            revTx = transactionRepository.saveAndFlush(revTx);

            AccountMovement revMov = new AccountMovement(
                    context.businessId(),
                    revTx.getId(),
                    account.getId(),
                    MovementDirection.INCREASE,
                    payout.getAmount(),
                    "PKR",
                    LocalDate.now(KARACHI_ZONE)
            );
            movementRepository.saveAndFlush(revMov);

            payout.setStatus(CommissionPayoutStatus.REVERSED);
            payout.setReversalTransactionId(revTx.getId());
            payout.setReversalReason(request.reason().trim());
        } else if (payout.getPayoutType() == CommissionPayoutType.ADVANCE_APPLICATION) {
            CommissionAdvance advance = advanceRepository.findForUpdateByIdAndBusinessId(payout.getCommissionAdvanceId(), context.businessId())
                    .orElseThrow(() -> new CommissionSettlementNotFoundException("Advance not found"));

            BigDecimal newApplied = advance.getAppliedAmount().subtract(payout.getAmount());
            if (newApplied.compareTo(BigDecimal.ZERO) < 0) newApplied = BigDecimal.ZERO;
            advance.setAppliedAmount(newApplied);
            if (advance.getStatus() == CommissionAdvanceStatus.FULLY_APPLIED) {
                advance.setStatus(CommissionAdvanceStatus.ACTIVE);
            }
            advanceRepository.save(advance);

            payout.setStatus(CommissionPayoutStatus.REVERSED);
            payout.setReversalReason(request.reason().trim());
        }

        payoutRepository.save(payout);

        BigDecimal remainingSettled = payoutRepository.sumEffectiveSettlementsByEarning(context.businessId(), earning.getId());
        if (remainingSettled.compareTo(BigDecimal.ZERO) <= 0) {
            earning.setStatus(CommissionEarningStatus.APPROVED);
            earning.setPaidAt(null);
        } else {
            BigDecimal approvedAmt = earning.getApprovedAmount() != null ? earning.getApprovedAmount() : earning.getCommissionAmount();
            if (remainingSettled.compareTo(approvedAmt) >= 0) {
                earning.setStatus(CommissionEarningStatus.SETTLED);
            } else {
                earning.setStatus(CommissionEarningStatus.PARTIALLY_SETTLED);
                earning.setPaidAt(null);
            }
        }
        earningRepository.save(earning);

        SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(payout.getSalespersonId(), context.businessId())
                .orElse(null);
        return mapPayoutResponse(payout, sp != null ? sp.getDisplayName() : "Unknown");
    }

    @Transactional(readOnly = true)
    public List<CommissionAdvanceResponse> listAdvances(BusinessAccessContext context, UUID salespersonIdFilter) {
        resolveCallerScoping(context, salespersonIdFilter);
        UUID effectiveSpId = resolveSalespersonId(context, salespersonIdFilter);

        List<CommissionAdvance> advances = (effectiveSpId != null)
                ? advanceRepository.findByBusinessIdAndSalespersonId(context.businessId(), effectiveSpId)
                : advanceRepository.findByBusinessId(context.businessId());

        Map<UUID, String> spNames = getSalespersonNames(context.businessId(), advances.stream().map(CommissionAdvance::getSalespersonId).collect(Collectors.toSet()));

        return advances.stream()
                .map(a -> mapAdvanceResponse(a, spNames.getOrDefault(a.getSalespersonId(), "Unknown")))
                .sorted(Comparator.comparing(CommissionAdvanceResponse::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommissionPayoutResponse> listPayoutsForEarning(BusinessAccessContext context, UUID earningId) {
        CommissionEarning earning = earningRepository.findByIdAndBusinessId(earningId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Earning not found: " + earningId));

        resolveCallerScoping(context, earning.getSalespersonId());

        List<CommissionPayout> payouts = payoutRepository.findByBusinessIdAndEarningId(context.businessId(), earningId);
        SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(earning.getSalespersonId(), context.businessId())
                .orElse(null);
        String spName = sp != null ? sp.getDisplayName() : "Unknown";

        return payouts.stream()
                .map(p -> mapPayoutResponse(p, spName))
                .sorted(Comparator.comparing(CommissionPayoutResponse::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommissionPayoutResponse> listPayouts(BusinessAccessContext context, UUID salespersonIdFilter) {
        resolveCallerScoping(context, salespersonIdFilter);
        UUID effectiveSpId = resolveSalespersonId(context, salespersonIdFilter);

        List<CommissionPayout> payouts = (effectiveSpId != null)
                ? payoutRepository.findByBusinessIdAndSalespersonId(context.businessId(), effectiveSpId)
                : payoutRepository.findByBusinessId(context.businessId());

        Map<UUID, String> spNames = getSalespersonNames(context.businessId(), payouts.stream().map(CommissionPayout::getSalespersonId).collect(Collectors.toSet()));

        return payouts.stream()
                .map(p -> mapPayoutResponse(p, spNames.getOrDefault(p.getSalespersonId(), "Unknown")))
                .sorted(Comparator.comparing(CommissionPayoutResponse::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CommissionEarningResponse> listPendingApprovals(BusinessAccessContext context) {
        checkPermission(context, BusinessPermission.COMMISSION_MANAGE);

        List<CommissionEarning> pending = earningRepository.findByBusinessIdAndStatus(context.businessId(), CommissionEarningStatus.EARNED);
        Map<UUID, String> spNames = getSalespersonNames(context.businessId(), pending.stream().map(CommissionEarning::getSalespersonId).collect(Collectors.toSet()));

        return pending.stream()
                .map(e -> mapEarningResponse(e, spNames.getOrDefault(e.getSalespersonId(), "Unknown")))
                .sorted(Comparator.comparing(CommissionEarningResponse::createdAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SalespersonSettlementSummaryResponse getSalespersonSettlementSummary(BusinessAccessContext context, UUID salespersonIdFilter) {
        resolveCallerScoping(context, salespersonIdFilter);
        UUID targetSpId = resolveSalespersonId(context, salespersonIdFilter);

        if (targetSpId == null) {
            throw new CommissionSettlementValidationException("Salesperson ID is required for settlement summary");
        }

        SalespersonProfile sp = salespersonRepository.findByIdAndBusinessId(targetSpId, context.businessId())
                .orElseThrow(() -> new CommissionSettlementNotFoundException("Salesperson not found"));

        List<CommissionEarning> earnings = earningRepository.findByBusinessIdAndSalespersonId(context.businessId(), targetSpId);
        List<CommissionAdvance> advances = advanceRepository.findByBusinessIdAndSalespersonId(context.businessId(), targetSpId);
        List<CommissionPayout> payouts = payoutRepository.findByBusinessIdAndSalespersonId(context.businessId(), targetSpId);

        BigDecimal totalEarned = earnings.stream()
                .filter(e -> e.getStatus() != CommissionEarningStatus.VOID && e.getStatus() != CommissionEarningStatus.REVERSED)
                .map(CommissionEarning::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalApproved = earnings.stream()
                .filter(e -> e.getStatus() == CommissionEarningStatus.APPROVED || e.getStatus() == CommissionEarningStatus.PARTIALLY_SETTLED || e.getStatus() == CommissionEarningStatus.SETTLED || e.getStatus() == CommissionEarningStatus.PAID)
                .map(e -> e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAdvancesPaid = advanceRepository.sumTotalAdvancesBySalesperson(context.businessId(), targetSpId);
        BigDecimal totalAdvancesApplied = advanceRepository.sumTotalAdvancesAppliedBySalesperson(context.businessId(), targetSpId);
        BigDecimal unappliedAdvanceBalance = totalAdvancesPaid.subtract(totalAdvancesApplied);
        if (unappliedAdvanceBalance.compareTo(BigDecimal.ZERO) < 0) unappliedAdvanceBalance = BigDecimal.ZERO;

        BigDecimal totalCashPaid = payoutRepository.sumTotalCashPayoutsBySalesperson(context.businessId(), targetSpId);
        BigDecimal totalSettled = payoutRepository.sumEffectiveSettlementsBySalesperson(context.businessId(), targetSpId);

        BigDecimal outstandingPayable = totalApproved.subtract(totalSettled);
        if (outstandingPayable.compareTo(BigDecimal.ZERO) < 0) outstandingPayable = BigDecimal.ZERO;

        List<CommissionEarningResponse> earningDtos = earnings.stream()
                .map(e -> mapEarningResponse(e, sp.getDisplayName()))
                .collect(Collectors.toList());

        List<CommissionAdvanceResponse> advanceDtos = advances.stream()
                .map(a -> mapAdvanceResponse(a, sp.getDisplayName()))
                .collect(Collectors.toList());

        List<CommissionPayoutResponse> payoutDtos = payouts.stream()
                .map(p -> mapPayoutResponse(p, sp.getDisplayName()))
                .collect(Collectors.toList());

        return new SalespersonSettlementSummaryResponse(
                sp.getId(),
                sp.getDisplayName(),
                totalEarned,
                totalApproved,
                totalAdvancesPaid,
                totalAdvancesApplied,
                unappliedAdvanceBalance,
                totalCashPaid,
                outstandingPayable,
                earningDtos,
                advanceDtos,
                payoutDtos,
                "PKR"
        );
    }

    private void resolveCallerScoping(BusinessAccessContext context, UUID salespersonIdFilter) {
        if (context.role() == MembershipRole.SALESPERSON) {
            SalespersonProfile profile = salespersonRepository.findByBusinessIdAndUserId(context.businessId(), context.userId())
                    .orElseThrow(() -> new AccessDeniedException("No salesperson profile linked to active user"));
            if (salespersonIdFilter != null && !salespersonIdFilter.equals(profile.getId())) {
                throw new AccessDeniedException("Salespeople can only access their own commission records");
            }
        }
    }

    private UUID resolveSalespersonId(BusinessAccessContext context, UUID salespersonIdFilter) {
        if (context.role() == MembershipRole.SALESPERSON) {
            return salespersonRepository.findByBusinessIdAndUserId(context.businessId(), context.userId())
                    .map(SalespersonProfile::getId)
                    .orElse(null);
        }
        return salespersonIdFilter;
    }

    private Map<UUID, String> getSalespersonNames(UUID businessId, Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return salespersonRepository.findAllById(ids).stream()
                .filter(s -> s.getBusinessId().equals(businessId))
                .collect(Collectors.toMap(SalespersonProfile::getId, SalespersonProfile::getDisplayName));
    }

    public CommissionEarningResponse mapEarningResponse(CommissionEarning e, String salespersonName) {
        BigDecimal approvedAmt = e.getApprovedAmount() != null ? e.getApprovedAmount() : e.getCommissionAmount();
        BigDecimal settledAmt = payoutRepository != null ? payoutRepository.sumEffectiveSettlementsByEarning(e.getBusinessId(), e.getId()) : BigDecimal.ZERO;
        if (settledAmt == null) settledAmt = BigDecimal.ZERO;
        BigDecimal remaining = approvedAmt.subtract(settledAmt);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        String ruleName = ruleRepository.findById(e.getRuleId()).map(r -> r.getRuleName()).orElse("Default Rule");
        String cpName = counterpartyRepository.findById(e.getCounterpartyId()).map(Counterparty::getDisplayName).orElse("Customer");

        return new CommissionEarningResponse(
                e.getId(),
                e.getBusinessId(),
                e.getSalespersonId(),
                salespersonName,
                e.getRuleId(),
                ruleName,
                e.getCounterpartyId(),
                cpName,
                e.getSourceType(),
                e.getSourceId(),
                e.getSourceFingerprint(),
                e.getSaleAmount(),
                e.getCommissionRateSnapshot(),
                e.getCommissionAmount(),
                e.getApprovedAmount(),
                settledAmt,
                remaining,
                e.getCurrency(),
                e.getStatus(),
                e.getApprovedAt(),
                e.getApprovedByUserId(),
                e.getPaidAt(),
                e.getNotes(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public CommissionAdvanceResponse mapAdvanceResponse(CommissionAdvance a, String salespersonName) {
        return new CommissionAdvanceResponse(
                a.getId(),
                a.getBusinessId(),
                a.getSalespersonId(),
                salespersonName,
                a.getAmount(),
                a.getAppliedAmount(),
                a.getUnappliedAmount(),
                a.getCurrency(),
                a.getPaymentDate(),
                a.getFinancialAccountId(),
                a.getFinancialTransactionId(),
                a.getStatus(),
                a.getNotes(),
                a.getCreatedAt()
        );
    }

    public CommissionPayoutResponse mapPayoutResponse(CommissionPayout p, String salespersonName) {
        return new CommissionPayoutResponse(
                p.getId(),
                p.getBusinessId(),
                p.getSalespersonId(),
                salespersonName,
                p.getEarningId(),
                p.getPayoutType(),
                p.getAmount(),
                p.getCurrency(),
                p.getPaymentDate(),
                p.getFinancialAccountId(),
                p.getFinancialTransactionId(),
                p.getCommissionAdvanceId(),
                p.getStatus(),
                p.getReversalTransactionId(),
                p.getReversalReason(),
                p.getNotes(),
                p.getCreatedAt()
        );
    }
}
