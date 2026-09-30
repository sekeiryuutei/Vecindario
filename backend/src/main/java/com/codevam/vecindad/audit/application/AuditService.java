package com.codevam.vecindad.audit.application;

import com.codevam.vecindad.audit.application.port.out.AuditPort;
import com.codevam.vecindad.audit.domain.AuditRecord;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.web.ClientInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Escribe auditoría en una transacción independiente para que los eventos (p. ej. intentos fallidos)
 * persistan aunque la operación de negocio haga rollback. Nunca guarda secretos.
 */
@Service
public class AuditService {
    private static final Pattern SENSITIVE_KEY = Pattern.compile("(?i).*(pass|secret|token|authorization).*");

    private final AuditPort port;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditService(AuditPort port, ObjectMapper mapper, Clock clock) {
        this.port = port;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Registra usando como actor al usuario autenticado (o "system" si no hay). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID tenantId, String action, String entity, String entityId, boolean success,
                    Map<String, Object> details) {
        UUID actorId = CurrentUser.get().map(u -> u.userId()).orElse(null);
        String actorEmail = CurrentUser.get().map(u -> u.email()).orElse("system");
        write(actorId, actorEmail, tenantId, action, entity, entityId, success, details);
    }

    /** Registra con un actor explícito (login, reset de contraseña, etc.). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAs(UUID actorId, String actorEmail, UUID tenantId, String action, String entity,
                      String entityId, boolean success, Map<String, Object> details) {
        write(actorId, actorEmail, tenantId, action, entity, entityId, success, details);
    }

    private void write(UUID actorId, String actorEmail, UUID tenantId, String action, String entity,
                       String entityId, boolean success, Map<String, Object> details) {
        port.save(new AuditRecord(UUID.randomUUID(), clock.instant(), tenantId, actorId, actorEmail, action, entity,
                entityId, success, ClientInfo.ip(), ClientInfo.userAgent(), MDC.get("traceId"), toJson(details)));
    }

    private String toJson(Map<String, Object> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((k, v) -> {
                if (k != null && !SENSITIVE_KEY.matcher(k).matches()) {
                    safe.put(k, v);
                }
            });
        }
        try {
            return mapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
