package com.ridex.service;

import com.ridex.config.AppProperties;
import com.ridex.dto.DriverDtos.*;
import com.ridex.entity.*;
import com.ridex.enums.*;
import com.ridex.exception.ApiException;
import com.ridex.repository.*;
import com.ridex.repository.PaymentRepository.DailyTotal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Driver onboarding (profile, vehicle, documents), availability, location and earnings. */
@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);
    private static final Pattern VEHICLE_NUMBER = Pattern.compile("[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{1,4}");
    // The app sends GPS every 15 s; background tabs slow that to about once a minute, so 10 min means "gone".
    private static final Duration SILENT_OFFLINE_AFTER = Duration.ofMinutes(10);

    private final DriverProfileRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverLocationRepository locations;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RatingRepository ratings;
    private final AppProperties properties;

    public DriverService(DriverProfileRepository drivers, VehicleRepository vehicles, DriverDocumentRepository documents,
                         DriverLocationRepository locations, BookingRepository bookings, PaymentRepository payments,
                         RatingRepository ratings, AppProperties properties) {
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.documents = documents;
        this.locations = locations;
        this.bookings = bookings;
        this.payments = payments;
        this.ratings = ratings;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DriverProfileView profile(Long driverId) {
        return view(load(driverId));
    }

    @Transactional
    public DriverProfileView updateProfile(Long driverId, ProfileRequest request) {
        DriverProfile driver = editable(driverId);
        String license = request.licenseNumber().trim().toUpperCase(Locale.ROOT);
        if (!license.equals(driver.getLicenseNumber()) && drivers.existsByLicenseNumber(license)) {
            throw ApiException.conflict("This licence number is already registered.");
        }
        driver.setLicenseNumber(license);
        driver.setLicenseExpiry(request.licenseExpiry());
        return view(driver);
    }

    @Transactional
    public DriverProfileView saveVehicle(Long driverId, VehicleRequest request) {
        DriverProfile driver = editable(driverId);
        String number = request.registrationNumber().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (!VEHICLE_NUMBER.matcher(number).matches()) {
            throw ApiException.badRequest("Enter a valid vehicle number, for example MH12AB1234.");
        }
        if (request.manufactureYear() > Year.now().getValue() + 1) {
            throw ApiException.badRequest("Enter a valid manufacture year.");
        }
        Vehicle vehicle = driver.getVehicle() != null ? driver.getVehicle() : new Vehicle(driver);
        if (!number.equals(vehicle.getRegistrationNumber()) && vehicles.existsByRegistrationNumber(number)) {
            throw ApiException.conflict("This vehicle number is already registered.");
        }
        vehicle.setRegistrationNumber(number);
        vehicle.setMake(request.make().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setColor(request.color().trim());
        vehicle.setManufactureYear(request.manufactureYear());
        driver.setVehicle(vehicles.save(vehicle));
        return view(driver);
    }

    @Transactional
    public DocumentInfo upload(Long driverId, DocumentType type, MultipartFile file) {
        DriverProfile driver = editable(driverId);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a file to upload.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read. Please try again.");
        }
        String contentType = detectType(data);
        if (contentType == null) {
            throw ApiException.badRequest("Upload a PDF, JPG or PNG file.");
        }
        String name = file.getOriginalFilename() == null ? type.name().toLowerCase(Locale.ROOT)
                : file.getOriginalFilename().replaceAll(".*[/\\\\]", "");
        DriverDocument document = documents.findByDriver_UserIdAndDocType(driverId, type)
                .orElseGet(() -> new DriverDocument(driver, type));
        document.replaceFile(name.length() > 255 ? name.substring(0, 255) : name, contentType, data);
        documents.save(document);
        return new DocumentInfo(document.getId(), type, document.getFileName(), contentType, document.getUploadedAt());
    }

    @Transactional(readOnly = true)
    public List<DocumentInfo> documents(Long driverId) {
        return documents.findByDriver_UserIdOrderByDocType(driverId).stream()
                .map(d -> new DocumentInfo(d.getId(), d.getDocType(), d.getFileName(), d.getContentType(), d.getUploadedAt()))
                .toList();
    }

    @Transactional
    public DriverProfileView setOnline(Long driverId, boolean online) {
        DriverProfile driver = load(driverId);
        if (online) {
            if (driver.getApprovalStatus() != ApprovalStatus.APPROVED) {
                throw ApiException.conflict("Your account must be approved before you can go online.");
            }
            if (driver.getUser().getStatus() != AccountStatus.ACTIVE) {
                throw ApiException.conflict("Your account is blocked. Contact support.");
            }
            if (driver.getVehicle() == null || driver.getVehicle().getStatus() != VehicleStatus.ACTIVE) {
                throw ApiException.conflict("Your vehicle is inactive. Contact support.");
            }
        }
        driver.setOnline(online);
        return view(driver);
    }

    @Transactional
    public void updateLocation(Long driverId, double lat, double lng) {
        if (!drivers.existsById(driverId)) {
            throw ApiException.notFound("Driver");
        }
        locations.save(new DriverLocation(driverId, lat, lng));
    }

    /** Captains who closed the app without going offline stop sending GPS: take them offline. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void takeSilentCaptainsOffline() {
        int count = drivers.goOfflineIfSilentSince(Instant.now().minus(SILENT_OFFLINE_AFTER));
        if (count > 0) {
            log.info("Took {} silent captain(s) offline", count);
        }
    }

    @Transactional(readOnly = true)
    public Earnings earnings(Long driverId) {
        ZoneId zone = ZoneId.of(properties.timeZone());
        LocalDate today = LocalDate.now(zone);
        Map<String, DailyTotal> byDay = Reports.byDay(payments.dailyTotals(
                today.minusDays(29).atStartOfDay(zone).toInstant(), properties.timeZone(), driverId));
        List<Day> daily = Reports.lastDays(today, 7).stream().map(date -> {
            DailyTotal row = byDay.get(date.toString());
            return new Day(date.toString(), row == null ? BigDecimal.ZERO : row.getEarning(), row == null ? 0 : row.getRides());
        }).toList();
        return new Earnings(sum(byDay, today, 1), sum(byDay, today, 7), sum(byDay, today, 30),
                ratings.averageForDriver(driverId),
                bookings.countByDriver_UserIdAndStatus(driverId, BookingStatus.COMPLETED), daily);
    }

    /** Shared with the admin "review driver" screen. Call inside a transaction. */
    DriverProfileView view(DriverProfile driver) {
        User user = driver.getUser();
        Vehicle vehicle = driver.getVehicle();
        Long id = driver.getUserId();
        return new DriverProfileView(id, user.getFullName(), user.getEmail(), user.getPhone(), user.getStatus(),
                driver.getLicenseNumber(), driver.getLicenseExpiry(), driver.getApprovalStatus(),
                driver.getRejectionReason(), driver.getApprovedAt(), driver.isOnline(),
                vehicle == null ? null : new VehicleView(vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getMake(),
                        vehicle.getModel(), vehicle.getColor(), vehicle.getManufactureYear(), vehicle.getStatus()),
                documents(id), ratings.averageForDriver(id),
                bookings.countByDriver_UserIdAndStatus(id, BookingStatus.COMPLETED));
    }

    private DriverProfile load(Long driverId) {
        return drivers.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver"));
    }

    /** Approved details are locked; editing a rejected profile re-submits it for review. */
    private DriverProfile editable(Long driverId) {
        DriverProfile driver = load(driverId);
        if (driver.getApprovalStatus() == ApprovalStatus.APPROVED) {
            throw ApiException.conflict("Your profile is approved. Contact the admin to change your details.");
        }
        if (driver.getApprovalStatus() == ApprovalStatus.REJECTED) {
            driver.setApprovalStatus(ApprovalStatus.PENDING);
            driver.setRejectionReason(null);
        }
        return driver;
    }

    private static Summary sum(Map<String, DailyTotal> byDay, LocalDate today, int days) {
        BigDecimal earning = BigDecimal.ZERO;
        long rides = 0;
        for (LocalDate date : Reports.lastDays(today, days)) {
            DailyTotal row = byDay.get(date.toString());
            if (row != null) {
                earning = earning.add(row.getEarning());
                rides += row.getRides();
            }
        }
        return new Summary(earning, rides);
    }

    /** Trust the file's first bytes, not the browser's claimed content type. */
    static String detectType(byte[] data) {
        if (data.length > 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F') {
            return "application/pdf";
        }
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }
}
