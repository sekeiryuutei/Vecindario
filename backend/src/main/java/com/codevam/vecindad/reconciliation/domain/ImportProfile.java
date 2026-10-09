package com.codevam.vecindad.reconciliation.domain;

import java.util.UUID;

/** Mapeo de columnas de un formato bancario. Cada columna admite alias separados por coma. decimalSeparator: ',' (1.234,56) o '.' (1,234.56). */
public record ImportProfile(UUID id, String name, String bankName, String dateColumn, String amountColumn, String creditColumn,
                            String debitColumn, String descriptionColumn, String referenceColumn, String counterpartyColumn,
                            String transactionIdColumn, String dateFormat, String decimalSeparator, boolean active) {}
