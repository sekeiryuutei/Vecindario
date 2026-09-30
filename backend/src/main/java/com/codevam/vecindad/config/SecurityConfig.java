package com.codevam.vecindad.config;

import com.codevam.vecindad.identity.adapter.out.security.JwtAuthenticationFilter;
import com.codevam.vecindad.identity.adapter.out.security.JwtService;
import com.codevam.vecindad.identity.application.port.out.MembershipPort;
import com.codevam.vecindad.identity.application.port.out.RolePermissionPort;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.shared.error.ErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UserPort users,
                                                   MembershipPort memberships, RolePermissionPort rolePermissions,
                                                   ObjectMapper mapper, CorsConfigurationSource cors) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwt, users, memberships, rolePermissions);
        http
                .csrf(csrf -> csrf.disable()) // API stateless con Bearer token, sin cookies de sesión
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.contentTypeOptions(o -> {}).frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/public/**", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole("SUPER_ADMIN_PLATFORM")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            boolean revoked = req.getAttribute(JwtAuthenticationFilter.TENANT_ACCESS_REVOKED_ATTR) != null;
                            if (revoked) {
                                ErrorWriter.write(req, res, mapper, HttpStatus.FORBIDDEN, "TENANT_ACCESS_REVOKED",
                                        "Ya no tienes acceso a esta copropiedad. Selecciona otra o inicia sesión de nuevo.");
                            } else {
                                ErrorWriter.write(req, res, mapper, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                        "Debes iniciar sesión para continuar.");
                            }
                        })
                        .accessDeniedHandler((req, res, ex) -> ErrorWriter.write(req, res, mapper, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "No tienes permisos para realizar esta acción.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        List<String> origins = props.security().corsAllowedOrigins() == null ? List.of()
                : props.security().corsAllowedOrigins().stream().filter(s -> s != null && !s.isBlank()).toList();
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Trace-Id", "Idempotency-Key"));
        c.setExposedHeaders(List.of("X-Trace-Id"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }
}
