package com.codevam.vecindad.vehicles.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.vehicles.application.port.out.AccessPort;
import com.codevam.vecindad.vehicles.application.port.out.VehicleCatalogPort;
import com.codevam.vecindad.vehicles.application.port.out.VehiclePort;
import com.codevam.vecindad.vehicles.domain.ParkingSpace;
import com.codevam.vecindad.vehicles.domain.Presence;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import com.codevam.vecindad.vehicles.domain.VehicleStatus;
import com.codevam.vecindad.vehicles.domain.VehicleType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reglas de vehículos. Todas viven en backend: un vehículo DENTRO no se modifica ni elimina, los límites por
 * tipo/inmueble son configurables y las operaciones críticas son atómicas en SQL.
 * (Sin @Transactional a propósito: cada sentencia crítica es atómica por sí sola.)
 */
@Service
public class VehicleService {
    public static final String INSIDE_MESSAGE =
            "Este vehículo se encuentra actualmente dentro de la copropiedad. Debe registrarse su salida antes de modificar su registro.";
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9]{3,10}$");

    public record Command(String typeCode, String plate, String brand, String model, String color, Integer modelYear,
                          UUID ownerPersonId, UUID unitId, UUID parkingSpaceId, VehicleStatus status) {}

    private final VehiclePort vehicles;
    private final VehicleCatalogPort catalog;
    private final AccessPort access;
    private final PropertyUnitPort units;
    private final PersonPort people;
    private final UnitRelationPort relations;
    private final AuditService audit;
    private final Clock clock;

    public VehicleService(VehiclePort vehicles, VehicleCatalogPort catalog, AccessPort access, PropertyUnitPort units,
                          PersonPort people, UnitRelationPort relations, AuditService audit, Clock clock) {
        this.vehicles = vehicles;
        this.catalog = catalog;
        this.access = access;
        this.units = units;
        this.people = people;
        this.relations = relations;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------- administración

    public Vehicle create(Command c) {
        UUID tenantId = TenantContext.require().id();
        VehicleType type = requireType(c.typeCode());
        String plate = normalizePlate(c.plate(), type);
        requireUnit(c.unitId());
        requireOwner(c.ownerPersonId());
        requireParking(c.parkingSpaceId(), c.unitId());
        checkPlateFree(plate, null, c.unitId());
        int limit = limitFor(c.unitId(), type.code());
        Vehicle v = new Vehicle(UUID.randomUUID(), type.code(), plate, trim(c.brand()), trim(c.model()), trim(c.color()),
                c.modelYear(), c.ownerPersonId(), c.unitId(), null, c.parkingSpaceId(),
                c.status() == null ? VehicleStatus.AUTHORIZED : c.status(), Presence.OUTSIDE, false, null, null);
        boolean inserted;
        try {
            inserted = vehicles.insertIfBelowLimit(v, limit);
        } catch (DuplicateKeyException e) {
            throw plateTaken();
        }
        if (!inserted) {
            throw limitReached(limit, type);
        }
        audit.log(tenantId, "VEHICLE_CREATED", "VEHICLE", v.id().toString(), true,
                Map.of("plate", String.valueOf(plate), "unitId", c.unitId().toString()));
        return get(v.id());
    }

    public Vehicle update(UUID id, Command c) {
        UUID tenantId = TenantContext.require().id();
        Vehicle cur = get(id);
        assertOutside(cur);
        VehicleType type = requireType(c.typeCode());
        String plate = normalizePlate(c.plate(), type);
        requireUnit(c.unitId());
        requireOwner(c.ownerPersonId());
        requireParking(c.parkingSpaceId(), c.unitId());
        checkPlateFree(plate, id, c.unitId());
        boolean slotChanged = !cur.unitId().equals(c.unitId()) || !cur.typeCode().equals(type.code());
        if (slotChanged) {
            int limit = limitFor(c.unitId(), type.code());
            if (vehicles.countActive(c.unitId(), type.code()) >= limit) {
                throw limitReached(limit, type);
            }
        }
        Vehicle upd = new Vehicle(id, type.code(), plate, trim(c.brand()), trim(c.model()), trim(c.color()), c.modelYear(),
                c.ownerPersonId(), c.unitId(), null, c.parkingSpaceId(), c.status() == null ? cur.status() : c.status(),
                cur.presence(), cur.primary(), cur.createdAt(), null);
        boolean ok;
        try {
            ok = vehicles.updateIfOutside(upd);
        } catch (DuplicateKeyException e) {
            throw plateTaken();
        }
        if (!ok) {
            throw rejectedUpdate(id); // entró justo ahora, o ya no existe
        }
        audit.log(tenantId, "VEHICLE_UPDATED", "VEHICLE", id.toString(), true, Map.of());
        return get(id);
    }

    public void delete(UUID id) {
        UUID tenantId = TenantContext.require().id();
        Vehicle cur = get(id);
        assertOutside(cur);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        if (!vehicles.softDeleteIfOutside(id, actor, clock.instant())) {
            throw rejectedUpdate(id);
        }
        audit.log(tenantId, "VEHICLE_DELETED", "VEHICLE", id.toString(), true, Map.of("plate", String.valueOf(cur.plate())));
    }

    public Vehicle get(UUID id) {
        return vehicles.findById(id).orElseThrow(() -> ApiException.notFound("El vehículo no existe."));
    }

    public PageResult<Vehicle> search(String plate, UUID unitId, Presence presence, int page, int size) {
        String p = plate == null || plate.isBlank() ? null : plate.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return vehicles.search(p, unitId, presence, Paging.page(page), Paging.size(size));
    }

    // ------------------------------------------------------- residente (solo lo propio)

    public List<Vehicle> listOwn(UUID unitId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (unitId != null && !relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        List<Vehicle> out = new java.util.ArrayList<>();
        for (var u : relations.unitsOfUser(me.userId())) {
            if (unitId == null || unitId.equals(u.unitId())) {
                out.addAll(vehicles.search(null, u.unitId(), null, 0, Paging.MAX_SIZE).content());
            }
        }
        return out;
    }

    public Vehicle createOwn(Command c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (c.unitId() == null || !relations.userHasUnit(me.userId(), c.unitId())) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        UUID owner = people.findByUserId(me.userId()).map(Person::id).orElse(null);
        return create(new Command(c.typeCode(), c.plate(), c.brand(), c.model(), c.color(), c.modelYear(), owner,
                c.unitId(), null, VehicleStatus.AUTHORIZED));
    }

    public Vehicle updateOwn(UUID id, Command c) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        Vehicle cur = ownVehicle(me, id);
        UUID unitId = c.unitId() == null ? cur.unitId() : c.unitId();
        if (!relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        return update(id, new Command(c.typeCode(), c.plate(), c.brand(), c.model(), c.color(), c.modelYear(),
                cur.ownerPersonId(), unitId, cur.parkingSpaceId(), cur.status()));
    }

    public void deleteOwn(UUID id) {
        ownVehicle(CurrentUser.requireTenant(), id);
        delete(id);
    }

    // ------------------------------------------------------------------ helpers

    private Vehicle ownVehicle(AuthenticatedUser me, UUID id) {
        Vehicle v = vehicles.findById(id).orElseThrow(() -> ApiException.notFound("El vehículo no existe."));
        if (!relations.userHasUnit(me.userId(), v.unitId())) {
            throw ApiException.notFound("El vehículo no existe."); // 404, no 403: no revela que existe
        }
        return v;
    }

    private static void assertOutside(Vehicle v) {
        if (v.presence() == Presence.INSIDE) {
            throw ApiException.conflict("VEHICLE_INSIDE", INSIDE_MESSAGE);
        }
    }

    private ApiException rejectedUpdate(UUID id) {
        return vehicles.findById(id)
                .filter(v -> v.presence() == Presence.INSIDE)
                .map(v -> ApiException.conflict("VEHICLE_INSIDE", INSIDE_MESSAGE))
                .orElseGet(() -> ApiException.notFound("El vehículo no existe."));
    }

    private VehicleType requireType(String code) {
        return catalog.findType(code == null ? "" : code.trim().toUpperCase(Locale.ROOT)).filter(VehicleType::active)
                .orElseThrow(() -> ApiException.badRequest("INVALID_VEHICLE_TYPE", "El tipo de vehículo no existe o está inactivo."));
    }

    static String normalizePlate(String raw, VehicleType type) {
        if (raw == null || raw.isBlank()) {
            if (type.requiresPlate()) {
                throw ApiException.badRequest("PLATE_REQUIRED", "La placa es obligatoria para este tipo de vehículo.");
            }
            return null;
        }
        String p = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        if (!PLATE.matcher(p).matches()) {
            throw ApiException.badRequest("INVALID_PLATE", "La placa debe tener entre 3 y 10 letras o números.");
        }
        return p;
    }

    private void requireUnit(UUID unitId) {
        if (unitId == null) {
            throw ApiException.badRequest("UNIT_REQUIRED", "Debes indicar el inmueble.");
        }
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
    }

    private void requireOwner(UUID personId) {
        if (personId != null) {
            people.findById(personId).orElseThrow(() -> ApiException.notFound("La persona propietaria del vehículo no existe."));
        }
    }

    private void requireParking(UUID parkingId, UUID unitId) {
        if (parkingId == null) return;
        ParkingSpace p = catalog.findParking(parkingId).orElseThrow(() -> ApiException.notFound("El espacio de parqueadero no existe."));
        if (!"ACTIVE".equals(p.status())) {
            throw ApiException.conflict("PARKING_INACTIVE", "El espacio de parqueadero no está activo.");
        }
        if (p.unitId() != null && !p.unitId().equals(unitId)) {
            throw ApiException.conflict("PARKING_ASSIGNED_OTHER_UNIT", "El espacio de parqueadero está asignado a otro inmueble.");
        }
    }

    private void checkPlateFree(String plate, UUID selfId, UUID unitId) {
        if (plate == null) return;
        vehicles.findByPlate(plate).filter(o -> !o.id().equals(selfId)).ifPresent(other -> {
            if (!other.unitId().equals(unitId)) {
                access.saveAlert("PLATE_OTHER_UNIT", other.id(), plate, unitId,
                        "Se intentó registrar la placa " + plate + " que ya pertenece a otro inmueble.",
                        CurrentUser.get().map(AuthenticatedUser::userId).orElse(null));
            }
            throw plateTaken();
        });
    }

    /** Sin regla configurada para el tipo = sin límite. */
    private int limitFor(UUID unitId, String typeCode) {
        Integer l = catalog.effectiveLimit(unitId, typeCode);
        return l == null ? Integer.MAX_VALUE : l;
    }

    private static ApiException plateTaken() {
        return ApiException.conflict("PLATE_ALREADY_REGISTERED", "La placa ya está registrada en la copropiedad.");
    }

    private static ApiException limitReached(int limit, VehicleType type) {
        return ApiException.conflict("VEHICLE_LIMIT_REACHED", "El inmueble ya alcanzó el máximo de " + limit
                + " vehículo(s) de tipo " + type.name() + ".");
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
