package com.codevam.vecindad.board.adapter.in.web;

import com.codevam.vecindad.board.application.BoardService;
import com.codevam.vecindad.board.domain.BoardMember;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/board")
@Tag(name = "Junta directiva")
public class BoardController {
    public record BoardMemberRequest(@NotNull UUID personId, @NotNull BoardPosition position, LocalDate startDate,
                                     LocalDate endDate, BoardStatus status, @Size(max = 500) String notes) {
        BoardService.Command toCommand() {
            return new BoardService.Command(personId, position, startDate, endDate, status, notes);
        }
    }

    private final BoardService service;

    public BoardController(BoardService service) {
        this.service = service;
    }

    @Operation(summary = "Lista los miembros de la junta directiva (opcional: status)")
    @GetMapping
    @PreAuthorize("hasAuthority('BOARD_VIEW')")
    public List<BoardMember> list(@RequestParam(required = false) BoardStatus status) {
        return service.list(status);
    }

    @Operation(summary = "Registra un miembro de la junta directiva")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BOARD_MANAGE')")
    public BoardMember create(@Valid @RequestBody BoardMemberRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Actualiza un miembro de la junta directiva")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD_MANAGE')")
    public BoardMember update(@PathVariable UUID id, @Valid @RequestBody BoardMemberRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente un miembro de la junta directiva")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BOARD_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
