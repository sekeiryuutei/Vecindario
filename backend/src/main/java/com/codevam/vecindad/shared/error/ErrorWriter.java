package com.codevam.vecindad.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/** Escribe errores JSON con el formato estándar desde componentes fuera de MVC (filtros de seguridad). */
public final class ErrorWriter {
    private ErrorWriter() {}

    public static void write(HttpServletRequest req, HttpServletResponse res, ObjectMapper mapper,
                             HttpStatus status, String code, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(),
                new ApiErrorResponse(code, message, Instant.now(), req.getRequestURI(), MDC.get("traceId"), List.of()));
    }
}
