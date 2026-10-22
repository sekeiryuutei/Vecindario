package com.codevam.vecindad.unitfiles.domain;

import java.time.Instant;
import java.util.UUID;

public record UnitFileNote(UUID id, UUID unitId, String title, String body, boolean internal,
                           UUID createdBy, Instant createdAt, Instant updatedAt) {}
