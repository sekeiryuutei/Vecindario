package com.codevam.vecindad.securitysummary;

import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSecuritySummaryAdapter implements SecuritySummaryPort {
    private final TenantJdbc t;

    public JdbcSecuritySummaryAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public SecuritySummary load() {
        return t.jdbc().queryForObject(t.q("""
                SELECT
                  (SELECT count(*) FROM {s}.vehicles WHERE presence = 'INSIDE' AND deleted_at IS NULL) AS vehicles_inside,
                  (SELECT count(*) FROM {s}.visits WHERE status = 'INSIDE') AS visitors_inside,
                  (SELECT count(*) FROM {s}.visits WHERE status = 'PENDING_AUTH') AS pending_visit_requests,
                  (SELECT count(*) FROM {s}.security_alerts WHERE resolved_at IS NULL) AS open_alerts,
                  (SELECT count(*) FROM {s}.packages WHERE status IN ('RECEIVED','NOTIFIED')) AS packages_pending,
                  (SELECT count(*) FROM {s}.incidents WHERE status <> 'CLOSED') AS open_incidents,
                  (SELECT count(*) FROM {s}.vehicle_access_events WHERE event_type = 'ENTRY'
                     AND occurred_at >= (date_trunc('day', now() AT TIME ZONE 'America/Bogota') AT TIME ZONE 'America/Bogota')) AS entries_today
                """), (rs, i) -> new SecuritySummary(rs.getLong("vehicles_inside"), rs.getLong("visitors_inside"),
                rs.getLong("pending_visit_requests"), rs.getLong("open_alerts"), rs.getLong("packages_pending"),
                rs.getLong("open_incidents"), rs.getLong("entries_today")));
    }
}
