package com.codevam.vecindad.unit;

import com.codevam.vecindad.reservations.application.ReservationService;
import com.codevam.vecindad.reservations.application.port.out.CommonAreaPort;
import com.codevam.vecindad.reservations.application.port.out.ReservationPort;
import com.codevam.vecindad.reservations.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReservationServiceTest {

    private CommonAreaPort commonAreaPort;
    private ReservationPort reservationPort;
    private ReservationService service;

    private CommonArea area;
    private UUID areaId;
    private UUID unitId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        commonAreaPort = Mockito.mock(CommonAreaPort.class);
        reservationPort = Mockito.mock(ReservationPort.class);
        service = new ReservationService(commonAreaPort, reservationPort);

        areaId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        userId = UUID.randomUUID();

        area = new CommonArea(
                areaId, "Salón Social", "Salón de eventos", 50,
                true, new BigDecimal("50000.00"), new BigDecimal("100000.00"), true,
                LocalTime.of(8, 0), LocalTime.of(22, 0), 60, 4, 2, 12, 30, 24, true,
                Instant.now(), Instant.now()
        );

        when(commonAreaPort.findById(areaId)).thenReturn(Optional.of(area));
        when(reservationPort.isUnitBlockedByDebt(unitId)).thenReturn(false);
        when(reservationPort.countActiveByUnit(unitId)).thenReturn(0);
        when(reservationPort.findOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        when(reservationPort.insert(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void blocksReservationWhenUnitHasOverdueDebt() {
        when(reservationPort.isUnitBlockedByDebt(unitId)).thenReturn(true);

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(19, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var cmd = new ReservationService.CreateReservationCommand(
                areaId, unitId, null, start, end, PaymentMethod.CASH, null, "Cumpleaños"
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createReservation(userId, cmd));
        assertEquals("RESERVATIONS_BLOCKED_BY_DEBT", ex.getCode());
    }

    @Test
    void blocksReservationWhenSlotAlreadyReserved() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(19, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        Reservation existing = new Reservation(
                UUID.randomUUID(), areaId, UUID.randomUUID(), null, UUID.randomUUID(),
                start, end, ReservationStatus.APPROVED, BigDecimal.ZERO, BigDecimal.ZERO,
                PaymentMethod.NONE, null, null, null, null, Instant.now(), Instant.now()
        );
        when(reservationPort.findOverlapping(any(), any(), any(), any())).thenReturn(List.of(existing));

        var cmd = new ReservationService.CreateReservationCommand(
                areaId, unitId, null, start, end, PaymentMethod.CASH, null, "Fiesta"
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.createReservation(userId, cmd));
        assertEquals("SLOT_ALREADY_RESERVED", ex.getCode());
    }

    @Test
    void createsReservationSuccessfullyWhenAvailableAndPaid() {
        // Horario 14:00 a 16:00 en Bogotá (UTC 19:00 a 21:00) dentro de 8:00 a 22:00
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(19, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var cmd = new ReservationService.CreateReservationCommand(
                areaId, unitId, null, start, end, PaymentMethod.WOMPI, "REF-123", "Reunión"
        );

        Reservation created = service.createReservation(userId, cmd);
        assertNotNull(created);
        assertEquals(ReservationStatus.PENDING_APPROVAL, created.status());
        assertEquals(new BigDecimal("100000.00"), created.totalPrice()); // 2 slots * 50.000
    }
}
