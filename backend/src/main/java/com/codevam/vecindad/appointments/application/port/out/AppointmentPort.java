package com.codevam.vecindad.appointments.application.port.out;

import com.codevam.vecindad.appointments.domain.Appointment;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentPort {
    Optional<Appointment> findById(UUID id);
    PageResult<Appointment> search(AppointmentStatus status, Instant from, Instant to, String q, int page, int size);
    List<Appointment> listByPerson(UUID personId, Instant from);
    boolean slotTaken(Instant scheduledAt, UUID excludeId);
    void insert(Appointment appointment);
    boolean update(Appointment appointment);
    boolean softDelete(UUID id, UUID by, Instant at);
}
