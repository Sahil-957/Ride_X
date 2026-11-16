package com.ridex.security;

import com.ridex.enums.AccountStatus;
import com.ridex.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public AppUser loadUserByUsername(String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(u -> new AppUser(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole(),
                        u.getStatus() == AccountStatus.ACTIVE))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown email"));
    }
}
