package com.codevam.vecindad.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditLogView(UUID id, Instant occurredAt, UUID tenantId, UUID actorUserId, String actorEmail,
                           String action, String entity, String entityId, String result,
                           String ip, String traceId, String details) {}
