package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Configuración pública")
public class PublicConfigController {
    private static final MediaType MANIFEST_JSON = MediaType.parseMediaType("application/manifest+json");

    public record PublicConfig(String appName, String company) {}

    private final AppProperties props;

    public PublicConfigController(AppProperties props) {
        this.props = props;
    }

    @Operation(summary = "Nombre visible del producto y de la empresa (configurables por variables de entorno)")
    @GetMapping("/config")
    public PublicConfig config() {
        return new PublicConfig(props.name(), props.company());
    }

    @Operation(summary = "Manifest PWA con la marca configurada por variables de entorno")
    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Map<String, Object>> manifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", props.name());
        manifest.put("short_name", props.name());
        manifest.put("description", "Administración de copropiedades, portería y portal del residente");
        manifest.put("start_url", "/");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("orientation", "portrait-primary");
        manifest.put("background_color", "#0f172a");
        manifest.put("theme_color", "#0f766e");
        manifest.put("lang", "es-CO");
        manifest.put("categories", List.of("business", "productivity"));
        manifest.put("icons", List.of(
                icon("/icons/icon-192.png", "192x192", "any"),
                icon("/icons/icon-512.png", "512x512", "any"),
                icon("/icons/maskable-512.png", "512x512", "maskable")
        ));
        return ResponseEntity.ok()
                .contentType(MANIFEST_JSON)
                .cacheControl(CacheControl.noCache())
                .body(manifest);
    }

    private Map<String, String> icon(String src, String sizes, String purpose) {
        return Map.of("src", src, "sizes", sizes, "type", "image/png", "purpose", purpose);
    }
}
