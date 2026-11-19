package com.cabzen.repository;

import com.cabzen.entity.User;
import com.cabzen.enums.Role;
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
