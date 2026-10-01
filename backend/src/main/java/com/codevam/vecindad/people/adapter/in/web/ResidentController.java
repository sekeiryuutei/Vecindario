package com.codevam.vecindad.people.adapter.in.web;

import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.domain.MyUnit;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.people.domain.RelationType;
import com.codevam.vecindad.people.domain.UnitRelation;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Personas y relaciones con inmuebles")
public class ResidentController {

    public record PersonRequest(@Pattern(regexp = "CC|CE|NIT|PAS|TI|OTRO", message = "Tipo de documento inválido") String documentType,
                                @Size(max = 30) String documentNumber,
                                @NotBlank @Size(max = 200) String fullName,
                                @Email @Size(max = 254) String email, @Size(max = 40) String phone) {
        PersonService.Command toCommand() {
            return new PersonService.Command(documentType, documentNumber, fullName, email, phone);
        }
    }
    public record LinkUserRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record RelationRequest(@NotNull UUID personId, @NotNull RelationType type, LocalDate startDate) {}

    private final PersonService service;

    public ResidentController(PersonService service) {
        this.service = service;
    }

    @Operation(summary = "Lista personas (paginado, filtro q por nombre/correo/documento)")
    @GetMapping("/api/v1/residents")
    @PreAuthorize("hasAuthority('RESIDENTES_VIEW')")
    public PageResult<Person> list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.search(q, page, size);
    }

    @GetMapping("/api/v1/residents/{id}")
    @PreAuthorize("hasAuthority('RESIDENTES_VIEW')")
    public Person get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Crea una persona (puede ser propietaria, arrendataria o residente de varios inmuebles)")
    @PostMapping("/api/v1/residents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESIDENTES_CREATE')")
    public Person create(@Valid @RequestBody PersonRequest req) {
        return service.create(req.toCommand());
    }

    @PutMapping("/api/v1/residents/{id}")
    @PreAuthorize("hasAuthority('RESIDENTES_UPDATE')")
    public Person update(@PathVariable UUID id, @Valid @RequestBody PersonRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente a la persona (no permitido con relaciones vigentes)")
    @DeleteMapping("/api/v1/residents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RESIDENTES_DELETE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Enlaza la persona con un usuario miembro de la copropiedad (define qué inmuebles ve ese usuario)")
    @PutMapping("/api/v1/residents/{id}/user")
    @PreAuthorize("hasAuthority('RESIDENTES_UPDATE')")
    public Person linkUser(@PathVariable UUID id, @Valid @RequestBody LinkUserRequest req) {
        return service.linkUser(id, req.email());
    }

    @GetMapping("/api/v1/residents/{id}/relations")
    @PreAuthorize("hasAuthority('RESIDENTES_VIEW')")
    public List<UnitRelation> relationsOfPerson(@PathVariable UUID id) {
        return service.relationsOfPerson(id);
    }

    @GetMapping("/api/v1/properties/{unitId}/relations")
    @PreAuthorize("hasAuthority('RESIDENTES_VIEW')")
    public List<UnitRelation> relationsOfUnit(@PathVariable UUID unitId) {
        return service.relationsOfUnit(unitId);
    }

    @Operation(summary = "Asocia una persona a un inmueble como propietario, arrendatario o residente")
    @PostMapping("/api/v1/properties/{unitId}/relations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESIDENTES_UPDATE')")
    public UnitRelation addRelation(@PathVariable UUID unitId, @Valid @RequestBody RelationRequest req) {
        return service.addRelation(unitId, req.personId(), req.type(), req.startDate());
    }

    @Operation(summary = "Termina una relación (se conserva el historial)")
    @PostMapping("/api/v1/relations/{relationId}/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RESIDENTES_UPDATE')")
    public void endRelation(@PathVariable UUID relationId) {
        service.endRelation(relationId);
    }

    @Operation(summary = "Inmuebles del usuario autenticado (según la persona enlazada); el residente solo ve los suyos")
    @GetMapping("/api/v1/my/units")
    @PreAuthorize("hasAuthority('UNITS_VIEW_OWN')")
    public List<MyUnit> myUnits() {
        return service.myUnits();
    }
}
