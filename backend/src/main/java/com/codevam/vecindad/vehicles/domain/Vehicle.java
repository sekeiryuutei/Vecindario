package com.codevam.vecindad.vehicles.domain;

import java.time.Instant;
import java.util.UUID;

public record Vehicle(UUID id, String typeCode, String plate, String brand, String model, String color,
                      Integer modelYear, UUID ownerPersonId, UUID unitId, String unitIdentifier,
                      UUID parkingSpaceId, VehicleStatus status, Presence presence, boolean primary,
                      Instant createdAt, Instant updatedAt) {}
