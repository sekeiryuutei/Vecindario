package com.codevam.vecindad.reservations.application.port.out;

import com.codevam.vecindad.reservations.domain.CommonArea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommonAreaPort {
    CommonArea insert(CommonArea area);
    Optional<CommonArea> findById(UUID id);
    List<CommonArea> list(boolean onlyActive);
    boolean update(CommonArea area);
    boolean delete(UUID id);
}

