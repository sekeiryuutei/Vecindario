package com.codevam.vecindad.storage;

import com.codevam.vecindad.shared.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
public class LocalStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageAdapter.class);

    private final Path basePath;

    public LocalStorageAdapter(@Value("${app.storage.local-dir:./data/storage}") String storageDir) {
        this.basePath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            log.warn("No se pudo crear directorio base de almacenamiento local: {}", e.getMessage());
        }
    }

    @Override
    public String store(String path, byte[] content, String contentType) {
        try {
            Path target = resolveSafe(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return path;
        } catch (IOException e) {
            log.error("Error al escribir archivo en almacenamiento local: {}", e.getMessage(), e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Error de almacenamiento al guardar archivo.");
        }
    }

    @Override
    public byte[] load(String path) {
        try {
            Path target = resolveSafe(path);
            if (!Files.exists(target)) {
                throw ApiException.notFound("Archivo no encontrado en almacenamiento.");
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("Error al leer archivo de almacenamiento local: {}", e.getMessage(), e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Error de almacenamiento al leer archivo.");
        }
    }

    @Override
    public void delete(String path) {
        try {
            Path target = resolveSafe(path);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Error al eliminar archivo en almacenamiento: {}", e.getMessage());
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(resolveSafe(path));
    }

    private Path resolveSafe(String relative) {
        Path resolved = basePath.resolve(relative).normalize();
        if (!resolved.startsWith(basePath)) {
            throw ApiException.badRequest("PATH_TRAVERSAL", "Ruta de archivo no permitida (path traversal detectado).");
        }
        return resolved;
    }
}
