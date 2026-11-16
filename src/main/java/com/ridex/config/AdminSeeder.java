package com.ridex.config;

import com.ridex.entity.User;
import com.ridex.enums.Role;
import com.ridex.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** There is no admin sign-up: the first admin is created at startup from ADMIN_EMAIL / ADMIN_PASSWORD. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Admin admin;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.admin = properties.admin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (admin.email() == null || admin.email().isBlank() || admin.password() == null || admin.password().length() < 8) {
            log.warn("No admin account yet. Set ADMIN_EMAIL and ADMIN_PASSWORD (8+ characters) to create one.");
            return;
        }
        // Admins never receive rides, so the phone is a placeholder that satisfies the unique column.
        users.save(new User("RideX Admin", admin.email().trim().toLowerCase(Locale.ROOT), "0000000000",
                passwordEncoder.encode(admin.password()), Role.ADMIN));
        log.info("Created admin account {}", admin.email());
    }
}
