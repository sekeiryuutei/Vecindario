package com.codevam.vecindad.identity.application.port.out;

import com.codevam.vecindad.identity.domain.*;
import com.codevam.vecindad.shared.model.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipPort {
    /** Solo devuelve acceso si usuario, membresía y copropiedad están ACTIVOS. */
    Optional<TenantAccess> findActiveAccess(UUID userId, UUID tenantId);
    List<TenantAccess> listActiveAccesses(UUID userId);
    Optional<Membership> find(UUID userId, UUID tenantId);
    /** Crea la membresía o la reactiva (conservando el historial). */
    void grant(UUID userId, UUID tenantId, String roleCode, UUID grantedBy);
    void updateRole(UUID userId, UUID tenantId, String roleCode);
    void updateStatus(UUID userId, UUID tenantId, MembershipStatus status, UUID actor);
    void touchActivity(UUID userId, UUID tenantId);
    PageResult<MemberView> list(UUID tenantId, int page, int size);
    Optional<MemberView> findView(UUID tenantId, UUID userId);
    List<AccessHistoryEntry> history(UUID userId, UUID tenantId);
    void addHistory(UUID userId, UUID tenantId, AccessEvent event, String roleCode, UUID actor, String ip);
}
