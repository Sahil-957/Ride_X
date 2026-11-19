package com.cabzen.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.cabzen.enums.BookingStatus;
import com.cabzen.enums.PaymentMethod;
import com.cabzen.enums.PaymentStatus;
import com.cabzen.enums.Role;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public final class BookingDtos {

    private BookingDtos() {
    }

    /** Pickup + drop chosen on the map. Used for both the fare estimate and the actual booking. */
    public record TripRequest(
            @Size(max = 255) String pickupAddress,
            @NotNull(message = "Choose a pickup point") @DecimalMin("-90") @DecimalMax("90") Double pickupLat,
            @NotNull(message = "Choose a pickup point") @DecimalMin("-180") @DecimalMax("180") Double pickupLng,
            @Size(max = 255) String dropAddress,
            @NotNull(message = "Choose a drop point") @DecimalMin("-90") @DecimalMax("90") Double dropLat,
            @NotNull(message = "Choose a drop point") @DecimalMin("-180") @DecimalMax("180") Double dropLng,
            PaymentMethod paymentMethod) {
    }

    /** @param roadRoute false when routing fell back to a straight-line estimate */
    public record Estimate(BigDecimal distanceKm, int durationMin, BigDecimal fare, long driversNearby,
                           boolean roadRoute, @JsonRawValue String path) {
    }

    public record RatingRequest(
            @NotNull(message = "Choose 1 to 5 stars") @Min(1) @Max(5) Integer stars,
            @Size(max = 500) String comment) {
    }

    public record OtpRequest(@NotBlank @Pattern(regexp = "\\d{4}", message = "OTP is 4 digits") String otp) {
    }

    public record Point(String address, double lat, double lng) {
    }

    public record Person(Long id, String name, String phone) {
    }

    public record VehicleInfo(String registrationNumber, String make, String model, String color) {
    }

    /**
     * One shape for every screen that shows a ride. Fields a viewer must not see are null:
     * the OTP is only sent to the passenger, the driver's earning only to the driver/admin.
     */
    public record BookingView(
            Long id, BookingStatus status, Point pickup, Point drop,
            BigDecimal distanceKm, int durationMin, BigDecimal fare,
            PaymentMethod paymentMethod, PaymentStatus paymentStatus, BigDecimal commission, BigDecimal driverEarning,
            String otp, Person passenger, Person driver, VehicleInfo vehicle, Double driverRating,
            Double driverLat, Double driverLng, Integer stars, String ratingComment,
            Instant requestedAt, Instant acceptedAt, Instant startedAt, Instant completedAt, Instant cancelledAt,
            Role cancelledBy, @JsonRawValue String path) {
    }

    /** What a driver sees for an open request: no phone number until they accept. */
    public record RequestCard(Long id, Point pickup, Point drop, BigDecimal distanceKm, int durationMin,
                              BigDecimal fare, PaymentMethod paymentMethod, String passengerName,
                              double distanceToPickupKm, Instant requestedAt, @JsonRawValue String path) {
    }
}
