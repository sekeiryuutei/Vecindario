package com.codevam.vecindad.identity.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.*;
import com.codevam.vecindad.identity.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.Tokens;
import com.codevam.vecindad.shared.web.ClientInfo;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Alta, cambio de rol, suspensión y revocación de miembros de una copropiedad, con historial. */
@Service
public class MemberService {
    private final UserPort users;
    private final MembershipPort memberships;
    private final OneTimeTokenPort oneTimeTokens;
    private final AccountMailPort mail;
    private final TenantPort tenants;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Clock clock;

    public MemberService(UserPort users, MembershipPort memberships, OneTimeTokenPort oneTimeTokens, AccountMailPort mail,
                         TenantPort tenants, AuditService audit, PasswordEncoder encoder, AppProperties props, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.oneTimeTokens = oneTimeTokens;
        this.mail = mail;
        this.tenants = tenants;
        this.audit = audit;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public MemberView addMember(UUID tenantId, AuthenticatedUser actor, String rawEmail, String fullName, String roleCode) {
        requireAssignableRole(actor, roleCode);
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> ApiException.notFound("La copropiedad no existe."));
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw ApiException.conflict("TENANT_NOT_ACTIVE", "La copropiedad no está activa.");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = users.findByEmail(email);
        User user = existing.orElseGet(() ->
                users.insert(email, fullName.trim(), encoder.encode(Tokens.newOpaqueToken()), UserStatus.INVITED, null));
        if (user.status() == UserStatus.DISABLED) {
            throw ApiException.conflict("USER_DISABLED", "El usuario está desactivado a nivel de plataforma.");
        }
        Optional<Membership> current = memberships.find(user.id(), tenantId);
        if (current.isPresent() && current.get().status() == MembershipStatus.ACTIVE) {
            throw ApiException.conflict("MEMBER_ALREADY_EXISTS", "Ese usuario ya pertenece a la copropiedad.");
        }
        UUID actorId = actor == null ? null : actor.userId();
        memberships.grant(user.id(), tenantId, roleCode, actorId);
        memberships.addHistory(user.id(), tenantId, AccessEvent.GRANTED, roleCode, actorId, ClientInfo.ip());
        if (user.status() == UserStatus.INVITED) {
            String raw = Tokens.newOpaqueToken();
            oneTimeTokens.create(user.id(), "INVITATION", Tokens.sha256Hex(raw), clock.instant().plus(props.security().invitationTtl()));
            mail.sendInvitation(user.email(), user.fullName(), tenant.name(), raw);
        }
        audit.log(tenantId, "MEMBER_ADDED", "USER", user.id().toString(), true, Map.of("role", roleCode, "email", email));
        return memberships.findView(tenantId, user.id()).orElseThrow();
    }

    public PageResult<MemberView> list(UUID tenantId, int page, int size) {
        return memberships.list(tenantId, Paging.page(page), Paging.size(size));
    }

    public List<AccessHistoryEntry> history(UUID tenantId, UUID userId) {
        requireMembership(tenantId, userId);
        return memberships.history(userId, tenantId);
    }

    @Transactional
    public MemberView changeRole(UUID tenantId, AuthenticatedUser actor, UUID userId, String roleCode) {
        requireAssignableRole(actor, roleCode);
        Membership m = requireMembership(tenantId, userId);
        requireNotSelf(actor, userId);
        if (Roles.ADMINISTRADOR.equals(m.roleCode()) && !canManageAdmins(actor)) {
            throw ApiException.forbidden("ADMIN_ROLE_PROTECTED", "Solo un administrador puede modificar a otro administrador.");
        }
        if (m.status() == MembershipStatus.REVOKED) {
            throw ApiException.conflict("MEMBERSHIP_REVOKED", "El acceso de este usuario fue revocado.");
        }
        memberships.updateRole(userId, tenantId, roleCode);
        memberships.addHistory(userId, tenantId, AccessEvent.ROLE_CHANGED, roleCode, actor == null ? null : actor.userId(), ClientInfo.ip());
        audit.log(tenantId, "MEMBER_ROLE_CHANGED", "USER", userId.toString(), true, Map.of("from", m.roleCode(), "to", roleCode));
        return memberships.findView(tenantId, userId).orElseThrow();
    }

    @Transactional
    public MemberView setSuspended(UUID tenantId, AuthenticatedUser actor, UUID userId, boolean suspended) {
        Membership m = requireMembership(tenantId, userId);
        requireNotSelf(actor, userId);
        guardAdminTarget(actor, m);
        if (m.status() == MembershipStatus.REVOKED) {
            throw ApiException.conflict("MEMBERSHIP_REVOKED", "El acceso de este usuario fue revocado; debes volver a invitarlo.");
        }
        MembershipStatus target = suspended ? MembershipStatus.SUSPENDED : MembershipStatus.ACTIVE;
        UUID actorId = actor == null ? null : actor.userId();
        memberships.updateStatus(userId, tenantId, target, actorId);
        memberships.addHistory(userId, tenantId, suspended ? AccessEvent.SUSPENDED : AccessEvent.ACTIVATED, m.roleCode(), actorId, ClientInfo.ip());
        audit.log(tenantId, suspended ? "MEMBER_SUSPENDED" : "MEMBER_ACTIVATED", "USER", userId.toString(), true, Map.of());
        return memberships.findView(tenantId, userId).orElseThrow();
    }

    @Transactional
    public void revoke(UUID tenantId, AuthenticatedUser actor, UUID userId) {
        Membership m = requireMembership(tenantId, userId);
        requireNotSelf(actor, userId);
        guardAdminTarget(actor, m);
        UUID actorId = actor == null ? null : actor.userId();
        memberships.updateStatus(userId, tenantId, MembershipStatus.REVOKED, actorId);
        memberships.addHistory(userId, tenantId, AccessEvent.REVOKED, m.roleCode(), actorId, ClientInfo.ip());
        audit.log(tenantId, "MEMBER_REVOKED", "USER", userId.toString(), true, Map.of("role", m.roleCode()));
    }

    // ---------------------------------------------------------------- helpers

    private Membership requireMembership(UUID tenantId, UUID userId) {
        return memberships.find(userId, tenantId).orElseThrow(() -> ApiException.notFound("El miembro no existe en esta copropiedad."));
    }

    private static void requireNotSelf(AuthenticatedUser actor, UUID userId) {
        if (actor != null && actor.userId().equals(userId)) {
            throw ApiException.conflict("CANNOT_MODIFY_SELF", "No puedes modificar tu propio acceso.");
        }
    }

    private static boolean canManageAdmins(AuthenticatedUser actor) {
        return actor == null || actor.isPlatformAdmin() || Roles.ADMINISTRADOR.equals(actor.roleCode());
    }

    private static void guardAdminTarget(AuthenticatedUser actor, Membership m) {
        if (Roles.ADMINISTRADOR.equals(m.roleCode()) && !canManageAdmins(actor)) {
            throw ApiException.forbidden("ADMIN_ROLE_PROTECTED", "Solo un administrador puede modificar a otro administrador.");
        }
    }

    private static void requireAssignableRole(AuthenticatedUser actor, String roleCode) {
        if (!Roles.TENANT_ROLES.contains(roleCode)) {
            throw ApiException.badRequest("INVALID_ROLE", "El rol indicado no existe o no es asignable en una copropiedad.");
        }
        if (Roles.ADMINISTRADOR.equals(roleCode) && !canManageAdmins(actor)) {
            throw ApiException.forbidden("ADMIN_ROLE_PROTECTED", "Solo un administrador puede asignar el rol de administrador.");
        }
    }
}
