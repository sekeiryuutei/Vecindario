package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.people.application.PersonService;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.pets.application.PetService;
import com.codevam.vecindad.properties.application.PropertyUnitService;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.properties.domain.UnitStatus;
import com.codevam.vecindad.properties.domain.UnitType;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import com.codevam.vecindad.unitfiles.application.UnitFileService;
import com.codevam.vecindad.unitfiles.application.port.out.UnitFileNotePort;
import com.codevam.vecindad.unitfiles.domain.UnitFileNote;
import com.codevam.vecindad.vehicles.application.VehicleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnitFileServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID NOTE_ID = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private PropertyUnitService units;
    private PersonService people;
    private VehicleService vehicles;
    private PetService pets;
    private DocumentService documents;
    private UnitFileNotePort notes;
    private UnitRelationPort relations;
    private AuditService audit;
    private UnitFileService service;

    @BeforeEach
    void setUp() {
        units = Mockito.mock(PropertyUnitService.class);
        people = Mockito.mock(PersonService.class);
        vehicles = Mockito.mock(VehicleService.class);
        pets = Mockito.mock(PetService.class);
        documents = Mockito.mock(DocumentService.class);
        notes = Mockito.mock(UnitFileNotePort.class);
        relations = Mockito.mock(UnitRelationPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new UnitFileService(units, people, vehicles, pets, documents, notes, relations, audit, CLOCK);
        TenantContext.set(TENANT);
        authenticate();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void viewOwnRejectsUnitWithoutResidentRelation() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.viewOwn(UNIT_ID));

        assertEquals("UNIT_FILE_FORBIDDEN", error.getCode());
        verifyNoInteractions(units, notes, documents);
    }

    @Test
    void viewOwnUsesPublicNotesAndRoleVisibleDocuments() {
        when(relations.userHasUnit(USER_ID, UNIT_ID)).thenReturn(true);
        when(units.get(UNIT_ID)).thenReturn(unit());
        when(people.relationsOfUnit(UNIT_ID)).thenReturn(List.of());
        when(vehicles.search(null, UNIT_ID, null, 0, 100)).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        when(pets.search(null, UNIT_ID, null, null, 0, 100)).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        Document document = document();
        UnitFileNote publicNote = note(false);
        when(documents.listForRoleByUnit("PROPIETARIO", UNIT_ID)).thenReturn(List.of(document));
        when(notes.listByUnit(UNIT_ID, false)).thenReturn(List.of(publicNote));

        UnitFileService.View view = service.viewOwn(UNIT_ID);

        assertEquals(UNIT_ID, view.unit().id());
        assertEquals(List.of(publicNote), view.notes());
        assertEquals(List.of(document), view.documents());
        verify(documents).listForRoleByUnit("PROPIETARIO", UNIT_ID);
        verify(notes).listByUnit(UNIT_ID, false);
    }

    @Test
    void administrativeViewIncludesInternalNotes() {
        when(units.get(UNIT_ID)).thenReturn(unit());
        when(people.relationsOfUnit(UNIT_ID)).thenReturn(List.of());
        when(vehicles.search(null, UNIT_ID, null, 0, 100)).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        when(pets.search(null, UNIT_ID, null, null, 0, 100)).thenReturn(PageResult.of(List.of(), 0, 100, 0));
        when(documents.listByUnit(UNIT_ID, null)).thenReturn(List.of(document()));
        UnitFileNote internalNote = note(true);
        when(notes.listByUnit(UNIT_ID, true)).thenReturn(List.of(internalNote));

        UnitFileService.View view = service.view(UNIT_ID);

        assertEquals(List.of(internalNote), view.notes());
        verify(notes).listByUnit(UNIT_ID, true);
    }

    @Test
    void createNoteValidatesTitleBeforeInserting() {
        when(units.get(UNIT_ID)).thenReturn(unit());

        ApiException error = assertThrows(ApiException.class,
                () -> service.createNote(UNIT_ID, new UnitFileService.NoteCommand("  ", "Contenido", false)));

        assertEquals("UNIT_FILE_NOTE_TITLE_REQUIRED", error.getCode());
        verify(notes, never()).insert(any());
    }

    @Test
    void createNoteInsertsAndReturnsNote() {
        when(units.get(UNIT_ID)).thenReturn(unit());
        AtomicReference<UnitFileNote> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return null;
        }).when(notes).insert(any(UnitFileNote.class));
        when(notes.findById(any(), eq(UNIT_ID))).thenAnswer(inv -> Optional.ofNullable(saved.get()));

        UnitFileNote created = service.createNote(UNIT_ID,
                new UnitFileService.NoteCommand("  Acta de entrega  ", "  Documento firmado  ", true));

        assertEquals("Acta de entrega", created.title());
        assertEquals("Documento firmado", created.body());
        assertTrue(created.internal());
        verify(audit).log(eq(TENANT_ID), eq("UNIT_FILE_NOTE_CREATED"), eq("UNIT_FILE_NOTE"), anyString(),
                eq(true), anyMap());
    }

    @Test
    void deleteNoteRejectsNoteFromAnotherUnit() {
        when(units.get(UNIT_ID)).thenReturn(unit());
        when(notes.findById(NOTE_ID, UNIT_ID)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.deleteNote(UNIT_ID, NOTE_ID));
        verify(notes, never()).softDelete(any(), any(), any(), any());
    }

    @Test
    void deleteNoteSoftDeletesAndAudits() {
        when(units.get(UNIT_ID)).thenReturn(unit());
        when(notes.findById(NOTE_ID, UNIT_ID)).thenReturn(Optional.of(note(false)));
        when(notes.softDelete(eq(NOTE_ID), eq(UNIT_ID), eq(USER_ID), eq(CLOCK.instant()))).thenReturn(true);

        service.deleteNote(UNIT_ID, NOTE_ID);

        verify(audit).log(eq(TENANT_ID), eq("UNIT_FILE_NOTE_DELETED"), eq("UNIT_FILE_NOTE"),
                eq(NOTE_ID.toString()), eq(true), anyMap());
    }

    @Test
    void seedNoteInsertsWhenOnlyAnotherTitleExists() {
        when(notes.listByUnit(UNIT_ID, true)).thenReturn(List.of(note(false)));

        service.seedNote(UNIT_ID, "Seguimiento interno", "Contenido interno", true);

        verify(notes).insert(argThat(saved -> saved.unitId().equals(UNIT_ID)
                && saved.title().equals("Seguimiento interno") && saved.internal()));
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "propietario@demo.local", "Propietario Demo", null,
                TENANT_ID, "tenant_demo_norte", "PROPIETARIO", Set.of("UNIT_FILE_VIEW_OWN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static PropertyUnit unit() {
        return new PropertyUnit(UNIT_ID, UnitType.APARTAMENTO, "T1-101", "101", "T1", 1,
                new BigDecimal("1.25"), new BigDecimal("72.50"), UnitStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), null, null);
    }

    private static Document document() {
        return new Document(UUID.randomUUID(), "Reglamento", "Reglamento interno", DocumentCategory.OTRO,
                UNIT_ID, "docs/reglamento.pdf", "reglamento.pdf", 1024, "application/pdf", "RESIDENTE",
                UUID.randomUUID(), true, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    private static UnitFileNote note(boolean internal) {
        return new UnitFileNote(NOTE_ID, UNIT_ID, "Acta de entrega", "Documento firmado", internal, USER_ID,
                Instant.parse("2026-01-01T00:00:00Z"), null);
    }
}
