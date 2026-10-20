package com.codevam.vecindad.identity.adapter.out.mail;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.AccountMailPort;
import com.codevam.vecindad.shared.mail.MailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Correo de cuenta (invitaciones y recuperación) mediante SMTP. En desarrollo,
 * si MAIL_DEV_LOG_LINKS=true y no hay SMTP, el token se imprime en el log para
 * poder probar el flujo sin un proveedor externo.
 */
@Component
public class AccountMailAdapter implements AccountMailPort {

    private static final Logger log = LoggerFactory.getLogger(AccountMailAdapter.class);

    private final MailGateway mail;
    private final AppProperties props;

    public AccountMailAdapter(MailGateway mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    public void sendPasswordReset(String email, String fullName, String token) {
        String link = link("/restablecer-contrasena", token);
        String subject = props.name() + " - Recuperación de contraseña";
        String text = "Hola " + safeName(fullName) + ":\n\n"
                + "Recibimos una solicitud para restablecer tu contraseña. Abre el siguiente enlace (válido por 30 minutos):\n"
                + link + "\n\nSi no realizaste esta solicitud, puedes ignorar este mensaje.\n\n"
                + props.company();
        boolean sent = deliver(email, subject, text, emailHtml("Recuperación de contraseña",
                "Hola " + safeName(fullName) + ", recibimos una solicitud para restablecer tu contraseña.",
                "Restablecer contraseña", link,
                "El enlace es válido por 30 minutos. Si no realizaste esta solicitud, ignora este mensaje."));
        if (!sent && devLog()) {
            log.info("[DEV-MAIL] Recuperación de contraseña para {} -> {}", email, link);
        }
    }

    @Override
    public void sendInvitation(String email, String fullName, String tenantName, String token) {
        String link = link("/activar-cuenta", token);
        String subject = props.name() + " - Invitación a " + tenantName;
        String text = "Hola " + safeName(fullName) + ":\n\n"
                + "La administración de " + tenantName + " te invitó a usar " + props.name() + ".\n"
                + "Activa tu cuenta y define tu contraseña en el siguiente enlace (válido por 72 horas):\n"
                + link + "\n\n" + props.company();
        boolean sent = deliver(email, subject, text, emailHtml("Te damos la bienvenida a " + tenantName,
                "Hola " + safeName(fullName) + ", la administración de " + tenantName
                        + " te invitó a la plataforma de copropiedad.",
                "Activar mi cuenta", link,
                "El enlace es válido por 72 horas. Si esperabas esta invitación, activa tu cuenta para comenzar."));
        if (!sent && devLog()) {
            log.info("[DEV-MAIL] Invitación a {} para {} -> {}", tenantName, email, link);
        }
    }

    private boolean deliver(String to, String subject, String text, String html) {
        if (!mail.isConfigured()) {
            if (!props.isProduction() && props.mail() != null && props.mail().devLogLinks()) {
                log.info("SMTP no configurado; el enlace de desarrollo se imprimirá en el log.");
            } else {
                log.warn("SMTP no configurado. No se pudo enviar '{}' a {}.", subject, to);
            }
            return false;
        }
        try {
            mail.send(to, subject, text, html);
            return true;
        } catch (RuntimeException e) {
            log.error("Fallo el envío de '{}' a {}: {}", subject, to, e.getMessage());
            return false;
        }
    }

    private boolean devLog() {
        return !props.isProduction() && props.mail() != null && props.mail().devLogLinks();
    }

    private String link(String path, String token) {
        String base = props.publicUrl() == null ? "" : props.publicUrl().replaceAll("/+$", "");
        return base + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String safeName(String fullName) {
        return fullName == null || fullName.isBlank() ? "vecino(a)" : escape(fullName);
    }

    private String emailHtml(String title, String intro, String action, String link, String footnote) {
        return """
                <!doctype html>
                <html lang="es"><body style="margin:0;background:#f4f6f4;font-family:Arial,Helvetica,sans-serif;color:#17231f">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 12px">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" style="max-width:560px;background:#ffffff;border-radius:14px;overflow:hidden">
                      <tr><td style="background:#0f5c4d;padding:22px 28px;color:#ffffff;font-size:20px;font-weight:bold">%s</td></tr>
                      <tr><td style="padding:30px 28px">
                        <h1 style="font-size:22px;margin:0 0 14px">%s</h1>
                        <p style="font-size:15px;line-height:1.6;margin:0 0 24px">%s</p>
                        <p style="margin:0 0 24px"><a href="%s" style="background:#0f5c4d;color:#ffffff;text-decoration:none;padding:13px 22px;border-radius:8px;display:inline-block;font-weight:bold">%s</a></p>
                        <p style="font-size:13px;line-height:1.6;color:#5c6963;margin:0">%s</p>
                      </td></tr>
                      <tr><td style="padding:18px 28px;background:#f7f9f7;font-size:12px;color:#6b7771">%s · %s</td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(escape(props.name()), escape(title), escape(intro), escape(link), escape(action),
                escape(footnote), escape(props.name()), escape(props.company()));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
