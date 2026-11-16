package com.ridex.entity;

import com.ridex.enums.VehicleStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    private String registrationNumber;
    private String make;
    private String model;
    private String color;
    private int manufactureYear;

    @Enumerated(EnumType.STRING)
    private VehicleStatus status = VehicleStatus.ACTIVE;

    protected Vehicle() {
    }

    public Vehicle(DriverProfile driver) {
        this.driver = driver;
    }

    public Long getId() { return id; }
    public DriverProfile getDriver() { return driver; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public String getColor() { return color; }
    public int getManufactureYear() { return manufactureYear; }
    public VehicleStatus getStatus() { return status; }

    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public void setMake(String make) { this.make = make; }
    public void setModel(String model) { this.model = model; }
    public void setColor(String color) { this.color = color; }
    public void setManufactureYear(int manufactureYear) { this.manufactureYear = manufactureYear; }
    public void setStatus(VehicleStatus status) { this.status = status; }
}
