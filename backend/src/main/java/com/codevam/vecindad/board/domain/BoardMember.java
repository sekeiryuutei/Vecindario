package com.codevam.vecindad.board.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BoardMember(UUID id, UUID personId, String personName, String documentNumber, String email, String phone,
                          BoardPosition position, LocalDate startDate, LocalDate endDate, BoardStatus status,
                          String notes, Instant createdAt, Instant updatedAt) {}
