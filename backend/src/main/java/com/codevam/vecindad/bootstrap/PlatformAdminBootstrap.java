package com.codevam.vecindad.bootstrap;

import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Crea el primer superadministrador de plataforma cuando la instalación no usa el seed de demostración.
 * Es idempotente: si el correo ya existe con el rol de plataforma no modifica nada. Si el correo pertenece a un
 * usuario operativo, falla el arranque para evitar una elevación de privilegios accidental.
 */
@Component
@Order(10)
public class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final AppProperties props;
    private final UserPort users;
    private final PasswordEncoder encoder;

    public PlatformAdminBootstrap(AppProperties props, UserPort users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = props.bootstrap();
        if (bootstrap == null
                || (!StringUtils.hasText(bootstrap.email()) && !StringUtils.hasText(bootstrap.password()))) {
            return;
        }
        if (!StringUtils.hasText(bootstrap.email()) || !StringUtils.hasText(bootstrap.password())) {
            throw new IllegalStateException("APP_BOOTSTRAP_EMAIL y APP_BOOTSTRAP_PASSWORD deben configurarse juntos.");
        }

        String email = bootstrap.email().trim().toLowerCase(Locale.ROOT);
        if (!isValidEmail(email)) {
            throw new IllegalStateException("APP_BOOTSTRAP_EMAIL no es una dirección de correo válida.");
        }
        validatePassword(bootstrap.password());

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (Roles.SUPER_ADMIN_PLATFORM.equals(existing.get().platformRole())) {
                log.info("Superadministrador de plataforma ya existe para {}; no se modifica.", email);
                return;
            }
            throw new IllegalStateException("APP_BOOTSTRAP_EMAIL pertenece a un usuario existente sin rol "
                    + Roles.SUPER_ADMIN_PLATFORM + "; usa otro correo o promueve la cuenta por un procedimiento auditado.");
        }

        String name = StringUtils.hasText(bootstrap.name()) ? bootstrap.name().trim() : "Administrador de plataforma";
        users.insert(email, name, encoder.encode(bootstrap.password()), UserStatus.ACTIVE, Roles.SUPER_ADMIN_PLATFORM);
        log.info("Superadministrador de plataforma creado para {}.", email);
    }

    private boolean isValidEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 && at == email.lastIndexOf('@') && at < email.length() - 1
                && !email.contains(" ") && email.length() <= 254;
    }

    private void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        boolean valid = password.length() >= 12
                && bytes <= 72
                && password.chars().anyMatch(Character::isLetter)
                && password.chars().anyMatch(Character::isDigit);
        if (!valid) {
            throw new IllegalStateException(
                    "APP_BOOTSTRAP_PASSWORD debe tener entre 12 y 72 bytes e incluir letras y números.");
        }
    }
}
