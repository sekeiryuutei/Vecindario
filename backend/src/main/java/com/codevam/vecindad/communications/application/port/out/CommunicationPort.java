package com.codevam.vecindad.communications.application.port.out;

import com.codevam.vecindad.communications.domain.Communication;
import com.codevam.vecindad.communications.domain.CommunicationStatus;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface CommunicationPort {
    Communication insert(Communication c);
    Optional<Communication> findById(UUID id);
    PageResult<Communication> search(CommunicationStatus status, int page, int size);
    PageResult<Communication> searchForAudience(TargetAudience audience, String tower, int page, int size);
    boolean update(Communication c);
    boolean delete(UUID id);
    void markRead(UUID communicationId, UUID userId);
    boolean isRead(UUID communicationId, UUID userId);
}

