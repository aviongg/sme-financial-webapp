package com.app.sme_health_backend.zakat;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.shared.exception.ResourceNotFoundException;
import com.app.sme_health_backend.zakat.dto.MonthlyRecordZakatRequest;
import com.app.sme_health_backend.zakat.dto.ZakatPreviewResponse;
import com.app.sme_health_backend.zakat.service.ZakatCalculationService;
import com.app.sme_health_backend.zakat.service.ZakatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.app.sme_health_backend.zakat.ZakatTestFixtures.*;
import static com.app.sme_health_backend.zakat.dto.ZakatPreviewRequest.*;
import static com.app.sme_health_backend.zakat.dto.ZakatTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZakatServiceTests {
    @Mock
    private MonthlyRecordRepository repository;

    private ZakatService service;
    private final UUID userId = UUID.fromString("a8fa09ed-7e46-4b50-94ea-91bebfa5d772");
    private MonthlyRecord record;

    @BeforeEach
    void setUp() {
        service = new ZakatService(repository, new ZakatCalculationService());
        record = new MonthlyRecord();
        record.setUserId(userId);
        record.setMonth("2026-09");
        record.setCashBalanceEom(money("100000"));
        record.setInventoryValue(money("5000"));
        record.setReceivablesOutstanding(money("10000"));
        record.setPayablesOutstanding(money("1000"));
        record.setLoanOutstanding(money("30000"));
        record.setInterestExpense(money("500"));
        record.setFinancingType("conventional");
    }

    @Test
    void savedAndManualInputsUseSameRulesAndRepositoryIsReadOnly() {
        stubRecord();
        ZakatPreviewResponse saved = service.previewMonthlyRecord(userId, "2026-09", supplements(new Fixture()));
        ZakatPreviewResponse manual = service.preview(saved.inputs());
        assertEquals(manual.calculationStatus(), saved.calculationStatus());
        assertEquals(manual.grossZakatableAssets(), saved.grossZakatableAssets());
        assertEquals(manual.deductibleLiabilities(), saved.deductibleLiabilities());
        assertEquals(manual.netZakatableAssets(), saved.netZakatableAssets());
        assertEquals(manual.zakatDue(), saved.zakatDue());
        assertEquals(manual.assetBreakdown(), saved.assetBreakdown());
        assertEquals(manual.liabilityBreakdown(), saved.liabilityBreakdown());
        assertEquals(manual.warnings(), saved.warnings());
        assertEquals(money("2825.00"), saved.zakatDue());
        assertEquals(userId, saved.source().userId());
        assertEquals("2026-09", saved.source().month());
        assertEquals(new Fixture().date, saved.source().balancesDate());
        assertEquals(money("30000"), record.getLoanOutstanding());
        assertEquals(money("5000"), record.getInventoryValue());
        verify(repository).findByUserIdAndMonth(userId, "2026-09");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void manualCalculationDoesNotQueryRepository() {
        service.preview(new Fixture().request());
        verifyNoInteractions(repository);
    }

    @Test
    void monthlyBalancesDoNotEstablishHaul() {
        stubRecord();
        Fixture fixture = new Fixture();
        fixture.haul = HaulStatus.UNKNOWN;
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", supplements(fixture));
        assertEquals(CalculationStatus.INCOMPLETE_HAUL_CONFIRMATION_REQUIRED, result.calculationStatus());
        assertNull(result.zakatDue());
        verify(repository).findByUserIdAndMonth(userId, "2026-09");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void flagsAssessmentDateAgainstMonthEndRatherThanUpdatedAt() {
        stubRecord();
        Fixture fixture = new Fixture();
        fixture.date = fixture.date.minusDays(1);
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", supplements(fixture));
        assertTrue(result.warnings().contains("ASSESSMENT_DATE_DIFFERS_FROM_RECORD_MONTH_END"));
        assertTrue(result.warnings().contains("PRICE_DATE_DIFFERS_FROM_ASSESSMENT_DATE"));
    }

    @Test
    void requiresValuationAndClassificationsForPositiveRecordedBalances() {
        stubRecord();
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", minimalSupplements());
        assertEquals(CalculationStatus.INCOMPLETE, result.calculationStatus());
        assertTrue(result.missingFields().containsAll(List.of("assets.inventory", "assets.receivables",
                "liabilities.currentPayables", "liabilities.principalDueWithin12LunarMonths")));
        assertNull(result.zakatDue());
    }

    @Test
    void nullSavedFinancialFieldsStayUnknown() {
        record.setInventoryValue(null);
        record.setReceivablesOutstanding(null);
        record.setPayablesOutstanding(null);
        record.setLoanOutstanding(null);
        stubRecord();
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", minimalSupplements());
        assertEquals(CalculationStatus.INCOMPLETE, result.calculationStatus());
        assertTrue(result.missingFields().containsAll(List.of("assets.inventory", "assets.receivables",
                "liabilities.accountsPayable", "financing.loanOutstanding")));
        assertNull(result.netZakatableAssets());
        assertNull(result.source().inventoryValue());
    }

    @Test
    void explicitRecordedZerosNeedNoAdditionalAmounts() {
        record.setInventoryValue(BigDecimal.ZERO);
        record.setReceivablesOutstanding(BigDecimal.ZERO);
        record.setPayablesOutstanding(BigDecimal.ZERO);
        record.setLoanOutstanding(BigDecimal.ZERO);
        stubRecord();
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", minimalSupplements());
        assertEquals(CalculationStatus.CALCULATED, result.calculationStatus());
        assertEquals(money("2500.00"), result.zakatDue());
    }

    @Test
    void rejectsPartialReceivablesAndCannotDropThemWithEmptyList() {
        stubRecord();
        MonthlyRecordZakatRequest input = supplements(new Fixture());
        for (List<Receivable> entries : List.of(List.<Receivable>of(),
                List.of(new Receivable("invoice", money("9999"), ReceivableClassification.GOOD)))) {
            MonthlyRecordZakatRequest request = new MonthlyRecordZakatRequest(input.assessment(), input.inventory(),
                    entries, input.currentPayables(), input.principalDueWithin12LunarMonths(), true, List.of());
            assertThrows(IllegalArgumentException.class, () -> service.previewMonthlyRecord(userId, "2026-09", request));
        }
    }

    @Test
    void permitsExplicitCurrentInventoryRevaluationAndReturnsBothValues() {
        stubRecord();
        MonthlyRecordZakatRequest input = supplements(new Fixture());
        InventoryItem current = new InventoryItem("inventory", InventoryType.RESALE, money("7000"), InventoryValuation.CURRENT_SELLING_VALUE);
        MonthlyRecordZakatRequest request = new MonthlyRecordZakatRequest(input.assessment(), current,
                input.receivables(), input.currentPayables(), input.principalDueWithin12LunarMonths(), true, List.of());
        ZakatPreviewResponse result = service.previewMonthlyRecord(userId, "2026-09", request);
        assertEquals(money("7000"), result.inputs().assets().inventory().getFirst().amount());
        assertEquals(money("5000"), result.source().inventoryValue());
        assertTrue(result.warnings().contains("INVENTORY_VALUATION_DIFFERS_FROM_RECORDED_VALUE"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "2026-13", "2026-9", "09-2026", "2026-09-01"})
    void rejectsInvalidMonthsBeforeReadingRepository(String month) {
        assertThrows(IllegalArgumentException.class, () -> service.previewMonthlyRecord(userId, month, minimalSupplements()));
        verifyNoInteractions(repository);
    }

    @Test
    void validatesUserAndBodyAndReportsMissingRecord() {
        assertThrows(IllegalArgumentException.class, () -> service.previewMonthlyRecord(null, "2026-09", minimalSupplements()));
        assertThrows(IllegalArgumentException.class, () -> service.previewMonthlyRecord(userId, "2026-09", null));
        verifyNoInteractions(repository);
        when(repository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.previewMonthlyRecord(userId, "2026-09", minimalSupplements()));
    }

    private void stubRecord() {
        when(repository.findByUserIdAndMonth(userId, "2026-09")).thenReturn(Optional.of(record));
    }

    private MonthlyRecordZakatRequest supplements(Fixture fixture) {
        return new MonthlyRecordZakatRequest(fixture.assessment(),
                new InventoryItem("inventory", InventoryType.RESALE, null, InventoryValuation.CURRENT_SELLING_VALUE),
                List.of(new Receivable("invoice", money("10000"), ReceivableClassification.GOOD)),
                List.of(new LiabilityItem("supplier", money("500"))),
                List.of(new LiabilityItem("principal", money("1500"))), true, List.of());
    }

    private MonthlyRecordZakatRequest minimalSupplements() {
        return new MonthlyRecordZakatRequest(new Fixture().assessment(), null, null, null, null, null, null);
    }
}
