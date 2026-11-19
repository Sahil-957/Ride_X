package com.cabzen.config;

import com.cabzen.entity.User;
import com.cabzen.enums.Role;
import com.cabzen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * There is no admin sign-up: the admin is created at startup from ADMIN_EMAIL / ADMIN_PASSWORD, and its password
 * follows ADMIN_PASSWORD on every start, so the configured credentials always work (rotate = change it + restart).
 */
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
        if (admin.email() == null || admin.email().isBlank() || admin.password() == null || admin.password().length() < 8) {
            if (!users.existsByRole(Role.ADMIN)) {
                log.warn("No admin account yet. Set ADMIN_EMAIL and ADMIN_PASSWORD (8+ characters) to create one.");
            }
            return;
        }
        String email = admin.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                log.warn("ADMIN_EMAIL {} belongs to a {} account; no admin was created.", email, user.getRole());
            } else if (!passwordEncoder.matches(admin.password(), user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(admin.password()));
                users.save(user);
                log.info("Admin password for {} updated from ADMIN_PASSWORD", email);
            }
            return;
        }
        if (users.existsByRole(Role.ADMIN)) {
            log.warn("ADMIN_EMAIL {} is not the existing admin's email; keeping the existing admin.", email);
            return;
        }
        // Admins never receive rides, so the phone is a placeholder that satisfies the unique column.
        users.save(new User("CabZen Admin", email, "0000000000", passwordEncoder.encode(admin.password()), Role.ADMIN));
        log.info("Created admin account {}", email);
    }
}
