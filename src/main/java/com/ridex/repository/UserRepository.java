package com.ridex.repository;

import com.ridex.entity.User;
import com.ridex.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    List<User> findByRoleOrderByCreatedAtDesc(Role role);
}
