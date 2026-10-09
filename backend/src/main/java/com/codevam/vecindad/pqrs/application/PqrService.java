package com.codevam.vecindad.pqrs.application;

import com.codevam.vecindad.pqrs.application.port.out.PqrPort;
import com.codevam.vecindad.pqrs.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PqrService {

    public record CreatePqrCommand(
            UUID unitId,
            UUID personId,
            PqrCategory category,
            PqrPriority priority,
            String title,
            String description,
            boolean isAnonymous
    ) {}

    public record PqrDetail(
            Pqr pqr,
            List<PqrMessage> messages
    ) {}

    private final PqrPort port;

    public PqrService(PqrPort port) {
        this.port = port;
    }

    public Pqr getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("PQR no encontrada."));
    }

    public PqrDetail getDetail(UUID id, boolean includeInternal) {
        Pqr pqr = getById(id);
        List<PqrMessage> messages = port.listMessages(id, includeInternal);
        return new PqrDetail(pqr, messages);
    }

    public PageResult<Pqr> search(PqrCategory category, PqrStatus status, PqrPriority priority,
                                  Boolean overdue, int page, int size) {
        return port.search(category, status, priority, overdue, page, size);
    }

    public PageResult<Pqr> findByUser(UUID userId, int page, int size) {
        return port.findByUser(userId, page, size);
    }

    @Transactional
    public Pqr create(UUID userId, CreatePqrCommand cmd) {
        PqrPriority priority = cmd.priority() == null ? PqrPriority.MEDIUM : cmd.priority();

        // Convivencia / Ruido preventivo: mínimo prioridad HIGH
        if (cmd.category() == PqrCategory.CONVIVENCIA_RUIDO && (priority == PqrPriority.LOW || priority == PqrPriority.MEDIUM)) {
            priority = PqrPriority.HIGH;
        }

        int slaHours = calculateSlaHours(cmd.category(), priority);
        Instant now = Instant.now();
        Instant slaDueAt = now.plus(Duration.ofHours(slaHours));
        String ticket = port.nextTicketNumber();

        Pqr toCreate = new Pqr(
                UUID.randomUUID(),
                ticket,
                cmd.unitId(),
                cmd.personId(),
                userId,
                cmd.category(),
                priority,
                PqrStatus.OPEN,
                cmd.title(),
                cmd.description(),
                cmd.isAnonymous(),
                null,
                slaHours,
                slaDueAt,
                null,
                null,
                null,
                now,
                now
        );

        return port.insert(toCreate);
    }

    @Transactional
    public PqrMessage addMessage(UUID pqrId, UUID senderUserId, String senderName, String message, boolean isInternal) {
        Pqr pqr = getById(pqrId);
        if (pqr.status() == PqrStatus.CLOSED && !isInternal) {
            throw ApiException.conflict("PQR_CLOSED", "La PQR ya ha sido cerrada.");
        }

        PqrMessage msg = new PqrMessage(
                UUID.randomUUID(),
                pqrId,
                senderUserId,
                senderName,
                isInternal,
                message,
                Instant.now()
        );
        PqrMessage created = port.addMessage(msg);

        // Si la PQR estaba abierta y el equipo responde, pasa a IN_PROGRESS
        if (pqr.status() == PqrStatus.OPEN && isInternal) {
            port.updateStatus(pqrId, PqrStatus.IN_PROGRESS, null, null, null);
        }

        return created;
    }

    @Transactional
    public Pqr updateStatus(UUID pqrId, PqrStatus newStatus, String resolutionNotes) {
        Pqr pqr = getById(pqrId);
        if (pqr.status() == PqrStatus.CLOSED && newStatus != PqrStatus.CLOSED) {
            throw ApiException.conflict("PQR_ALREADY_CLOSED", "Una PQR cerrada no puede cambiar de estado.");
        }

        Instant resolvedAt = null;
        Instant closedAt = null;
        Instant now = Instant.now();

        if (newStatus == PqrStatus.RESOLVED) {
            resolvedAt = now;
        } else if (newStatus == PqrStatus.CLOSED) {
            closedAt = now;
            if (pqr.resolvedAt() == null) {
                resolvedAt = now;
            }
        }

        port.updateStatus(pqrId, newStatus, resolutionNotes, resolvedAt, closedAt);
        return getById(pqrId);
    }

    @Transactional
    public Pqr assign(UUID pqrId, UUID assigneeId) {
        Pqr pqr = getById(pqrId);
        port.assign(pqrId, assigneeId);
        if (pqr.status() == PqrStatus.OPEN) {
            port.updateStatus(pqrId, PqrStatus.IN_PROGRESS, null, null, null);
        }
        return getById(pqrId);
    }

    public PqrSlaMetrics getSlaMetrics() {
        return port.getSlaMetrics();
    }

    private int calculateSlaHours(PqrCategory category, PqrPriority priority) {
        int base = switch (category) {
            case CONVIVENCIA_RUIDO -> 24;
            case CONVIVENCIA_MASCOTAS, CONVIVENCIA_GENERAL -> 48;
            case PETICION -> 120; // 5 días hábiles (Ley 1755 de 2015)
            case QUEJA, RECLAMO -> 72;
            case SUGERENCIA -> 168; // 7 días calendario
            case OTRO -> 72;
        };

        if (priority == PqrPriority.URGENT) {
            return Math.min(base, 12);
        }
        return base;
    }
}

