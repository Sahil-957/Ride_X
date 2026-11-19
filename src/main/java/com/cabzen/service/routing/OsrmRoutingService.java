package com.cabzen.service.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.cabzen.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/** Road routes from an OSRM server; falls back to a straight-line estimate so booking never breaks. */
@Service
public class OsrmRoutingService implements RoutingService {

    private static final Logger log = LoggerFactory.getLogger(OsrmRoutingService.class);
    private static final double ROAD_FACTOR = 1.3;        // roads are ~30% longer than a straight line in cities
    private static final double CITY_SPEED_KMH = 22;      // typical city bike-taxi speed in traffic

    private final RestClient http;

    public OsrmRoutingService(AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .baseUrl(properties.routing().osrmUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.routing().userAgent())
                .build();
    }

    @Override
    public Route route(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            OsrmResponse response = http.get()
                    .uri("/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=simplified&geometries=geojson",
                            fromLng, fromLat, toLng, toLat)
                    .retrieve()
                    .body(OsrmResponse.class);
            if (response != null && "Ok".equals(response.code()) && response.routes() != null && !response.routes().isEmpty()) {
                OsrmRoute best = response.routes().getFirst();
                // OSRM returns [lng, lat]; Leaflet wants [lat, lng]
                String path = best.geometry().coordinates().stream()
                        .map(c -> "[" + c.get(1) + "," + c.get(0) + "]")
                        .collect(Collectors.joining(",", "[", "]"));
                double km = best.distance() / 1000;
                // Public OSRM times free-flowing cars (4 km in 6 min across Mumbai); never quote faster than city speed
                int minutes = Math.max((int) Math.ceil(best.duration() / 60), minutesAtCitySpeed(km));
                return new Route(km, minutes, path, false);
            }
            log.warn("OSRM found no route ({}), using straight-line estimate", response == null ? "empty" : response.code());
        } catch (RestClientException e) {
            log.warn("OSRM unavailable, using straight-line estimate: {}", e.getMessage());
        }
        double km = RoutingService.haversineKm(fromLat, fromLng, toLat, toLng) * ROAD_FACTOR;
        String straight = "[[%s,%s],[%s,%s]]".formatted(fromLat, fromLng, toLat, toLng);
        return new Route(km, minutesAtCitySpeed(km), straight, true);
    }

    private static int minutesAtCitySpeed(double km) {
        return (int) Math.ceil(km / CITY_SPEED_KMH * 60);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmRoute(double distance, double duration, Geometry geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometry(List<List<Double>> coordinates) {
    }
}
