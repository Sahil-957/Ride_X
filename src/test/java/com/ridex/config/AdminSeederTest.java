package com.ridex.config;

import com.ridex.entity.User;
import com.ridex.enums.Role;
import com.ridex.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** The admin account always matches ADMIN_EMAIL / ADMIN_PASSWORD after startup. */
class AdminSeederTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost keeps the test fast

    private AdminSeeder seeder(String email, String password) {
        return new AdminSeeder(users, encoder, new AppProperties(null, null, null, null, new AppProperties.Admin(email, password)));
    }

    private User admin(String password) {
        return new User("RideX Admin", "admin@ridex.local", "0000000000", encoder.encode(password), Role.ADMIN);
    }

    @Test
    void createsTheAdminWhenNoneExists() {
        when(users.findByEmail("admin@ridex.local")).thenReturn(Optional.empty());
        seeder(" Admin@RideX.local ", "new-password").run(null);
        verify(users).save(argThat(u -> u.getRole() == Role.ADMIN && u.getEmail().equals("admin@ridex.local")
                && encoder.matches("new-password", u.getPasswordHash())));
    }

    @Test
    void updatesAPasswordThatNoLongerMatchesTheConfig() {
        User admin = admin("old-password");
        when(users.findByEmail("admin@ridex.local")).thenReturn(Optional.of(admin));
        seeder("admin@ridex.local", "new-password").run(null);
        assertTrue(encoder.matches("new-password", admin.getPasswordHash()));
        verify(users).save(admin);
    }

    @Test
    void leavesAMatchingAdminAndOtherRolesAlone() {
        when(users.findByEmail("admin@ridex.local")).thenReturn(Optional.of(admin("same-password")));
        seeder("admin@ridex.local", "same-password").run(null);

        when(users.findByEmail("rider@ridex.local")).thenReturn(Optional.of(
                new User("Rider", "rider@ridex.local", "9000000000", encoder.encode("x"), Role.PASSENGER)));
        seeder("rider@ridex.local", "new-password").run(null);

        verify(users, never()).save(any());
    }
}
