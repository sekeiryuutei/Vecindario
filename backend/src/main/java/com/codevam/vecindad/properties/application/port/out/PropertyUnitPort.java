package com.codevam.vecindad.properties.application.port.out;

import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface PropertyUnitPort {
    PropertyUnit save(PropertyUnit unit);
    Optional<PropertyUnit> findActiveById(UUID id);
    boolean existsActiveIdentifier(String identifier, UUID excludeId);
    PageResult<PropertyUnit> search(String text, UnitType type, int page, int size);
}
