package com.codevam.vecindad.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollItem(
        UUID id,
        UUID employeeId,
        String employeeName,
        String documentNumber,
        String position,
        BigDecimal baseSalary,
        int workedDays,
        BigDecimal grossPay,
        BigDecimal healthDeduction,
        BigDecimal pensionDeduction,
        BigDecimal otherDeductions,
        BigDecimal netPay,
        BigDecimal healthEmployer,
        BigDecimal pensionEmployer,
        BigDecimal arl,
        BigDecimal compensation,
        BigDecimal sena,
        BigDecimal icbf,
        BigDecimal severance,
        BigDecimal vacation,
        BigDecimal severanceInterest,
        BigDecimal employerCost
) {
}
