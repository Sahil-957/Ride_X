package com.cabzen.service;

import com.cabzen.dto.BookingDtos.*;
import com.cabzen.entity.*;
import com.cabzen.repository.DriverLocationRepository;
import com.cabzen.repository.RatingRepository;
import org.springframework.stereotype.Component;

/** Builds BookingView. Must run inside a transaction (lazy relations). */
@Component
public class BookingMapper {

    private final RatingRepository ratings;
    private final DriverLocationRepository locations;

    public BookingMapper(RatingRepository ratings, DriverLocationRepository locations) {
        this.ratings = ratings;
        this.locations = locations;
    }

    /** One ride in full: route, driver's average rating and live position while active. */
    public BookingView detail(Booking b, boolean forPassenger) {
        DriverProfile driver = b.getDriver();
        Double rating = driver == null ? null : ratings.averageForDriver(driver.getUserId());
        DriverLocation location = driver != null && BookingService.ACTIVE.contains(b.getStatus())
                ? locations.findById(driver.getUserId()).orElse(null)
                : null;
        return build(b, forPassenger, rating, location, true);
    }

    /** List rows: no extra queries, no route. */
    public BookingView row(Booking b, boolean forPassenger) {
        return build(b, forPassenger, null, null, false);
    }

    /** Admin live map: route plus the driver's position. */
    public BookingView live(Booking b, DriverLocation location) {
        return build(b, false, null, location, true);
    }

    private BookingView build(Booking b, boolean forPassenger, Double driverRating, DriverLocation location, boolean withPath) {
        Payment payment = b.getPayment();
        DriverProfile driver = b.getDriver();
        Vehicle vehicle = driver == null ? null : driver.getVehicle();
        Rating rating = b.getRating();
        return new BookingView(
                b.getId(), b.getStatus(),
                new Point(b.getPickupAddress(), b.getPickupLat(), b.getPickupLng()),
                new Point(b.getDropAddress(), b.getDropLat(), b.getDropLng()),
                b.getDistanceKm(), b.getDurationMin(), b.getFare(),
                payment == null ? null : payment.getMethod(),
                payment == null ? null : payment.getStatus(),
                forPassenger || payment == null ? null : payment.getCommission(),
                forPassenger || payment == null ? null : payment.getDriverEarning(),
                forPassenger ? b.getOtp() : null,
                person(b.getPassenger()),
                driver == null ? null : person(driver.getUser()),
                vehicle == null ? null : new VehicleInfo(vehicle.getRegistrationNumber(), vehicle.getMake(),
                        vehicle.getModel(), vehicle.getColor()),
                driverRating,
                location == null ? null : location.getLatitude(),
                location == null ? null : location.getLongitude(),
                rating == null ? null : rating.getStars(),
                rating == null ? null : rating.getComment(),
                b.getRequestedAt(), b.getAcceptedAt(), b.getStartedAt(), b.getCompletedAt(), b.getCancelledAt(),
                b.getCancelledBy(),
                withPath ? b.getRoutePath() : null);
    }

    private static Person person(User user) {
        return new Person(user.getId(), user.getFullName(), user.getPhone());
    }
}
