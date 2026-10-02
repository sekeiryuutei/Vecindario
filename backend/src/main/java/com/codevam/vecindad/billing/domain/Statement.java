package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estado de cuenta de un inmueble en un periodo. closingBalance = openingBalance + movimientos del periodo (del libro).
 * openCharges, overdueAmount, nextDueDate y creditBalance reflejan la situación ACTUAL.
 */
public record Statement(UUID unitId, String unitIdentifier, LocalDate from, LocalDate to, BigDecimal openingBalance,
                        BigDecimal chargesTotal, BigDecimal interestTotal, BigDecimal paymentsTotal,
                        BigDecimal adjustmentsTotal, BigDecimal closingBalance, BigDecimal overdueAmount,
                        LocalDate nextDueDate, BigDecimal creditBalance, List<LedgerEntry> lines, List<Charge> openCharges) {}
