package com.codevam.vecindad.camera.application;

import com.codevam.vecindad.camera.domain.PlateDetectionResult;
import com.codevam.vecindad.camera.port.CameraProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CameraService {

    private final CameraProvider cameraProvider;

    public CameraService(CameraProvider cameraProvider) {
        this.cameraProvider = cameraProvider;
    }

    public PlateDetectionResult detect(String cameraSource, String simulatedPlate) {
        if (simulatedPlate != null && !simulatedPlate.isBlank()) {
            return cameraProvider.simulateDetection(cameraSource, simulatedPlate);
        }
        return cameraProvider.capturePlate(cameraSource);
    }

    public Map<String, Object> checkStatus(String cameraSource) {
        String source = (cameraSource == null || cameraSource.isBlank()) ? "GATE_CAMERA_01" : cameraSource;
        boolean connected = cameraProvider.testConnection(source);
        boolean healthy = cameraProvider.isHealthy(source);

        return Map.of(
                "cameraSource", source,
                "status", (connected && healthy) ? "ONLINE" : "OFFLINE",
                "fps", 30,
                "resolution", "1080p",
                "lprEngine", "CodeVam Neural LPR v2.1"
        );
    }
}

