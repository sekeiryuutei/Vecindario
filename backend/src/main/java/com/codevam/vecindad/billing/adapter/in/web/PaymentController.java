package com.codevam.vecindad.billing.adapter.in.web;

import com.codevam.vecindad.billing.application.PaymentService;
import com.codevam.vecindad.billing.domain.Payment;
import com.codevam.vecindad.billing.domain.PaymentMethod;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Pagos")
public class PaymentController {

    public record PaymentRequest(@NotNull UUID unitId, @NotNull @DecimalMin("0.01") BigDecimal amount, LocalDate paymentDate,
                                 @NotNull PaymentMethod method, @Size(max = 100) String reference, @Size(max = 200) String payerName,
                                 @Size(max = 300) String notes) {}
    public record ReverseRequest(@NotBlank @Size(max = 300) String reason) {}

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Registra un pago y lo imputa según el orden configurado. Header opcional Idempotency-Key: reintentar con la misma llave "
            + "devuelve el mismo pago (200) sin duplicarlo; la primera vez responde 201")
    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENTS_CREATE')")
    public ResponseEntity<PaymentService.PaymentResult> register(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                  @Valid @RequestBody PaymentRequest r) {
        PaymentService.PaymentResult res = service.register(new PaymentService.RegisterCommand(r.unitId(), r.amount(), r.paymentDate(),
                r.method(), "MANUAL", r.reference(), r.payerName(), r.notes()), key);
        return ResponseEntity.status(res.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(res);
    }

    @Operation(summary = "Lista pagos (filtros: unitId, from, to)")
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PageResult<Payment> list(@RequestParam(required = false) UUID unitId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(unitId, from, to, page, size);
    }

    @Operation(summary = "Detalle del pago con la imputación a cada cargo")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public PaymentService.PaymentResult get(@PathVariable UUID id) {
        return service.getWithAllocations(id);
    }

    @Operation(summary = "Revierte un pago (con motivo): libera los cargos, asienta la reversión en el libro. Los pagos nunca se editan ni se borran")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public PaymentService.PaymentResult reverse(@PathVariable UUID id, @Valid @RequestBody ReverseRequest r) {
        return service.reverse(id, r.reason());
    }
}
