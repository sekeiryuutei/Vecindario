package com.codevam.vecindad.billing.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.port.out.BillingSettingsPort;
import com.codevam.vecindad.billing.domain.BillingSettings;
import com.codevam.vecindad.billing.domain.ConceptType;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingSettingsService {
    private final BillingSettingsPort port;
    private final AuditService audit;

    public BillingSettingsService(BillingSettingsPort port, AuditService audit) {
        this.port = port;
        this.audit = audit;
    }

    public BillingSettings get() {
        TenantContext.require();
        return port.load();
    }

    public BillingSettings update(BillingSettings s) {
        UUID tenantId = TenantContext.require().id();
        List<ConceptType> order = s.allocationOrder();
        if (order == null || order.isEmpty() || new HashSet<>(order).size() != order.size()) {
            throw ApiException.badRequest("INVALID_ALLOCATION_ORDER", "El orden de imputación no puede estar vacío ni repetir conceptos.");
        }
        if (s.interestMonthlyRate() == null || s.interestMonthlyRate().signum() < 0 || s.interestMonthlyRate().compareTo(BigDecimal.TEN) > 0) {
            throw ApiException.badRequest("INVALID_INTEREST_RATE", "La tasa de interés mensual debe estar entre 0 y 10 (%).");
        }
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        port.save(s, actor);
        audit.log(tenantId, "BILLING_SETTINGS_CHANGED", "BILLING_SETTINGS", "1", true,
                Map.of("order", order.toString(), "interestEnabled", s.interestEnabled(), "rate", s.interestMonthlyRate().toPlainString()));
        return port.load();
    }
}
