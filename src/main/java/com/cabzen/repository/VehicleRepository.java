package com.cabzen.repository;

import com.cabzen.entity.Vehicle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByDriver_UserId(Long driverId);

    boolean existsByRegistrationNumber(String registrationNumber);

    @EntityGraph(attributePaths = {"driver", "driver.user"})
    List<Vehicle> findAllByOrderByIdDesc();
}
