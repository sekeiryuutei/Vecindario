package com.codevam.vecindad.assemblies.adapter.in.web;

import com.codevam.vecindad.assemblies.application.AssemblyService;
import com.codevam.vecindad.assemblies.domain.Assembly;
import com.codevam.vecindad.assemblies.domain.AssemblyVote;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/assemblies")
@Tag(name = "Mis Asambleas y Votación (Residente/Propietario)")
public class MyAssemblyController {

    public record VoteRequest(
            @NotNull UUID unitId,
            @NotBlank String choice
    ) {}

    private final AssemblyService service;
    private final PersonPort personPort;
    private final UnitRelationPort relationPort;

    public MyAssemblyController(AssemblyService service, PersonPort personPort, UnitRelationPort relationPort) {
        this.service = service;
        this.personPort = personPort;
        this.relationPort = relationPort;
    }

    @Operation(summary = "Lista asambleas programadas o realizadas")
    @GetMapping
    @PreAuthorize("hasAuthority('ASSEMBLIES_VIEW')")
    public List<Assembly> list() {
        return service.listAssemblies();
    }

    @Operation(summary = "Emite voto para un tema de asamblea en representación del inmueble")
    @PostMapping("/topics/{topicId}/vote")
    @PreAuthorize("hasAuthority('ASSEMBLIES_VOTE')")
    public AssemblyVote vote(@PathVariable UUID topicId, @Valid @RequestBody VoteRequest r) {
        var user = CurrentUser.requireTenant();
        if (!relationPort.userHasUnit(user.userId(), r.unitId())) {
            throw ApiException.badRequest("INVALID_UNIT", "El inmueble especificado no pertenece al usuario autenticado.");
        }

        UUID personId = personPort.findByUserId(user.userId())
                .map(Person::id)
                .orElse(null);

        return service.castVote(topicId, r.unitId(), personId, r.choice());
    }
}

