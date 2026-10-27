package com.app.sme_health_backend.projects;

import com.app.sme_health_backend.projects.model.ProjectBudgetStatus;
import com.app.sme_health_backend.projects.model.ProjectStatus;
import org.junit.jupiter.api.Test;

import static com.app.sme_health_backend.projects.model.ProjectStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLifecycleTests {

    @Test
    void projectStatusTransitionsPreserveCloseAndArchiveInvariants() {
        assertTrue(DRAFT.canTransitionTo(ACTIVE));
        assertTrue(DRAFT.canTransitionTo(CANCELLED));
        assertTrue(DRAFT.canTransitionTo(ARCHIVED));
        assertFalse(DRAFT.canTransitionTo(COMPLETED));
        assertFalse(DRAFT.canTransitionTo(DRAFT));

        assertTrue(ACTIVE.canTransitionTo(COMPLETED));
        assertTrue(ACTIVE.canTransitionTo(CANCELLED));
        assertTrue(ACTIVE.canTransitionTo(ARCHIVED));
        assertFalse(ACTIVE.canTransitionTo(DRAFT));

        assertTrue(COMPLETED.canTransitionTo(ARCHIVED));
        assertFalse(COMPLETED.canTransitionTo(ACTIVE));
        assertFalse(COMPLETED.canTransitionTo(DRAFT));

        assertTrue(CANCELLED.canTransitionTo(ARCHIVED));
        assertFalse(CANCELLED.canTransitionTo(ACTIVE));

        for (ProjectStatus target : ProjectStatus.values()) {
            assertFalse(ARCHIVED.canTransitionTo(target));
        }

        assertTrue(DRAFT.allowsPlanningMutations());
        assertTrue(ACTIVE.allowsPlanningMutations());
        assertFalse(COMPLETED.allowsPlanningMutations());
        assertFalse(CANCELLED.allowsPlanningMutations());
        assertFalse(ARCHIVED.allowsPlanningMutations());
    }

    @Test
    void budgetStatusMarksApprovedAndSupersededVersionsImmutableAndOnlyDraftDeletable() {
        assertFalse(ProjectBudgetStatus.DRAFT.isImmutable());
        assertTrue(ProjectBudgetStatus.APPROVED.isImmutable());
        assertTrue(ProjectBudgetStatus.SUPERSEDED.isImmutable());

        assertTrue(ProjectBudgetStatus.DRAFT.canDeleteDraft());
        assertFalse(ProjectBudgetStatus.APPROVED.canDeleteDraft());
        assertFalse(ProjectBudgetStatus.SUPERSEDED.canDeleteDraft());

        assertTrue(DRAFT.canDeleteProject(false));
        assertFalse(DRAFT.canDeleteProject(true));
        assertFalse(ACTIVE.canDeleteProject(false));
        assertFalse(COMPLETED.canDeleteProject(false));
        assertFalse(CANCELLED.canDeleteProject(false));
        assertFalse(ARCHIVED.canDeleteProject(false));
    }
}
