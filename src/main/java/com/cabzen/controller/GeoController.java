package com.cabzen.controller;

import com.cabzen.dto.GeoDtos.Address;
import com.cabzen.dto.GeoDtos.Place;
import com.cabzen.exception.ApiException;
import com.cabzen.service.routing.GeocodingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Address search for logged-in users; proxied so Nominatim's usage policy is enforced server-side. */
@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeocodingService geocoding;

    public GeoController(GeocodingService geocoding) {
        this.geocoding = geocoding;
    }

    @GetMapping("/search")
    public List<Place> search(@RequestParam String q) {
        if (q.isBlank() || q.length() > 200) {
            throw ApiException.badRequest("Type an address to search.");
        }
        return geocoding.search(q);
    }

    @GetMapping("/reverse")
    public Address reverse(@RequestParam double lat, @RequestParam double lng) {
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw ApiException.badRequest("Invalid coordinates.");
        }
        return new Address(geocoding.reverse(lat, lng));
    }
}
