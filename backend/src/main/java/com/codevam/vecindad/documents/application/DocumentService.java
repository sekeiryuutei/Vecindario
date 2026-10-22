package com.codevam.vecindad.documents.application;

import com.codevam.vecindad.documents.application.port.out.DocumentPort;
import com.codevam.vecindad.documents.domain.Document;
import com.codevam.vecindad.documents.domain.DocumentCategory;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.storage.StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx", "pptx", "csv", "txt");
    private static final Set<String> ALLOWED_ROLES =
            Set.of("RESIDENTE", "PROPIETARIO", "CONSEJO", "ADMINISTRADOR");
    private static final Map<String, String> SAFE_CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("csv", "text/plain"),
            Map.entry("txt", "text/plain")
    );

    public record DocumentFile(
            String fileName,
            String contentType,
            long fileSize,
            byte[] data
    ) {}

    private final DocumentPort port;
    private final StoragePort storage;
    private final UnitRelationPort relations;

    public DocumentService(DocumentPort port, StoragePort storage, UnitRelationPort relations) {
        this.port = port;
        this.storage = storage;
        this.relations = relations;
    }

    public List<Document> listAll(DocumentCategory category) {
        return port.listAll(category);
    }

    public List<Document> listByUnit(UUID unitId, DocumentCategory category) {
        return port.listByUnit(unitId, category);
    }

    public List<Document> listForRole(String roleCode, DocumentCategory category) {
        List<String> accessibleRoles = getAccessibleRoles(roleCode);
        return port.listVisibleForRole(accessibleRoles, category);
    }

    public List<Document> listForRoleByUnit(String roleCode, UUID unitId) {
        return port.listVisibleForRoleByUnit(getAccessibleRoles(roleCode), unitId);
    }

    public Document getById(UUID id) {
        return port.findById(id)
                .orElseThrow(() -> ApiException.notFound("Documento no encontrado."));
    }

    @Transactional
    public Document upload(UUID uploadedBy, String title, String description, DocumentCategory category,
                           String minimumRole, String fileName, String contentType, byte[] data) {
        return upload(uploadedBy, title, description, category, minimumRole, fileName, contentType, null, data);
    }

    @Transactional
    public Document upload(UUID uploadedBy, String title, String description, DocumentCategory category,
                           String minimumRole, String fileName, String contentType, UUID unitId, byte[] data) {
        String safeTitle = validateTitle(title);
        String safeDescription = validateDescription(description);
        if (category == null) {
            throw ApiException.badRequest("INVALID_CATEGORY", "Selecciona una categoría para el documento.");
        }
        String safeRole = validateRole(minimumRole);
        String safeFileName = sanitizeFileName(fileName);
        String extension = extensionOf(safeFileName);
        validateExtension(extension);
        validateFile(data, extension);

        UUID docId = UUID.randomUUID();
        UUID tenantId = TenantContext.require().id();
        String storageKey = "tenants/" + tenantId + "/documents/" + docId + "/" + safeFileName;
        String safeContentType = SAFE_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");

        storage.store(storageKey, data, safeContentType);
        try {
            Document doc = new Document(
                    docId,
                    safeTitle,
                    safeDescription,
                    category,
                    unitId,
                    storageKey,
                    safeFileName,
                    data.length,
                    safeContentType,
                    safeRole,
                    uploadedBy,
                    true,
                    Instant.now(),
                    Instant.now()
            );
            return port.insert(doc);
        } catch (RuntimeException e) {
            // Si la fila no se persiste, no debe quedar un archivo huérfano.
            storage.delete(storageKey);
            throw e;
        }
    }

    public DocumentFile download(UUID id, String roleCode) {
        Document doc = getById(id);
        if (!canAccess(roleCode, doc.minimumRole())) {
            throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED", "No tienes permisos suficientes para acceder a este documento.");
        }
        return readFile(doc);
    }

    public DocumentFile downloadForUser(UUID id, String roleCode, UUID userId) {
        Document doc = getById(id);
        if (!canAccess(roleCode, doc.minimumRole())) {
            throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED", "No tienes permisos suficientes para acceder a este documento.");
        }
        if (doc.unitId() != null && !relations.userHasUnit(userId, doc.unitId())) {
            throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED",
                    "No tienes acceso al expediente del inmueble asociado a este documento.");
        }
        return readFile(doc);
    }

    private DocumentFile readFile(Document doc) {
        byte[] data = storage.load(doc.storageKey());
        String extension = extensionOf(doc.fileName());
        String contentType = SAFE_CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
        return new DocumentFile(sanitizeFileName(doc.fileName()), contentType, doc.fileSize(), data);
    }

    @Transactional
    public void delete(UUID id) {
        Document doc = getById(id);
        port.delete(id);
        storage.delete(doc.storageKey());
    }

    private String validateTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.length() < 3 || value.length() > 200) {
            throw ApiException.badRequest("INVALID_TITLE", "El título debe tener entre 3 y 200 caracteres.");
        }
        return value;
    }

    private String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String value = description.trim();
        if (value.length() > 500) {
            throw ApiException.badRequest("INVALID_DESCRIPTION", "La descripción no puede superar 500 caracteres.");
        }
        return value.isBlank() ? null : value;
    }

    private String validateRole(String minimumRole) {
        String role = minimumRole == null || minimumRole.isBlank()
                ? "RESIDENTE" : minimumRole.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(role)) {
            throw ApiException.badRequest("INVALID_ROLE", "El rol mínimo del documento no es válido.");
        }
        return role;
    }

    private void validateFile(byte[] data, String extension) {
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("EMPTY_FILE", "El archivo no puede estar vacío.");
        }
        if (data.length > MAX_FILE_BYTES) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "El archivo supera el tamaño máximo permitido (5 MB).");
        }
        if (!hasValidMagicBytes(data, extension)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE",
                    "El contenido del archivo no corresponde a su extensión o no está permitido.");
        }
    }

    private boolean hasValidMagicBytes(byte[] data, String extension) {
        return switch (extension) {
            case "pdf" -> startsWith(data, new byte[]{0x25, 0x50, 0x44, 0x46, 0x2d});
            case "png" -> startsWith(data, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "jpg", "jpeg" -> startsWith(data, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "webp" -> data.length >= 12 && startsWith(data, "RIFF".getBytes())
                    && new String(Arrays.copyOfRange(data, 8, 12), java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            case "docx", "xlsx", "pptx" -> startsWith(data, new byte[]{0x50, 0x4b, 0x03, 0x04});
            case "doc", "xls" -> startsWith(data, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                    (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "csv", "txt" -> !containsNul(data);
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsNul(byte[] data) {
        for (byte b : data) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeFileName(String original) {
        String name = original == null ? "documento" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        name = name.replaceAll("[\\p{Cntrl}]", "");
        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        name = name.trim().replaceAll("\\s+", "_").replaceAll("_+", "_");
        name = name.replaceAll("^\\.+", "");
        if (name.isBlank()) {
            name = "documento";
        }
        if (name.length() > 120) {
            String ext = extensionOf(name);
            String base = ext.isBlank() ? name : name.substring(0, name.length() - ext.length() - 1);
            int keep = Math.max(1, 120 - (ext.isBlank() ? 0 : ext.length() + 1));
            name = base.substring(0, Math.min(base.length(), keep)) + (ext.isBlank() ? "" : "." + ext);
        }
        return name;
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE",
                    "Solo se permiten archivos PDF, imágenes, documentos de Office, CSV o texto plano.");
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean canAccess(String userRole, String minimumRole) {
        List<String> accessible = getAccessibleRoles(userRole);
        return accessible.contains(minimumRole);
    }

    private List<String> getAccessibleRoles(String roleCode) {
        List<String> list = new ArrayList<>();
        list.add("RESIDENTE");
        if (roleCode == null) return list;

        String r = roleCode.toUpperCase(Locale.ROOT);
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
