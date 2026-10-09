package com.codevam.vecindad.pqrs.domain;

public record PqrSlaMetrics(
        long totalOpen,
        long onTimeCount,
        long overdueCount,
        double avgResolutionHours
) {}

