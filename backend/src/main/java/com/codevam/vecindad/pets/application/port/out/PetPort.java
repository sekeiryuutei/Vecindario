package com.codevam.vecindad.pets.application.port.out;

import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PetPort {
    Optional<Pet> findById(UUID id);
    Optional<Pet> findByMicrochip(String microchip);
    PageResult<Pet> search(String q, UUID unitId, Species species, PetStatus status, int page, int size);
    List<Pet> findByUnitIds(List<UUID> unitIds);
    void insert(Pet pet);
    boolean update(Pet pet);
    boolean softDelete(UUID id, UUID by, Instant at);
}
