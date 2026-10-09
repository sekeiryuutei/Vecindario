package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Lo que se sabe de un inmueble para compararlo con una transacción bancaria. */
public record UnitProfile(UUID unitId, String identifier, String unitNumber, String tower, List<String> personNames,
                          List<String> documents, BigDecimal outstanding, List<BigDecimal> openChargeAmounts) {}
