package com.codevam.vecindad.reconciliation.domain;

import java.time.Instant;
import java.util.UUID;

public record Decision(UUID id, String action, String fromStatus, String toStatus, UUID unitId, UUID paymentId,
                       String reason, UUID decidedBy, Instant decidedAt) {}
