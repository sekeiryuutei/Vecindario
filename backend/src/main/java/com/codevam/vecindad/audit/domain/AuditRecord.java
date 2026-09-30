package com.codevam.vecindad.audit.domain;

import java.time.Instant;
import java.util.UUID;

/** Registro de auditoría a persistir. */
public record AuditRecord(UUID id, Instant occurredAt, UUID tenantId, UUID actorUserId, String actorEmail,
                          String action, String entity, String entityId, boolean success,
                          String ip, String userAgent, String traceId, String detailsJson) {}
