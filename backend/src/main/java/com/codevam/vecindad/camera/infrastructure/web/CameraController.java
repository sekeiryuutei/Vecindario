package com.codevam.vecindad.camera.infrastructure.web;

import com.codevam.vecindad.camera.application.CameraService;
import com.codevam.vecindad.camera.domain.PlateDetectionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cameras")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('VEHICLES_VIEW', 'VEHICLES_MANAGE')")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam(required = false, defaultValue = "GATE_MAIN_LPR") String source) {
        return ResponseEntity.ok(cameraService.checkStatus(source));
    }

    public record DetectRequest(String source, String plate) {}

    @PostMapping("/detect")
    @PreAuthorize("hasAnyAuthority('VEHICLES_VIEW', 'VEHICLES_MANAGE')")
    public ResponseEntity<PlateDetectionResult> detect(@RequestBody(required = false) DetectRequest request) {
        String source = request != null ? request.source() : "GATE_MAIN_LPR";
        String plate = request != null ? request.plate() : null;
        return ResponseEntity.ok(cameraService.detect(source, plate));
    }
}

