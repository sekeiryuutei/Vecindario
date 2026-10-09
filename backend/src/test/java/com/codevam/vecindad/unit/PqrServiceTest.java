package com.codevam.vecindad.unit;

import com.codevam.vecindad.pqrs.application.PqrService;
import com.codevam.vecindad.pqrs.application.port.out.PqrPort;
import com.codevam.vecindad.pqrs.domain.Pqr;
import com.codevam.vecindad.pqrs.domain.PqrCategory;
import com.codevam.vecindad.pqrs.domain.PqrPriority;
import com.codevam.vecindad.pqrs.domain.PqrStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PqrServiceTest {

    private PqrPort pqrPort;
    private PqrService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        pqrPort = Mockito.mock(PqrPort.class);
        service = new PqrService(pqrPort);
        userId = UUID.randomUUID();

        when(pqrPort.nextTicketNumber()).thenReturn("PQR-2026-1001");
        when(pqrPort.insert(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void assigns24HourSlaAndHighPriorityToNoiseComplaints() {
        var cmd = new PqrService.CreatePqrCommand(
                UUID.randomUUID(), null, PqrCategory.CONVIVENCIA_RUIDO, PqrPriority.LOW,
                "Exceso de ruido", "Música a volumen excesivo en madrugada", false
        );

        Pqr created = service.create(userId, cmd);
        assertNotNull(created);
        assertEquals("PQR-2026-1001", created.ticketNumber());
        assertEquals(PqrPriority.HIGH, created.priority()); // Auto-elevated from LOW
        assertEquals(24, created.slaTargetHours());
        assertEquals(PqrStatus.OPEN, created.status());

        long hoursBetween = Duration.between(Instant.now(), created.slaDueAt()).toHours();
        assertEquals(24, hoursBetween);
    }

    @Test
    void assigns120HourSlaToPeticion() {
        var cmd = new PqrService.CreatePqrCommand(
                UUID.randomUUID(), null, PqrCategory.PETICION, PqrPriority.MEDIUM,
                "Copia de acta", "Solicito copia del acta de asamblea", false
        );

        Pqr created = service.create(userId, cmd);
        assertNotNull(created);
        assertEquals(120, created.slaTargetHours());
    }
}

