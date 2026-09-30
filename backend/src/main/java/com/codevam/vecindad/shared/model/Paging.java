package com.codevam.vecindad.shared.model;

/** Normaliza parámetros de paginación: nunca se devuelven listas ilimitadas. */
public final class Paging {
    public static final int MAX_SIZE = 100;

    private Paging() {}

    public static int page(int page) {
        return Math.max(0, page);
    }

    public static int size(int size) {
        return Math.max(1, Math.min(size, MAX_SIZE));
    }
}
