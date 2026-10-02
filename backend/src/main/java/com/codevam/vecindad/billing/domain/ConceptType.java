package com.codevam.vecindad.billing.domain;

import java.util.List;

public enum ConceptType {
    ORDINARY, EXTRAORDINARY, INTEREST, FINE, OTHER;

    /** Orden de imputación por defecto (configurable por copropiedad). */
    public static final List<ConceptType> DEFAULT_ORDER = List.of(INTEREST, EXTRAORDINARY, ORDINARY, FINE, OTHER);
}
