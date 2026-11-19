package com.cabzen.dto;

import com.cabzen.dto.BookingDtos.BookingView;
import com.cabzen.enums.AccountStatus;
import com.cabzen.enums.ApprovalStatus;
import com.cabzen.enums.BookingStatus;
import com.cabzen.enums.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record Stats(long passengers, long drivers, long approvedDrivers, long pendingDrivers, long onlineDrivers,
                        long activeRides, long openRequests, long ridesToday, BigDecimal revenueToday,
                        BigDecimal commissionToday) {
    }

    public record DayRow(String date, long rides, BigDecimal revenue, BigDecimal commission) {
    }

    public record Analytics(List<DayRow> daily, Map<BookingStatus, Long> statusCounts, long rides,
                            BigDecimal revenue, BigDecimal commission) {
    }

    public record PassengerRow(Long id, String fullName, String email, String phone, AccountStatus status,
                               Instant createdAt, long rides) {
    }

    public record DriverRow(Long id, String fullName, String email, String phone, AccountStatus accountStatus,
                            String licenseNumber, ApprovalStatus approvalStatus, boolean online, String vehicleNumber,
                            Double rating, long completedRides, Instant createdAt) {
    }

    public record VehicleRow(Long id, String registrationNumber, String make, String model, String color,
                             int manufactureYear, VehicleStatus status, Long driverId, String driverName) {
    }

    public record UserStatusRequest(@NotNull AccountStatus status) {
    }

    public record VehicleStatusRequest(@NotNull VehicleStatus status) {
    }

    public record RejectRequest(@NotBlank(message = "Give a reason the driver can act on") @Size(max = 255) String reason) {
    }

    public record DriverPin(Long id, String name, double lat, double lng, boolean busy) {
    }

    public record LiveMap(List<BookingView> rides, List<DriverPin> drivers) {
    }
}
