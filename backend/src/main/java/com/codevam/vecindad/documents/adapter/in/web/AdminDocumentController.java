package com.codevam.vecindad.documents.adapter.in.web;

import com.codevam.vecindad.documents.application.DocumentService;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
        if (file.isEmpty()) {
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

    @Operation(summary = "Elimina un documento")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DOCUMENTS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
