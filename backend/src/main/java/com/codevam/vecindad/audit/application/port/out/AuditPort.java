package com.codevam.vecindad.audit.application.port.out;

import com.codevam.vecindad.audit.domain.AuditLogView;
import com.codevam.vecindad.audit.domain.AuditRecord;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.UUID;

public interface AuditPort {
    void save(AuditRecord record);

    /** tenantId null = todos los tenants (solo para el área de plataforma). */
    PageResult<AuditLogView> search(UUID tenantId, String action, Instant from, Instant to, int page, int size);
}
