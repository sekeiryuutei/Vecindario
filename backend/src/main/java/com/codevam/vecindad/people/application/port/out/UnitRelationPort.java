package com.codevam.vecindad.people.application.port.out;

import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.people.domain.UnitRelation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitRelationPort {
    UnitRelation add(UUID unitId, UUID personId, RelationType type, LocalDate start, UUID by);
    boolean end(UUID relationId, LocalDate end, UUID by);
    Optional<UnitRelation> find(UUID id);
    List<UnitRelation> listByUnit(UUID unitId);
    List<UnitRelation> listByPerson(UUID personId);
    List<MyUnit> unitsOfUser(UUID userId);
    boolean userHasUnit(UUID userId, UUID unitId);
}
