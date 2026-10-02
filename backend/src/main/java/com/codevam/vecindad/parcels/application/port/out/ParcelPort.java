package com.codevam.vecindad.parcels.application.port.out;

import com.codevam.vecindad.parcels.domain.Parcel;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelPort {
    Parcel insert(Parcel p, UUID receivedBy);
    Optional<Parcel> findById(UUID id);
    /** unitIds null = todos; lista vacía = ninguno. status/q opcionales. */
    PageResult<Parcel> search(List<UUID> unitIds, String status, String q, int page, int size);
    boolean markDelivered(UUID id, String deliveredTo, UUID by);
    boolean markReturned(UUID id, String note, UUID by);
}
