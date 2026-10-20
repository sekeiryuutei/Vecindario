package com.codevam.vecindad.wompi.adapter.out;

import com.codevam.vecindad.wompi.application.port.out.OnlinePaymentPort;
import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcOnlinePaymentAdapter implements OnlinePaymentPort {

    private final JdbcTemplate jdbc;

    public JdbcOnlinePaymentAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<OnlinePaymentIntent> MAPPER = (rs, rowNum) -> new OnlinePaymentIntent(
            rs.getObject("id", UUID.class),
            rs.getString("reference"),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("unit_id", UUID.class),
            rs.getObject("user_id", UUID.class),
            rs.getLong("amount_in_cents"),
            rs.getString("currency"),
            rs.getString("signature"),
            OnlinePaymentIntent.IntentStatus.valueOf(rs.getString("status")),
            rs.getString("wompi_tx_id"),
            rs.getString("payment_method"),
            rs.getObject("fee_in_cents") != null ? rs.getLong("fee_in_cents") : null,
            rs.getString("error_message"),
            rs.getObject("payment_id", UUID.class),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    @Override
    public OnlinePaymentIntent insert(OnlinePaymentIntent intent) {
        jdbc.update(
                "INSERT INTO public.online_payment_intents (id, reference, tenant_id, unit_id, user_id, "
                        + "amount_in_cents, currency, signature, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                intent.id(), intent.reference(), intent.tenantId(), intent.unitId(), intent.userId(),
                intent.amountInCents(), intent.currency(), intent.signature(), intent.status().name()
        );
        return findByReference(intent.reference()).orElseThrow();
    }

    @Override
    public Optional<OnlinePaymentIntent> findByReference(String reference) {
        try {
            OnlinePaymentIntent intent = jdbc.queryForObject(
                    "SELECT * FROM public.online_payment_intents WHERE reference = ?",
                    MAPPER, reference
            );
            return Optional.ofNullable(intent);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<OnlinePaymentIntent> findByReferenceForUpdate(String reference) {
        try {
            OnlinePaymentIntent intent = jdbc.queryForObject(
                    "SELECT * FROM public.online_payment_intents WHERE reference = ? FOR UPDATE",
                    MAPPER, reference
            );
            return Optional.ofNullable(intent);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateStatus(String reference, OnlinePaymentIntent.IntentStatus status, String wompiTxId,
                             String paymentMethod, Long feeInCents, UUID paymentId, String errorMessage) {
        jdbc.update(
                "UPDATE public.online_payment_intents SET status = ?, wompi_tx_id = COALESCE(?, wompi_tx_id), "
                        + "payment_method = COALESCE(?, payment_method), fee_in_cents = COALESCE(?, fee_in_cents), "
                        + "payment_id = COALESCE(?, payment_id), error_message = ?, updated_at = now() "
                        + "WHERE reference = ?",
                status.name(), wompiTxId, paymentMethod, feeInCents, paymentId, errorMessage, reference
        );
    }
}

