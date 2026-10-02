package com.codevam.vecindad.visitors.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.security.Tokens;
import com.codevam.vecindad.visitors.application.port.out.VisitorPort;
import com.codevam.vecindad.visitors.domain.Invitation;
import com.codevam.vecindad.visitors.domain.Visit;
import com.codevam.vecindad.visitors.domain.VisitStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Visitantes: invitaciones con QR temporal (el residente), validación y registro de ingreso (portería) y
 * autorización en tiempo real de visitantes sin invitación. Las transiciones críticas son sentencias SQL atómicas.
 * Aún no se envían notificaciones push/email: el residente consulta sus solicitudes pendientes (la entrega por canal
 * llega con el módulo de notificaciones).
 */
@Service
public class VisitorService {
    public static final int MAX_VALIDITY_DAYS = 30;

    public record InvitationCommand(UUID unitId, String visitorName, String documentNumber, String phone, String plate,
                                    Integer peopleCount, Integer maxEntries, Instant validFrom, Instant validTo, String notes) {}
    public record InvitationCreated(Invitation invitation, String qrToken) {}
    public record QrValidation(boolean valid, String reason, Invitation invitation) {}
    public record WalkInCommand(UUID unitId, String visitorName, String documentNumber, String phone, String plate,
                                Integer peopleCount, String note) {}

    private final VisitorPort visitors;
    private final UnitRelationPort relations;
    private final PropertyUnitPort units;
    private final AuditService audit;
    private final Clock clock;

    public VisitorService(VisitorPort visitors, UnitRelationPort relations, PropertyUnitPort units, AuditService audit, Clock clock) {
        this.visitors = visitors;
        this.relations = relations;
        this.units = units;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ residente

    public InvitationCreated createInvitation(InvitationCommand c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        requireOwnUnit(me, c.unitId());
        Instant now = clock.instant();
        Instant from = c.validFrom() == null ? now : c.validFrom();
        Instant to = c.validTo();
        if (to == null || !to.isAfter(from)) {
            throw ApiException.badRequest("INVALID_VALIDITY", "La fecha y hora de fin deben ser posteriores a las de inicio.");
        }
        if (!to.isAfter(now)) {
            throw ApiException.badRequest("INVITATION_ALREADY_EXPIRED", "La invitación ya estaría vencida.");
        }
        if (Duration.between(from, to).toDays() > MAX_VALIDITY_DAYS) {
            throw ApiException.badRequest("VALIDITY_TOO_LONG", "Una invitación no puede durar más de " + MAX_VALIDITY_DAYS + " días.");
        }
        String token = Tokens.newOpaqueToken();
        Invitation inv = visitors.insertInvitation(new Invitation(UUID.randomUUID(), c.unitId(), null, c.visitorName().trim(),
                blank(c.documentNumber()), blank(c.phone()), plate(c.plate()), c.peopleCount() == null ? 1 : c.peopleCount(),
                from, to, c.maxEntries() == null ? 1 : c.maxEntries(), 0, "ACTIVE", blank(c.notes()), null),
                Tokens.sha256Hex(token), me.userId());
        audit.log(me.tenantId(), "VISITOR_INVITATION_CREATED", "VISITOR_INVITATION", inv.id().toString(), true,
                Map.of("unitId", c.unitId().toString(), "visitor", inv.visitorName()));
        return new InvitationCreated(inv, token);
    }

    public List<Invitation> myInvitations(UUID unitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        List<UUID> ids = myUnitIds(me, unitId);
        return ids.isEmpty() ? List.of() : visitors.searchInvitations(ids, false, 0, Paging.MAX_SIZE).content();
    }

    public void cancelMine(UUID invitationId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Invitation inv = ownInvitation(me, invitationId);
        if (!visitors.cancelInvitation(invitationId)) {
            throw ApiException.conflict("INVITATION_NOT_ACTIVE", "La invitación ya estaba cancelada.");
        }
        audit.log(me.tenantId(), "VISITOR_INVITATION_CANCELLED", "VISITOR_INVITATION", inv.id().toString(), true, Map.of());
    }

    /** Genera un QR nuevo e invalida el anterior (el token solo se muestra al crearlo o regenerarlo). */
    public InvitationCreated regenerateQr(UUID invitationId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        ownInvitation(me, invitationId);
        String token = Tokens.newOpaqueToken();
        if (!visitors.replaceQr(invitationId, Tokens.sha256Hex(token))) {
            throw ApiException.conflict("INVITATION_NOT_ACTIVE", "Solo se puede regenerar el QR de una invitación activa.");
        }
        audit.log(me.tenantId(), "VISITOR_QR_REGENERATED", "VISITOR_INVITATION", invitationId.toString(), true, Map.of());
        return new InvitationCreated(visitors.findInvitation(invitationId).orElseThrow(), token);
    }

    public List<Visit> myRequests(VisitStatus status) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        List<UUID> ids = myUnitIds(me, null);
        return ids.isEmpty() ? List.of() : visitors.searchVisits(ids, status, 0, Paging.MAX_SIZE).content();
    }

    public Visit decide(UUID visitId, boolean authorize, String note) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Visit v = visitors.findVisit(visitId).orElseThrow(() -> ApiException.notFound("La solicitud no existe."));
        requireOwnUnit(me, v.unitId()); // 404 si la visita no es de uno de sus inmuebles
        if (!visitors.decide(visitId, authorize, me.userId(), blank(note))) {
            throw ApiException.conflict("VISIT_ALREADY_DECIDED", "Esta solicitud ya fue respondida.");
        }
        audit.log(me.tenantId(), authorize ? "VISIT_AUTHORIZED" : "VISIT_REJECTED", "VISIT", visitId.toString(), true,
                Map.of("unitId", v.unitId().toString()));
        return visitors.findVisit(visitId).orElseThrow();
    }

    // ------------------------------------------------------------------- portería

    public QrValidation validateQr(String token) {
        CurrentUser.requireTenant();
        Invitation inv = visitors.findInvitationByQrHash(Tokens.sha256Hex(token.trim())).orElse(null);
        if (inv == null) return new QrValidation(false, "INVALID_QR", null);
        String reason = reasonIfNotUsable(inv);
        return new QrValidation(reason == null, reason, inv);
    }

    public Visit checkInByQr(String token) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        String hash = Tokens.sha256Hex(token.trim());
        var visitId = visitors.checkInByQr(hash, me.userId());
        if (visitId.isPresent()) {
            Visit v = visitors.findVisit(visitId.get()).orElseThrow();
            audit.log(me.tenantId(), "VISITOR_CHECK_IN", "VISIT", v.id().toString(), true, Map.of("source", "INVITATION"));
            return v;
        }
        Invitation inv = visitors.findInvitationByQrHash(hash).orElse(null);
        if (inv == null) {
            visitors.saveAlert("INVALID_QR", null, null, "Se leyó un código QR de visitante no válido.", me.userId());
            audit.log(me.tenantId(), "VISITOR_CHECK_IN_DENIED", "VISIT", null, false, Map.of("reason", "invalid_qr"));
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "INVALID_QR", "El código QR no es válido.");
        }
        String reason = reasonIfNotUsable(inv);
        if ("QR_EXPIRED".equals(reason)) {
            visitors.saveAlert("VISITOR_EXPIRED", inv.plate(), inv.unitId(), "Visitante con invitación vencida: " + inv.visitorName() + ".", me.userId());
        }
        audit.log(me.tenantId(), "VISITOR_CHECK_IN_DENIED", "VISIT", inv.id().toString(), false, Map.of("reason", String.valueOf(reason)));
        throw ApiException.conflict(reason == null ? "QR_NOT_USABLE" : reason, qrMessage(reason));
    }

    public Visit walkIn(WalkInCommand c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        units.findActiveById(c.unitId()).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        Visit saved = visitors.insertWalkIn(new Visit(UUID.randomUUID(), null, c.unitId(), null, c.visitorName().trim(),
                blank(c.documentNumber()), blank(c.phone()), plate(c.plate()), c.peopleCount() == null ? 1 : c.peopleCount(),
                "WALK_IN", VisitStatus.PENDING_AUTH, null, null, null, null, null, blank(c.note())), me.userId());
        audit.log(me.tenantId(), "VISIT_REQUESTED", "VISIT", saved.id().toString(), true, Map.of("unitId", c.unitId().toString()));
        return saved;
    }

    public Visit checkInVisit(UUID visitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (visitors.checkInAuthorized(visitId, me.userId())) {
            audit.log(me.tenantId(), "VISITOR_CHECK_IN", "VISIT", visitId.toString(), true, Map.of("source", "WALK_IN"));
            return visitors.findVisit(visitId).orElseThrow();
        }
        Visit v = visitors.findVisit(visitId).orElseThrow(() -> ApiException.notFound("La visita no existe."));
        throw switch (v.status()) {
            case PENDING_AUTH -> ApiException.conflict("VISIT_PENDING_AUTH", "El residente aún no responde la solicitud de ingreso.");
            case REJECTED -> ApiException.forbidden("VISIT_REJECTED", "El residente rechazó el ingreso de este visitante.");
            case INSIDE -> ApiException.conflict("VISIT_ALREADY_INSIDE", "Este visitante ya se encuentra dentro.");
            case LEFT -> ApiException.conflict("VISIT_ALREADY_LEFT", "Esta visita ya terminó.");
            case AUTHORIZED -> ApiException.conflict("VISIT_STATE_CHANGED", "El estado de la visita cambió; intenta de nuevo.");
        };
    }

    public Visit checkOut(UUID visitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (!visitors.checkOut(visitId, me.userId())) {
            visitors.findVisit(visitId).orElseThrow(() -> ApiException.notFound("La visita no existe."));
            throw ApiException.conflict("VISIT_NOT_INSIDE", "Este visitante no figura dentro de la copropiedad.");
        }
        audit.log(me.tenantId(), "VISITOR_CHECK_OUT", "VISIT", visitId.toString(), true, Map.of());
        return visitors.findVisit(visitId).orElseThrow();
    }

    public PageResult<Visit> visits(UUID unitId, VisitStatus status, int page, int size) {
        CurrentUser.requireTenant();
        return visitors.searchVisits(unitId == null ? null : List.of(unitId), status, Paging.page(page), Paging.size(size));
    }

    public PageResult<Invitation> invitations(UUID unitId, boolean validNowOnly, int page, int size) {
        CurrentUser.requireTenant();
        return visitors.searchInvitations(unitId == null ? null : List.of(unitId), validNowOnly, Paging.page(page), Paging.size(size));
    }

    // -------------------------------------------------------------------- helpers

    private String reasonIfNotUsable(Invitation inv) {
        Instant now = clock.instant();
        if ("CANCELLED".equals(inv.status())) return "QR_CANCELLED";
        if (inv.usedCount() >= inv.maxEntries()) return "QR_ALREADY_USED";
        if (now.isBefore(inv.validFrom())) return "QR_NOT_YET_VALID";
        if (now.isAfter(inv.validTo())) return "QR_EXPIRED";
        return null;
    }

    private static String qrMessage(String reason) {
        if (reason == null) return "La invitación no se puede usar en este momento.";
        return switch (reason) {
            case "QR_CANCELLED" -> "El residente canceló esta invitación.";
            case "QR_ALREADY_USED" -> "Esta invitación ya fue utilizada.";
            case "QR_NOT_YET_VALID" -> "La invitación todavía no está vigente.";
            case "QR_EXPIRED" -> "La invitación venció.";
            default -> "La invitación no se puede usar en este momento.";
        };
    }

    private void requireOwnUnit(AuthenticatedUser me, UUID unitId) {
        if (unitId == null || !relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe."); // 404: no revela inmuebles ajenos
        }
    }

    private Invitation ownInvitation(AuthenticatedUser me, UUID id) {
        Invitation inv = visitors.findInvitation(id).orElseThrow(() -> ApiException.notFound("La invitación no existe."));
        if (!relations.userHasUnit(me.userId(), inv.unitId())) {
            throw ApiException.notFound("La invitación no existe.");
        }
        return inv;
    }

    private List<UUID> myUnitIds(AuthenticatedUser me, UUID onlyUnit) {
        if (onlyUnit != null) {
            requireOwnUnit(me, onlyUnit);
            return List.of(onlyUnit);
        }
        return relations.unitsOfUser(me.userId()).stream().map(MyUnit::unitId).toList();
    }

    private static String plate(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
