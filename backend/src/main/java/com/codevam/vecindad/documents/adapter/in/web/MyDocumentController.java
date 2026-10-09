package com.codevam.vecindad.documents.adapter.in.web;

import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/documents")
@Tag(name = "Mis Documentos (Residente)")
public class MyDocumentController {

    private final DocumentService service;

    public MyDocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Lista documentos disponibles según mi rol")
    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENTS_VIEW')")
    public List<Document> listMy(@RequestParam(required = false) DocumentCategory category) {
        var user = CurrentUser.requireTenant();
        String role = user.roleCode() != null ? user.roleCode() : "RESIDENTE";
        return service.listForRole(role, category);
    }

    @Operation(summary = "Descarga un documento")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('DOCUMENTS_VIEW')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        var user = CurrentUser.requireTenant();
        String role = user.roleCode() != null ? user.roleCode() : "RESIDENTE";
        DocumentService.DocumentFile doc = service.download(id, role);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (doc.contentType() != null) {
                mediaType = MediaType.parseMediaType(doc.contentType());
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.fileName() + "\"")
                .body(doc.data());
    }
}
