package com.codevam.vecindad.identity.adapter.in.web;

import com.codevam.vecindad.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}
    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}
    public record SelectTenantRequest(@NotNull UUID tenantId) {}
    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetPasswordRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String newPassword) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Operation(summary = "Inicia sesión. Si el usuario tiene una sola copropiedad (o la última usada sigue vigente) el token ya trae tenant.")
    @PostMapping("/login")
    public AuthService.SessionResult login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.email(), req.password());
    }

    @Operation(summary = "Rota el refresh token y emite un nuevo access token")
    @PostMapping("/refresh")
    public AuthService.SessionResult refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @Operation(summary = "Revoca el refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @Operation(summary = "Selecciona la copropiedad activa (el backend valida la membresía)")
    @PostMapping("/select-tenant")
    public AuthService.TenantTokenResult selectTenant(@Valid @RequestBody SelectTenantRequest req) {
        return auth.selectTenant(req.tenantId());
    }

    @Operation(summary = "Cambia la contraseña del usuario autenticado y revoca sus refresh tokens")
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(req.currentPassword(), req.newPassword());
    }

    @Operation(summary = "Solicita recuperación de contraseña (respuesta idéntica exista o no la cuenta)")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
    }

    @Operation(summary = "Define una nueva contraseña con el token recibido (también activa invitaciones)")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
    }

    @Operation(summary = "Usuario autenticado, copropiedades y permisos efectivos")
    @GetMapping("/me")
    public AuthService.MeResult me() {
        return auth.me();
    }
}
