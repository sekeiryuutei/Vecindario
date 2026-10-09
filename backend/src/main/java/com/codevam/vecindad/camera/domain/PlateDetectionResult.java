package com.codevam.vecindad.camera.domain;

import java.time.Instant;

public record PlateDetectionResult(
        String plate,
        double confidence,
        Instant capturedAt,
        String cameraSource,
        boolean registered,
        String vehicleDetails,
        String message
) {
}

