package com.cabzen.repository;

import com.cabzen.entity.DriverLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {
}
