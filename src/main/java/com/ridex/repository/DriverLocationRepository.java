package com.ridex.repository;

import com.ridex.entity.DriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {
}
