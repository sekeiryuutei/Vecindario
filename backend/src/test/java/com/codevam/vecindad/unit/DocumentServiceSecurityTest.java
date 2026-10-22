package com.codevam.vecindad.unit;

import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.application.port.out.DocumentPort;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.storage.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceSecurityTest {

    private DocumentPort port;
    private StoragePort storage;
    private UnitRelationPort relations;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(DocumentPort.class);
        storage = Mockito.mock(StoragePort.class);
        relations = Mockito.mock(UnitRelationPort.class);
        service = new DocumentService(port, storage, relations);
    }

    @Test
    void rejectsExecutableDisguisedAsPdf() {
        byte[] executable = "MZ\u0000\u0000binary".getBytes(StandardCharsets.UTF_8);

        ApiException ex = assertThrows(ApiException.class, () -> service.upload(UUID.randomUUID(),
                "Documento de prueba", null, DocumentCategory.OTRO, "RESIDENTE",
                "manual.pdf", "application/pdf", executable));

        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
    }

    @Test
    void rejectsDisallowedExtension() {
        ApiException ex = assertThrows(ApiException.class, () -> service.upload(UUID.randomUUID(),
                "Documento de prueba", null, DocumentCategory.OTRO, "RESIDENTE",
                "instalador.exe", "application/octet-stream", "MZ".getBytes(StandardCharsets.UTF_8)));

        assertEquals("UNSUPPORTED_FILE_TYPE", ex.getCode());
    }

    @Test
    void rejectsPathTraversalFileNameAndInvalidRoleBeforePersisting() {
        ApiException ex = assertThrows(ApiException.class, () -> service.upload(UUID.randomUUID(),
                "Documento de prueba", null, DocumentCategory.OTRO, "SUPER_ADMIN_PLATFORM",
                "../../manual.txt", "text/plain", "contenido".getBytes(StandardCharsets.UTF_8)));

        assertEquals("INVALID_ROLE", ex.getCode());
    }

    @Test
    void rejectsUnitDocumentDownloadWhenUserHasNoRelationToTheUnit() {
        UUID documentId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(port.findById(documentId)).thenReturn(Optional.of(document(documentId, unitId)));
        when(relations.userHasUnit(userId, unitId)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.downloadForUser(documentId, "RESIDENTE", userId));

        assertEquals("DOCUMENT_ACCESS_DENIED", ex.getCode());
        verifyNoInteractions(storage);
    }

    @Test
    void downloadsUnitDocumentForRelatedResident() {
        UUID documentId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] content = "contenido".getBytes(StandardCharsets.UTF_8);
        when(port.findById(documentId)).thenReturn(Optional.of(document(documentId, unitId)));
        when(relations.userHasUnit(userId, unitId)).thenReturn(true);
        when(storage.load("tenants/demo/documents/manual.txt")).thenReturn(content);

        DocumentService.DocumentFile file = service.downloadForUser(documentId, "RESIDENTE", userId);

        assertEquals("manual.txt", file.fileName());
        assertArrayEquals(content, file.data());
    }

    private static Document document(UUID id, UUID unitId) {
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        return new Document(id, "Manual", "Documento del inmueble", DocumentCategory.OTRO, unitId,
                "tenants/demo/documents/manual.txt", "manual.txt", 9, "text/plain", "RESIDENTE",
                UUID.randomUUID(), true, now, now);
    }
}
