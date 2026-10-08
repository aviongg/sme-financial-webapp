package com.app.sme_health_backend.documents.dto;

import java.time.OffsetDateTime;
import java.util.*;

public record DocumentCorrectionResponse(UUID id, String previousData, String newData, List<String> changedFields,
                                         OffsetDateTime correctedAt, boolean actorIsCurrentUser) {}
