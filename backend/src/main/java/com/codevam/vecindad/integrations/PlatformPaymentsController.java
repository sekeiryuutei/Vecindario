package com.codevam.vecindad.integrations;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resumen global de los pagos en línea de la plataforma: cuánto se cobró a los
 * usuarios, cuánto se acreditó a las copropiedades y cuánto corresponde a la
 * tarifa de la plataforma y a la comisión de la pasarela.
 */
@RestController
@RequestMapping("/api/v1/platform/online-payments")
@Tag(name = "Plataforma (pagos en línea)")
public class PlatformPaymentsController {

    public record OnlinePaymentsSummary(
            long approvedPayments,
            long pendingPayments,
            long chargedInCents,
            long creditedInCents,
            long platformFeesInCents,
            long wompiFeesInCents,
            long platformRevenueInCents
    ) {}

    private final JdbcTemplate jdbc;

    public PlatformPaymentsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Operation(summary = "Totales de cobros por pagos en línea y ingreso de la plataforma")
    @GetMapping("/summary")
    public OnlinePaymentsSummary summary() {
        return jdbc.queryForObject("""
                SELECT
                    count(*) FILTER (WHERE status = 'APPROVED')                       AS approved_payments,
                    count(*) FILTER (WHERE status = 'PENDING')                        AS pending_payments,
                    COALESCE(sum(amount_in_cents) FILTER (WHERE status = 'APPROVED'), 0)     AS charged,
                    COALESCE(sum(net_amount_in_cents) FILTER (WHERE status = 'APPROVED'), 0) AS credited,
                    COALESCE(sum(platform_fee_in_cents) FILTER (WHERE status = 'APPROVED'), 0) AS platform_fees,
                    COALESCE(sum(fee_in_cents) FILTER (WHERE status = 'APPROVED'), 0)         AS wompi_fees
                  FROM public.online_payment_intents
                """, (rs, rowNum) -> {
            long charged = rs.getLong("charged");
            long credited = rs.getLong("credited");
            long platformFees = rs.getLong("platform_fees");
            long wompiFees = rs.getLong("wompi_fees");
            return new OnlinePaymentsSummary(
                    rs.getLong("approved_payments"),
                    rs.getLong("pending_payments"),
                    charged,
                    credited,
                    platformFees,
                    wompiFees,
                    charged - credited
            );
        });
    }
}
