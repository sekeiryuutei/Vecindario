package com.codevam.vecindad.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Política de cobro por pago en línea. El pagador asume la comisión de la
 * pasarela más una tarifa fija de la plataforma por cada transacción, de modo
 * que la copropiedad recibe exactamente el valor de la deuda.
 *
 * <p>Valores por defecto alineados con el plan de Wompi Colombia
 * (2,65% + $700 por transacción exitosa más IVA).</p>
 */
@ConfigurationProperties(prefix = "app.fees")
public record FeeProperties(
        boolean enabled,
        BigDecimal platformFee,
        BigDecimal wompiRate,
        BigDecimal wompiFixed,
        BigDecimal wompiIvaRate
) {
}
