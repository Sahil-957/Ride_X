package com.cabzen.entity;

import com.cabzen.enums.BookingStatus;
import com.cabzen.enums.Role;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id")
    private User passenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Payment payment;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private Rating rating;

    private String pickupAddress;
    private double pickupLat;
    private double pickupLng;
    private String dropAddress;
    private double dropLat;
    private double dropLng;

    private BigDecimal distanceKm;
    private int durationMin;
    private BigDecimal fare;
    private String routePath; // JSON array of [lat, lng] points

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.REQUESTED;

    private String otp;
    private Instant requestedAt = Instant.now();
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    private Role cancelledBy;

    @Version
    private long version;

    protected Booking() {
    }

    public Booking(User passenger, String pickupAddress, double pickupLat, double pickupLng,
                   String dropAddress, double dropLat, double dropLng,
                   BigDecimal distanceKm, int durationMin, BigDecimal fare, String routePath, String otp) {
        this.passenger = passenger;
        this.pickupAddress = pickupAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropAddress = dropAddress;
        this.dropLat = dropLat;
        this.dropLng = dropLng;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.fare = fare;
        this.routePath = routePath;
        this.otp = otp;
    }

    public Long getId() { return id; }
    public User getPassenger() { return passenger; }
    public DriverProfile getDriver() { return driver; }
    public Payment getPayment() { return payment; }
    public Rating getRating() { return rating; }
    public String getPickupAddress() { return pickupAddress; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLng() { return pickupLng; }
    public String getDropAddress() { return dropAddress; }
    public double getDropLat() { return dropLat; }
    public double getDropLng() { return dropLng; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public int getDurationMin() { return durationMin; }
    public BigDecimal getFare() { return fare; }
    public String getRoutePath() { return routePath; }
    public BookingStatus getStatus() { return status; }
    public String getOtp() { return otp; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Role getCancelledBy() { return cancelledBy; }

    /** Keeps the in-memory inverse side in sync after saving a new payment. */
    public void setPayment(Payment payment) { this.payment = payment; }
    public void setDriver(DriverProfile driver) { this.driver = driver; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setCancelledBy(Role cancelledBy) { this.cancelledBy = cancelledBy; }
}
