package com.ridex.security;

import com.ridex.enums.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** The logged-in user stored in the session. Controllers get it via @AuthenticationPrincipal. */
public class AppUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    public AppUser(Long id, String email, String passwordHash, Role role, boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public Long id() { return id; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }

    /** Same user = same id, so the session registry groups all of a user's logins under one principal. */
    @Override
    public boolean equals(Object other) {
        return other instanceof AppUser user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Drop the hash after login so it never sits in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
