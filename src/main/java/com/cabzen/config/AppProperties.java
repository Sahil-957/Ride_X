package com.cabzen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** All CabZen tunables, bound from the `cabzen.*` section of application.yml. */
@ConfigurationProperties("cabzen")
public record AppProperties(String timeZone, Fare fare, Dispatch dispatch, Routing routing, Admin admin) {

    public record Fare(BigDecimal base, BigDecimal perKm, BigDecimal perMin, BigDecimal minimum, BigDecimal commissionRate) {
    }

    public record Dispatch(double radiusKm, int requestTimeoutMinutes, int locationStaleSeconds) {
    }

    public record Routing(String osrmUrl, String nominatimUrl, String userAgent, String countryCodes) {
    }

    public record Admin(String email, String password) {
    }
}
