package com.ridex.service;

import com.ridex.config.AppProperties;
import com.ridex.dto.AdminDtos.*;
import com.ridex.dto.BookingDtos.BookingView;
import com.ridex.dto.DriverDtos.DriverProfileView;
import com.ridex.entity.*;
import com.ridex.enums.*;
import com.ridex.exception.ApiException;
import com.ridex.repository.*;
import com.ridex.repository.BookingRepository.IdCount;
import com.ridex.repository.PaymentRepository.DailyTotal;
import com.ridex.repository.RatingRepository.DriverAverage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin dashboard data, user/driver/vehicle management and ride monitoring. */
@Service
public class AdminService {

    private final UserRepository users;
    private final DriverProfileRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverLocationRepository locations;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RatingRepository ratings;
    private final DriverService driverService;
    private final BookingMapper mapper;
    private final AppProperties properties;

    public AdminService(UserRepository users, DriverProfileRepository drivers, VehicleRepository vehicles,
                        DriverDocumentRepository documents, DriverLocationRepository locations,
                        BookingRepository bookings, PaymentRepository payments, RatingRepository ratings,
                        DriverService driverService, BookingMapper mapper, AppProperties properties) {
        this.users = users;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.documents = documents;
        this.locations = locations;
        this.bookings = bookings;
        this.payments = payments;
        this.ratings = ratings;
        this.driverService = driverService;
        this.mapper = mapper;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- dashboard

    @Transactional(readOnly = true)
    public Stats stats() {
        ZoneId zone = ZoneId.of(properties.timeZone());
        List<DailyTotal> todayRows = payments.dailyTotals(
                LocalDate.now(zone).atStartOfDay(zone).toInstant(), properties.timeZone(), null);
        DailyTotal today = todayRows.isEmpty() ? null : todayRows.getFirst();
        return new Stats(users.countByRole(Role.PASSENGER), users.countByRole(Role.DRIVER),
                drivers.countByApprovalStatus(ApprovalStatus.APPROVED), drivers.countByApprovalStatus(ApprovalStatus.PENDING),
                drivers.countByOnlineTrue(), bookings.countByStatusIn(BookingService.ACTIVE),
                bookings.countByStatusIn(Set.of(BookingStatus.REQUESTED)),
                today == null ? 0 : today.getRides(),
                today == null ? BigDecimal.ZERO : today.getAmount(),
                today == null ? BigDecimal.ZERO : today.getCommission());
    }

    @Transactional(readOnly = true)
    public Analytics analytics(int days) {
        int window = Math.clamp(days, 1, 90);
        ZoneId zone = ZoneId.of(properties.timeZone());
        LocalDate today = LocalDate.now(zone);
        Map<String, DailyTotal> byDay = Reports.byDay(payments.dailyTotals(
                today.minusDays(window - 1L).atStartOfDay(zone).toInstant(), properties.timeZone(), null));
        List<DayRow> daily = Reports.lastDays(today, window).stream().map(date -> {
            DailyTotal row = byDay.get(date.toString());
            return row == null ? new DayRow(date.toString(), 0, BigDecimal.ZERO, BigDecimal.ZERO)
                    : new DayRow(date.toString(), row.getRides(), row.getAmount(), row.getCommission());
        }).toList();
        Map<BookingStatus, Long> statusCounts = new EnumMap<>(BookingStatus.class);
        Arrays.stream(BookingStatus.values()).forEach(s -> statusCounts.put(s, 0L));
        bookings.countPerStatus().forEach(c -> statusCounts.put(c.getStatus(), c.getTotal()));
        return new Analytics(daily, statusCounts,
                daily.stream().mapToLong(DayRow::rides).sum(),
                daily.stream().map(DayRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                daily.stream().map(DayRow::commission).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ---------------------------------------------------------------- passengers

    @Transactional(readOnly = true)
    public List<PassengerRow> passengers() {
        Map<Long, Long> rides = bookings.countPerPassenger().stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getTotal));
        return users.findByRoleOrderByCreatedAtDesc(Role.PASSENGER).stream()
                .map(u -> new PassengerRow(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(),
                        u.getCreatedAt(), rides.getOrDefault(u.getId(), 0L)))
                .toList();
    }

    @Transactional
    public void setUserStatus(Long userId, AccountStatus status) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.conflict("Admin accounts cannot be blocked.");
        }
        user.setStatus(status);
        if (status == AccountStatus.BLOCKED && user.getRole() == Role.DRIVER) {
            drivers.findById(userId).ifPresent(d -> d.setOnline(false));
        }
    }

    // ---------------------------------------------------------------- drivers

    @Transactional(readOnly = true)
    public List<DriverRow> drivers(ApprovalStatus filter) {
        Map<Long, Double> rating = ratings.averagePerDriver().stream()
                .collect(Collectors.toMap(DriverAverage::getDriverId, DriverAverage::getAverage));
        Map<Long, Long> completed = bookings.countPerDriver(BookingStatus.COMPLETED).stream()
                .collect(Collectors.toMap(IdCount::getId, IdCount::getTotal));
        List<DriverProfile> list = filter == null ? drivers.findAllByOrderByUserIdDesc()
                : drivers.findByApprovalStatusOrderByUserIdDesc(filter);
        return list.stream().map(d -> {
            User u = d.getUser();
            return new DriverRow(d.getUserId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(),
                    d.getLicenseNumber(), d.getApprovalStatus(), d.isOnline(),
                    d.getVehicle() == null ? null : d.getVehicle().getRegistrationNumber(),
                    rating.get(d.getUserId()), completed.getOrDefault(d.getUserId(), 0L), u.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public DriverProfileView driver(Long driverId) {
        return driverService.profile(driverId);
    }

    @Transactional(readOnly = true)
    public DriverDocument document(Long documentId) {
        return documents.findById(documentId).orElseThrow(() -> ApiException.notFound("Document"));
    }

    @Transactional
    public DriverProfileView approve(Long driverId) {
        DriverProfile driver = loadDriver(driverId);
        if (driver.getLicenseExpiry() == null) {
            throw ApiException.conflict("The driver has not completed their licence details.");
        }
        if (driver.getVehicle() == null) {
            throw ApiException.conflict("The driver has not added a vehicle.");
        }
        if (documents.countByDriver_UserId(driverId) < DocumentType.values().length) {
            throw ApiException.conflict("The driver has not uploaded all documents (licence, RC and ID proof).");
        }
        driver.setApprovalStatus(ApprovalStatus.APPROVED);
        driver.setApprovedAt(Instant.now());
        driver.setRejectionReason(null);
        return driverService.view(driver);
    }

    @Transactional
    public DriverProfileView reject(Long driverId, String reason) {
        DriverProfile driver = loadDriver(driverId);
        driver.setApprovalStatus(ApprovalStatus.REJECTED);
        driver.setRejectionReason(reason.trim());
        driver.setApprovedAt(null);
        driver.setOnline(false);
        return driverService.view(driver);
    }

    // ---------------------------------------------------------------- vehicles

    @Transactional(readOnly = true)
    public List<VehicleRow> vehicles() {
        return vehicles.findAllByOrderByIdDesc().stream()
                .map(v -> new VehicleRow(v.getId(), v.getRegistrationNumber(), v.getMake(), v.getModel(), v.getColor(),
                        v.getManufactureYear(), v.getStatus(), v.getDriver().getUserId(),
                        v.getDriver().getUser().getFullName()))
                .toList();
    }

    @Transactional
    public void setVehicleStatus(Long vehicleId, VehicleStatus status) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> ApiException.notFound("Vehicle"));
        vehicle.setStatus(status);
        if (status == VehicleStatus.INACTIVE) {
            vehicle.getDriver().setOnline(false);
        }
    }

    // ---------------------------------------------------------------- bookings

    @Transactional(readOnly = true)
    public List<BookingView> bookings(BookingStatus status) {
        List<Booking> list = status == null ? bookings.findTop200ByOrderByRequestedAtDesc()
                : bookings.findTop200ByStatusOrderByRequestedAtDesc(status);
        return list.stream().map(b -> mapper.row(b, false)).toList();
    }

    /** Open/active rides with routes, plus every online driver's last position. */
    @Transactional(readOnly = true)
    public LiveMap live() {
        List<Booking> rides = bookings.findByStatusInOrderByRequestedAtDesc(BookingService.OPEN);
        List<DriverProfile> online = drivers.findByOnlineTrue();
        Set<Long> driverIds = new HashSet<>();
        online.forEach(d -> driverIds.add(d.getUserId()));
        rides.stream().filter(b -> b.getDriver() != null).forEach(b -> driverIds.add(b.getDriver().getUserId()));
        Map<Long, DriverLocation> where = locations.findAllById(driverIds).stream()
                .collect(Collectors.toMap(DriverLocation::getDriverId, Function.identity()));
        Set<Long> busy = rides.stream().filter(b -> b.getDriver() != null && BookingService.ACTIVE.contains(b.getStatus()))
                .map(b -> b.getDriver().getUserId()).collect(Collectors.toSet());

        List<BookingView> rideViews = rides.stream()
                .map(b -> mapper.live(b, b.getDriver() == null ? null : where.get(b.getDriver().getUserId())))
                .toList();
        List<DriverPin> pins = online.stream()
                .filter(d -> where.containsKey(d.getUserId()))
                .map(d -> new DriverPin(d.getUserId(), d.getUser().getFullName(),
                        where.get(d.getUserId()).getLatitude(), where.get(d.getUserId()).getLongitude(),
                        busy.contains(d.getUserId())))
                .toList();
        return new LiveMap(rideViews, pins);
    }

    private DriverProfile loadDriver(Long driverId) {
        return drivers.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver"));
    }
}
