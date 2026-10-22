package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.board.application.BoardService;
import com.codevam.vecindad.board.application.port.out.BoardPort;
import com.codevam.vecindad.board.domain.BoardMember;
import com.codevam.vecindad.board.domain.BoardPosition;
import com.codevam.vecindad.board.domain.BoardStatus;
import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID BOARD_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private BoardPort board;
    private PersonPort people;
    private AuditService audit;
    private BoardService service;

    @BeforeEach
    void setUp() {
        board = Mockito.mock(BoardPort.class);
        people = Mockito.mock(PersonPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new BoardService(board, people, audit, CLOCK);
        TenantContext.set(TENANT);
        authenticate();
        when(people.findById(PERSON_ID)).thenReturn(Optional.of(person(PERSON_ID)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsPersonWithActivePosition() {
        when(board.activePersonExists(PERSON_ID, null)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command(PERSON_ID, BoardPosition.PRESIDENTE)));

        assertEquals("BOARD_PERSON_ALREADY_ACTIVE", error.getCode());
        verify(board, never()).insert(any());
    }

    @Test
    void createRejectsFilledUniquePosition() {
        when(board.activePersonExists(PERSON_ID, null)).thenReturn(false);
        when(board.activePositionExists(BoardPosition.TESORERO, null)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command(PERSON_ID, BoardPosition.TESORERO)));

        assertEquals("BOARD_POSITION_ALREADY_FILLED", error.getCode());
        verify(board, never()).insert(any());
    }

    @Test
    void createAllowsAnotherVocalWithoutCheckingUniquePosition() {
        when(board.activePersonExists(PERSON_ID, null)).thenReturn(false);
        AtomicReference<BoardMember> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(board).insert(any(BoardMember.class));
        when(board.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        BoardMember created = service.create(command(PERSON_ID, BoardPosition.VOCAL));

        assertEquals(BoardPosition.VOCAL, created.position());
        assertEquals(BoardStatus.ACTIVE, created.status());
        verify(board, never()).activePositionExists(any(), any());
        verify(audit).log(eq(TENANT_ID), eq("BOARD_MEMBER_CREATED"), eq("BOARD_MEMBER"), anyString(),
                eq(true), anyMap());
    }

    @Test
    void createTranslatesUniqueConstraintToConflict() {
        when(board.activePersonExists(PERSON_ID, null)).thenReturn(false);
        doThrow(new DuplicateKeyException("duplicado")).when(board).insert(any());

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command(PERSON_ID, BoardPosition.VOCAL)));

        assertEquals("BOARD_MEMBER_CONFLICT", error.getCode());
    }

    @Test
    void updatePreservesPersonDataAndAudits() {
        BoardMember current = member(BoardPosition.SECRETARIO);
        AtomicReference<BoardMember> updated = new AtomicReference<>();
        when(board.findById(BOARD_ID)).thenReturn(Optional.of(current))
                .thenAnswer(inv -> Optional.ofNullable(updated.get()));
        when(board.activePersonExists(PERSON_ID, BOARD_ID)).thenReturn(false);
        when(board.activePositionExists(BoardPosition.SECRETARIO, BOARD_ID)).thenReturn(false);
        when(board.update(any(BoardMember.class))).thenAnswer(inv -> {
            updated.set(inv.getArgument(0));
            return true;
        });

        BoardMember result = service.update(BOARD_ID, command(PERSON_ID, BoardPosition.SECRETARIO));

        assertEquals("Persona Demo", result.personName());
        assertEquals("123456", result.documentNumber());
        verify(audit).log(eq(TENANT_ID), eq("BOARD_MEMBER_UPDATED"), eq("BOARD_MEMBER"),
                eq(BOARD_ID.toString()), eq(true), anyMap());
    }

    @Test
    void deleteSoftDeletesAndAudits() {
        when(board.findById(BOARD_ID)).thenReturn(Optional.of(member(BoardPosition.VOCAL)));
        when(board.softDelete(eq(BOARD_ID), eq(USER_ID), eq(CLOCK.instant()))).thenReturn(true);

        service.delete(BOARD_ID);

        verify(audit).log(eq(TENANT_ID), eq("BOARD_MEMBER_DELETED"), eq("BOARD_MEMBER"),
                eq(BOARD_ID.toString()), eq(true), anyMap());
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "admin@demo.local", "Administrador Demo", null,
                TENANT_ID, "tenant_demo_norte", "ADMINISTRADOR", Set.of("BOARD_VIEW", "BOARD_MANAGE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static BoardService.Command command(UUID personId, BoardPosition position) {
        return new BoardService.Command(personId, position, LocalDate.of(2026, 1, 15), null,
                BoardStatus.ACTIVE, "  Nota de prueba  ");
    }

    private static Person person(UUID id) {
        return new Person(id, "CC", "123456", "Persona Demo", "persona@demo.local", "3001234567", null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static BoardMember member(BoardPosition position) {
        return new BoardMember(BOARD_ID, PERSON_ID, "Persona Demo", "123456", "persona@demo.local",
                "3001234567", position, LocalDate.of(2026, 1, 15), null, BoardStatus.ACTIVE, "Nota",
                Instant.parse("2026-01-15T00:00:00Z"), null);
    }
}
