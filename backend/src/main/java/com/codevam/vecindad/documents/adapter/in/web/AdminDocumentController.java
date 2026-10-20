package com.codevam.vecindad.documents.adapter.in.web;

import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documentos (Administración)")
public class AdminDocumentController {

    private final DocumentService service;

    public AdminDocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todos los documentos activos")
    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENTS_MANAGE')")
    public List<Document> list(@RequestParam(required = false) DocumentCategory category) {
        return service.listAll(category);
    }

    @Operation(summary = "Sube un nuevo documento a la copropiedad")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('DOCUMENTS_MANAGE')")
    public Document upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("category") DocumentCategory category,
            @RequestParam(value = "minimumRole", defaultValue = "RESIDENTE") String minimumRole
    ) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "El archivo no puede estar vacío.");
        }
        UUID userId = CurrentUser.requireTenant().userId();
        try {
            return service.upload(
                    userId, title, description, category, minimumRole,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes()
            );
        } catch (IOException e) {
            throw ApiException.badRequest("FILE_PROCESSING_ERROR", "Error al procesar archivo: " + e.getMessage());
        }
    }

    @Operation(summary = "Descarga un documento desde la administración")
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('DOCUMENTS_MANAGE')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DocumentService.DocumentFile doc = service.download(id, "ADMINISTRADOR");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (doc.contentType() != null) {
                mediaType = MediaType.parseMediaType(doc.contentType());
            }
        } catch (RuntimeException ignored) {
            // Se conserva el tipo seguro por defecto.
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(doc.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(doc.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(doc.data());
    }

    @Operation(summary = "Elimina un documento")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENTS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
