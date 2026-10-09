package com.codevam.vecindad.camera.adapter;

import com.codevam.vecindad.camera.domain.PlateDetectionResult;
import com.codevam.vecindad.camera.port.CameraProvider;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class MockCameraProvider implements CameraProvider {

    private final TenantJdbc t;

    public MockCameraProvider(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public PlateDetectionResult capturePlate(String cameraSource) {
        // Query an authorized registered vehicle from tenant or simulate default
        String source = (cameraSource == null || cameraSource.isBlank()) ? "GATE_CAMERA_01" : cameraSource;
        String query = t.q("SELECT plate, brand, model, color FROM {s}.vehicles WHERE status = 'AUTHORIZED' LIMIT 1");
        List<Map<String, Object>> rows = t.jdbc().queryForList(query);
        if (!rows.isEmpty()) {
            Map<String, Object> r = rows.get(0);
            String plate = (String) r.get("plate");
            String details = r.get("brand") + " " + r.get("model") + " (" + r.get("color") + ")";
            return new PlateDetectionResult(plate, 0.96, Instant.now(), source, true, details, "Vehículo registrado en la copropiedad");
        }
        return new PlateDetectionResult("AAA001", 0.92, Instant.now(), source, false, "Desconocido", "Vehículo no registrado");
    }

    @Override
    public PlateDetectionResult simulateDetection(String cameraSource, String plate) {
        String source = (cameraSource == null || cameraSource.isBlank()) ? "GATE_CAMERA_01" : cameraSource;
        String cleanPlate = plate != null ? plate.trim().toUpperCase() : "ABC123";

        String query = t.q("SELECT v.plate, v.brand, v.model, v.color, u.identifier AS unit_num " +
                "FROM {s}.vehicles v " +
                "JOIN {s}.property_units u ON u.id = v.unit_id " +
                "WHERE UPPER(v.plate) = ? AND v.status = 'AUTHORIZED'");

        List<Map<String, Object>> rows = t.jdbc().queryForList(query, cleanPlate);

        if (!rows.isEmpty()) {
            Map<String, Object> r = rows.get(0);
            String details = r.get("brand") + " " + r.get("model") + " - Apto " + r.get("unit_num");
            return new PlateDetectionResult(cleanPlate, 0.98, Instant.now(), source, true, details, "Acceso Autorizado: Vehículo residente");
        } else {
            return new PlateDetectionResult(cleanPlate, 0.94, Instant.now(), source, false, "Sin registro previo", "Atención: Vehículo no registrado / Visitante");
        }
    }

    @Override
    public boolean testConnection(String cameraSource) {
        return true;
    }

    @Override
    public boolean isHealthy(String cameraSource) {
        return true;
    }
}
