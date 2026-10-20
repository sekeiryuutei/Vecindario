package com.codevam.vecindad.payroll.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayrollRun(
        UUID id,
        String runNumber,
        String period,
        PayrollRunStatus status,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet,
        BigDecimal totalEmployerCost,
        String notes,
        UUID createdBy,
        Instant createdAt,
        Instant calculatedAt,
        Instant paidAt,
        Instant voidedAt,
        String voidReason,
        List<PayrollItem> items
) {
    public PayrollRun withItems(List<PayrollItem> detail) {
        return new PayrollRun(id, runNumber, period, status, totalGross, totalDeductions, totalNet,
                totalEmployerCost, notes, createdBy, createdAt, calculatedAt, paidAt, voidedAt, voidReason, detail);
    }
}
