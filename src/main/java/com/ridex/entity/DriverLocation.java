package com.ridex.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Last known position of a driver (one row per driver, overwritten on every update). */
@Entity
@Table(name = "driver_locations")
public class DriverLocation {

    @Id
    private Long driverId;

    private double latitude;
    private double longitude;
    private Instant updatedAt;

    protected DriverLocation() {
    }

    public DriverLocation(Long driverId, double latitude, double longitude) {
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = Instant.now();
    }

    public Long getDriverId() { return driverId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getUpdatedAt() { return updatedAt; }
}
