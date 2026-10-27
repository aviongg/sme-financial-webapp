package com.app.sme_health_backend.commercial.controller;

import com.app.sme_health_backend.commercial.model.CommercialDocumentKind;
import com.app.sme_health_backend.commercial.service.CommercialDocumentService;
import com.app.sme_health_backend.identity.service.BusinessAuthorizationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer invoices (F04 Phase A): recorded documents only, financialEffect NONE. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController extends CommercialDocumentController {
    public InvoiceController(BusinessAuthorizationService authorization, CommercialDocumentService service) {
        super(authorization, service, CommercialDocumentKind.INVOICE);
    }
}
