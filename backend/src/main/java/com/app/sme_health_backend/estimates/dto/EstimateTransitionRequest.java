package com.app.sme_health_backend.estimates.dto;

/** Approve (revision version) or archive (estimate version); nothing else is client-controlled. */
public record EstimateTransitionRequest(Long expectedVersion) { }
