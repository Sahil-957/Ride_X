package com.ridex.dto;

import com.ridex.enums.AccountStatus;
import com.ridex.enums.ApprovalStatus;
import com.ridex.enums.DocumentType;
import com.ridex.enums.VehicleStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DriverDtos {

    private DriverDtos() {
    }

    public record ProfileRequest(
            @NotBlank(message = "Enter your licence number") @Size(max = 30) String licenseNumber,
            @NotNull(message = "Enter the licence expiry date") @Future(message = "Your licence has expired") LocalDate licenseExpiry) {
    }

    public record VehicleRequest(
            @NotBlank(message = "Enter the vehicle number") @Size(max = 20) String registrationNumber,
            @NotBlank(message = "Enter the make") @Size(max = 50) String make,
            @NotBlank(message = "Enter the model") @Size(max = 50) String model,
            @NotBlank(message = "Enter the colour") @Size(max = 30) String color,
            @NotNull(message = "Enter the year") @Min(value = 1990, message = "Year must be 1990 or later")
            @Max(value = 2100) Integer manufactureYear) {
    }

    public record AvailabilityRequest(@NotNull Boolean online) {
    }

    public record LocationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record VehicleView(Long id, String registrationNumber, String make, String model, String color,
                              int manufactureYear, VehicleStatus status) {
    }

    public record DocumentInfo(Long id, DocumentType docType, String fileName, String contentType, Instant uploadedAt) {
    }

    /** Full driver profile, shown to the driver and to admins reviewing them. */
    public record DriverProfileView(Long id, String fullName, String email, String phone, AccountStatus accountStatus,
                                    String licenseNumber, LocalDate licenseExpiry, ApprovalStatus approvalStatus,
                                    String rejectionReason, Instant approvedAt, boolean online, VehicleView vehicle,
                                    List<DocumentInfo> documents, Double rating, long completedRides) {
    }

    public record Summary(BigDecimal earning, long rides) {
    }

    public record Day(String date, BigDecimal earning, long rides) {
    }

    public record Earnings(Summary today, Summary week, Summary month, Double rating, long completedRides,
                           List<Day> daily) {
    }
}
