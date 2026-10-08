package com.app.sme_health_backend.whatsapp.client;

import com.app.sme_health_backend.whatsapp.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    private boolean simulateFailure = false;
    private boolean simulateIndeterminate = false;

    @Override
    public WhatsAppSendResult sendSummary(WhatsAppSendRequest request) {
        String maskedNumber = PhoneNumberValidator.mask(request.destinationNumber());
        log.info("MockWhatsAppClient: Sending summary to {} [lang={}, template={}]",
                maskedNumber, request.language(), request.templateName());

        if (simulateIndeterminate || request.destinationNumber().endsWith("0000")) {
            log.warn("MockWhatsAppClient: Simulating indeterminate network timeout for {}", maskedNumber);
            return WhatsAppSendResult.indeterminate("Simulated provider network timeout", getProviderName());
        }

        if (simulateFailure || request.destinationNumber().endsWith("9999")) {
            log.warn("MockWhatsAppClient: Simulating provider rejection for {}", maskedNumber);
            return WhatsAppSendResult.failed("Simulated provider rejection", getProviderName());
        }

        String messageId = "mock-msg-" + UUID.randomUUID();
        log.info("MockWhatsAppClient: Summary accepted by provider with id {} for {}", messageId, maskedNumber);
        return WhatsAppSendResult.sent(messageId, getProviderName());
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void setSimulateIndeterminate(boolean simulateIndeterminate) {
        this.simulateIndeterminate = simulateIndeterminate;
    }
}
