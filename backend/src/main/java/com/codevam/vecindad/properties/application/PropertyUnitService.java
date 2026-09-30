package com.codevam.vecindad.properties.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Todas las operaciones corren en el schema del tenant activo (TenantContext), nunca en otro. */
@Service
public class PropertyUnitService {

    public record Command(UnitType type, String identifier, String unitNumber, String tower, Integer floorNumber,
                          BigDecimal coefficient, BigDecimal areaM2, UnitStatus status) {}

    private final PropertyUnitPort port;
    private final AuditService audit;
    private final Clock clock;

    public PropertyUnitService(PropertyUnitPort port, AuditService audit, Clock clock) {
        this.port = port;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PropertyUnit create(Command c) {
        UUID tenantId = TenantContext.require().id();
        String identifier = c.identifier().trim();
        if (port.existsActiveIdentifier(identifier, null)) {
            throw ApiException.conflict("PROPERTY_IDENTIFIER_EXISTS", "Ya existe un inmueble con ese identificador.");
        }
        var now = clock.instant();
        PropertyUnit saved = port.save(new PropertyUnit(UUID.randomUUID(), c.type(), identifier, c.unitNumber(), c.tower(),
                c.floorNumber(), c.coefficient(), c.areaM2(), c.status() == null ? UnitStatus.ACTIVE : c.status(),
                now, now, null, null));
        audit.log(tenantId, "PROPERTY_CREATED", "PROPERTY_UNIT", saved.id().toString(), true,
                Map.of("identifier", saved.identifier(), "type", saved.type().name()));
        return saved;
    }

    @Transactional
    public PropertyUnit update(UUID id, Command c) {
        UUID tenantId = TenantContext.require().id();
        PropertyUnit current = get(id);
        String identifier = c.identifier().trim();
        if (port.existsActiveIdentifier(identifier, id)) {
            throw ApiException.conflict("PROPERTY_IDENTIFIER_EXISTS", "Ya existe un inmueble con ese identificador.");
        }
        PropertyUnit saved = port.save(new PropertyUnit(id, c.type(), identifier, c.unitNumber(), c.tower(),
                c.floorNumber(), c.coefficient(), c.areaM2(), c.status() == null ? current.status() : c.status(),
                current.createdAt(), clock.instant(), null, null));
        audit.log(tenantId, "PROPERTY_UPDATED", "PROPERTY_UNIT", id.toString(), true, Map.of("identifier", identifier));
        return saved;
    }

    @Transactional(readOnly = true)
    public PropertyUnit get(UUID id) {
        return port.findActiveById(id).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
    }

    @Transactional(readOnly = true)
    public PageResult<PropertyUnit> search(String text, UnitType type, int page, int size) {
        return port.search(text, type, Paging.page(page), Paging.size(size));
    }

    /** Eliminación lógica: conserva la fila, quién y cuándo. */
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require().id();
        PropertyUnit u = get(id);
        UUID actor = CurrentUser.get().map(x -> x.userId()).orElse(null);
        port.save(new PropertyUnit(u.id(), u.type(), u.identifier(), u.unitNumber(), u.tower(), u.floorNumber(),
                u.coefficient(), u.areaM2(), UnitStatus.INACTIVE, u.createdAt(), clock.instant(), clock.instant(), actor));
        audit.log(tenantId, "PROPERTY_DELETED", "PROPERTY_UNIT", id.toString(), true, Map.of("identifier", u.identifier()));
    }
}
