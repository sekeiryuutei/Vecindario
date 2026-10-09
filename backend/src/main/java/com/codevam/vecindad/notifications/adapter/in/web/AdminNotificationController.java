package com.codevam.vecindad.notifications.adapter.in.web;

import com.codevam.vecindad.notifications.application.NotificationService;
import com.codevam.vecindad.notifications.domain.NotificationLog;
import com.codevam.vecindad.notifications.domain.TenantNotificationSettings;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones y Alertas (Administración)")
public class AdminNotificationController {

    public record UpdateSettingsRequest(
            boolean emailEnabled,
            boolean pushEnabled,
            boolean whatsappEnabled,
            @NotBlank @Size(max = 100) String senderDisplayName
    ) {}

    private final NotificationService service;

    public AdminNotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Obtiene la configuración de canales de notificación")
    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_MANAGE')")
    public TenantNotificationSettings getSettings() {
        return service.getSettings();
    }

    @Operation(summary = "Actualiza la configuración de canales de notificación")
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_MANAGE')")
    public TenantNotificationSettings updateSettings(@Valid @RequestBody UpdateSettingsRequest r) {
        TenantNotificationSettings current = service.getSettings();
        return service.updateSettings(new TenantNotificationSettings(
                current.id(), r.emailEnabled(), r.pushEnabled(), r.whatsappEnabled(),
                r.senderDisplayName(), null
        ));
    }

    @Operation(summary = "Dispara recordatorios masivos de cartera vencida a propietarios")
    @PostMapping("/portfolio-reminders")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_MANAGE')")
    public Map<String, Object> sendPortfolioReminders() {
        int sent = service.sendPortfolioOverdueReminders();
        return Map.of("status", "SUCCESS", "remindersDispatched", sent);
    }

    @Operation(summary = "Consulta los registros (logs) de notificaciones enviadas")
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_MANAGE')")
    public PageResult<NotificationLog> listLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listLogs(userId, page, size);
    }
}

