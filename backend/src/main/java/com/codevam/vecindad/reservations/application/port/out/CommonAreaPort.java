package com.codevam.vecindad.reservations.application.port.out;

import com.codevam.vecindad.reservations.domain.CommonArea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommonAreaPort {
    CommonArea insert(CommonArea area);
    Optional<CommonArea> findById(UUID id);

    /**
     * Bloquea la zona común hasta el final de la transacción. Serializa las
     * reservas de la misma zona para que dos peticiones concurrentes no pasen
     * ambas la validación de traslape.
     */
    Optional<CommonArea> lockById(UUID id);

    List<CommonArea> list(boolean onlyActive);
    boolean update(CommonArea area);
    boolean delete(UUID id);
}

