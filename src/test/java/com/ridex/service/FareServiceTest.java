package com.ridex.service;

import com.ridex.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FareServiceTest {

    // Same numbers as application.yml: base 20, 6/km, 0.5/min, minimum 30, 20% commission
    private final FareService fares = new FareService(new AppProperties("Asia/Kolkata",
            new AppProperties.Fare(new BigDecimal("20"), new BigDecimal("6"), new BigDecimal("0.5"),
                    new BigDecimal("30"), new BigDecimal("0.20")),
            null, null, null));

    @Test
    void shortTripsPayTheMinimumFare() {
        assertEquals(new BigDecimal("30.00"), fares.fareFor(0.8, 3)); // 20 + 4.8 + 1.5 = 26.3 → minimum 30
    }

    @Test
    void longerTripsAreRoundedToWholeRupees() {
        assertEquals(new BigDecimal("79.00"), fares.fareFor(7.4, 29)); // 20 + 44.4 + 14.5 = 78.9 → 79
        assertEquals(new BigDecimal("50.00"), fares.fareFor(4.25, 9)); // 20 + 25.5 + 4.5 = 50.0
    }

    @Test
    void commissionIsTwentyPercent() {
        assertEquals(new BigDecimal("15.80"), fares.commissionOn(new BigDecimal("79.00")));
    }
}
