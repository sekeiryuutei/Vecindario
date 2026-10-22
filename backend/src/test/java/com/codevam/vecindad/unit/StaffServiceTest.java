package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.staff.application.StaffService;
import com.codevam.vecindad.staff.application.port.out.StaffPort;
import com.codevam.vecindad.staff.domain.StaffMember;
import com.codevam.vecindad.staff.domain.StaffStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private StaffPort staff;
    private AuditService audit;
    private StaffService service;

    @BeforeEach
    void setUp() {
        staff = Mockito.mock(StaffPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new StaffService(staff, audit, CLOCK);
        TenantContext.set(TENANT);
        authenticate();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNormalizesDocumentTypeAndStartDate() {
        when(staff.documentExists("CC", "123456", null)).thenReturn(false);
        AtomicReference<StaffMember> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(staff).insert(any(StaffMember.class));
        when(staff.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        StaffMember created = service.create(command("  ", "  123456  ", null, null));

        assertEquals("CC", created.documentType());
        assertEquals("123456", created.documentNumber());
        assertEquals(LocalDate.of(2026, 10, 21), created.startDate());
        assertEquals(StaffStatus.ACTIVE, created.status());
        verify(audit).log(eq(TENANT_ID), eq("STAFF_CREATED"), eq("STAFF"), anyString(), eq(true), anyMap());
    }

    @Test
    void createRejectsDuplicateDocument() {
        when(staff.documentExists("CC", "123456", null)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command("CC", "123456", null, null)));

        assertEquals("STAFF_DOCUMENT_EXISTS", error.getCode());
        verify(staff, never()).insert(any());
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        when(staff.documentExists("CC", "123456", null)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.create(command("CC", "123456", LocalDate.of(2026, 10, 21),
                        LocalDate.of(2026, 10, 20))));

        assertEquals("STAFF_DATES_INVALID", error.getCode());
        verify(staff, never()).insert(any());
    }

    @Test
    void updateRejectsDocumentOwnedByAnotherMember() {
        when(staff.findById(STAFF_ID)).thenReturn(Optional.of(member()));
        when(staff.documentExists("CC", "999999", STAFF_ID)).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.update(STAFF_ID, command("CC", "999999", null, null)));

        assertEquals("STAFF_DOCUMENT_EXISTS", error.getCode());
        verify(staff, never()).update(any());
    }

    @Test
    void deleteSoftDeletesAndAudits() {
        when(staff.findById(STAFF_ID)).thenReturn(Optional.of(member()));
        when(staff.softDelete(eq(STAFF_ID), eq(USER_ID), eq(CLOCK.instant()))).thenReturn(true);

        service.delete(STAFF_ID);

        verify(audit).log(eq(TENANT_ID), eq("STAFF_DELETED"), eq("STAFF"), eq(STAFF_ID.toString()), eq(true),
                anyMap());
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "admin@demo.local", "Administrador Demo", null,
                TENANT_ID, "tenant_demo_norte", "ADMINISTRADOR", Set.of("STAFF_VIEW", "STAFF_MANAGE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static StaffService.Command command(String documentType, String documentNumber, LocalDate startDate,
                                                LocalDate endDate) {
        return new StaffService.Command("  Ana Torres  ", documentType, documentNumber, "  Auxiliar  ",
                "  Servicios  ", "3001234567", "ana@demo.local", "Lunes a viernes", startDate, endDate,
                StaffStatus.ACTIVE, null);
    }

    private static StaffMember member() {
        return new StaffMember(STAFF_ID, "Ana Torres", "CC", "123456", "Auxiliar", "Servicios",
                "3001234567", "ana@demo.local", "Lunes a viernes", LocalDate.of(2026, 1, 15), null,
                StaffStatus.ACTIVE, null, Instant.parse("2026-01-15T00:00:00Z"), null);
    }
}
