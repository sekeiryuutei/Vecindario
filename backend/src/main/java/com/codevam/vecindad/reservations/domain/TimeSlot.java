package com.codevam.vecindad.reservations.domain;

import java.time.Instant;

public record TimeSlot(Instant startTime, Instant endTime, boolean available) {}

