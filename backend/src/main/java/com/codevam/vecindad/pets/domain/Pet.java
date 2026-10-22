package com.codevam.vecindad.pets.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Pet(UUID id, UUID unitId, String unitIdentifier, String name, Species species, String breed,
                  String color, LocalDate birthDate, BigDecimal weightKg, String microchip, boolean vaccinated,
                  LocalDate vaccineExpiresOn, String notes, PetStatus status, Instant createdAt, Instant updatedAt) {}
