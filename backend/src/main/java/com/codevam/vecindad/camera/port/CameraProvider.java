package com.codevam.vecindad.camera.port;

import com.codevam.vecindad.camera.domain.PlateDetectionResult;

public interface CameraProvider {

    PlateDetectionResult capturePlate(String cameraSource);

    PlateDetectionResult simulateDetection(String cameraSource, String plate);

    boolean testConnection(String cameraSource);

    boolean isHealthy(String cameraSource);
}

