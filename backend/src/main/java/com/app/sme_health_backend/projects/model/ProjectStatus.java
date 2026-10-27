package com.app.sme_health_backend.projects.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Phase A planning lifecycle for a project/job/order record.
 * Archiving or closing never deletes project metadata or approved budget history.
 */
public enum ProjectStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    ARCHIVED;

    public boolean canTransitionTo(ProjectStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return allowedTargets().contains(target);
    }

    public Set<ProjectStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> EnumSet.of(ACTIVE, CANCELLED, ARCHIVED);
            case ACTIVE -> EnumSet.of(COMPLETED, CANCELLED, ARCHIVED);
            case COMPLETED, CANCELLED -> EnumSet.of(ARCHIVED);
            case ARCHIVED -> EnumSet.noneOf(ProjectStatus.class);
        };
    }

    public boolean allowsPlanningMutations() {
        return this == DRAFT || this == ACTIVE;
    }

    /**
     * A project can only be physically deleted while still in DRAFT status and only when
     * no budget version has ever been approved (neither APPROVED nor SUPERSEDED exists).
     */
    public boolean canDeleteProject(boolean hasApprovedOrSupersededBudget) {
        return this == DRAFT && !hasApprovedOrSupersededBudget;
    }
}
