package com.codevam.vecindad.identity.adapter.in.web;

import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.domain.TenantAccess;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resumen de todas las copropiedades a las que pertenece el usuario. Es la
 * entrada del panel general para administradores con más de una propiedad.
 * El resumen operativo solo se calcula para roles con capacidad de gestión y
 * siempre dentro del esquema de la copropiedad correspondiente.
 */
@RestController
@RequestMapping("/api/v1/my")
@Tag(name = "Panel general de copropiedades")
public class MyTenantOverviewController {

    private static final Logger log = LoggerFactory.getLogger(MyTenantOverviewController.class);
    private static final Set<String> SUMMARY_ROLES =
            Set.of("ADMINISTRADOR", "SECRETARIA_ADMINISTRACION", "CONSEJO", "SUPER_ADMIN_PLATFORM");

    public record Metrics(
            long units,
            long people,
            long openPqrs,
            long openIncidents,
            long upcomingReservations,
            long pendingPackages,
            long overdueCharges,
            BigDecimal overdueAmount
    ) {}

    public record TenantOverview(
            UUID id,
            String name,
            String slug,
            String role,
            boolean canManage,
            Metrics metrics
    ) {}

    private final MembershipPort memberships;
    private final TenantJdbc tenantJdbc;

    public MyTenantOverviewController(MembershipPort memberships, TenantJdbc tenantJdbc) {
        this.memberships = memberships;
        this.tenantJdbc = tenantJdbc;
    }

    @Operation(summary = "Resumen general de las copropiedades asignadas al usuario")
    @GetMapping("/tenant-overview")
    @PreAuthorize("isAuthenticated()")
    public List<TenantOverview> overview() {
        UUID userId = CurrentUser.require().userId();
        return memberships.listActiveAccesses(userId).stream()
                .map(this::toOverview)
                .toList();
    }

    private TenantOverview toOverview(TenantAccess access) {
        boolean canManage = SUMMARY_ROLES.contains(access.roleCode());
        if (!canManage) {
            return new TenantOverview(access.tenantId(), access.tenantName(), access.slug(),
                    access.roleCode(), false, null);
        }
        try {
            Metrics metrics = TenantContext.callAs(
                    new TenantRef(access.tenantId(), access.schemaName()),
                    this::loadMetrics);
            return new TenantOverview(access.tenantId(), access.tenantName(), access.slug(),
                    access.roleCode(), true, metrics);
        } catch (RuntimeException ex) {
            log.warn("No fue posible calcular el resumen de la copropiedad {}", access.slug(), ex);
            return new TenantOverview(access.tenantId(), access.tenantName(), access.slug(),
                    access.roleCode(), true, null);
        }
    }

    private Metrics loadMetrics() {
        String sql = """
                SELECT
                  (SELECT count(*) FROM {s}.property_units
                    WHERE deleted_at IS NULL AND status = 'ACTIVE') AS units,
                  (SELECT count(*) FROM {s}.people WHERE deleted_at IS NULL) AS people,
                  (SELECT count(*) FROM {s}.pqrs
                    WHERE status IN ('OPEN', 'IN_PROGRESS')) AS open_pqrs,
                  (SELECT count(*) FROM {s}.incidents
                    WHERE status IN ('OPEN', 'IN_REVIEW')) AS open_incidents,
                  (SELECT count(*) FROM {s}.reservations
                    WHERE status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'APPROVED')
                      AND start_time > now()) AS upcoming_reservations,
                  (SELECT count(*) FROM {s}.packages
                    WHERE status IN ('RECEIVED', 'NOTIFIED')) AS pending_packages,
                  (SELECT count(*) FROM {s}.charges
                    WHERE voided_at IS NULL AND due_date < CURRENT_DATE
                      AND amount + adjusted_amount > paid_amount) AS overdue_charges,
                  (SELECT COALESCE(sum(amount + adjusted_amount - paid_amount), 0)
                     FROM {s}.charges
                    WHERE voided_at IS NULL AND due_date < CURRENT_DATE
                      AND amount + adjusted_amount > paid_amount) AS overdue_amount
                """;
        return tenantJdbc.jdbc().queryForObject(tenantJdbc.q(sql), (rs, rowNum) -> new Metrics(
                rs.getLong("units"),
                rs.getLong("people"),
                rs.getLong("open_pqrs"),
                rs.getLong("open_incidents"),
                rs.getLong("upcoming_reservations"),
                rs.getLong("pending_packages"),
                rs.getLong("overdue_charges"),
                rs.getBigDecimal("overdue_amount")
        ));
    }
}
