package com.codevam.vecindad.board.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.board.application.port.out.BoardPort;
import com.codevam.vecindad.board.domain.BoardMember;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Junta directiva: cada persona puede tener un cargo activo y los cargos únicos (presidente, secretario, etc.)
 * no pueden duplicarse. Los vocales son la excepción y pueden ser varios.
 */
@Service
public class BoardService {
    public record Command(UUID personId, BoardPosition position, LocalDate startDate, LocalDate endDate,
                          BoardStatus status, String notes) {}

    private final BoardPort board;
    private final PersonPort people;
    private final AuditService audit;
    private final Clock clock;

    public BoardService(BoardPort board, PersonPort people, AuditService audit, Clock clock) {
        this.board = board;
        this.people = people;
        this.audit = audit;
        this.clock = clock;
    }

    public List<BoardMember> list(BoardStatus status) {
        return board.list(status);
    }

    public BoardMember get(UUID id) {
        return board.findById(id).orElseThrow(() -> ApiException.notFound("El miembro de la junta no existe."));
    }

    public BoardMember create(Command c) {
        UUID tenantId = TenantContext.require().id();
        validate(c);
        UUID personId = c.personId();
        people.findById(personId).orElseThrow(() -> ApiException.notFound("La persona no existe."));
        BoardPosition position = c.position();
        LocalDate start = c.startDate() == null ? LocalDate.now(clock) : c.startDate();
        LocalDate end = c.endDate();
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_BOARD_DATES", "La fecha de fin no puede ser anterior al inicio.");
        }
        BoardStatus status = c.status() == null ? BoardStatus.ACTIVE : c.status();
        if (status == BoardStatus.ACTIVE) {
            checkActiveRules(personId, position, null);
        }
        BoardMember member = new BoardMember(UUID.randomUUID(), personId, null, null, null, null, position, start, end,
                status, trim(c.notes()), null, null);
        try {
            board.insert(member);
        } catch (DuplicateKeyException e) {
            throw conflict();
        }
        audit.log(tenantId, "BOARD_MEMBER_CREATED", "BOARD_MEMBER", member.id().toString(), true,
                Map.of("personId", personId.toString(), "position", position.name()));
        return get(member.id());
    }

    public BoardMember update(UUID id, Command c) {
        UUID tenantId = TenantContext.require().id();
        BoardMember current = get(id);
        validate(c);
        people.findById(c.personId()).orElseThrow(() -> ApiException.notFound("La persona no existe."));
        LocalDate start = c.startDate() == null ? current.startDate() : c.startDate();
        LocalDate end = c.endDate();
        if (end != null && end.isBefore(start)) {
            throw ApiException.badRequest("INVALID_BOARD_DATES", "La fecha de fin no puede ser anterior al inicio.");
        }
        BoardStatus status = c.status() == null ? current.status() : c.status();
        if (status == BoardStatus.ACTIVE) {
            checkActiveRules(c.personId(), c.position(), id);
        }
        BoardMember updated = new BoardMember(id, c.personId(), current.personName(), current.documentNumber(),
                current.email(), current.phone(), c.position(), start, end, status, trim(c.notes()),
                current.createdAt(), null);
        try {
            if (!board.update(updated)) {
                throw ApiException.notFound("El miembro de la junta no existe.");
            }
        } catch (DuplicateKeyException e) {
            throw conflict();
        }
        audit.log(tenantId, "BOARD_MEMBER_UPDATED", "BOARD_MEMBER", id.toString(), true, Map.of());
        return get(id);
    }

    public void delete(UUID id) {
        UUID tenantId = TenantContext.require().id();
        BoardMember current = get(id);
        UUID actor = CurrentUser.get().map(u -> u.userId()).orElse(null);
        if (!board.softDelete(id, actor, clock.instant())) {
            throw ApiException.notFound("El miembro de la junta no existe.");
        }
        audit.log(tenantId, "BOARD_MEMBER_DELETED", "BOARD_MEMBER", id.toString(), true,
                Map.of("personId", current.personId().toString()));
    }

    private void checkActiveRules(UUID personId, BoardPosition position, UUID excludeId) {
        if (board.activePersonExists(personId, excludeId)) {
            throw ApiException.conflict("BOARD_PERSON_ALREADY_ACTIVE",
                    "La persona ya tiene un cargo activo en la junta directiva.");
        }
        if (position != BoardPosition.VOCAL && board.activePositionExists(position, excludeId)) {
            throw ApiException.conflict("BOARD_POSITION_ALREADY_FILLED",
                    "Ese cargo ya está ocupado por un miembro activo.");
        }
    }

    private static void validate(Command c) {
        if (c.personId() == null) {
            throw ApiException.badRequest("BOARD_PERSON_REQUIRED", "Selecciona la persona que ocupa el cargo.");
        }
        if (c.position() == null) {
            throw ApiException.badRequest("BOARD_POSITION_REQUIRED", "Selecciona el cargo de la junta directiva.");
        }
    }

    private static ApiException conflict() {
        return ApiException.conflict("BOARD_MEMBER_CONFLICT",
                "La persona o el cargo ya tienen un miembro activo en la junta directiva.");
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
