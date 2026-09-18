package com.app.sme_health_backend.zakat;

import com.app.sme_health_backend.records.entity.MonthlyRecord;
import com.app.sme_health_backend.records.repository.MonthlyRecordRepository;
import com.app.sme_health_backend.shared.exception.GlobalExceptionHandler;
import com.app.sme_health_backend.zakat.controller.ZakatController;
import com.app.sme_health_backend.zakat.controller.ZakatExceptionHandler;
import com.app.sme_health_backend.zakat.service.ZakatCalculationService;
import com.app.sme_health_backend.zakat.service.ZakatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ZakatController.class)
@Import({ZakatService.class, ZakatCalculationService.class, ZakatExceptionHandler.class, GlobalExceptionHandler.class})
class ZakatControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MonthlyRecordRepository repository;

    private static final UUID USER_ID = UUID.fromString("a8fa09ed-7e46-4b50-94ea-91bebfa5d772");
    private static final String ASSESSMENT = """
            {
              "assessmentDate": "2026-09-30",
              "currency": "PKR",
              "nisabMetal": "SILVER",
              "metalWeightGrams": 612.36,
              "metalPricePerGram": 100,
              "priceSource": "Test quotation",
              "priceTimestamp": "2026-09-30T12:00:00+05:00",
              "haulStatus": "CONFIRMED"
            }
            """;
    private static final String MANUAL_REQUEST = """
            {
              "assessment": %s,
              "assets": {
                "cashAndBankBalances": 100000,
                "inventory": [],
                "receivables": [],
                "unsupportedCategories": []
              },
              "liabilities": {
                "accountsPayable": 0,
                "currentPayables": [],
                "principalDueWithin12LunarMonths": []
              },
              "financing": {
                "financingType": "none",
                "loanOutstanding": 0,
                "interestExpense": 0
              }
            }
            """.formatted(ASSESSMENT);

    @Test
    void manualEndpointIntegratesSerializationValidationAndRealCalculation() throws Exception {
        mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(MANUAL_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleProfile").value("HANAFI_PK_BUSINESS_V1"))
                .andExpect(jsonPath("$.ruleVersion").value("1.0.0"))
                .andExpect(jsonPath("$.calculationStatus").value("CALCULATED"))
                .andExpect(jsonPath("$.zakatDue").value(2500))
                .andExpect(jsonPath("$.nisabValue").value(61236))
                .andExpect(jsonPath("$.nisabMet").value(true))
                .andExpect(jsonPath("$.priceSource").value("Test quotation"))
                .andExpect(jsonPath("$.disclosure", containsString("qualified Islamic scholar")));
        verifyNoInteractions(repository);
    }

    @Test
    void savedEndpointUsesUuidAndReturnsSourceWithoutWriting() throws Exception {
        MonthlyRecord record = zeroRecord();
        when(repository.findByUserIdAndMonth(USER_ID, "2026-09")).thenReturn(Optional.of(record));
        mvc.perform(post("/api/zakat/{userId}/{month}/preview", USER_ID, "2026-09")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assessment\":" + ASSESSMENT + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zakatDue").value(2500))
                .andExpect(jsonPath("$.source.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.source.balancesDate").value("2026-09-30"));
        verify(repository).findByUserIdAndMonth(USER_ID, "2026-09");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void unknownInputsProduceIncompleteResponseNotZeroLiability() throws Exception {
        mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationStatus").value("INCOMPLETE"))
                .andExpect(jsonPath("$.zakatDue").isEmpty())
                .andExpect(jsonPath("$.missingFields").isNotEmpty())
                .andExpect(jsonPath("$.ruleProfile").value("HANAFI_PK_BUSINESS_V1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AGRICULTURE", "LIVESTOCK"})
    void unsupportedCategoriesAreExplicitInHttpResponse(String category) throws Exception {
        String request = MANUAL_REQUEST.replace("\"unsupportedCategories\": []", "\"unsupportedCategories\": [\"" + category + "\"]");
        mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationStatus").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.zakatDue").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{\"assessment\":{\"haulStatus\":\"INVALID\"}}",
            "{\"assessment\":{\"assessmentDate\":\"2026-99-01\"}}",
            "{\"assessment\":{\"priceTimestamp\":\"yesterday\"}}",
            "{\"assets\":{\"cashAndBankBalances\":\"invalid\"}}"})
    void malformedJsonAndInvalidTypesAreBadRequests(String body) throws Exception {
        mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(repository);
    }

    @Test
    void negativeAmountsAndConflictingNisabAreBadRequests() throws Exception {
        for (String request : new String[]{MANUAL_REQUEST.replace("100000", "-1"),
                MANUAL_REQUEST.replace("SILVER", "GOLD"), MANUAL_REQUEST.replace("612.36", "595"),
                MANUAL_REQUEST.replace("\"metalPricePerGram\": 100", "\"metalPricePerGram\": 0")}) {
            mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void doesNotSilentlyIgnoreUnrecognizedFinancialOrPolicyFields() throws Exception {
        for (String request : new String[]{MANUAL_REQUEST.replace("\"assessment\":", "\"zakatRate\": 0.05, \"assessment\":"),
                MANUAL_REQUEST.replace("\"cashAndBankBalances\":", "\"personalJewellery\": 999999, \"cashAndBankBalances\":"),
                MANUAL_REQUEST.replace("\"haulStatus\":", "\"solarYear\": true, \"haulStatus\":"),
                MANUAL_REQUEST.replace("\"loanOutstanding\":", "\"purificationAmount\": 300, \"loanOutstanding\":")}) {
            mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void preservesPriceOffsetAtPakistanDateBoundary() throws Exception {
        String request = MANUAL_REQUEST.replace("2026-09-30T12:00:00+05:00", "2026-09-30T00:30:00+05:00");
        mvc.perform(post("/api/zakat/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings").isEmpty())
                .andExpect(jsonPath("$.priceTimestamp").value("2026-09-30T00:30:00+05:00"))
                .andExpect(jsonPath("$.inputs.assessment.priceTimestamp").value("2026-09-30T00:30:00+05:00"));
    }

    @Test
    void invalidUuidAndMonthAreBadRequests() throws Exception {
        mvc.perform(post("/api/zakat/not-a-uuid/2026-09/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/zakat/{userId}/2026-13/preview", USER_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void missingRecordReturnsNotFound() throws Exception {
        when(repository.findByUserIdAndMonth(USER_ID, "2026-09")).thenReturn(Optional.empty());
        mvc.perform(post("/api/zakat/{userId}/2026-09/preview", USER_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedRepositoryFailureUsesExistingGenericErrorResponse() throws Exception {
        when(repository.findByUserIdAndMonth(USER_ID, "2026-09"))
                .thenThrow(new IllegalStateException("Database detail must not be exposed"));
        mvc.perform(post("/api/zakat/{userId}/2026-09/preview", USER_ID).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    private MonthlyRecord zeroRecord() {
        MonthlyRecord record = new MonthlyRecord();
        record.setUserId(USER_ID);
        record.setMonth("2026-09");
        record.setCashBalanceEom(new BigDecimal("100000"));
        record.setInventoryValue(BigDecimal.ZERO);
        record.setReceivablesOutstanding(BigDecimal.ZERO);
        record.setPayablesOutstanding(BigDecimal.ZERO);
        record.setLoanOutstanding(BigDecimal.ZERO);
        record.setInterestExpense(BigDecimal.ZERO);
        return record;
    }
}
