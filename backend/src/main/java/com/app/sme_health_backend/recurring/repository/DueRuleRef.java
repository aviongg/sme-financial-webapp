package com.app.sme_health_backend.recurring.repository;

import java.util.UUID;

/** A due recurring rule's identity only: generation scans must not load (and cache) entities before locking them. */
public record DueRuleRef(UUID id, UUID businessId) { }
