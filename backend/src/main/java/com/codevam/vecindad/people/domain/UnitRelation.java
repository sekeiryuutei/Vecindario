package com.codevam.vecindad.people.domain;

import java.time.LocalDate;
import java.util.UUID;

public record UnitRelation(UUID id, UUID unitId, String unitIdentifier, UUID personId, String personName,
                           RelationType type, LocalDate startDate, LocalDate endDate, String status) {}
