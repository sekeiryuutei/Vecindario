package com.codevam.vecindad.unit;

import com.codevam.vecindad.camera.application.CameraService;
import com.codevam.vecindad.camera.domain.PlateDetectionResult;
import com.codevam.vecindad.camera.port.CameraProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraServiceTest {

    private CameraProvider provider;
    private CameraService service;

    @BeforeEach
    void setUp() {
        provider = Mockito.mock(CameraProvider.class);
        service = new CameraService(provider);
    }

    @Test
    void testDetectWithSimulatedPlate() {
        when(provider.simulateDetection("GATE_01", "ABC123"))
                .thenReturn(new PlateDetectionResult("ABC123", 0.98, Instant.now(), "GATE_01", true, "Mazda 3", "Autorizado"));

        PlateDetectionResult res = service.detect("GATE_01", "ABC123");
        assertEquals("ABC123", res.plate());
        assertTrue(res.registered());
        verify(provider).simulateDetection("GATE_01", "ABC123");
    }

    @Test
    void testCheckStatus() {
        when(provider.testConnection("GATE_01")).thenReturn(true);
        when(provider.isHealthy("GATE_01")).thenReturn(true);

        Map<String, Object> status = service.checkStatus("GATE_01");
        assertEquals("ONLINE", status.get("status"));
        assertEquals("GATE_01", status.get("cameraSource"));
    }
}

