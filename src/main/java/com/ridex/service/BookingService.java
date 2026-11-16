package com.ridex.service;

import com.ridex.config.AppProperties;
import com.ridex.dto.BookingDtos.*;
import com.ridex.entity.*;
import com.ridex.enums.*;
import com.ridex.exception.ApiException;
import com.ridex.repository.*;
import com.ridex.service.routing.RoutingService;
import com.ridex.service.routing.RoutingService.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.ridex.enums.BookingStatus.*;

/**
 * The ride lifecycle:
 * REQUESTED → (atomic accept) ACCEPTED → (OTP) STARTED → COMPLETED, plus CANCELLED and EXPIRED.
 * Every status change happens here, inside a transaction.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    public static final Set<BookingStatus> OPEN = EnumSet.of(REQUESTED, ACCEPTED, STARTED);
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(ACCEPTED, STARTED);

    private static final double MIN_TRIP_KM = 0.3;
    private static final double MAX_TRIP_KM = 50;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RatingRepository ratings;
    private final UserRepository users;
    private final DriverProfileRepository drivers;
    private final DriverLocationRepository locations;
    private final RoutingService routing;
    private final FareService fares;
    private final BookingMapper mapper;
    private final AppProperties.Dispatch dispatch;

    public BookingService(BookingRepository bookings, PaymentRepository payments, RatingRepository ratings,
                          UserRepository users, DriverProfileRepository drivers, DriverLocationRepository locations,
                          RoutingService routing, FareService fares, BookingMapper mapper, AppProperties properties) {
        this.bookings = bookings;
        this.payments = payments;
        this.ratings = ratings;
        this.users = users;
        this.drivers = drivers;
        this.locations = locations;
        this.routing = routing;
        this.fares = fares;
        this.mapper = mapper;
        this.dispatch = properties.dispatch();
    }

    // ---------------------------------------------------------------- passenger

    @Transactional(readOnly = true)
    public Estimate estimate(TripRequest trip) {
        Route route = routeFor(trip);
        return new Estimate(km(route), route.durationMin(), fares.fareFor(route.distanceKm(), route.durationMin()),
                driversNear(trip.pickupLat(), trip.pickupLng()), !route.estimated(), route.path());
    }

    @Transactional
    public BookingView create(Long passengerId, TripRequest trip) {
        User passenger = users.findById(passengerId).orElseThrow(() -> ApiException.notFound("User"));
        if (passenger.getStatus() != AccountStatus.ACTIVE) { // sessions outlive a block; check on every booking
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account has been blocked. Contact support.");
        }
        if (bookings.existsByPassenger_IdAndStatusIn(passengerId, OPEN)) {
            throw ApiException.conflict("You already have an open booking.");
        }
        Route route = routeFor(trip); // the fare is always recalculated here, never taken from the browser
        if (driversNear(trip.pickupLat(), trip.pickupLng()) == 0) {
            throw ApiException.conflict("No captains are available near your pickup right now. Please try again in a few minutes.");
        }
        BigDecimal fare = fares.fareFor(route.distanceKm(), route.durationMin());
        Booking booking = bookings.save(new Booking(passenger,
                address(trip.pickupAddress(), trip.pickupLat(), trip.pickupLng()), trip.pickupLat(), trip.pickupLng(),
                address(trip.dropAddress(), trip.dropLat(), trip.dropLng()), trip.dropLat(), trip.dropLng(),
                km(route), route.durationMin(), fare, route.path(), "%04d".formatted(RANDOM.nextInt(10_000))));
        PaymentMethod method = trip.paymentMethod() == null ? PaymentMethod.CASH : trip.paymentMethod();
        booking.setPayment(payments.save(new Payment(booking, fare, method)));
        log.info("Booking {} requested by passenger {}: {} km, fare {}", booking.getId(), passengerId, km(route), fare);
        return mapper.detail(booking, true);
    }

    @Transactional(readOnly = true)
    public Optional<BookingView> activeForPassenger(Long passengerId) {
        return bookings.findFirstByPassenger_IdAndStatusIn(passengerId, OPEN).map(b -> mapper.detail(b, true));
    }

    @Transactional(readOnly = true)
    public BookingView passengerBooking(Long passengerId, Long bookingId) {
        return mapper.detail(ownedByPassenger(passengerId, bookingId), true);
    }

    @Transactional(readOnly = true)
    public List<BookingView> passengerHistory(Long passengerId) {
        return bookings.findTop50ByPassenger_IdOrderByRequestedAtDesc(passengerId).stream()
                .map(b -> mapper.row(b, true)).toList();
    }

    @Transactional
    public BookingView cancelByPassenger(Long passengerId, Long bookingId) {
        Booking booking = ownedByPassenger(passengerId, bookingId);
        if (booking.getStatus() != REQUESTED && booking.getStatus() != ACCEPTED) {
            throw ApiException.conflict("This ride can no longer be cancelled.");
        }
        booking.setStatus(CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.setCancelledBy(Role.PASSENGER);
        if (booking.getPayment() != null) {
            booking.getPayment().setStatus(PaymentStatus.VOID);
        }
        return mapper.detail(booking, true);
    }

    @Transactional
    public void rate(Long passengerId, Long bookingId, RatingRequest request) {
        Booking booking = ownedByPassenger(passengerId, bookingId);
        if (booking.getStatus() != COMPLETED) {
            throw ApiException.conflict("You can rate a ride once it is completed.");
        }
        if (booking.getRating() != null) {
            throw ApiException.conflict("You have already rated this ride.");
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        ratings.save(new Rating(booking, booking.getDriver(), request.stars(), comment));
    }

    // ---------------------------------------------------------------- driver

    /** Requests a driver can see right now: only when online, approved, free, and recently located. */
    @Transactional(readOnly = true)
    public List<RequestCard> openRequests(Long driverId) {
        DriverProfile driver = drivers.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver"));
        if (!driver.isOnline() || driver.getApprovalStatus() != ApprovalStatus.APPROVED
                || bookings.existsByDriver_UserIdAndStatusIn(driverId, ACTIVE)) {
            return List.of();
        }
        DriverLocation here = locations.findById(driverId).orElse(null);
        if (here == null) {
            return List.of();
        }
        List<Long> ids = bookings.findOpenRequestIdsNear(driverId, here.getLatitude(), here.getLongitude(), dispatch.radiusKm());
        return bookings.findByIdIn(ids).stream()
                .map(b -> new RequestCard(b.getId(),
                        new Point(b.getPickupAddress(), b.getPickupLat(), b.getPickupLng()),
                        new Point(b.getDropAddress(), b.getDropLat(), b.getDropLng()),
                        b.getDistanceKm(), b.getDurationMin(), b.getFare(),
                        b.getPayment() == null ? null : b.getPayment().getMethod(),
                        b.getPassenger().getFullName().split(" ")[0],
                        round1(RoutingService.haversineKm(here.getLatitude(), here.getLongitude(), b.getPickupLat(), b.getPickupLng())),
                        b.getRequestedAt(), b.getRoutePath()))
                .sorted(Comparator.comparingDouble(RequestCard::distanceToPickupKm))
                .toList();
    }

    @Transactional
    public BookingView accept(Long driverId, Long bookingId) {
        DriverProfile driver = readyDriver(driverId);
        if (bookings.existsByDriver_UserIdAndStatusIn(driverId, ACTIVE)) {
            throw ApiException.conflict("Finish your current ride first.");
        }
        // Conditional UPDATE ... WHERE status = 'REQUESTED': two drivers can race, only one row update succeeds.
        if (bookings.accept(bookingId, driver, Instant.now(), REQUESTED, ACCEPTED) == 0) {
            throw ApiException.conflict("Sorry, this ride was already taken or cancelled.");
        }
        log.info("Booking {} accepted by driver {}", bookingId, driverId);
        return mapper.detail(ownedByDriver(driverId, bookingId), false);
    }

    @Transactional
    public void reject(Long driverId, Long bookingId) {
        if (!bookings.existsById(bookingId)) {
            throw ApiException.notFound("Ride");
        }
        bookings.reject(bookingId, driverId);
    }

    @Transactional(readOnly = true)
    public Optional<BookingView> activeForDriver(Long driverId) {
        return bookings.findFirstByDriver_UserIdAndStatusIn(driverId, ACTIVE).map(b -> mapper.detail(b, false));
    }

    @Transactional
    public BookingView start(Long driverId, Long bookingId, String otp) {
        Booking booking = ownedByDriver(driverId, bookingId);
        if (booking.getStatus() != ACCEPTED) {
            throw ApiException.conflict("Only an accepted ride can be started.");
        }
        if (!booking.getOtp().equals(otp)) {
            throw ApiException.badRequest("Wrong OTP. Ask the passenger for the 4-digit code shown in their app.");
        }
        booking.setStatus(STARTED);
        booking.setStartedAt(Instant.now());
        return mapper.detail(booking, false);
    }

    @Transactional
    public BookingView complete(Long driverId, Long bookingId) {
        Booking booking = ownedByDriver(driverId, bookingId);
        if (booking.getStatus() != STARTED) {
            throw ApiException.conflict("Only a ride in progress can be completed.");
        }
        booking.setStatus(COMPLETED);
        booking.setCompletedAt(Instant.now());
        Payment payment = booking.getPayment();
        BigDecimal commission = fares.commissionOn(payment.getAmount());
        payment.markPaid(commission, payment.getAmount().subtract(commission));
        log.info("Booking {} completed, paid {} ({} commission)", bookingId, payment.getAmount(), commission);
        return mapper.detail(booking, false);
    }

    /** Driver backs out before starting: the request goes back to other nearby drivers. */
    @Transactional
    public void releaseByDriver(Long driverId, Long bookingId) {
        Booking booking = ownedByDriver(driverId, bookingId);
        if (booking.getStatus() != ACCEPTED) {
            throw ApiException.conflict("You can only cancel before starting the ride.");
        }
        booking.setDriver(null);
        booking.setStatus(REQUESTED);
        booking.setAcceptedAt(null);
        booking.setRequestedAt(Instant.now()); // fresh window before it expires
        bookings.reject(bookingId, driverId);  // never offer it to this driver again
    }

    @Transactional(readOnly = true)
    public List<BookingView> driverHistory(Long driverId) {
        return bookings.findTop50ByDriver_UserIdOrderByRequestedAtDesc(driverId).stream()
                .map(b -> mapper.row(b, false)).toList();
    }

    // ---------------------------------------------------------------- background

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    @Transactional
    public void expireUnanswered() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(dispatch.requestTimeoutMinutes()));
        int expired = bookings.expireRequestedBefore(cutoff);
        if (expired > 0) {
            log.info("Expired {} unanswered ride request(s)", expired);
        }
    }

    // ---------------------------------------------------------------- helpers

    private Route routeFor(TripRequest trip) {
        Route route = routing.route(trip.pickupLat(), trip.pickupLng(), trip.dropLat(), trip.dropLng());
        if (route.distanceKm() < MIN_TRIP_KM) {
            throw ApiException.badRequest("Pickup and drop are too close. Choose a drop point further away.");
        }
        if (route.distanceKm() > MAX_TRIP_KM) {
            throw ApiException.badRequest("Bike taxi trips are limited to 50 km.");
        }
        return route;
    }

    private long driversNear(double lat, double lng) {
        return drivers.countAvailableNear(lat, lng, dispatch.radiusKm(),
                Instant.now().minusSeconds(dispatch.locationStaleSeconds()));
    }

    private DriverProfile readyDriver(Long driverId) {
        DriverProfile driver = drivers.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver"));
        if (driver.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.conflict("Your account is not approved yet.");
        }
        if (!driver.isOnline()) {
            throw ApiException.conflict("Go online to accept rides.");
        }
        if (driver.getUser().getStatus() != AccountStatus.ACTIVE
                || driver.getVehicle() == null || driver.getVehicle().getStatus() != VehicleStatus.ACTIVE) {
            throw ApiException.conflict("Your account or vehicle is inactive. Contact support.");
        }
        return driver;
    }

    private Booking ownedByPassenger(Long passengerId, Long bookingId) {
        return bookings.findWithDetailsById(bookingId)
                .filter(b -> b.getPassenger().getId().equals(passengerId))
                .orElseThrow(() -> ApiException.notFound("Ride"));
    }

    private Booking ownedByDriver(Long driverId, Long bookingId) {
        return bookings.findWithDetailsById(bookingId)
                .filter(b -> b.getDriver() != null && b.getDriver().getUserId().equals(driverId))
                .orElseThrow(() -> ApiException.notFound("Ride"));
    }

    private static BigDecimal km(Route route) {
        return BigDecimal.valueOf(route.distanceKm()).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static String address(String given, double lat, double lng) {
        String text = given == null || given.isBlank() ? "%.5f, %.5f".formatted(lat, lng) : given.trim();
        return text.length() > 255 ? text.substring(0, 255) : text;
    }
}
