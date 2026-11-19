package com.cabzen.service;

import com.cabzen.config.AppProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** fare = max(minimum, base + perKm × km + perMin × minutes), rounded to whole rupees. */
@Service
public class FareService {

    private final AppProperties.Fare fare;

    public FareService(AppProperties properties) {
        this.fare = properties.fare();
    }

    public BigDecimal fareFor(double distanceKm, int durationMin) {
        BigDecimal raw = fare.base()
                .add(fare.perKm().multiply(BigDecimal.valueOf(distanceKm)))
                .add(fare.perMin().multiply(BigDecimal.valueOf(durationMin)));
        return raw.max(fare.minimum()).setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Platform's share of a paid fare; the driver keeps the rest. */
    public BigDecimal commissionOn(BigDecimal amount) {
        return amount.multiply(fare.commissionRate()).setScale(2, RoundingMode.HALF_UP);
    }
}
