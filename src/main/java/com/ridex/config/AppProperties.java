package com.ridex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** All RideX tunables, bound from the `ridex.*` section of application.yml. */
@ConfigurationProperties("ridex")
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
