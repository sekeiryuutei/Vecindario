package com.codevam.vecindad.unit;

import com.codevam.vecindad.config.FeeProperties;
import com.codevam.vecindad.wompi.application.PlatformFeeCalculator;
import com.codevam.vecindad.shared.error.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformFeeCalculatorTest {

    private static final FeeProperties ACTIVE = new FeeProperties(true, new BigDecimal("2000"),
            new BigDecimal("0.0265"), new BigDecimal("700"), new BigDecimal("0.19"));
    private static final FeeProperties DISABLED = new FeeProperties(false, new BigDecimal("2000"),
            new BigDecimal("0.0265"), new BigDecimal("700"), new BigDecimal("0.19"));

    @Test
    void sinTarifaElPagoEsLaDeuda() {
        PlatformFeeCalculator.Quote quote = new PlatformFeeCalculator(DISABLED).quote(100_000L);

        assertEquals(100_000L, quote.netAmountInCents());
        assertEquals(0L, quote.platformFeeInCents());
        assertEquals(0L, quote.commissionInCents());
        assertEquals(100_000L, quote.totalInCents());
    }

    @Test
    void elTotalCubreLaDeudaLaTarifaYLaComision() {
        PlatformFeeCalculator.Quote quote = new PlatformFeeCalculator(ACTIVE).quote(100_000L);

        assertEquals(100_000L, quote.netAmountInCents());
        assertEquals(200_000L, quote.platformFeeInCents());
        assertEquals(395_781L, quote.totalInCents());
        assertEquals(95_781L, quote.commissionInCents());
        assertEquals(quote.totalInCents(),
                quote.netAmountInCents() + quote.platformFeeInCents() + quote.commissionInCents());
    }

    @Test
    void laComisionSeCalculaSobreElTotalQueCobraLaPasarela() {
        PlatformFeeCalculator calculator = new PlatformFeeCalculator(ACTIVE);

        PlatformFeeCalculator.Quote quote = calculator.quote(1_000_000L);

        BigDecimal expectedRate = new BigDecimal("0.0265").multiply(new BigDecimal("1.19"));
        BigDecimal expectedFixed = new BigDecimal("700").multiply(new BigDecimal("100"))
                .multiply(new BigDecimal("1.19"));
        BigDecimal expectedCommission = BigDecimal.valueOf(quote.totalInCents())
                .multiply(expectedRate).add(expectedFixed).setScale(0, java.math.RoundingMode.HALF_UP);

        assertTrue(expectedCommission.subtract(BigDecimal.valueOf(quote.commissionInCents())).abs()
                .compareTo(BigDecimal.valueOf(2)) < 0);
    }

    @Test
    void elTotalEsUnEnteroEnCentavos() {
        PlatformFeeCalculator calculator = new PlatformFeeCalculator(ACTIVE);

        for (long net : new long[]{150_000L, 333_333L, 5_000_001L}) {
            PlatformFeeCalculator.Quote quote = calculator.quote(net);
            assertEquals(quote.totalInCents(),
                    quote.netAmountInCents() + quote.platformFeeInCents() + quote.commissionInCents());
            assertTrue(quote.totalInCents() >= quote.netAmountInCents() + quote.platformFeeInCents());
        }
    }

    @Test
    void rechazaValoresNoPositivos() {
        PlatformFeeCalculator calculator = new PlatformFeeCalculator(ACTIVE);

        ApiException error = assertThrows(ApiException.class, () -> calculator.quote(0L));

        assertEquals("INVALID_AMOUNT", error.getCode());
    }

    @Test
    void usaLosValoresPorDefectoCuandoFaltanLasTarifas() {
        PlatformFeeCalculator calculator = new PlatformFeeCalculator(new FeeProperties(true, null, null, null, null));

        PlatformFeeCalculator.Quote quote = calculator.quote(100_000L);

        assertEquals(200_000L, quote.platformFeeInCents());
        assertTrue(quote.commissionInCents() > 0);
    }
}
