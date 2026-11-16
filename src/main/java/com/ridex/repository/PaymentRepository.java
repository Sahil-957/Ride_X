package com.ridex.repository;

import com.ridex.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Paid totals per local calendar day since `since`; driverId = null means all drivers. */
    @Query(value = """
            select to_char(p.paid_at at time zone :zone, 'YYYY-MM-DD') as day,
                   sum(p.amount) as amount, sum(p.commission) as commission,
                   sum(p.driver_earning) as earning, count(*) as rides
            from payments p join bookings b on b.id = p.booking_id
            where p.status = 'PAID' and p.paid_at >= :since
              and (cast(:driverId as bigint) is null or b.driver_id = :driverId)
            group by 1 order by 1""", nativeQuery = true)
    List<DailyTotal> dailyTotals(Instant since, String zone, Long driverId);

    interface DailyTotal {
        String getDay();
        BigDecimal getAmount();
        BigDecimal getCommission();
        BigDecimal getEarning();
        long getRides();
    }
}
