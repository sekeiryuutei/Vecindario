package com.codevam.vecindad.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi(AppProperties props) {
        return new OpenAPI()
                .info(new Info().title(props.name() + " API").version("v1")
                        .description("API REST de " + props.name() + " (" + props.company() + "). "
                                + "Flujo: POST /api/v1/auth/login y luego POST /api/v1/auth/select-tenant si el usuario "
                                + "pertenece a varias copropiedades. Errores con formato {code, message, timestamp, path, traceId, details}."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
