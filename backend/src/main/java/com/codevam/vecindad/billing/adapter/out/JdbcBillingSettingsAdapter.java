package com.codevam.vecindad.billing.adapter.out;

import com.codevam.vecindad.billing.application.port.out.BillingSettingsPort;
import com.codevam.vecindad.billing.domain.BillingSettings;
import com.codevam.vecindad.billing.domain.ConceptType;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcBillingSettingsAdapter implements BillingSettingsPort {
    private final TenantJdbc t;

    public JdbcBillingSettingsAdapter(TenantJdbc t) {
        this.t = t;
    }

    @Override
    public BillingSettings load() {
        return t.jdbc().queryForObject(t.q("SELECT allocation_order, oldest_first, interest_enabled, interest_monthly_rate, grace_days, "
                + "block_reservations_when_overdue, overdue_days_for_block FROM {s}.billing_settings WHERE id = 1"), (rs, i) -> {
            List<ConceptType> order = Arrays.stream(rs.getString("allocation_order").split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(ConceptType::valueOf).toList();
            return new BillingSettings(order, rs.getBoolean("oldest_first"), rs.getBoolean("interest_enabled"),
                    rs.getBigDecimal("interest_monthly_rate"), rs.getInt("grace_days"),
                    rs.getBoolean("block_reservations_when_overdue"), rs.getInt("overdue_days_for_block"));
        });
    }

    @Override
    public void save(BillingSettings s, UUID by) {
        t.jdbc().update(t.q("UPDATE {s}.billing_settings SET allocation_order = ?, oldest_first = ?, interest_enabled = ?, "
                        + "interest_monthly_rate = ?, grace_days = ?, block_reservations_when_overdue = ?, overdue_days_for_block = ?, "
                        + "updated_at = now(), updated_by = ? WHERE id = 1"),
                s.allocationOrder().stream().map(Enum::name).collect(Collectors.joining(",")), s.oldestFirst(), s.interestEnabled(),
                s.interestMonthlyRate(), s.graceDays(), s.blockReservationsWhenOverdue(), s.overdueDaysForBlock(), by);
    }
}
