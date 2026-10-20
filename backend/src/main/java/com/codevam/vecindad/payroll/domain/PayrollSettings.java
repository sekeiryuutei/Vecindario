package com.codevam.vecindad.payroll.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Parámetros de liquidación configurables por copropiedad. Los porcentajes se
 * expresan como valores porcentuales (4.00 = 4%). No representan una
 * certificación legal: la copropiedad debe validar sus tarifas vigentes.
 */
public record PayrollSettings(
        String currency,
        String periodicity,
        int baseDays,
        BigDecimal healthEmployeeRate,
        BigDecimal pensionEmployeeRate,
        BigDecimal healthEmployerRate,
        BigDecimal pensionEmployerRate,
        BigDecimal arlRate,
        BigDecimal compensationRate,
        BigDecimal senaRate,
        BigDecimal icbfRate,
        BigDecimal severanceRate,
        BigDecimal vacationRate,
        BigDecimal severanceInterestRate,
        BigDecimal transportAllowance,
        BigDecimal minimumWage,
        Instant updatedAt
) {
}
