package com.codevam.vecindad.vehicles.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.vehicles.application.port.out.VehicleCatalogPort;
import com.codevam.vecindad.vehicles.domain.ParkingSpace;
import com.codevam.vecindad.vehicles.domain.VehicleLimit;
import com.codevam.vecindad.vehicles.domain.VehicleType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Configuración por copropiedad: tipos de vehículo, límites por tipo/inmueble y espacios de parqueadero. */
@Service
public class VehicleCatalogService {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Set<String> KINDS = Set.of("PUBLICO", "PRIVADO", "CUBIERTO", "SOTANO", "VISITANTE", "TEMPORAL");

    private final VehicleCatalogPort catalog;
    private final PropertyUnitPort units;
    private final AuditService audit;

    public VehicleCatalogService(VehicleCatalogPort catalog, PropertyUnitPort units, AuditService audit) {
        this.catalog = catalog;
        this.units = units;
        this.audit = audit;
    }

    public List<VehicleType> types() {
        TenantContext.require();
        return catalog.types();
    }

    public VehicleType upsertType(String rawCode, String name, boolean active, boolean requiresPlate) {
        UUID tenantId = TenantContext.require().id();
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw ApiException.badRequest("INVALID_VEHICLE_TYPE_CODE", "El código debe iniciar con letra y usar mayúsculas, números o guion bajo (2 a 30).");
        }
        catalog.upsertType(new VehicleType(code, name.trim(), active, requiresPlate));
        audit.log(tenantId, "VEHICLE_TYPE_SAVED", "VEHICLE_TYPE", code, true, Map.of("active", active));
        return catalog.findType(code).orElseThrow();
    }

    public List<VehicleLimit> limits() {
        TenantContext.require();
        return catalog.defaultLimits();
    }

    public VehicleLimit setDefaultLimit(String typeCode, int max) {
        UUID tenantId = TenantContext.require().id();
        requireType(typeCode);
        catalog.setDefaultLimit(typeCode, max);
        audit.log(tenantId, "VEHICLE_LIMIT_SET", "VEHICLE_TYPE", typeCode, true, Map.of("max", max));
        return new VehicleLimit(typeCode, max);
    }

    /** Excepción para un inmueble (p. ej. 2 carros porque tiene parqueadero privado en sótano). */
    public VehicleLimit setUnitLimit(UUID unitId, String typeCode, int max) {
        UUID tenantId = TenantContext.require().id();
        requireType(typeCode);
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        catalog.setUnitLimit(unitId, typeCode, max);
        audit.log(tenantId, "UNIT_VEHICLE_LIMIT_SET", "PROPERTY_UNIT", unitId.toString(), true, Map.of("type", typeCode, "max", max));
        return new VehicleLimit(typeCode, max);
    }

    public List<ParkingSpace> parkingSpaces() {
        TenantContext.require();
        return catalog.parkingSpaces();
    }

    public ParkingSpace createParking(String code, String kind, UUID unitId) {
        UUID tenantId = TenantContext.require().id();
        String k = kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            throw ApiException.badRequest("INVALID_PARKING_KIND", "Tipo de parqueadero inválido.");
        }
        if (unitId != null) {
            units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        }
        try {
            ParkingSpace p = catalog.insertParking(new ParkingSpace(UUID.randomUUID(), code.trim(), k, unitId, "ACTIVE"));
            audit.log(tenantId, "PARKING_CREATED", "PARKING_SPACE", p.id().toString(), true, Map.of("code", p.code()));
            return p;
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("PARKING_CODE_EXISTS", "Ya existe un espacio con ese código.");
        }
    }

    private void requireType(String typeCode) {
        catalog.findType(typeCode).orElseThrow(() -> ApiException.notFound("El tipo de vehículo no existe."));
    }
}
