package com.app.sme_health_backend.commercial.dto;

/** Finalize (reason always null) or void (reason required) command. */
public record CommercialTransitionRequest(Long expectedVersion, String reason) { }
