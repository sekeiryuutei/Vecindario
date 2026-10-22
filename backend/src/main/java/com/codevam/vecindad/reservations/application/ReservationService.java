package com.codevam.vecindad.reservations.application;

import com.codevam.vecindad.reservations.application.port.out.CommonAreaPort;
import com.codevam.vecindad.reservations.application.port.out.ReservationPort;
import com.codevam.vecindad.reservations.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    public record CreateReservationCommand(
            UUID commonAreaId,
            UUID unitId,
            UUID personId,
            Instant startTime,
            Instant endTime,
            PaymentMethod paymentMethod,
            String paymentReference,
            String notes,
            boolean acceptedTerms
    ) {
        public CreateReservationCommand(UUID commonAreaId, UUID unitId, UUID personId,
                                        Instant startTime, Instant endTime, PaymentMethod paymentMethod,
                                        String paymentReference, String notes) {
            this(commonAreaId, unitId, personId, startTime, endTime, paymentMethod,
                    paymentReference, notes, true);
        }
    }

    private final CommonAreaPort commonAreaPort;
    private final ReservationPort reservationPort;

    public ReservationService(CommonAreaPort commonAreaPort, ReservationPort reservationPort) {
        this.commonAreaPort = commonAreaPort;
        this.reservationPort = reservationPort;
    }

    public Reservation getById(UUID id) {
        return reservationPort.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reserva no encontrada."));
    }

    public PageResult<Reservation> search(UUID commonAreaId, UUID unitId, ReservationStatus status,
                                          Instant from, Instant to, int page, int size) {
        return reservationPort.search(commonAreaId, unitId, status, from, to, page, size);
    }

    public List<Reservation> findByUnit(UUID unitId) {
        return reservationPort.findByUnit(unitId);
    }

    @Transactional
    public Reservation createReservation(UUID userId, CreateReservationCommand cmd) {
        // El bloqueo por zona serializa las reservas concurrentes. Sin él, dos
        // peticiones pueden ver la franja libre y ambas insertar.
        CommonArea area = commonAreaPort.lockById(cmd.commonAreaId())
                .orElseThrow(() -> ApiException.notFound("Zona común no encontrada."));
        if (!area.isActive()) {
            throw ApiException.conflict("COMMON_AREA_INACTIVE", "La zona común se encuentra inactiva o en mantenimiento.");
        }
        if (area.terms() != null && !area.terms().isBlank() && !cmd.acceptedTerms()) {
            throw ApiException.badRequest("TERMS_NOT_ACCEPTED",
                    "Debes aceptar los términos y condiciones de la zona común para continuar.");
        }

        // 1. Bloqueo por mora según billing_settings
        if (reservationPort.isUnitBlockedByDebt(cmd.unitId())) {
            throw ApiException.conflict("RESERVATIONS_BLOCKED_BY_DEBT",
                    "El inmueble presenta cartera vencida. No está autorizada la reserva de zonas comunes hasta encontrarse a paz y salvo.");
        }

        Instant now = Instant.now();
        if (cmd.startTime().isBefore(now)) {
            throw ApiException.badRequest("PAST_DATE", "La reserva no puede realizarse en el pasado.");
        }
        if (!cmd.startTime().isBefore(cmd.endTime())) {
            throw ApiException.badRequest("INVALID_TIMES", "La hora de inicio debe ser anterior a la hora de fin.");
        }

        // 2. Validación de anticipación
        long hoursAdvance = Duration.between(now, cmd.startTime()).toHours();
        if (hoursAdvance < area.minAdvanceHours()) {
            throw ApiException.badRequest("MIN_ADVANCE_NOT_MET", "La reserva requiere al menos " + area.minAdvanceHours() + " horas de anticipación.");
        }
        long daysAdvance = Duration.between(now, cmd.startTime()).toDays();
        if (daysAdvance > area.maxAdvanceDays()) {
            throw ApiException.badRequest("MAX_ADVANCE_EXCEEDED", "No se pueden hacer reservas con más de " + area.maxAdvanceDays() + " días de anticipación.");
        }

        // 3. Validación de horario de funcionamiento
        ZonedDateTime zonedStart = cmd.startTime().atZone(BOGOTA);
        ZonedDateTime zonedEnd = cmd.endTime().atZone(BOGOTA);
        LocalTime localStart = zonedStart.toLocalTime();
        LocalTime localEnd = zonedEnd.toLocalTime();
        if (!zonedStart.toLocalDate().equals(zonedEnd.toLocalDate())
                || localStart.isBefore(area.openingTime()) || localEnd.isAfter(area.closingTime())) {
            throw ApiException.badRequest("OUTSIDE_OPERATING_HOURS", "El horario reservado está fuera del horario de funcionamiento ("
                    + area.openingTime() + " a " + area.closingTime() + ").");
        }

        // 4. Duración y slots máximos
        long durationMinutes = Duration.between(cmd.startTime(), cmd.endTime()).toMinutes();
        if (durationMinutes % area.slotDurationMinutes() != 0) {
            throw ApiException.badRequest("INVALID_SLOT_DURATION", "La duración debe ser múltiplo de bloques de " + area.slotDurationMinutes() + " minutos.");
        }
        long slots = durationMinutes / area.slotDurationMinutes();
        if (slots > area.maxSlotsPerReservation()) {
            throw ApiException.badRequest("MAX_SLOTS_EXCEEDED", "Se excede el máximo de " + area.maxSlotsPerReservation() + " bloques por reserva.");
        }

        // 5. Máximo de reservas activas por inmueble
        int activeCount = reservationPort.countActiveByUnit(cmd.unitId());
        if (activeCount >= area.maxActiveReservationsPerUnit()) {
            throw ApiException.conflict("MAX_RESERVATIONS_REACHED",
                    "El inmueble ya cuenta con " + activeCount + " reservas activas (máximo permitido: " + area.maxActiveReservationsPerUnit() + ").");
        }

        // 6. Concurrencia y verificación de traslape
        List<Reservation> overlaps = reservationPort.findOverlapping(cmd.commonAreaId(), cmd.startTime(), cmd.endTime(), null);
        if (!overlaps.isEmpty()) {
            throw ApiException.conflict("SLOT_ALREADY_RESERVED", "La zona común ya se encuentra reservada en el horario solicitado.");
        }

        // 7. Cálculo de precio
        BigDecimal totalPrice = BigDecimal.ZERO;
        if (area.requiresPayment()) {
            totalPrice = area.pricePerSlot().multiply(BigDecimal.valueOf(slots)).setScale(2, RoundingMode.HALF_UP);
        }

        boolean needsApproval = requiresApproval(area, cmd.startTime(), cmd.endTime());
        boolean needsPayment = area.requiresPayment() && totalPrice.signum() > 0;
        ReservationStatus initialStatus = needsPayment
                ? ReservationStatus.PENDING_PAYMENT
                : needsApproval ? ReservationStatus.PENDING_APPROVAL : ReservationStatus.APPROVED;

        Reservation reservation = new Reservation(
                UUID.randomUUID(),
                area.id(),
                cmd.unitId(),
                cmd.personId(),
                userId,
                cmd.startTime(),
                cmd.endTime(),
                initialStatus,
                totalPrice,
                area.depositAmount(),
                cmd.paymentMethod() == null ? PaymentMethod.NONE : cmd.paymentMethod(),
                cmd.paymentReference(),
                null,
                null,
                cmd.notes(),
                now,
                now
        );

        return reservationPort.insert(reservation);
    }

    /**
     * Confirma el pago de una reserva. Si el horario cae en la ventana de
     * aprobación configurada, pasa a revisión de administración; de lo
     * contrario queda aprobada automáticamente.
     */
    @Transactional
    public boolean markPaid(UUID reservationId, String paymentReference) {
        Reservation r = getById(reservationId);
        if (r.status() != ReservationStatus.PENDING_PAYMENT) {
            return false;
        }
        CommonArea area = commonAreaPort.findById(r.commonAreaId())
                .orElseThrow(() -> ApiException.notFound("Zona común no encontrada."));
        ReservationStatus next = requiresApproval(area, r.startTime(), r.endTime())
                ? ReservationStatus.PENDING_APPROVAL
                : ReservationStatus.APPROVED;
        return reservationPort.markPaid(reservationId, next, paymentReference);
    }

    private boolean requiresApproval(CommonArea area, Instant start, Instant end) {
        if (area.requiresApproval()) {
            return true;
        }
        if (area.approvalStartTime() == null) {
            return false;
        }
        LocalTime windowStart = area.approvalStartTime();
        LocalTime windowEnd = area.approvalEndTime() != null ? area.approvalEndTime() : area.closingTime();
        LocalTime localStart = start.atZone(BOGOTA).toLocalTime();
        LocalTime localEnd = end.atZone(BOGOTA).toLocalTime();
        return localStart.isBefore(windowEnd) && localEnd.isAfter(windowStart);
    }

    @Transactional
    public Reservation approveReservation(UUID reservationId) {
        Reservation r = getById(reservationId);
        if (r.status() != ReservationStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("RESERVATION_NOT_PENDING", "La reserva no está pendiente de aprobación.");
        }
        reservationPort.updateStatus(reservationId, ReservationStatus.APPROVED, null, null);
        return getById(reservationId);
    }

    @Transactional
    public Reservation rejectReservation(UUID reservationId, String reason) {
        Reservation r = getById(reservationId);
        if (r.status() != ReservationStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("RESERVATION_NOT_PENDING", "La reserva no está pendiente de aprobación.");
        }
        reservationPort.updateStatus(reservationId, ReservationStatus.REJECTED, reason, null);
        return getById(reservationId);
    }

    @Transactional
    public Reservation cancelReservation(UUID reservationId, String reason, boolean isAdmin) {
        Reservation r = getById(reservationId);
        if (r.status() != ReservationStatus.APPROVED
                && r.status() != ReservationStatus.PENDING_APPROVAL
                && r.status() != ReservationStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("RESERVATION_CANNOT_BE_CANCELLED", "La reserva ya se encuentra " + r.status());
        }

        if (!isAdmin) {
            CommonArea area = commonAreaPort.findById(r.commonAreaId()).orElseThrow();
            Instant now = Instant.now();
            long hoursToStart = Duration.between(now, r.startTime()).toHours();
            if (hoursToStart < area.cancellationDeadlineHours()) {
                throw ApiException.conflict("CANCELLATION_DEADLINE_EXCEEDED",
                        "El plazo límite de cancelación (" + area.cancellationDeadlineHours() + " horas de anticipación) ha vencido.");
            }
        }

        reservationPort.updateStatus(reservationId, ReservationStatus.CANCELLED, null, reason);
        return getById(reservationId);
    }

    public List<TimeSlot> getAvailability(UUID commonAreaId, LocalDate date) {
        CommonArea area = commonAreaPort.findById(commonAreaId)
                .orElseThrow(() -> ApiException.notFound("Zona común no encontrada."));

        ZonedDateTime dayStart = date.atStartOfDay(BOGOTA);
        ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(BOGOTA);

        List<Reservation> activeReservations = reservationPort.findByCommonAreaAndRange(
                commonAreaId, dayStart.toInstant(), dayEnd.toInstant());

        List<TimeSlot> slots = new ArrayList<>();
        LocalTime current = area.openingTime();
        while (current.isBefore(area.closingTime())) {
            LocalTime next = current.plusMinutes(area.slotDurationMinutes());
            if (next.isBefore(current) || next.isAfter(area.closingTime())) {
                break;
            }
            Instant slotStart = date.atTime(current).atZone(BOGOTA).toInstant();
            Instant slotEnd = date.atTime(next).atZone(BOGOTA).toInstant();

            boolean isAvailable = activeReservations.stream().noneMatch(r ->
                    r.startTime().isBefore(slotEnd) && r.endTime().isAfter(slotStart));

            slots.add(new TimeSlot(slotStart, slotEnd, isAvailable));
            current = next;
        }

        return slots;
    }
}
