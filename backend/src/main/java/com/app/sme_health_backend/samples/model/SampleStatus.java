package com.app.sme_health_backend.samples.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Phase A planning lifecycle for a sample / production-cost activity record.
 * Archiving or closing never deletes activity metadata or approved plan history.
 */
public enum SampleStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    public boolean canTransitionTo(SampleStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    public Set<SampleStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(ACTIVE, CANCELLED, ARCHIVED);
            case ACTIVE -> EnumSet.of(COMPLETED, CANCELLED, ARCHIVED);
            case COMPLETED, CANCELLED -> EnumSet.of(ARCHIVED);
            case ARCHIVED -> EnumSet.noneOf(SampleStatus.class);
        };
    }

    public boolean allowsPlanningMutations() {
        return this == DRAFT || this == ACTIVE;
    }

    /**
     * An activity can only be physically deleted while still in DRAFT status and only when
     * no plan version has ever been approved (neither APPROVED nor SUPERSEDED exists).
     */
    public boolean canDeleteActivity(boolean hasApprovedOrSupersededPlan) {
        return this == DRAFT && !hasApprovedOrSupersededPlan;
    }
}
