package com.cabzen.entity;

import com.cabzen.enums.PaymentMethod;
import com.cabzen.enums.PaymentStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Record of what the passenger owes / paid. No gateway: the driver confirms collection at ride completion. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private BigDecimal amount;
    private BigDecimal commission = BigDecimal.ZERO;
    private BigDecimal driverEarning = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private Instant paidAt;

    protected Payment() {
    }

    public Payment(Booking booking, BigDecimal amount, PaymentMethod method) {
        this.booking = booking;
        this.amount = amount;
        this.method = method;
    }

    public void markPaid(BigDecimal commission, BigDecimal driverEarning) {
        this.commission = commission;
        this.driverEarning = driverEarning;
        this.status = PaymentStatus.PAID;
        this.paidAt = Instant.now();
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCommission() { return commission; }
    public BigDecimal getDriverEarning() { return driverEarning; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }

    public void setStatus(PaymentStatus status) { this.status = status; }
}
