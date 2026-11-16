package com.ridex.service.routing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ridex.config.AppProperties;
import com.ridex.dto.GeoDtos.Place;
import com.ridex.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Address search and reverse lookup via OpenStreetMap Nominatim, called from the server so the
 * usage policy is respected in one place: identifying User-Agent, max 1 request/second, cached results.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final int CACHE_LIMIT = 500;

    private final RestClient http;
    private final String countryCodes;
    // ponytail: naive cache cleared when full; swap for Caffeine if traffic grows
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private long lastCallAt;

    public GeocodingService(AppProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .baseUrl(properties.routing().nominatimUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.routing().userAgent())
                .build();
        this.countryCodes = properties.routing().countryCodes();
    }

    @SuppressWarnings("unchecked")
    public List<Place> search(String query) {
        String key = "s:" + query.trim().toLowerCase(Locale.ROOT);
        Object cached = cache.get(key);
        if (cached != null) {
            return (List<Place>) cached;
        }
        try {
            throttle();
            NominatimPlace[] found = http.get()
                    .uri(uri -> uri.path("/search").queryParam("q", query.trim()).queryParam("format", "jsonv2")
                            .queryParam("limit", 6).queryParam("countrycodes", countryCodes).build())
                    .retrieve()
                    .body(NominatimPlace[].class);
            List<Place> places = found == null ? List.of() : Arrays.stream(found)
                    .map(p -> new Place(shortName(p.displayName()), p.displayName(),
                            Double.parseDouble(p.lat()), Double.parseDouble(p.lon())))
                    .toList();
            remember(key, places);
            return places;
        } catch (RestClientException e) {
            log.warn("Nominatim search failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address search is unavailable right now. Tap the map to choose a point instead.");
        }
    }

    /** Best-effort address for a point; falls back to the coordinates themselves. */
    public String reverse(double lat, double lng) {
        String key = "r:%.5f,%.5f".formatted(lat, lng);
        Object cached = cache.get(key);
        if (cached != null) {
            return (String) cached;
        }
        String fallback = "%.5f, %.5f".formatted(lat, lng);
        try {
            throttle();
            NominatimPlace place = http.get()
                    .uri(uri -> uri.path("/reverse").queryParam("lat", lat).queryParam("lon", lng)
                            .queryParam("format", "jsonv2").queryParam("zoom", 18).build())
                    .retrieve()
                    .body(NominatimPlace.class);
            String name = place == null || place.displayName() == null ? fallback : shortName(place.displayName());
            remember(key, name);
            return name;
        } catch (RestClientException e) {
            log.warn("Nominatim reverse lookup failed: {}", e.getMessage());
            return fallback;
        }
    }

    /** "Gateway of India, Apollo Bandar, Colaba, Mumbai, ..." → first three parts. */
    static String shortName(String displayName) {
        return Arrays.stream(displayName.split(",")).map(String::trim).limit(3).collect(Collectors.joining(", "));
    }

    private void remember(String key, Object value) {
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, value);
    }

    /** Nominatim usage policy: at most one request per second. */
    private synchronized void throttle() {
        long wait = lastCallAt + 1000 - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallAt = System.currentTimeMillis();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimPlace(@JsonProperty("display_name") String displayName, String lat, String lon) {
    }
}
