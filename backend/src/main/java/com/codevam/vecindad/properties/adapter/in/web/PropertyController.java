package com.codevam.vecindad.properties.adapter.in.web;

import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/properties")
@Tag(name = "Inmuebles")
public class PropertyController {

    public record PropertyRequest(@NotNull UnitType type, @NotBlank @Size(max = 60) String identifier,
                                  @Size(max = 30) String unitNumber, @Size(max = 30) String tower, Integer floorNumber,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal coefficient,
                                  @DecimalMin("0") BigDecimal areaM2, UnitStatus status) {
        PropertyUnitService.Command toCommand() {
            return new PropertyUnitService.Command(type, identifier, unitNumber, tower, floorNumber, coefficient, areaM2, status);
        }
    }

    private final PropertyUnitService service;

    public PropertyController(PropertyUnitService service) {
        this.service = service;
    }

    @Operation(summary = "Lista inmuebles de la copropiedad activa (paginado, filtros q y type)")
    @GetMapping
    @PreAuthorize("hasAuthority('PROPERTIES_VIEW')")
    public PageResult<PropertyUnit> list(@RequestParam(required = false) String q, @RequestParam(required = false) UnitType type,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(q, type, page, size);
    }

    @Operation(summary = "Detalle de un inmueble (404 si no existe en la copropiedad activa)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROPERTIES_VIEW')")
    public PropertyUnit get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Crea un inmueble")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROPERTIES_CREATE')")
    public PropertyUnit create(@Valid @RequestBody PropertyRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Actualiza un inmueble")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROPERTIES_UPDATE')")
    public PropertyUnit update(@PathVariable UUID id, @Valid @RequestBody PropertyRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente un inmueble")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PROPERTIES_DELETE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
