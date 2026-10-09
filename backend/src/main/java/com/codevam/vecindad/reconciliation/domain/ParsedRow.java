package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ParsedRow(int rowNumber, LocalDate date, BigDecimal amount, String description, String reference,
                        String counterparty, String bankTxnId, Map<String, String> raw) {}
