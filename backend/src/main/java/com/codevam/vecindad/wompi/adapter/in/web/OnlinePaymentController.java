package com.codevam.vecindad.wompi.adapter.in.web;

import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.wompi.application.WompiService;
import com.codevam.vecindad.wompi.domain.OnlinePaymentIntent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/payments/online")
@Tag(name = "Pagos en línea (Wompi)")
public class OnlinePaymentController {

    public record CreateIntentRequest(
            @NotNull UUID unitId,
            @NotNull @DecimalMin("1000") BigDecimal amount,
            @Size(max = 300) String notes
    ) {}

    private final WompiService wompi;

    public OnlinePaymentController(WompiService wompi) {
        this.wompi = wompi;
    }

    @Operation(summary = "Crea una intención de pago en línea para un inmueble propio. Retorna la firma de integridad y la URL de checkout de Wompi.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PAYMENTS_ONLINE_CREATE') or hasAuthority('PAYMENTS_CREATE')")
    public WompiService.IntentResponse createIntent(@Valid @RequestBody CreateIntentRequest req) {
        return wompi.createIntent(req.unitId(), req.amount(), req.notes());
    }

    @Operation(summary = "Consulta el estado de una intención de pago en línea por su referencia")
    @GetMapping("/{reference}")
    @PreAuthorize("hasAuthority('PAYMENTS_ONLINE_CREATE') or hasAuthority('PAYMENTS_CREATE') or hasAuthority('FINANCE_VIEW')")
    public OnlinePaymentIntent getIntent(@PathVariable String reference) {
        return wompi.getIntent(reference)
                .orElseThrow(() -> ApiException.notFound("La transacción no existe."));
    }
}

