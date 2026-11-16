package com.ridex.entity;

import com.ridex.enums.ApprovalStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Driver-only data. Shares its primary key with users.id, so a driver has one id everywhere. */
@Entity
@Table(name = "driver_profiles")
public class DriverProfile {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne(mappedBy = "driver", fetch = FetchType.LAZY)
    private Vehicle vehicle;

    private String licenseNumber;
    private LocalDate licenseExpiry;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    private String rejectionReason;
    private boolean online;
    private Instant approvedAt;

    protected DriverProfile() {
    }

    public DriverProfile(User user, String licenseNumber) {
        this.user = user;
        this.licenseNumber = licenseNumber;
    }

    public Long getUserId() { return userId; }
    public User getUser() { return user; }
    public Vehicle getVehicle() { return vehicle; }
    public String getLicenseNumber() { return licenseNumber; }
    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isOnline() { return online; }
    public Instant getApprovedAt() { return approvedAt; }

    /** Keeps the in-memory inverse side in sync after adding a vehicle. */
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public void setOnline(boolean online) { this.online = online; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
}
