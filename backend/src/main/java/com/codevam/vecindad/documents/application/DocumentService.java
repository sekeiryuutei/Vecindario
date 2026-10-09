package com.codevam.vecindad.documents.application;

import com.codevam.vecindad.documents.application.port.out.DocumentPort;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.storage.StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    public record DocumentFile(
            String fileName,
            String contentType,
            long fileSize,
            byte[] data
    ) {}

    private final DocumentPort port;
    private final StoragePort storage;

    public DocumentService(DocumentPort port, StoragePort storage) {
        this.port = port;
        this.storage = storage;
    }

    public List<Document> listAll(DocumentCategory category) {
        return port.listAll(category);
    }

    public List<Document> listForRole(String roleCode, DocumentCategory category) {
        List<String> accessibleRoles = getAccessibleRoles(roleCode);
        return port.listVisibleForRole(accessibleRoles, category);
    }

    public Document getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("Documento no encontrado."));
    }

    @Transactional
    public Document upload(UUID uploadedBy, String title, String description, DocumentCategory category,
                           String minimumRole, String fileName, String contentType, byte[] data) {
        UUID docId = UUID.randomUUID();
        UUID tenantId = TenantContext.require().id();
        String storageKey = "tenants/" + tenantId + "/documents/" + docId + "/" + fileName;

        storage.store(storageKey, data, contentType);

        Document doc = new Document(
                docId,
                title,
                description,
                category,
                storageKey,
                fileName,
                data.length,
                contentType != null ? contentType : "application/octet-stream",
                minimumRole != null ? minimumRole : "RESIDENTE",
                uploadedBy,
                true,
                Instant.now(),
                Instant.now()
        );

        return port.insert(doc);
    }

    public DocumentFile download(UUID id, String roleCode) {
        Document doc = getById(id);
        if (!canAccess(roleCode, doc.minimumRole())) {
            throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED", "No tienes permisos suficientes para acceder a este documento.");
        }
        byte[] data = storage.load(doc.storageKey());
        return new DocumentFile(doc.fileName(), doc.contentType(), doc.fileSize(), data);
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = getById(id);
        port.delete(id);
        storage.delete(doc.storageKey());
    }

    private boolean canAccess(String userRole, String minimumRole) {
        List<String> accessible = getAccessibleRoles(userRole);
        return accessible.contains(minimumRole);
    }

    private List<String> getAccessibleRoles(String roleCode) {
        List<String> list = new ArrayList<>();
        list.add("RESIDENTE");
        if (roleCode == null) return list;

        String r = roleCode.toUpperCase();
        if (r.contains("ADMIN") || r.contains("SUPER_ADMIN")) {
            list.add("PROPIETARIO");
            list.add("CONSEJO");
            list.add("ADMINISTRADOR");
        } else if (r.contains("CONSEJO")) {
            list.add("PROPIETARIO");
            list.add("CONSEJO");
        } else if (r.contains("PROPIETARIO")) {
            list.add("PROPIETARIO");
        }
        return list;
    }
}
