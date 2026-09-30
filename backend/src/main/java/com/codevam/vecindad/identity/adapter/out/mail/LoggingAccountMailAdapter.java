package com.codevam.vecindad.identity.adapter.out.mail;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.AccountMailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador de correo de cuenta de la Fase 1: NO envía correos. Solo si MAIL_DEV_LOG_LINKS=true (desarrollo)
 * imprime el token en el log para poder probar activación/recuperación. La Fase de Notificaciones lo reemplaza
 * por el adaptador SMTP sin tocar los casos de uso.
 */
@Component
public class LoggingAccountMailAdapter implements AccountMailPort {
    private static final Logger log = LoggerFactory.getLogger(LoggingAccountMailAdapter.class);
    private final AppProperties props;

    public LoggingAccountMailAdapter(AppProperties props) {
        this.props = props;
    }

    @Override
    public void sendPasswordReset(String email, String fullName, String token) {
        if (props.mail().devLogLinks() && !props.isProduction()) {
            log.info("[DEV-MAIL] Recuperación de contraseña para {} -> token={}", email, token);
        } else {
            log.info("Recuperación de contraseña solicitada para un usuario (correo no enviado: adaptador SMTP no configurado)");
        }
    }

    @Override
    public void sendInvitation(String email, String fullName, String tenantName, String token) {
        if (props.mail().devLogLinks() && !props.isProduction()) {
            log.info("[DEV-MAIL] Invitación a {} para {} -> token={}", tenantName, email, token);
        } else {
            log.info("Invitación generada para un usuario (correo no enviado: adaptador SMTP no configurado)");
        }
    }
}
