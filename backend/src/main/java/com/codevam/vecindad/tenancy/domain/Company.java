package com.codevam.vecindad.tenancy.domain;

import java.time.Instant;
import java.util.UUID;

public record Company(UUID id, String name, String nit, String status, Instant createdAt) {}
