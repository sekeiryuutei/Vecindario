package com.codevam.vecindad.identity.application;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.shared.persistence.Jdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Elimina tokens caducados/revocados que ya no tienen utilidad. Sin esta purga
 * las tablas de refresh tokens y tokens de un solo uso crecen indefinidamente.
 */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public TokenCleanupJob(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Scheduled(cron = "${app.maintenance.token-cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredTokens() {
        int retentionDays = Math.max(1, props.maintenance().tokenRetentionDays());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int refresh = jdbc.update(
                "DELETE FROM public.refresh_tokens WHERE expires_at < ? OR (revoked_at IS NOT NULL AND revoked_at < ?)",
                Jdbc.ts(cutoff), Jdbc.ts(cutoff));
        int oneTime = jdbc.update(
                "DELETE FROM public.one_time_tokens WHERE expires_at < ? OR (used_at IS NOT NULL AND used_at < ?)",
                Jdbc.ts(cutoff), Jdbc.ts(cutoff));

        if (refresh > 0 || oneTime > 0) {
            log.info("Purga de tokens completada: {} refresh tokens y {} tokens de un solo uso eliminados.",
                    refresh, oneTime);
        }
    }
}
