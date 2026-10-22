package com.codevam.vecindad.unitfiles.adapter.in.web;

import com.codevam.vecindad.unitfiles.application.UnitFileService;
import com.codevam.vecindad.unitfiles.domain.UnitFileNote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Expediente del inmueble")
public class UnitFileController {
    public record NoteRequest(@NotBlank @Size(max = 160) String title,
                              @NotBlank @Size(max = 2000) String body, boolean internal) {
        UnitFileService.NoteCommand toCommand() {
            return new UnitFileService.NoteCommand(title, body, internal);
        }
    }

    private final UnitFileService service;

    public UnitFileController(UnitFileService service) {
        this.service = service;
    }

    @Operation(summary = "Consulta el expediente completo de un inmueble")
    @GetMapping("/properties/{unitId}/file")
    @PreAuthorize("hasAuthority('UNIT_FILE_VIEW')")
    public UnitFileService.View view(@PathVariable UUID unitId) {
        return service.view(unitId);
    }

    @Operation(summary = "Consulta el expediente de un inmueble propio")
    @GetMapping("/my/units/{unitId}/file")
    @PreAuthorize("hasAuthority('UNIT_FILE_VIEW_OWN')")
    public UnitFileService.View viewOwn(@PathVariable UUID unitId) {
        return service.viewOwn(unitId);
    }

    @Operation(summary = "Agrega una nota al expediente")
    @PostMapping("/properties/{unitId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('UNIT_FILE_MANAGE')")
    public UnitFileNote createNote(@PathVariable UUID unitId, @Valid @RequestBody NoteRequest req) {
        return service.createNote(unitId, req.toCommand());
    }

    @Operation(summary = "Actualiza una nota del expediente")
    @PutMapping("/properties/{unitId}/notes/{noteId}")
    @PreAuthorize("hasAuthority('UNIT_FILE_MANAGE')")
    public UnitFileNote updateNote(@PathVariable UUID unitId, @PathVariable UUID noteId,
                                   @Valid @RequestBody NoteRequest req) {
        return service.updateNote(unitId, noteId, req.toCommand());
    }

    @Operation(summary = "Elimina una nota del expediente")
    @DeleteMapping("/properties/{unitId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('UNIT_FILE_MANAGE')")
    public void deleteNote(@PathVariable UUID unitId, @PathVariable UUID noteId) {
        service.deleteNote(unitId, noteId);
    }
}
