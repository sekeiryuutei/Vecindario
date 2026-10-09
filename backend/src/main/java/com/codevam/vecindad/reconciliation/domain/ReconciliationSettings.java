package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;

/** autoThreshold: puntaje mínimo para aplicar sin revisión. ambiguityGap: si el segundo candidato queda a menos de esta diferencia, es AMBIGUO. */
public record ReconciliationSettings(BigDecimal autoThreshold, BigDecimal ambiguityGap, boolean autoApply) {}
