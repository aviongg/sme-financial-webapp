package com.app.sme_health_backend.accounting.model;

public enum TransactionEventType {
    DIRECT_SALE_RECEIPT(MovementDirection.INCREASE, true, false),
    OPERATING_EXPENSE_PAYMENT(MovementDirection.DECREASE, false, true),
    INTERNAL_TRANSFER(MovementDirection.DECREASE, false, false),
    RECEIVABLE_COLLECTION(MovementDirection.INCREASE, false, false),
    PAYABLE_SETTLEMENT(MovementDirection.DECREASE, false, false);

    private final MovementDirection movementDirection;
    private final boolean revenue;
    private final boolean operatingExpense;

    TransactionEventType(MovementDirection movementDirection, boolean revenue, boolean operatingExpense) {
        this.movementDirection = movementDirection;
        this.revenue = revenue;
        this.operatingExpense = operatingExpense;
    }

    public MovementDirection getMovementDirection() {
        return movementDirection;
    }

    public boolean isRevenue() {
        return revenue;
    }

    public boolean isOperatingExpense() {
        return operatingExpense;
    }
}
