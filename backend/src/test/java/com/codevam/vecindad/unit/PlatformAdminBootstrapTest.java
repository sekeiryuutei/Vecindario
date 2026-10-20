package com.codevam.vecindad.unit;

import com.codevam.vecindad.bootstrap.PlatformAdminBootstrap;
import com.codevam.vecindad.config.AppProperties;
import com.codevam.vecindad.identity.application.port.out.UserPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.identity.domain.User;
import com.codevam.vecindad.identity.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAdminBootstrapTest {

    private UserPort users;
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        users = Mockito.mock(UserPort.class);
        encoder = Mockito.mock(PasswordEncoder.class);
        when(encoder.encode("StrongPass123!")).thenReturn("hash");
    }

    @Test
    void createsPlatformAdminWhenEmailDoesNotExist() {
        when(users.findByEmail("superadmin@example.com")).thenReturn(Optional.empty());
        bootstrap(new AppProperties.Bootstrap("  SUPERADMIN@Example.com ", "StrongPass123!", "Root"))
                .run(null);

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> role = ArgumentCaptor.forClass(String.class);
        verify(users).insert(email.capture(), name.capture(), Mockito.eq("hash"),
                Mockito.eq(UserStatus.ACTIVE), role.capture());
        assertEquals("superadmin@example.com", email.getValue());
        assertEquals("Root", name.getValue());
        assertEquals(Roles.SUPER_ADMIN_PLATFORM, role.getValue());
    }

    @Test
    void doesNothingWhenPlatformAdminAlreadyExists() {
        when(users.findByEmail("superadmin@example.com"))
                .thenReturn(Optional.of(user(Roles.SUPER_ADMIN_PLATFORM)));

        bootstrap(new AppProperties.Bootstrap("superadmin@example.com", "StrongPass123!", null)).run(null);

        verify(users, never()).insert(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void refusesToElevateAnExistingOperationalUser() {
        when(users.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user(null)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> bootstrap(new AppProperties.Bootstrap("admin@example.com", "StrongPass123!", null)).run(null));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains(Roles.SUPER_ADMIN_PLATFORM));
        verify(users, never()).insert(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void rejectsIncompleteOrWeakBootstrapConfiguration() {
        assertThrows(IllegalStateException.class,
                () -> bootstrap(new AppProperties.Bootstrap("superadmin@example.com", "", null)).run(null));
        assertThrows(IllegalStateException.class,
                () -> bootstrap(new AppProperties.Bootstrap("superadmin@example.com", "short", null)).run(null));
        assertThrows(IllegalStateException.class,
                () -> bootstrap(new AppProperties.Bootstrap("not-an-email", "StrongPass123!", null)).run(null));
    }

    private PlatformAdminBootstrap bootstrap(AppProperties.Bootstrap bootstrap) {
        AppProperties props = new AppProperties("Vecindad", "CodeVam", "production", "https://app.example.com",
                null, null, null, null, bootstrap, null, null, null);
        return new PlatformAdminBootstrap(props, users, encoder);
    }

    private User user(String platformRole) {
        return new User(UUID.randomUUID(), "user@example.com", "Usuario", "hash", platformRole,
                UserStatus.ACTIVE, 0, null, null);
    }
}
