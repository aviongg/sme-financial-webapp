package com.app.sme_health_backend.commercial.controller;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Supplier bills (F04 Phase A): recorded documents only, financialEffect NONE. */
@RestController
@RequestMapping("/api/bills")
public class BillController extends CommercialDocumentController {
    public BillController(BusinessAuthorizationService authorization, CommercialDocumentService service) {
        super(authorization, service, CommercialDocumentKind.BILL);
    }
}
