package com.codevam.vecindad.pets.adapter.in.web;

import com.codevam.vecindad.pets.application.PetService;
import com.codevam.vecindad.pets.domain.Pet;
import com.codevam.vecindad.pets.domain.PetStatus;
import com.codevam.vecindad.pets.domain.Species;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Mascotas")
public class PetController {

    public record PetRequest(@NotBlank @Size(max = 80) String name, Species species,
                             @Size(max = 80) String breed, @Size(max = 40) String color, LocalDate birthDate,
                             @DecimalMin("0.01") @DecimalMax("200.00") BigDecimal weightKg,
                             @Size(max = 40) String microchip, Boolean vaccinated, LocalDate vaccineExpiresOn,
                             @Size(max = 500) String notes, PetStatus status, UUID unitId) {
        PetService.Command toCommand() {
            return new PetService.Command(name, species, breed, color, birthDate, weightKg, microchip, vaccinated,
                    vaccineExpiresOn, notes, status, unitId);
        }
    }

    private final PetService service;

    public PetController(PetService service) {
        this.service = service;
    }

    @Operation(summary = "Lista las mascotas registradas (filtros: q, unitId, species, status)")
    @GetMapping("/api/v1/pets")
    @PreAuthorize("hasAuthority('PETS_VIEW')")
    public PageResult<Pet> list(@RequestParam(required = false) String q, @RequestParam(required = false) UUID unitId,
                                @RequestParam(required = false) Species species,
                                @RequestParam(required = false) PetStatus status,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.search(q, unitId, species, status, page, size);
    }

    @GetMapping("/api/v1/pets/{id}")
    @PreAuthorize("hasAuthority('PETS_VIEW')")
    public Pet get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Registra una mascota asociada a un inmueble")
    @PostMapping("/api/v1/pets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PETS_CREATE')")
    public Pet create(@Valid @RequestBody PetRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Modifica una mascota registrada")
    @PutMapping("/api/v1/pets/{id}")
    @PreAuthorize("hasAuthority('PETS_UPDATE')")
    public Pet update(@PathVariable UUID id, @Valid @RequestBody PetRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente una mascota; el historial se conserva")
    @DeleteMapping("/api/v1/pets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PETS_DELETE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ---------------------------------------------- residente: solo mascotas de sus inmuebles

    @Operation(summary = "Mascotas de mis inmuebles (opcionalmente de uno: unitId)")
    @GetMapping("/api/v1/my/pets")
    @PreAuthorize("hasAuthority('PETS_VIEW_OWN')")
    public List<Pet> myPets(@RequestParam(required = false) UUID unitId) {
        return service.listOwn(unitId);
    }

    @Operation(summary = "Registra una mascota en uno de mis inmuebles")
    @PostMapping("/api/v1/my/pets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PETS_MANAGE_OWN')")
    public Pet createMine(@Valid @RequestBody PetRequest req) {
        return service.createOwn(req.toCommand());
    }

    @Operation(summary = "Modifica una mascota de uno de mis inmuebles")
    @PutMapping("/api/v1/my/pets/{id}")
    @PreAuthorize("hasAuthority('PETS_MANAGE_OWN')")
    public Pet updateMine(@PathVariable UUID id, @Valid @RequestBody PetRequest req) {
        return service.updateOwn(id, req.toCommand());
    }

    @Operation(summary = "Elimina una mascota de uno de mis inmuebles")
    @DeleteMapping("/api/v1/my/pets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PETS_MANAGE_OWN')")
    public void deleteMine(@PathVariable UUID id) {
        service.deleteOwn(id);
    }
}
