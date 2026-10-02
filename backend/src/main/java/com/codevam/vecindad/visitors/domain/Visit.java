package com.codevam.vecindad.visitors.domain;

import java.time.Instant;
import java.util.UUID;

public record Visit(UUID id, UUID invitationId, UUID unitId, String unitIdentifier, String visitorName,
                    String documentNumber, String phone, String plate, int peopleCount, String source,
                    VisitStatus status, Instant requestedAt, Instant decidedAt, String decisionNote,
                    Instant entryTime, Instant exitTime, String note) {}
