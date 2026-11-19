package com.cabzen;

import com.cabzen.dto.AuthDtos.RegisterRequest;
import com.cabzen.dto.BookingDtos.BookingView;
import com.cabzen.dto.BookingDtos.RatingRequest;
import com.cabzen.dto.BookingDtos.TripRequest;
import com.cabzen.dto.DriverDtos.ProfileRequest;
import com.cabzen.dto.DriverDtos.VehicleRequest;
import com.cabzen.enums.AccountStatus;
import com.cabzen.enums.BookingStatus;
import com.cabzen.enums.DocumentType;
import com.cabzen.enums.PaymentMethod;
import com.cabzen.enums.PaymentStatus;
import com.cabzen.exception.ApiException;
import com.cabzen.service.AdminService;
import com.cabzen.service.AuthService;
import com.cabzen.service.BookingService;
import com.cabzen.service.DriverService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole ride lifecycle against the real Neon database, including two drivers racing to accept
 * the same booking. Rows created here are deleted afterwards (only the ones this test made).
 */
@SpringBootTest
class RideFlowTest {

    // Mumbai: Dadar → Mahim, about 3 km
    static final double PICK_LAT = 19.0178, PICK_LNG = 72.8478, DROP_LAT = 19.0390, DROP_LNG = 72.8406;

    @Autowired AuthService auth;
    @Autowired DriverService driverService;
    @Autowired BookingService bookingService;
    @Autowired AdminService adminService;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUsers = new ArrayList<>();

    @Test
    void fullRideWhereTwoDriversRaceToAccept() throws Exception {
        long passenger = passenger();
        long driverA = approvedOnlineDriver();
        long driverB = approvedOnlineDriver();

        BookingView booking = bookingService.create(passenger, trip());
        assertEquals(BookingStatus.REQUESTED, booking.status());
        assertTrue(booking.otp().matches("\\d{4}"), "passenger sees a 4-digit OTP");
        assertTrue(booking.fare().compareTo(new BigDecimal("30")) >= 0, "at least the minimum fare");
        assertTrue(bookingService.openRequests(driverA).stream().anyMatch(r -> r.id().equals(booking.id())),
                "nearby driver sees the request");

        // Both drivers press "Accept" at the same moment: exactly one may win.
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> a = pool.submit(() -> tryAccept(go, driverA, booking.id()));
        Future<Boolean> b = pool.submit(() -> tryAccept(go, driverB, booking.id()));
        go.countDown();
        boolean aWon = a.get(30, TimeUnit.SECONDS);
        boolean bWon = b.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(aWon ^ bWon, "exactly one driver gets the ride (A=" + aWon + ", B=" + bWon + ")");
        long winner = aWon ? driverA : driverB;

        BookingView accepted = bookingService.passengerBooking(passenger, booking.id());
        assertEquals(BookingStatus.ACCEPTED, accepted.status());
        assertEquals(winner, accepted.driver().id());
        assertNotNull(accepted.vehicle(), "passenger sees the bike details");

        String wrongOtp = booking.otp().equals("0000") ? "1111" : "0000";
        assertThrows(ApiException.class, () -> bookingService.start(winner, booking.id(), wrongOtp));
        assertEquals(BookingStatus.STARTED, bookingService.start(winner, booking.id(), booking.otp()).status());

        BookingView done = bookingService.complete(winner, booking.id());
        assertEquals(BookingStatus.COMPLETED, done.status());
        assertEquals(PaymentStatus.PAID, done.paymentStatus());
        assertEquals(0, done.fare().multiply(new BigDecimal("0.20")).compareTo(done.commission()), "20% commission");
        assertEquals(0, done.fare().subtract(done.commission()).compareTo(done.driverEarning()), "driver keeps the rest");

        bookingService.rate(passenger, booking.id(), new RatingRequest(5, "Smooth ride"));
        assertThrows(ApiException.class, () -> bookingService.rate(passenger, booking.id(), new RatingRequest(4, null)));
        assertEquals(5, bookingService.passengerBooking(passenger, booking.id()).stars());
        assertTrue(driverService.earnings(winner).today().rides() >= 1);
    }

    @Test
    void cancelReleaseAndExpiry() {
        long passenger = passenger();
        long driver = approvedOnlineDriver();

        // A blocked passenger cannot book, even with a live session
        adminService.setUserStatus(passenger, AccountStatus.BLOCKED);
        assertThrows(ApiException.class, () -> bookingService.create(passenger, trip()));
        adminService.setUserStatus(passenger, AccountStatus.ACTIVE);

        // Passenger cancels while searching → payment voided
        BookingView first = bookingService.create(passenger, trip());
        BookingView cancelled = bookingService.cancelByPassenger(passenger, first.id());
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(PaymentStatus.VOID, cancelled.paymentStatus());

        // Driver accepts then backs out → back to REQUESTED, hidden from that driver
        BookingView second = bookingService.create(passenger, trip());
        bookingService.accept(driver, second.id());
        bookingService.releaseByDriver(driver, second.id());
        assertEquals(BookingStatus.REQUESTED, bookingService.passengerBooking(passenger, second.id()).status());
        assertTrue(bookingService.openRequests(driver).stream().noneMatch(r -> r.id().equals(second.id())));

        // Nobody answers for longer than the timeout → EXPIRED by the scheduled job
        jdbc.update("update bookings set requested_at = now() - interval '10 minutes' where id = ?", second.id());
        bookingService.expireUnanswered();
        BookingView expired = bookingService.passengerBooking(passenger, second.id());
        assertEquals(BookingStatus.EXPIRED, expired.status());
        assertEquals(PaymentStatus.VOID, expired.paymentStatus());

        // The captain closed the app without going offline: after 10 silent minutes the job takes them offline
        driverService.takeSilentCaptainsOffline();
        assertTrue(driverService.profile(driver).online(), "a captain with fresh GPS stays online");
        jdbc.update("update driver_locations set updated_at = now() - interval '11 minutes' where driver_id = ?", driver);
        driverService.takeSilentCaptainsOffline();
        assertFalse(driverService.profile(driver).online());
    }

    // ------------------------------------------------------------------ helpers

    private boolean tryAccept(CountDownLatch go, long driverId, long bookingId) throws InterruptedException {
        go.await();
        try {
            bookingService.accept(driverId, bookingId);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    private static TripRequest trip() {
        return new TripRequest("Dadar station", PICK_LAT, PICK_LNG, "Mahim church", DROP_LAT, DROP_LNG, PaymentMethod.UPI);
    }

    private long passenger() {
        long id = auth.registerPassenger(new RegisterRequest("Test Passenger", uniqueEmail(), uniquePhone(), "secret123", null)).id();
        createdUsers.add(id);
        return id;
    }

    private long approvedOnlineDriver() {
        long id = auth.registerDriver(new RegisterRequest("Test Captain", uniqueEmail(), uniquePhone(), "secret123",
                "TEST" + ThreadLocalRandom.current().nextLong(1_000_000_000L))).id();
        createdUsers.add(id);
        driverService.updateProfile(id, new ProfileRequest("TEST" + ThreadLocalRandom.current().nextLong(1_000_000_000L),
                LocalDate.now().plusYears(5)));
        driverService.saveVehicle(id, new VehicleRequest(
                "MH%02dZZ%04d".formatted(ThreadLocalRandom.current().nextInt(100), ThreadLocalRandom.current().nextInt(10_000)),
                "Honda", "Activa 6G", "Black", 2023));
        for (DocumentType type : DocumentType.values()) {
            driverService.upload(id, type, new MockMultipartFile("file", type + ".pdf", "application/pdf",
                    "%PDF-1.4 test document".getBytes()));
        }
        adminService.approve(id);
        driverService.setOnline(id, true);
        driverService.updateLocation(id, PICK_LAT + 0.002, PICK_LNG + 0.002); // ~300 m from pickup
        return id;
    }

    private static String uniqueEmail() {
        return "test-" + System.nanoTime() + "@test.example.com";
    }

    private static String uniquePhone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    @AfterEach
    void deleteTestData() {
        if (createdUsers.isEmpty()) {
            return;
        }
        String ids = createdUsers.stream().map(String::valueOf).collect(Collectors.joining(","));
        String testBookings = "(select id from bookings where passenger_id in (" + ids + ") or driver_id in (" + ids + "))";
        jdbc.update("delete from ratings where booking_id in " + testBookings);
        jdbc.update("delete from payments where booking_id in " + testBookings);
        jdbc.update("delete from booking_rejections where booking_id in " + testBookings + " or driver_id in (" + ids + ")");
        jdbc.update("delete from bookings where passenger_id in (" + ids + ") or driver_id in (" + ids + ")");
        for (String table : List.of("driver_locations", "driver_documents", "vehicles")) {
            jdbc.update("delete from " + table + " where driver_id in (" + ids + ")");
        }
        jdbc.update("delete from driver_profiles where user_id in (" + ids + ")");
        jdbc.update("delete from users where id in (" + ids + ")");
    }
}
