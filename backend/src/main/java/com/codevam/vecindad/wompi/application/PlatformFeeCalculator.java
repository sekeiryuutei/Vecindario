package com.codevam.vecindad.wompi.application;

import com.codevam.vecindad.config.FeeProperties;
import com.codevam.vecindad.shared.error.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula el total que paga el usuario por un pago en línea.
 *
 * <p>El monto que se cobra al pagador cubre la deuda más la comisión estimada
 * de la pasarela (porcentaje y parte fija, con IVA) más la tarifa fija de la
 * plataforma. Así la copropiedad recibe exactamente el valor adeudado y la
 * diferencia queda como ingreso de la plataforma.</p>
 */
@Component
public class PlatformFeeCalculator {

    private static final BigDecimal DEFAULT_PLATFORM_FEE = new BigDecimal("2000");
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.0265");
    private static final BigDecimal DEFAULT_FIXED = new BigDecimal("700");
    private static final BigDecimal DEFAULT_IVA_RATE = new BigDecimal("0.19");
    private static final BigDecimal CENTS = BigDecimal.valueOf(100);

    /**
     * @param netAmountInCents  valor de la deuda que se acredita a la copropiedad
     * @param platformFeeInCents tarifa fija de la plataforma
     * @param commissionInCents  comisión estimada de la pasarela
     * @param totalInCents       valor total que cobra la pasarela
     */
    public record Quote(long netAmountInCents, long platformFeeInCents,
                        long commissionInCents, long totalInCents) {}

    private final FeeProperties props;

    public PlatformFeeCalculator(FeeProperties props) {
        this.props = props;
    }

    public Quote quote(long netAmountInCents) {
        if (netAmountInCents <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "El valor a pagar debe ser mayor que cero.");
        }
        if (props == null || !props.enabled()) {
            return new Quote(netAmountInCents, 0L, 0L, netAmountInCents);
        }

        BigDecimal platformFee = value(props.platformFee(), DEFAULT_PLATFORM_FEE);
        BigDecimal rate = value(props.wompiRate(), DEFAULT_RATE);
        BigDecimal fixed = value(props.wompiFixed(), DEFAULT_FIXED);
        BigDecimal ivaRate = value(props.wompiIvaRate(), DEFAULT_IVA_RATE);

        BigDecimal ivaFactor = BigDecimal.ONE.add(ivaRate);
        BigDecimal effectiveRate = rate.multiply(ivaFactor);
        if (effectiveRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException("La comisión configurada de la pasarela debe ser menor que el 100%.");
        }

        BigDecimal platformFeeCents = platformFee.multiply(CENTS);
        BigDecimal fixedCents = fixed.multiply(CENTS).multiply(ivaFactor);
        BigDecimal denominator = BigDecimal.ONE.subtract(effectiveRate);

        BigDecimal total = BigDecimal.valueOf(netAmountInCents)
                .add(platformFeeCents)
                .add(fixedCents)
                .divide(denominator, 0, RoundingMode.CEILING);

        long totalInCents = total.longValueExact();
        long platformFeeInCents = platformFeeCents.longValueExact();
        long commissionInCents = totalInCents - netAmountInCents - platformFeeInCents;

        return new Quote(netAmountInCents, platformFeeInCents, commissionInCents, totalInCents);
    }

    private BigDecimal value(BigDecimal configured, BigDecimal fallback) {
        return configured == null ? fallback : configured;
    }
}
