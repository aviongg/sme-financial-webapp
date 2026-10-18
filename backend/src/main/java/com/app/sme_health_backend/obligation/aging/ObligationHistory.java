package com.app.sme_health_backend.obligation.aging;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical record of an obligation and its full adjustment ledger.
 *
 * @param obligationId   stable unique identifier for this obligation
 * @param direction      receivable or payable
 * @param issueDate      date the obligation was issued/originated
 * @param dueDate        optional due date for payment
 * @param originalAmount initial gross obligation amount
 * @param voidDate       optional date when this obligation was marked void
 * @param adjustments    chronological or un-ordered settlement adjustments
 */
public record ObligationHistory(
        String obligationId,
        ObligationDirection direction,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal originalAmount,
        LocalDate voidDate,
        List<SettlementAdjustment> adjustments
) {
    public ObligationHistory {
        adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
    }
}
