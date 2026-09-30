package com.codevam.vecindad.properties.adapter.out;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PropertyUnitJpaRepository extends JpaRepository<PropertyUnitEntity, UUID>, JpaSpecificationExecutor<PropertyUnitEntity> {
    Optional<PropertyUnitEntity> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByIdentifierIgnoreCaseAndDeletedAtIsNull(String identifier);
    boolean existsByIdentifierIgnoreCaseAndDeletedAtIsNullAndIdNot(String identifier, UUID id);
}
