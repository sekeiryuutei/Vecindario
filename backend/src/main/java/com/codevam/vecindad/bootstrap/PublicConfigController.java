package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Configuración pública")
public class PublicConfigController {
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
}
