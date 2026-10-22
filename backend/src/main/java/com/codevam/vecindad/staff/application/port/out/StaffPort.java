package com.codevam.vecindad.staff.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.staff.domain.StaffMember;
import com.codevam.vecindad.staff.domain.StaffStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StaffPort {
    Optional<StaffMember> findById(UUID id);
    PageResult<StaffMember> search(String q, StaffStatus status, int page, int size);
    boolean documentExists(String documentType, String documentNumber, UUID excludeId);
    void insert(StaffMember member);
    boolean update(StaffMember member);
    boolean softDelete(UUID id, UUID by, Instant at);
}
