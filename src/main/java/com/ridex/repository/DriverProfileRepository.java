package com.ridex.repository;

import com.ridex.entity.DriverProfile;
import com.ridex.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Long> {

    @EntityGraph(attributePaths = {"user", "vehicle"})
    List<DriverProfile> findAllByOrderByUserIdDesc();

    @EntityGraph(attributePaths = {"user", "vehicle"})
    List<DriverProfile> findByApprovalStatusOrderByUserIdDesc(ApprovalStatus status);

    long countByApprovalStatus(ApprovalStatus status);

    long countByOnlineTrue();

    boolean existsByLicenseNumber(String licenseNumber);

    /** Drivers who could take a ride starting at (lat, lng) right now. */
    @Query(value = """
            select count(*) from driver_profiles d
            join users u on u.id = d.user_id and u.status = 'ACTIVE'
            join vehicles v on v.driver_id = d.user_id and v.status = 'ACTIVE'
            join driver_locations l on l.driver_id = d.user_id
            where d.online and d.approval_status = 'APPROVED'
              and l.updated_at > :freshSince
              and distance_km(l.latitude, l.longitude, :lat, :lng) <= :radiusKm
              and not exists (select 1 from bookings b
                              where b.driver_id = d.user_id and b.status in ('ACCEPTED', 'STARTED'))
            """, nativeQuery = true)
    long countAvailableNear(double lat, double lng, double radiusKm, Instant freshSince);
}
