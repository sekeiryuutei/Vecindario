package com.codevam.vecindad.pqrs.adapter.in.web;

import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.pqrs.application.PqrService;
import com.codevam.vecindad.pqrs.domain.Pqr;
import com.codevam.vecindad.pqrs.domain.PqrCategory;
import com.codevam.vecindad.pqrs.domain.PqrMessage;
import com.codevam.vecindad.pqrs.domain.PqrPriority;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/pqrs")
@Tag(name = "Mis PQRS y Convivencia (Residente)")
public class MyPqrController {

    public record CreateMyPqrRequest(
            UUID unitId,
            @NotNull PqrCategory category,
            PqrPriority priority,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            boolean isAnonymous
    ) {}

    public record ResidentMessageRequest(
            @NotBlank @Size(max = 2000) String message
    ) {}

    private final PqrService service;
    private final PersonPort personPort;
    private final UnitRelationPort relationPort;

    public MyPqrController(PqrService service, PersonPort personPort, UnitRelationPort relationPort) {
        this.service = service;
        this.personPort = personPort;
        this.relationPort = relationPort;
    }

    @Operation(summary = "Radica una nueva PQR")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PQR_CREATE')")
    public Pqr create(@Valid @RequestBody CreateMyPqrRequest r) {
        var user = CurrentUser.requireTenant();
        UUID personId = personPort.findByUserId(user.userId()).map(Person::id).orElse(null);

        if (r.unitId() != null) {
            if (!relationPort.userHasUnit(user.userId(), r.unitId())) {
                throw ApiException.badRequest("INVALID_UNIT", "El inmueble especificado no corresponde al usuario.");
            }
        }

        return service.create(user.userId(), new PqrService.CreatePqrCommand(
                r.unitId(), personId, r.category(), r.priority(), r.title(), r.description(), r.isAnonymous()
        ));
    }

    @Operation(summary = "Lista mis PQRS radicadas")
    @GetMapping
    @PreAuthorize("hasAuthority('PQR_VIEW_OWN')")
    public PageResult<Pqr> listMy(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var user = CurrentUser.requireTenant();
        return service.findByUser(user.userId(), page, size);
    }

    @Operation(summary = "Consulta el detalle y respuestas de una PQR propia")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PQR_VIEW_OWN')")
    public PqrService.PqrDetail getDetail(@PathVariable UUID id) {
        var user = CurrentUser.requireTenant();
        Pqr pqr = service.getById(id);
        if (!pqr.userId().equals(user.userId())) {
            throw ApiException.notFound("PQR no encontrada.");
        }
        return service.getDetail(id, false);
    }

    @Operation(summary = "Responde o aporta información a una PQR propia")
    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PQR_VIEW_OWN')")
    public PqrMessage addMessage(@PathVariable UUID id, @Valid @RequestBody ResidentMessageRequest r) {
        var user = CurrentUser.requireTenant();
        Pqr pqr = service.getById(id);
        if (!pqr.userId().equals(user.userId())) {
            throw ApiException.notFound("PQR no encontrada.");
        }
        String senderName = pqr.isAnonymous() ? "Residente (Anónimo)" : "Residente";
        return service.addMessage(id, user.userId(), senderName, r.message(), false);
    }
}

