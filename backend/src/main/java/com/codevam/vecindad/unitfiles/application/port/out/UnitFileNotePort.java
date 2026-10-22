package com.codevam.vecindad.unitfiles.application.port.out;

import com.codevam.vecindad.unitfiles.domain.UnitFileNote;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitFileNotePort {
    List<UnitFileNote> listByUnit(UUID unitId, boolean includeInternal);
    Optional<UnitFileNote> findById(UUID id, UUID unitId);
    void insert(UnitFileNote note);
    boolean update(UnitFileNote note);
    boolean softDelete(UUID id, UUID unitId, UUID by, Instant at);
}
