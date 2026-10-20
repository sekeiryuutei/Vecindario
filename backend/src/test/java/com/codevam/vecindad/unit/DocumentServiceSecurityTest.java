package com.codevam.vecindad.unit;

import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.application.port.out.DocumentPort;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.storage.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentServiceSecurityTest {

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(Mockito.mock(DocumentPort.class), Mockito.mock(StoragePort.class));
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
}
