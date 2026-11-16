package com.ridex.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    private int stars;
    private String comment;
    private Instant createdAt = Instant.now();

    protected Rating() {
    }

    public Rating(Booking booking, DriverProfile driver, int stars, String comment) {
        this.booking = booking;
        this.driver = driver;
        this.stars = stars;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public DriverProfile getDriver() { return driver; }
    public int getStars() { return stars; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
