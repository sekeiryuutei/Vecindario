package com.codevam.vecindad.parcels.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.parcels.application.port.out.ParcelPort;
import com.codevam.vecindad.parcels.domain.Parcel;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paquetería. Estados: RECEIVED → (NOTIFIED lo marcará el módulo de notificaciones) → DELIVERED | RETURNED.
 * Las transiciones son UPDATE condicionales (atómicos). La foto del paquete llegará con el almacenamiento S3.
 */
@Service
public class ParcelService {
    private static final Set<String> STATUSES = Set.of("RECEIVED", "NOTIFIED", "DELIVERED", "RETURNED");

    public record ReceiveCommand(UUID unitId, String recipientName, String carrier, String trackingNumber, String description) {}

    private final ParcelPort parcels;
    private final PropertyUnitPort units;
    private final UnitRelationPort relations;
    private final AuditService audit;

    public ParcelService(ParcelPort parcels, PropertyUnitPort units, UnitRelationPort relations, AuditService audit) {
        this.parcels = parcels;
        this.units = units;
        this.relations = relations;
        this.audit = audit;
    }

    public Parcel receive(ReceiveCommand c) {
        UUID tenantId = TenantContext.require().id();
        units.findActiveById(c.unitId()).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        UUID guard = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        Parcel p = parcels.insert(new Parcel(UUID.randomUUID(), c.unitId(), null, c.recipientName().trim(), blank(c.carrier()),
                blank(c.trackingNumber()), blank(c.description()), "RECEIVED", null, null, null, null, null, null), guard);
        audit.log(tenantId, "PARCEL_RECEIVED", "PARCEL", p.id().toString(), true, Map.of("unitId", c.unitId().toString()));
        return p;
    }

    public Parcel deliver(UUID id, String deliveredTo) {
        UUID tenantId = TenantContext.require().id();
        get(id);
        UUID guard = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!parcels.markDelivered(id, deliveredTo.trim(), guard)) {
            throw ApiException.conflict("PARCEL_NOT_PENDING", "El paquete ya fue entregado o devuelto.");
        }
        audit.log(tenantId, "PARCEL_DELIVERED", "PARCEL", id.toString(), true, Map.of("deliveredTo", deliveredTo.trim()));
        return get(id);
    }

    public Parcel returnParcel(UUID id, String note) {
        UUID tenantId = TenantContext.require().id();
        get(id);
        UUID guard = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!parcels.markReturned(id, blank(note), guard)) {
            throw ApiException.conflict("PARCEL_NOT_PENDING", "El paquete ya fue entregado o devuelto.");
        }
        audit.log(tenantId, "PARCEL_RETURNED", "PARCEL", id.toString(), true, Map.of());
        return get(id);
    }

    public Parcel get(UUID id) {
        return parcels.findById(id).orElseThrow(() -> ApiException.notFound("El paquete no existe."));
    }

    public PageResult<Parcel> search(UUID unitId, String status, String q, int page, int size) {
        TenantContext.require();
        return parcels.search(unitId == null ? null : List.of(unitId), normalizeStatus(status), blank(q), Paging.page(page), Paging.size(size));
    }

    /** Paquetes de MIS inmuebles (según la persona enlazada al usuario). */
    public List<Parcel> mine(String status) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        List<UUID> ids = relations.unitsOfUser(me.userId()).stream().map(MyUnit::unitId).toList();
        return ids.isEmpty() ? List.of() : parcels.search(ids, normalizeStatus(status), null, 0, Paging.MAX_SIZE).content();
    }

    private static String normalizeStatus(String s) {
        if (s == null || s.isBlank()) return null;
        String up = s.trim().toUpperCase();
        if (!STATUSES.contains(up)) {
            throw ApiException.badRequest("INVALID_STATUS", "Estado de paquete inválido.");
        }
        return up;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
