package com.codevam.vecindad.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(String code, String message, Instant timestamp, String path,
                               String traceId, List<FieldDetail> details) {
    public record FieldDetail(String field, String message) {}
}
