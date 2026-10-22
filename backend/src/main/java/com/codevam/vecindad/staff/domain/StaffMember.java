package com.codevam.vecindad.staff.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StaffMember(UUID id, String fullName, String documentType, String documentNumber, String position,
                          String area, String phone, String email, String shift, LocalDate startDate,
                          LocalDate endDate, StaffStatus status, String notes, Instant createdAt,
                          Instant updatedAt) {}
