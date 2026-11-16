package com.ridex.repository;

import com.ridex.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    @Query("select avg(r.stars) from Rating r where r.driver.userId = :driverId")
    Double averageForDriver(Long driverId);

    @Query("select r.driver.userId as driverId, avg(r.stars) as average from Rating r group by r.driver.userId")
    List<DriverAverage> averagePerDriver();

    interface DriverAverage {
        Long getDriverId();
        Double getAverage();
    }
}
