package com.codevam.vecindad.identity.application.port.out;

import java.time.Duration;

public interface RateLimiterPort {
    /** @return true si la operación está permitida dentro de la ventana. */
    boolean allow(String key, int maxInWindow, Duration window);
}
