package com.codevam.vecindad.vehicles.domain;

import java.util.UUID;

public record ParkingSpace(UUID id, String code, String kind, UUID unitId, String status) {}
