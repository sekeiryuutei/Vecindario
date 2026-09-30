package com.codevam.vecindad.identity.adapter.out.ratelimit;

import com.codevam.vecindad.identity.application.port.out.RateLimiterPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Solo para el perfil de pruebas (sin Redis). */
@Component
@Profile("test")
public class NoopRateLimiter implements RateLimiterPort {
    @Override
    public boolean allow(String key, int maxInWindow, Duration window) {
        return true;
    }
}
