package com.codevam.vecindad.suppliers.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SupplierContract(
        UUID id,
        UUID supplierId,
        String contractNumber,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyAmount,
        BigDecimal totalAmount,
        boolean hasInsurancePolicy,
        String insurancePolicyNumber,
        LocalDate insuranceExpirationDate,
        ContractStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

