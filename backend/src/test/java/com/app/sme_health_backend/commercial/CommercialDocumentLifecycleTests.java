package com.app.sme_health_backend.commercial;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.model.CommercialDocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommercialDocumentLifecycleTests {
    @Test
    void phaseALifecycleIsDraftFinalizedVoidOnly() {
        assertEquals(List.of("DRAFT", "FINALIZED", "VOID"),
                Arrays.stream(CommercialDocumentStatus.values()).map(Enum::name).toList(),
                "ISSUED/APPROVED stay reserved for future C04 integration");
        assertTrue(CommercialDocumentStatus.DRAFT.isEditable());
        assertTrue(CommercialDocumentStatus.DRAFT.canFinalize());
        assertTrue(CommercialDocumentStatus.DRAFT.canDelete());
        assertFalse(CommercialDocumentStatus.DRAFT.canVoid(), "Drafts are deleted, never voided");
        for (CommercialDocumentStatus frozen : List.of(CommercialDocumentStatus.FINALIZED, CommercialDocumentStatus.VOID)) {
            assertFalse(frozen.isEditable());
            assertFalse(frozen.canFinalize());
            assertFalse(frozen.canDelete());
        }
        assertTrue(CommercialDocumentStatus.FINALIZED.canVoid());
        assertFalse(CommercialDocumentStatus.VOID.canVoid());
    }

    @Test
    void numbersAreSixDigitAndPrefixedPerKind() {
        assertEquals("INV-000001", CommercialDocumentKind.INVOICE.formatNumber(1));
        assertEquals("BILL-000001", CommercialDocumentKind.BILL.formatNumber(1));
        assertEquals("INV-999999", CommercialDocumentKind.INVOICE.formatNumber(999_999));
        assertEquals("BILL-000042", CommercialDocumentKind.BILL.formatNumber(42));
    }
}
