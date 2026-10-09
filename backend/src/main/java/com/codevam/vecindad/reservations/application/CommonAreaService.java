package com.codevam.vecindad.reservations.application;

import com.codevam.vecindad.reservations.application.port.out.CommonAreaPort;
import com.codevam.vecindad.reservations.domain.CommonArea;
import com.codevam.vecindad.shared.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CommonAreaService {

    private final CommonAreaPort port;

    public CommonAreaService(CommonAreaPort port) {
        this.port = port;
    }

    public List<CommonArea> list(boolean onlyActive) {
        return port.list(onlyActive);
    }

    public CommonArea getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("Zona común no encontrada."));
    }

    @Transactional
    public CommonArea create(CommonArea area) {
        if (area.openingTime().isAfter(area.closingTime()) || area.openingTime().equals(area.closingTime())) {
            throw ApiException.badRequest("INVALID_TIMES", "La hora de apertura debe ser anterior a la hora de cierre.");
        }
        CommonArea toSave = new CommonArea(
                UUID.randomUUID(), area.name(), area.description(), area.capacity(),
                area.requiresPayment(), area.pricePerSlot(), area.depositAmount(),
                area.requiresApproval(), area.openingTime(), area.closingTime(),
                area.slotDurationMinutes(), area.maxSlotsPerReservation(),
                area.maxActiveReservationsPerUnit(), area.minAdvanceHours(),
                area.maxAdvanceDays(), area.cancellationDeadlineHours(),
                true, Instant.now(), Instant.now()
        );
        return port.insert(toSave);
    }

    @Transactional
    public CommonArea update(UUID id, CommonArea area) {
        CommonArea existing = getById(id);
        if (area.openingTime().isAfter(area.closingTime()) || area.openingTime().equals(area.closingTime())) {
            throw ApiException.badRequest("INVALID_TIMES", "La hora de apertura debe ser anterior a la hora de cierre.");
        }
        CommonArea updated = new CommonArea(
                existing.id(), area.name(), area.description(), area.capacity(),
                area.requiresPayment(), area.pricePerSlot(), area.depositAmount(),
                area.requiresApproval(), area.openingTime(), area.closingTime(),
                area.slotDurationMinutes(), area.maxSlotsPerReservation(),
                area.maxActiveReservationsPerUnit(), area.minAdvanceHours(),
                area.maxAdvanceDays(), area.cancellationDeadlineHours(),
                area.isActive(), existing.createdAt(), Instant.now()
        );
        port.update(updated);
        return getById(id);
    }

    @Transactional
    public void delete(UUID id) {
        getById(id);
        port.delete(id);
    }
}
