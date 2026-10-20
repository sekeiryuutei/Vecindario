package com.codevam.vecindad.payroll.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollEmployee(
        UUID id,
        String documentType,
        String documentNumber,
        String fullName,
        String position,
        BigDecimal baseSalary,
        LocalDate startDate,
        LocalDate endDate,
        PayrollEmployeeStatus status,
        String bankName,
        String bankAccount,
        String costCenter,
        BigDecimal fixedDeductions,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
