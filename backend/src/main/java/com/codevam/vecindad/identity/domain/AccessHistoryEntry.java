package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record AccessHistoryEntry(AccessEvent event, String roleCode, UUID actorUserId, Instant occurredAt, String ip) {}
