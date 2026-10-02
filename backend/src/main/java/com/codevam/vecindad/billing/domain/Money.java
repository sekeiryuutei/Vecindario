package com.codevam.vecindad.billing.domain;

import com.codevam.vecindad.shared.error.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Utilidades de dinero (COP; NUMERIC en BD, jamás float/double) y de "hoy" en la zona horaria del negocio. */
public final class Money {
    public static final BigDecimal MAX = new BigDecimal("999999999999.99");
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private Money() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Positivo, con a lo sumo 2 decimales y dentro del máximo permitido. */
    public static BigDecimal positive(BigDecimal v, String field) {
        if (v == null || v.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "El valor de " + field + " debe ser mayor que cero.");
        }
        if (v.stripTrailingZeros().scale() > 2 || v.compareTo(MAX) > 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "El valor de " + field + " debe tener máximo 2 decimales y no superar 999.999.999.999,99.");
        }
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal round0(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }
}
