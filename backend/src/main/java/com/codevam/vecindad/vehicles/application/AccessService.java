package com.codevam.vecindad.vehicles.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.vehicles.application.port.out.AccessPort;
import com.codevam.vecindad.vehicles.application.port.out.VehiclePort;
import com.codevam.vecindad.vehicles.domain.AccessEvent;
import com.codevam.vecindad.vehicles.domain.Presence;
import com.codevam.vecindad.vehicles.domain.SecurityAlert;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import com.codevam.vecindad.vehicles.domain.VehicleStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Portería: consulta por placa, entradas y salidas manuales (la cámara/LPR llegará por el mismo caso de uso). */
@Service
public class AccessService {

    /** Vista mínima para portería: sin propietario ni datos financieros. */
    public record VehicleLookup(UUID vehicleId, String plate, String typeCode, String brand, String color,
                                String unitIdentifier, String status, String presence) {}

    private final VehiclePort vehicles;
    private final AccessPort access;
    private final AuditService audit;

    public AccessService(VehiclePort vehicles, AccessPort access, AuditService audit) {
        this.vehicles = vehicles;
        this.access = access;
        this.audit = audit;
    }

    public VehicleLookup lookup(String plate) {
        CurrentUser.requireTenant();
        Vehicle v = vehicles.findByPlate(normalize(plate))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "VEHICLE_NOT_REGISTERED",
                        "La placa no está registrada en la copropiedad."));
        return new VehicleLookup(v.id(), v.plate(), v.typeCode(), v.brand(), v.color(), v.unitIdentifier(),
                v.status().name(), v.presence().name());
    }

    public List<Vehicle> inside() {
        CurrentUser.requireTenant();
        return vehicles.inside();
    }

    public AccessEvent registerEntry(String rawPlate, String method, String cameraId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        String plate = normalize(rawPlate);
        Vehicle v = vehicles.findByPlate(plate).orElse(null);
        if (v == null) {
            access.saveAlert("UNKNOWN_PLATE", null, plate, null, "Intento de ingreso con la placa " + plate + ", no registrada.", me.userId());
            audit.log(me.tenantId(), "VEHICLE_ENTRY_DENIED", "VEHICLE", plate, false, Map.of("reason", "unknown_plate"));
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "VEHICLE_NOT_REGISTERED",
                    "La placa " + plate + " no está registrada ni autorizada en la copropiedad.");
        }
        AccessEvent ev = access.registerEntry(v.id(), me.userId(), source(cameraId), method(method), cameraId).orElse(null);
        if (ev != null) {
            audit.log(me.tenantId(), "VEHICLE_ENTRY", "VEHICLE", v.id().toString(), true, Map.of("plate", plate, "eventId", ev.id().toString()));
            return ev;
        }
        // No cumplió la condición atómica: se relee para dar el motivo exacto y alertar.
        Vehicle cur = vehicles.findById(v.id()).orElse(v);
        if (cur.status() == VehicleStatus.BLOCKED) {
            access.saveAlert("VEHICLE_BLOCKED", cur.id(), plate, cur.unitId(), "Intento de ingreso de vehículo bloqueado " + plate + ".", me.userId());
            audit.log(me.tenantId(), "VEHICLE_ENTRY_DENIED", "VEHICLE", cur.id().toString(), false, Map.of("reason", "blocked"));
            throw ApiException.forbidden("VEHICLE_BLOCKED", "Este vehículo está bloqueado y no puede ingresar.");
        }
        access.saveAlert("DUPLICATE_ENTRY", cur.id(), plate, cur.unitId(), "El vehículo " + plate + " intentó ingresar estando ya dentro.", me.userId());
        audit.log(me.tenantId(), "VEHICLE_ENTRY_DENIED", "VEHICLE", cur.id().toString(), false, Map.of("reason", "already_inside"));
        throw ApiException.conflict("VEHICLE_ALREADY_INSIDE", "Este vehículo ya se encuentra dentro de la copropiedad.");
    }

    public AccessEvent registerExit(String rawPlate, String method, String cameraId) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        String plate = normalize(rawPlate);
        Vehicle v = vehicles.findByPlate(plate).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                "VEHICLE_NOT_REGISTERED", "La placa " + plate + " no está registrada en la copropiedad."));
        AccessEvent ev = access.registerExit(v.id(), me.userId(), source(cameraId), method(method), cameraId).orElse(null);
        if (ev != null) {
            audit.log(me.tenantId(), "VEHICLE_EXIT", "VEHICLE", v.id().toString(), true, Map.of("plate", plate, "eventId", ev.id().toString()));
            return ev;
        }
        access.saveAlert("EXIT_WITHOUT_ENTRY", v.id(), plate, v.unitId(), "Se intentó registrar la salida de " + plate + " sin entrada previa.", me.userId());
        audit.log(me.tenantId(), "VEHICLE_EXIT_DENIED", "VEHICLE", v.id().toString(), false, Map.of("reason", "not_inside"));
        throw ApiException.conflict("VEHICLE_NOT_INSIDE", "Este vehículo no figura dentro de la copropiedad.");
    }

    public PageResult<AccessEvent> events(String plate, UUID unitId, Instant from, Instant to, int page, int size) {
        CurrentUser.requireTenant();
        String p = plate == null || plate.isBlank() ? null : normalize(plate);
        return access.events(p, unitId, from, to, Paging.page(page), Paging.size(size));
    }

    public PageResult<SecurityAlert> alerts(boolean onlyOpen, int page, int size) {
        CurrentUser.requireTenant();
        return access.alerts(onlyOpen, Paging.page(page), Paging.size(size));
    }

    public void resolveAlert(UUID id) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (!access.resolveAlert(id, me.userId())) {
            throw ApiException.notFound("La alerta no existe o ya fue resuelta.");
        }
        audit.log(me.tenantId(), "SECURITY_ALERT_RESOLVED", "SECURITY_ALERT", id.toString(), true, Map.of());
    }

    private static String normalize(String plate) {
        if (plate == null || plate.isBlank()) {
            throw ApiException.badRequest("PLATE_REQUIRED", "Debes indicar la placa.");
        }
        return plate.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    private static String source(String cameraId) {
        return cameraId == null || cameraId.isBlank() ? "MANUAL" : "CAMERA";
    }

    private static String method(String method) {
        return method == null || method.isBlank() ? "PLATE_SEARCH" : method.trim().toUpperCase(Locale.ROOT);
    }
}
