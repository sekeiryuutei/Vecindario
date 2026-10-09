package com.codevam.vecindad.communications.application;

import com.codevam.vecindad.communications.application.port.out.CommunicationPort;
import com.codevam.vecindad.communications.domain.Communication;
import com.codevam.vecindad.communications.domain.CommunicationPriority;
import com.codevam.vecindad.communications.domain.CommunicationStatus;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CommunicationService {

    public record CreateCommunicationCommand(
            String title,
            String summary,
            String content,
            CommunicationPriority priority,
            TargetAudience targetAudience,
            String targetTower,
            boolean isPinned,
            Instant expiresAt,
            boolean publishNow
    ) {}

    private final CommunicationPort port;

    public CommunicationService(CommunicationPort port) {
        this.port = port;
    }

    public Communication getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("Comunicado no encontrado."));
    }

    public PageResult<Communication> search(CommunicationStatus status, int page, int size) {
        return port.search(status, page, size);
    }

    public PageResult<Communication> searchForAudience(TargetAudience audience, String tower, int page, int size) {
        return port.searchForAudience(audience, tower, page, size);
    }

    @Transactional
    public Communication create(UUID createdBy, CreateCommunicationCommand cmd) {
        Instant now = Instant.now();
        Communication c = new Communication(
                UUID.randomUUID(),
                cmd.title(),
                cmd.summary(),
                cmd.content(),
                cmd.priority() == null ? CommunicationPriority.NORMAL : cmd.priority(),
                cmd.targetAudience() == null ? TargetAudience.ALL : cmd.targetAudience(),
                cmd.targetTower(),
                cmd.publishNow() ? CommunicationStatus.PUBLISHED : CommunicationStatus.DRAFT,
                cmd.isPinned(),
                createdBy,
                cmd.publishNow() ? now : null,
                cmd.expiresAt(),
                now,
                now
        );
        return port.insert(c);
    }

    @Transactional
    public Communication publish(UUID id) {
        Communication c = getById(id);
        Communication updated = new Communication(
                c.id(), c.title(), c.summary(), c.content(), c.priority(),
                c.targetAudience(), c.targetTower(), CommunicationStatus.PUBLISHED,
                c.isPinned(), c.createdBy(), Instant.now(), c.expiresAt(),
                c.createdAt(), Instant.now()
        );
        port.update(updated);
        return getById(id);
    }

    @Transactional
    public Communication archive(UUID id) {
        Communication c = getById(id);
        Communication updated = new Communication(
                c.id(), c.title(), c.summary(), c.content(), c.priority(),
                c.targetAudience(), c.targetTower(), CommunicationStatus.ARCHIVED,
                c.isPinned(), c.createdBy(), c.publishedAt(), c.expiresAt(),
                c.createdAt(), Instant.now()
        );
        port.update(updated);
        return getById(id);
    }

    @Transactional
    public void delete(UUID id) {
        getById(id);
        port.delete(id);
    }

    @Transactional
    public void markAsRead(UUID communicationId, UUID userId) {
        getById(communicationId);
        port.markRead(communicationId, userId);
    }

    public boolean isRead(UUID communicationId, UUID userId) {
        return port.isRead(communicationId, userId);
    }
}

