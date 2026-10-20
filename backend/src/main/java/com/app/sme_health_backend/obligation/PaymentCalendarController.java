package com.app.sme_health_backend.obligation;

import com.app.sme_health_backend.identity.dto.BusinessAccessContext;
import com.app.sme_health_backend.identity.model.BusinessPermission;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import com.app.sme_health_backend.obligation.dto.PaymentCalendarResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/payment-calendar")
public class PaymentCalendarController {

    private final PaymentPlanService paymentPlanService;
    private final BusinessAuthorizationService authService;

    public PaymentCalendarController(
            PaymentPlanService paymentPlanService,
            BusinessAuthorizationService authService
    ) {
        this.paymentPlanService = paymentPlanService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<PaymentCalendarResponse> getPaymentCalendar(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "from", required = false) String fromDate,
            @RequestParam(name = "to", required = false) String toDate,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = authService.requirePermission(
                request, BusinessPermission.FINANCIAL_TRANSACTION_READ
        );
        PaymentCalendarResponse response = paymentPlanService.getPaymentCalendar(
                context, direction, fromDate, toDate
        );
        return ResponseEntity.ok(response);
    }
}
