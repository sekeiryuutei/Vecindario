package com.codevam.vecindad.incidents.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.domain.MembershipStatus;
import com.codevam.vecindad.incidents.application.port.out.IncidentPort;
import com.codevam.vecindad.incidents.domain.Incident;
import com.codevam.vecindad.incidents.domain.IncidentCategory;
import com.codevam.vecindad.incidents.domain.IncidentStatus;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Novedades de portería. Las evidencias (fotos) llegarán con el almacenamiento S3 (fase de documentos). */
@Service
public class IncidentService {

    public record Command(IncidentCategory category, String description, String location, Instant occurredAt) {}

    private final IncidentPort incidents;
    private final MembershipPort memberships;
    private final AuditService audit;
    private final Clock clock;

    public IncidentService(IncidentPort incidents, MembershipPort memberships, AuditService audit, Clock clock) {
        this.incidents = incidents;
        this.memberships = memberships;
        this.audit = audit;
        this.clock = clock;
    }

    public Incident create(UUID reporter, Command c) {
        UUID tenantId = TenantContext.require().id();
        Instant now = clock.instant();
        Instant when = c.occurredAt() == null ? now : c.occurredAt();
        if (when.isAfter(now.plusSeconds(300))) {
            throw ApiException.badRequest("INVALID_OCCURRED_AT", "La fecha de la novedad no puede estar en el futuro.");
        }
        Incident saved = incidents.insert(new Incident(UUID.randomUUID(), c.category(), c.description().trim(),
                c.location() == null || c.location().isBlank() ? null : c.location().trim(), when, reporter, null,
                IncidentStatus.OPEN, null, null, null, null));
        audit.log(tenantId, "INCIDENT_CREATED", "INCIDENT", saved.id().toString(), true, Map.of("category", c.category().name()));
        return saved;
    }

    public Incident get(UUID id) {
        TenantContext.require();
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("La novedad no existe."));
    }

    public PageResult<Incident> search(IncidentCategory category, IncidentStatus status, Instant from, Instant to, int page, int size) {
        TenantContext.require();
        return incidents.search(category, status, from, to, Paging.page(page), Paging.size(size));
    }

    public Incident updateStatus(UUID id, IncidentStatus status, String resolution) {
        UUID tenantId = TenantContext.require().id();
        Incident cur = get(id);
        String res = resolution == null || resolution.isBlank() ? null : resolution.trim();
        if (status == IncidentStatus.CLOSED && res == null) {
            throw ApiException.badRequest("RESOLUTION_REQUIRED", "Para cerrar la novedad indica cómo se resolvió.");
        }
        if (cur.status() == status) {
            throw ApiException.conflict("INCIDENT_SAME_STATUS", "La novedad ya está en ese estado.");
        }
        incidents.updateStatus(id, status, res);
        audit.log(tenantId, "INCIDENT_STATUS_CHANGED", "INCIDENT", id.toString(), true,
                Map.of("from", cur.status().name(), "to", status.name()));
        return get(id);
    }

    public Incident assign(UUID id, UUID userId) {
        UUID tenantId = TenantContext.require().id();
        get(id);
        var m = memberships.find(userId, tenantId);
        if (m.isEmpty() || m.get().status() != MembershipStatus.ACTIVE) {
            throw ApiException.conflict("USER_NOT_MEMBER", "El responsable debe ser un usuario activo de esta copropiedad.");
        }
        incidents.assign(id, userId);
        audit.log(tenantId, "INCIDENT_ASSIGNED", "INCIDENT", id.toString(), true, Map.of("assignedTo", userId.toString()));
        return get(id);
    }
}
